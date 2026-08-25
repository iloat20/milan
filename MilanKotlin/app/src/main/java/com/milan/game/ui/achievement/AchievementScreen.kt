package com.milan.game.ui.achievement

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.milan.game.services.AchievementDef
import com.milan.game.services.AchievementStatus
import com.milan.game.services.WriteOutcome
import com.milan.game.ui.GameState
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * 成就页（2026-08 二期）：
 * - 全部成就定义/阈值/奖励出自 services/Achievements.kt（单一事实来源），本页只做展示与领取；
 * - 解锁态由存档实时推导（不落盘），领取态以存档为准；
 * - 领取走 GameService.claimAchievement 事务（重复领取/未解锁在服务层拒绝）。
 */
@Composable
fun AchievementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    val snapshot by GameState.service.snapshot.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }

    // 快照 revision 触发重算：任何页面的经济/养成写操作都会推进进度显示
    val statuses = remember(snapshot.revision) { GameState.service.achievementStatuses() }
    val unlockedCount = statuses.count { it.unlocked }
    val claimedCount = statuses.count { it.claimed }

    PageBackground(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(title = "成 就", onBack = onBack)
            Column(Modifier.padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "已解锁 $unlockedCount / ${statuses.size} · 已领取 $claimedCount",
                    fontSize = 13.sp,
                    color = AppTheme.Text2,
                )
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 18.dp, top = 10.dp, end = 18.dp, bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(statuses.size, key = { statuses[it].def.id }) { i ->
                    AchievementCard(
                        status = statuses[i],
                        enabled = !busy,
                        onClaim = {
                            val id = statuses[i].def.id
                            scope.launch {
                                if (busy) return@launch
                                busy = true
                                try {
                                    val msg = when (val outcome = GameState.service.claimAchievement(id)) {
                                        WriteOutcome.Success -> "奖励已发放"
                                        WriteOutcome.Rejected -> "尚未解锁或已领取"
                                        WriteOutcome.SaveFailed -> "保存失败，请重试"
                                    }
                                    feedback.show(msg)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 单条成就卡：已领取金边、可领金按钮高亮、未解锁灰态。 */
@Composable
private fun AchievementCard(
    status: AchievementStatus,
    enabled: Boolean,
    onClaim: () -> Unit,
) {
    val def = status.def
    val highlight = status.claimed || (status.unlocked && !status.claimed)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.Surface.copy(alpha = if (status.unlocked) 0.75f else 0.45f))
            .border(
                1.dp,
                when {
                    status.claimed -> AppTheme.Gold.copy(alpha = 0.55f)
                    status.unlocked -> AppTheme.Gold.copy(alpha = 0.85f)
                    else -> AppTheme.Stroke
                },
                RoundedCornerShape(14.dp),
            )
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(def.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppTheme.Text1)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            status.claimed -> "已领取"
                            status.unlocked -> "可领取"
                            else -> "未解锁"
                        },
                        fontSize = 10.sp,
                        color = if (status.unlocked && !status.claimed) AppTheme.Gold else AppTheme.Text3,
                        modifier = Modifier
                            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(def.desc, fontSize = 12.sp, color = AppTheme.Text2, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(rewardText(def), fontSize = 11.sp, color = AppTheme.GoldHi)
            }
            if (status.unlocked && !status.claimed) {
                GoldButton(text = "领 取", onClick = onClaim, enabled = enabled, textSize = 13.sp)
            }
        }
    }
}

/** 奖励文案（数值取自定义，禁止就地写）。 */
private fun rewardText(def: AchievementDef): String {
    val parts = mutableListOf<String>()
    if (def.rewardSoft > 0) parts += "星尘 +${def.rewardSoft}"
    if (def.rewardHard > 0) parts += "钻石 ×${def.rewardHard}"
    if (def.rewardTickets > 0) parts += "战票 ×${def.rewardTickets}"
    return "奖励：" + parts.joinToString(" ＋ ").ifEmpty { "无" }
}
