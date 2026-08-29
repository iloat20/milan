package com.milan.game.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 水墨国风·丹青录 (Ink Chronicle) — 唯一主调色板。
 * 素雅宣纸底 / 朱砂点缀 / 金箔高光 / 翠玉次级 / 墨色层次。
 * 替代旧「暗夜神性·诸神黄昏」暗紫+熔金调性。
 */
object AppTheme {

    // ── 墨色层次（背景 / 面板 / 分区）──

    /** 全局最底背景（浓墨·最深）。 */
    val BgDeepest = Color(0xFF0A0A0F)

    /** 次级背景 / 分区（淡墨·灰蓝）。 */
    val BgMid = Color(0xFF141620)

    /** 玻璃面底色（宣纸半透明）。 */
    val Surface = Color(0x8C1A1828)

    // ── 金箔系（主色 / 高光 / 收尾）──

    /** 金箔主色（线 / 点 / 字 / 当期强调）。 */
    val Gold = Color(0xFFD4A853)

    /** 金箔高光（渐变起笔 / 高光点）。 */
    val GoldHi = Color(0xFFF0C864)

    /** 金箔收尾 / 按钮底边。 */
    val GoldDeep = Color(0xFFB08930)

    // ── 石青系（次级操作 / 信息 / 导航图标）──

    /** 石青（淡蓝绿）。 */
    val Frost = Color(0xFF7EBAB1)

    /** 石青深。 */
    val FrostDeep = Color(0xFF5A9A90)

    // ── 紫砚系（点缀 / 分隔 / 天赋节点）──

    /** 紫砚（深紫点缀）。 */
    val Violet = Color(0xFF8A6BBD)

    // ── 文字层次 ──

    /** 主文字（宣纸白）。 */
    val Text1 = Color(0xFFF0E8D8)

    /** 次文字（淡墨灰）。 */
    val Text2 = Color(0xFFB0A898)

    /** 弱化 / 占位（WCAG AA 达标）。 */
    val Text3 = Color(0xFF787068)

    // ── 语义色 ──

    /** 语义色：成功（翠玉绿）。 */
    val Success = Color(0xFF5CB87A)

    /** 语义色：警告（琥珀）。 */
    val Warning = Color(0xFFD4A020)

    /** 语义色：危险 / 朱砂红。 */
    val Danger = Color(0xFFC84040)

    // ── 装饰线 / 填充 ──

    /** 发丝描边（白 α08）。 */
    val Stroke = Color(0x14FFFFFF)

    /** 金底上的深色文字。 */
    val GoldTextOn = Color(0xFF3A2800)

    /** 印章点缀红（国风母题）。 */
    val SealRed = Color(0xFFBF3A3A)

    /** 浮层底部铭牌渐变起笔（透明 → 墨色）。 */
    val ScrimTop = Color(0x000A0A0F)

    /** 浮层底部铭牌渐变收尾（墨色 α≈0.75）。 */
    val ScrimBottom = Color(0xBE080810)

    /** 嵌套玻璃面板底色（更深墨色 α≈0.59）。 */
    val SurfaceNested = Color(0x96161220)

    /** 武器加成绿。 */
    val WoWGreen = Color(0xFF5CBE80)

    /** 武器舞台圆角暗底。 */
    val WeaponStageBg = Color(0xFF0C0C14)

    /** 魔兽属性面板渐变起笔。 */
    val WoWPanelTop = Color(0xFF12101A)

    /** 魔兽属性面板渐变收尾。 */
    val WoWPanelBottom = Color(0xFF0A0810)

    /** 稀有度色板 — 丹青录调性（UR 金箔 / SSR 朱砂 / SR 石青 / R 素白）。 */
    fun rarityColor(rarity: Int): Color = when (rarity) {
        1 -> Color(0xFFD8D0C0)   // R  - 素白
        2 -> Color(0xFF7EBAB1)   // SR - 石青
        3 -> Color(0xFFC85050)   // SSR - 朱砂
        4 -> Color(0xFFF0C864)   // UR - 金箔
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
 * Shinwa = 水墨丹青，Aether = 星辰虚空，Ironveil = 钢铁机关。
 */
object WorldTheme {

    /** 获取世界的主题（未知世界回退 Shinwa）。 */
    fun forWorld(world: String): WorldPalette = when (world) {
        "Aether" -> Aether
        "Ironveil" -> Ironveil
        else -> Shinwa
    }

    /** 神话界：水墨丹青。 */
    val Shinwa = WorldPalette(
        primary = Color(0xFFBF3A3A),      // 朱砂红
        secondary = Color(0xFFD4A853),    // 金箔
        accent = Color(0xFF5A9A90),       // 石青
        background = Color(0xFF0D0D14),
        surface = Color(0xFF1A1820),
        textPrimary = Color(0xFFF0E8D8),  // 宣纸白
        textSecondary = Color(0xFFB0A080),
        glow = Color(0xFFD4A853),
        particleColor = Color(0xFFD4A853),
        stroke = Color(0xFF3A2A1A),
    )

    /** 虚空界：星辰虚空。 */
    val Aether = WorldPalette(
        primary = Color(0xFF2D1B50),      // 虚空紫
        secondary = Color(0xFF5A9A90),    // 星云青
        accent = Color(0xFF8A3030),       // 暗红
        background = Color(0xFF0A0A12),
        surface = Color(0xFF121228),
        textPrimary = Color(0xFFE8E0F0),  // 幽灵白
        textSecondary = Color(0xFF9090C0),
        glow = Color(0xFF6A3DAD),
        particleColor = Color(0xFF5A9A90),
        stroke = Color(0xFF2D1B50),
    )

    /** 铁幕界：钢铁机关。 */
    val Ironveil = WorldPalette(
        primary = Color(0xFF4A4A58),      // 钢铁灰
        secondary = Color(0xFFB87333),    // 铜
        accent = Color(0xFF7EBAB1),       // 电光青
        background = Color(0xFF1A1A20),
        surface = Color(0xFF252530),
        textPrimary = Color(0xFFE0E0E0),
        textSecondary = Color(0xFF8888A0),
        glow = Color(0xFFFF6B00),
        particleColor = Color(0xFFFF6B00),
        stroke = Color(0xFF4A4A5A),
    )
}
