package com.milan.game.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 元素视觉身份（C# ElementTheme.For 翻译）：签名渐变 from→to、辉光色、文字 glyph。
 * 驱动每张角色卡的配色（立绘晕染 / 卡面渐变 / 稀有度标签）。
 */
data class ElementIdentity(
    val from: Color,
    val to: Color,
    val glow: Color,
    val glyph: String,
)

/**
 * 元素 → 视觉身份 的单一事实来源（C# ElementTheme.cs 译文；未知元素回退 Flame）。
 * 元素体系（2026-08-11 定稿）：金木水火土光暗电 8 种；键名沿用旧数据标识，避免动序列化结构：
 * Metal=金 / Wood=木 / Water=水 / Flame=火 / Earth=土 / Light=光 / Shadow=暗 / Thunder=电；
 * 旧 Wind/Frost/Void/Star 已并入 Wood/Water/Shadow/Light，data.json 与 GameContent 兜底同步收敛。
 */
object ElementTheme {

    private val Metal = ElementIdentity(Color(0xFFFFD700), Color(0xFFFFF3C4), Color(0xFFFFC107), "金")
    private val Wood = ElementIdentity(Color(0xFF00C853), Color(0xFFB9F6CA), Color(0xFF69F0AE), "木")
    private val Water = ElementIdentity(Color(0xFF0097A7), Color(0xFFB2EBF2), Color(0xFF4DD0E1), "水")
    private val Flame = ElementIdentity(Color(0xFFFF4D00), Color(0xFFFFB300), Color(0xFFFF5252), "火")
    private val Earth = ElementIdentity(Color(0xFF795548), Color(0xFFD7CCC8), Color(0xFFA1887F), "土")
    private val Light = ElementIdentity(Color(0xFFFFD600), Color(0xFFFFFDE7), Color(0xFFFDE7FF), "光")
    private val Shadow = ElementIdentity(Color(0xFF1A0033), Color(0xFF6A1B9A), Color(0xFF9C27B0), "暗")
    private val Thunder = ElementIdentity(Color(0xFF00B8D4), Color(0xFFB3E5FC), Color(0xFF18FFFF), "电")

    fun forElement(element: String): ElementIdentity = when (element) {
        "Metal" -> Metal
        "Wood" -> Wood
        "Water" -> Water
        "Flame" -> Flame
        "Earth" -> Earth
        "Light" -> Light
        "Shadow" -> Shadow
        "Thunder" -> Thunder
        else -> Flame
    }
}
