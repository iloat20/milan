package com.milan.game.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 东方新中式 · 水墨进阶（设计语言 v4，2026-09）— **UI 权威调色板**。
 *
 * 概念：夜色砚台 + 宣纸。界面退后，立绘是唯一主角。
 * 朱砂 = 行动；金箔 = 仅稀有度/珍贵；青瓷 = 次操作。
 * 禁止：金线包一切、按钮切角环痕、第四层氛围色下渗控件。
 *
 * 旧名兼容：`Gold`/`Frost`/`BgDeepest` 等 API 保留，值已换芯为砚墨/朱砂/青瓷。
 */
object AppTheme {

    // ============================================================
    // 砚墨三阶（基底）
    // ============================================================

    /** 页面最深底（微青墨，非纯黑）。 */
    val BgDeepest = Color(0xFF0E100F)

    /** 卡片/面板基底。 */
    val BgMid = Color(0xFF161918)

    /** 嵌套面板基底。 */
    val SurfaceNested = Color(0xFF1E2220)

    /** 玻璃面底色（白 5%）。 */
    val Surface = Color(0x0DFFFFFF)

    // ── 朱砂系（主 CTA / 行动）──

    /** 朱砂主色（主按钮、选中、行动）。 */
    val ZhuSha = Color(0xFFC4453A)

    /** 朱砂高光 / 按下。 */
    val ZhuShaHi = Color(0xFFE06A5A)

    /** 朱砂深（按压底）。 */
    val ZhuShaDeep = Color(0xFF9A342C)

    /** 旧 API：熔金主强调 → 已退位为稀有度金；主 CTA 请用 [ZhuSha]。 */
    val Gold = Color(0xFFC9A96A)

    /** 金箔高光。 */
    val GoldHi = Color(0xFFE0C888)

    /** 金箔收尾。 */
    val GoldDeep = Color(0xFFA88848)

    /** 金底/朱砂底上的深色文字。 */
    val GoldTextOn = Color(0xFF1A1208)

    // ── 青瓷系（次级操作）──

    /** 青瓷（次级操作 / 导航未选中强调）。 */
    val Frost = Color(0xFF6B9E92)

    /** 青瓷深。 */
    val FrostDeep = Color(0xFF4E7A70)

    // ── 紫砚系（点缀）──

    /** 紫砚。 */
    val Violet = Color(0xFF8A7AB8)

    // ── 文字三级 ──

    /** 主文字（宣纸暖白）。 */
    val Text1 = Color(0xFFE8E4DC)

    /** 次文字。 */
    val Text2 = Color(0xFFA0A39C)

    /** 弱化 / 占位。 */
    val Text3 = Color(0xFF6A6E68)

    // ── 语义色 ──

    /** 危险 / 行动警示（与朱砂同源）。 */
    val Danger = Color(0xFFC4453A)

    /** 成功 / 正向。 */
    val Success = Color(0xFF6BA888)

    /** 警告 / 注意。 */
    val Warning = Color(0xFFC9A96A)

    // ── 装饰线 / 填充 ──

    /** 分隔发丝线（极淡，少用）。 */
    val Stroke = Color(0x1AFFFFFF)

    /** 印章红 / 行动色（= 朱砂）。 */
    val SealRed = Color(0xFFC4453A)

    /** 浮层底部铭牌渐变起笔。 */
    val ScrimTop = Color(0x000E100F)

    /** 浮层底部铭牌渐变收尾。 */
    val ScrimBottom = Color(0xBE0E100F)

    /** 武器加成绿（兼容）。 */
    val WoWGreen = Color(0xFF6BA888)

    /** 武器舞台圆角暗底。 */
    val WeaponStageBg = Color(0xFF121412)

    /** 属性面板渐变起笔。 */
    val WoWPanelTop = Color(0xFF1A1E1C)

    /** 属性面板渐变收尾。 */
    val WoWPanelBottom = Color(0xFF0E100F)

    /**
     * 稀有度四档。R 松烟 / SR 青瓷 / SSR 朱砂 / UR 金箔。
     * ⚠️ 全站唯一取色口；effects 层一律引用，禁止硬编码。
     */
    fun rarityColor(rarity: Int): Color = when (rarity) {
        1 -> Color(0xFF9AA39A)   // R   - 松烟
        2 -> Color(0xFF6B9E92)   // SR  - 青瓷
        3 -> Color(0xFFC4453A)   // SSR - 朱砂
        4 -> Color(0xFFC9A96A)   // UR  - 金箔
        else -> Text3
    }

    /** 稀有度发光色（与 [rarityColor] 同源）。 */
    fun rarityGlow(rarity: Int): Color = when (rarity) {
        1 -> Color(0x409AA39A)
        2 -> Color(0x556B9E92)
        3 -> Color(0x66C4453A)
        4 -> Color(0x77C9A96A)
        else -> Color(0x406A6E68)
    }

    /** 稀有度渐变（与 [rarityColor] 同源）。 */
    fun rarityGradient(rarity: Int): List<Color> = when (rarity) {
        1 -> listOf(Color(0xFF6A726A), Color(0xFF9AA39A))
        2 -> listOf(Color(0xFF4E7A70), Color(0xFF6B9E92))
        3 -> listOf(Color(0xFF9A342C), Color(0xFFC4453A))
        4 -> listOf(Color(0xFFA88848), Color(0xFFD4B878))
        else -> listOf(Color(0xFF6A726A), Color(0xFF9AA39A))
    }

    fun rarityName(rarity: Int): String = when (rarity) {
        3 -> "SSR"
        2 -> "SR"
        4 -> "UR"
        else -> "R"
    }

    // ── 间距 Token ──

    /** 间距 token：xs=4 / sm=8 / md=12 / lg=16 / xl=24 / xxl=32。 */
    object Spacing {
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 24.dp
        val xxl = 32.dp
    }

    // ── 圆角 Token ──

    /** 圆角 token：xxs=2 / xs=4 / sm=8 / md=12 / lg=16 / xl=20 / xxl=24。 */
    object Roundness {
        val xxs = 2.dp
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 20.dp
        val xxl = 24.dp
    }

    // ── 三世界 ambient（只染背景光，永不下渗按钮/文字/形状）──

    /** 神话界：夜宣 + 朱砂暖光。 */
    val Shinwa = WorldPalette(
        primary = Color(0xFFC4453A),
        secondary = Color(0xFFC9A96A),
        accent = Color(0xFF6B9E92),
        background = Color(0xFF0E100F),
        surface = Color(0xFF161918),
        textPrimary = Color(0xFFE8E4DC),
        textSecondary = Color(0xFFA0A39C),
        glow = Color(0xFFC4453A),
        particleColor = Color(0xFFC9A96A),
        stroke = Color(0xFF2A2E2B),
    )

    /** 苍穹界：深空 + 青紫。 */
    val Aether = WorldPalette(
        primary = Color(0xFF5A4A8A),
        secondary = Color(0xFF6B9E92),
        accent = Color(0xFFC4453A),
        background = Color(0xFF0C0E12),
        surface = Color(0xFF14161A),
        textPrimary = Color(0xFFE4E0F0),
        textSecondary = Color(0xFF98A0B0),
        glow = Color(0xFF7A6AB0),
        particleColor = Color(0xFF6B9E92),
        stroke = Color(0xFF2A2840),
    )

    /** 铁幕界：冷铁 + 铜。 */
    val Ironveil = WorldPalette(
        primary = Color(0xFF6A6E78),
        secondary = Color(0xFFB87840),
        accent = Color(0xFF6B9E92),
        background = Color(0xFF0E1012),
        surface = Color(0xFF181A1E),
        textPrimary = Color(0xFFE4E6EC),
        textSecondary = Color(0xFF9096A0),
        glow = Color(0xFFD07030),
        particleColor = Color(0xFFD07030),
        stroke = Color(0xFF4A5058),
    )
}

/**
 * 世界配色方案（C# WorldPalette 翻译）。
 */
data class WorldPalette(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val glow: Color,
    val particleColor: Color,
    val stroke: Color,
)

/** 世界装饰风格（C# WorldDecorationStyle）。 */
enum class WorldDecorationStyle { InkBrush, EnergyLines, Rivets }

/** 世界背景氛围（C# WorldBackground）。 */
enum class WorldBackground { Mythical, Cosmic, Industrial }

/**
 * 三世界视觉语言。
 * Shinwa = 夜宣丹青，Aether = 星辰虚空，Ironveil = 钢铁机关。
 */
object WorldTheme {

    /** 获取世界的主题（未知世界回退 Shinwa）。 */
    fun forWorld(world: String): WorldPalette = when (world) {
        "Aether" -> AppTheme.Aether
        "Ironveil" -> AppTheme.Ironveil
        else -> AppTheme.Shinwa
    }
}
