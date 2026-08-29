package com.milan.game.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 元素视觉身份（C# ElementTheme.For 翻译）：签名渐变 from→to、辉光色、文字 glyph。
 * 驱动每张角色卡的配色（立绘晕染 / 卡面渐变 / 稀有度标签）。
 * 水墨国风适配：色彩偏国画矿物颜料调性。
 */
data class ElementIdentity(
    val from: Color,
    val to: Color,
    val glow: Color,
    val glyph: String,
)

/**
 * 元素 → 视觉身份 的单一事实来源（水墨国风调性适配）。
 * 元素体系：金木水火土光暗电 8 种。
 */
object ElementTheme {

    private val Metal = ElementIdentity(Color(0xFFD4A853), Color(0xFFF0E0A0), Color(0xFFC89830), "金")
    private val Wood = ElementIdentity(Color(0xFF4A8A50), Color(0xFFB0D8A0), Color(0xFF60B868), "木")
    private val Water = ElementIdentity(Color(0xFF3A7A90), Color(0xFFA0D0D8), Color(0xFF50A8B8), "水")
    private val Flame = ElementIdentity(Color(0xFFC84040), Color(0xFFF0A050), Color(0xFFD85050), "火")
    private val Earth = ElementIdentity(Color(0xFF8A6A50), Color(0xFFD0C0A0), Color(0xFFA88860), "土")
    private val Light = ElementIdentity(Color(0xFFD4B040), Color(0xFFF8F0D0), Color(0xFFE8D060), "光")
    private val Shadow = ElementIdentity(Color(0xFF2A1840), Color(0xFF6A3880), Color(0xFF8848A0), "暗")
    private val Thunder = ElementIdentity(Color(0xFF308898), Color(0xFFA0D8E8), Color(0xFF40B8D0), "电")

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
