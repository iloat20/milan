package com.milan.game.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.delay

/**
 * 通用 HP 条组件（2026-09 战斗视觉增强）。
 *
 * 功能：
 * - 渐变填充：绿→黄→红（按 HP 百分比自动着色）
 * - 受伤闪烁：HP 下降时红色闪光
 * - 护盾层：可选的蓝色护盾叠加层
 * - 数值显示：当前/最大 HP 文字
 *
 * @param currentHp 当前 HP
 * @param maxHp 最大 HP
 * @param shield 护盾值（0=无护盾）
 * @param showText 是否显示数值文字
 * @param barHeight 条高度
 * @param modifier 外部修饰符
 */
@Composable
fun HealthBar(
    currentHp: Int,
    maxHp: Int,
    shield: Int = 0,
    showText: Boolean = true,
    barHeight: Float = 12f,
    modifier: Modifier = Modifier,
) {
    val hpRatio = if (maxHp > 0) (currentHp.toFloat() / maxHp).coerceIn(0f, 1f) else 0f
    val shieldRatio = if (maxHp > 0) (shield.toFloat() / maxHp).coerceIn(0f, 0.5f) else 0f

    // 受伤闪烁状态
    var flashAlpha by remember { mutableFloatStateOf(0f) }
    var prevHp by remember { mutableStateOf(currentHp) }

    LaunchedEffect(currentHp) {
        if (currentHp < prevHp) {
            flashAlpha = 0.6f
            delay(150)
            flashAlpha = 0f
        }
        prevHp = currentHp
    }

    // 平滑填充动画
    val animatedRatio by animateFloatAsState(
        targetValue = hpRatio,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "hpFill",
    )

    if (showText) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier,
        ) {
            HPBarCanvas(
                hpRatio = animatedRatio,
                shieldRatio = shieldRatio,
                flashAlpha = flashAlpha,
                barHeight = barHeight,
                modifier = Modifier
                    .weight(1f)
                    .height(barHeight.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$currentHp/$maxHp",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Text2,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            if (shield > 0) {
                Text(
                    text = " +$shield",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Frost,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
            }
        }
    } else {
        HPBarCanvas(
            hpRatio = animatedRatio,
            shieldRatio = shieldRatio,
            flashAlpha = flashAlpha,
            barHeight = barHeight,
            modifier = modifier
                .fillMaxWidth()
                .height(barHeight.dp),
        )
    }
}

/**
 * HP 条 Canvas 绘制。
 */
@Composable
private fun HPBarCanvas(
    hpRatio: Float,
    shieldRatio: Float,
    flashAlpha: Float,
    barHeight: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(barHeight.dp / 2))) {
        val w = size.width
        val h = size.height
        val cornerRadius = CornerRadius(h / 2, h / 2)

        // 背景（暗底）
        drawRoundRect(
            color = AppTheme.BgDeepest,
            size = Size(w, h),
            cornerRadius = cornerRadius,
        )

        // HP 填充：绿→黄→红渐变
        val hpColor = hpBarColor(hpRatio)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(hpColor.copy(alpha = 0.9f), hpColor),
                startX = 0f,
                endX = w * hpRatio,
            ),
            size = Size(w * hpRatio, h),
            cornerRadius = cornerRadius,
        )

        // 护盾层（蓝色叠加）
        if (shieldRatio > 0f) {
            val shieldStart = hpRatio
            val shieldEnd = (hpRatio + shieldRatio).coerceAtMost(1f)
            drawRoundRect(
                color = AppTheme.Frost.copy(alpha = 0.5f),
                size = Size(w * (shieldEnd - shieldStart), h),
                topLeft = Offset(w * shieldStart, 0f),
                cornerRadius = cornerRadius,
            )
        }

        // 受伤闪烁（红色闪光覆盖）
        if (flashAlpha > 0f) {
            drawRoundRect(
                color = AppTheme.Danger.copy(alpha = flashAlpha),
                size = Size(w, h),
                cornerRadius = cornerRadius,
            )
        }

        // 高光（顶部发丝线）
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                startY = 0f,
                endY = h * 0.5f,
            ),
            size = Size(w, h * 0.5f),
            cornerRadius = cornerRadius,
        )

        // 边框
        drawRoundRect(
            color = AppTheme.Stroke,
            size = Size(w, h),
            cornerRadius = cornerRadius,
            style = Stroke(width = 1f),
        )
    }
}

/**
 * HP 条颜色：绿→黄→红（按百分比分段）。
 */
private fun hpBarColor(ratio: Float): Color = when {
    ratio > 0.6f -> AppTheme.Success // 绿色
    ratio > 0.3f -> AppTheme.Warning // 黄色
    else -> AppTheme.Danger // 红色
}

/**
 * 简化版 HP 条（无文字，纯条形）。
 */
@Composable
fun MiniHealthBar(
    currentHp: Int,
    maxHp: Int,
    modifier: Modifier = Modifier,
    barHeight: Float = 6f,
) {
    HealthBar(
        currentHp = currentHp,
        maxHp = maxHp,
        showText = false,
        barHeight = barHeight,
        modifier = modifier,
    )
}

/**
 * 带标签的 HP 条（用于战斗面板）。
 */
@Composable
fun LabeledHealthBar(
    label: String,
    currentHp: Int,
    maxHp: Int,
    shield: Int = 0,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = AppTheme.Text2,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "$currentHp/$maxHp",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Text1,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            if (shield > 0) {
                Text(
                    text = " 🛡$shield",
                    fontSize = 10.sp,
                    color = AppTheme.Frost,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        HealthBar(
            currentHp = currentHp,
            maxHp = maxHp,
            shield = shield,
            showText = false,
            barHeight = 8f,
        )
    }
}
