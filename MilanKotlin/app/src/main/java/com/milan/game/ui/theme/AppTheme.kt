package com.milan.game.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 暗夜神性·诸神黄昏 (Twilight of Gods) — 唯一主调色板（C# AppTheme 翻译，颜色单一事实来源）。
 * cosmic dark / 中西融合 / Obsidian&Gold / Cosmic Nebula 四套重叠，定义已收口到此。
 */
object AppTheme {

    /** 全局最底背景（暗紫夜·最深）。 */
    val BgDeepest = Color(0xFF0B0612)

    /** 次级背景 / 分区（暗紫夜·中）。 */
    val BgMid = Color(0xFF160A26)

    /** 玻璃面底色（#1E0E33 @ α≈0.55，140/255）。 */
    val Surface = Color(0x8C1E0E33)

    /** 熔金主色（线 / 点 / 字 / 当期强调）。 */
    val Gold = Color(0xFFE8B84B)

    /** 熔金高光（渐变起笔 / 高光点）。 */
    val GoldHi = Color(0xFFFFC857)

    /** 熔金收尾 / 按钮底边。 */
    val GoldDeep = Color(0xFFC9962E)

    /** 霜蓝（次级操作 / 信息 / 导航图标）。 */
    val Frost = Color(0xFF7FC4FF)

    /** 霜蓝深。 */
    val FrostDeep = Color(0xFF4A90D9)

    /** 暮紫（点缀 / 分隔 / 天赋节点 / 裂隙）。 */
    val Violet = Color(0xFF9A6BFF)

    /** 主文字。 */
    val Text1 = Color(0xFFF3ECFF)

    /** 次文字。 */
    val Text2 = Color(0xFFB7A6CF)

    /** 弱化 / 占位。 */
    val Text3 = Color(0xFF6E5C8A)

    /** 语义色：成功。 */
    val Success = Color(0xFF35D07F)

    /** 语义色：警告。 */
    val Warning = Color(0xFFFFB020)

    /** 语义色：危险 / 强调红。 */
    val Danger = Color(0xFFFF4D5E)

    /** 发丝描边（白 α08，20/255）：替代旧金线作为默认面板边。 */
    val Stroke = Color(0x14FFFFFF)

    /** 金底上的深色文字。 */
    val GoldTextOn = Color(0xFF3A2800)

    /** 印章点缀红（中西融合母题保留项）。 */
    val SealRed = Color(0xFFC8252A)

    /** 稀有度色板 — 诸神黄昏·东方 调性（UR 熔金 / SSR 暮紫 / SR 霜蓝 / R 苍白）。 */
    fun rarityColor(rarity: Int): Color = when (rarity) {
        1 -> Color(0xFFE8E2F2)   // R  - 苍白
        2 -> Color(0xFF7FC4FF)   // SR - 霜蓝
        3 -> Color(0xFFC79BFF)   // SSR - 暮紫
        4 -> Color(0xFFFFC857)   // UR - 熔金
        else -> Text3
    }

    fun rarityName(rarity: Int): String = when (rarity) {
        3 -> "SSR"
        2 -> "SR"
        4 -> "UR"
        else -> "R"
    }
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
 * 每个世界有自己的调色板与氛围，实现「混合渲染」承诺：
 * Shinwa = 赛璐璐水墨，Aether = 半写实暗黑星空，Ironveil = 硬表面机械。
 */
object WorldTheme {

    /** 获取世界的主题（未知世界回退 Shinwa）。 */
    fun forWorld(world: String): WorldPalette = when (world) {
        "Aether" -> Aether
        "Ironveil" -> Ironveil
        else -> Shinwa
    }

    /** 神话界：赛璐璐水墨。 */
    val Shinwa = WorldPalette(
        primary = Color(0xFFC41E3A),      // 朱砂红
        secondary = Color(0xFFD4AF37),    // 金箔
        accent = Color(0xFF1E90FF),       // 石青
        background = Color(0xFF0D0D1A),
        surface = Color(0xFF1A1520),
        textPrimary = Color(0xFFF5F0E8),  // 玉白
        textSecondary = Color(0xFFB8A080),
        glow = Color(0xFFFFD700),
        particleColor = Color(0xFFFFD700),
        stroke = Color(0xFF3A2A1A),
    )

    /** 虚空界：半写实暗黑宇宙。 */
    val Aether = WorldPalette(
        primary = Color(0xFF2D1B69),      // 虚空紫
        secondary = Color(0xFF4A90D9),    // 星云蓝
        accent = Color(0xFF8B0000),       // 暗红
        background = Color(0xFF0A0A14),
        surface = Color(0xFF12122A),
        textPrimary = Color(0xFFE8E0F0),  // 幽灵白
        textSecondary = Color(0xFF9090C0),
        glow = Color(0xFF6A0DAD),
        particleColor = Color(0xFF4A90D9),
        stroke = Color(0xFF2D1B69),
    )

    /** 铁幕界：硬表面机械。 */
    val Ironveil = WorldPalette(
        primary = Color(0xFF4A4A5A),      // 钢铁灰
        secondary = Color(0xFFB87333),    // 铜
        accent = Color(0xFF00BFFF),       // 电光蓝
        background = Color(0xFF1A1A20),
        surface = Color(0xFF252530),
        textPrimary = Color(0xFFE0E0E0),
        textSecondary = Color(0xFF8888A0),
        glow = Color(0xFFFF6B00),
        particleColor = Color(0xFFFF6B00),
        stroke = Color(0xFF4A4A5A),
    )
}
