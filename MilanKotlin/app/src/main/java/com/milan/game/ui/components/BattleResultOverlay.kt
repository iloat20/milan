package com.milan.game.ui.components

import androidx.compose.material3.MaterialTheme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.domain.battle.StrikeEvent
import com.milan.game.ui.effects.BattleHapticEffect
import com.milan.game.ui.effects.BattleHapticEvent
import com.milan.game.ui.theme.AppTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * 战斗结算全屏覆盖层（2026-09 战斗视觉增强）。
 *
 * 分镜：
 *   1. 背景遮罩淡入（200ms）
 *   2. 结果文字弹入（spring 弹跳）：「攻克！」「止步于此」
 *   3. 奖励数字滚动计数（500ms）
 *   4. 粒子效果：胜利=金箔飘落，失败=灰烬上升
 *   5. 底部操作按钮淡入
 *
 * @param victory 是否胜利
 * @param turns 回合数
 * @param rewardSoft 星尘奖励（0=无奖励）
 * @param rewardHard 钻石奖励（0=无奖励）
 * @param recordAdvanced 是否推进了纪录
 * @param bestFloorAfter 结算后最高层
 * @param onDismiss 关闭回调
 */
@Composable
fun BattleResultOverlay(
    victory: Boolean,
    turns: Int,
    rewardSoft: Int,
    rewardHard: Int,
    recordAdvanced: Boolean,
    bestFloorAfter: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    log: List<StrikeEvent> = emptyList(),
) {
    // ── 动画状态 ──
    val overlayAlpha = remember { Animatable(0f) }
    val titleScale = remember { Animatable(0.5f) }
    val contentAlpha = remember { Animatable(0f) }
    val buttonAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 阶段一：遮罩淡入
        overlayAlpha.animateTo(1f, tween(200))
        // 阶段二：标题弹入
        titleScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        // 阶段三：内容淡入
        contentAlpha.animateTo(1f, tween(300, delayMillis = 100))
        // 阶段四：按钮淡入
        buttonAlpha.animateTo(1f, tween(250, delayMillis = 200))
    }

    // 结算触觉：胜利/失败一次（接线 BattleHapticEffect，此前定义后零调用）
    BattleHapticEffect(
        event = if (victory) BattleHapticEvent.Victory else BattleHapticEvent.Defeat,
        isActive = true,
    )
    // 结算音效（第 8 节 P0）：资源缺失静默
    LaunchedEffect(Unit) {
        com.milan.game.infrastructure.MilanAudio.playSfx(
            if (victory) "battle_victory" else "battle_defeat",
        )
    }

    val primaryColor = if (victory) AppTheme.Gold else AppTheme.Text2
    val accentColor = if (victory) AppTheme.GoldHi else AppTheme.Text3

    // 失败去饱和（设计语言 P3）：内容层 ColorMatrix 从 1 动画到 0.15
    val saturate = remember { Animatable(1f) }
    LaunchedEffect(victory) {
        if (!victory) {
            saturate.animateTo(0.12f, tween(400))
        } else {
            saturate.snapTo(1f)
        }
    }
    val contentColorFilter = if (!victory) {
        ColorFilter.colorMatrix(
            ColorMatrix().apply { setToSaturation(saturate.value) },
        )
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(overlayAlpha.value)
            .background(Color.Black.copy(alpha = 0.75f))
            // R6-P2：等底部按钮淡入后再开放全屏 dismiss，防「点开结算那一下」误关
            .clickable(
                enabled = buttonAlpha.value > 0.9f,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // ── 粒子背景 ──
        BattleParticles(victory = victory, modifier = Modifier.fillMaxSize())

        // ── 伤害飘字回放（战斗日志逐条播放浮动数字）──
        if (log.isNotEmpty()) {
            DamageFloatingText(
                events = log,
                active = true,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    colorFilter = contentColorFilter
                },
                eventDelayMs = 120L,
                floatDurationMs = 700L,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .graphicsLayer { colorFilter = contentColorFilter },
        ) {
            // ── 结果标题 ──
            Text(
                text = if (victory) "✦ 攻克！" else "✖ 止步于此",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = primaryColor,
                modifier = Modifier
                    .scale(titleScale.value)
                    .graphicsLayer {
                        // 胜利时标题发光
                        if (victory) {
                            shadowElevation = 8f
                            this.alpha = 1f
                        }
                    },
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "用时 $turns 回合",
                color = accentColor,
                modifier = Modifier.alpha(contentAlpha.value),
            )

            Spacer(Modifier.height(24.dp))

            // ── 奖励展示 ──
            if (victory && (rewardSoft > 0 || rewardHard > 0)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .alpha(contentAlpha.value)
                        .clip(RoundedCornerShape(AppTheme.Roundness.lg))
                        .background(AppTheme.Surface.copy(alpha = 0.6f))
                        .border(1.dp, AppTheme.Gold.copy(alpha = 0.3f), RoundedCornerShape(AppTheme.Roundness.lg))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = "战利品",
                        style = MaterialTheme.typography.labelLarge,
                        color = AppTheme.Text3,
                    )
                    Spacer(Modifier.height(8.dp))

                    if (rewardSoft > 0) {
                        RewardRow(
                            icon = "✦",
                            label = "星尘",
                            amount = rewardSoft,
                            color = AppTheme.Gold,
                        )
                    }
                    if (rewardHard > 0) {
                        Spacer(Modifier.height(6.dp))
                        RewardRow(
                            icon = "◆",
                            label = "钻石",
                            amount = rewardHard,
                            color = AppTheme.Frost,
                        )
                    }

                    if (recordAdvanced) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "纪录推进至第 $bestFloorAfter 层",
                            style = MaterialTheme.typography.labelLarge,
                            color = AppTheme.GoldHi,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── 战斗统计面板 ──
            if (log.isNotEmpty()) {
                BattleStatsPanel(
                    log = log,
                    modifier = Modifier.alpha(contentAlpha.value),
                )
                Spacer(Modifier.height(10.dp))
                // v3 §7.4：战局重演时间轴
                BattleReplayTimeline(
                    log = log,
                    modifier = Modifier.alpha(contentAlpha.value),
                )
                Spacer(Modifier.height(10.dp))
            }

            // ── 操作提示 ──
            Text(
                text = "点击任意位置继续",
                style = MaterialTheme.typography.labelLarge,
                color = AppTheme.Text3,
                modifier = Modifier.alpha(buttonAlpha.value),
            )
        }
    }
}

/**
 * 奖励行：图标 + 标签 + 滚动计数数字。
 */
@Composable
private fun RewardRow(
    icon: String,
    label: String,
    amount: Int,
    color: Color,
) {
    val animatedAmount by animateIntAsState(
        targetValue = amount,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "rewardCount",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = icon, style = MaterialTheme.typography.titleLarge, color = color)
        Text(
            text = label,
            color = AppTheme.Text2,
        )
        Text(
            text = "+$animatedAmount",
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

/**
 * 战斗粒子效果：胜利=金箔飘落，失败=灰烬上升。
 * 纯 Canvas 实现，零依赖。
 */
@Composable
private fun BattleParticles(victory: Boolean, modifier: Modifier = Modifier) {
    // 动效减弱：结算只保留文字/淡入，不播粒子
    if (com.milan.game.ui.effects.LocalReduceMotion.current) return

    val particleCount = if (victory) 24 else 12
    val particles = remember(victory) {
        List(particleCount) { i ->
            ParticleData(
                x = (i * 37 + 13) % 100 / 100f,
                y = (i * 53 + 7) % 100 / 100f,
                size = 2f + (i % 4) * 1.5f,
                speed = 0.3f + (i % 3) * 0.2f,
                phase = (i * 1.2f) % (2f * Math.PI.toFloat()),
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particleTime",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        particles.forEach { p ->
            val px = (p.x * w + sin(time * p.speed + p.phase) * 20f) % w
            val py = if (victory) {
                // 金箔从上飘落
                ((p.y * h + time * p.speed * 40f) % (h + 40f)) - 20f
            } else {
                // 灰烬从下上升
                h - ((p.y * h + time * p.speed * 30f) % (h + 40f)) + 20f
            }

            val alpha = (0.3f + 0.4f * sin(time * 0.5f + p.phase)).coerceIn(0.1f, 0.7f)
            val color = if (victory) {
                AppTheme.Gold.copy(alpha = alpha)
            } else {
                AppTheme.Text3.copy(alpha = alpha * 0.5f)
            }

            drawCircle(
                color = color,
                radius = p.size,
                center = Offset(px, py),
            )
        }
    }
}

// ── 战斗统计面板（2026-09 战斗视觉增强）──

/**
 * 战斗统计面板：展示总伤害、克制次数、击杀数、最高单次伤害。
 * 从战斗日志 [StrikeEvent] 列表实时计算。
 */
@Composable
private fun BattleStatsPanel(
    log: List<StrikeEvent>,
    modifier: Modifier = Modifier,
) {
    // 统计数据计算
    val stats = remember(log) {
        val totalDamage = log.sumOf { it.damage }
        val counterHits = log.count {
            com.milan.game.domain.battle.ElementChart.damageMultiplier(it.attackerElement, it.targetElement) > 1.05
        }
        val defeats = log.count { it.targetDefeated }
        val maxHit = log.maxOfOrNull { it.damage } ?: 0
        val avgDamage = if (log.isNotEmpty()) totalDamage / log.size else 0
        BattleStats(totalDamage, counterHits, defeats, maxHit, avgDamage)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Roundness.md))
            .background(AppTheme.Surface.copy(alpha = 0.5f))
            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(AppTheme.Roundness.md))
            .padding(12.dp),
    ) {
        Text(
            text = "战 斗 统 计",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = AppTheme.Text3,
            letterSpacing = 0.15.sp,
        )
        Spacer(Modifier.height(8.dp))

        // 统计网格：2×3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatItem(label = "总伤害", value = "${stats.totalDamage}", color = AppTheme.Text1)
            StatItem(label = "克制", value = "${stats.counterHits}", color = AppTheme.Gold)
            StatItem(label = "击杀", value = "${stats.defeats}", color = AppTheme.SealRed)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatItem(label = "最高伤害", value = "${stats.maxHit}", color = AppTheme.Warning)
            StatItem(label = "平均伤害", value = "${stats.avgDamage}", color = AppTheme.Frost)
            StatItem(label = "攻击次数", value = "${log.size}", color = AppTheme.Text2)
        }
    }
}

/** 单个统计项：标签 + 数值。 */
@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = label,
            color = AppTheme.Text3,
        )
    }
}

/** 战斗统计数据容器。 */
private data class BattleStats(
    val totalDamage: Int,
    val counterHits: Int,
    val defeats: Int,
    val maxHit: Int,
    val avgDamage: Int,
)

private data class ParticleData(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float,
    val phase: Float,
)
