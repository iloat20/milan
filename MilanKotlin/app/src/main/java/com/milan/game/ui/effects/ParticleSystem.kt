package com.milan.game.ui.effects

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Canvas 粒子系统 — 抽卡/战斗特效核心。
 *
 * 轻量级 2D 粒子引擎，纯 Compose Canvas 实现，无外部依赖。
 * 性能优化：粒子池复用 + 生命周期感知 + 帧率控制。
 *
 * 特效类型：
 * - GachaStarBurst: 抽卡星爆（UR/SSR 光点汇聚）
 * - BattleHitSparks: 战斗打击火花
 * - RarityAura: 稀有度光环粒子
 * - ElementParticles: 元素属性粒子
 */

// ══════════════════════════════════════════════════════════════════════════════
// 粒子数据模型
// ══════════════════════════════════════════════════════════════════════════════

/** 单个粒子 */
data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,  // X速度
    var vy: Float,  // Y速度
    var life: Float, // 剩余生命（0~1）
    var maxLife: Float,
    var size: Float,
    var color: Color,
    var alpha: Float = 1f,
    var rotation: Float = 0f,
    var rotationSpeed: Float = 0f,
)

/** 粒子发射器配置 */
data class EmitterConfig(
    val maxParticles: Int = 100,
    val emitRate: Float = 20f, // 每秒发射数
    val gravity: Float = 0f,
    val fadeOut: Boolean = true,
    val shrink: Boolean = true,
    val colors: List<Color> = listOf(Color.White),
    val minSize: Float = 2f,
    val maxSize: Float = 8f,
    val minLife: Float = 0.5f,
    val maxLife: Float = 1.5f,
    val speed: Float = 100f,
    val spread: Float = 360f, // 发射角度范围
    val angle: Float = 0f,   // 发射中心角度
)

// ══════════════════════════════════════════════════════════════════════════════
// 粒子系统核心
// ══════════════════════════════════════════════════════════════════════════════

/**
 * 通用粒子系统 Composable。
 *
 * @param config 发射器配置
 * @param active 是否激活
 * @param modifier 修饰符
 * @param emitPosition 发射位置（默认中心）
 */
@Composable
fun ParticleSystem(
    config: EmitterConfig,
    active: Boolean = true,
    modifier: Modifier = Modifier,
    emitPosition: Offset = Offset.Unspecified,
    emitFromCenter: Boolean = false,
    emitFromCenterBottom: Boolean = false,
) {
    // 动效减弱：整层粒子直接不组合（设计语言 P3 无障碍）
    if (LocalReduceMotion.current) return

    val particles = remember { mutableStateListOf<Particle>() }
    val random = remember { Random(System.currentTimeMillis()) }

    // 帧时间追踪
    var lastFrameNanos = remember { 0L }
    var emitAccumulator = remember { 0f }
    // 画布尺寸（首帧由 Canvas 回写；emitFromCenter* 依赖）
    var canvasW by remember { mutableFloatStateOf(0f) }
    var canvasH by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect

        while (true) {
            withFrameNanos { frameNanos ->
                val deltaSeconds = if (lastFrameNanos == 0L) 0.016f
                    else (frameNanos - lastFrameNanos) / 1_000_000_000f
                lastFrameNanos = frameNanos

                val resolvedEmit = when {
                    emitFromCenter && canvasW > 0f -> Offset(canvasW / 2f, canvasH / 2f)
                    emitFromCenterBottom && canvasW > 0f -> Offset(canvasW / 2f, canvasH * 0.92f)
                    emitPosition != Offset.Unspecified -> emitPosition
                    else -> Offset(canvasW / 2f, canvasH / 2f).takeIf { canvasW > 0f } ?: return@withFrameNanos
                }

                // 发射新粒子
                emitAccumulator += config.emitRate * deltaSeconds
                while (emitAccumulator >= 1f && particles.size < config.maxParticles) {
                    emitAccumulator -= 1f
                    particles.add(createParticle(config, resolvedEmit, random))
                }

                // 更新粒子
                val iterator = particles.iterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()
                    p.life -= deltaSeconds / p.maxLife

                    if (p.life <= 0f) {
                        iterator.remove()
                        continue
                    }

                    // 物理更新
                    p.vy += config.gravity * deltaSeconds
                    p.x += p.vx * deltaSeconds
                    p.y += p.vy * deltaSeconds
                    p.rotation += p.rotationSpeed * deltaSeconds

                    // 生命周期外观
                    val lifeRatio = p.life
                    p.alpha = if (config.fadeOut) lifeRatio else 1f
                    p.size = if (config.shrink) p.size * lifeRatio else p.size
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                canvasW = size.width.toFloat()
                canvasH = size.height.toFloat()
            },
    ) {
        for (p in particles) {
            drawParticle(p)
        }
    }
}

/** 创建新粒子 */
private fun createParticle(
    config: EmitterConfig,
    emitPosition: Offset,
    random: Random
): Particle {
    val angle = Math.toRadians(
        (config.angle - config.spread / 2f + random.nextFloat() * config.spread).toDouble()
    ).toFloat()
    val speed = config.speed * (0.5f + random.nextFloat() * 0.5f)

    return Particle(
        x = emitPosition.x,
        y = emitPosition.y,
        vx = cos(angle) * speed,
        vy = sin(angle) * speed,
        life = 1f,
        maxLife = config.minLife + random.nextFloat() * (config.maxLife - config.minLife),
        size = config.minSize + random.nextFloat() * (config.maxSize - config.minSize),
        color = config.colors[random.nextInt(config.colors.size)],
        rotationSpeed = (random.nextFloat() - 0.5f) * 360f,
    )
}

/** 绘制单个粒子 */
private fun DrawScope.drawParticle(p: Particle) {
    val alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
    val color = p.color.copy(alpha = alpha / 255f)

    // 发光效果：外圈模糊 + 内圈实心
    val glowRadius = (p.size * 2f).coerceAtLeast(1f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = Offset(p.x, p.y),
            radius = glowRadius
        ),
        radius = glowRadius,
        center = Offset(p.x, p.y)
    )
    drawCircle(
        color = color,
        radius = p.size,
        center = Offset(p.x, p.y)
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// 抽卡特效预设
// ══════════════════════════════════════════════════════════════════════════════

/** 抽卡星爆（光点从中心向外扩散）。[emitFromCenter] 在首帧取画布中心，避免 Offset.Unspecified 产生 NaN 粒子。 */
@Composable
fun GachaStarBurst(
    rarity: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
    emitFromCenter: Boolean = true,
) {
    val colors = rarityEffectColors(rarity)

    val config = EmitterConfig(
        maxParticles = when (rarity) { 4 -> 150; 3 -> 100; else -> 60 },
        emitRate = when (rarity) { 4 -> 80f; 3 -> 50f; else -> 30f },
        gravity = 20f,
        colors = colors,
        minSize = 1f,
        maxSize = when (rarity) { 4 -> 6f; 3 -> 4f; else -> 3f },
        minLife = 0.4f,
        maxLife = when (rarity) { 4 -> 1.8f; 3 -> 1.2f; else -> 0.8f },
        speed = when (rarity) { 4 -> 250f; 3 -> 180f; else -> 120f },
        spread = 360f,
        angle = 0f,
    )

    ParticleSystem(
        config = config,
        active = active,
        modifier = modifier,
        emitFromCenter = emitFromCenter,
    )
}

/** 抽卡光柱（垂直向上喷射）。[emitFromCenterBottom] 自画布底边中点发射。 */
@Composable
fun GachaBeamParticles(
    rarity: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
    emitFromCenterBottom: Boolean = true,
) {
    val colors = rarityEffectColors(rarity)

    val config = EmitterConfig(
        maxParticles = when (rarity) { 4 -> 80; 3 -> 50; else -> 30 },
        emitRate = 40f,
        gravity = -150f, // 向上
        colors = colors,
        minSize = 1f,
        maxSize = 4f,
        minLife = 0.3f,
        maxLife = 1.0f,
        speed = 200f,
        spread = 30f,
        angle = 270f, // 向上
    )

    ParticleSystem(
        config = config,
        active = active,
        modifier = modifier,
        emitFromCenterBottom = emitFromCenterBottom,
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// 战斗特效预设
// ══════════════════════════════════════════════════════════════════════════════

/** 战斗打击火花 */
@Composable
fun BattleHitSparks(
    isCritical: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = if (isCritical) {
        listOf(Color(0xFFFFD700), Color(0xFFFF4500), Color.White)
    } else {
        listOf(Color(0xFFFF6347), Color(0xFFFFA500), Color.White)
    }

    val config = EmitterConfig(
        maxParticles = if (isCritical) 60 else 30,
        emitRate = if (isCritical) 100f else 50f,
        gravity = 200f,
        colors = colors,
        minSize = 1f,
        maxSize = if (isCritical) 4f else 2f,
        minLife = 0.2f,
        maxLife = 0.5f,
        speed = if (isCritical) 300f else 150f,
        spread = 180f,
        angle = 0f,
    )

    ParticleSystem(config = config, active = active, modifier = modifier)
}

/** 稀有度光环粒子（角色周围漂浮） */
@Composable
fun RarityAuraParticles(
    rarity: Int,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = rarityEffectColors(rarity).take(2)

    val config = EmitterConfig(
        maxParticles = 40,
        emitRate = 10f,
        gravity = -10f, // 轻微上浮
        colors = colors,
        minSize = 1f,
        maxSize = 3f,
        minLife = 1.0f,
        maxLife = 2.0f,
        speed = 20f,
        spread = 360f,
        angle = 0f,
    )

    ParticleSystem(config = config, active = active, modifier = modifier)
}

/** 元素属性粒子 */
@Composable
fun ElementParticles(
    element: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = elementEffectColors(element)

    val config = EmitterConfig(
        maxParticles = 50,
        emitRate = 15f,
        gravity = -20f, // 轻微上浮
        colors = colors,
        minSize = 1f,
        maxSize = 4f,
        minLife = 0.8f,
        maxLife = 1.5f,
        speed = 40f,
        spread = 360f,
        angle = 0f,
    )

    ParticleSystem(config = config, active = active, modifier = modifier)
}
