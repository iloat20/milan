package com.milan.game.ui.arena

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milan.game.data.ArenaOpponent
import com.milan.game.data.ArenaSaveData
import com.milan.game.services.ArenaChallengeOutcome
import com.milan.game.ui.components.GlassDialog
import com.milan.game.ui.components.ArtifactPanel
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.components.InkButton
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme

/**
 * PVP 竞技场界面（2026-09 死功能激活）。
 *
 * 服务层 [com.milan.game.services.ArenaService] 已实现完整闭环：
 * - 积分/段位管理、每日 5 次免费挑战、防守阵容、赛季奖励。
 * - 挑战走 BattleSimulator 模拟 + WriteOutcome 事务范式。
 *
 * 本页负责：展示段位信息、对手列表、挑战操作。
 * 2026-09-09 P1-6 D 批：派生数据与挑战动作收敛进 [ArenaViewModel]。
 */
@Composable
fun ArenaScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedback = LocalFeedback.current
    // P1-6 D 批：段位/积分/剩余次数/战绩/对手/赛季奖励 + 挑战动作全部在 ArenaViewModel
    //（busy 防连点在 VM 内部完成，无需 UI 收集）。
    val vm: ArenaViewModel = viewModel(factory = com.milan.game.di.AppGraph.factory)
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val lastResult by vm.lastResult.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.toasts.collect { feedback.show(it) } }
    val rankTitle = ui.rankTitle
    val opponents = ui.opponents
    val seasonRewards = ui.seasonRewards
    val attacksLeft = ui.attacksLeft

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.BgDeepest),
    ) {
        PageBackground {
            Column(modifier = Modifier.fillMaxSize()) {
                AppTopBar(title = "竞技场", onBack = onBack)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                ) {
                    // 段位信息卡片
                    item {
                        ArenaRankCard(
                            rankTitle = rankTitle,
                            points = ui.points,
                            attacksLeft = attacksLeft,
                            winCount = ui.winCount,
                            loseCount = ui.loseCount,
                        )
                    }

                    // 对手列表标题
                    item {
                        Text(
                            text = "可挑战对手",
                            color = AppTheme.Text1,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // 对手列表
                    items(opponents, key = { it.characterId }) { opponent ->
                        OpponentCard(
                            opponent = opponent,
                            canChallenge = attacksLeft > 0,
                            onChallenge = { vm.challenge(opponent) },
                        )
                    }

                    // 赛季奖励
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "赛季奖励",
                            color = AppTheme.Text1,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(seasonRewards) { reward ->
                        SeasonRewardRow(reward)
                    }
                }
            }
        }

        // 挑战结算卡（胜负 / 回合 / 积分变动）
        lastResult?.let { result ->
            ArenaResultDialog(
                result = result,
                onDismiss = { vm.dismissResult() },
            )
        }
    }
}

/** 挑战结算对话框：对齐爬塔结算的信息密度（胜负/回合/积分），轻量 GlassDialog 版。 */
@Composable
private fun ArenaResultDialog(
    result: ArenaChallengeOutcome.Completed,
    onDismiss: () -> Unit,
) {
    val title = if (result.victory) "✦ 挑战胜利" else "✖ 挑战失败"
    val deltaSign = if (result.pointsDelta >= 0) "+" else ""
    val body = buildString {
        append("对手：${result.opponentName}\n")
        append("回合：${result.turns}\n")
        append("积分：$deltaSign${result.pointsDelta}（当前 ${result.pointsAfter}）")
    }
    GlassDialog(
        show = true,
        onDismiss = onDismiss,
        title = title,
        body = body,
        buttons = {
            GoldButton(
                text = "知道了",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/** 段位信息卡片。 */
@Composable
private fun ArenaRankCard(
    rankTitle: String,
    points: Int,
    attacksLeft: Int,
    winCount: Int,
    loseCount: Int,
) {
    ArtifactPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = rankTitle,
                        color = AppTheme.Gold,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "积分 $points",
                        color = AppTheme.Text2,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "剩余次数 $attacksLeft / ${ArenaSaveData.DAILY_FREE_ATTACKS}",
                        color = if (attacksLeft > 0) AppTheme.Text2 else AppTheme.Danger,
                    )
                    Text(
                        text = "胜 $winCount / 负 $loseCount",
                        color = AppTheme.Text3,
                    )
                }
            }
        }
    }
}

/** 单个对手卡片。 */
@Composable
private fun OpponentCard(
    opponent: ArenaOpponent,
    canChallenge: Boolean,
    onChallenge: () -> Unit,
) {
    ArtifactPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = opponent.name,
                        color = AppTheme.Text1,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Lv.${opponent.level}",
                        color = AppTheme.Text3,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        text = "积分 ${opponent.points}",
                        color = AppTheme.Text2,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "战力 ${opponent.teamPower}",
                        color = AppTheme.Text2,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            InkButton(
                text = "挑战",
                enabled = canChallenge,
                onClick = onChallenge,
            )
        }
    }
}

/** 赛季奖励行。 */
@Composable
private fun SeasonRewardRow(reward: com.milan.game.data.SeasonReward) {
    ArtifactPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Top ${reward.rank}",
                color = AppTheme.Gold,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(60.dp),
            )
            Text(
                text = reward.title,
                color = AppTheme.Text1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${reward.softCurrency} 金",
                color = AppTheme.Text2,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${reward.hardCurrency} 碎片",
                color = AppTheme.Text2,
            )
        }
    }
}
