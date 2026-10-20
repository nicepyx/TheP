package com.thep.core.database

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun transactionRollsBackOnForeignKeyFailure() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            assertTrue(
                runCatching {
                        db.withTransaction {
                            db.workouts().session(SessionEntity("session", "测试", null, 1))
                            db.workouts().sets(listOf(SetEntity("orphan", "missing", 0)))
                        }
                    }
                    .isFailure
            )
            assertTrue(db.workouts().sessions().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun confirmedRecordSurvivesDatabaseReopen() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            db.workouts().session(SessionEntity("session", "测试", null, 1))
            db.workouts()
                .sessionExercises(
                    listOf(SessionExerciseEntity("exercise", "session", "bench", "卧推", 0, 90))
                )
            db.workouts().sets(listOf(SetEntity("set", "exercise", 0, 20250, 10, 2, 2)))
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            assertEquals(20250L, db.workouts().sets().single().weightGrams)
            assertEquals(10, db.workouts().sets().single().reps)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
