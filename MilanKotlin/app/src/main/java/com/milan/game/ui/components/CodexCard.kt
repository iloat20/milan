package com.milan.game.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * CodexCard — **实体收藏卡外壳**（对标 Marvel Snap / 宝可梦 TCG 卡面）。
 *
 * 三层结构模拟真实卡牌厚度与装裱：
 * ```
 * ┌──────────────────┐  ① 卡边（深色 + 稀有度微染，纸厚感）
 * │ ╭──────────────╮ │  ② 工艺框（R 素线 / SR 双线 / SSR 金边 / UR 流光）
 * │ │   content    │ │  ③ 画心（content 自填）
 * │ ╰──────────────╯ │
 * └──────────────────┘
 * ```
 * 四档工艺：色 + 纹 + 光 + 动四维冗余编码。
 * 取色只认 [AppTheme.rarityColor] / [AppTheme.rarityGradient] / [AppTheme.rarityGlow]。
 */
@Composable
fun CodexCard(
    tier: Int,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(CardMetrics.Corner),
    /** 卡边厚度（纸厚感）；网格里可传 2.dp 减负。 */
    edge: Dp = CardMetrics.Edge,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val rarityCol = AppTheme.rarityColor(tier)
    val grad = AppTheme.rarityGradient(tier)

    // 工艺框线宽 / 透明度
    val borderWidth = when {
        tier >= 4 -> 2.dp
        tier == 3 -> 1.75.dp
        tier == 2 -> 1.25.dp
        else -> 1.dp
    }
    val borderAlpha = when {
        tier >= 4 -> 0.95f
        tier == 3 -> 0.9f
        tier == 2 -> 0.7f
        else -> 0.45f
    }

    // 无限动画只在 SSR/UR 开启（2026-09-10：R/SR 卡格里十余个 infiniteTransition 会空转耗帧）
    val ssrBreath by rememberInfiniteTransition(label = "codex_ssr_breath").animateFloat(
        initialValue = 0.28f,
        targetValue = 0.58f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "ssr_glow",
    )
    val urFlow by rememberInfiniteTransition(label = "codex_ur_flow").animateFloat(
        initialValue = 0.02f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse),
        label = "ur_flow",
    )

    val urBorderBrush = if (tier >= 4) {
        val hiPos = 0.05f + urFlow
        Brush.sweepGradient(
            0f to rarityCol.copy(alpha = borderAlpha),
            hiPos * 0.5f to rarityCol.copy(alpha = borderAlpha),
            hiPos to AppTheme.GoldHi,
            (hiPos + 0.18f).coerceAtMost(0.99f) to rarityCol.copy(alpha = borderAlpha),
            1f to rarityCol.copy(alpha = borderAlpha),
        )
    } else null

    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)

    val innerShape = RoundedCornerShape(CardMetrics.Corner - edge)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                // 轻微纸厚阴影
                shadowElevation = 6f
                clip = true
            }
            // ① 卡边：深色纸厚 + 稀有度极淡染边
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        rarityCol.copy(alpha = 0.22f),
                        Color(0xFF0E1016),
                        Color(0xFF08090C),
                    )
                ),
                shape,
            )
            .then(
                if (currentOnClick != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                scope.launch { scale.animateTo(0.965f, spring()) }
                                tryAwaitRelease()
                                scope.launch { scale.animateTo(1f, spring()) }
                                currentOnClick?.invoke()
                            },
                        )
                    }
                } else Modifier
            )
    ) {
        // ② 内框：工艺线 + 画心容器
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(edge)
                .clip(innerShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            grad.first().copy(alpha = 0.18f),
                            AppTheme.BgMid,
                            AppTheme.BgDeepest,
                        )
                    ),
                    innerShape,
                )
                .border(
                    width = borderWidth,
                    brush = urBorderBrush ?: Brush.linearGradient(
                        listOf(
                            grad.last().copy(alpha = borderAlpha),
                            grad.first().copy(alpha = borderAlpha * 0.7f),
                        )
                    ),
                    shape = innerShape,
                )
                // 内金线（所有稀有度都有，像烫金内框）
                .padding(if (tier >= 3) 2.dp else 1.5.dp)
                .border(
                    width = if (tier >= 3) 0.75.dp else 0.5.dp,
                    color = AppTheme.Gold.copy(alpha = if (tier >= 3) 0.45f else 0.22f),
                    shape = innerShape,
                )
                .drawWithContent {
                    drawContent()
                    // 顶部镜面高光（卡面塑料感）
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
                            endY = size.height * 0.28f,
                        )
                    )
                    if (tier == 3) {
                        drawSsrWeave()
                        drawCornerBrackets(AppTheme.GoldHi.copy(alpha = 0.85f))
                        drawGlowRing(ssrBreath * 0.45f, rarityCol)
                    }
                    if (tier >= 4) {
                        drawFoilSheen(urFlow)
                        drawCornerBrackets(AppTheme.GoldHi)
                        drawGlowRing(0.4f, rarityCol)
                    }
                    if (tier == 2) {
                        drawGlazeSheen()
                    }
                },
        ) {
            content()
        }
    }
}

/** 卡牌度量：标准 TCG 比例与厚度。 */
object CardMetrics {
    /** 卡角（实体卡偏小圆角，非 Material 大圆角）。 */
    val Corner = 8.dp

    /** 卡边纸厚。 */
    val Edge = 3.dp

    /** 画心/整卡比例 ≈ 0.71（近 5:7）。 */
    const val Aspect = 0.71f
}

/** SR 釉光：右上→左下一道极淡斜向高光。 */
private fun DrawScope.drawGlazeSheen() {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.06f),
                Color.Transparent,
                Color.White.copy(alpha = 0.03f),
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
    )
}

/** SSR 织纹：45° 细线。 */
private fun DrawScope.drawSsrWeave() {
    val step = 11.dp.toPx()
    val stroke = 1.dp.toPx()
    val weave = Color.White.copy(alpha = 0.028f)
    var x = -size.height
    while (x < size.width) {
        drawLine(weave, Offset(x, size.height), Offset(x + size.height, 0f), stroke)
        x += step
    }
}

/** 四角 L 形金角标（实体卡烫金角）。 */
private fun DrawScope.drawCornerBrackets(color: Color) {
    val inset = 4.dp.toPx()
    val len = 11.dp.toPx()
    val sw = 1.5.dp.toPx()
    val w = size.width
    val h = size.height
    // 左上
    drawLine(color, Offset(inset, inset), Offset(inset + len, inset), sw)
    drawLine(color, Offset(inset, inset), Offset(inset, inset + len), sw)
    // 右上
    drawLine(color, Offset(w - inset, inset), Offset(w - inset - len, inset), sw)
    drawLine(color, Offset(w - inset, inset), Offset(w - inset, inset + len), sw)
    // 左下
    drawLine(color, Offset(inset, h - inset), Offset(inset + len, h - inset), sw)
    drawLine(color, Offset(inset, h - inset), Offset(inset, h - inset - len), sw)
    // 右下
    drawLine(color, Offset(w - inset, h - inset), Offset(w - inset - len, h - inset), sw)
    drawLine(color, Offset(w - inset, h - inset), Offset(w - inset, h - inset - len), sw)
}

/** UR 箔面扫光。 */
private fun DrawScope.drawFoilSheen(flow: Float) {
    val hiPos = 0.05f + flow
    drawRect(
        brush = Brush.sweepGradient(
            0f to Color.Transparent,
            hiPos * 0.5f to Color.Transparent,
            hiPos to AppTheme.GoldHi.copy(alpha = 0.13f),
            (hiPos + 0.22f).coerceAtMost(0.99f) to Color.Transparent,
            1f to Color.Transparent,
        ),
    )
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.09f), Color.Transparent),
            endY = size.height * 0.35f,
        ),
    )
}

/** 内缘光晕（SSR 呼吸 / UR 常驻）。 */
private fun DrawScope.drawGlowRing(alpha: Float, color: Color) {
    drawRoundRect(
        color = color.copy(alpha = alpha),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(CardMetrics.Corner.toPx()),
        style = Stroke(width = 3.dp.toPx()),
    )
}
