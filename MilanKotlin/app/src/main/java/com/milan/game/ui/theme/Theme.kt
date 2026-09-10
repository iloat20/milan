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
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milan.game.R

// 丹青典藏 v3 · Material 3 Expressive 配色
//
// 玄墨基底 + 金箔主强调 + 朱砂行动色 + 石青次操作。
// 与 AppTheme.kt 单一事实来源对齐；游戏默认关闭动态取色以保品牌一致性。

private val InkColors = darkColorScheme(
    primary = AppTheme.Gold,                     // 金箔 = 珍贵与度量 / 主 CTA
    onPrimary = AppTheme.GoldTextOn,
    primaryContainer = Color(0xFF3A3020),
    onPrimaryContainer = Color(0xFFF3E8CF),
    inversePrimary = AppTheme.GoldDeep,

    secondary = AppTheme.Frost,                  // 石青 = 次操作 / 导航
    onSecondary = Color(0xFF0A1A18),
    secondaryContainer = Color(0xFF1E3838),
    onSecondaryContainer = Color(0xFFD0EDE8),

    tertiary = AppTheme.SealRed,                 // 朱砂 = 行动 / 警示
    onTertiary = Color(0xFF2A0A0A),
    tertiaryContainer = Color(0xFF4A1A1A),
    onTertiaryContainer = Color(0xFFFFD9D9),

    background = AppTheme.BgDeepest,             // Ink0
    onBackground = AppTheme.Text1,

    surface = AppTheme.BgMid,                    // Ink1
    onSurface = AppTheme.Text1,
    surfaceVariant = AppTheme.SurfaceNested,     // Ink2
    onSurfaceVariant = AppTheme.Text2,
    surfaceTint = AppTheme.Gold,

    outline = Color(0xFF2A2E38),
    outlineVariant = Color(0xFF1C2028),

    scrim = Color(0xFF000000),

    error = AppTheme.Danger,
    onError = Color(0xFF2A0808),
    errorContainer = Color(0xFF4A1414),
    onErrorContainer = Color(0xFFFFD9D9),
)

/**
 * 当前世界氛围调色板（v3 §5.1：三世界 ambient 层的唯一注入点）。
 * 默认神话界；详情页/主页 Hero 用
 * `CompositionLocalProvider(LocalWorldPalette provides WorldTheme.forWorld(world)) { ... }`
 * 包裹子树即可换氛围。世界调色板**只允许作用于背景氛围层**（GalleryBackdrop /
 * 光晕/粒子），永不下渗到按钮、文字、形状 token。
 */
val LocalWorldPalette = compositionLocalOf { AppTheme.Shinwa }

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

    // 字号档位（设计语言 P3 §5.7）：经 LocalDensity.fontScale 全局放大 sp，
    // 不改 275 处裸 fontSize。0=标准 1=+10% 2=+20%。
    val fontScaleTier by com.milan.game.di.AppGraph.service.meta
        .collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val scale = when (fontScaleTier.fontScaleTier) {
        1 -> 1.1f
        2 -> 1.2f
        else -> 1f
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * scale,
        ),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = GameShapes,
            typography = GameTypography,
            content = content,
        )
    }
}

/**
 * 简化版主题入口（保持向后兼容）。
 * 游戏默认使用水墨国风配色，不启用动态取色。
 */
@Composable
fun MilanTheme(content: @Composable () -> Unit) {
    MilanTheme(useDynamicColor = false, useDarkTheme = true, content = content)
}

/**
 * 游戏形状体系：从 [AppTheme.Roundness] token 派生（单一事实来源，v3 §5.3）。
 * 历史：此处曾有独立的 6/10/14/16/20 第二套档位，与 Roundness token 矛盾，已废除。
 */
val GameShapes = Shapes(
    extraSmall = RoundedCornerShape(AppTheme.Roundness.xs),
    small = RoundedCornerShape(AppTheme.Roundness.sm),
    medium = RoundedCornerShape(AppTheme.Roundness.md),
    large = RoundedCornerShape(AppTheme.Roundness.lg),
    extraLarge = RoundedCornerShape(AppTheme.Roundness.xl),
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
 * Noto Serif SC 可变字重（res/font/noto_serif_sc_variable.ttf）——v3 §5.4 Display/Title 档字体。
 * U-02（2026-09-09）：此前该文件为**零引用死资源**，本次排印收敛激活。
 * 可变字体经 [FontVariation.weight] 实例化 500/600 两档；字库为全集 CJK，无楷书的缺字回退问题。
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

/**
 * 品牌排印（v3 §5.4：楷书仅两处——「丹青录」Logo 与抽卡仪式标题，不作页面标题用）。
 * 楷书为单一 Regular 字重，**禁止 FontWeight.Bold**（合成伪粗体笔锋糊团，见 U-01 记录）。
 */
val BrandType = TextStyle(
    fontFamily = MaShanZheng,
    fontSize = 26.sp,
    lineHeight = 34.sp,
    letterSpacing = 4.sp,
)

/** 仪式标题（抽卡揭晓等 ritual 时刻的楷书大字）。 */
val RitualType = TextStyle(
    fontFamily = MaShanZheng,
    fontSize = 34.sp,
    lineHeight = 44.sp,
    letterSpacing = 6.sp,
)

/**
 * 游戏排版体系 v3（§5.4 Type Scale，全站唯一档位）：
 *
 * - Display/Title 档 = Noto Serif SC（宋体骨架衬线，典藏气质）；
 * - 正文/控件/数字 = 系统无衬线，数字档带 `tnum` 等宽；
 * - 楷书只保留 [BrandType] / [RitualType] 两个品牌位（不再进 typography 档位）。
 *
 * 历史：v2 曾把楷书放进 headline/title 档（22/20/17sp），小字号笔画粘连且 275 处裸
 * fontSize 与档位互相脱节；v3 收敛后 UI 层新代码禁止裸 fontSize，一律走本档位。
 */
val GameTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = NotoSerifSC, fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp, lineHeight = 48.sp, fontFeatureSettings = "tnum",
    ),
    displayMedium = TextStyle(
        fontFamily = NotoSerifSC, fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp, lineHeight = 40.sp, fontFeatureSettings = "tnum",
    ),
    displaySmall = TextStyle(
        fontFamily = NotoSerifSC, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 36.sp, fontFeatureSettings = "tnum",
    ),
    headlineMedium = TextStyle(
        fontFamily = NotoSerifSC, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = NotoSerifSC, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 28.sp,
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
        letterSpacing = 0.5.sp, fontFeatureSettings = "tnum",
    ),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
)
