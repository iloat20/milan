package com.milan.game.ui.battlepass

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.data.BattlePassReward
import com.milan.game.ui.components.GlassDialog
import com.milan.game.ui.components.ArtifactPanel
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.components.InkButton
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme

/**
 * Battle Pass 界面。
 *
 * 对标原神/崩铁的纪行系统：
 * - 免费轨 + 付费轨双列奖励
 * - 等级进度条
 * - 购买豪华版入口
 */
@Composable
fun BattlePassScreen(
    onBack: () -> Unit,
) {
    val feedback = com.milan.game.ui.feedback.LocalFeedback.current
    val vm: BattlePassViewModel = viewModel(factory = com.milan.game.di.AppGraph.factory)
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var showPremiumConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.toasts.collect { feedback.show(it) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BgDeepest)
    ) {
        PageBackground {
            Column(modifier = Modifier.fillMaxSize()) {
                AppTopBar(title = "纪行", onBack = onBack)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    // 当前等级信息
                    item {
                        BattlePassHeader(
                            level = ui.level,
                            exp = ui.exp,
                            isPremium = ui.isPremium,
                            enabled = !busy,
                            onPurchasePremium = { showPremiumConfirm = true },
                        )
                    }

                    // 奖励列表
                    items(ui.rewards) { reward ->
                        RewardRow(
                            reward = reward,
                            currentLevel = ui.level,
                            claimedLevels = ui.claimedLevels,
                            isPremium = ui.isPremium,
                            enabled = !busy,
                            onClaimFree = { vm.claimReward(reward.level) },
                            onClaimPremium = { vm.claimReward(reward.level) },
                        )
                    }
                }
            }
        }

        GlassDialog(
            show = showPremiumConfirm,
            onDismiss = { showPremiumConfirm = false },
            title = "购买豪华版纪行",
            body = "将消耗 ${BattlePassViewModel.PREMIUM_COST_HARD} 钻石解锁豪华奖励轨。是否继续？",
            buttons = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InkButton(
                        text = "取 消",
                        onClick = { showPremiumConfirm = false },
                        modifier = Modifier.weight(1f),
                    )
                    GoldButton(
                        text = "确认购买",
                        onClick = {
                            showPremiumConfirm = false
                            vm.purchasePremium()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        )
    }
}

/**
 * Battle Pass 头部信息。
 */
@Composable
private fun BattlePassHeader(
    level: Int,
    exp: Int,
    isPremium: Boolean,
    enabled: Boolean = true,
    onPurchasePremium: () -> Unit,
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
                        text = "纪行等级",
                        color = AppTheme.Text3,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = "Lv.$level",
                        color = AppTheme.Gold,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (!isPremium) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(AppTheme.Gold, AppTheme.Frost)
                                ),
                                RoundedCornerShape(AppTheme.Roundness.sm)
                            )
                            .clickable(enabled = enabled, onClick = onPurchasePremium)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "购买豪华版 680💎",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(AppTheme.Gold.copy(alpha = 0.2f), RoundedCornerShape(AppTheme.Roundness.sm))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "豪华版 ✅",
                            color = AppTheme.Gold,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 经验进度
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(
                    progress = { exp / 1000f },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(AppTheme.Roundness.xxs)),
                    color = AppTheme.Gold,
                    trackColor = AppTheme.Surface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$exp / 1000",
                    color = AppTheme.Text3,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * 单行奖励（免费轨 + 付费轨）。
 */
@Composable
private fun RewardRow(
    reward: BattlePassReward,
    currentLevel: Int,
    claimedLevels: List<Int?>,
    isPremium: Boolean,
    enabled: Boolean = true,
    onClaimFree: () -> Unit,
    onClaimPremium: () -> Unit,
) {
    val isUnlocked = currentLevel >= reward.level
    val isClaimed = claimedLevels.contains(reward.level)

    ArtifactPanel(
        modifier = Modifier.fillMaxWidth(),
        highlighted = isUnlocked && !isClaimed,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 等级
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(AppTheme.Roundness.sm))
                    .background(
                        if (isUnlocked) AppTheme.Gold.copy(alpha = 0.2f) else AppTheme.Surface
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${reward.level}",
                    color = if (isUnlocked) AppTheme.Gold else AppTheme.Text3,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.width(8.dp))

            // 免费轨奖励
            Box(modifier = Modifier.weight(1f)) {
                RewardItem(
                    reward = reward.freeReward,
                    isUnlocked = isUnlocked,
                    isClaimed = isClaimed,
                    label = "免费",
                    enabled = enabled,
                    onClick = onClaimFree,
                )
            }

            Spacer(Modifier.width(8.dp))

            // 付费轨奖励
            Box(modifier = Modifier.weight(1f)) {
                RewardItem(
                    reward = reward.premiumReward,
                    isUnlocked = isUnlocked && isPremium,
                    isClaimed = isClaimed,
                    label = "豪华",
                    isPremium = true,
                    enabled = enabled,
                    onClick = onClaimPremium,
                )
            }
        }
    }
}

/**
 * 单个奖励物品。
 */
@Composable
private fun RewardItem(
    reward: com.milan.game.data.BPReward?,
    isUnlocked: Boolean,
    isClaimed: Boolean,
    label: String,
    isPremium: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bgColor = when {
        isClaimed -> AppTheme.Gold.copy(alpha = 0.1f)
        isUnlocked -> if (isPremium) AppTheme.Frost.copy(alpha = 0.15f) else AppTheme.Gold.copy(alpha = 0.1f)
        else -> AppTheme.Surface
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && isUnlocked && !isClaimed, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(AppTheme.Roundness.sm))
                .background(bgColor),
            contentAlignment = Alignment.Center,
        ) {
            if (reward != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = rewardItemIdToEmoji(reward.itemId),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = formatRewardAmount(reward.amount),
                        color = if (isUnlocked) AppTheme.Text1 else AppTheme.Text3,
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isPremium) AppTheme.Frost else AppTheme.Text3,
        )
    }
}

/** 奖励物品ID转emoji。 */
private fun rewardItemIdToEmoji(itemId: String): String = when {
    itemId.startsWith("soft") -> "💰"
    itemId.startsWith("hard") -> "💎"
    itemId.startsWith("exp") -> "📖"
    itemId.startsWith("eq") -> "⚔️"
    itemId.startsWith("skin") -> "🎨"
    itemId.startsWith("char") -> "👤"
    itemId.startsWith("mat") -> "📦"
    else -> "🎁"
}

/** 格式化奖励数量。 */
private fun formatRewardAmount(amount: Int): String = when {
    amount >= 10000 -> "${amount / 10000}万"
    amount >= 1000 -> "${amount / 1000}k"
    else -> "$amount"
}
