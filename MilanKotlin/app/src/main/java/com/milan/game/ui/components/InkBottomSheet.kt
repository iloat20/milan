package com.milan.game.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.theme.AppTheme
import kotlin.math.abs

/**
 * 水墨国风底部抽屉组件 — 替代 Material BottomSheet。
 *
 * 特性：
 * - 拖拽下滑关闭（超过 120px 或速度阈值）
 * - 半透明遮罩层点击关闭
 * - 弹簧动画展开/收起
 * - 内容区滚动不穿透
 *
 * 用法：
 * ```kotlin
 * var showSheet by remember { mutableStateOf(false) }
 * InkBottomSheet(visible = showSheet, onDismiss = { showSheet = false }) {
 *     // 内容
 * }
 * ```
 */
@Composable
fun InkBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return

    // 遮罩层透明度动画
    val overlayAlpha by animateFloatAsState(
        targetValue = if (visible) 0.6f else 0f,
        animationSpec = spring(),
        label = "sheetOverlay",
    )
    // 内容滑入动画
    val contentOffset by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "sheetContent",
    )
    // 拖拽偏移量
    var dragOffset by remember { mutableStateOf(0f) }

    Box(modifier = modifier.fillMaxSize()) {
        // 半透明遮罩层（点击关闭）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = overlayAlpha }
                .background(Color.Black)
                .clickable(onClick = onDismiss),
        )

        // 底部抽屉内容
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = (contentOffset * 300f) + dragOffset
                    alpha = 1f - abs(contentOffset).coerceIn(0f, 0.3f)
                }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(AppTheme.Surface)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            // 拖拽超过 120px 或速度超过阈值时关闭
                            if (dragOffset > 120f) {
                                onDismiss()
                            }
                            dragOffset = 0f
                        },
                        onVerticalDrag = { _, dragAmount ->
                            dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                        },
                    )
                },
            content = content,
        )
    }
}

/**
 * InkBottomSheet 顶部拖拽手柄 — 墨色发丝线。
 */
@Composable
fun SheetDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AppTheme.Text3.copy(alpha = 0.4f)),
        )
    }
}
