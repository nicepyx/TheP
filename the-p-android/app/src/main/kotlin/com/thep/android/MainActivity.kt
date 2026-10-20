package com.thep.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.thep.core.designsystem.ThePTheme
import com.thep.core.model.WorkoutRules
import com.thep.feature.home.HomeScreen
import com.thep.feature.progress.ProgressScreen
import com.thep.feature.settings.SettingsScreen
import com.thep.feature.workout.PlanEditor
import com.thep.feature.workout.WorkoutScreen
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ThePTheme { MainApp() } }
    }

    @Composable
    private fun MainApp() {
        val data by model.data.collectAsStateWithLifecycle()
        val autoRest by model.autoRest.collectAsStateWithLifecycle()
        val busy by model.busy.collectAsStateWithLifecycle()
        val initialized by model.initialized.collectAsStateWithLifecycle()
        val preview by model.preview.collectAsStateWithLifecycle()
        val message by model.message.collectAsStateWithLifecycle()
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: "home"
        var sessionId by rememberSaveable { mutableStateOf<String?>(null) }
        var dayId by rememberSaveable { mutableStateOf<String?>(null) }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(message) {
            message?.let {
                snackbar.showSnackbar(it)
                model.clearMessage()
            }
        }
        val import =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let {
                    model.preview {
                        val bytes =
                            requireNotNull(contentResolver.openInputStream(it)).use { stream ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = stream.read(buffer)
                                    if (count < 0) break
                                    require(output.size() + count <= 10 * 1024 * 1024) {
                                        "文件不能超过 10 MB"
                                    }
                                    output.write(buffer, 0, count)
                                }
                                output.toByteArray()
                            }
                        require(bytes.size <= 10 * 1024 * 1024) { "文件不能超过 10 MB" }
                        bytes.toString(Charsets.UTF_8)
                    }
                }
            }
        val export =
            rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                uri?.let {
                    model.export { text ->
                        requireNotNull(contentResolver.openOutputStream(it)).use { stream ->
                            stream.write(text.toByteArray())
                        }
                    }
                }
            }
        fun navigate(destination: String) {
            nav.navigate(destination) {
                launchSingleTop = true
                popUpTo("home") { saveState = true }
                restoreState = true
            }
        }
        val stats =
            remember(data, route) {
                WorkoutRules.statistics(data.workouts, System.currentTimeMillis())
            }
        Scaffold(
            modifier = Modifier.imePadding(),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    listOf(
                            "home" to "首页",
                            "workout" to "训练",
                            "progress" to "进度",
                            "settings" to "设置",
                        )
                        .forEach { (key, label) ->
                            NavigationBarItem(
                                selected = key == route,
                                onClick = {
                                    if (key == "workout") sessionId = data.active?.id
                                    dayId = null
                                    navigate(key)
                                },
                                icon = {
                                    Text(
                                        when (key) {
                                            "home" -> "⌂"
                                            "workout" -> "✓"
                                            "progress" -> "↗"
                                            else -> "⚙"
                                        }
                                    )
                                },
                                label = { Text(label) },
                            )
                        }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!initialized) {
                    Text("正在准备本机训练数据库…", Modifier.padding(24.dp))
                    return@Column
                }
                NavHost(nav, startDestination = "home", modifier = Modifier.weight(1f)) {
                    composable("home") {
                        HomeScreen(
                            data,
                            stats,
                            { day ->
                                model.start(day) { id ->
                                    sessionId = id
                                    navigate("workout")
                                }
                            },
                            {
                                sessionId = data.active?.id
                                navigate("workout")
                            },
                            { day ->
                                dayId = day
                                nav.navigate("plan")
                            },
                            model::activatePlan,
                            model::createPlan,
                        )
                    }
                    composable("plan") {
                        data.days
                            .firstOrNull { it.id == dayId }
                            ?.let { day ->
                                PlanEditor(
                                    day,
                                    { nav.popBackStack() },
                                    { model.saveMove(day.id, it) },
                                    { model.removeMove(day.id, it) },
                                    { id, direction -> model.reorder(day.id, id, direction) },
                                    { title, weekday -> model.saveDay(day.id, title, weekday) },
                                )
                            }
                    }
                    composable("workout") {
                        val workout =
                            data.workouts.firstOrNull { it.id == sessionId } ?: data.active
                        WorkoutScreen(
                            workout,
                            { workout?.let { w -> model.saveSet(w.id, it) } },
                            { workout?.let { w -> model.status(w.id, it) } },
                            { workout?.let { w -> model.timer(w.id, it) } },
                            { workout?.let { w -> model.warmup(w.id, it) } },
                            { workout?.let { w -> model.note(w.id, it) } },
                            { navigate("home") },
                            busy,
                        )
                    }
                    composable("progress") {
                        ProgressScreen(
                            data,
                            stats,
                            { id ->
                                sessionId = id
                                navigate("workout")
                            },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            autoRest,
                            model::autoRest,
                            {
                                import.launch(
                                    arrayOf(
                                        "application/json",
                                        "text/plain",
                                        "application/octet-stream",
                                    )
                                )
                            },
                            { export.launch("TheP-${LocalDate.now()}.json") },
                            preview,
                            model::confirmImport,
                            model::cancelImport,
                            model::body,
                            model::cardio,
                            busy,
                        )
                    }
                }
            }
        }
    }
}
