package com.milan.game.ui.effects

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.milan.game.infrastructure.HapticManager

/**
 * Compose 触觉反馈扩展函数集。
 *
 * 用法：
 * - Modifier.hapticClick { action }  — 按钮点击触觉 + 回调
 * - Modifier.hapticToggle(state)     — 开关切换触觉
 * - GachaHapticEffect(revealState)   — 抽卡阶段触觉自动化
 * - BattleHapticEffect(event)        — 战斗事件触觉
 */

/**
 * 按钮点击触觉反馈修饰符。
 * 点击时触发轻触振动 + 执行回调。
 */
fun Modifier.hapticClick(
    onClick: () -> Unit
): Modifier = composed {
    val view = LocalView.current
    this.then(
        Modifier.semantics {
            role = Role.Button
        }.clickable {
            HapticManager.buttonClick(view)
            onClick()
        }
    )
}

/**
 * 开关切换触觉反馈修饰符。
 */
fun Modifier.hapticToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
): Modifier = composed {
    val view = LocalView.current
    this.then(
        Modifier.clickable {
            HapticManager.toggleSwitch(view)
            onToggle(!enabled)
        }
    )
}

/**
 * 抽卡阶段触觉自动化。
 *
 * 根据 RevealStage 自动触发对应触觉：
 * - Charge: 蓄力脉冲
 * - Beam: 光柱爆发（中等确认）
 * - Single/Ten: 揭晓（根据稀有度分级）
 */
@Composable
fun GachaHapticEffect(
    stage: RevealStage,
    rarity: Int,
    isActive: Boolean
) {
    val view = LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(stage, rarity, isActive) {
        if (!isActive) return@LaunchedEffect

        when (stage) {
            RevealStage.Charge -> {
                HapticManager.gachaCharge(context)
            }
            RevealStage.Beam -> {
                HapticManager.gachaReveal(view, rarity)
            }
            RevealStage.Single, RevealStage.Ten -> {
                HapticManager.gachaReveal(view, rarity)
                if (rarity >= 4) {
                    // UR: 额外丰富振动
                    HapticManager.gachaUrSpecial(context)
                }
            }
            RevealStage.Idle -> { }
        }
    }
}

/**
 * 战斗事件触觉自动化。
 */
@Composable
fun BattleHapticEffect(
    event: BattleHapticEvent?,
    isActive: Boolean = true
) {
    val view = LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(event, isActive) {
        if (!isActive || event == null) return@LaunchedEffect

        when (event) {
            is BattleHapticEvent.CriticalHit -> {
                HapticManager.battleCriticalHit(context)
            }
            is BattleHapticEvent.Victory -> {
                HapticManager.battleVictory(view)
            }
            is BattleHapticEvent.Defeat -> {
                HapticManager.battleDefeat(context)
            }
            is BattleHapticEvent.LevelUp -> {
                HapticManager.levelUp(view)
            }
            is BattleHapticEvent.Breakthrough -> {
                HapticManager.breakthrough(context)
            }
            is BattleHapticEvent.Error -> {
                HapticManager.errorBuzz(view)
            }
        }
    }
}

/** 抽卡阶段枚举 */
enum class RevealStage {
    Idle, Charge, Beam, Single, Ten
}

/** 战斗触觉事件密封类 */
sealed class BattleHapticEvent {
    data object CriticalHit : BattleHapticEvent()
    data object Victory : BattleHapticEvent()
    data object Defeat : BattleHapticEvent()
    data object LevelUp : BattleHapticEvent()
    data object Breakthrough : BattleHapticEvent()
    data object Error : BattleHapticEvent()
}


