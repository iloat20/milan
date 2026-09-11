package com.milan.game.ui.affinity

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milan.game.services.AffinityFormulas
import com.milan.game.ui.components.ArtifactPanel
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme

/**
 * 角色好感度界面。
 *
 * 对标原神/崩铁的好感度系统：
 * - 角色列表 + 好感度等级
 * - 好感度等级奖励预览
 * - 累计好感度经验
 *
 * 2026-09-02 完整闭环 + 2026-09-08 P1-6 VM 化：状态/赠送动作在 [AffinityViewModel]，
 * 好感数据随 snapshot revision 重算下沉 VM；本组合层只订阅 + 转发反馈。
 */
@Composable
fun AffinityScreen(
    onBack: () -> Unit,
    onOpenCharacter: (String) -> Unit,
) {
    val feedback = LocalFeedback.current
    val vm: AffinityViewModel = viewModel(factory = com.milan.game.di.AppGraph.factory)
    val ui by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.toasts.collect { feedback.show(it) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BgDeepest)
    ) {
        PageBackground {
            Column(modifier = Modifier.fillMaxSize()) {
                AppTopBar(title = "好感度", onBack = onBack)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    // 说明
                    item {
                        AffinityInfoCard()
                    }

                    // 角色好感度列表（owned 过滤已在 VM 完成）
                    items(ui.rows) { row ->
                        AffinityCard(
                            characterId = row.characterId,
                            name = row.displayName,
                            rarity = row.rarity,
                            affinity = row.affinity,
                            claimedLevels = row.claimedLevels,
                            giftEnabled = row.affinity < AffinityFormulas.MAX_AFFINITY &&
                                ui.softCurrency >= AffinityFormulas.GIFT_COST_SOFT,
                            onGift = { vm.gift(row.characterId) },
                            onClaimReward = { level -> vm.claimReward(row.characterId, level) },
                            onClick = { onOpenCharacter(row.characterId) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 好感度说明卡片。
 *
 * 2026-09-10：等级奖励从「规划中」落地——档位奖励见 [AffinityFormulas.LEVEL_REWARDS]，
 * 角色卡片内可领取；语音/剧情/皮肤等视觉奖励仍待后续内容接入。
 */
@Composable
private fun AffinityInfoCard() {
    ArtifactPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "好感度系统",
                color = AppTheme.Text1,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "通过赠送礼物、出战战斗提升角色好感度。达到档位后可在角色行领取等级奖励。",
                color = AppTheme.Text2,
            )
            Spacer(Modifier.height(8.dp))
            // 等级奖励预览
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AffinityFormulas.LEVEL_REWARDS.forEach { reward ->
                    AffinityLevelReward(level = reward.level, reward = reward.label)
                }
            }
        }
    }
}

/**
 * 好感度等级奖励预览。
 */
@Composable
private fun AffinityLevelReward(level: Int, reward: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(AppTheme.Roundness.sm))
                .background(AppTheme.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$level",
                color = AppTheme.Gold,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = reward,
            color = AppTheme.Text3,
        )
    }
}

/**
 * 单个角色好感度卡片。
 *
 * 右侧操作区：已满级 → 徽章；未满级 → 「赠送」按钮。
 * 下方奖励行：已达档位且未领取时显示可点「领取」；已领取显示 ✓。
 */
@Composable
private fun AffinityCard(
    characterId: String,
    name: String,
    rarity: Int,
    affinity: Int,
    claimedLevels: Set<Int>,
    giftEnabled: Boolean,
    onGift: () -> Unit,
    onClaimReward: (Int) -> Unit,
    onClick: () -> Unit,
) {
    val level = AffinityFormulas.levelOf(affinity)
    val expInLevel = AffinityFormulas.expInLevel(affinity)
    val claimable = AffinityFormulas.LEVEL_REWARDS.filter {
        it.level <= level && it.level !in claimedLevels
    }

    ArtifactPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 角色立绘
            PortraitImage(
                characterId = characterId,
                rarity = rarity,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(AppTheme.Roundness.sm)),
                name = name,
            )

            Spacer(Modifier.width(12.dp))

            // 角色信息
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        color = AppTheme.Text1,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "好感 Lv.$level",
                        color = AppTheme.Frost,
                    )
                }
                Spacer(Modifier.height(6.dp))
                // 好感度进度条
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(
                        progress = { expInLevel / AffinityFormulas.EXP_PER_LEVEL.toFloat() },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(AppTheme.Roundness.xxs)),
                        color = AppTheme.Frost,
                        trackColor = AppTheme.Surface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$expInLevel / ${AffinityFormulas.EXP_PER_LEVEL}",
                        color = AppTheme.Text3,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // 右侧操作区：满级徽章 or 赠送按钮
            if (level >= AffinityFormulas.MAX_LEVEL) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(text = "❤️", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "已满级",
                        color = AppTheme.Text3,
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppTheme.Roundness.md))
                            .background(
                                if (giftEnabled) AppTheme.Gold.copy(alpha = 0.18f) else AppTheme.Surface
                            )
                            .clickable(
                                enabled = true, // 禁用态仍可点，走 Rejected 提示（余额/满级原因）
                                onClick = onGift,
                            )
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "🎁 赠送",
                            color = if (giftEnabled) AppTheme.Gold else AppTheme.Text3,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = "${AffinityFormulas.GIFT_COST_SOFT} 星尘",
                        color = AppTheme.Text3,
                    )
                }
            }
        }

        // 可领取的等级奖励行（2026-09-10：从「规划中」落地为可点领取）
        if (claimable.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                claimable.forEach { reward ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppTheme.Roundness.sm))
                            .background(AppTheme.Gold.copy(alpha = 0.22f))
                            .clickable { onClaimReward(reward.level) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "领取 Lv.${reward.level}",
                            color = AppTheme.Gold,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        }
    }
}
