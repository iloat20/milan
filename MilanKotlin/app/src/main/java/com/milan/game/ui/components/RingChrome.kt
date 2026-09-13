package com.milan.game.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.milan.game.ui.theme.AppTheme

/**
 * v4 面板修饰：砚墨实底 + 极淡描边。
 * 默认无金边、无内双环；选中用 [ringChip] 或提高 Stroke。
 */
fun Modifier.ringPanel(
    stroke: Color = AppTheme.Stroke,
    innerAlpha: Float = 0f,
    fill: Color = AppTheme.BgMid,
    radius: Dp = AppTheme.Roundness.lg,
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .background(fill, shape)
        .border(1.dp, stroke, shape)
}

/** 次级 chip：胶囊；选中朱砂底。 */
fun Modifier.ringChip(selected: Boolean = false): Modifier {
    val shape = RoundedCornerShape(AppTheme.Roundness.xl)
    val stroke = if (selected) AppTheme.ZhuSha.copy(alpha = 0.7f) else AppTheme.Stroke
    val fill = if (selected) AppTheme.ZhuSha.copy(alpha = 0.18f) else AppTheme.BgMid
    return this
        .background(fill, shape)
        .border(1.dp, stroke, shape)
}
