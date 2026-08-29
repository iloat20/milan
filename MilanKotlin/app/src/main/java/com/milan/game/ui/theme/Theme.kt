package com.milan.game.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 水墨国风 · Material 3 Expressive 配色
//
// 保留 MaterialExpressiveTheme + GameShapes 体系，色调从暗紫+熔金切换到墨色+金箔+朱砂：
//  - primary = 石青（淡蓝绿，水墨山水中常见的矿物色）
//  - secondary = 金箔（温暖的古金色，延续强调功能）
//  - tertiary = 朱砂（印章红，点缀）
//  - background / surface = 墨色层次

private val InkColors = darkColorScheme(
    primary = Color(0xFF5A9A90),              // 石青
    onPrimary = Color(0xFF0A1A18),
    primaryContainer = Color(0xFF2A4A44),
    onPrimaryContainer = Color(0xFFD0EDE8),
    inversePrimary = Color(0xFF8AD0C8),

    secondary = AppTheme.Gold,                // 金箔（单一事实来源）
    onSecondary = Color(0xFF3A2800),
    secondaryContainer = Color(0xFF4A3A1E),
    onSecondaryContainer = Color(0xFFF3E8CF),

    tertiary = Color(0xFFC85050),            // 朱砂
    onTertiary = Color(0xFF2A0A0A),
    tertiaryContainer = Color(0xFF4A1A1A),
    onTertiaryContainer = Color(0xFFFFD9D9),

    background = Color(0xFF0A0A0F),
    onBackground = Color(0xFFF0E8D8),

    surface = Color(0xFF141620),
    onSurface = Color(0xFFF0E8D8),
    surfaceVariant = Color(0xFF1E1C28),
    onSurfaceVariant = Color(0xFFB0A898),
    surfaceTint = Color(0xFF5A9A90),

    outline = Color(0xFF3A3840),
    outlineVariant = Color(0xFF1E1C28),

    scrim = Color(0xFF000000),

    error = Color(0xFFC84040),
    onError = Color(0xFF3A0808),
    errorContainer = Color(0xFF4A1414),
    onErrorContainer = Color(0xFFFFD9D9),
)

@Composable
fun MilanTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = InkColors,
        shapes = GameShapes,
        typography = GameTypography,
        content = content,
    )
}

/** 游戏形状体系：收敛全站圆角为五档 token。 */
val GameShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** 游戏排版体系：水墨国风的标题/正文/标签档位。 */
val GameTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 14.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 11.sp),
    labelSmall = TextStyle(fontSize = 10.sp),
)
