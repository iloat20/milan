package com.milan.game.ui.missions

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.GameState
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme
import com.milan.game.services.ChestStatus
import com.milan.game.services.DailyMissionStatus
import kotlinx.coroutines.launch

/**
 * 每日任务界面。
 *
 * 对标原神/崩铁的每日委托：
 * - 6个每日任务列表（进度+完成状态）
 * - 活跃度进度条（0-100）
 * - 5个活跃度宝箱（20/40/60/80/100里程碑）
 */
@Composable
fun DailyMissionScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val service = GameState.service
    val feedback = com.milan.game.ui.feedback.LocalFeedback.current
    var busy by remember { mutableStateOf(false) }
    // I1 修复：与 BattlePassScreen(B2) 同模式——三处 remember 无 key + 无 snapshot 订阅，
    // 页面永不重组。宝箱真实发放星尘/钻石却仍显示"可领取"，重复点击静默 Rejected。
    val snap by service.snapshot.collectAsStateWithLifecycle()
    // R5-I4：进入页面即触发跨日重置（事务 + 落盘），避免只读 API 锁外写。重置成功后
    // revision 递增，下方 remember(snap.revision) 自动重组刷新今日任务/宝箱。
    LaunchedEffect(Unit) { service.ensureDailyMissionReset() }
    val missions = remember(snap.revision) { service.getTodayMissions() }
    val chestStatuses = remember(snap.revision) { service.getChestStatuses() }
    val data = remember(snap.revision) { service.getDailyMissionData() }

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
                            points = data.activityPoints,
                            claimedChests = data.claimedChests,
                        )
                    }

                    // 活跃度宝箱
                    item {
                        ActivityChests(
                            statuses = chestStatuses,
                            onClaim = { milestone ->
                                // 状态判断 + 反馈下沉到这里：未解锁/已领取也给出提示，
                                // 避免"点了没反应"（此前由 ChestItem 外层 if 静默拦截）。
                                val st = chestStatuses.firstOrNull { it.milestone == milestone }
                                scope.launch {
                                    if (busy) return@launch
                                    when {
                                        st == null -> Unit
                                        st.claimed -> feedback.show("该宝箱已领取")
                                        !st.unlocked -> feedback.show("活跃度达到 $milestone 点可领取")
                                        else -> {
                                            busy = true
                                            try {
                                                val msg = when (service.claimActivityChest(milestone)) {
                                                    com.milan.game.services.WriteOutcome.Success -> "已领取 ${milestone} 点活跃度宝箱"
                                                    com.milan.game.services.WriteOutcome.Rejected -> "活跃度不足或已领取"
                                                    com.milan.game.services.WriteOutcome.SaveFailed -> "保存失败，请重试"
                                                }
                                                feedback.show(msg)
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    }
                                }
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
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "活跃度",
                    color = AppTheme.Text1,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "$points / 100",
                    color = AppTheme.Gold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { points / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
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
                        fontSize = 10.sp,
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
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "活跃度奖励",
                color = AppTheme.Text1,
                fontSize = 14.sp,
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
                .clip(RoundedCornerShape(8.dp))
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
                fontSize = 24.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${status.milestone}",
            color = if (status.unlocked) AppTheme.Gold else AppTheme.Text3,
            fontSize = 10.sp,
        )
        if (status.hardReward > 0) {
            Text(
                text = "+${status.hardReward}💎",
                color = AppTheme.Frost,
                fontSize = 8.sp,
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

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (mission.completed) AppTheme.Gold.copy(alpha = 0.2f) else AppTheme.Surface
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mission.def.icon,
                    fontSize = 20.sp,
                )
            }

            Spacer(Modifier.width(12.dp))

            // 任务信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mission.def.title,
                    color = if (mission.completed) AppTheme.Gold else AppTheme.Text1,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = mission.def.description,
                    color = AppTheme.Text3,
                    fontSize = 11.sp,
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
                            .clip(RoundedCornerShape(2.dp)),
                        color = if (mission.completed) AppTheme.Gold else AppTheme.Frost,
                        trackColor = AppTheme.Surface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${mission.progress}/${mission.def.targetCount}",
                        color = AppTheme.Text3,
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // 奖励
            Box(
                modifier = Modifier
                    .background(
                        if (mission.completed) AppTheme.Gold.copy(alpha = 0.2f) else AppTheme.Surface,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "+${mission.def.activityReward}",
                    color = if (mission.completed) AppTheme.Gold else AppTheme.Text3,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
