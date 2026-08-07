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

/** 元素 → 视觉身份 的单一事实来源（C# ElementTheme.cs 译文；未知元素回退 Flame）。 */
object ElementTheme {

    private val Flame = ElementIdentity(Color(0xFFFF4D00), Color(0xFFFFB300), Color(0xFFFF5252), "炎")
    private val Frost = ElementIdentity(Color(0xFF0097A7), Color(0xFFB2EBF2), Color(0xFF4DD0E1), "冰")
    private val Thunder = ElementIdentity(Color(0xFFFFD600), Color(0xFF7E57C2), Color(0xFFE040FB), "雷")
    private val Wind = ElementIdentity(Color(0xFF00C853), Color(0xFFB9F6CA), Color(0xFF69F0AE), "风")
    private val Shadow = ElementIdentity(Color(0xFF1A0033), Color(0xFF6A1B9A), Color(0xFF9C27B0), "暗")
    private val Light = ElementIdentity(Color(0xFFFFD600), Color(0xFFFFFDE7), Color(0xFFFDE7FF), "光")
    private val Earth = ElementIdentity(Color(0xFF795548), Color(0xFFD7CCC8), Color(0xFFA1887F), "土")
    private val Metal = ElementIdentity(Color(0xFF455A64), Color(0xFFB0BEC5), Color(0xFF78909C), "钢")
    private val Void = ElementIdentity(Color(0xFF0D0221), Color(0xFF6A0DAD), Color(0xFFCE93D8), "虚")
    private val Star = ElementIdentity(Color(0xFF1A237E), Color(0xFF7C4DFF), Color(0xFFB388FF), "星")

    fun forElement(element: String): ElementIdentity = when (element) {
        "Flame" -> Flame
        "Frost" -> Frost
        "Thunder" -> Thunder
        "Wind" -> Wind
        "Shadow" -> Shadow
        "Light" -> Light
        "Earth" -> Earth
        "Metal" -> Metal
        "Void" -> Void
        "Star" -> Star
        else -> Flame
    }
}
