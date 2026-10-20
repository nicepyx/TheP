package com.thep.feature.settings

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
import java.time.LocalDate

@Composable
fun SettingsScreen(
    autoRest: Boolean,
    onAutoRest: (Boolean) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    preview: ImportPreview?,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onBody: (String, String) -> Unit,
    onCardio: (String, String, String, String) -> Unit,
    busy: Boolean,
) {
    var weight by rememberSaveable { mutableStateOf("") }
    var waist by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var minutes by rememberSaveable { mutableStateOf("") }
    var stairs by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("walk") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("设置与数据")
        InfoCard("训练偏好") {
            Row {
                Text("完成一组后自动休息", modifier = Modifier.weight(1f))
                Switch(autoRest, onAutoRest, enabled = !busy)
            }
        }
        InfoCard("迁移与备份") {
            Text("V5 独立保存数据。请先在旧 App 导出 JSON 并保留原文件；不要卸载旧版。")
            Text("导入采用追加与冲突跳过，不覆盖既有场次。文件大小上限 10 MB。")
            Button(onClick = onImport, enabled = !busy) { Text("选择 JSON 并预览") }
            OutlinedButton(onClick = onExport, enabled = !busy) { Text("导出 The P 备份") }
            Text("备份包含私人训练信息及修改日志，请妥善保管。当前不自动上传。", style = MaterialTheme.typography.bodySmall)
        }
        InfoCard("添加身体记录") {
            Field(weight, { weight = it }, "体重 kg")
            Field(waist, { waist = it }, "腰围 cm，可空")
            Button(onClick = { onBody(weight, waist) }, enabled = !busy) { Text("保存身体记录") }
        }
        InfoCard("添加有氧记录") {
            OutlinedTextField(
                date,
                { date = it.take(10) },
                label = { Text("日期 YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                FilterChip(
                    selected = kind == "walk",
                    onClick = { kind = "walk" },
                    label = { Text("快走") },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = kind == "stairs",
                    onClick = { kind = "stairs" },
                    label = { Text("爬楼") },
                )
            }
            Field(minutes, { minutes = it }, "时长（分钟）")
            Field(stairs, { stairs = it }, "楼层数，可空")
            Button(onClick = { onCardio(date, kind, minutes, stairs) }, enabled = !busy) {
                Text("保存有氧记录")
            }
        }
        InfoCard("The P · 5.0 Alpha") {
            Text("Android 原生 · Kotlin / Compose / Room")
            Text("A1 奶霜丝带 · 静态素材展示")
            Text("云同步与 AI 尚未启用；后续 DeepSeek 通过安全后端接入。")
        }
        Spacer(Modifier.height(24.dp))
    }
    preview?.let { p ->
        AlertDialog(
            onDismissRequest = onCancelImport,
            title = { Text(if (p.alreadyImported) "该文件已导入" else "确认导入预览") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(p.source)
                    Text(
                        "源文件：${p.sessions} 个场次、${p.completedSets} 个完成组、${"%.1f".format(p.volumeKg)} kg·次、${p.body} 条身体记录、${p.cardio} 条有氧记录"
                    )
                    p.warnings.forEach { Text("• $it") }
                    Text("确认后写入 V5 数据库，旧 App 与原始文件保持不变。")
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmImport, enabled = !busy && !p.alreadyImported) {
                    Text("确认追加导入")
                }
            },
            dismissButton = { TextButton(onClick = onCancelImport) { Text("取消") } },
        )
    }
}

@Composable
private fun Field(value: String, change: (String) -> Unit, label: String) {
    OutlinedTextField(
        value,
        { change(it.take(12)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
