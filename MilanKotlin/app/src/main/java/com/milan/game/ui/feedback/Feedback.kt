package com.milan.game.ui.feedback

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * 统一反馈宿主（R3 / I4）。
 *
 * 以 [LocalFeedback] 向全树提供一次 Snackbar 入口，替代此前四处不一致写法：
 * - ShopScreen / SettingsScreen 自管的 `toast` state + `LaunchedEffect(toast)`；
 * - GachaScreen 直调 `Toast.makeText`；
 * - ProgressionScreen 局部的 `fun toast`。
 *
 * 宿主在 [com.milan.game.MainActivity.MilanNavHost] 用 [SnackbarHostState] 提供，
 * 配 AppTheme 配色；调用方取 [LocalFeedback] 即可展示反馈，不感知 Snackbar 细节。
 */
class Feedback(private val host: SnackbarHostState) {
    /** 展示一条反馈文案（挂起：内部委托 [SnackbarHostState.showSnackbar]）。 */
    suspend fun show(message: String) {
        host.showSnackbar(message)
    }
}

/** 全树共享的反馈宿主（必须在 MainActivity.MilanNavHost 的 CompositionLocalProvider 内使用）。 */
val LocalFeedback = compositionLocalOf<Feedback> {
    error("LocalFeedback 未提供：请在 MainActivity.MilanNavHost 的 CompositionLocalProvider 内使用")
}

/** 在 Composable 中获取当前反馈宿主（便捷委托）。 */
val LocalFeedbackProvider: Feedback
    @Composable
    get() = LocalFeedback.current
