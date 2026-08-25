package com.milan.game.ui.tower

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milan.game.domain.progression.EconomyFormulas
import com.milan.game.services.TowerOutcome
import com.milan.game.ui.GameState
import com.milan.game.ui.components.FormationBar
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.components.NeonButton
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * 无尽之塔（2026-08 终局内容，对标 StS 进阶难度 / Balatro 无尽模式的长线留存定位）：
 * - 挑战「最高层 +1」；胜利推进纪录并发放星尘（数值走 EconomyFormulas 单一事实来源）；
 * - 敌队按层数程序化生成且元素随机分布——编队的元素克制与共鸣成为爬层策略；
 * - 空编队时引导去卡组页组队；战斗为服务层纯模拟，本页只做状态与结果呈现。
 */
@Composable
fun TowerScreen(
    onBack: () -> Unit,
    onOpenDeck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snapshot by GameState.service.snapshot.collectAsStateWithLifecycle()
    val best = snapshot.towerBestFloor
    val nextFloor = best + 1
    // 门票门槛（2026-08 二期）：入场扣票、胜利返票；票源走商店每日补给
    val ticketCost = EconomyFormulas.towerTicketCost()
    val tickets = snapshot.battleTickets
    val canChallenge = tickets >= ticketCost

    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<TowerOutcome?>(null) }

    // 编队成员视图（快照 revision 触发重算；战力/元素预览同口径 StatsCalculator）
    val members = remember(snapshot.revision) {
        val formed = snapshot.formation.toSet()
        GameState.owned().filter { it.save.characterId in formed }
    }
    val teamPower = members.sumOf { GameState.computeStats(it).atk }

    PageBackground(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(title = "无 尽 之 塔", onBack = onBack)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
            ) {
                Spacer(Modifier.height(10.dp))

                // ── 纪录卡：当前最高层 + 编队战力 ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppTheme.Surface.copy(alpha = 0.7f))
                        .border(1.dp, AppTheme.Stroke, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Text(text = "历史最高", fontSize = 12.sp, color = AppTheme.Text2)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (best == 0) "未挑战" else "第 $best 层",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.Text1,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "编队战力 $teamPower · ⚔ $tickets",
                            fontSize = 12.sp,
                            color = AppTheme.Text2,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                FormationBar(
                    members = members,
                    maxSlots = com.milan.game.data.SaveData.MAX_FORMATION_SIZE,
                    onSlotClick = { onOpenDeck() },
                )

                Spacer(Modifier.height(16.dp))

                if (snapshot.formation.isEmpty()) {
                    Text(
                        text = "还没有出战编队，先去卡组页点选角色入队。",
                        fontSize = 13.sp,
                        color = AppTheme.Text3,
                    )
                    Spacer(Modifier.height(10.dp))
                    NeonButton(text = "前往编队", onClick = onOpenDeck, modifier = Modifier.fillMaxWidth())
                } else {
                    // 下一层挑战：奖励预览按 EconomyFormulas 计算，禁止就地写数字。
                    Text(
                        text = "第 $nextFloor 层 · 入场 ⚔$ticketCost · 预计通关星尘 ${EconomyFormulas.towerRewardSoft(nextFloor)}",
                        fontSize = 12.sp,
                        color = AppTheme.Text2,
                    )
                    if (!canChallenge) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "战票不足（需 $ticketCost 张）——去商店「每日补给」免费领取",
                            fontSize = 12.sp,
                            color = AppTheme.Gold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    GoldButton(
                        text = when {
                            running -> "战斗中…"
                            !canChallenge -> "战票不足"
                            else -> "⚔ 挑战第 $nextFloor 层"
                        },
                        onClick = {
                            if (running || !canChallenge) return@GoldButton
                            running = true
                            scope.launch {
                                result = GameState.service.runTowerFloor(nextFloor)
                                running = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canChallenge && !running,
                    )
                    // 已通层的复刷入口：低层速刷拿保底星尘收益（数值线性，低层仍有意义）。
                    if (best > 0) {
                        Spacer(Modifier.height(8.dp))
                        NeonButton(
                            text = "复刷第 $best 层（⚔$ticketCost）",
                            onClick = {
                                if (running || !canChallenge) return@NeonButton
                                running = true
                                scope.launch {
                                    result = GameState.service.runTowerFloor(best)
                                    running = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canChallenge && !running,
                        )
                    }
                }

                // ── 结算卡 ──
                (result as? TowerOutcome.Completed)?.let { done ->
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (done.victory) AppTheme.Gold.copy(alpha = 0.12f) else AppTheme.Surface)
                            .border(
                                1.dp,
                                if (done.victory) AppTheme.Gold.copy(alpha = 0.6f) else AppTheme.Stroke,
                                RoundedCornerShape(14.dp),
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (done.victory) "✦ 攻克！用时 ${done.turns} 回合" else "✖ 止步于此（${done.turns} 回合）",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (done.victory) AppTheme.Gold else AppTheme.Text2,
                        )
                        if (done.victory) {
                            Text(text = "星尘 +${done.rewardSoft}", fontSize = 13.sp, color = AppTheme.Text1)
                            if (done.bestFloorAfter >= nextFloor) {
                                Text(text = "纪录推进至第 ${done.bestFloorAfter} 层", fontSize = 12.sp, color = AppTheme.Text2)
                            }
                        }
                        Text(
                            text = "提示：敌方元素随层数轮转，用克制元素编队能显著降低损血。",
                            fontSize = 11.sp,
                            color = AppTheme.Text3,
                        )
                    }
                }
                when (result) {
                    TowerOutcome.Rejected -> Text(
                        text = "挑战被拒绝：请检查编队配置。",
                        fontSize = 12.sp,
                        color = AppTheme.Text3,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TowerOutcome.SaveFailed -> Text(
                        text = "存档失败，本次结果已回滚，请重试。",
                        fontSize = 12.sp,
                        color = AppTheme.Text3,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    else -> Unit
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
