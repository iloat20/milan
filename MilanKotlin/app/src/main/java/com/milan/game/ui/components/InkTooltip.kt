package com.milan.game.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.theme.AppTheme
import kotlin.math.roundToInt

/**
 * 水墨国风长按提示气泡 — 长按触发，松手消失。
 *
 * 视觉：墨色玻璃底 + 金箔描边 + 三角形指针（指向触发元素）。
 *
 * 用法：
 * ```kotlin
 * InkTooltip(text = "长按查看详情") {
 *     Text("触发元素", modifier = Modifier)
 * }
 * ```
 */
@Composable
fun InkTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var showTooltip by remember { mutableStateOf(false) }
    var tooltipPosition by remember { mutableStateOf(Offset.Zero) }
    var contentSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Box(modifier = modifier) {
        // 触发元素：长按显示，松手隐藏
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { offset ->
                            tooltipPosition = offset
                            showTooltip = true
                        },
                        onPress = {
                            tryAwaitRelease()
                            showTooltip = false
                        },
                    )
                }
                .onGloballyPositioned { coordinates ->
                    contentSize = coordinates.size
                },
        ) {
            content()
        }

        // 气泡提示
        AnimatedVisibility(
            visible = showTooltip,
            enter = scaleIn(
                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                initialScale = 0.8f,
            ) + fadeIn(),
            exit = scaleOut(
                animationSpec = spring(dampingRatio = 0.8f),
                targetScale = 0.8f,
            ) + fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        // 气泡居中于触发点上方
                        IntOffset(
                            x = (tooltipPosition.x - 60.dp.toPx()).roundToInt(),
                            y = (tooltipPosition.y - 50.dp.toPx()).roundToInt(),
                        )
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppTheme.Surface)
                    .border(1.dp, AppTheme.Gold.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .drawBehind {
                        // 底部三角形指针
                        val triSize = 8.dp.toPx()
                        val triPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width / 2f - triSize, 0f)
                            lineTo(size.width / 2f, triSize)
                            lineTo(size.width / 2f + triSize, 0f)
                            close()
                        }
                        drawPath(triPath, color = AppTheme.Surface)
                        drawPath(
                            triPath,
                            color = AppTheme.Gold.copy(alpha = 0.6f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppTheme.Text1,
                )
            }
        }
    }
}
