package com.milan.game.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
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
 * 水墨国风对话框：墨色渐变面板 + 缩放淡入弹出（240ms）。
 * 按钮区由调用方传 composable（全站统一 GoldButton/NeonButton）。
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
            dismissOnClickOutside = false,
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
            Box(Modifier.pointerInput(Unit) { detectTapGestures { } }) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(200)) +
                            slideInVertically(
                                initialOffsetY = { it / 8 },
                                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
                            ) +
                            scaleIn(initialScale = 0.95f, animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp)
                            .clip(RoundedCornerShape(AppTheme.Roundness.xl))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        AppTheme.BgMid.copy(alpha = 0.97f),
                                        AppTheme.BgDeepest.copy(alpha = 0.97f),
                                    ),
                                ),
                            )
                            .border(1.dp, AppTheme.Gold.copy(alpha = 0.2f), RoundedCornerShape(AppTheme.Roundness.xl))
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
