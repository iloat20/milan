package com.milan.game.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 水墨国风·丹青录 (Ink Chronicle) — **UI 权威调色板**。
 * 素雅宣纸底 / 朱砂点缀 / 金箔高光 / 翠玉次级 / 墨色层次。
 */
object AppTheme {

    // ============================================================
    // 一、水墨色板（当前权威 · 31 个 UI 文件正在使用）
    // ============================================================

    // ── 墨色层次（背景 / 面板 / 分区）──

    /** 全局最底背景（浓墨·最深）。 */
    val BgDeepest = Color(0xFF0A0A0F)

    /** 次级背景 / 分区（淡墨·灰蓝）。 */
    val BgMid = Color(0xFF141620)

    /** 玻璃面底色（宣纸半透明）。 */
    val Surface = Color(0x8C1A1828)

    /** 嵌套玻璃面板底色（更深墨色 α≈0.59）。 */
    val SurfaceNested = Color(0x96161220)

    // ── 金箔系（主色 / 高光 / 收尾）──

    /** 金箔主色。 */
    val Gold = Color(0xFFD4A853)

    /** 金箔高光（渐变起笔 / 高光点）。 */
    val GoldHi = Color(0xFFF0C864)

    /** 金箔收尾 / 按钮底边。 */
    val GoldDeep = Color(0xFFB08930)

    /** 金底上的深色文字。 */
    val GoldTextOn = Color(0xFF3A2800)

    // ── 石青系（次级操作 / 信息 / 导航图标）──

    /** 石青。 */
    val Frost = Color(0xFF7EBAB1)

    /** 石青深。 */
    val FrostDeep = Color(0xFF5A9A90)

    // ── 紫砚系（点缀 / 分隔 / 天赋节点）──

    /** 紫砚。 */
    val Violet = Color(0xFF8A6BBD)

    // ── 文字层次 ──

    /** 主文字（宣纸白）。 */
    val Text1 = Color(0xFFF0E8D8)

    /** 次文字（淡墨灰）。 */
    val Text2 = Color(0xFFB0A898)

    /** 弱化 / 占位（WCAG AA 达标）。 */
    val Text3 = Color(0xFF787068)

    // ── 语义色 ──

    /** 危险 / 失败（朱砂）。 */
    val Danger = Color(0xFFC84040)

    /** 成功 / 正向（翠玉）。⚠️ 3 处调用点（WoWStatsPanel ×2 / InfoPanels ×1）依赖此口径。 */
    val Success = Color(0xFF5CB87A)

    /** 警告 / 注意（琥珀金）。⚠️ 同上。 */
    val Warning = Color(0xFFD4A020)

    // ── 装饰线 / 填充 ──

    /** 分隔发丝线。 */
    val Stroke = Color(0x14FFFFFF)

    /** 印章点缀红（国风母题）。 */
    val SealRed = Color(0xFFBF3A3A)

    /** 浮层底部铭牌渐变起笔（透明 → 墨色）。 */
    val ScrimTop = Color(0x000A0A0F)

    /** 浮层底部铭牌渐变收尾（墨色 α≈0.75）。 */
    val ScrimBottom = Color(0xBE080810)

    /** 武器加成绿。 */
    val WoWGreen = Color(0xFF5CBE80)

    /** 武器舞台圆角暗底。 */
    val WeaponStageBg = Color(0xFF0C0C14)

    /** 魔兽属性面板渐变起笔。 */
    val WoWPanelTop = Color(0xFF12101A)

    /** 魔兽属性面板渐变收尾。 */
    val WoWPanelBottom = Color(0xFF0A0810)

    /** 稀有度色板 — 丹青录调性（UR 金箔 / SSR 朱砂 / SR 石青 / R 素白）。
     *  ⚠️ 19 处调用点（抽卡/图鉴/卡组/详情/编队/首页/分享卡）依赖此口径，勿改调性。 */
    fun rarityColor(rarity: Int): Color = when (rarity) {
        1 -> Color(0xFFD8D0C0)   // R   - 素白
        2 -> Color(0xFF7EBAB1)   // SR  - 石青
        3 -> Color(0xFFC85050)   // SSR - 朱砂
        4 -> Color(0xFFF0C864)   // UR  - 金箔
        else -> Text3
    }

    /** 稀有度发光色（与 [rarityColor] 同源，保证水墨口径一致）。 */
    fun rarityGlow(rarity: Int): Color = when (rarity) {
        1 -> Color(0x44D8D0C0)
        2 -> Color(0x667EBAB1)
        3 -> Color(0x88C85050)
        4 -> Color(0xAAF0C864)
        else -> Color(0x44787068)
    }

    /** 稀有度渐变（与 [rarityColor] 同源）。 */
    fun rarityGradient(rarity: Int): List<Color> = when (rarity) {
        1 -> listOf(Color(0xFFA8A090), Color(0xFFD8D0C0))
        2 -> listOf(Color(0xFF5A9A90), Color(0xFF7EBAB1))
        3 -> listOf(Color(0xFF8A3030), Color(0xFFC85050))
        4 -> listOf(Color(0xFFD4A853), Color(0xFFF0C864))
        else -> listOf(Color(0xFFA8A090), Color(0xFFD8D0C0))
    }

    fun rarityName(rarity: Int): String = when (rarity) {
        3 -> "SSR"
        2 -> "SR"
        4 -> "UR"
        else -> "R"
    }

    // ── 间距 Token（统一全站间距节奏）──

    /** 间距 token：xs=4 / sm=8 / md=12 / lg=16 / xl=24 / xxl=32。 */
    object Spacing {
        val xs = 4.dp    // 紧凑内边距
        val sm = 8.dp    // 小间距
        val md = 12.dp   // 列表项间距
        val lg = 16.dp   // 段落间距
        val xl = 24.dp   // 大段间距
        val xxl = 32.dp  // 页面级间距
    }

    // ── 圆角 Token（统一全站圆角语言）──

    /** 圆角 token：sm=8 / md=12 / lg=16 / xl=20 / xxl=24。 */
    object Roundness {
        val sm = 8.dp    // 小元素（Chip / 小按钮）
        val md = 12.dp   // 中元素（按钮 / 卡片）
        val lg = 16.dp   // 大元素（面板 / GlassPanel）
        val xl = 20.dp   // NavBar / 弹窗面板
        val xxl = 24.dp  // GlassDialog
    }

    // ── 三世界调色板（水墨体系，[WorldTheme.forWorld] 返回这些）──

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
 *
 * ⚠️ 返回的是**水墨体系**调色板（[AppTheme.Shinwa] / [AppTheme.Aether] / [AppTheme.Ironveil]），
 * 与 [AppTheme] 当前权威色板一致。未来若启用「未来色板」，需同步切到 World* 三个实例。
 */
object WorldTheme {

    /** 获取世界的主题（未知世界回退 Shinwa）。 */
    fun forWorld(world: String): WorldPalette = when (world) {
        "Aether" -> AppTheme.Aether
        "Ironveil" -> AppTheme.Ironveil
        else -> AppTheme.Shinwa
    }
}
