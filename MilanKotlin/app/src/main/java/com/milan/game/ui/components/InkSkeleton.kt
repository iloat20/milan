package com.milan.game.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.theme.AppTheme
import kotlin.math.sin

/**
 * 水墨骨架屏加载组件 — 替代三墨点脉动加载器。
 *
 * 视觉：墨色矩形条 + 水墨渐变 shimmer（左→右扫描），更符合加载等待体验。
 *
 * 使用方式：
 * - [InkSkeleton] — 完整骨架屏（条目列表占位 + "加载中" 文字）
 * - [InkShimmer] — 单个 shimmer 矩形条（可组合嵌入）
 * - [Modifier.inkShimmer] — shimmer Modifier 扩展（任意形状）
 */
@Composable
fun InkSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 4,
    showLabel: Boolean = true,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 卡片骨架条（模拟列表条目）
        repeat(rows) { index ->
            InkShimmer(
                fraction = when {
                    index == 0 -> 0.85f
                    index == rows - 1 -> 0.6f
                    else -> 0.75f
                },
                height = 48.dp,
                shape = RoundedCornerShape(AppTheme.Roundness.md),
                modifier = Modifier
                    .fillMaxWidth(
                        when {
                            index == 0 -> 0.85f
                            index == rows - 1 -> 0.6f
                            else -> 0.75f
                        }
                    )
                    .padding(vertical = 6.dp),
            )
        }

        if (showLabel) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "加载中",
                fontSize = 13.sp,
                color = AppTheme.Text3,
            )
        }
    }
}

/**
 * 单条 shimmer 矩形条 — 用于列表/卡片骨架占位。
 *
 * [fraction] 控制宽度占比（0f~1f），[height] 固定高度。
 */
@Composable
fun InkShimmer(
    modifier: Modifier = Modifier,
    fraction: Float = 1f,
    height: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(AppTheme.Roundness.md),
) {
    val shimmerBrush = rememberInkShimmerBrush()
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(shimmerBrush),
    )
}

/**
 * 水墨 shimmer 渐变 Brush — 左→右扫描动画。
 *
 * 背景色为 SurfaceNested（≈墨灰 #2C2C2E），shimmer 高光为 Text3 α20%。
 */
@Composable
private fun rememberInkShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "inkShimmer")
    val progress by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    return Brush.linearGradient(
        colors = listOf(
            AppTheme.SurfaceNested,
            AppTheme.SurfaceNested,
            AppTheme.Text3.copy(alpha = 0.12f),
            AppTheme.SurfaceNested,
            AppTheme.SurfaceNested,
        ),
        start = Offset(progress * 1000f - 400f, 0f),
        end = Offset(progress * 1000f + 400f, 0f),
    )
}

/**
 * Modifier.inkShimmer — 为任意 Composable 添加水墨 shimmer 动画。
 *
 * 用法：`Modifier.inkShimmer().fillMaxWidth().height(48.dp)`
 */
fun Modifier.inkShimmer(): Modifier = composed {
    val shimmerBrush = rememberInkShimmerBrush()
    this.background(shimmerBrush)
}
