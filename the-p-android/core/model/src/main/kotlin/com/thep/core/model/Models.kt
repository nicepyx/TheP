package com.thep.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

enum class SessionStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    ABANDONED,
}

enum class SetKind {
    WORK,
    WARMUP,
}

data class Exercise(
    val id: String,
    val name: String,
    val muscles: String = "自定义",
    val equipment: String = "自定义",
)

data class PlanMove(
    val id: String,
    val exercise: Exercise,
    val order: Int,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
)

data class TrainingPlan(val id: String, val name: String, val active: Boolean)

data class PlanDay(
    val id: String,
    val title: String,
    val weekday: Int,
    val moves: List<PlanMove>,
    val planId: String = "default",
)

data class WorkoutSet(
    val id: String,
    val exerciseId: String,
    val order: Int,
    val weightGrams: Long?,
    val reps: Int?,
    val rir: Int?,
    val completedAt: Long?,
    val kind: SetKind,
)

data class SessionExercise(
    val id: String,
    val name: String,
    val restSeconds: Int,
    val sets: List<WorkoutSet>,
    val sourceExerciseId: String = id,
)

data class Workout(
    val id: String,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val status: SessionStatus,
    val note: String,
    val restDeadline: Long?,
    val exercises: List<SessionExercise>,
    val trainingDate: String? = null,
)

data class BodyMeasurement(
    val id: String,
    val recordedAt: Long,
    val weightGrams: Long,
    val waistMillimeters: Int?,
)

data class CardioEntry(
    val id: String,
    val date: String,
    val kind: String,
    val durationSeconds: Int,
    val stairs: Int,
    val completed: Boolean = true,
)

data class ExerciseRecord(
    val name: String,
    val maxWeightGrams: Long,
    val estimatedOneRepMaxGrams: Long,
)

data class Statistics(
    val completedSessions: Int = 0,
    val weeklySessions: Int = 0,
    val volumeKg: Double = 0.0,
    val records: List<ExerciseRecord> = emptyList(),
    val weeklyVolumes: List<Pair<String, Double>> = emptyList(),
)

data class Dashboard(
    val days: List<PlanDay> = emptyList(),
    val workouts: List<Workout> = emptyList(),
    val body: List<BodyMeasurement> = emptyList(),
    val cardio: List<CardioEntry> = emptyList(),
    val plans: List<TrainingPlan> = emptyList(),
) {
    val active: Workout?
        get() =
            workouts.firstOrNull {
                it.status == SessionStatus.ACTIVE || it.status == SessionStatus.PAUSED
            }
}

data class ImportPreview(
    val hash: String,
    val source: String,
    val sessions: Int,
    val completedSets: Int,
    val volumeKg: Double,
    val body: Int,
    val cardio: Int,
    val warnings: List<String>,
    val alreadyImported: Boolean,
)

data class ImportResult(val sessions: Int, val skipped: Int, val body: Int, val cardio: Int)

object WorkoutRules {
    fun weightGrams(text: String): Long? {
        if (text.isBlank()) return null
        val value = text.toBigDecimalOrNull() ?: error("重量须为数字")
        require(value >= java.math.BigDecimal.ZERO && value <= java.math.BigDecimal(2000)) {
            "重量须在 0–2000 kg 之间"
        }
        return value.multiply(java.math.BigDecimal(1000)).longValueExact()
    }

    fun reps(text: String): Int? = optionalInt(text, 0..1000, "次数")

    fun rir(text: String): Int? = optionalInt(text, 0..10, "RIR")

    private fun optionalInt(text: String, range: IntRange, label: String): Int? {
        if (text.isBlank()) return null
        val value = text.toIntOrNull() ?: error("${label}须为整数")
        require(value in range) { "${label}超出范围" }
        return value
    }

    fun volumeKg(sets: List<WorkoutSet>): Double =
        sets
            .filter { it.completedAt != null && it.kind == SetKind.WORK }
            .sumOf { (it.weightGrams ?: 0) / 1000.0 * (it.reps ?: 0) }

    fun estimatedOneRepMax(weight: Long, reps: Int): Long? =
        when {
            weight <= 0 || reps !in 1..12 -> null
            reps == 1 -> weight
            else -> (weight * (1.0 + reps / 30.0)).toLong()
        }

    fun statistics(
        workouts: List<Workout>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Statistics {
        val complete = workouts.filter { it.status == SessionStatus.COMPLETED }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        fun date(w: Workout) =
            w.trainingDate?.let(java.time.LocalDate::parse)
                ?: Instant.ofEpochMilli(w.startedAt).atZone(zone).toLocalDate()
        val records =
            complete
                .flatMap { it.exercises }
                .groupBy { it.sourceExerciseId }
                .mapNotNull { (_, exercises) ->
                    val name = exercises.first().name
                    val sets =
                        exercises
                            .flatMap { it.sets }
                            .filter {
                                it.completedAt != null &&
                                    it.kind == SetKind.WORK &&
                                    (it.weightGrams ?: 0) > 0 &&
                                    (it.reps ?: 0) > 0
                            }
                    if (sets.isEmpty()) null
                    else
                        ExerciseRecord(
                            name,
                            sets.maxOf { it.weightGrams!! },
                            sets
                                .mapNotNull { estimatedOneRepMax(it.weightGrams!!, it.reps!!) }
                                .maxOrNull() ?: 0,
                        )
                }
        return Statistics(
            complete.size,
            complete.count { date(it) >= monday && date(it) <= today },
            complete.sumOf { volumeKg(it.exercises.flatMap { e -> e.sets }) },
            records,
            (3 downTo 0).map { weeks ->
                val start = monday.minusWeeks(weeks.toLong())
                start.toString() to
                    complete
                        .filter { date(it) >= start && date(it) < start.plusDays(7) }
                        .sumOf { volumeKg(it.exercises.flatMap { e -> e.sets }) }
            },
        )
    }

    fun remainingSeconds(deadline: Long?, now: Long): Int =
        if (deadline == null) 0 else ((deadline - now).coerceAtLeast(0) / 1000.0).roundToInt()
}
