package com.milan.game.ui.effects

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 水墨滚动视差 Modifier：content 以 0.6x 速率滚动，背景以 0.8x 速率滚动，
 * 产生「宣纸层叠」视差深度感。
 *
 * @param scrollProgress 滚动进度 0f..1f（从 LazyListState.derivedStateOf 传入）
 * @param parallaxFactor 视差系数（越小视差越大），默认 0.6
 */
fun Modifier.parallaxScroll(
    scrollProgress: Float,
    parallaxFactor: Float = 0.6f,
): Modifier = this.graphicsLayer {
    translationY = -scrollProgress * size.height * (1f - parallaxFactor)
}

/**
 * 背景层视差 Modifier：以较快速率滚动，与 content 层配合产生深度感。
 */
fun Modifier.parallaxBackground(
    scrollProgress: Float,
    parallaxFactor: Float = 0.8f,
): Modifier = this.graphicsLayer {
    translationY = -scrollProgress * size.height * (1f - parallaxFactor)
}
