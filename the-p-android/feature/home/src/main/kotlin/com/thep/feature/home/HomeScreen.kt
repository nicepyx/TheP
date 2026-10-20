package com.thep.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thep.core.designsystem.*
import com.thep.core.model.*
import java.time.LocalDate

@Composable
fun HomeScreen(
    data: Dashboard,
    stats: Statistics,
    onStart: (String) -> Unit,
    onResume: () -> Unit,
    onPlan: (String) -> Unit,
    onActivatePlan: (String) -> Unit,
    onCreatePlan: (String) -> Unit,
) {
    var newPlan by remember { mutableStateOf(false) }
    var planName by remember { mutableStateOf("") }
    if (newPlan)
        AlertDialog(
            onDismissRequest = { newPlan = false },
            title = { Text("新建训练计划") },
            text = {
                OutlinedTextField(
                    planName,
                    { planName = it.take(80) },
                    label = { Text("计划名称；将复制当前课表供编辑") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreatePlan(planName)
                        planName = ""
                        newPlan = false
                    },
                    enabled = planName.isNotBlank(),
                ) {
                    Text("创建")
                }
            },
            dismissButton = { TextButton(onClick = { newPlan = false }) { Text("取消") } },
        )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandHeader()
        A1Greeting()
        InfoCard("本周训练") {
            Text("${stats.weeklySessions} 次已完成 · 累计 ${stats.completedSessions} 次")
            Text("全部训练量 ${"%.1f".format(stats.volumeKg)} kg·次（正式组）")
        }
        data.active?.let { active ->
            InfoCard("继续未结束的训练") {
                Text(active.name)
                Button(onClick = onResume) { Text("继续训练") }
            }
        }
        val today = LocalDate.now().dayOfWeek.value
        SectionTitle("训练计划")
        data.plans.forEach { plan ->
            FilterChip(
                selected = plan.active,
                onClick = { onActivatePlan(plan.id) },
                label = { Text(plan.name + if (plan.active) " · 当前" else "") },
            )
        }
        TextButton(onClick = { newPlan = true }) { Text("新建计划") }
        if (today !in listOf(1, 2, 4, 6))
            Text(if (today == 3) "今天建议快走 30 分钟" else "今天以恢复为主，可按自身状态安排")
        if (today == 2 || today == 4) Text("晨起爬楼参考：10–15 层")
        data.days
            .filter { day -> data.plans.firstOrNull { it.active }?.id == day.planId }
            .forEach { day ->
                InfoCard(
                    "${if (day.weekday == today) "今日 · " else ""}周${listOf("一", "二", "三", "四", "五", "六", "日")[day.weekday - 1]} · ${day.title}"
                ) {
                    Text("${day.moves.size} 个动作 · ${day.moves.sumOf { it.sets }} 组")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onStart(day.id) }, enabled = day.moves.isNotEmpty()) {
                            Text(if (data.active == null) "开始训练" else "继续当前训练")
                        }
                        OutlinedButton(onClick = { onPlan(day.id) }) { Text("编辑课表") }
                    }
                }
            }
        Text("离线记录 · 数据仅保存在本机；请定期导出备份。", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
    }
}
