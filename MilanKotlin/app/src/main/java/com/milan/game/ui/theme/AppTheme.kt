package com.milan.game.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 丹青典藏 · Gilded Codex（设计语言 v3，2026-09-09）— **UI 权威调色板**。
 *
 * 概念：整个 App 是一座只陈列珍本的典藏馆——卡牌是文物，界面是展陈系统。
 * 范式：界面向卡牌供奉（水墨从装饰层退为馆内氛围层），与写实卡面立绘 v2.3 互为前提。
 *
 * 色彩三层：基底（玄墨）/ 材质面（玻璃）/ 强调（金箔 + 朱砂）。禁止第四层氛围色。
 * 全站唯一稀有度取色口：[rarityColor]（effects 层禁止硬编码）。
 *
 * 旧名兼容：`BgDeepest`/`BgMid`/`Surface`/`Frost` 等保留 API，值已换芯为玄墨系。
 */
object AppTheme {

    // ============================================================
    // 一、玄墨三阶（基底）· 替代 v2 深靛云海
    // ============================================================

    /** 页面最深底（Ink0，原 BgDeepest）。 */
    val BgDeepest = Color(0xFF0A0D14)

    /** 卡片/面板基底（Ink1，原 BgMid）。 */
    val BgMid = Color(0xFF10141C)

    /** 嵌套面板基底（Ink2）。 */
    val SurfaceNested = Color(0xFF161C27)

    /** 玻璃面底色（白 5% 量级，去冰蓝色偏）。 */
    val Surface = Color(0x0DFFFFFF)

    // ── 金箔系（珍贵与度量；不进正文）──

    /** 金箔主色。 */
    val Gold = Color(0xFFE0B860)

    /** 金箔高光。 */
    val GoldHi = Color(0xFFF8D878)

    /** 金箔收尾 / 按钮底边。 */
    val GoldDeep = Color(0xFFC09838)

    /** 金底上的深色文字。 */
    val GoldTextOn = Color(0xFF2A1800)

    // ── 石青系（次级操作 / 导航；v2 Frost API 保留）──

    /** 石青（次级操作）。 */
    val Frost = Color(0xFF68B0A8)

    /** 石青深。 */
    val FrostDeep = Color(0xFF4A9088)

    // ── 紫砚系（点缀 / 天赋节点）──

    /** 紫砚。 */
    val Violet = Color(0xFF9A7BD0)

    // ── 文字三级（Text2 on Ink1 ≥ 4.5:1；Text3 仅 Caption）──

    /** 主文字（微暖白，配金）。 */
    val Text1 = Color(0xFFF2EFE8)

    /** 次文字。 */
    val Text2 = Color(0xFFB8BCC8)

    /** 弱化 / 占位（仅 Caption）。 */
    val Text3 = Color(0xFF767C90)

    // ── 语义色 ──

    /** 危险 / 失败 / 行动警示（朱砂升格为行动色）。 */
    val Danger = Color(0xFFD04848)

    /** 成功 / 正向。 */
    val Success = Color(0xFF60C888)

    /** 警告 / 注意。 */
    val Warning = Color(0xFFE0B030)

    // ── 装饰线 / 填充 ──

    /** 分隔发丝线。 */
    val Stroke = Color(0x20FFFFFF)

    /** 印章点缀红 / 行动色（v3 升格）。 */
    val SealRed = Color(0xFFD04848)

    /** 浮层底部铭牌渐变起笔。 */
    val ScrimTop = Color(0x000A0D14)

    /** 浮层底部铭牌渐变收尾。 */
    val ScrimBottom = Color(0xBE0A0D14)

    /** 武器加成绿（兼容旧调用点）。 */
    val WoWGreen = Color(0xFF60D090)

    /** 武器舞台圆角暗底。 */
    val WeaponStageBg = Color(0xFF0E1218)

    /** 属性面板渐变起笔。 */
    val WoWPanelTop = Color(0xFF141820)

    /** 属性面板渐变收尾。 */
    val WoWPanelBottom = Color(0xFF0A0D14)

    /**
     * 稀有度四档（v3 §5.2 工艺分级，色沿用调性微调）。
     * R 松烟 / SR 石青 / SSR 朱砂 / UR 金箔。
     * ⚠️ 全站唯一取色口；effects 层一律引用，禁止硬编码。
     */
    fun rarityColor(rarity: Int): Color = when (rarity) {
        1 -> Color(0xFFC8D0DC)   // R   - 松烟
        2 -> Color(0xFF68B0A8)   // SR  - 石青
        3 -> Color(0xFFD86060)   // SSR - 朱砂
        4 -> Color(0xFFF0D060)   // UR  - 金箔
        else -> Text3
    }

    /** 稀有度发光色（与 [rarityColor] 同源）。 */
    fun rarityGlow(rarity: Int): Color = when (rarity) {
        1 -> Color(0x55C8D0DC)
        2 -> Color(0x7768B0A8)
        3 -> Color(0x99D86060)
        4 -> Color(0xBBF0D060)
        else -> Color(0x55767C90)
    }

    /** 稀有度渐变（与 [rarityColor] 同源）。 */
    fun rarityGradient(rarity: Int): List<Color> = when (rarity) {
        1 -> listOf(Color(0xFF9AA4B4), Color(0xFFC8D0DC))
        2 -> listOf(Color(0xFF4A9088), Color(0xFF68B0A8))
        3 -> listOf(Color(0xFFA84040), Color(0xFFD86060))
        4 -> listOf(Color(0xFFC09838), Color(0xFFF0D060))
        else -> listOf(Color(0xFF9AA4B4), Color(0xFFC8D0DC))
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

    // ── 圆角 Token（v3 §5.3 唯一档位；GameShapes 派生自此）──

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

    // ── 三世界 ambient（v3：骨架 token 不变，只换氛围层；永不下渗按钮/文字/形状）──

    /** 神话界：宣纸暗纹 + 朱砂金。 */
    val Shinwa = WorldPalette(
        primary = Color(0xFFD04848),
        secondary = Color(0xFFE0B860),
        accent = Color(0xFF68B0A8),
        background = Color(0xFF0C0E14),
        surface = Color(0xFF141820),
        textPrimary = Color(0xFFF2EFE8),
        textSecondary = Color(0xFFB8BCC8),
        glow = Color(0xFFE0B860),
        particleColor = Color(0xFFE0B860),
        stroke = Color(0xFF2A2830),
    )

    /** 苍穹界：星图玻璃暗调 + 紫青。 */
    val Aether = WorldPalette(
        primary = Color(0xFF3A2868),
        secondary = Color(0xFF68B0A8),
        accent = Color(0xFFB04848),
        background = Color(0xFF0A0C14),
        surface = Color(0xFF12141E),
        textPrimary = Color(0xFFE8E0F8),
        textSecondary = Color(0xFFA0A8C0),
        glow = Color(0xFF7A4DC0),
        particleColor = Color(0xFF68B0A8),
        stroke = Color(0xFF2A2850),
    )

    /** 铁幕界：拉丝金属暗调 + 钢铜。 */
    val Ironveil = WorldPalette(
        primary = Color(0xFF5A5A68),
        secondary = Color(0xFFC88040),
        accent = Color(0xFF8EC8C0),
        background = Color(0xFF0E1014),
        surface = Color(0xFF181C22),
        textPrimary = Color(0xFFE8E8F0),
        textSecondary = Color(0xFF9098A8),
        glow = Color(0xFFFF7800),
        particleColor = Color(0xFFFF7800),
        stroke = Color(0xFF505868),
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
 * 三世界视觉语言（C# WorldTheme 翻译）。
 * Shinwa = 宣纸丹青，Aether = 星辰虚空，Ironveil = 钢铁机关。
 */
object WorldTheme {

    /** 获取世界的主题（未知世界回退 Shinwa）。 */
    fun forWorld(world: String): WorldPalette = when (world) {
        "Aether" -> AppTheme.Aether
        "Ironveil" -> AppTheme.Ironveil
        else -> AppTheme.Shinwa
    }
}
