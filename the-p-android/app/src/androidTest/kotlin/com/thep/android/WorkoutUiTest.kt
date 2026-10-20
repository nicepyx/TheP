package com.thep.android

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thep.core.data.WorkoutRepository
import com.thep.core.database.AppDatabase
import com.thep.core.designsystem.ThePTheme
import com.thep.core.model.WorkoutRules
import com.thep.feature.home.HomeScreen
import com.thep.feature.workout.WorkoutScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun nativeTrainingEntryAndSave() {
        val db =
            Room.inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    AppDatabase::class.java,
                )
                .build()
        val repository = WorkoutRepository(db)
        var data by
            mutableStateOf(
                runBlocking {
                    repository.initialize()
                    repository.snapshot()
                }
            )
        var training by mutableStateOf(false)
        try {
            compose.setContent {
                ThePTheme {
                    if (!training)
                        HomeScreen(
                            data,
                            WorkoutRules.statistics(data.workouts, System.currentTimeMillis()),
                            onStart = { day ->
                                runBlocking {
                                    repository.start(day)
                                    data = repository.snapshot()
                                }
                                training = true
                            },
                            onResume = { training = true },
                            onPlan = {},
                            onActivatePlan = {},
                            onCreatePlan = {},
                        )
                    else
                        WorkoutScreen(
                            data.active,
                            onSave = { input ->
                                runBlocking {
                                    repository.saveSet(
                                        data.active!!.id,
                                        input.id,
                                        input.weight,
                                        input.reps,
                                        input.rir,
                                        input.completed,
                                        input.warmup,
                                        false,
                                    )
                                    data = repository.snapshot()
                                }
                            },
                            onStatus = {},
                            onTimer = {},
                            onWarmup = {},
                            onNote = {},
                            onHome = { training = false },
                            busy = false,
                        )
                }
            }
            compose.onAllNodesWithText("开始训练")[0].performScrollTo().performClick()
            compose
                .onAllNodes(hasText("重量 kg") and hasSetTextAction())[0]
                .performTextReplacement("20.25")
            compose.onAllNodes(hasText("次数") and hasSetTextAction())[0].performTextReplacement("10")
            compose.onAllNodesWithText("完成并保存")[0].performScrollTo().performClick()
            compose.onAllNodesWithText("撤销完成")[0].assertExists()
            assertEquals(
                20250L,
                runBlocking { repository.snapshot() }
                    .active!!
                    .exercises
                    .first()
                    .sets
                    .first()
                    .weightGrams,
            )
        } finally {
            db.close()
        }
    }
}
