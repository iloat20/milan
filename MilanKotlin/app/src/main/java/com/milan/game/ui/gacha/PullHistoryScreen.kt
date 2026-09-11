package com.milan.game.ui.gacha

import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milan.game.data.PullLogEntry
import com.milan.game.ui.components.EntranceItem
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.GlyphBadge
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 抽卡历史屏（2026-08 三期新增；对标主流 gacha 的抽卡记录页 / wish-simulator 的 Gacha History）。
 *
 * 数据：GameService.pullHistory()（最近 100 条，时间正序存储）→ 本页倒序展示（最新在最上）。
 * 刷新：订阅 snapshot.revision（写操作后自动重组重读，与全 App 同一范式）。
 * 历史仅作回顾展示，不影响任何玩法判定。
 */
@Composable
fun PullHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: PullHistoryViewModel = viewModel(factory = com.milan.game.di.AppGraph.factory)
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val timeFormat = vm.timeFormatter

    PageBackground(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(title = "抽 卡 历 史", onBack = onBack)
            Spacer(Modifier.height(10.dp))

            val entries = ui.entries
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Text(
                        text = "还没有召唤记录\n去「次元裂缝」试试手气吧",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.Text3,
                        lineHeight = 24.sp,
                        modifier = Modifier.padding(top = 100.dp),
                    )
                }
                return@Column
            }

            // 统计头：窗口内总抽数 / SSR+ 次数（对标 pity 追踪类工具的核心指标）
            val ssrPlus = entries.count { it.rarity >= 3 }
            GlassPanel(modifier = Modifier.padding(horizontal = 18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatCell("累计召唤", "${entries.size} 抽")
                    StatCell("SSR+ 出货", "$ssrPlus 次")
                    StatCell("综合占比", if (entries.isEmpty()) "—" else "${ssrPlus * 100 / entries.size}%")
                }
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 18.dp, vertical = 6.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(entries, key = { _, e -> e.timestamp }) { i, entry ->
                    EntranceItem(index = i) {
                        HistoryRow(entry = entry, timeText = timeFormat.format(Date(entry.timestamp)))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, color = AppTheme.Gold)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = AppTheme.Text2)
    }
}

/** 单条记录：稀有度渐变徽章（SSR+ 加同色发光边框）+ 角色名/NEW 徽标 + 右侧碎片与时间。 */
@Composable
private fun HistoryRow(entry: PullLogEntry, timeText: String) {
    val rc = AppTheme.rarityColor(entry.rarity)
    val premium = entry.rarity >= 3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(AppTheme.Surface)
            .border(
                width = if (premium) 1.dp else 0.dp,
                color = if (premium) rc.copy(alpha = 0.55f) else AppTheme.Surface,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphBadge(
            // 单字徽记（金/紫/蓝/白）对应稀有度主色，与全站稀有度视觉同语言
            glyph = when (entry.rarity) {
                4 -> "金"
                3 -> "紫"
                2 -> "蓝"
                else -> "白"
            },
            from = rc,
            to = rc.copy(alpha = 0.6f),
            glyphColor = rc,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = entry.characterName.ifEmpty { "未知角色" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = AppTheme.Text1,
            maxLines = 1,
        )
        if (entry.isNew) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "NEW",
                fontWeight = FontWeight.Bold,
                color = AppTheme.GoldTextOn,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppTheme.Roundness.xs))
                    .background(AppTheme.Gold)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (entry.fragmentsAwarded > 0) {
            Text(
                text = "碎片 +${entry.fragmentsAwarded}",
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.Frost,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text = timeText, style = MaterialTheme.typography.labelMedium, color = AppTheme.Text3)
    }
}
