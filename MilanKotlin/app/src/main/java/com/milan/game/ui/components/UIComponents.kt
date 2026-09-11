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

/** 面板材质档位（v3 §5.3）：一个组件最多叠两层材质。 */
enum class PanelMaterial {
    /** 玄墨实底（默认）。 */
    Ink,

    /** 玻璃（浮层/导航/胶囊）——发丝线 + 顶部内高光。 */
    Glass,

    /** 纸纹暗底（典藏容器：卡组册页/详情卷轴）。 */
    Paper,
}

/**
 * 典藏展陈面板 `ArtifactPanel`（v3 §6.1）。
 * 旧名 [GlassPanel] 保留调用点；材质默认 Ink，与 v3「界面向卡牌供奉」一致。
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
    val borderColor = if (highlighted) AppTheme.Gold.copy(alpha = 0.45f) else AppTheme.Stroke

    Box(
        modifier = modifier
            .clip(shape)
            .background(fill, shape)
            .then(
                if (material == PanelMaterial.Glass || material == PanelMaterial.Ink) {
                    Modifier.border(1.dp, borderColor, shape)
                } else {
                    Modifier.border(1.dp, AppTheme.Gold.copy(alpha = 0.18f), shape)
                }
            ),
    ) {
        if (material != PanelMaterial.Paper) {
            // 顶部内高光：微暖白 α6% → 透明（玻璃材质托底，文字永远有实底）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(radius)
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(AppTheme.Text1.copy(alpha = 0.06f), Color.Transparent),
                        ),
                    ),
            )
        } else {
            // 纸纹：极淡金箔经纬线
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .drawBehind {
                        val step = 12.dp.toPx()
                        val line = AppTheme.Gold.copy(alpha = 0.03f)
                        var y = 0f
                        while (y < size.height) {
                            drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
                            y += step
                        }
                        var x = 0f
                        while (x < size.width) {
                            drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
                            x += step
                        }
                    },
            )
        }
        content()
    }
}

/** @deprecated 旧名；请用 [ArtifactPanel]。 */
@Deprecated("v3 改名为 ArtifactPanel", ReplaceWith("ArtifactPanel(modifier, radius, highlighted, nested, material, content)"))
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    radius: Dp = AppTheme.Roundness.lg,
    highlighted: Boolean = false,
    nested: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) = ArtifactPanel(
    modifier = modifier,
    radius = radius,
    highlighted = highlighted,
    nested = nested,
    material = PanelMaterial.Glass,
    content = content,
)

/**
 * 展廊底 `GalleryBackdrop`（v3 §6.1）：玄墨三阶 + 世界 ambient 氛围层。
 * 氛围只染背景光晕，永不下渗按钮/文字/形状。
 * scrollOffset 驱动背景视差（0.3x）。
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
            .background(
                Brush.verticalGradient(
                    listOf(
                        world.background,
                        AppTheme.BgMid,
                        world.background,
                    ),
                ),
            ),
    ) {
        // 世界氛围光：自上而下极淡 glow（神话朱砂金 / 苍穹紫青 / 铁幕钢铜）
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            world.glow.copy(alpha = 0.07f),
                            Color.Transparent,
                            world.glow.copy(alpha = 0.04f),
                        ),
                    ),
                ),
        )
        // 顶部展柜射灯（固定中性，保证可读）
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(AppTheme.Text1.copy(alpha = 0.035f), Color.Transparent),
                        endY = 420f,
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

/** 分区小标题（labelLarge）。 */
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
