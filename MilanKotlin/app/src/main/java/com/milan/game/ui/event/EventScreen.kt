package com.milan.game.ui.event

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme

/**
 * 活动界面（2026-09 死功能激活）。
 *
 * 服务层 [com.milan.game.services.EventRhythmService] 已实现：
 * - 活动生命周期管理（ensureActiveEvents）
 * - 活动代币系统（addEventCurrency / balanceOf / spendCurrency）
 * - 任务进度追踪
 *
 * 本页展示当前活跃活动列表。2026-09-08 P1-6 C 批：懒激活与派生读取收敛进
 * [EventViewModel]，Composable 只订阅。
 */
@Composable
fun EventScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: EventViewModel = viewModel(factory = com.milan.game.di.AppGraph.factory)
    val activeEvents by vm.uiState.collectAsStateWithLifecycle()

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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                ) {
                    if (activeEvents.isEmpty()) {
                        // 无活跃活动
                        item {
                            EmptyEventCard()
                        }
                    } else {
                        items(activeEvents, key = { it.event.eventId }) { card ->
                            EventCard(card = card)
                        }
                    }
                }
            }
        }
    }
}

/** 活动卡片。 */
@Composable
private fun EventCard(card: EventCardUi) {
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

            // 活动进度（来自存档 eventTaskProgress，不再硬编码 0）
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
                            .padding(vertical = 3.dp),
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
                            color = if (taskUi.task.isCompleted) AppTheme.Success else AppTheme.Text3,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
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
