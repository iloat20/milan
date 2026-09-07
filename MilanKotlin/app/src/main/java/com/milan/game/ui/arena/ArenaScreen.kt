package com.milan.game.ui.arena

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milan.game.data.ArenaOpponent
import com.milan.game.data.ArenaSaveData
import com.milan.game.services.WriteOutcome
import com.milan.game.ui.GameState
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.NeonButton
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * PVP 竞技场界面（2026-09 死功能激活）。
 *
 * 服务层 [com.milan.game.services.ArenaService] 已实现完整闭环：
 * - 积分/段位管理、每日 5 次免费挑战、防守阵容、赛季奖励。
 * - 挑战走 BattleSimulator 模拟 + WriteOutcome 事务范式。
 *
 * 本页负责：展示段位信息、对手列表、挑战操作。
 */
@Composable
fun ArenaScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val service = GameState.service
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val snapshot by service.snapshot.collectAsStateWithLifecycle()

    // 竞技场数据随 snapshot.revision 重算
    val arenaData = remember(snapshot.revision) { service.getArenaData() }
    val (rankNum, rankTitle) = remember(snapshot.revision) { service.getArenaRank() }
    val opponents = remember(snapshot.revision) { service.getOpponents() }
    val seasonRewards = remember(snapshot.revision) { service.getSeasonRewards() }

    // 每日剩余挑战次数（跨日重置与 ArenaService.lastRefreshTime 同口径：epochDay = millis / 86_400_000）
    val todayKey = System.currentTimeMillis() / 86_400_000L
    val attacksUsed = if (arenaData.lastRefreshTime == todayKey) arenaData.attackCount else 0
    val attacksLeft = (ArenaSaveData.DAILY_FREE_ATTACKS - attacksUsed).coerceAtLeast(0)

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
                            points = arenaData.arenaPoints,
                            attacksLeft = attacksLeft,
                            winCount = arenaData.winCount,
                            loseCount = arenaData.loseCount,
                        )
                    }

                    // 对手列表标题
                    item {
                        Text(
                            text = "可挑战对手",
                            color = AppTheme.Text1,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // 对手列表
                    items(opponents, key = { it.characterId }) { opponent ->
                        OpponentCard(
                            opponent = opponent,
                            canChallenge = attacksLeft > 0,
                            onChallenge = {
                                scope.launch {
                                    when (service.challengeOpponent(opponent)) {
                                        WriteOutcome.Success -> feedback.show("挑战完成！")
                                        WriteOutcome.Rejected -> feedback.show("挑战次数不足或未编队")
                                        WriteOutcome.SaveFailed -> feedback.show("保存失败，请重试")
                                    }
                                }
                            },
                        )
                    }

                    // 赛季奖励
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "赛季奖励",
                            color = AppTheme.Text1,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(seasonRewards) { reward ->
                        SeasonRewardRow(reward)
                    }
                }
            }
        }
    }
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
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
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
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "积分 $points",
                        color = AppTheme.Text2,
                        fontSize = 12.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "剩余次数 $attacksLeft / ${ArenaSaveData.DAILY_FREE_ATTACKS}",
                        color = if (attacksLeft > 0) AppTheme.Text2 else AppTheme.Danger,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "胜 $winCount / 负 $loseCount",
                        color = AppTheme.Text3,
                        fontSize = 11.sp,
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
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Lv.${opponent.level}",
                        color = AppTheme.Text3,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        text = "积分 ${opponent.points}",
                        color = AppTheme.Text2,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "战力 ${opponent.teamPower}",
                        color = AppTheme.Text2,
                        fontSize = 11.sp,
                    )
                }
            }
            NeonButton(
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
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Top ${reward.rank}",
                color = AppTheme.Gold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(60.dp),
            )
            Text(
                text = reward.title,
                color = AppTheme.Text1,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${reward.softCurrency} 金",
                color = AppTheme.Text2,
                fontSize = 11.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${reward.hardCurrency} 碎片",
                color = AppTheme.Text2,
                fontSize = 11.sp,
            )
        }
    }
}
