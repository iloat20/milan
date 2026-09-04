package com.milan.game.ui.battlepass

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.data.BattlePassReward
import com.milan.game.ui.GameState
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val service = GameState.service
    val feedback = com.milan.game.ui.feedback.LocalFeedback.current
    var busy by remember { mutableStateOf(false) }
    // B2 修复：此前无任何 snapshot 订阅 + remember 无 key → 页面永不重组。
    // 「购买豪华版 680💎」会真实扣钻落盘，界面却零变化（按钮不消失、豪华轨仍锁），
    // 用户会重复点击，而重复点击返回 Rejected 且无反馈 → 完全静默的扣费黑洞。
    // 现按 ShopScreen 范式：订阅 snapshot + revision 作 remember key + 三态反馈 + busy 防重入。
    val snap by service.snapshot.collectAsStateWithLifecycle()
    val data = remember(snap.revision) { service.getMonetizationData() }
    val rewards = remember(snap.revision) { service.getBattlePassRewards() }

    /** 领取纪行奖励通用流程：三态反馈 + busy 防重入（同 ShopScreen.buyDaily 范式）。 */
    fun claimReward(level: Int) {
        scope.launch {
            if (busy) return@launch
            busy = true
            try {
                val msg = when (service.claimBattlePassReward(level)) {
                    com.milan.game.services.WriteOutcome.Success -> "已领取 Lv.$level 奖励"
                    com.milan.game.services.WriteOutcome.Rejected -> "等级不足或已领取"
                    com.milan.game.services.WriteOutcome.SaveFailed -> "保存失败，请重试"
                }
                feedback.show(msg)
            } finally {
                busy = false
            }
        }
    }

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
                            level = data.battlePassLevel,
                            exp = data.battlePassExp,
                            isPremium = data.battlePassPremium,
                            enabled = !busy,
                            onPurchasePremium = {
                                scope.launch {
                                    if (busy) return@launch
                                    busy = true
                                    try {
                                        val msg = when (service.purchaseBattlePass(680)) {
                                            com.milan.game.services.WriteOutcome.Success -> "豪华版已激活"
                                            com.milan.game.services.WriteOutcome.Rejected -> "钻石不足或已购买"
                                            com.milan.game.services.WriteOutcome.SaveFailed -> "保存失败，请重试"
                                        }
                                        feedback.show(msg)
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                        )
                    }

                    // 奖励列表
                    items(rewards) { reward ->
                        RewardRow(
                            reward = reward,
                            currentLevel = data.battlePassLevel,
                            claimedLevels = data.claimedBPRewards,
                            isPremium = data.battlePassPremium,
                            enabled = !busy,
                            onClaimFree = { claimReward(reward.level) },
                            onClaimPremium = { claimReward(reward.level) },
                        )
                    }
                }
            }
        }
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
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
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
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "Lv.$level",
                        color = AppTheme.Gold,
                        fontSize = 28.sp,
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
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = enabled, onClick = onPurchasePremium)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "购买豪华版 680💎",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(AppTheme.Gold.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "豪华版 ✅",
                            color = AppTheme.Gold,
                            fontSize = 12.sp,
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
                        .clip(RoundedCornerShape(3.dp)),
                    color = AppTheme.Gold,
                    trackColor = AppTheme.Surface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$exp / 1000",
                    color = AppTheme.Text3,
                    fontSize = 10.sp,
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

    GlassPanel(
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
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isUnlocked) AppTheme.Gold.copy(alpha = 0.2f) else AppTheme.Surface
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${reward.level}",
                    color = if (isUnlocked) AppTheme.Gold else AppTheme.Text3,
                    fontSize = 12.sp,
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
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center,
        ) {
            if (reward != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = rewardItemIdToEmoji(reward.itemId),
                        fontSize = 18.sp,
                    )
                    Text(
                        text = formatRewardAmount(reward.amount),
                        color = if (isUnlocked) AppTheme.Text1 else AppTheme.Text3,
                        fontSize = 10.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isPremium) AppTheme.Frost else AppTheme.Text3,
            fontSize = 9.sp,
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
