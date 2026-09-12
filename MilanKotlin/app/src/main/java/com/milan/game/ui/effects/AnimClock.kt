package com.milan.game.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.isActive

/**
 * 组合时钟：仅在 Lifecycle RESUMED 时推进，避免 Gacha/主页 infinite 循环在后台空转。
 * 2026-09-12 性能报告 F5：多条 withFrameNanos 常亮会耗电并抢帧。
 */
@Composable
fun rememberAnimTime(): Float {
    val owner = LocalLifecycleOwner.current
    val lifecycle = owner.lifecycle
    var resumed by remember {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_PAUSE ||
                event == Lifecycle.Event.ON_STOP
            ) {
                resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        while (isActive && resumed) {
            withFrameNanos { nano -> time = nano / 1_000_000_000f }
        }
    }
    return time
}
