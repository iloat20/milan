package com.milan.game.ui.gacha

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.isActive

// P4-2（2026-08-27）：光柱阶段从 CyberStage.kt 拆出（分镜 2：Beam）。
// 2026-09 再收：「聚光开匣」——顶光锥压下 → 匣盖对开 → 内光上冲（与 Charge 蓄墨、召印外匣同叙事）。

/**
 * 聚光开匣（Beam）。
 *
 * 时序（约 0.62s）：
 * 1. 顶光锥淡入，照亮中心匣体
 * 2. 左右匣盖滑开，缝口漏光
 * 3. 宣纸白光柱自匣口上冲 + 金箔辉光 + 底墨晕
 *
 * UR 更亮更宽；禁赛博霓虹竖条。
 */
@Composable
internal fun RiftBeam(rarity: Int = 1, modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nano -> time = nano / 1_000_000_000f }
        }
    }
    val sparkCount = when { rarity >= 4 -> 36; rarity == 3 -> 28; else -> 18 }
    val sparks = remember(rarity) {
        val r = Random(20260822)
        List(sparkCount) {
            CyberParticle(
                startX = 0.5f,
                startY = 0.55f,
                endX = 0.38f + r.nextFloat() * 0.24f,
                endY = r.nextFloat() * 0.55f,
                delaySec = 0.18f + r.nextFloat() * 0.28f,
                durSec = 0.38f + r.nextFloat() * 0.35f,
                radiusDp = 1.0f + r.nextFloat() * 1.8f + if (rarity >= 4) 0.6f else 0f,
                color = when {
                    rarity >= 4 && r.nextBoolean() -> CyberPalette.Magenta
                    r.nextBoolean() -> CyberPalette.Cyan
                    else -> CyberPalette.BeamCore
                },
            )
        }
    }

    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.52f
        // 总进度：0..1 覆盖「亮灯→开匣→光柱」
        val progress = (time / 0.62f).coerceIn(0f, 1f)
        // 分段
        val spot = (progress / 0.28f).coerceIn(0f, 1f)          // 聚光淡入
        val open = ((progress - 0.22f) / 0.38f).coerceIn(0f, 1f) // 匣盖滑开
        val beam = ((progress - 0.38f) / 0.40f).coerceIn(0f, 1f) // 光柱上冲
        val beamE = 1f - (1f - beam) * (1f - beam)

        drawSpotlightCone(cx, cy, spot, rarity)
        drawCasket(cx, cy, open, beam, rarity, time)
        drawInnerBeam(cx, cy, beamE, rarity)
        drawInkRings(cx, size.height * 0.72f, beamE, rarity)
        drawSparks(sparks, time)
    }
}

/** 顶光锥：自屏顶压下的聚光，开匣仪式的「打光」。 */
private fun DrawScope.drawSpotlightCone(cx: Float, cy: Float, spot: Float, rarity: Int) {
    if (spot <= 0.01f) return
    val topY = 0f
    val halfTop = size.width * 0.10f
    val halfBottom = size.width * 0.42f
    val gold = if (rarity >= 4) 0.22f else 0.12f
    val path = Path().apply {
        moveTo(cx - halfTop, topY)
        lineTo(cx + halfTop, topY)
        lineTo(cx + halfBottom, cy)
        lineTo(cx - halfBottom, cy)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.05f * spot),
                CyberPalette.Magenta.copy(alpha = gold * spot),
                Color.Transparent,
            ),
            startY = 0f,
            endY = cy,
        ),
    )
    // 顶光源晕
    val topR = (18f + spot * 28f).dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Color.White.copy(alpha = 0.35f * spot),
                CyberPalette.Magenta.copy(alpha = 0.15f * spot),
                Color.Transparent,
            ),
            center = Offset(cx, 0f),
            radius = topR,
        ),
        radius = topR,
        center = Offset(cx, 0f),
    )
}

/** 匣体 + 对开盖 + 缝口漏光。 */
private fun DrawScope.drawCasket(
    cx: Float,
    cy: Float,
    open: Float,
    beam: Float,
    rarity: Int,
    time: Float,
) {
    val boxW = size.minDimension * 0.42f
    val boxH = size.minDimension * 0.18f
    val left = cx - boxW / 2f
    val top = cy - boxH / 2f
    val shape = CornerRadius(10.dp.toPx())
    val frame = if (rarity >= 4) AppThemeGoldHi() else AppThemeGold()

    // 匣座（固定）
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(
                Color(0xFF2A2E34),
                Color(0xFF12151A),
                Color(0xFF0A0C10),
            ),
        ),
        topLeft = Offset(left, top),
        size = Size(boxW, boxH),
        cornerRadius = shape,
    )
    drawRoundRect(
        color = frame.copy(alpha = 0.35f + beam * 0.25f),
        topLeft = Offset(left, top),
        size = Size(boxW, boxH),
        cornerRadius = shape,
        style = Stroke(width = 1.2f.dp.toPx()),
    )

    // 内腔：开匣后发光
    if (beam > 0.02f) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.15f * beam),
                    Color(0xFFE8E4DC).copy(alpha = 0.55f * beam),
                ),
            ),
            topLeft = Offset(left + 4.dp.toPx(), top + 4.dp.toPx()),
            size = Size(boxW - 8.dp.toPx(), boxH - 8.dp.toPx()),
            cornerRadius = CornerRadius(6.dp.toPx()),
        )
    }

    // 左右匣盖滑开
    val slide = open * boxW * 0.48f
    val lidW = boxW * 0.52f
    // 左盖
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF1A1E22), Color(0xFF2A2E34)),
        ),
        topLeft = Offset(left - slide, top),
        size = Size(lidW, boxH),
        cornerRadius = shape,
    )
    drawRoundRect(
        color = frame.copy(alpha = 0.4f),
        topLeft = Offset(left - slide, top),
        size = Size(lidW, boxH),
        cornerRadius = shape,
        style = Stroke(width = 1.dp.toPx()),
    )
    // 右盖
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF2A2E34), Color(0xFF1A1E22)),
        ),
        topLeft = Offset(left + boxW - lidW + slide, top),
        size = Size(lidW, boxH),
        cornerRadius = shape,
    )
    drawRoundRect(
        color = frame.copy(alpha = 0.4f),
        topLeft = Offset(left + boxW - lidW + slide, top),
        size = Size(lidW, boxH),
        cornerRadius = shape,
        style = Stroke(width = 1.dp.toPx()),
    )

    // 缝口漏光（开缝随 open 增大）
    if (open > 0.05f) {
        val gap = 2.dp.toPx() + open * boxW * 0.22f
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.55f * open),
                    CyberPalette.Magenta.copy(alpha = 0.35f * open),
                    Color.White.copy(alpha = 0.55f * open),
                    Color.Transparent,
                ),
            ),
            topLeft = Offset(cx - gap, top),
            size = Size(gap * 2f, boxH),
        )
    }

    // 匣上印字「启」
    if (open < 0.85f) {
        // Canvas 无法直接画文字；用方印块暗示（与 Charge/召印同构）
        val sealS = boxH * 0.42f
        drawRoundRect(
            color = AppThemeZhuSha().copy(alpha = (1f - open) * (0.85f + 0.1f * sin(time * 6f))),
            topLeft = Offset(cx - sealS / 2f, cy - sealS / 2f),
            size = Size(sealS, sealS),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
    }
}

/** 匣口上冲光柱 + 金箔外辉。 */
private fun DrawScope.drawInnerBeam(cx: Float, cy: Float, beam: Float, rarity: Int) {
    if (beam <= 0.02f) return
    val beamH = size.height * 0.55f * beam
    val w = when {
        rarity >= 4 -> 28f
        rarity == 3 -> 22f
        else -> 16f
    }.dp.toPx()
    val glowW = w * (if (rarity >= 4) 4.2f else 3.2f)
    val top = cy - beamH

    // 外金辉
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                CyberPalette.Magenta.copy(alpha = if (rarity >= 4) 0.26f else 0.16f),
                Color.Transparent,
            ),
        ),
        topLeft = Offset(cx - glowW, top),
        size = Size(glowW * 2f, beamH),
    )
    // 中朱砂
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                CyberPalette.Cyan.copy(alpha = 0.5f),
                CyberPalette.BeamCore.copy(alpha = 0.85f),
                CyberPalette.Cyan.copy(alpha = 0.5f),
                Color.Transparent,
            ),
        ),
        topLeft = Offset(cx - w * 0.9f, top),
        size = Size(w * 1.8f, beamH),
    )
    // 核心宣纸白
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                Color.Transparent,
                CyberPalette.BeamCore.copy(alpha = 0.9f),
                Color.White,
                CyberPalette.BeamCore.copy(alpha = 0.95f),
            ),
            startY = top,
            endY = cy,
        ),
        topLeft = Offset(cx - w / 2f, top),
        size = Size(w, beamH),
    )
    // 柱顶光晕
    val topR = (20f + beam * 32f).dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White, CyberPalette.Magenta.copy(alpha = 0.45f), Color.Transparent),
            center = Offset(cx, top),
            radius = topR,
        ),
        radius = topR,
        center = Offset(cx, top),
    )
}

/** 底墨晕扩散环（落在匣下）。 */
private fun DrawScope.drawInkRings(cx: Float, baseY: Float, beam: Float, rarity: Int) {
    if (beam <= 0.05f) return
    repeat(3) { i ->
        val phase = ((beam + i * 0.25f) % 1f)
        val ringR = (20f + phase * 160f).dp.toPx()
        val alpha = (1f - phase) * (0.5f - i * 0.1f)
        if (alpha <= 0.02f) return@repeat
        drawCircle(
            color = if (i == 0) CyberPalette.BeamCore else CyberPalette.Cyan,
            radius = ringR,
            center = Offset(cx, baseY),
            style = Stroke(width = (2.8f - i * 0.7f).dp.toPx()),
            alpha = alpha,
        )
    }
}

private fun DrawScope.drawSparks(sparks: List<CyberParticle>, time: Float) {
    sparks.forEach { p ->
        val local = ((time - p.delaySec) / p.durSec).coerceIn(0f, 1f)
        if (local <= 0f) return@forEach
        drawCircle(
            color = p.color.copy(alpha = (1f - local) * 0.8f),
            radius = p.radiusDp.dp.toPx() * (1f - local * 0.35f),
            center = Offset(
                p.startX * size.width + (p.endX - p.startX) * size.width * local,
                p.startY * size.height + (p.endY - p.startY) * size.height * local,
            ),
        )
    }
}

// 与 AppTheme 对齐的近似色，避免 Canvas 依赖 Composable 主题读取
private fun AppThemeGold() = Color(0xFFC9A96A)
private fun AppThemeGoldHi() = Color(0xFFE0C888)
private fun AppThemeZhuSha() = Color(0xFFC4453A)
