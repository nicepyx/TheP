package com.thep.core.data

import com.thep.core.database.*
import com.thep.core.model.*
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

data class ParsedImport(
    val hash: String,
    val source: String,
    val backup: Backup,
    val warnings: List<String>,
)

object ImportParser {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeBackup(backup: Backup): String = json.encodeToString(backup)

    fun parse(text: String): ParsedImport {
        require(text.toByteArray().size <= 10 * 1024 * 1024) { "备份文件不能超过 10 MB" }
        val root = json.parseToJsonElement(text) as? JsonObject ?: error("备份根节点必须是对象")
        val hash =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") {
                "%02x".format(it)
            }
        val warnings = mutableListOf<String>()
        val source: String
        val backup: Backup
        if (root["format"]?.jsonPrimitive?.content == "the-p") {
            source = "The P V5"
            require(root["version"]?.jsonPrimitive?.int == 1) { "不支持此备份版本" }
            backup = json.decodeFromString<Backup>(text)
            warnings += "同 ID 场次、身体记录和有氧记录将跳过。全新安装且课表未修改时，恢复备份课表；否则保留现有课表，原始历史仍导入。"
        } else {
            source = "FitWave V4 JSON"
            require(
                root["sessions"] is JsonObject &&
                    root["cardio"] is JsonObject &&
                    root["body"] is JsonArray
            ) {
                "缺少 sessions、cardio 或 body，文件不是支持的 V4 备份"
            }
            val sessions = mutableListOf<SessionEntity>()
            val exercises = Seeds.exercises.toMutableList()
            val ses = mutableListOf<SessionExerciseEntity>()
            val sets = mutableListOf<SetEntity>()
            root.getValue("sessions").jsonObject.forEach { (key, element) ->
                require(Regex("\\d{4}-\\d{2}-\\d{2}__(mon|tue|thu|sat)").matches(key)) {
                    "无法识别训练日期或训练日"
                }
                val day = key.substringAfter("__")
                val date = key.substringBefore("__")
                val dateTime =
                    LocalDate.parse(date)
                        .atTime(12, 0)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                val obj = element.jsonObject
                val sid = stable("session:$key")
                val ended = timestamp(obj["completedAt"])
                val started = timestamp(obj["startedAt"]) ?: dateTime
                sessions +=
                    SessionEntity(
                        sid,
                        Seeds.days.first { it.id == day }.title,
                        day,
                        started,
                        ended,
                        if (ended != null) "COMPLETED" else "ABANDONED",
                        obj["note"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        updatedAt = ended ?: started,
                        trainingDate = date,
                    )
                if (ended == null) warnings += "未结束的旧训练会保留记录，标记为已放弃，避免自动成为当前训练。"
                val legacySets = obj["sets"] as? JsonObject ?: error("训练场次缺少 sets")
                legacySets.entries.forEachIndexed { index, (eid, value) ->
                    require(eid.isNotBlank() && eid.length <= 100)
                    if (exercises.none { it.id == eid }) {
                        exercises += ExerciseEntity(eid, "旧版动作 · $eid", "未识别", "未知")
                        warnings += "存在未知动作 ID，已保留原 ID 与训练组。"
                    }
                    val seid = stable("session-exercise:$key:$eid")
                    ses +=
                        SessionExerciseEntity(
                            seid,
                            sid,
                            eid,
                            exercises.first { it.id == eid }.name,
                            index,
                            Seeds.moves.firstOrNull { it.exerciseId == eid }?.restSeconds ?: 90,
                        )
                    value.jsonArray.forEachIndexed { i, v ->
                        val s = v.jsonObject
                        val weight = WorkoutRules.weightGrams(s["weight"].nullableText())
                        val reps = WorkoutRules.reps(s["reps"].nullableText())
                        val done = s["done"]?.jsonPrimitive?.booleanOrNull ?: false
                        sets +=
                            SetEntity(
                                stable("set:$key:$eid:$i"),
                                seid,
                                i,
                                weight,
                                reps,
                                completedAt = if (done) ended ?: started else null,
                            )
                    }
                }
            }
            val body =
                root.getValue("body").jsonArray.mapIndexed { i, v ->
                    val b = v.jsonObject
                    val date = LocalDate.parse(b.getValue("date").jsonPrimitive.content)
                    val weight =
                        requireNotNull(WorkoutRules.weightGrams(b["weight"].nullableText())) {
                            "体重缺失"
                        }
                    val waist =
                        b["waist"]
                            .nullableText()
                            .takeIf { it.isNotBlank() }
                            ?.toBigDecimal()
                            ?.multiply(java.math.BigDecimal.TEN)
                            ?.intValueExact()
                    BodyEntity(
                        stable("body:${date}:$weight:$waist:$i"),
                        date
                            .atTime(12, 0)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli(),
                        weight,
                        waist,
                    )
                }
            val cardio =
                root.getValue("cardio").jsonObject.map { (date, v) ->
                    LocalDate.parse(date)
                    val c = v.jsonObject
                    val rawKind = c["kind"]?.jsonPrimitive?.contentOrNull
                    val kind =
                        if (
                            rawKind == "stairs" || c["stairs"]?.jsonPrimitive?.booleanOrNull == true
                        )
                            "stairs"
                        else "walk"
                    val duration =
                        c["durationSeconds"]?.jsonPrimitive?.intOrNull
                            ?: c["walk"]?.jsonPrimitive?.intOrNull?.times(60)
                            ?: c["minutes"]?.jsonPrimitive?.intOrNull?.times(60)
                            ?: 0
                    val floors = c["stairs"]?.jsonPrimitive?.intOrNull ?: 0
                    CardioEntity(
                        stable("cardio:$date"),
                        date,
                        kind,
                        duration,
                        floors,
                        c["done"]?.jsonPrimitive?.booleanOrNull ?: false,
                    )
                }
            val overrides = (root["settings"] as? JsonObject)?.get("overrides") as? JsonObject
            // Imported custom targets become separate plan days; the current plan is never
            // overwritten.
            val importDays =
                if (overrides.isNullOrEmpty()) emptyList()
                else
                    Seeds.days.map {
                        it.copy(
                            id = "legacy-${hash.take(8)}-${it.id}",
                            title = "旧版课表 · ${it.title}",
                            planId = "legacy-${hash.take(8)}",
                        )
                    }
            val importMoves =
                if (importDays.isEmpty()) emptyList()
                else
                    Seeds.moves.map { m ->
                        val o = overrides?.get(m.exerciseId) as? JsonObject
                        val target = o?.get("reps")?.jsonPrimitive?.contentOrNull
                        val range =
                            target?.let { Regex("(\\d+)\\s*[–—~至-]\\s*(\\d+)").matchEntire(it) }
                        val count = o?.get("sets")?.jsonPrimitive?.intOrNull ?: m.sets
                        require(count in 1..12) { "旧版课表组数无效" }
                        if (target != null && range == null)
                            warnings += "部分次数目标无法转换，保留在原始备份中；课表使用默认次数。"
                        m.copy(
                            id = "legacy-${hash.take(8)}-${m.id}",
                            dayId = "legacy-${hash.take(8)}-${m.dayId}",
                            sets = count,
                            repMin = range?.groupValues?.get(1)?.toInt() ?: m.repMin,
                            repMax = range?.groupValues?.get(2)?.toInt() ?: m.repMax,
                        )
                    }
            backup =
                Backup(
                    exercises = exercises,
                    days = Seeds.days + importDays,
                    plans =
                        listOf(PlanEntity("default", "四分化训练")) +
                            (if (importDays.isEmpty()) emptyList()
                            else listOf(PlanEntity("legacy-${hash.take(8)}", "旧版自定义课表", false))),
                    moves = Seeds.moves + importMoves,
                    sessions = sessions,
                    sessionExercises = ses,
                    sets = sets,
                    body = body,
                    cardio = cardio,
                )
            warnings += "未知字段、旧偏好和原始内容保存在本机导入日志，不自动改变当前偏好。"
            warnings += "旧版有氧按原始 walk 分钟与 stairs 楼层导入，缺失值保留为 0；不补造运动量。"
            if (importDays.isNotEmpty()) warnings += "自定义目标保存为独立的旧版课表，不覆盖当前课表。"
        }
        validate(backup)
        return ParsedImport(hash, source, backup, warnings.distinct())
    }

    private fun validate(b: Backup) {
        fun ids(values: List<String>) {
            require(
                values.all { it.isNotBlank() && it.length <= 200 } &&
                    values.toSet().size == values.size
            ) {
                "记录 ID 无效或重复"
            }
        }
        ids(b.plans.map { it.id })
        require(b.plans.count { it.active } == 1 && b.plans.all { it.name.isNotBlank() }) {
            "计划须且仅须一个当前计划"
        }
        require(b.days.all { d -> b.plans.any { it.id == d.planId } }) { "训练日缺少所属计划" }
        ids(b.exercises.map { it.id })
        ids(b.days.map { it.id })
        ids(b.moves.map { it.id })
        ids(b.sessions.map { it.id })
        ids(b.sessionExercises.map { it.id })
        ids(b.sets.map { it.id })
        ids(b.body.map { it.id })
        ids(b.cardio.map { it.id })
        ids(b.edits.map { it.id })
        val exercises = b.exercises.map { it.id }.toSet()
        val days = b.days.map { it.id }.toSet()
        val sessions = b.sessions.map { it.id }.toSet()
        val ses = b.sessionExercises.map { it.id }.toSet()
        require(
            b.exercises.all { it.name.isNotBlank() && it.name.length <= 200 } &&
                b.days.all { it.weekday in 1..7 && it.title.isNotBlank() }
        )
        require(
            b.moves.all {
                it.dayId in days &&
                    it.exerciseId in exercises &&
                    it.sets in 1..12 &&
                    it.repMin in 1..1000 &&
                    it.repMax in it.repMin..1000 &&
                    it.restSeconds in 0..3600
            }
        ) {
            "课表数据无效"
        }
        require(
            b.sessions.all {
                runCatching { LocalDate.parse(it.trainingDate) }.isSuccess &&
                    it.status in SessionStatus.entries.map { s -> s.name } &&
                    it.startedAt > 0 &&
                    (it.endedAt?.let { end -> end >= it.startedAt } ?: true) &&
                    (it.status != "COMPLETED" || it.endedAt != null) &&
                    (it.dayId == null || it.dayId in days)
            }
        ) {
            "训练状态或时间无效"
        }
        require(
            b.sessionExercises.all {
                it.sessionId in sessions && it.exerciseId in exercises && it.restSeconds in 0..3600
            }
        ) {
            "训练动作关联无效"
        }
        require(
            b.sets.all {
                it.sessionExerciseId in ses &&
                    it.position >= 0 &&
                    (it.weightGrams == null || it.weightGrams in 0L..2000000L) &&
                    (it.reps == null || it.reps in 0..1000) &&
                    (it.rir == null || it.rir in 0..10) &&
                    it.kind in SetKind.entries.map { k -> k.name }
            }
        ) {
            "训练组数值或关联无效"
        }
        require(
            b.body.all {
                it.weightGrams in 20000..300000 &&
                    (it.waistMillimeters == null || it.waistMillimeters in 200..3000)
            }
        ) {
            "体重或腰围无效"
        }
        require(
            b.cardio.all {
                runCatching { LocalDate.parse(it.date) }.isSuccess &&
                    it.durationSeconds in 0..86400 &&
                    it.stairs in 0..1000 &&
                    it.kind in listOf("stairs", "walk")
            }
        ) {
            "有氧记录无效"
        }
    }

    private fun stable(key: String) =
        UUID.nameUUIDFromBytes("the-p-v4:$key".toByteArray()).toString()

    private fun timestamp(value: JsonElement?): Long? {
        val text = value.nullableText()
        if (text.isBlank()) return null
        return text.toLongOrNull() ?: Instant.parse(text).toEpochMilli()
    }

    private fun JsonElement?.nullableText(): String =
        (this as? JsonPrimitive)?.contentOrNull.orEmpty()
}
