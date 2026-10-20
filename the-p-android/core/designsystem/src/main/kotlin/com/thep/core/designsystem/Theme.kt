package com.thep.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val colors =
    lightColorScheme(
        primary = Color(0xFF943D60),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD9E5),
        onPrimaryContainer = Color(0xFF3D1025),
        secondary = Color(0xFF735560),
        secondaryContainer = Color(0xFFFBDCE6),
        onSecondaryContainer = Color(0xFF2D1520),
        background = Color(0xFFFFF8FA),
        surface = Color(0xFFFFF8FA),
        onSurface = Color(0xFF291D22),
        surfaceVariant = Color(0xFFF2E0E6),
        onSurfaceVariant = Color(0xFF654954),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFFF4F8),
        surfaceContainer = Color(0xFFFFEFF5),
        surfaceContainerHigh = Color(0xFFFCE7EF),
        surfaceContainerHighest = Color(0xFFF8DFE9),
        surfaceDim = Color(0xFFF2DCE5),
        surfaceBright = Color(0xFFFFF8FA),
        outline = Color(0xFF9B7D88),
        outlineVariant = Color(0xFFE3C3D0),
        error = Color(0xFFBA1A1A),
    )

@Composable
fun ThePTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun BrandHeader() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.the_p_logo), "The P 已确认品牌标志", Modifier.size(56.dp))
        Column {
            Text(
                "The P",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text("认真训练，也温柔对待自己", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun A1Greeting(celebrating: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(
            painterResource(if (celebrating) R.drawable.a1_cheer else R.drawable.a1_idle),
            "A1 奶霜丝带",
            Modifier.size(68.dp),
        )
        Column {
            Text("奶霜丝带 · A1", fontWeight = FontWeight.Bold)
            Text(if (celebrating) "今天的努力已经好好记下啦。" else "从今天的一组开始，慢慢变强。")
        }
    }
}

fun kg(grams: Long?): String =
    grams
        ?.let { java.math.BigDecimal(it).movePointLeft(3).stripTrailingZeros().toPlainString() }
        .orEmpty()
