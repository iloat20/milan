package com.milan.game.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * 水墨墨溅效果 Modifier：观察外部 [interactionSource] 的 Press/Release 事件，
 * 按下时从组件中心扩散金色半透明圆形波纹，释放后淡出。
 *
 * **不拦截手势**——所有触摸事件由上游 [androidx.compose.foundation.clickable] 处理，
 * 本 Modifier 仅做 drawBehind 绘制。
 *
 * 用法：
 * ```
 * val interactionSource = remember { MutableInteractionSource() }
 * Box(
 *     modifier = Modifier
 *         .inkSplash(interactionSource)
 *         .clickable(interactionSource = interactionSource, indication = null) { onClick() }
 * )
 * ```
 */
fun Modifier.inkSplash(interactionSource: MutableInteractionSource): Modifier = composed {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var radius by remember { mutableFloatStateOf(0f) }
    var alpha by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    radius = 0f
                    alpha = 0.15f
                    scope.launch {
                        val maxRadius = with(density) { 80.dp.toPx() }
                        Animatable(0f).animateTo(maxRadius, tween(300)) {
                            radius = this.value
                        }
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    scope.launch {
                        Animatable(alpha).animateTo(0f, tween(200)) {
                            alpha = this.value
                        }
                    }
                }
            }
        }
    }

    this.drawBehind {
        if (alpha > 0f) {
            drawCircle(
                color = AppTheme.Gold.copy(alpha = alpha),
                radius = radius,
            )
        }
    }
}

/**
 * 水墨按压 Modifier：观察外部 [interactionSource] 的 Press/Release 事件，
 * 按下时 scale 0.96 → 释放弹回 1.0（spring 弹簧）。
 *
 * **不拦截手势**——所有触摸事件由上游 [androidx.compose.foundation.clickable] 处理。
 *
 * 用法：
 * ```
 * val interactionSource = remember { MutableInteractionSource() }
 * Box(
 *     modifier = Modifier
 *         .inkPress(interactionSource)
 *         .clickable(interactionSource = interactionSource, indication = null) { onClick() }
 * )
 * ```
 */
fun Modifier.inkPress(interactionSource: MutableInteractionSource): Modifier = composed {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    scope.launch {
                        scale.animateTo(
                            targetValue = 0.96f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        )
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    scope.launch {
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        )
                    }
                }
            }
        }
    }

    this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
