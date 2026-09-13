package com.milan.game.ui.battle

import androidx.compose.ui.graphics.Color

/**
 * 对弈台 · Battle HUD 独立主题。
 *
 * 与菜单 chrome **刻意分离**：战斗可读性优先——高对比、实底、敌我双色。
 * v4：Focus 改为朱砂行动色，与全站主 CTA 同源。
 */
object BattleTheme {
    val Stage0 = Color(0xFF0A0C0B)
    val Stage1 = Color(0xFF121614)
    val Stage2 = Color(0xFF181C1A)
    val Ally = Color(0xFF6B9E92)
    val AllyDeep = Color(0xFF3A5C56)
    val Enemy = Color(0xFFC4453A)
    val EnemyDeep = Color(0xFF6A2420)
    val Focus = Color(0xFFC4453A)
    val Text = Color(0xFFE8E4DC)
    val TextDim = Color(0xFFA0A39C)
    val Line = Color(0xFF2A2E2B)
    val HpHigh = Color(0xFF6BA888)
    val HpMid = Color(0xFFC9A96A)
    val HpLow = Color(0xFFC4453A)
    val Shield = Color(0xFF5A8AB8)

    /** HP 比例着色（高→中→低）。 */
    fun hpColor(ratio: Float): Color = when {
        ratio > 0.55f -> HpHigh
        ratio > 0.28f -> HpMid
        else -> HpLow
    }
}
