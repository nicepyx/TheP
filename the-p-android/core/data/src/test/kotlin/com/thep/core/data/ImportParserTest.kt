package com.thep.core.data

import org.junit.Assert.*
import org.junit.Test

class ImportParserTest {
    @Test
    fun nativeBackupKeepsItsFormatMarkerAndRestoresAsV5() {
        val source =
            com.thep.core.database.Backup(
                exercises = emptyList(),
                days = emptyList(),
                moves = emptyList(),
                sessions = emptyList(),
                sessionExercises = emptyList(),
                sets = emptyList(),
                body = emptyList(),
                cardio = emptyList(),
            )
        val encoded = ImportParser.encodeBackup(source)
        val decoded = ImportParser.parse(encoded)
        assertEquals("The P V5", decoded.source)
        assertEquals(source, decoded.backup)
    }

    private val valid =
        """{"sessions":{"2026-10-19__mon":{"completedAt":"2026-10-19T12:00:00Z","sets":{"bench":[{"weight":"20.25","reps":"10","done":true},{"weight":"","reps":"","done":false}]}}},"body":[{"date":"2026-10-19","weight":70,"waist":80}],"cardio":{},"settings":{},"futureField":"preserved"}"""

    @Test
    fun legacyMappingIsExactAndDeterministic() {
        val first = ImportParser.parse(valid)
        val again = ImportParser.parse(valid)
        assertEquals(first.backup, again.backup)
        assertEquals(20250L, first.backup.sets.first().weightGrams)
        assertNull(first.backup.sets.last().weightGrams)
        assertNotNull(first.backup.sets.first().completedAt)
        assertEquals(70000L, first.backup.body.first().weightGrams)
        assertEquals(800, first.backup.body.first().waistMillimeters)
    }

    @Test
    fun backdatedLegacySessionKeepsItsCalendarDate() {
        val text =
            valid.replace(
                "\"completedAt\":\"2026-10-19T12:00:00Z\"",
                "\"startedAt\":\"2026-10-20T10:00:00Z\",\"completedAt\":\"2026-10-20T12:00:00Z\"",
            )
        val session = ImportParser.parse(text).backup.sessions.single()
        assertEquals("2026-10-19", session.trainingDate)
        assertEquals(
            java.time.Instant.parse("2026-10-20T10:00:00Z").toEpochMilli(),
            session.startedAt,
        )
    }

    @Test
    fun malformedDatesAndValuesAreRejected() {
        for (text in
            listOf(
                valid.replace("2026-10-19__mon", "2026-13-01__mon"),
                valid.replace("20.25", "-1"),
                "{}",
                valid.replace("\"body\":[", "\"body\":null,\"other\":["),
            )) assertTrue(runCatching { ImportParser.parse(text) }.isFailure)
    }

    @Test
    fun malformedJsonNeverProducesPartialImport() {
        assertTrue(runCatching { ImportParser.parse(valid.dropLast(8)) }.isFailure)
    }

    @Test
    fun excessiveSizeRejected() {
        assertTrue(runCatching { ImportParser.parse(" ".repeat(10 * 1024 * 1024 + 1)) }.isFailure)
    }

    @Test
    fun cardioUsesActualWalkAndStairValues() {
        val text =
            valid.replace(
                "\"cardio\":{}",
                "\"cardio\":{\"2026-10-19\":{\"kind\":\"walk\",\"walk\":45,\"stairs\":0,\"done\":true},\"2026-10-20\":{\"kind\":\"stairs\",\"stairs\":15,\"walk\":0,\"done\":true}}",
            )
        val result = ImportParser.parse(text).backup.cardio
        assertEquals(2700, result.first().durationSeconds)
        assertEquals(15, result.last().stairs)
    }

    @Test
    fun customPlanDoesNotReplaceDefault() {
        val custom =
            valid.replace(
                "\"settings\":{}",
                "\"settings\":{\"overrides\":{\"bench\":{\"sets\":2,\"reps\":\"6–8\"}}}",
            )
        val moves = ImportParser.parse(custom).backup.moves
        assertEquals(3, moves.first { it.id == "mon-bench" }.sets)
        assertEquals(2, moves.first { it.id.endsWith("mon-bench") && it.id != "mon-bench" }.sets)
    }
}
