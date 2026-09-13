package com.milan.game.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 元素视觉身份：签名渐变 from→to、辉光色、文字 glyph。
 * v4：色彩收敛为矿物颜料，辉光强度整体压低，避免与 chrome 抢戏。
 */
data class ElementIdentity(
    val from: Color,
    val to: Color,
    val glow: Color,
    val glyph: String,
)

/**
 * 元素 → 视觉身份 的单一事实来源。
 * 元素体系：金木水火土光暗电 8 种。
 */
object ElementTheme {

    private val Metal = ElementIdentity(Color(0xFFB8924A), Color(0xFFD8C888), Color(0xFFC9A96A), "金")
    private val Wood = ElementIdentity(Color(0xFF4A7A50), Color(0xFF8AB888), Color(0xFF6BA870), "木")
    private val Water = ElementIdentity(Color(0xFF3A6A80), Color(0xFF7AABB8), Color(0xFF5A9AAA), "水")
    private val Flame = ElementIdentity(Color(0xFFB84038), Color(0xFFD88850), Color(0xFFC4453A), "火")
    private val Earth = ElementIdentity(Color(0xFF7A6050), Color(0xFFB0A088), Color(0xFF9A8060), "土")
    private val Light = ElementIdentity(Color(0xFFC9A96A), Color(0xFFE8E0C0), Color(0xFFD4B878), "光")
    private val Shadow = ElementIdentity(Color(0xFF2A1840), Color(0xFF5A3068), Color(0xFF7A4890), "暗")
    private val Thunder = ElementIdentity(Color(0xFF307888), Color(0xFF88C0D0), Color(0xFF40A0B8), "电")

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
