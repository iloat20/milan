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
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.isActive

// P4-2（2026-08-27）：蓄能阶段从 CyberStage.kt 拆出（分镜 1：Charge）。

/**
 * 蓄能漩涡（Charge 阶段）：粒子从四边汇聚至中心 + 三环弧线旋转 + 中心能量球脉动。
 * 中心点取画布 42% 高度（给上方稀有度大字留位）。
 * rarity 驱动粒子密度与速度：UR=50 颗/快速，SSR=40，SR=30，R=20。
 */
@Composable
internal fun ChargeCore(rarity: Int = 1, modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nano -> time = nano / 1_000_000_000f }
        }
    }
    // 稀有度驱动：UR 更多粒子、更快汇聚
    val particleCount = when { rarity >= 4 -> 50; rarity == 3 -> 40; rarity == 2 -> 30; else -> 20 }
    val speedMul = when { rarity >= 4 -> 1.4f; rarity == 3 -> 1.15f; else -> 1f }
    val particles = remember(rarity) {
        val r = Random(20260821)
        List(particleCount) { i ->
            val side = i % 4
            val sx = when (side) { 0 -> r.nextFloat(); 1 -> 1f; else -> r.nextFloat() }
            val sy = when (side) { 2 -> r.nextFloat(); 3 -> 1f; else -> r.nextFloat() }
            CyberParticle(
                startX = sx, startY = sy,
                endX = 0.5f, endY = 0.42f,
                delaySec = r.nextFloat() * 0.25f,
                durSec = (0.35f + r.nextFloat() * 0.35f) / speedMul,
                radiusDp = 1.5f + r.nextFloat() * 2.5f + if (rarity >= 4) 0.8f else 0f,
                color = if (r.nextBoolean()) CyberPalette.Cyan else CyberPalette.Magenta,
            )
        }
    }
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.42f
        particles.forEach { p ->
            val local = ((time - p.delaySec) / p.durSec).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val e = 1f - (1f - local) * (1f - local)
            drawCircle(
                color = p.color.copy(alpha = (1f - local) * 0.9f),
                radius = p.radiusDp.dp.toPx(),
                center = Offset(lerp(p.startX * size.width, cx, e), lerp(p.startY * size.height, cy, e)),
            )
        }
        // 弧线环：UR 增加第四环 + 更宽描边
        val arcCount = if (rarity >= 4) 4 else 3
        repeat(arcCount) { i ->
            val r = (70f + i * 26f).dp.toPx()
            val start = (time * 130f + i * 120f) % 360f
            val strokeW = if (rarity >= 4) 2.5f else 2f
            drawArc(
                color = if (i % 2 == 0) CyberPalette.Cyan.copy(alpha = 0.5f) else CyberPalette.Magenta.copy(alpha = 0.4f),
                startAngle = start,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = strokeW.dp.toPx()),
            )
        }
        // 中心能量球：UR 更大更亮
        val pulse = 0.85f + 0.15f * sin(time * 5f)
        val baseRadius = if (rarity >= 4) 130f else 110f
        val radius = baseRadius.dp.toPx() * pulse
        val coreAlpha = if (rarity >= 4) listOf(
            CyberPalette.BeamCore, CyberPalette.Magenta.copy(alpha = 0.75f),
            CyberPalette.Cyan.copy(alpha = 0.3f), Color.Transparent,
        ) else listOf(
            CyberPalette.BeamCore, CyberPalette.Cyan.copy(alpha = 0.6f),
            CyberPalette.VioletGlow.copy(alpha = 0.15f), Color.Transparent,
        )
        drawCircle(
            brush = Brush.radialGradient(coreAlpha, center = Offset(cx, cy), radius = radius),
            radius = radius,
            center = Offset(cx, cy),
        )
    }
}
