package com.milan.game.ui.theme

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// twilight 暗夜主题 · Material 3 Expressive 刷新（Task 9 + M3 升级）
//
// 保留「暗夜神性·诸神黄昏」调性，套用 M3 Expressive 的形状/动效体系：
//  - MaterialExpressiveTheme 取代 MaterialTheme，自带 expressive motion scheme（更弹、更有情绪）。
//  - shapes 用标准 M3 Shapes（alpha22 无 ExpressiveShapes；表达性由 MotionScheme/形状默认值承担）。
//  - 配色方案补全 tertiary / container / outline 等 expressive 组件会用到的槽位，颜色仍来自 AppTheme 同套暮紫夜+熔金调性。
//
// 依赖：material3 已显式覆盖为 1.5.0-alpha22（libs.versions.toml）。该版本中 MaterialExpressiveTheme 已在
// 1.5.0-alpha18 毕业为非实验 API，故此处不再需要 @OptIn(ExperimentalMaterial3ExpressiveApi)。
// 注：ExpressiveShapes 是 alpha23+ 才引入（且会把 Compose 抬到 1.12.0-beta01），alpha22 用标准 Shapes() 即可。
// 注：1.5.0 仍为 alpha（截至 2026-08 无稳定版），属技术试验田取舍；待 1.5.0 稳定后可移除版本覆盖让 BOM 接管。
private val TwilightColors = darkColorScheme(
    primary = Color(0xFF8B7BD8),          // 暮紫主色
    onPrimary = Color(0xFF1A1530),
    primaryContainer = Color(0xFF3A2F66),
    onPrimaryContainer = Color(0xFFE8E4F2),
    inversePrimary = Color(0xFFC9B8FF),

    secondary = Color(0xFFD9A95C),        // 熔金
    onSecondary = Color(0xFF3A2800),
    secondaryContainer = Color(0xFF4A3A1E),
    onSecondaryContainer = Color(0xFFF3E8CF),

    tertiary = Color(0xFF9A6BFF),         // 暮紫点缀
    onTertiary = Color(0xFF1A0E33),
    tertiaryContainer = Color(0xFF2E1A52),
    onTertiaryContainer = Color(0xFFE9DEFF),

    background = Color(0xFF0F0D1A),
    onBackground = Color(0xFFE8E4F2),

    surface = Color(0xFF1A1730),
    onSurface = Color(0xFFE8E4F2),
    surfaceVariant = Color(0xFF2A2440),
    onSurfaceVariant = Color(0xFFB7A6CF),
    surfaceTint = Color(0xFF8B7BD8),

    outline = Color(0xFF4A3F66),
    outlineVariant = Color(0xFF2A2440),

    scrim = Color(0xFF000000),

    error = Color(0xFFFF4D5E),
    onError = Color(0xFF3A0008),
    errorContainer = Color(0xFF4A1018),
    onErrorContainer = Color(0xFFFFD9DC),
)

@Composable
fun MilanTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = TwilightColors,
        shapes = Shapes(),
        content = content,
    )
}
