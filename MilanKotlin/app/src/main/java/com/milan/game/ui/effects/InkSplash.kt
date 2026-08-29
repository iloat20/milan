package com.milan.game.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * 水墨墨溅效果 Modifier：点击时从触摸点扩散金色半透明圆形波纹。
 * 用于 NeonButton、可点击卡片、Tab 项。
 */
fun Modifier.inkSplash(): Modifier = composed {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
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

    this.pointerInput(Unit) {
        detectTapGestures(
            onPress = { offset: Offset ->
                val press = PressInteraction.Press(offset)
                interactionSource.tryEmit(press)
                tryAwaitRelease()
                interactionSource.tryEmit(PressInteraction.Release(press))
            }
        )
    }.drawBehind {
        if (alpha > 0f) {
            drawCircle(
                color = AppTheme.Gold.copy(alpha = alpha),
                radius = radius,
            )
        }
    }
}
