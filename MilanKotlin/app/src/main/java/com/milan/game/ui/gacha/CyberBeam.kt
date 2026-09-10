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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.isActive

// P4-2（2026-08-27）：光柱阶段从 CyberStage.kt 拆出（分镜 2：Beam）。
// 2026-09：次元裂缝霓虹柱 → 丹青「敕令开卷」：宣纸白光柱 + 金箔辉光 + 底部墨晕扩散环。

/**
 * 开卷光柱（Beam）：宣纸白核心自底部上冲，外层金箔/朱砂辉光，
 * 火花呈墨点上飞，底部三重扩散环如墨晕入水。UR 更宽更密更久感。
 */
@Composable
internal fun RiftBeam(rarity: Int = 1, modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nano -> time = nano / 1_000_000_000f }
        }
    }
    val sparkCount = when { rarity >= 4 -> 44; rarity == 3 -> 34; else -> 26 }
    val beamWidth = when { rarity >= 4 -> 40f; rarity == 3 -> 32f; else -> 24f }
    val sparks = remember(rarity) {
        val r = Random(20260822)
        List(sparkCount) {
            CyberParticle(
                startX = 0.5f,
                startY = 1f,
                endX = 0.40f + r.nextFloat() * 0.20f,
                endY = r.nextFloat() * 0.88f,
                delaySec = r.nextFloat() * 0.45f,
                durSec = 0.42f + r.nextFloat() * 0.42f,
                radiusDp = 1.0f + r.nextFloat() * 2.0f + if (rarity >= 4) 0.7f else 0f,
                color = when {
                    rarity >= 4 && r.nextBoolean() -> CyberPalette.Magenta
                    r.nextBoolean() -> CyberPalette.Cyan
                    else -> CyberPalette.BeamCore
                },
            )
        }
    }
    // 底部墨晕随机羽化点
    val blooms = remember {
        val r = Random(20260902)
        List(9) {
            Triple(r.nextFloat() * 360f, 0.15f + r.nextFloat() * 0.55f, 0.4f + r.nextFloat() * 0.6f)
        }
    }
    Canvas(modifier) {
        val cx = size.width / 2f
        val progress = (time / 0.55f).coerceIn(0f, 1f)
        val e = 1f - (1f - progress) * (1f - progress)
        val beamH = size.height * e
        val w = beamWidth.dp.toPx()

        // 外层金箔柔光柱（宽）
        val glowW = if (rarity >= 4) 120f else 90f
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    CyberPalette.Magenta.copy(alpha = if (rarity >= 4) 0.28f else 0.18f),
                    Color.Transparent,
                ),
            ),
            topLeft = Offset(cx - glowW.dp.toPx(), size.height - beamH),
            size = Size(glowW.dp.toPx() * 2f, beamH),
        )
        // 中层朱砂/石青
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    CyberPalette.Cyan.copy(alpha = 0.55f),
                    CyberPalette.BeamCore.copy(alpha = 0.9f),
                    CyberPalette.Cyan.copy(alpha = 0.55f),
                    Color.Transparent,
                ),
            ),
            topLeft = Offset(cx - w * 0.85f, size.height - beamH),
            size = Size(w * 1.7f, beamH),
        )
        // 核心宣纸白细柱
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    CyberPalette.BeamCore.copy(alpha = 0.95f),
                    Color.White,
                    CyberPalette.BeamCore.copy(alpha = 0.95f),
                    Color.Transparent,
                ),
                startY = size.height - beamH,
                endY = size.height,
            ),
            topLeft = Offset(cx - w / 2f, size.height - beamH),
            size = Size(w, beamH),
        )
        // 顶部光晕（开卷口）
        val topR = (28f + progress * 36f).dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                listOf(CyberPalette.BeamCore, CyberPalette.Magenta.copy(alpha = 0.4f), Color.Transparent),
                center = Offset(cx, size.height - beamH),
                radius = topR,
            ),
            radius = topR,
            center = Offset(cx, size.height - beamH),
        )
        // 底部能量核
        val orbR = if (rarity >= 4) 36f else 28f
        drawCircle(
            color = CyberPalette.Cyan.copy(alpha = 0.5f),
            radius = orbR.dp.toPx() * (1f + 0.08f * sin(time * 8f)),
            center = Offset(cx, size.height - beamH * 0.02f),
        )
        // 三重墨晕扩散环
        repeat(3) { i ->
            val phase = ((progress + i * 0.22f) % 1f)
            val ringR = (24f + phase * 190f).dp.toPx()
            val alpha = (1f - phase) * (0.55f - i * 0.12f)
            if (alpha <= 0.02f) return@repeat
            drawCircle(
                color = if (i == 0) CyberPalette.BeamCore else CyberPalette.Cyan,
                radius = ringR,
                center = Offset(cx, size.height),
                style = Stroke(width = (3.2f - i * 0.8f).dp.toPx()),
                alpha = alpha,
            )
        }
        // 底部羽化墨点
        blooms.forEach { (deg, dist, sz) ->
            val rad = Math.toRadians(deg.toDouble())
            val d = dist * size.width * 0.45f * progress
            val px = cx + kotlin.math.cos(rad).toFloat() * d
            val py = size.height + kotlin.math.sin(rad).toFloat() * d * 0.25f
            drawCircle(
                color = CyberPalette.Cyan.copy(alpha = (1f - progress) * 0.22f),
                radius = sz * 6.dp.toPx(),
                center = Offset(px, py),
            )
        }
        // 上飞墨粒
        sparks.forEach { p ->
            val local = ((time - p.delaySec) / p.durSec).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            drawCircle(
                color = p.color.copy(alpha = (1f - local) * 0.85f),
                radius = p.radiusDp.dp.toPx() * (1f - local * 0.4f),
                center = Offset(
                    lerp(p.startX * size.width, p.endX * size.width, local),
                    lerp(p.startY * size.height, p.endY * size.height, local),
                ),
            )
        }
        // 光柱两侧细线（卷轴边）
        val sideAlpha = (1f - progress * 0.5f) * 0.25f
        val pathL = Path().apply {
            moveTo(cx - w * 1.2f, size.height)
            lineTo(cx - w * 0.35f, size.height - beamH)
        }
        val pathR = Path().apply {
            moveTo(cx + w * 1.2f, size.height)
            lineTo(cx + w * 0.35f, size.height - beamH)
        }
        drawPath(pathL, CyberPalette.Magenta.copy(alpha = sideAlpha), style = Stroke(1.dp.toPx()))
        drawPath(pathR, CyberPalette.Magenta.copy(alpha = sideAlpha), style = Stroke(1.dp.toPx()))
    }
}
