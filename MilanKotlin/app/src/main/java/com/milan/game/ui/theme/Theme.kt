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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.R

// 东方新中式 · 水墨进阶 v4 · Material 3 配色
//
// 砚墨基底 + 朱砂主 CTA + 青瓷次操作 + 金箔仅稀有度。
// 与 AppTheme.kt 单一事实来源对齐；游戏默认关闭动态取色。

private val InkColors = darkColorScheme(
    primary = AppTheme.ZhuSha,                  // 朱砂 = 主 CTA / 行动
    onPrimary = AppTheme.Text1,
    primaryContainer = Color(0xFF3A1C18),
    onPrimaryContainer = Color(0xFFFFD9D0),
    inversePrimary = AppTheme.ZhuShaDeep,

    secondary = AppTheme.Frost,                 // 青瓷 = 次操作
    onSecondary = Color(0xFF0A1614),
    secondaryContainer = Color(0xFF1A302C),
    onSecondaryContainer = Color(0xFFD0E8E0),

    tertiary = AppTheme.Gold,                   // 金箔 = 珍贵标识（非主 CTA）
    onTertiary = Color(0xFF1A1208),
    tertiaryContainer = Color(0xFF3A3020),
    onTertiaryContainer = Color(0xFFF3E8CF),

    background = AppTheme.BgDeepest,
    onBackground = AppTheme.Text1,

    surface = AppTheme.BgMid,
    onSurface = AppTheme.Text1,
    surfaceVariant = AppTheme.SurfaceNested,
    onSurfaceVariant = AppTheme.Text2,
    surfaceTint = AppTheme.ZhuSha,

    outline = Color(0xFF2A2E2B),
    outlineVariant = Color(0xFF1C201E),

    scrim = Color(0xFF000000),

    error = AppTheme.Danger,
    onError = AppTheme.Text1,
    errorContainer = Color(0xFF3A1410),
    onErrorContainer = Color(0xFFFFD9D0),
)

/**
 * 当前世界氛围调色板（三世界 ambient 层的唯一注入点）。
 * 世界调色板**只允许作用于背景氛围层**，永不下渗到按钮、文字、形状 token。
 */
val LocalWorldPalette = compositionLocalOf { AppTheme.Shinwa }

/**
 * 动态主题支持（Material You）。
 *
 * @param useDynamicColor 是否启用壁纸动态取色（默认关闭）
 * @param useDarkTheme 是否使用深色主题（默认深色）
 */
@Composable
fun MilanTheme(
    useDynamicColor: Boolean = false,
    useDarkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
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
 * 字号档位（0=标准 1=+10% 2=+20%）。
 * 经 LocalFontScaleTier 由 ready 门控后的 MilanNavHost 注入。
 */
val LocalFontScaleTier = compositionLocalOf { 0 }

/** 简化版主题入口（向后兼容）。 */
@Composable
fun MilanTheme(content: @Composable () -> Unit) {
    MilanTheme(useDynamicColor = false, useDarkTheme = true, content = content)
}

/**
 * 游戏形状体系：从 [AppTheme.Roundness] token 派生。
 */
val GameShapes = Shapes(
    extraSmall = RoundedCornerShape(AppTheme.Roundness.xs),
    small = RoundedCornerShape(AppTheme.Roundness.sm),
    medium = RoundedCornerShape(AppTheme.Roundness.md),
    large = RoundedCornerShape(AppTheme.Roundness.lg),
    extraLarge = RoundedCornerShape(AppTheme.Roundness.xl),
)

/**
 * 马善政毛笔楷书——仅品牌「织环」二字。
 * 楷书单一 Regular 字重，**禁止 FontWeight.Bold**。
 */
private val MaShanZheng = FontFamily(Font(R.font.ma_shan_zheng_regular))

/**
 * Noto Serif SC 可变字重——Display/Title 档。
 */
private val NotoSerifSC = FontFamily(
    Font(
        R.font.noto_serif_sc_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.noto_serif_sc_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

/** 品牌排印：仅「织环」Logo。 */
val BrandType = TextStyle(
    fontFamily = MaShanZheng,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = 2.sp,
)

/** 仪式标题（抽卡揭晓等 ritual 时刻的楷书大字）。 */
val RitualType = TextStyle(
    fontFamily = MaShanZheng,
    fontSize = 32.sp,
    lineHeight = 42.sp,
    letterSpacing = 4.sp,
)

/**
 * 排版体系 v4：
 * - Display/Title = Noto Serif SC（克制用）
 * - 正文/控件/数字 = 系统无衬线
 * - 楷书只保留 [BrandType] / [RitualType]
 *
 * UI 层新代码禁止裸 fontSize，一律走本档位。
 */
val GameTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = NotoSerifSC, fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp, lineHeight = 44.sp, fontFeatureSettings = "tnum",
    ),
    displayMedium = TextStyle(
        fontFamily = NotoSerifSC, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 38.sp, fontFeatureSettings = "tnum",
    ),
    displaySmall = TextStyle(
        fontFamily = NotoSerifSC, fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 34.sp, fontFeatureSettings = "tnum",
    ),
    headlineMedium = TextStyle(
        fontFamily = NotoSerifSC, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 28.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = NotoSerifSC, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = NotoSerifSC, fontWeight = FontWeight.Medium,
        fontSize = 18.sp, lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp,
        letterSpacing = 0.3.sp, fontFeatureSettings = "tnum",
    ),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
)
