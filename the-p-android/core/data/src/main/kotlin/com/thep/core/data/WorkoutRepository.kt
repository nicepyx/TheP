package com.thep.core.data

import androidx.room.withTransaction
import com.thep.core.database.*
import com.thep.core.model.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkoutRepository(
    private val database: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.workouts()

    private fun id() = UUID.randomUUID().toString()

    val dashboard =
        database.invalidationTracker
            .createFlow(
                "exercise",
                "workout_plan",
                "plan_day",
                "plan_exercise",
                "workout_session",
                "session_exercise",
                "workout_set",
                "body_measurement",
                "cardio_entry",
            )
            .map { snapshot() }

    suspend fun initialize() =
        database.withTransaction {
            if (dao.days().isEmpty()) {
                dao.plans(listOf(PlanEntity("default", "四分化训练")))
                dao.exercises(Seeds.exercises)
                dao.days(Seeds.days)
                dao.moves(Seeds.moves)
            }
        }

    suspend fun snapshot(): Dashboard =
        database.withTransaction {
            val exercises = dao.exercises().associateBy { it.id }
            val moves = dao.moves()
            val sessionExercises = dao.sessionExercises()
            val sets = dao.sets()
            Dashboard(
                dao.days().map { d ->
                    PlanDay(
                        d.id,
                        d.title,
                        d.weekday,
                        moves
                            .filter { it.dayId == d.id }
                            .map { m ->
                                val e = exercises.getValue(m.exerciseId)
                                PlanMove(
                                    m.id,
                                    Exercise(e.id, e.name, e.muscles, e.equipment),
                                    m.position,
                                    m.sets,
                                    m.repMin,
                                    m.repMax,
                                    m.restSeconds,
                                )
                            },
                        d.planId,
                    )
                },
                dao.sessions().map { s ->
                    Workout(
                        s.id,
                        s.name,
                        s.startedAt,
                        s.endedAt,
                        SessionStatus.valueOf(s.status),
                        s.note,
                        s.restDeadline,
                        sessionExercises
                            .filter { it.sessionId == s.id }
                            .map { e ->
                                SessionExercise(
                                    e.id,
                                    e.nameSnapshot,
                                    e.restSeconds,
                                    sets.filter { it.sessionExerciseId == e.id }.map { it.model() },
                                    e.exerciseId,
                                )
                            },
                        s.trainingDate,
                    )
                },
                dao.body().map {
                    BodyMeasurement(it.id, it.recordedAt, it.weightGrams, it.waistMillimeters)
                },
                dao.cardio().map {
                    CardioEntry(
                        it.id,
                        it.date,
                        it.kind,
                        it.durationSeconds,
                        it.stairs,
                        it.completed,
                    )
                },
                dao.plans().map { TrainingPlan(it.id, it.name, it.active) },
            )
        }

    suspend fun activatePlan(planId: String) =
        database.withTransaction {
            val plans = dao.plans()
            require(plans.any { it.id == planId })
            dao.plans(plans.map { it.copy(active = it.id == planId) })
        }

    suspend fun createPlan(name: String) =
        database.withTransaction {
            require(name.isNotBlank() && name.length <= 80) { "计划名称不能为空或超过 80 字" }
            val pid = id()
            val current = dao.plans().first { it.active }
            val days = dao.days().filter { it.planId == current.id }
            val moves = dao.moves()
            dao.plans(dao.plans().map { it.copy(active = false) } + PlanEntity(pid, name.trim()))
            days.forEach { day ->
                val newId = id()
                dao.days(listOf(day.copy(id = newId, planId = pid, revision = 1)))
                dao.moves(
                    moves.filter { it.dayId == day.id }.map { it.copy(id = id(), dayId = newId) }
                )
            }
        }

    suspend fun saveDay(dayId: String, title: String, weekday: Int) =
        database.withTransaction {
            require(title.isNotBlank() && title.length <= 80 && weekday in 1..7) {
                "请填写训练日名称与星期 1–7"
            }
            val day = dao.days().first { it.id == dayId }
            dao.days(
                listOf(
                    day.copy(title = title.trim(), weekday = weekday, revision = day.revision + 1)
                )
            )
        }

    suspend fun start(dayId: String): String =
        database.withTransaction {
            dao.sessions()
                .firstOrNull { it.status in listOf("ACTIVE", "PAUSED") }
                ?.let {
                    return@withTransaction it.id
                }
            val day = dao.days().first { it.id == dayId }
            val moves = dao.moves().filter { it.dayId == dayId }
            require(moves.isNotEmpty()) { "请先添加训练动作" }
            val exerciseMap = dao.exercises().associateBy { it.id }
            val allExercises = dao.sessionExercises()
            val allSets = dao.sets()
            val previousIds = dao.sessions().filter { it.status == "COMPLETED" }.map { it.id }
            val session = SessionEntity(id(), day.title, dayId, now())
            dao.session(session)
            moves.forEach { m ->
                val e =
                    SessionExerciseEntity(
                        id(),
                        session.id,
                        m.exerciseId,
                        exerciseMap.getValue(m.exerciseId).name,
                        m.position,
                        m.restSeconds,
                    )
                val previous =
                    previousIds.firstNotNullOfOrNull { s ->
                        allExercises.firstOrNull {
                            it.sessionId == s && it.exerciseId == m.exerciseId
                        }
                    }
                val last =
                    allSets.filter {
                        it.sessionExerciseId == previous?.id &&
                            it.kind == "WORK" &&
                            it.completedAt != null
                    }
                dao.sessionExercises(listOf(e))
                dao.sets(
                    (0 until m.sets).map { i ->
                        SetEntity(id(), e.id, i, last.getOrNull(i)?.weightGrams)
                    }
                )
            }
            session.id
        }

    suspend fun saveSet(
        sessionId: String,
        setId: String,
        weight: String,
        reps: String,
        rir: String,
        completed: Boolean,
        warmup: Boolean,
        autoRest: Boolean,
    ) =
        database.withTransaction {
            val w = WorkoutRules.weightGrams(weight)
            val r = WorkoutRules.reps(reps)
            val reserve = WorkoutRules.rir(rir)
            if (completed) require(r != null && r > 0) { "完成训练组前请填写有效次数；自重动作可留空重量" }
            val session = requireNotNull(dao.session(sessionId))
            require(session.status != "ABANDONED") { "已放弃的训练不可修改" }
            val set = requireNotNull(dao.set(setId))
            val exercise = dao.sessionExercises().first { it.id == set.sessionExerciseId }
            require(exercise.sessionId == sessionId)
            if (set.completedAt != null)
                dao.editLog(EditLogEntity(id(), set.id, now(), Json.encodeToString(set)))
            dao.set(
                set.copy(
                    weightGrams = w,
                    reps = r,
                    rir = reserve,
                    completedAt = if (completed) set.completedAt ?: now() else null,
                    kind = if (warmup) "WARMUP" else "WORK",
                )
            )
            dao.session(
                session.copy(
                    updatedAt = now(),
                    restDeadline =
                        if (
                            autoRest &&
                                completed &&
                                set.completedAt == null &&
                                session.status == "ACTIVE"
                        )
                            now() + exercise.restSeconds * 1000
                        else session.restDeadline,
                )
            )
        }

    suspend fun addWarmup(sessionId: String, exerciseId: String) =
        database.withTransaction {
            val session = requireNotNull(dao.session(sessionId))
            require(session.status == "ACTIVE" || session.status == "PAUSED")
            require(dao.sessionExercises().any { it.id == exerciseId && it.sessionId == sessionId })
            dao.sets(
                listOf(
                    SetEntity(
                        id(),
                        exerciseId,
                        (dao.sets()
                            .filter { it.sessionExerciseId == exerciseId }
                            .maxOfOrNull { it.position } ?: -1) + 1,
                        kind = "WARMUP",
                    )
                )
            )
            dao.session(session.copy(updatedAt = now()))
        }

    suspend fun status(sessionId: String, status: SessionStatus) =
        database.withTransaction {
            val s = requireNotNull(dao.session(sessionId))
            if (s.status == "COMPLETED" && status == SessionStatus.COMPLETED) return@withTransaction
            require(s.status in listOf("ACTIVE", "PAUSED")) { "训练已经结束" }
            if (status == SessionStatus.COMPLETED) {
                val ids =
                    dao.sessionExercises()
                        .filter { it.sessionId == sessionId }
                        .map { it.id }
                        .toSet()
                require(dao.sets().any { it.sessionExerciseId in ids && it.completedAt != null }) {
                    "至少完成一组再结束训练"
                }
            }
            dao.session(
                s.copy(
                    status = status.name,
                    updatedAt = now(),
                    endedAt =
                        if (status == SessionStatus.COMPLETED || status == SessionStatus.ABANDONED)
                            now()
                        else null,
                    restDeadline = if (status == SessionStatus.ACTIVE) s.restDeadline else null,
                )
            )
        }

    suspend fun timer(sessionId: String, seconds: Int) =
        database.withTransaction {
            require(seconds in 0..3600)
            val s = requireNotNull(dao.session(sessionId))
            require(s.status in listOf("ACTIVE", "PAUSED"))
            dao.session(
                s.copy(
                    restDeadline = if (seconds == 0) null else now() + seconds * 1000,
                    updatedAt = now(),
                )
            )
        }

    suspend fun saveNote(sessionId: String, note: String) =
        database.withTransaction {
            require(note.length <= 2000)
            val s = requireNotNull(dao.session(sessionId))
            dao.session(s.copy(note = note, updatedAt = now()))
        }

    suspend fun saveMove(
        dayId: String,
        moveId: String?,
        name: String,
        sets: Int,
        min: Int,
        max: Int,
        rest: Int,
    ) =
        database.withTransaction {
            require(
                name.isNotBlank() &&
                    name.length <= 80 &&
                    sets in 1..12 &&
                    min in 1..1000 &&
                    max in min..1000 &&
                    rest in 0..3600
            ) {
                "检查动作名称、组数、次数范围及休息秒数"
            }
            val existing = dao.moves().firstOrNull { it.id == moveId }
            require(existing == null || existing.dayId == dayId)
            val oldExercise = existing?.let { e -> dao.exercises().first { it.id == e.exerciseId } }
            val eid = oldExercise?.id ?: id()
            dao.exercises(
                listOf(
                    ExerciseEntity(
                        eid,
                        name.trim(),
                        oldExercise?.muscles ?: "自定义",
                        oldExercise?.equipment ?: "自定义",
                    )
                )
            )
            dao.moves(
                listOf(
                    MoveEntity(
                        existing?.id ?: id(),
                        dayId,
                        eid,
                        existing?.position
                            ?: ((dao.moves()
                                .filter { it.dayId == dayId }
                                .maxOfOrNull { it.position } ?: -1) + 1),
                        sets,
                        min,
                        max,
                        rest,
                    )
                )
            )
            dao.reviseDay(dayId)
        }

    suspend fun removeMove(dayId: String, moveId: String) =
        database.withTransaction {
            require(dao.moves().any { it.id == moveId && it.dayId == dayId })
            dao.removeMove(moveId)
            dao.reviseDay(dayId)
        }

    suspend fun reorder(dayId: String, moveId: String, direction: Int) =
        database.withTransaction {
            val moves = dao.moves().filter { it.dayId == dayId }.sortedBy { it.position }
            val i = moves.indexOfFirst { it.id == moveId }
            val j = i + direction
            if (i >= 0 && j in moves.indices) {
                dao.moves(
                    listOf(
                        moves[i].copy(position = moves[j].position),
                        moves[j].copy(position = moves[i].position),
                    )
                )
                dao.reviseDay(dayId)
            }
        }

    suspend fun addBody(weight: String, waist: String) {
        val w = requireNotNull(WorkoutRules.weightGrams(weight))
        require(w in 20000..300000) { "体重须在 20–300 kg 之间" }
        val mm =
            if (waist.isBlank()) null
            else
                waist.toBigDecimalOrNull()?.multiply(java.math.BigDecimal.TEN)?.intValueExact()
                    ?: error("腰围格式错误")
        require(mm == null || mm in 200..3000) { "腰围须在 20–300 cm 之间" }
        dao.body(BodyEntity(id(), now(), w, mm))
    }

    suspend fun addCardio(date: String, kind: String, minutes: Int, stairs: Int) {
        LocalDate.parse(date)
        require(
            kind in listOf("walk", "stairs") &&
                minutes in 0..1440 &&
                stairs in 0..1000 &&
                (minutes > 0 || stairs > 0)
        ) {
            "请填写有效时长或楼层数"
        }
        dao.cardio(CardioEntity(id(), date, kind, minutes * 60, stairs))
    }

    suspend fun exportJson(): String =
        database.withTransaction {
            ImportParser.encodeBackup(
                Backup(
                    exercises = dao.exercises(),
                    days = dao.days(),
                    moves = dao.moves(),
                    sessions = dao.sessions(),
                    sessionExercises = dao.sessionExercises(),
                    sets = dao.sets(),
                    body = dao.body(),
                    cardio = dao.cardio(),
                    edits = dao.edits(),
                    plans = dao.plans(),
                    imports = dao.imports(),
                )
            )
        }

    suspend fun preview(text: String): ImportPreview {
        val parsed = ImportParser.parse(text)
        val b = parsed.backup
        val knownSessions = dao.sessions().map { it.id }.toSet()
        return ImportPreview(
            parsed.hash,
            parsed.source,
            b.sessions.size,
            b.sets.count { it.completedAt != null },
            WorkoutRules.volumeKg(b.sets.map { it.model() }),
            b.body.size,
            b.cardio.size,
            parsed.warnings +
                listOfNotNull(
                    if (b.sessions.any { it.id in knownSessions }) "存在相同 ID 的场次，导入时将跳过这些场次，不覆盖现有数据。"
                    else null
                ),
            dao.imported(parsed.hash) != null,
        )
    }

    suspend fun importJson(text: String): ImportResult =
        database.withTransaction {
            val parsed = ImportParser.parse(text)
            val b = parsed.backup
            if (dao.imported(parsed.hash) != null)
                return@withTransaction ImportResult(0, b.sessions.size, 0, 0)
            val sessionsBefore = dao.sessions().map { it.id }.toSet()
            val bodyBefore = dao.body().map { it.id }.toSet()
            val cardioBefore = dao.cardio().map { it.id }.toSet()
            val pristinePlans =
                dao.exercises().toSet() == Seeds.exercises.toSet() &&
                    dao.days().toSet() == Seeds.days.toSet() &&
                    dao.moves().toSet() == Seeds.moves.toSet() &&
                    sessionsBefore.isEmpty() &&
                    bodyBefore.isEmpty() &&
                    cardioBefore.isEmpty()
            if (parsed.source == "The P V5" && pristinePlans) {
                // Only replace untouched demonstration plans in a fresh installation, after import
                // confirmation.
                dao.clearMoves()
                dao.clearDays()
                dao.clearPlans()
                dao.plans(b.plans)
                dao.exercises(b.exercises)
                dao.days(b.days)
                dao.moves(b.moves)
            }
            val knownPlans = dao.plans().map { it.id }.toSet()
            dao.plans(b.plans.filter { it.id !in knownPlans }.map { it.copy(active = false) })
            val exerciseIds = dao.exercises().map { it.id }.toSet()
            dao.exercises(b.exercises.filter { it.id !in exerciseIds })
            val daysBefore = dao.days().associateBy { it.id }
            dao.days(b.days.filter { it.id !in daysBefore })
            val moveIds = dao.moves().map { it.id }.toSet()
            dao.moves(b.moves.filter { it.id !in moveIds })
            val sessions = b.sessions.filter { it.id !in sessionsBefore }
            val ids = sessions.map { it.id }.toSet()
            val ses = b.sessionExercises.filter { it.sessionId in ids }
            val seIds = ses.map { it.id }.toSet()
            require(
                sessions.count { it.status in listOf("ACTIVE", "PAUSED") } +
                    dao.sessions().count { it.status in listOf("ACTIVE", "PAUSED") } <= 1
            ) {
                "存在多个未结束训练，请先结束当前训练后再导入"
            }
            sessions.forEach { dao.session(it) }
            dao.sessionExercises(ses)
            dao.sets(b.sets.filter { it.sessionExerciseId in seIds })
            val body = b.body.filter { it.id !in bodyBefore }
            val cardio = b.cardio.filter { it.id !in cardioBefore }
            body.forEach { dao.body(it) }
            cardio.forEach { dao.cardio(it) }
            b.edits
                .filter { e -> b.sets.any { it.id == e.setId && it.sessionExerciseId in seIds } }
                .forEach { dao.editLog(it) }
            val result =
                ImportResult(sessions.size, b.sessions.size - sessions.size, body.size, cardio.size)
            b.imports.forEach { if (dao.imported(it.sourceHash) == null) dao.importLog(it) }
            if (dao.imported(parsed.hash) == null)
                dao.importLog(
                    ImportLogEntity(
                        parsed.hash,
                        now(),
                        parsed.source,
                        "sessions=${result.sessions};skipped=${result.skipped};body=${result.body};cardio=${result.cardio}",
                        if (parsed.source == "FitWave V4 JSON") text else "",
                    )
                )
            result
        }
}

private fun SetEntity.model() =
    WorkoutSet(
        id,
        sessionExerciseId,
        position,
        weightGrams,
        reps,
        rir,
        completedAt,
        SetKind.valueOf(kind),
    )
