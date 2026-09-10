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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.isActive

// P4-2（2026-08-27）：蓄能阶段从 CyberStage.kt 拆出（分镜 1：Charge）。
// 2026-09：赛博汇聚球 → 丹青典藏「敕令蓄墨」：墨粒自屏缘吸入 + 金箔敕令环收束 + 朱砂印核呼吸。

/**
 * 蓄墨漩涡（Charge）：淡墨粒子自四边汇入中心，金箔虚线敕令环逆时针收束，
 * 中心宣纸白能量核按稀有度加大加速。UR 增第四环与金箔主色。
 */
@Composable
internal fun ChargeCore(rarity: Int = 1, modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nano -> time = nano / 1_000_000_000f }
        }
    }
    val particleCount = when {
        rarity >= 4 -> 56; rarity == 3 -> 44; rarity == 2 -> 32; else -> 22
    }
    val speedMul = when { rarity >= 4 -> 1.45f; rarity == 3 -> 1.2f; else -> 1f }
    val particles = remember(rarity) {
        val r = Random(20260821)
        List(particleCount) {
            val side = it % 4
            val sx = when (side) { 0 -> r.nextFloat(); 1 -> 1f; else -> r.nextFloat() }
            val sy = when (side) { 2 -> r.nextFloat(); 3 -> 1f; else -> r.nextFloat() }
            CyberParticle(
                startX = sx, startY = sy,
                endX = 0.5f, endY = 0.42f,
                delaySec = r.nextFloat() * 0.28f,
                durSec = (0.38f + r.nextFloat() * 0.38f) / speedMul,
                radiusDp = 1.2f + r.nextFloat() * 2.2f + if (rarity >= 4) 0.9f else 0f,
                color = when {
                    rarity >= 4 && r.nextFloat() < 0.55f -> CyberPalette.Magenta
                    r.nextFloat() < 0.35f -> CyberPalette.BeamCore
                    else -> CyberPalette.Cyan.copy(alpha = 0.75f)
                },
            )
        }
    }
    // 旋转敕令环种子点（固定）
    val sealMarks = remember {
        val r = Random(20260901)
        List(12) { i ->
            Triple(i * 30f + r.nextFloat() * 8f, 0.9f + r.nextFloat() * 0.2f, r.nextFloat())
        }
    }
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.42f
        particles.forEach { p ->
            val local = ((time - p.delaySec) / p.durSec).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val e = 1f - (1f - local) * (1f - local)
            // 汇入时微微加速并收细
            val trail = 1f - local * 0.35f
            drawCircle(
                color = p.color.copy(alpha = (1f - local) * 0.85f),
                radius = p.radiusDp.dp.toPx() * trail,
                center = Offset(lerp(p.startX * size.width, cx, e), lerp(p.startY * size.height, cy, e)),
            )
        }
        // 外圈虚线敕令环：UR/SSR 更亮
        val ringBase = if (rarity >= 4) 118f else 100f
        val ringR = ringBase.dp.toPx() * (1f - 0.06f * sin(time * 4f))
        drawCircle(
            color = CyberPalette.Magenta.copy(alpha = 0.28f + if (rarity >= 4) 0.18f else 0f),
            radius = ringR,
            center = Offset(cx, cy),
            style = Stroke(
                width = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
            ),
        )
        // 金箔旋转刻度弧
        val arcCount = if (rarity >= 4) 4 else 3
        repeat(arcCount) { i ->
            val r = (68f + i * 22f).dp.toPx()
            val start = (-time * 110f + i * 120f) % 360f
            drawArc(
                color = if (i % 2 == 0) CyberPalette.Magenta.copy(alpha = 0.55f)
                else CyberPalette.Cyan.copy(alpha = 0.4f),
                startAngle = start,
                sweepAngle = if (rarity >= 4) 90f else 72f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = if (rarity >= 4) 2.4f.dp.toPx() else 1.8f.dp.toPx()),
            )
        }
        // 朱砂印核：呼吸 + 内光
        val pulse = 0.82f + 0.18f * sin(time * 5.2f)
        val baseRadius = if (rarity >= 4) 132f else 112f
        val radius = baseRadius.dp.toPx() * pulse
        val coreColors = if (rarity >= 4) listOf(
            CyberPalette.BeamCore,
            CyberPalette.Magenta.copy(alpha = 0.7f),
            CyberPalette.Cyan.copy(alpha = 0.22f),
            Color.Transparent,
        ) else listOf(
            CyberPalette.BeamCore,
            CyberPalette.Cyan.copy(alpha = 0.5f),
            CyberPalette.VioletGlow.copy(alpha = 0.12f),
            Color.Transparent,
        )
        drawCircle(
            brush = Brush.radialGradient(coreColors, center = Offset(cx, cy), radius = radius),
            radius = radius,
            center = Offset(cx, cy),
        )
        // 印核外一圈朱砂描边（印章感）
        drawCircle(
            color = CyberPalette.Cyan.copy(alpha = 0.45f),
            radius = radius * 0.42f,
            center = Offset(cx, cy),
            style = Stroke(1.5.dp.toPx()),
        )
        // 绕核金点
        sealMarks.forEach { (deg, w, phase) ->
            val ang = Math.toRadians((deg + time * 40f).toDouble())
            val rr = radius * 0.55f * w
            val px = cx + cos(ang).toFloat() * rr
            val py = cy + sin(ang).toFloat() * rr
            val tw = (sin(time * 3f + phase * 6.28f) + 1f) * 0.5f
            rotate(degrees = deg + time * 40f, pivot = Offset(px, py)) {
                drawCircle(
                    color = CyberPalette.Magenta.copy(alpha = 0.35f + tw * 0.45f),
                    radius = (1.8f + tw).dp.toPx(),
                    center = Offset(px, py),
                )
            }
        }
    }
}
