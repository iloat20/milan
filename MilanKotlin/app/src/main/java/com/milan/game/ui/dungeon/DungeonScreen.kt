package com.milan.game.ui.dungeon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milan.game.data.DailyDungeonType
import com.milan.game.di.AppGraph
import com.milan.game.ui.battle.StrategicBattleMode
import com.milan.game.ui.battle.StrategicBattleScreen
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme

/**
 * 日常副本 + 深渊入口（2026-09-12 Dungeon keep：服务已有，UI 此前零调用）。
 * 2026-09-12 起深渊走 [StrategicBattleScreen] 真战斗，胜利后自动结算星级。
 */
@Composable
fun DungeonScreen(onBack: () -> Unit) {
    val feedback = LocalFeedback.current
    val vm: DungeonViewModel = viewModel(factory = AppGraph.factory)
    val ui by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.toasts.collect { feedback.show(it) } }

    var showBattle by rememberSaveable { mutableStateOf(false) }
    var battleFloor by rememberSaveable { mutableStateOf(1) }

    PageBackground {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(title = "副本 · 深渊", onBack = onBack)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("日常副本", color = AppTheme.Text3)
                Spacer(Modifier.height(8.dp))
                ui.daily.forEach { st ->
                    DailyRow(
                        name = st.type.displayName(),
                        remaining = st.remaining,
                        max = st.maxChallenges,
                        onSweep = { vm.sweep(st.type, times = 1) },
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text("无尽深渊", color = AppTheme.Text3)
                Spacer(Modifier.height(8.dp))
                val abyss = ui.abyss
                if (abyss == null) {
                    Text("暂无深渊数据", color = AppTheme.Text3)
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AppTheme.Roundness.md))
                            .background(AppTheme.Surface.copy(alpha = 0.55f))
                            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(AppTheme.Roundness.md))
                            .padding(14.dp),
                    ) {
                        Text("当前层 ${abyss.currentFloor} · 最佳 ${abyss.bestFloor}", color = AppTheme.Text1)
                        Text(
                            "总星数 ${abyss.totalStars} · 今日剩余 " +
                                "${abyss.remainingChallenges}/${abyss.maxChallenges}",
                            color = AppTheme.Text2,
                        )
                        Spacer(Modifier.height(10.dp))
                        ActionChip(
                            "进入战斗",
                            enabled = abyss.remainingChallenges > 0,
                        ) {
                            battleFloor = abyss.currentFloor
                            showBattle = true
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "星级：无阵亡 3★ · 1 人阵亡 2★ · 其余胜局 1★；首通/新星发奖。",
                            color = AppTheme.Text3,
                        )
                        if (com.milan.game.domain.progression.EconomyFormulas
                                .isAbyssEliteFloor(abyss.currentFloor, isAbyss = true)
                        ) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "本层为精英层：0 号位精英（属性 ×1.35 + 焚心词缀）",
                                color = AppTheme.Gold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showBattle) {
        StrategicBattleScreen(
            floor = battleFloor,
            mode = StrategicBattleMode.ABYSS,
            onExit = {
                showBattle = false
                vm.refresh()
            },
        )
    }
}

@Composable
private fun DailyRow(name: String, remaining: Int, max: Int, onSweep: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(AppTheme.Roundness.md))
            .background(AppTheme.Surface.copy(alpha = 0.5f))
            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(AppTheme.Roundness.md))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = AppTheme.Text1)
            Text("剩余 $remaining / $max", color = AppTheme.Text3)
        }
        ActionChip("扫荡×1", enabled = remaining > 0, onClick = onSweep)
    }
}

@Composable
private fun ActionChip(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        color = if (enabled) AppTheme.Gold else AppTheme.Text3,
        modifier = Modifier
            .clip(RoundedCornerShape(AppTheme.Roundness.xl))
            .background(AppTheme.Surface)
            .border(1.dp, AppTheme.Gold.copy(alpha = 0.5f), RoundedCornerShape(AppTheme.Roundness.xl))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

private fun DailyDungeonType.displayName(): String = when (this) {
    DailyDungeonType.GOLD_DUNGEON -> "金币本"
    DailyDungeonType.EXP_DUNGEON -> "经验本"
    DailyDungeonType.MATERIAL_DUNGEON -> "素材本"
    DailyDungeonType.EQUIPMENT_DUNGEON -> "装备本"
    DailyDungeonType.FRAGMENT_DUNGEON -> "碎片本"
}
