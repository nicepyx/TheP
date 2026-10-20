package com.thep.core.database

import androidx.room.*
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val muscles: String,
    val equipment: String,
)

@Serializable
@Entity(tableName = "workout_plan")
data class PlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val active: Boolean = true,
    val revision: Int = 1,
)

@Serializable
@Entity(tableName = "plan_day")
data class DayEntity(
    @PrimaryKey val id: String,
    val title: String,
    val weekday: Int,
    val revision: Int = 1,
    val planId: String = "default",
)

@Serializable
@Entity(
    tableName = "plan_exercise",
    indices = [Index("dayId")],
    foreignKeys =
        [
            ForeignKey(
                entity = DayEntity::class,
                parentColumns = ["id"],
                childColumns = ["dayId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class MoveEntity(
    @PrimaryKey val id: String,
    val dayId: String,
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
)

@Serializable
@Entity(tableName = "workout_session")
data class SessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dayId: String?,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: String = "ACTIVE",
    val note: String = "",
    val restDeadline: Long? = null,
    val updatedAt: Long = startedAt,
    val trainingDate: String =
        java.time.Instant.ofEpochMilli(startedAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toString(),
)

@Serializable
@Entity(
    tableName = "session_exercise",
    indices = [Index("sessionId")],
    foreignKeys =
        [
            ForeignKey(
                entity = SessionEntity::class,
                parentColumns = ["id"],
                childColumns = ["sessionId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class SessionExerciseEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val nameSnapshot: String,
    val position: Int,
    val restSeconds: Int,
)

@Serializable
@Entity(
    tableName = "workout_set",
    indices = [Index("sessionExerciseId")],
    foreignKeys =
        [
            ForeignKey(
                entity = SessionExerciseEntity::class,
                parentColumns = ["id"],
                childColumns = ["sessionExerciseId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class SetEntity(
    @PrimaryKey val id: String,
    val sessionExerciseId: String,
    val position: Int,
    val weightGrams: Long? = null,
    val reps: Int? = null,
    val rir: Int? = null,
    val completedAt: Long? = null,
    val kind: String = "WORK",
)

@Serializable
@Entity(tableName = "body_measurement")
data class BodyEntity(
    @PrimaryKey val id: String,
    val recordedAt: Long,
    val weightGrams: Long,
    val waistMillimeters: Int?,
)

@Serializable
@Entity(tableName = "cardio_entry")
data class CardioEntity(
    @PrimaryKey val id: String,
    val date: String,
    val kind: String,
    val durationSeconds: Int,
    val stairs: Int,
    val completed: Boolean = true,
)

@Serializable
@Entity(tableName = "data_import_log")
data class ImportLogEntity(
    @PrimaryKey val sourceHash: String,
    val importedAt: Long,
    val source: String,
    val counts: String,
    val rawJson: String,
)

@Serializable
@Entity(tableName = "set_edit_log")
data class EditLogEntity(
    @PrimaryKey val id: String,
    val setId: String,
    val changedAt: Long,
    val previousJson: String,
)

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_plan") suspend fun plans(): List<PlanEntity>

    @Upsert suspend fun plans(value: List<PlanEntity>)

    @Query("DELETE FROM workout_plan") suspend fun clearPlans()

    @Query("SELECT * FROM exercise") suspend fun exercises(): List<ExerciseEntity>

    @Query("SELECT * FROM plan_day ORDER BY weekday") suspend fun days(): List<DayEntity>

    @Query("SELECT * FROM plan_exercise ORDER BY position") suspend fun moves(): List<MoveEntity>

    @Query("SELECT * FROM workout_session ORDER BY startedAt DESC")
    suspend fun sessions(): List<SessionEntity>

    @Query("SELECT * FROM session_exercise ORDER BY position")
    suspend fun sessionExercises(): List<SessionExerciseEntity>

    @Query("SELECT * FROM workout_set ORDER BY position") suspend fun sets(): List<SetEntity>

    @Query("SELECT * FROM body_measurement ORDER BY recordedAt DESC")
    suspend fun body(): List<BodyEntity>

    @Query("SELECT * FROM cardio_entry ORDER BY date DESC") suspend fun cardio(): List<CardioEntity>

    @Query("SELECT * FROM data_import_log") suspend fun imports(): List<ImportLogEntity>

    @Query("SELECT * FROM set_edit_log") suspend fun edits(): List<EditLogEntity>

    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun session(id: String): SessionEntity?

    @Query("SELECT * FROM workout_set WHERE id = :id") suspend fun set(id: String): SetEntity?

    @Query("SELECT * FROM data_import_log WHERE sourceHash = :hash")
    suspend fun imported(hash: String): ImportLogEntity?

    @Query("DELETE FROM plan_exercise") suspend fun clearMoves()

    @Query("DELETE FROM plan_day") suspend fun clearDays()

    @Query("DELETE FROM plan_exercise WHERE id = :id") suspend fun removeMove(id: String)

    @Query("UPDATE plan_day SET revision = revision + 1 WHERE id = :id")
    suspend fun reviseDay(id: String)

    @Upsert suspend fun exercises(value: List<ExerciseEntity>)

    @Upsert suspend fun days(value: List<DayEntity>)

    @Upsert suspend fun moves(value: List<MoveEntity>)

    @Upsert suspend fun session(value: SessionEntity)

    @Insert suspend fun sessionExercises(value: List<SessionExerciseEntity>)

    @Insert suspend fun sets(value: List<SetEntity>)

    @Upsert suspend fun set(value: SetEntity)

    @Upsert suspend fun body(value: BodyEntity)

    @Upsert suspend fun cardio(value: CardioEntity)

    @Insert suspend fun importLog(value: ImportLogEntity)

    @Insert suspend fun editLog(value: EditLogEntity)
}

@Database(
    entities =
        [
            PlanEntity::class,
            ExerciseEntity::class,
            DayEntity::class,
            MoveEntity::class,
            SessionEntity::class,
            SessionExerciseEntity::class,
            SetEntity::class,
            BodyEntity::class,
            CardioEntity::class,
            ImportLogEntity::class,
            EditLogEntity::class,
        ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workouts(): WorkoutDao
}

@Serializable
data class Backup(
    val format: String = "the-p",
    val version: Int = 1,
    val exercises: List<ExerciseEntity>,
    val days: List<DayEntity>,
    val moves: List<MoveEntity>,
    val sessions: List<SessionEntity>,
    val sessionExercises: List<SessionExerciseEntity>,
    val sets: List<SetEntity>,
    val body: List<BodyEntity>,
    val cardio: List<CardioEntity>,
    val edits: List<EditLogEntity> = emptyList(),
    val imports: List<ImportLogEntity> = emptyList(),
    val plans: List<PlanEntity> = listOf(PlanEntity("default", "四分化训练")),
)
