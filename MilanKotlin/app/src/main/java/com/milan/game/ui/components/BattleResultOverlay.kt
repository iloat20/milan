package com.milan.game.ui.components

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.domain.battle.StrikeEvent
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

    val primaryColor = if (victory) AppTheme.Gold else AppTheme.Text2
    val accentColor = if (victory) AppTheme.GoldHi else AppTheme.Text3

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(overlayAlpha.value)
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
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
                modifier = Modifier.fillMaxSize(),
                eventDelayMs = 120L,
                floatDurationMs = 700L,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            // ── 结果标题 ──
            Text(
                text = if (victory) "✦ 攻克！" else "✖ 止步于此",
                fontSize = 36.sp,
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
                fontSize = 14.sp,
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppTheme.Surface.copy(alpha = 0.6f))
                        .border(1.dp, AppTheme.Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = "战利品",
                        fontSize = 12.sp,
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
                            fontSize = 12.sp,
                            color = AppTheme.GoldHi,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── 操作提示 ──
            Text(
                text = "点击任意位置继续",
                fontSize = 12.sp,
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
        Text(text = icon, fontSize = 18.sp, color = color)
        Text(
            text = label,
            fontSize = 13.sp,
            color = AppTheme.Text2,
        )
        Text(
            text = "+$animatedAmount",
            fontSize = 18.sp,
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

private data class ParticleData(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float,
    val phase: Float,
)
