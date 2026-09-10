package com.milan.game.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme

/**
 * 活动界面（2026-09 死功能激活；2026-09-10 补全签到/任务领取/商店兑换）。
 *
 * 服务层 [com.milan.game.services.EventRhythmService] 已实现：
 * - 活动生命周期管理（ensureActiveEvents）
 * - 活动代币系统（addEventCurrency / balanceOf / spendCurrency）
 * - 任务进度追踪、签到、商店
 *
 * 本页：活跃活动列表 + 任务领取 + 签到 + 商店兑换 + 代币余额。
 * 状态与写动作收敛在 [EventViewModel]，Composable 只订阅与回调。
 */
@Composable
fun EventScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedback = LocalFeedback.current
    val vm: EventViewModel = viewModel(factory = com.milan.game.di.AppGraph.factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.toasts.collect { feedback.show(it) } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.BgDeepest),
    ) {
        PageBackground {
            Column(modifier = Modifier.fillMaxSize()) {
                AppTopBar(title = "活动", onBack = onBack)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    if (state.eventBalances.isNotEmpty()) {
                        item { EventCurrencyBar(balances = state.eventBalances) }
                    }
                    if (state.cards.isEmpty()) {
                        item { EmptyEventCard() }
                    } else {
                        items(state.cards, key = { it.event.eventId }) { card ->
                            EventCard(
                                card = card,
                                busy = busy,
                                onSignIn = { vm.signIn(card.event.eventId) },
                                onClaimTask = { taskId -> vm.claimTask(card.event.eventId, taskId) },
                                onRedeem = { itemId -> vm.redeem(card.event.eventId, itemId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 活动代币余额条。 */
@Composable
private fun EventCurrencyBar(balances: Map<String, Int>) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            balances.forEach { (type, amount) ->
                Text(
                    text = "${currencyLabel(type)} $amount",
                    color = AppTheme.Gold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun currencyLabel(type: String): String = when (type) {
    "EVENT_CURRENCY" -> "活动代币"
    "ACTIVITY_POINTS" -> "活跃点"
    "COLLABORATION_TOKENS" -> "联动徽章"
    else -> type
}

/** 活动卡片。 */
@Composable
private fun EventCard(
    card: EventCardUi,
    busy: Boolean,
    onSignIn: () -> Unit,
    onClaimTask: (String) -> Unit,
    onRedeem: (String) -> Unit,
) {
    val event = card.event
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.name.ifEmpty { event.eventId },
                        color = AppTheme.Text1,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = event.description.ifEmpty { "限时活动" },
                        color = AppTheme.Text2,
                        fontSize = 11.sp,
                    )
                }
                EventCountdown(endTime = event.endTime)
            }

            // 签到按钮
            if (card.isSignInType) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "已签 ${card.signInProgress} / ${event.signInDays.coerceAtLeast(1)} 天",
                        color = AppTheme.Text2,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    ActionChip(
                        label = if (card.canSignIn) "签到" else "已签到",
                        enabled = card.canSignIn && !busy,
                        onClick = onSignIn,
                    )
                }
            }

            // 活动任务
            if (card.tasks.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "活动任务",
                    color = AppTheme.Gold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                card.tasks.forEach { taskUi ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = taskUi.task.name.ifEmpty { taskUi.task.taskId },
                            color = AppTheme.Text2,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = taskUi.progressLabel,
                            color = when {
                                taskUi.claimed -> AppTheme.Text3
                                taskUi.canClaim -> AppTheme.Gold
                                taskUi.reachedTarget -> AppTheme.Success
                                else -> AppTheme.Text3
                            },
                            fontSize = 10.sp,
                        )
                        if (taskUi.canClaim) {
                            Spacer(Modifier.width(8.dp))
                            ActionChip(
                                label = "领取",
                                enabled = !busy,
                                onClick = { onClaimTask(taskUi.task.taskId) },
                            )
                        }
                    }
                }
            }

            // 活动商店
            if (card.shop.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "活动商店",
                    color = AppTheme.Gold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                card.shop.forEach { shopUi ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = shopUi.item.name.ifEmpty { shopUi.item.itemId },
                                color = AppTheme.Text2,
                                fontSize = 11.sp,
                            )
                            Text(
                                text = "价格 ${shopUi.item.price} · 已兑 ${shopUi.redeemed}/${shopUi.item.maxRedemptions}",
                                color = AppTheme.Text3,
                                fontSize = 10.sp,
                            )
                        }
                        val canRedeem = shopUi.redeemed < shopUi.item.maxRedemptions
                        ActionChip(
                            label = if (canRedeem) "兑换" else "售罄",
                            enabled = canRedeem && !busy,
                            onClick = { onRedeem(shopUi.item.itemId) },
                        )
                    }
                }
            }
        }
    }
}

/** 小型操作按钮。 */
@Composable
private fun ActionChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppTheme.Roundness.sm))
            .background(
                if (enabled) AppTheme.Gold.copy(alpha = 0.2f) else AppTheme.Surface
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = if (enabled) AppTheme.Gold else AppTheme.Text3,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 活动倒计时。 */
@Composable
private fun EventCountdown(endTime: Long) {
    val remaining = (endTime - System.currentTimeMillis()).coerceAtLeast(0)
    val hours = remaining / 3_600_000
    val days = hours / 24
    val text = if (days > 0) "剩余 ${days}天" else if (hours > 0) "剩余 ${hours}小时" else "即将结束"

    Text(
        text = text,
        color = if (days <= 1) AppTheme.Danger else AppTheme.Text3,
        fontSize = 10.sp,
    )
}

/** 无活动占位卡片。 */
@Composable
private fun EmptyEventCard() {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "暂无活动",
                color = AppTheme.Text1,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "精彩活动即将开启，敬请期待！",
                color = AppTheme.Text3,
                fontSize = 12.sp,
            )
        }
    }
}
