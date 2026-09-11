package com.milan.game.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milan.game.domain.battle.BattlePhase
import com.milan.game.domain.battle.BattleUnitState
import com.milan.game.domain.battle.SkillTarget
import com.milan.game.services.TowerOutcome
import com.milan.game.ui.components.BattleResultOverlay
import com.milan.game.ui.components.GlassDialog
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.components.InkButton
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember

/**
 * 可操作战斗全屏层（2026-09-09）。
 * 敌方上排 → 战报 → 我方下排 → 技能条。选技能（单体再点目标）→ 确认。
 */
@Composable
fun StrategicBattleScreen(
    floor: Int,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: StrategicBattleViewModel = viewModel(factory = com.milan.game.di.AppGraph.factory)
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(floor) { vm.start(floor) }

    // 战斗 BGM：进入切 battle，退出切回 theme（资源缺失静默）
    LaunchedEffect(Unit) {
        com.milan.game.infrastructure.MilanAudio.playBgm("battle")
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            com.milan.game.infrastructure.MilanAudio.playBgm("theme")
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PageBackground {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                // 顶栏
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Text(
                        text = "策略战斗 · 第 ${ui.floor} 层",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTheme.Text1,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "回合 ${ui.state?.turn ?: 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = AppTheme.Gold,
                    )
                    Spacer(Modifier.width(12.dp))
                    InkButton(
                        text = "退出",
                        color = AppTheme.Text3,
                        onClick = onExit,
                    )
                }

                val st = ui.state
                if (st == null) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("准备中…", color = AppTheme.Text2)
                    }
                    return@Column
                }

                // 打击演出：按单位 id 挂 punch/shake/death 驱动器（id 稳定则复用）
                // R6-P1：不在组合体里直接写 map（非法 side effect）；SideEffect 填表，
                // 单元 id 变化时 reset 防死亡残留。缺失项不临时 new，避免每帧分配。
                val playerFx = remember { mutableStateMapOf<String, UnitStrikeFx>() }
                val enemyFx = remember { mutableStateMapOf<String, UnitStrikeFx>() }
                val playerKeys = st.playerTeam.mapIndexed { i, u ->
                    u.stats.characterId.ifEmpty { "p_$i" }
                }
                val enemyKeys = st.enemyTeam.mapIndexed { i, u ->
                    u.stats.characterId.ifEmpty { "e_$i" }
                }
                SideEffect {
                    playerKeys.forEach { k -> playerFx.getOrPut(k) { UnitStrikeFx() } }
                    enemyKeys.forEach { k -> enemyFx.getOrPut(k) { UnitStrikeFx() } }
                }
                LaunchedEffect(playerKeys, enemyKeys) {
                    playerKeys.forEach { playerFx[it]?.reset() }
                    enemyKeys.forEach { enemyFx[it]?.reset() }
                }
                BattleStrikeOrchestrator(
                    pulses = ui.fxPulses,
                    playerFx = playerFx,
                    enemyFx = enemyFx,
                    onConsumed = vm::clearFx,
                )

                Spacer(Modifier.height(6.dp))
                Text("敌方", style = MaterialTheme.typography.labelLarge, color = AppTheme.Danger, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    st.enemyTeam.forEachIndexed { i, u ->
                        val unitId = u.stats.characterId.ifEmpty { "e_$i" }
                        UnitTile(
                            unit = u,
                            selected = ui.needTarget && ui.selectedTarget == i &&
                                ui.selectedSkillId?.let { sid ->
                                    st.playerTeam.getOrNull(ui.currentActor)?.skills
                                        ?.firstOrNull { it.skillId == sid }
                                        ?.target == SkillTarget.SINGLE_ENEMY
                                } == true,
                            onClick = {
                                val sid = ui.selectedSkillId ?: return@UnitTile
                                val skill = st.playerTeam.getOrNull(ui.currentActor)
                                    ?.skills?.firstOrNull { it.skillId == sid } ?: return@UnitTile
                                if (skill.target == SkillTarget.SINGLE_ENEMY && u.hp > 0) {
                                    vm.selectTarget(i)
                                }
                            },
                            modifier = Modifier.weight(1f).then(
                                enemyFx[unitId]?.let { Modifier.unitStrikeLayer(it) } ?: Modifier,
                            ),
                        )
                    }
                }

                // 战报
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppTheme.BgMid.copy(alpha = 0.85f))
                        .border(1.dp, AppTheme.Stroke, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                ) {
                    LazyColumn(reverseLayout = true) {
                        items(ui.logLines.asReversed()) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelMedium,
                                color = AppTheme.Text2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 1.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                if (ui.enemyActing) {
                    Text(
                        text = "敌方行动中…",
                        color = AppTheme.Danger,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text("我方", style = MaterialTheme.typography.labelLarge, color = AppTheme.Frost, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    st.playerTeam.forEachIndexed { i, u ->
                        val unitId = u.stats.characterId.ifEmpty { "p_$i" }
                        UnitTile(
                            unit = u,
                            selected = i == ui.currentActor && u.hp > 0,
                            isActor = i == ui.currentActor,
                            onClick = {
                                val sid = ui.selectedSkillId ?: return@UnitTile
                                val skill = st.playerTeam.getOrNull(ui.currentActor)
                                    ?.skills?.firstOrNull { it.skillId == sid } ?: return@UnitTile
                                if (skill.target == SkillTarget.SINGLE_ALLY && u.hp > 0) {
                                    vm.selectTarget(i)
                                }
                            },
                            modifier = Modifier.weight(1f).then(
                                playerFx[unitId]?.let { Modifier.unitStrikeLayer(it) } ?: Modifier,
                            ),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // 技能条
                val actor = st.playerTeam.getOrNull(ui.currentActor)
                if (actor != null && actor.hp > 0 && !ui.finished && !ui.enemyActing) {
                    Text(
                        text = "行动：${actor.stats.characterId.ifEmpty { "单位${ui.currentActor + 1}" }} · 能量 ${actor.energy}/${actor.maxEnergy}",
                        style = MaterialTheme.typography.labelLarge,
                        color = AppTheme.Text2,
                    )
                    Spacer(Modifier.height(6.dp))
                    val battleView = androidx.compose.ui.platform.LocalView.current
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        actor.skills.forEach { skill ->
                            val ready = skillReady(actor, skill.skillId)
                            val selected = ui.selectedSkillId == skill.skillId
                            val label = buildString {
                                append(skill.name)
                                if (skill.energyCost > 0) append("\n⚡${skill.energyCost}")
                                val cd = actor.cooldowns[skill.skillId] ?: 0
                                if (cd > 0) append("\nCD$cd")
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) AppTheme.Gold.copy(alpha = 0.25f)
                                        else if (ready) AppTheme.BgMid
                                        else AppTheme.BgMid.copy(alpha = 0.4f)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) AppTheme.Gold else AppTheme.Stroke,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable(enabled = ready) {
                                        // 2026-09-10：选技能轻触觉，给出「已选中」的物理确认
                                        if (ready) {
                                            com.milan.game.infrastructure.HapticManager.buttonClick(battleView)
                                        }
                                        vm.selectSkill(skill.skillId)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (ready) AppTheme.Text1 else AppTheme.Text3,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 14.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GoldButton(
                            text = "确认行动",
                            modifier = Modifier.weight(1f),
                            onClick = { vm.confirm() },
                        )
                    }
                }
            }
        }

        // 全屏打击层（须在 PageBackground 外的 Box 内，避免挤占 Column 流）
        if (ui.state != null) {
            val skillPulse = ui.fxPulses.lastOrNull { it.kind != StrikeFxKind.NORMAL }
            BattleSkillVignette(pulse = skillPulse, modifier = Modifier.matchParentSize())
            val hurtPulse = ui.fxPulses.lastOrNull {
                it.targetIsPlayer && it.damage > 0 && !it.targetDefeated
            }
            BattleHurtFlash(pulse = hurtPulse, modifier = Modifier.matchParentSize())
        }

        // 结算：与自动爬塔共用 BattleResultOverlay（粒子/触觉/奖励滚动），非 Completed 仍用轻量 Dialog
        val outcome = ui.outcome
        if (outcome != null) {
            when (outcome) {
                is TowerOutcome.Completed -> BattleResultOverlay(
                    victory = outcome.victory,
                    turns = outcome.turns,
                    rewardSoft = outcome.rewardSoft,
                    rewardHard = outcome.rewardHard,
                    recordAdvanced = outcome.recordAdvanced,
                    bestFloorAfter = outcome.bestFloorAfter,
                    log = outcome.log,
                    onDismiss = { vm.dismiss(); onExit() },
                )
                else -> StrategicResultDialog(outcome = outcome, onDismiss = { vm.dismiss(); onExit() })
            }
        }
    }
}

@Composable
private fun UnitTile(
    unit: BattleUnitState,
    selected: Boolean,
    modifier: Modifier = Modifier,
    isActor: Boolean = false,
    onClick: () -> Unit = {},
) {
    val elem = ElementTheme.forElement(unit.stats.element)
    val hpRatio = if (unit.maxHp <= 0) 0f else (unit.hp.toFloat() / unit.maxHp).coerceIn(0f, 1f)
    val dead = unit.hp <= 0
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    dead -> Color.Black.copy(alpha = 0.45f)
                    isActor -> AppTheme.Gold.copy(alpha = 0.12f)
                    selected -> elem.glow.copy(alpha = 0.18f)
                    else -> AppTheme.BgMid
                }
            )
            .border(
                width = if (selected || isActor) 1.5.dp else 1.dp,
                color = when {
                    dead -> AppTheme.Stroke
                    isActor -> AppTheme.Gold
                    selected -> elem.glow
                    else -> AppTheme.Stroke
                },
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(enabled = !dead, onClick = onClick)
            .padding(8.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(elem.from.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(elem.glyph, style = MaterialTheme.typography.labelSmall, color = elem.glow, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = unit.stats.characterId.substringAfterLast('_').ifEmpty { "?" },
                    color = if (dead) AppTheme.Text3 else AppTheme.Text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { hpRatio },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (hpRatio > 0.5f) AppTheme.Success else if (hpRatio > 0.25f) AppTheme.Warning else AppTheme.Danger,
                trackColor = AppTheme.BgDeepest,
            )
            Text(
                text = "${unit.hp.coerceAtLeast(0)}/${unit.maxHp}",
                color = AppTheme.Text3,
                modifier = Modifier.padding(top = 2.dp),
            )
            // 能量条
            val en = if (unit.maxEnergy <= 0) 0f else (unit.energy.toFloat() / unit.maxEnergy).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { en },
                modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 3.dp),
                color = AppTheme.Frost,
                trackColor = AppTheme.BgDeepest,
            )
        }
    }
}

@Composable
private fun StrategicResultDialog(
    outcome: TowerOutcome,
    onDismiss: () -> Unit,
) {
    val title = when (outcome) {
        is TowerOutcome.Completed -> if (outcome.victory) "胜利" else "失败"
        is TowerOutcome.Draw -> "平局"
        is TowerOutcome.Rejected -> "无法挑战"
        is TowerOutcome.SaveFailed -> "保存失败"
    }
    val body = when (outcome) {
        is TowerOutcome.Completed -> buildString {
            if (outcome.victory) {
                append("推进至第 ${outcome.bestFloorAfter} 层\n")
                append("星尘 +${outcome.rewardSoft}")
                if (outcome.rewardHard > 0) append("  星玉 +${outcome.rewardHard}")
                if (outcome.rewardExp > 0) append("\n经验 +${outcome.rewardExp}")
            } else {
                append("再接再厉，调整编队后再战。")
            }
        }
        is TowerOutcome.Draw -> "回合耗尽，不消耗战票。"
        is TowerOutcome.Rejected -> "战票不足或编队为空。"
        is TowerOutcome.SaveFailed -> "存档失败，请重试。"
    }
    GlassDialog(show = true, onDismiss = onDismiss, title = title, body = body) {
        GoldButton(text = "知道了", onClick = onDismiss)
    }
}
