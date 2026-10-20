package com.thep.android

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thep.core.data.WorkoutRepository
import com.thep.core.database.AppDatabase
import com.thep.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private fun database() =
        Room.inMemoryDatabaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext,
                AppDatabase::class.java,
            )
            .build()

    @Test
    fun workoutSnapshotCompletionAndBackupRoundTrip() = runBlocking {
        val db = database()
        val restored = database()
        try {
            val repo = WorkoutRepository(db)
            repo.initialize()
            val before = repo.snapshot()
            assertEquals(4, before.days.size)
            val id = repo.start("mon")
            assertEquals(id, repo.start("tue"))
            val session = repo.snapshot().active!!
            val set = session.exercises.first().sets.first()
            repo.saveSet(id, set.id, "20.25", "10", "2", true, false, true)
            val move = before.days.first().moves.first()
            repo.saveMove("mon", move.id, "新名称", 2, 6, 8, 60)
            assertEquals("平板卧推", repo.snapshot().active!!.exercises.first().name)
            repo.status(id, SessionStatus.COMPLETED)
            repo.status(id, SessionStatus.COMPLETED)
            assertEquals(1, repo.snapshot().workouts.count { it.status == SessionStatus.COMPLETED })
            val second = repo.start("mon")
            assertNotEquals(id, second)
            assertEquals(
                20250L,
                repo.snapshot().active!!.exercises.first().sets.first().weightGrams,
            )
            repo.status(second, SessionStatus.ABANDONED)
            val target = WorkoutRepository(restored)
            target.initialize()
            val backup = repo.exportJson()
            assertEquals(2, target.importJson(backup).sessions)
            assertEquals(0, target.importJson(backup).sessions)
            assertEquals(2, target.snapshot().workouts.size)
            assertEquals("新名称", target.snapshot().days.first().moves.first().exercise.name)
            assertEquals(2, target.snapshot().days.first().moves.first().sets)
            assertEquals(
                202.5,
                WorkoutRules.statistics(target.snapshot().workouts, System.currentTimeMillis())
                    .volumeKg,
                0.001,
            )
        } finally {
            db.close()
            restored.close()
        }
    }

    @Test
    fun legacyImportIsIdempotentAndInvalidInputLeavesNoWrites() = runBlocking {
        val db = database()
        try {
            val repo = WorkoutRepository(db)
            repo.initialize()
            val legacy =
                """{"sessions":{"2026-10-19__mon":{"completedAt":"2026-10-19T18:00:00Z","sets":{"bench":[{"weight":"20","reps":"10","done":true}]}}},"body":[],"cardio":{}}"""
            assertEquals(1, repo.importJson(legacy).sessions)
            assertEquals(0, repo.importJson(legacy).sessions)
            assertTrue(repo.preview(legacy).alreadyImported)
            assertTrue(runCatching { repo.importJson(legacy.replace("20", "-1")) }.isFailure)
            assertEquals(1, repo.snapshot().workouts.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun planSelectionIsUniqueAndHistoryIsIndependent() = runBlocking {
        val db = database()
        try {
            val repo = WorkoutRepository(db)
            repo.initialize()
            repo.createPlan("我的新计划")
            val snapshot = repo.snapshot()
            assertEquals(2, snapshot.plans.size)
            assertEquals(1, snapshot.plans.count { it.active })
            val active = snapshot.plans.first { it.active }
            assertEquals(4, snapshot.days.count { it.planId == active.id })
            repo.activatePlan("default")
            assertEquals("default", repo.snapshot().plans.single { it.active }.id)
        } finally {
            db.close()
        }
    }

    @Test
    fun incompleteAndInvalidSetsCannotFinishSession() = runBlocking {
        val db = database()
        try {
            val repo = WorkoutRepository(db)
            repo.initialize()
            val id = repo.start("mon")
            val set = repo.snapshot().active!!.exercises.first().sets.first()
            assertTrue(runCatching { repo.status(id, SessionStatus.COMPLETED) }.isFailure)
            assertTrue(
                runCatching { repo.saveSet(id, set.id, "-1", "10", "", true, false, true) }
                    .isFailure
            )
            assertTrue(
                runCatching { repo.saveSet(id, set.id, "20", "", "", true, false, true) }.isFailure
            )
            assertNull(repo.snapshot().active!!.exercises.first().sets.first().completedAt)
        } finally {
            db.close()
        }
    }
}
