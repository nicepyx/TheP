package com.thep.core.model

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class WorkoutRulesTest {
    @Test
    fun crossingMidnightAndBackdatedRecordsKeepTheirTrainingDay() {
        val sunday = Instant.parse("2026-10-18T23:50:00Z").toEpochMilli()
        val monday = Instant.parse("2026-10-19T00:10:00Z").toEpochMilli()
        val overnight =
            Workout(
                "overnight",
                "训练",
                sunday,
                monday,
                SessionStatus.COMPLETED,
                "",
                null,
                emptyList(),
            )
        val backdated =
            overnight.copy(id = "backdated", startedAt = monday, trainingDate = "2026-10-18")
        val result = WorkoutRules.statistics(listOf(overnight, backdated), monday, ZoneId.of("UTC"))
        assertEquals(0, result.weeklySessions)
        assertEquals(2, result.completedSessions)
    }

    private fun set(
        weight: Long? = 20000,
        reps: Int? = 10,
        done: Long? = 1,
        kind: SetKind = SetKind.WORK,
    ) = WorkoutSet("s", "e", 0, weight, reps, null, done, kind)

    @Test
    fun decimalWeightsAreExact() {
        assertEquals(20250L, WorkoutRules.weightGrams("20.25"))
        assertNull(WorkoutRules.weightGrams(""))
    }

    @Test
    fun invalidInputsAreRejected() {
        for (x in listOf("-1", "NaN", "2001", "1.0001")) assertTrue(
            runCatching { WorkoutRules.weightGrams(x) }.isFailure
        )
        assertTrue(runCatching { WorkoutRules.reps("1.5") }.isFailure)
        assertTrue(runCatching { WorkoutRules.rir("11") }.isFailure)
    }

    @Test
    fun volumeExcludesIncompleteAndWarmup() {
        assertEquals(
            200.0,
            WorkoutRules.volumeKg(
                listOf(set(), set(done = null), set(kind = SetKind.WARMUP), set(weight = null))
            ),
            0.001,
        )
    }

    @Test
    fun oneRepMaxHasGuardrails() {
        assertEquals(20000L, WorkoutRules.estimatedOneRepMax(20000, 1))
        assertNull(WorkoutRules.estimatedOneRepMax(20000, 20))
        assertNull(WorkoutRules.estimatedOneRepMax(0, 10))
    }

    @Test
    fun mondayAndTimeZoneBoundaries() {
        val monday = Instant.parse("2026-10-19T00:10:00Z").toEpochMilli()
        fun workout(id: String, end: Long, status: SessionStatus = SessionStatus.COMPLETED) =
            Workout(
                id,
                "训练",
                end,
                end,
                status,
                "",
                null,
                listOf(SessionExercise("e", "卧推", 90, listOf(set()))),
            )
        val stats =
            WorkoutRules.statistics(
                listOf(
                    workout("sun", monday - 3600000),
                    workout("mon", monday),
                    workout("active", monday, SessionStatus.ACTIVE),
                ),
                monday,
                ZoneId.of("UTC"),
            )
        assertEquals(1, stats.weeklySessions)
        assertEquals(2, stats.completedSessions)
        assertEquals(400.0, stats.volumeKg, 0.001)
        assertEquals(
            2,
            WorkoutRules.statistics(
                    listOf(workout("sun", monday - 3600000), workout("mon", monday)),
                    monday,
                    ZoneId.of("Asia/Shanghai"),
                )
                .weeklySessions,
        )
    }

    @Test
    fun timerDoesNotDependOnTicks() {
        assertEquals(30, WorkoutRules.remainingSeconds(60000, 30000))
        assertEquals(0, WorkoutRules.remainingSeconds(60000, 90000))
    }
}
