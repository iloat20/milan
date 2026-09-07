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
 * rarity 驱动光柱宽度与火花密度：UR 更宽更密，R 纤细快速。
 */
@Composable
internal fun RiftBeam(rarity: Int = 1, modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nano -> time = nano / 1_000_000_000f }
        }
    }
    // 稀有度驱动：UR 更多火花、更宽光柱
    val sparkCount = when { rarity >= 4 -> 40; rarity == 3 -> 32; else -> 26 }
    val beamWidth = when { rarity >= 4 -> 36f; rarity == 3 -> 30f; else -> 26f }
    val sparks = remember(rarity) {
        val r = Random(20260822)
        List(sparkCount) {
            CyberParticle(
                startX = 0.5f,
                startY = 1f,
                endX = 0.42f + r.nextFloat() * 0.16f,
                endY = r.nextFloat() * 0.85f,
                delaySec = r.nextFloat() * 0.5f,
                durSec = 0.45f + r.nextFloat() * 0.4f,
                radiusDp = 1.2f + r.nextFloat() * 2.2f + if (rarity >= 4) 0.6f else 0f,
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
        val progress = (time / 0.55f).coerceIn(0f, 1f)
        val e = 1f - (1f - progress) * (1f - progress)
        val beamH = size.height * e
        val w = beamWidth.dp.toPx()
        // 核心光柱
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
        // 外层辉光：UR 更宽
        val glowW = if (rarity >= 4) 110f else 84f
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, CyberPalette.VioletGlow.copy(alpha = 0.35f), Color.Transparent),
                startY = size.height - beamH,
                endY = size.height,
            ),
            topLeft = Offset(cx - glowW.dp.toPx(), size.height - beamH),
            size = Size(glowW.dp.toPx() * 2f, beamH),
        )
        // 底部能量球：UR 更大
        val orbR = if (rarity >= 4) 34f else 26f
        drawCircle(
            color = CyberPalette.Cyan.copy(alpha = 0.55f),
            radius = orbR.dp.toPx(),
            center = Offset(cx, size.height - beamH),
        )
        // 底部扩散环
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
        // 火花
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
