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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.random.Random
import kotlinx.coroutines.isActive

// P4-2（2026-08-27）：光柱阶段从 CyberStage.kt 拆出（分镜 2：Beam）。

/**
 * 次元光柱（Beam 阶段）：光柱自底部上冲 + 火花沿柱上升 + 顶部辉光 + 底部能量环扩散。
 */
@Composable
internal fun RiftBeam(modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nano -> time = nano / 1_000_000_000f }
        }
    }
    val sparks = remember {
        val r = Random(20260822)
        List(26) {
            CyberParticle(
                startX = 0.5f,
                startY = 1f,
                endX = 0.42f + r.nextFloat() * 0.16f,
                endY = r.nextFloat() * 0.85f,
                delaySec = r.nextFloat() * 0.5f,
                durSec = 0.45f + r.nextFloat() * 0.4f,
                radiusDp = 1.2f + r.nextFloat() * 2.2f,
                color = if (r.nextBoolean()) CyberPalette.Cyan else CyberPalette.BeamCore,
            )
        }
    }
    Canvas(modifier) {
        val cx = size.width / 2f
        val progress = (time / 0.55f).coerceIn(0f, 1f)
        val e = 1f - (1f - progress) * (1f - progress)
        val beamH = size.height * e
        val w = 26.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    CyberPalette.Cyan.copy(alpha = 0.65f),
                    CyberPalette.BeamCore.copy(alpha = 0.95f),
                    CyberPalette.Cyan.copy(alpha = 0.65f),
                    Color.Transparent,
                ),
                startY = size.height - beamH,
                endY = size.height,
            ),
            topLeft = Offset(cx - w / 2f, size.height - beamH),
            size = Size(w, beamH),
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, CyberPalette.VioletGlow.copy(alpha = 0.35f), Color.Transparent),
                startY = size.height - beamH,
                endY = size.height,
            ),
            topLeft = Offset(cx - 84.dp.toPx(), size.height - beamH),
            size = Size(168.dp.toPx(), beamH),
        )
        drawCircle(
            color = CyberPalette.Cyan.copy(alpha = 0.55f),
            radius = 26.dp.toPx(),
            center = Offset(cx, size.height - beamH),
        )
        val ringR = 30.dp.toPx() + progress * 170.dp.toPx()
        drawCircle(
            color = CyberPalette.BeamCore.copy(alpha = (1f - progress) * 0.5f),
            radius = ringR,
            center = Offset(cx, size.height),
            style = Stroke(3.dp.toPx()),
        )
        drawCircle(
            color = CyberPalette.Cyan.copy(alpha = (1f - progress) * 0.35f),
            radius = ringR * 0.7f,
            center = Offset(cx, size.height),
            style = Stroke(1.5.dp.toPx()),
        )
        sparks.forEach { p ->
            val local = ((time - p.delaySec) / p.durSec).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            drawCircle(
                color = p.color.copy(alpha = (1f - local) * 0.9f),
                radius = p.radiusDp.dp.toPx(),
                center = Offset(
                    lerp(p.startX * size.width, p.endX * size.width, local),
                    lerp(p.startY * size.height, p.endY * size.height, local),
                ),
            )
        }
    }
}
