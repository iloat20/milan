package com.milan.game.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.milan.game.ui.theme.AppTheme

/**
 * 玻璃拟态对话框（2026-08 UI 现代化）：替换设置页残留的原生 Material AlertDialog/TextButton。
 *
 * 视觉与全站同语言：暮紫夜渐变面板 + 缩放淡入弹出（240ms，与塔结算卡同节奏）；
 * 按钮区由调用方传 composable（全站统一 GoldButton/NeonButton，无 TextButton 残留）。
 * 关闭走系统 Back（dismissOnBackPress）与点击遮罩（tap 手势，无涟漪）。
 */
@Composable
fun GlassDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    buttons: @Composable () -> Unit,
) {
    if (!show) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false, // 遮罩 tap 由手势层处理（默认 true 时点击面板也会关闭）
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            // 面板自吞 tap 手势：命中面板时事件到此为止，不落穿遮罩误关
            Box(Modifier.pointerInput(Unit) { detectTapGestures { } }) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(240)) + scaleIn(initialScale = 0.92f, animationSpec = tween(240)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        AppTheme.BgMid.copy(alpha = 0.97f),
                                        AppTheme.BgDeepest.copy(alpha = 0.97f),
                                    ),
                                ),
                            )
                            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(18.dp))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.Text1,
                        )
                        Spacer(Modifier.height(10.dp))
                        // 长文本（如崩溃报告回显）限高内部滚动，不撑破屏
                        Box(
                            Modifier
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = body,
                                fontSize = 13.sp,
                                color = AppTheme.Text2,
                                lineHeight = 20.sp,
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        buttons()
                    }
                }
            }
        }
    }
}
