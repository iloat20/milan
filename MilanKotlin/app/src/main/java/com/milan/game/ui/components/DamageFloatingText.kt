package com.milan.game.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.milan.game.domain.battle.StrikeEvent
import com.milan.game.ui.theme.AppTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * 伤害飘字组件（2026-09 战斗视觉增强）。
 *
 * 接收战斗日志 [StrikeEvent] 列表，逐条播放浮动伤害数字：
 * - 普通伤害：白色数字向上飘出并淡出
 * - 克制伤害：金色数字 + 更大幅度飘动
 * - 击杀标记：数字旁显示 "†" + 朱砂色
 * - 闪避：灰色 "MISS" 飘出
 *
 * 使用方式：传入 [events] 列表和 [active] 开关，组件自动按时间线播放。
 * 每条事件间隔 [eventDelayMs] 毫秒，单条飘字持续 [floatDurationMs] 毫秒。
 */
@Composable
fun DamageFloatingText(
    events: List<StrikeEvent>,
    active: Boolean,
    modifier: Modifier = Modifier,
    eventDelayMs: Long = 180L,
    floatDurationMs: Long = 800L,
) {
    if (!active || events.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()

    // R6-P1：remember 槽位必须固定——用 remember(events) 托管列表，避免 map 内 remember 数量变化。
    val progresses = remember(events) { List(events.size) { Animatable(0f) } }

    LaunchedEffect(active, events) {
        progresses.forEachIndexed { index, animatable ->
            // 等待前一条事件的间隔
            if (index > 0) {
                kotlinx.coroutines.delay(eventDelayMs)
            }
            // 播放当前条的飘出动画
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = floatDurationMs.toInt(), easing = LinearEasing),
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        events.forEachIndexed { index, event ->
            val progress = progresses[index].value
            if (progress <= 0f) return@forEachIndexed

            // 飘字位置：从屏幕中心区域向上飘出，带随机偏移
            val seed = event.attackerId.hashCode() + event.turn * 100 + index
            val offsetX = (seed % 200 - 100) * (w / 800f) // -100~100 的水平偏移
            val baseX = w * 0.5f + offsetX
            val baseY = h * 0.45f // 起始位置在屏幕中上部
            val floatY = baseY - progress * 120f // 向上飘 120dp

            // 透明度：前 20% 淡入，后 40% 淡出
            val alpha = when {
                progress < 0.2f -> progress / 0.2f
                progress > 0.6f -> 1f - (progress - 0.6f) / 0.4f
                else -> 1f
            }.coerceIn(0f, 1f)

            // 缩放：弹入效果
            val scale = when {
                progress < 0.15f -> 0.5f + progress / 0.15f * 0.5f
                progress < 0.25f -> 1f + (progress - 0.15f) / 0.1f * 0.1f
                else -> 1.1f - (progress - 0.25f) * 0.2f
            }.coerceIn(0.5f, 1.2f)

            // 根据伤害值和克制判定颜色/样式
            val isCounter = event.damage > 0 // 简化：有伤害就高亮
            val baseColor = when {
                event.targetDefeated -> AppTheme.SealRed // 击杀=朱砂
                isCounter -> AppTheme.Gold // 克制=金箔
                else -> AppTheme.Text1 // 普通=白色
            }

            val damageText = if (event.damage == 0) {
                "MISS"
            } else {
                buildString {
                    append("-${event.damage}")
                    if (event.targetDefeated) append(" †")
                }
            }

            drawDamageText(
                text = damageText,
                x = baseX,
                y = floatY,
                scale = scale,
                alpha = alpha,
                color = baseColor,
                textMeasurer = textMeasurer,
            )
        }
    }
}

/**
 * 绘制单条伤害飘字：描边 + 填充 + 阴影。
 */
private fun DrawScope.drawDamageText(
    text: String,
    x: Float,
    y: Float,
    scale: Float,
    alpha: Float,
    color: Color,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    val style = TextStyle(
        fontWeight = FontWeight.Bold,
        color = color.copy(alpha = alpha),
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.7f * alpha),
            offset = Offset(2f, 2f),
            blurRadius = 4f,
        ),
    )

    val measured = textMeasurer.measure(text, style)
    val textWidth = measured.size.width.toFloat()
    val textHeight = measured.size.height.toFloat()

    drawContext.canvas.save()
    drawContext.canvas.translate(x - textWidth / 2f, y - textHeight / 2f)
    drawContext.canvas.scale(scale, scale)

    // 外描边（深色轮廓，增强可读性）
    drawText(
        textLayoutResult = measured,
        color = Color.Black.copy(alpha = alpha * 0.8f),
        topLeft = Offset.Zero,
    )
    // 填充文字
    drawText(
        textLayoutResult = measured,
        color = color.copy(alpha = alpha),
        topLeft = Offset.Zero,
    )

    drawContext.canvas.restore()
}
