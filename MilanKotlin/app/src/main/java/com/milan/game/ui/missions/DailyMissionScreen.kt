package com.milan.game.ui.missions

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.milan.game.ui.components.ArtifactPanel
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme
import com.milan.game.services.ChestStatus
import com.milan.game.services.DailyMissionStatus

/**
 * 每日任务界面。
 *
 * 对标原神/崩铁的每日委托：
 * - 6个每日任务列表（进度+完成状态）
 * - 活跃度进度条（0-100）
 * - 5个活跃度宝箱（20/40/60/80/100里程碑）
 *
 * 2026-09-08 P1-6 C 批：跨日重置、派生状态与宝箱领取全部收敛进 [DailyMissionViewModel]，
 * Composable 只订阅与回调。
 */
@Composable
fun DailyMissionScreen(
    onBack: () -> Unit,
) {
    val feedback = com.milan.game.ui.feedback.LocalFeedback.current
    val vm: DailyMissionViewModel = viewModel(factory = com.milan.game.di.AppGraph.factory)
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.toasts.collect { feedback.show(it) } }
    val missions = ui.missions
    val chestStatuses = ui.chestStatuses

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BgDeepest)
    ) {
        PageBackground {
            Column(modifier = Modifier.fillMaxSize()) {
                AppTopBar(title = "每日任务", onBack = onBack)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    // 活跃度进度条
                    item {
                        ActivityProgressBar(
                            points = ui.activityPoints,
                            claimedChests = ui.claimedChests,
                        )
                    }

                    // 活跃度宝箱
                    item {
                        ActivityChests(
                            statuses = chestStatuses,
                            onClaim = { milestone ->
                                // 状态判断 + 反馈已收敛进 VM（未解锁/已领取也给出提示，
                                // 避免"点了没反应"）。
                                vm.claimChest(milestone)
                            },
                        )
                    }

                    // 每日任务列表
                    items(missions) { mission ->
                        MissionCard(mission = mission)
                    }
                }
            }
        }
    }
}

/**
 * 活跃度进度条。
 */
@Composable
private fun ActivityProgressBar(
    points: Int,
    claimedChests: List<Int?>,
) {
    ArtifactPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "活跃度",
                    color = AppTheme.Text1,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "$points / 100",
                    color = AppTheme.Gold,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { points / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(AppTheme.Roundness.xs)),
                color = AppTheme.Gold,
                trackColor = AppTheme.Surface,
            )
            Spacer(Modifier.height(8.dp))
            // 里程碑标记
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(20, 40, 60, 80, 100).forEach { milestone ->
                    val claimed = claimedChests.contains(milestone)
                    Text(
                        text = "$milestone",
                        color = if (claimed) AppTheme.Gold else AppTheme.Text3,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/**
 * 活跃度宝箱列表。
 */
@Composable
private fun ActivityChests(
    statuses: List<ChestStatus>,
    onClaim: (Int) -> Unit,
) {
    ArtifactPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "活跃度奖励",
                color = AppTheme.Text1,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                statuses.forEach { status ->
                    ChestItem(
                        status = status,
                        // 状态门控已下沉到 onClaim（需给出提示），此处不做静默拦截。
                        onClick = { onClaim(status.milestone) },
                    )
                }
            }
        }
    }
}

/**
 * 单个宝箱。
 */
@Composable
private fun ChestItem(
    status: ChestStatus,
    onClick: () -> Unit,
) {
    val bgColor = when {
        status.claimed -> AppTheme.Gold.copy(alpha = 0.3f)
        status.unlocked -> AppTheme.Gold.copy(alpha = 0.15f)
        else -> AppTheme.Surface
    }
    val borderColor = when {
        status.claimed -> AppTheme.Gold
        status.unlocked -> AppTheme.Gold.copy(alpha = 0.5f)
        else -> AppTheme.Text3.copy(alpha = 0.3f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(AppTheme.Roundness.sm))
                .background(bgColor)
                .then(
                    if (status.unlocked && !status.claimed) {
                        Modifier.background(
                            Brush.radialGradient(
                                colors = listOf(
                                    AppTheme.Gold.copy(alpha = 0.2f),
                                    Color.Transparent,
                                )
                            )
                        )
                    } else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (status.claimed) "✅" else "🎁",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${status.milestone}",
            color = if (status.unlocked) AppTheme.Gold else AppTheme.Text3,
        )
        if (status.hardReward > 0) {
            Text(
                text = "+${status.hardReward}💎",
                color = AppTheme.Frost,
            )
        }
    }
}

/**
 * 单个每日任务卡片。
 */
@Composable
private fun MissionCard(mission: DailyMissionStatus) {
    val progress = if (mission.def.targetCount > 0) {
        mission.progress.toFloat() / mission.def.targetCount
    } else 0f

    ArtifactPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 任务图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(AppTheme.Roundness.sm))
                    .background(
                        if (mission.completed) AppTheme.Gold.copy(alpha = 0.2f) else AppTheme.Surface
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mission.def.icon,
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            Spacer(Modifier.width(12.dp))

            // 任务信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mission.def.title,
                    color = if (mission.completed) AppTheme.Gold else AppTheme.Text1,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = mission.def.description,
                    color = AppTheme.Text3,
                )
                Spacer(Modifier.height(6.dp))
                // 进度条
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(AppTheme.Roundness.xxs)),
                        color = if (mission.completed) AppTheme.Gold else AppTheme.Frost,
                        trackColor = AppTheme.Surface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${mission.progress}/${mission.def.targetCount}",
                        color = AppTheme.Text3,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // 奖励
            Box(
                modifier = Modifier
                    .background(
                        if (mission.completed) AppTheme.Gold.copy(alpha = 0.2f) else AppTheme.Surface,
                        RoundedCornerShape(AppTheme.Roundness.sm)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "+${mission.def.activityReward}",
                    color = if (mission.completed) AppTheme.Gold else AppTheme.Text3,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
