package com.milan.game.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

/**
 * 对弈台原生小按钮：实底战术片，禁玻璃/金丝（§7.4）。
 * 战斗内退出、确认等一律用本组件，不走菜单 GoldButton/InkButton。
 */
@Composable
fun BattleFlatChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    tone: Color = BattleTheme.TextDim,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(6.dp)
    val bg = when {
        pressed -> BattleTheme.Stage2
        active -> BattleTheme.Focus
        else -> BattleTheme.Stage1
    }
    val fg = if (active) BattleTheme.Stage0 else tone
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = fg,
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (active) BattleTheme.Focus else BattleTheme.Line, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** 对弈台主确认：朱砂实底，无金丝。 */
@Composable
fun BattlePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    !enabled -> BattleTheme.Stage2
                    pressed -> BattleTheme.EnemyDeep
                    else -> BattleTheme.Focus
                },
            )
            .border(
                1.dp,
                if (enabled) BattleTheme.Focus else BattleTheme.Line,
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) BattleTheme.Stage0 else BattleTheme.TextDim,
            letterSpacing = 2.sp,
        )
    }
}
