package com.thep.feature.workout

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.thep.core.designsystem.*
import com.thep.core.model.*
import kotlinx.coroutines.delay

data class SetInput(
    val id: String,
    val weight: String,
    val reps: String,
    val rir: String,
    val completed: Boolean,
    val warmup: Boolean,
)

data class MoveInput(
    val id: String?,
    val name: String,
    val sets: Int,
    val min: Int,
    val max: Int,
    val rest: Int,
)

@Composable
fun WorkoutScreen(
    workout: Workout?,
    onSave: (SetInput) -> Unit,
    onStatus: (SessionStatus) -> Unit,
    onTimer: (Int) -> Unit,
    onWarmup: (String) -> Unit,
    onNote: (String) -> Unit,
    onHome: () -> Unit,
    busy: Boolean,
) {
    if (workout == null) {
        Column(Modifier.padding(24.dp)) {
            SectionTitle("还没有进行中的训练")
            Text("从首页选择一个训练日开始。")
            Button(onClick = onHome) { Text("前往首页") }
        }
        return
    }
    val editable =
        workout.status in
            listOf(SessionStatus.ACTIVE, SessionStatus.PAUSED, SessionStatus.COMPLETED)
    var confirmation by remember { mutableStateOf<SessionStatus?>(null) }
    val start = remember(workout.restDeadline) { SystemClock.elapsedRealtime() }
    val remainingAtStart =
        remember(workout.restDeadline) {
            ((workout.restDeadline ?: 0) - System.currentTimeMillis()).coerceIn(0, 3600000)
        }
    var remaining by remember(workout.restDeadline) { mutableLongStateOf(remainingAtStart / 1000) }
    LaunchedEffect(workout.restDeadline) {
        while (true) {
            remaining =
                ((remainingAtStart - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(0) +
                    999) / 1000
            delay(250)
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(workout.name)
        Text(
            when (workout.status) {
                SessionStatus.ACTIVE -> "训练进行中 · 确认保存后即写入本机数据库"
                SessionStatus.PAUSED -> "训练已暂停"
                SessionStatus.COMPLETED -> "已完成 · 可编辑记录并重新计算统计"
                SessionStatus.ABANDONED -> "已放弃 · 已保存的训练组仍保留"
            }
        )
        if (workout.status in listOf(SessionStatus.ACTIVE, SessionStatus.PAUSED)) {
            InfoCard("休息 ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}") {
                Text("Alpha 仅显示计时；后台到时通知在 Beta 提供。", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { onTimer(90) }, enabled = !busy) { Text("90秒") }
                    TextButton(
                        onClick = { onTimer((remaining + 30).toInt().coerceAtMost(3600)) },
                        enabled = !busy,
                    ) {
                        Text("+30秒")
                    }
                    TextButton(onClick = { onTimer(0) }, enabled = !busy) { Text("跳过") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onStatus(
                            if (workout.status == SessionStatus.ACTIVE) SessionStatus.PAUSED
                            else SessionStatus.ACTIVE
                        )
                    },
                    enabled = !busy,
                ) {
                    Text(if (workout.status == SessionStatus.ACTIVE) "暂停训练" else "恢复训练")
                }
                Button(onClick = { confirmation = SessionStatus.COMPLETED }, enabled = !busy) {
                    Text("结束训练")
                }
            }
        }
        workout.exercises.forEach { exercise ->
            InfoCard(exercise.name) {
                Text("动作间休息 ${exercise.restSeconds} 秒；重量单位 kg")
                exercise.sets.forEach { set ->
                    key(set.id) { SetEditor(set, editable && !busy, onSave) }
                }
                if (workout.status in listOf(SessionStatus.ACTIVE, SessionStatus.PAUSED))
                    TextButton(onClick = { onWarmup(exercise.id) }, enabled = !busy) {
                        Text("添加热身组")
                    }
            }
        }
        var note by rememberSaveable(workout.id, workout.note) { mutableStateOf(workout.note) }
        OutlinedTextField(
            note,
            { note = it.take(2000) },
            label = { Text("训练备注") },
            modifier = Modifier.fillMaxWidth(),
            enabled = editable,
        )
        OutlinedButton(onClick = { onNote(note) }, enabled = editable && !busy) { Text("保存备注") }
        if (workout.status in listOf(SessionStatus.ACTIVE, SessionStatus.PAUSED))
            TextButton(onClick = { confirmation = SessionStatus.ABANDONED }, enabled = !busy) {
                Text("放弃本次训练…")
            }
        if (workout.status == SessionStatus.COMPLETED) A1Greeting(true)
        Spacer(Modifier.height(24.dp))
    }
    confirmation?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (target == SessionStatus.COMPLETED) "确认结束训练？" else "确认放弃训练？") },
            text = {
                Text(
                    "已保存 ${workout.exercises.sumOf { e -> e.sets.count { it.completedAt != null } }} 组。未点击保存的输入不会提交；已保存记录将保留。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmation = null
                        onStatus(target)
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("返回训练") } },
        )
    }
}

@Composable
private fun SetEditor(set: WorkoutSet, enabled: Boolean, onSave: (SetInput) -> Unit) {
    var weight by rememberSaveable(set.id, set.weightGrams) { mutableStateOf(kg(set.weightGrams)) }
    var reps by
        rememberSaveable(set.id, set.reps) { mutableStateOf(set.reps?.toString().orEmpty()) }
    var rir by rememberSaveable(set.id, set.rir) { mutableStateOf(set.rir?.toString().orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "第 ${set.order + 1} 组 · ${if (set.kind == SetKind.WARMUP) "热身" else "正式"} · ${if (set.completedAt != null) "已完成" else "未完成"}"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField(weight, { weight = it }, "重量 kg", Modifier.weight(1f), enabled)
            NumberField(reps, { reps = it }, "次数", Modifier.weight(1f), enabled)
            NumberField(rir, { rir = it }, "RIR 可选", Modifier.weight(1f), enabled)
        }
        Row {
            TextButton(
                enabled = enabled,
                onClick = {
                    onSave(
                        SetInput(
                            set.id,
                            weight,
                            reps,
                            rir,
                            set.completedAt != null,
                            set.kind == SetKind.WARMUP,
                        )
                    )
                },
            ) {
                Text("保存本组")
            }
            TextButton(
                enabled = enabled,
                onClick = {
                    onSave(
                        SetInput(
                            set.id,
                            weight,
                            reps,
                            rir,
                            set.completedAt == null,
                            set.kind == SetKind.WARMUP,
                        )
                    )
                },
            ) {
                Text(if (set.completedAt == null) "完成并保存" else "撤销完成")
            }
        }
        HorizontalDivider()
    }
}

@Composable
fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value,
        { onChange(it.take(12)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun PlanEditor(
    day: PlanDay,
    onBack: () -> Unit,
    onSave: (MoveInput) -> Unit,
    onRemove: (String) -> Unit,
    onReorder: (String, Int) -> Unit,
    onDaySave: (String, Int) -> Unit,
) {
    var editing by remember { mutableStateOf<MoveInput?>(null) }
    var remove by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("返回") }
        SectionTitle("课表 · ${day.title}")
        var title by rememberSaveable(day.id, day.title) { mutableStateOf(day.title) }
        var weekday by
            rememberSaveable(day.id, day.weekday) { mutableStateOf(day.weekday.toString()) }
        OutlinedTextField(
            title,
            { title = it.take(80) },
            label = { Text("训练日名称") },
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField(weekday, { weekday = it }, "星期 1–7")
        OutlinedButton(
            onClick = { onDaySave(title, weekday.toInt()) },
            enabled = title.isNotBlank() && (weekday.toIntOrNull() ?: 0) in 1..7,
        ) {
            Text("保存训练日")
        }
        Text("编辑只影响后续训练，当前场次与历史快照保持不变。")
        day.moves.forEachIndexed { i, m ->
            InfoCard(m.exercise.name) {
                Text("${m.sets}组 × ${m.repMin}–${m.repMax}次 · 休息${m.restSeconds}秒")
                Row {
                    TextButton(
                        onClick = {
                            editing =
                                MoveInput(
                                    m.id,
                                    m.exercise.name,
                                    m.sets,
                                    m.repMin,
                                    m.repMax,
                                    m.restSeconds,
                                )
                        }
                    ) {
                        Text("编辑")
                    }
                    TextButton(onClick = { onReorder(m.id, -1) }, enabled = i > 0) { Text("上移") }
                    TextButton(
                        onClick = { onReorder(m.id, 1) },
                        enabled = i < day.moves.lastIndex,
                    ) {
                        Text("下移")
                    }
                    TextButton(onClick = { remove = m.id }) { Text("移除") }
                }
            }
        }
        Button(onClick = { editing = MoveInput(null, "", 3, 8, 12, 90) }) { Text("添加动作") }
    }
    remove?.let { id ->
        AlertDialog(
            onDismissRequest = { remove = null },
            title = { Text("从课表移除此动作？") },
            text = { Text("既有训练记录不会删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        remove = null
                        onRemove(id)
                    }
                ) {
                    Text("确认移除")
                }
            },
            dismissButton = { TextButton(onClick = { remove = null }) { Text("取消") } },
        )
    }
    editing?.let { input ->
        MoveDialog(input, { editing = null }) {
            editing = null
            onSave(it)
        }
    }
}

@Composable
private fun MoveDialog(input: MoveInput, onDismiss: () -> Unit, onSave: (MoveInput) -> Unit) {
    var name by remember { mutableStateOf(input.name) }
    var sets by remember { mutableStateOf(input.sets.toString()) }
    var min by remember { mutableStateOf(input.min.toString()) }
    var max by remember { mutableStateOf(input.max.toString()) }
    var rest by remember { mutableStateOf(input.rest.toString()) }
    val valid =
        name.isNotBlank() &&
            sets.toIntOrNull() in 1..12 &&
            min.toIntOrNull() in 1..1000 &&
            max.toIntOrNull() in (min.toIntOrNull() ?: 1)..1000 &&
            rest.toIntOrNull() in 0..3600
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("动作设置") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("动作名称") })
                NumberField(sets, { sets = it }, "组数 1–12")
                NumberField(min, { min = it }, "最少次数")
                NumberField(max, { max = it }, "最多次数")
                NumberField(rest, { rest = it }, "休息秒数 0–3600")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        MoveInput(
                            input.id,
                            name,
                            sets.toInt(),
                            min.toInt(),
                            max.toInt(),
                            rest.toInt(),
                        )
                    )
                },
                enabled = valid,
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
