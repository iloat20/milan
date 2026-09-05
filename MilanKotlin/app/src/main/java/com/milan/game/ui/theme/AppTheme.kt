package com.milan.game.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 云海仙气·丹青录 (Celestial Cloud Chronicle) — **UI 权威调色板**。
 * 云海蓝底 / 冰蓝面板 / 金箔高光 / 石青次级 / 仙气留白。
 *
 * v2（2026-09-05）：从「水墨国风·浓墨底」切换到「云海仙气·蓝底渐变」——
 * 背景从近纯黑 (#0A0A0F) 提升到深靛蓝 (#0F1428)，面板从暗紫切换到冰蓝半透明，
 * 整体色调明亮通透，营造仙境云海的空灵感。
 */
object AppTheme {

    // ============================================================
    // 一、云海色板（当前权威 · 全站 UI 使用）
    // ============================================================

    // ── 云海层次（背景 / 面板 / 分区）──

    /** 全局最底背景（深靛·云海底）。 */
    val BgDeepest = Color(0xFF0F1428)

    /** 次级背景 / 分区（中靛·云海中层）。 */
    val BgMid = Color(0xFF1A2040)

    /** 玻璃面底色（冰蓝半透明）。 */
    val Surface = Color(0x8C1E2848)

    /** 嵌套玻璃面板底色（更深冰蓝 α≈0.59）。 */
    val SurfaceNested = Color(0x96182038)

    // ── 金箔系（主色 / 高光 / 收尾）──

    /** 金箔主色（仙气提亮）。 */
    val Gold = Color(0xFFE0B860)

    /** 金箔高光（渐变起笔 / 高光点）。 */
    val GoldHi = Color(0xFFF8D878)

    /** 金箔收尾 / 按钮底边。 */
    val GoldDeep = Color(0xFFC09838)

    /** 金底上的深色文字。 */
    val GoldTextOn = Color(0xFF2A1800)

    // ── 石青系（次级操作 / 信息 / 导航图标）──

    /** 石青（仙气提亮）。 */
    val Frost = Color(0xFF8EC8C0)

    /** 石青深。 */
    val FrostDeep = Color(0xFF68B0A8)

    // ── 紫砚系（点缀 / 分隔 / 天赋节点）──

    /** 紫砚。 */
    val Violet = Color(0xFF9A7BD0)

    // ── 文字层次 ──

    /** 主文字（云间白）。 */
    val Text1 = Color(0xFFF0ECF0)

    /** 次文字（淡蓝灰）。 */
    val Text2 = Color(0xFFB8C0D0)

    /** 弱化 / 占位（WCAG AA 达标）。 */
    val Text3 = Color(0xFF7880A0)

    // ── 语义色 ──

    /** 危险 / 失败（朱砂）。 */
    val Danger = Color(0xFFD05050)

    /** 成功 / 正向（翠玉）。⚠️ 3 处调用点（WoWStatsPanel ×2 / InfoPanels ×1）依赖此口径。 */
    val Success = Color(0xFF60C888)

    /** 警告 / 注意（琥珀金）。⚠️ 同上。 */
    val Warning = Color(0xFFE0B030)

    // ── 装饰线 / 填充 ──

    /** 分隔发丝线。 */
    val Stroke = Color(0x20FFFFFF)

    /** 印章点缀红（国风母题）。 */
    val SealRed = Color(0xFFD04848)

    /** 浮层底部铭牌渐变起笔（透明 → 云海蓝）。 */
    val ScrimTop = Color(0x000F1428)

    /** 浮层底部铭牌渐变收尾（云海蓝 α≈0.75）。 */
    val ScrimBottom = Color(0xBE101830)

    /** 武器加成绿。 */
    val WoWGreen = Color(0xFF60D090)

    /** 武器舞台圆角暗底。 */
    val WeaponStageBg = Color(0xFF121830)

    /** 魔兽属性面板渐变起笔。 */
    val WoWPanelTop = Color(0xFF161C38)

    /** 魔兽属性面板渐变收尾。 */
    val WoWPanelBottom = Color(0xFF0E1428)

    /** 稀有度色板 — 云海仙气调性（UR 金箔 / SSR 朱砂 / SR 石青 / R 月白）。
     *  ⚠️ 19 处调用点（抽卡/图鉴/卡组/详情/编队/首页/分享卡）依赖此口径，勿改调性。 */
    fun rarityColor(rarity: Int): Color = when (rarity) {
        1 -> Color(0xFFD0D8E8)   // R   - 月白
        2 -> Color(0xFF8EC8C0)   // SR  - 石青
        3 -> Color(0xFFD86060)   // SSR - 朱砂
        4 -> Color(0xFFF0D060)   // UR  - 金箔
        else -> Text3
    }

    /** 稀有度发光色（与 [rarityColor] 同源，保证仙气口径一致）。 */
    fun rarityGlow(rarity: Int): Color = when (rarity) {
        1 -> Color(0x55D0D8E8)
        2 -> Color(0x778EC8C0)
        3 -> Color(0x99D86060)
        4 -> Color(0xBBF0D060)
        else -> Color(0x557880A0)
    }

    /** 稀有度渐变（与 [rarityColor] 同源）。 */
    fun rarityGradient(rarity: Int): List<Color> = when (rarity) {
        1 -> listOf(Color(0xFFB0B8D0), Color(0xFFD0D8E8))
        2 -> listOf(Color(0xFF68B0A8), Color(0xFF8EC8C0))
        3 -> listOf(Color(0xFFA84040), Color(0xFFD86060))
        4 -> listOf(Color(0xFFE0B860), Color(0xFFF0D060))
        else -> listOf(Color(0xFFB0B8D0), Color(0xFFD0D8E8))
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

    // ── 三世界调色板（云海仙气体系，[WorldTheme.forWorld] 返回这些）──

    /** 神话界：云海仙宫。 */
    val Shinwa = WorldPalette(
        primary = Color(0xFFD04848),      // 朱砂红
        secondary = Color(0xFFE0B860),    // 金箔
        accent = Color(0xFF68B0A8),       // 石青
        background = Color(0xFF121830),
        surface = Color(0xFF1E2440),
        textPrimary = Color(0xFFF0ECF0),  // 云间白
        textSecondary = Color(0xFFB8C0D0),
        glow = Color(0xFFE0B860),
        particleColor = Color(0xFFE0B860),
        stroke = Color(0xFF2A3050),
    )

    /** 虚空界：星辰深渊。 */
    val Aether = WorldPalette(
        primary = Color(0xFF3A2868),      // 深空紫
        secondary = Color(0xFF68B0A8),    // 星云青
        accent = Color(0xFFB04848),       // 暗红
        background = Color(0xFF101428),
        surface = Color(0xFF1A2040),
        textPrimary = Color(0xFFE8E0F8),  // 幽灵白
        textSecondary = Color(0xFFA0A8D0),
        glow = Color(0xFF7A4DC0),
        particleColor = Color(0xFF68B0A8),
        stroke = Color(0xFF2A2050),
    )

    /** 铁幕界：钢铁机关。 */
    val Ironveil = WorldPalette(
        primary = Color(0xFF5A5A68),      // 钢铁灰
        secondary = Color(0xFFC88040),    // 铜
        accent = Color(0xFF8EC8C0),       // 电光青
        background = Color(0xFF181C28),
        surface = Color(0xFF222838),
        textPrimary = Color(0xFFE8E8F0),
        textSecondary = Color(0xFF9098B0),
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
