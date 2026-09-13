package com.milan.game.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.milan.game.ui.theme.LocalWorldPalette
import kotlin.math.cos
import kotlin.math.sin

/**
 * 水墨进阶 v4 共享设计系统：砚墨实底面板、极淡描边、分区小标题。
 * 默认无金边；选中才加强描边。装饰性环纹已移除。
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

/** 面板材质档位（v3 §5.3）：一个组件最多叠两层材质。 */
enum class PanelMaterial {
    /** 玄墨实底（默认）。 */
    Ink,

    /** 玻璃（浮层/导航/胶囊）——发丝线 + 顶部内高光。 */
    Glass,

    /** 纸纹暗底（仪式容器：卡组册页/详情卷轴）。 */
    Paper,
}

/**
 * 展陈面板：砚墨实底 + 斜光 + 四角角标（高亮时朱砂/金）。
 */
@Composable
fun ArtifactPanel(
    modifier: Modifier = Modifier,
    radius: Dp = AppTheme.Roundness.lg,
    highlighted: Boolean = false,
    nested: Boolean = false,
    material: PanelMaterial = PanelMaterial.Ink,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val fill = when {
        material == PanelMaterial.Paper -> AppTheme.SurfaceNested
        nested -> AppTheme.SurfaceNested
        material == PanelMaterial.Glass -> AppTheme.Surface
        else -> AppTheme.BgMid
    }
    val borderColor = if (highlighted) AppTheme.ZhuSha.copy(alpha = 0.45f) else AppTheme.Stroke
    val cornerCol = if (highlighted) AppTheme.Gold else AppTheme.Text3

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(fill, fill.copy(alpha = 0.92f), AppTheme.SurfaceNested.copy(alpha = 0.95f)),
                ),
                shape,
            )
            .border(1.dp, borderColor, shape)
            .drawBehind {
                // 左上→右下斜光
                drawRect(
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.045f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.12f),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                )
                // 四角 L 角标
                val inset = 6.dp.toPx()
                val len = 10.dp.toPx()
                val sw = 1.dp.toPx()
                val a = cornerCol.copy(alpha = if (highlighted) 0.7f else 0.28f)
                // 左上
                drawLine(a, Offset(inset, inset), Offset(inset + len, inset), sw)
                drawLine(a, Offset(inset, inset), Offset(inset, inset + len), sw)
                // 右下
                drawLine(a, Offset(size.width - inset - len, size.height - inset), Offset(size.width - inset, size.height - inset), sw)
                drawLine(a, Offset(size.width - inset, size.height - inset - len), Offset(size.width - inset, size.height - inset), sw)
            },
    ) {
        content()
    }
}

/**
 * 展廊底：多层砚墨 + 世界晕 + 斜向光带 + 边缘压暗。
 */
@Composable
fun GalleryBackdrop(
    modifier: Modifier = Modifier,
    scrollOffset: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    val world = LocalWorldPalette.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { translationY = scrollOffset * 0.3f }
            .background(AppTheme.BgDeepest),
    ) {
        // 1) 垂直三阶
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(world.background, AppTheme.BgMid.copy(alpha = 0.9f), AppTheme.BgDeepest),
                    ),
                ),
        )
        // 2) 世界色顶晕
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(world.glow.copy(alpha = 0.09f), Color.Transparent, world.glow.copy(alpha = 0.03f)),
                    ),
                ),
        )
        // 3) 斜向光带（像展厅顶灯）
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.03f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.015f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        // 4) 四角压暗（聚焦中心）
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                        radius = 900f,
                    ),
                ),
        )
        content()
    }
}

/** 旧名 [PageBackground] → [GalleryBackdrop]。 */
@Composable
fun PageBackground(
    modifier: Modifier = Modifier,
    scrollOffset: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) = GalleryBackdrop(modifier = modifier, scrollOffset = scrollOffset, content = content)

/** 分区小标题。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = AppTheme.Text2,
        modifier = modifier.padding(bottom = 4.dp),
    )
}

/**
 * 装饰分区标题：朱砂短竖 + 标题 + 渐隐横线 + 可选副标。
 * 全站统一「册页页眉」语言。
 */
@Composable
fun OrnamentSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accent: Color = AppTheme.ZhuSha,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 16.dp)
                .background(accent),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.Text1,
        )
        if (subtitle != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.Text3,
                letterSpacing = 1.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.45f), Color.Transparent),
                    )
                ),
        )
    }
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
