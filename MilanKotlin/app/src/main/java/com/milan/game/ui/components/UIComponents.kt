package com.milan.game.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.theme.AppTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * 水墨国风共享设计系统 v2：宣纸面板、墨迹边框、朱印、分区小标题。
 * 视觉从「暗色 + 金线」升级为「宣纸肌理 + 墨迹笔触 + 朱印点缀」。
 */

// ── 墨迹边框绘制工具 ──

/**
 * 模拟毛笔笔触的墨迹边框路径（优化版）。
 * 用 4 段直线 + 4 角圆弧，避免逐段 sin/cos 计算（原实现每条边数十次三角函数调用）。
 * 视觉效果：圆角矩形边框，配合 Stroke 粗细变化模拟毛笔提按。
 */
private fun inkBorderPath(
    width: Float,
    height: Float,
    radius: Float,
    strokeWidth: Float,
): Path {
    val path = Path()
    val inset = strokeWidth * 0.4f // 描边内缩量，模拟毛笔笔尖位置
    // 上边（左→右）
    path.moveTo(radius, inset)
    path.lineTo(width - radius, inset)
    // 右上角圆弧
    path.arcTo(
        rect = androidx.compose.ui.geometry.Rect(width - radius * 2, 0f, width, radius * 2),
        startAngleDegrees = -90f, sweepAngleDegrees = 90f, forceMoveTo = false,
    )
    // 右边（上→下）
    path.lineTo(width - inset, height - radius)
    // 右下角圆弧
    path.arcTo(
        rect = androidx.compose.ui.geometry.Rect(width - radius * 2, height - radius * 2, width, height),
        startAngleDegrees = 0f, sweepAngleDegrees = 90f, forceMoveTo = false,
    )
    // 下边（右→左）
    path.lineTo(radius, height - inset)
    // 左下角圆弧
    path.arcTo(
        rect = androidx.compose.ui.geometry.Rect(0f, height - radius * 2, radius * 2, height),
        startAngleDegrees = 90f, sweepAngleDegrees = 90f, forceMoveTo = false,
    )
    // 左边（下→上）
    path.lineTo(inset, radius)
    // 左上角圆弧
    path.arcTo(
        rect = androidx.compose.ui.geometry.Rect(0f, 0f, radius * 2, radius * 2),
        startAngleDegrees = 180f, sweepAngleDegrees = 90f, forceMoveTo = false,
    )
    path.close()
    return path
}

/** 水墨面板 v2：宣纸底色 + 墨迹不均匀笔触边框 + 顶部内高光。
 *  [highlighted] 金箔墨迹边框点睛；[nested] 嵌套态略深。 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 14.dp,
    highlighted: Boolean = false,
    nested: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val fill = if (nested) AppTheme.SurfaceNested else AppTheme.Surface
    val borderColor = if (highlighted) AppTheme.Gold.copy(alpha = 0.5f) else AppTheme.Text3.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .clip(shape)
            .background(fill, shape)
            // 墨迹笔触边框（代替均匀 border）
            .drawBehind {
                val strokeW = 1.5f
                val path = inkBorderPath(size.width, size.height, radius.toPx(), strokeW)
                drawPath(
                    path,
                    color = borderColor,
                    style = Stroke(
                        width = strokeW,
                        // 粗细变化模拟毛笔提按
                    ),
                )
            },
    ) {
        // 顶部内高光：宣纸白 α8% → 透明
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(radius * 2)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                    ),
                ),
        )
        content()
    }
}

/** 水墨页面底色：宣纸暖灰三段渐变（非纯黑）。scrollOffset 驱动背景视差（0.3x 速率）。 */
@Composable
fun PageBackground(
    modifier: Modifier = Modifier,
    scrollOffset: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { translationY = scrollOffset * 0.3f }
            .background(
                Brush.verticalGradient(
                    listOf(
                        AppTheme.BgDeepest,
                        AppTheme.BgMid,
                        AppTheme.BgDeepest,
                    ),
                ),
            ),
        content = content,
    )
}

/** 分区小标题（12sp 加粗淡墨灰）。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = AppTheme.Text2,
        modifier = modifier.padding(bottom = 4.dp),
    )
}

/** 朱印（方形红色印章装饰）— 用于页面角落点缀。mergeDescendants 合并子节点语义，TalkBack 读「印章 印」。 */
@Composable
fun SealStamp(
    text: String = "印",
    modifier: Modifier = Modifier,
    stampSize: Dp = 36.dp,
) {
    Box(
        modifier = modifier
            .size(stampSize)
            .semantics(mergeDescendants = true) {
                contentDescription = "印章 $text"
            }
            .drawBehind {
                val s = size.width
                val pad = 2.dp.toPx()
                drawRoundRect(
                    color = AppTheme.SealRed.copy(alpha = 0.7f),
                    topLeft = Offset(pad, pad),
                    size = ComposeSize(s - pad * 2, s - pad * 2),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
                val inner = 5.dp.toPx()
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(inner, inner),
                    size = ComposeSize(s - inner * 2, s - inner * 2),
                    cornerRadius = CornerRadius(1.dp.toPx()),
                    style = Stroke(width = 0.8.dp.toPx()),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = (stampSize.value * 0.45f).sp,
            fontWeight = FontWeight.ExtraBold,
            color = AppTheme.SealRed.copy(alpha = 0.85f),
        )
    }
}
