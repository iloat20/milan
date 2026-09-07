package com.milan.game.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.domain.battle.ElementChart
import com.milan.game.domain.battle.StrikeEvent
import com.milan.game.domain.battle.UnitStats
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme
import kotlinx.coroutines.delay

/**
 * 战斗回放组件（2026-09 战斗视觉增强）。
 *
 * 逐步播放战斗日志，展示：
 * - 我方/敌方 HP 条实时变化
 * - 当前攻击者高亮
 * - 伤害数字弹出
 * - 克制/击杀特效
 *
 * @param log 战斗日志
 * @param myTeamStats 我方队伍初始属性（用于 HP 条）
 * @param enemyTeamStats 敌方队伍初始属性
 * @param autoPlay 是否自动播放
 * @param playSpeed 播放速度（毫秒/步）
 * @param onReplayComplete 回放完成回调
 */
@Composable
fun BattleReplay(
    log: List<StrikeEvent>,
    myTeamStats: List<UnitStats>,
    enemyTeamStats: List<UnitStats>,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    playSpeed: Long = 600L,
    onReplayComplete: (() -> Unit)? = null,
) {
    if (log.isEmpty()) return

    // 当前播放步数
    var currentStep by remember { mutableIntStateOf(0) }
    val isPlaying = remember { mutableStateOf(autoPlay) }

    // 我方/敌方 HP 状态（可变）
    val myHpMap = remember(myTeamStats) {
        myTeamStats.associate { it.characterId to it.hp }.toMutableMap()
    }
    val enemyHpMap = remember(enemyTeamStats) {
        enemyTeamStats.associate { it.characterId to it.hp }.toMutableMap()
    }

    // 当前攻击事件
    val currentEvent = remember(currentStep, log) {
        if (currentStep in log.indices) log[currentStep] else null
    }

    // 自动播放
    LaunchedEffect(isPlaying.value, currentStep) {
        if (isPlaying.value && currentStep < log.size) {
            delay(playSpeed)
            // 应用当前步的 HP 变化
            val event = log[currentStep]
            val targetMap = if (event.targetId.startsWith("tower_f")) enemyHpMap else myHpMap
            val currentHp = targetMap[event.targetId] ?: 0
            targetMap[event.targetId] = (currentHp - event.damage).coerceAtLeast(0)

            currentStep++
            if (currentStep >= log.size) {
                isPlaying.value = false
                onReplayComplete?.invoke()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.Surface.copy(alpha = 0.6f))
            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        // 标题
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "战 斗 回 放",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Gold,
                letterSpacing = 0.15.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${currentStep}/${log.size}",
                fontSize = 10.sp,
                color = AppTheme.Text3,
            )
        }

        Spacer(Modifier.height(8.dp))

        // 进度条
        val progress = if (log.isNotEmpty()) currentStep.toFloat() / log.size else 0f
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(200, easing = LinearEasing),
            label = "replayProgress",
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AppTheme.BgDeepest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(AppTheme.Gold, AppTheme.GoldHi)
                        )
                    ),
            )
        }

        Spacer(Modifier.height(10.dp))

        // 我方 HP 条
        Text(
            text = "我方",
            fontSize = 10.sp,
            color = AppTheme.Frost,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        myTeamStats.forEach { stats ->
            val currentHp = myHpMap[stats.characterId] ?: stats.hp
            val isTarget = currentEvent?.targetId == stats.characterId && !stats.characterId.startsWith("tower_f")
            val isAttacker = currentEvent?.attackerId == stats.characterId
            LabeledHealthBar(
                label = stats.characterId.substringAfter('_').take(6),
                currentHp = currentHp,
                maxHp = stats.hp,
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .then(
                        if (isTarget) Modifier
                            .border(1.dp, AppTheme.Danger, RoundedCornerShape(4.dp))
                            .padding(1.dp)
                        else if (isAttacker) Modifier
                            .border(1.dp, AppTheme.Frost, RoundedCornerShape(4.dp))
                            .padding(1.dp)
                        else Modifier
                    ),
            )
        }

        Spacer(Modifier.height(8.dp))

        // 敌方 HP 条
        Text(
            text = "敌方",
            fontSize = 10.sp,
            color = AppTheme.Danger,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        enemyTeamStats.forEach { stats ->
            val currentHp = enemyHpMap[stats.characterId] ?: stats.hp
            val isTarget = currentEvent?.targetId == stats.characterId && stats.characterId.startsWith("tower_f")
            val isAttacker = currentEvent?.attackerId == stats.characterId && stats.characterId.startsWith("tower_f")
            val ei = ElementTheme.forElement(stats.element)
            LabeledHealthBar(
                label = "${ei.glyph} 敌方",
                currentHp = currentHp,
                maxHp = stats.hp,
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .then(
                        if (isTarget) Modifier
                            .border(1.dp, AppTheme.Danger, RoundedCornerShape(4.dp))
                            .padding(1.dp)
                        else if (isAttacker) Modifier
                            .border(1.dp, AppTheme.Danger.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(1.dp)
                        else Modifier
                    ),
            )
        }

        // 当前攻击事件显示
        AnimatedVisibility(
            visible = currentEvent != null,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(100)),
        ) {
            currentEvent?.let { event ->
                StrikeEventBubble(event = event)
            }
        }

        Spacer(Modifier.height(8.dp))

        // 控制按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            // 播放/暂停按钮
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppTheme.Gold.copy(alpha = 0.2f))
                    .border(1.dp, AppTheme.Gold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .then(
                        Modifier.run {
                            if (currentStep < log.size) {
                                this.then(
                                    Modifier.graphicsLayer { }
                                )
                            } else this
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        currentStep >= log.size -> "回放完成"
                        isPlaying.value -> "暂停"
                        else -> "播放"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Gold,
                )
            }

            if (currentStep > 0 && currentStep < log.size) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppTheme.Surface)
                        .border(1.dp, AppTheme.Stroke, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "跳过",
                        fontSize = 11.sp,
                        color = AppTheme.Text3,
                    )
                }
            }
        }
    }
}

/**
 * 单条攻击事件气泡：显示攻击者 → 目标 + 伤害 + 特效。
 */
@Composable
private fun StrikeEventBubble(event: StrikeEvent) {
    val counterMul = ElementChart.damageMultiplier(event.attackerElement, event.targetElement)
    val isCounter = counterMul > 1.05
    val isDefeated = event.targetDefeated

    val attackerEi = remember(event.attackerElement) { ElementTheme.forElement(event.attackerElement) }
    val targetEi = remember(event.targetElement) { ElementTheme.forElement(event.targetElement) }

    val damageColor = when {
        isDefeated -> AppTheme.SealRed
        isCounter -> AppTheme.Gold
        event.damage > 200 -> AppTheme.Warning
        else -> AppTheme.Text1
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isDefeated) AppTheme.Danger.copy(alpha = 0.1f)
                else if (isCounter) AppTheme.Gold.copy(alpha = 0.08f)
                else AppTheme.BgDeepest.copy(alpha = 0.3f)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // 攻击者
        Text(
            text = attackerEi.glyph,
            fontSize = 12.sp,
            color = attackerEi.glow,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = unitLabelShort(event.attackerId),
            fontSize = 10.sp,
            color = AppTheme.Text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(48.dp),
        )

        // 箭头 + 伤害
        Text(
            text = "→",
            fontSize = 10.sp,
            color = AppTheme.Text3,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        // 目标
        Text(
            text = targetEi.glyph,
            fontSize = 12.sp,
            color = targetEi.glow,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = unitLabelShort(event.targetId),
            fontSize = 10.sp,
            color = if (isDefeated) AppTheme.Danger else AppTheme.Text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(48.dp),
        )

        Spacer(Modifier.weight(1f))

        // 伤害值
        Text(
            text = "-${event.damage}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = damageColor,
        )

        // 特效标记
        if (isCounter) {
            Text(
                text = " 克制",
                fontSize = 9.sp,
                color = AppTheme.Gold,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppTheme.Gold.copy(alpha = 0.15f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        if (isDefeated) {
            Text(
                text = " 击杀",
                fontSize = 9.sp,
                color = AppTheme.SealRed,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppTheme.SealRed.copy(alpha = 0.15f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/** 简化单位标签（取 ID 最后一段）。 */
private fun unitLabelShort(characterId: String): String = when {
    characterId.startsWith("tower_f") -> {
        val parts = characterId.split("_")
        "敌方${parts.getOrNull(2)?.removePrefix("e") ?: ""}"
    }
    else -> characterId.substringAfter('_').take(6).ifEmpty { characterId.take(6) }
}
