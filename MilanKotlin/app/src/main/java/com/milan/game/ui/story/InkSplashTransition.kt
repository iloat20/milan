package com.milan.game.ui.story

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 墨汁泼溅转场效果。
 *
 * 从屏幕中心向外扩散的不规则墨迹圆形，模拟水墨泼溅效果。
 * 用于剧情对话界面进入/退出时的视觉过渡。
 *
 * @param isActive 是否激活转场动画
 * @param isEntering true=进入（墨汁扩散后消失），false=退出（墨汁收缩覆盖）
 * @param color 墨汁颜色（默认深色）
 * @param onAnimEnd 动画结束回调
 */
@Composable
fun InkSplashTransition(
    isActive: Boolean,
    isEntering: Boolean,
    color: Color = Color(0xFF1A1A2E),
    onAnimEnd: () -> Unit = {},
) {
    if (!isActive) return

    val infiniteTransition = rememberInfiniteTransition(label = "ink_splash")

    // 主进度 0→1
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 800,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ink_progress",
    )

    // 触发结束回调
    LaunchedEffect(progress) {
        if (progress > 0.95f) {
            onAnimEnd()
        }
    }

    // 预生成随机控制点（墨汁边缘不规则性）
    val blobPoints = remember {
        List(12) { i ->
            val angle = (i * 30f) + Random.nextFloat() * 15f
            val radiusFactor = 0.7f + Random.nextFloat() * 0.6f // 0.7~1.3 不规则
            Pair(angle, radiusFactor)
        }
    }

    Canvas(
        modifier = Modifier.fillMaxSize(),
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxRadius = size.width.coerceAtLeast(size.height) * 0.8f

        val animProgress = if (isEntering) (1f - progress) else progress
        val currentRadius = maxRadius * animProgress

        drawInkBlob(
            center = Offset(cx, cy),
            radius = currentRadius,
            color = color,
            blobPoints = blobPoints,
            progress = animProgress,
        )
    }
}

/**
 * 绘制单个不规则墨迹圆形。
 */
private fun DrawScope.drawInkBlob(
    center: Offset,
    radius: Float,
    color: Color,
    blobPoints: List<Pair<Float, Float>>,
    progress: Float,
) {
    if (radius < 1f) return

    val path = Path().apply {
        val firstPoint = blobPoints.first()
        val r0 = radius * firstPoint.second
        val x0 = center.x + cos(Math.toRadians(firstPoint.first.toDouble())).toFloat() * r0
        val y0 = center.y + sin(Math.toRadians(firstPoint.first.toDouble())).toFloat() * r0
        moveTo(x0, y0)

        for (i in 1 until blobPoints.size) {
            val (angle, radiusFactor) = blobPoints[i]
            val r = radius * radiusFactor
            val x = center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * r
            val y = center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * r
            // 用二次贝塞尔曲线连接，产生平滑不规则边缘
            val ctrlOffset = radius * 0.15f
            val ctrlX = (x0 + x) / 2f + (Random.nextFloat() - 0.5f) * ctrlOffset
            val ctrlY = (y0 + y) / 2f + (Random.nextFloat() - 0.5f) * ctrlOffset
            quadraticTo(ctrlX, ctrlY, x, y)
        }
        close()
    }

    drawPath(path, color)

    // 外圈散布小墨点（增加泼溅感）
    if (progress > 0.3f) {
        val dotCount = (progress * 8).toInt().coerceAtMost(8)
        for (i in 0 until dotCount) {
            val angle = i * 45f + progress * 30f
            val dotDist = radius * (0.85f + Random.nextFloat() * 0.3f)
            val dotRadius = 2f + Random.nextFloat() * 6f
            val dx = center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * dotDist
            val dy = center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * dotDist
            drawCircle(
                color = color.copy(alpha = (1f - progress * 0.3f).coerceAtLeast(0.3f)),
                radius = dotRadius,
                center = Offset(dx, dy),
            )
        }
    }
}
