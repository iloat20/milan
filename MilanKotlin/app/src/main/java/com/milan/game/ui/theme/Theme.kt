package com.milan.game.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.R

// 云海仙气 · Material 3 Expressive 配色
//
// 保留 MaterialExpressiveTheme + GameShapes 体系，色调从墨色+金箔+朱砂切换到云海蓝+冰蓝+金箔：
//  - primary = 石青（冰蓝绿，仙境矿物色）
//  - secondary = 金箔（温暖的仙宫金色，延续强调功能）
//  - tertiary = 朱砂（印章红，点缀）
//  - background / surface = 云海蓝层次

private val InkColors = darkColorScheme(
    primary = Color(0xFF68B0A8),              // 石青（仙气提亮）
    onPrimary = Color(0xFF0A1A18),
    primaryContainer = Color(0xFF1E3838),
    onPrimaryContainer = Color(0xFFD0EDE8),
    inversePrimary = Color(0xFF90D8D0),

    secondary = AppTheme.Gold,                // 金箔（单一事实来源）
    onSecondary = Color(0xFF2A1800),
    secondaryContainer = Color(0xFF3A3020),
    onSecondaryContainer = Color(0xFFF3E8CF),

    tertiary = Color(0xFFD86060),            // 朱砂
    onTertiary = Color(0xFF2A0A0A),
    tertiaryContainer = Color(0xFF4A1A1A),
    onTertiaryContainer = Color(0xFFFFD9D9),

    background = Color(0xFF0F1428),          // 云海底
    onBackground = Color(0xFFF0ECF0),

    surface = Color(0xFF1A2040),             // 云海中层
    onSurface = Color(0xFFF0ECF0),
    surfaceVariant = Color(0xFF1E2848),      // 冰蓝面板
    onSurfaceVariant = Color(0xFFB8C0D0),
    surfaceTint = Color(0xFF68B0A8),

    outline = Color(0xFF2A3050),
    outlineVariant = Color(0xFF1E2848),

    scrim = Color(0xFF000000),

    error = Color(0xFFD05050),
    onError = Color(0xFF2A0808),
    errorContainer = Color(0xFF4A1414),
    onErrorContainer = Color(0xFFFFD9D9),
)

/**
 * 动态主题支持（Material You）。
 *
 * @param useDynamicColor 是否启用壁纸动态取色（默认关闭，游戏保留品牌一致性）
 * @param useDarkTheme 是否使用深色主题（默认深色，游戏沉浸感）
 * @param content 内容
 */
@Composable
fun MilanTheme(
    useDynamicColor: Boolean = false,
    useDarkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // 动态取色：Android 12+ 支持，从壁纸提取主题色
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        // 默认：水墨国风自定义配色
        else -> InkColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = GameShapes,
        typography = GameTypography,
        content = content,
    )
}

/**
 * 简化版主题入口（保持向后兼容）。
 * 游戏默认使用水墨国风配色，不启用动态取色。
 */
@Composable
fun MilanTheme(content: @Composable () -> Unit) {
    MilanTheme(useDynamicColor = false, useDarkTheme = true, content = content)
}

/** 游戏形状体系：收敛全站圆角为五档 token。 */
val GameShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/**
 * 马善政毛笔楷书（res/font/ma_shan_zheng_regular.ttf）。
 *
 * U-01（2026-08-30）：此前该字体文件虽已就位，但**全项目零引用**，所有文字走系统默认无衬线，
 * 「水墨」只剩配色与纹样、缺了字体这个国风识别度的主要载体。本次接入。
 *
 * ⚠️ 文件曾损坏：2026-08-28 提交的版本被截断到 1,212,416 字节（原 5,857,936，仅 20.7%），
 * fontTools 解析时报 `post` 表长度不符、表目录越界。已从上游重新下载完整版替换（SHA256 前 16 位 6d2546bb189c732a）。
 */
private val MaShanZheng = FontFamily(Font(R.font.ma_shan_zheng_regular))

/**
 * 游戏排版体系：水墨国风的标题/正文/标签档位。
 *
 * **仅大字号标题用楷书**——小字号（≤15sp）楷书笔画易粘连、可读性下降，故正文与标签沿用系统无衬线。
 * 楷书为单一 Regular 字重，标题**不再设 FontWeight.Bold**（否则触发合成伪粗体，笔锋糊成一团）。
 *
 * 字库覆盖：马善政含 GB2312 全集 6763 字；项目实际用字 1439 个中覆盖 1433 个（99.6%），
 * 缺 6 字（槃 / 蝟 / 話 / 跂 / 開 / 陣，多为繁体与生僻字）由系统字体自动回退。
 */
val GameTypography = Typography(
    headlineSmall = TextStyle(fontFamily = MaShanZheng, fontSize = 22.sp, letterSpacing = 3.sp),
    titleLarge = TextStyle(fontFamily = MaShanZheng, fontSize = 20.sp, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontFamily = MaShanZheng, fontSize = 17.sp, letterSpacing = 0.5.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 14.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 11.sp),
    labelSmall = TextStyle(fontSize = 10.sp),
)
