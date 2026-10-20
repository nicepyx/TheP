package com.thep.feature.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thep.core.designsystem.*
import com.thep.core.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ProgressScreen(data: Dashboard, stats: Statistics, onWorkout: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("训练进度")
        InfoCard("累计训练") {
            Text("${stats.completedSessions} 次完成 · ${"%.1f".format(stats.volumeKg)} kg·次")
            Text("统计仅使用已结束训练的已完成正式组。")
        }
        InfoCard("最近四周训练量") {
            val max = stats.weeklyVolumes.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
            stats.weeklyVolumes.forEach { (date, value) ->
                Text("$date 起 · ${"%.1f".format(value)} kg·次")
                LinearProgressIndicator(
                    progress = { (value / max).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        InfoCard("动作 PR 与估算 1RM") {
            Text(
                "1RM 使用 Epley 公式，仅计算 1–12 次的有效负重组；属于估算。",
                style = MaterialTheme.typography.bodySmall,
            )
            if (stats.records.isEmpty()) Text("完成训练后显示记录")
            stats.records.forEach {
                Text(
                    "${it.name}：最大 ${kg(it.maxWeightGrams)} kg · 估算1RM ${if (it.estimatedOneRepMaxGrams > 0) kg(it.estimatedOneRepMaxGrams) + " kg" else "数据不足"}"
                )
            }
        }
        SectionTitle("训练历史")
        if (data.workouts.isEmpty()) Text("还没有训练记录")
        data.workouts.forEach { w ->
            InfoCard(w.name) {
                w.trainingDate?.let { Text("训练日期：$it") }
                Text(
                    Instant.ofEpochMilli(w.startedAt)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                )
                Text(
                    "${when (w.status) { SessionStatus.ACTIVE -> "进行中"
 SessionStatus.PAUSED -> "已暂停"
 SessionStatus.COMPLETED -> "已完成"
 SessionStatus.ABANDONED -> "已放弃" }} · ${w.exercises.sumOf { e -> e.sets.count { it.completedAt != null } }} 组 · ${"%.1f".format(WorkoutRules.volumeKg(w.exercises.flatMap { it.sets }))} kg·次"
                )
                TextButton(onClick = { onWorkout(w.id) }) { Text("查看训练详情") }
            }
        }
        SectionTitle("身体记录")
        data.body.take(20).forEach {
            Text(
                "${Instant.ofEpochMilli(it.recordedAt).atZone(ZoneId.systemDefault()).toLocalDate()} · ${kg(it.weightGrams)} kg · 腰围 ${it.waistMillimeters?.let { mm -> "${mm / 10.0} cm" } ?: "未填写"}"
            )
        }
        if (data.body.isEmpty()) Text("在设置中添加体重与腰围")
        SectionTitle("有氧记录")
        data.cardio.take(20).forEach {
            Text(
                "${it.date} · ${if (it.kind == "stairs") "爬楼" else "快走"} · ${it.durationSeconds / 60} 分钟 / ${it.stairs} 层 · ${if (it.completed) "已完成" else "未完成"}"
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
