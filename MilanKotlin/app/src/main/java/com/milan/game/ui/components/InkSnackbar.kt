package com.milan.game.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.delay

/**
 * 水墨国风自定义 Snackbar — 4 种类型 + 自动消失 + 滑入/滑出动画。
 *
 * 类型：
 * - [SnackbarType.Info] — 冰蓝描边，普通提示
 * - [SnackbarType.Success] — 金箔描边，操作成功
 * - [SnackbarType.Warning] — 朱砂描边，警告
 * - [SnackbarType.Error] — 深红描边，错误
 *
 * 用法：
 * ```kotlin
 * val snackbar = rememberInkSnackbarState()
 * InkSnackbar(state = snackbar)
 * // 触发：
 * snackbar.show("操作成功", SnackbarType.Success)
 * ```
 */

enum class SnackbarType { Info, Success, Warning, Error }

@Composable
fun InkSnackbar(
    state: InkSnackbarState,
    modifier: Modifier = Modifier,
) {
    val message = state.message
    val type = state.type

    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(dampingRatio = 0.7f),
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = spring(dampingRatio = 0.7f),
        ) + fadeOut(),
    ) {
        if (message != null) {
            val borderColor = when (type) {
                SnackbarType.Info -> AppTheme.Frost
                SnackbarType.Success -> AppTheme.Gold
                SnackbarType.Warning -> AppTheme.SealRed
                SnackbarType.Error -> AppTheme.Danger
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppTheme.Surface)
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 类型图标
                val icon = when (type) {
                    SnackbarType.Info -> "ℹ"
                    SnackbarType.Success -> "✓"
                    SnackbarType.Warning -> "⚠"
                    SnackbarType.Error -> "✕"
                }
                Text(
                    text = icon,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = borderColor,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = AppTheme.Text1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * InkSnackbar 状态持有者 — 管理消息队列与自动消失。
 */
class InkSnackbarState {
    var message by mutableStateOf<String?>(null)
        private set
    var type by mutableStateOf(SnackbarType.Info)
        private set

    fun show(message: String, type: SnackbarType = SnackbarType.Info) {
        this.message = message
        this.type = type
    }

    fun dismiss() {
        message = null
    }
}

@Composable
fun rememberInkSnackbarState(): InkSnackbarState {
    val state = remember { InkSnackbarState() }
    // 自动消失：3 秒后清除
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(3000L)
            state.dismiss()
        }
    }
    return state
}
