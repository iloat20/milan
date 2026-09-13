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
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember

/**
 * 可操作战斗全屏层 · **对弈台**（独立于菜单织环台）。
 * 敌方上排 → 战报 → 我方下排 → 技能条。选技能（单体再点目标）→ 确认。
 * 视觉走 [BattleTheme]：高对比实底、敌我双色，禁装饰金环。
 */
@Composable
fun StrategicBattleScreen(
    floor: Int,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    mode: StrategicBattleMode = StrategicBattleMode.TOWER,
    storyStageId: String? = null,
) {
    val vm: StrategicBattleViewModel = viewModel(factory = com.milan.game.di.AppGraph.factory)
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(floor, mode, storyStageId) { vm.start(floor, mode, storyStageId) }

    LaunchedEffect(Unit) {
        com.milan.game.infrastructure.MilanAudio.playBgm("battle")
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            com.milan.game.infrastructure.MilanAudio.playBgm("theme")
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 对弈台底：独立实底，不用菜单 PageBackground
        Box(
            Modifier
                .fillMaxSize()
                .background(BattleTheme.Stage0)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(BattleTheme.Stage2.copy(alpha = 0.55f), BattleTheme.Stage0, BattleTheme.Stage1),
                        )
                    )
            )
        }
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            // 顶栏：战术条
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BattleTheme.Stage2)
                    .border(1.dp, BattleTheme.Line, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = when (mode) {
                        StrategicBattleMode.ABYSS -> "深渊 · 第 ${ui.floor} 层"
                        StrategicBattleMode.STORY -> "剧情 · 战斗"
                        else -> "对弈 · 第 ${ui.floor} 层"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BattleTheme.Text,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "回合 ${ui.state?.turn ?: 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = BattleTheme.Focus,
                )
                Spacer(Modifier.width(8.dp))
                SpeedToggle(
                    label = if (ui.speedMul >= 2f) "×2" else "×1",
                    active = ui.speedMul >= 2f,
                    onClick = { vm.toggleSpeed() },
                )
                Spacer(Modifier.width(6.dp))
                SpeedToggle(
                    label = "自动",
                    active = ui.autoBattle,
                    onClick = { vm.toggleAuto() },
                )
                Spacer(Modifier.width(8.dp))
                BattleFlatChip(
                    text = "退出",
                    tone = BattleTheme.TextDim,
                    onClick = onExit,
                )
            }

            val st = ui.state
            if (st == null) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("准备中…", color = BattleTheme.TextDim)
                }
                return@Column
            }

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

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(12.dp)
                        .background(BattleTheme.Enemy),
                )
                Spacer(Modifier.width(6.dp))
                Text("敌方", style = MaterialTheme.typography.labelLarge, color = BattleTheme.Enemy, fontWeight = FontWeight.Bold)
            }
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
                        isEnemy = true,
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

            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(BattleTheme.Stage2)
                    .border(1.dp, BattleTheme.Line, RoundedCornerShape(6.dp))
                    .padding(8.dp),
            ) {
                LazyColumn(reverseLayout = true) {
                    items(ui.logLines.asReversed()) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelMedium,
                            color = BattleTheme.TextDim,
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
                    color = BattleTheme.Enemy,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(12.dp)
                        .background(BattleTheme.Ally),
                )
                Spacer(Modifier.width(6.dp))
                Text("我方", style = MaterialTheme.typography.labelLarge, color = BattleTheme.Ally, fontWeight = FontWeight.Bold)
            }
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
                        isEnemy = false,
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

            val actor = st.playerTeam.getOrNull(ui.currentActor)
            if (actor != null && actor.hp > 0 && !ui.finished && !ui.enemyActing && !ui.autoBattle) {
                Text(
                    text = "行动：${actor.stats.characterId.ifEmpty { "单位${ui.currentActor + 1}" }} · 能量 ${actor.energy}/${actor.maxEnergy}",
                    style = MaterialTheme.typography.labelLarge,
                    color = BattleTheme.TextDim,
                )
                Spacer(Modifier.height(6.dp))
                val battleView = androidx.compose.ui.platform.LocalView.current
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actor.skills.forEach { skill ->
                        val ready = skillReady(actor, skill.skillId)
                        val selected = ui.selectedSkillId == skill.skillId
                        val label = buildString {
                            append(skill.name)
                            if (skill.energyCost > 0) append("\n能${skill.energyCost}")
                            val cd = actor.cooldowns[skill.skillId] ?: 0
                            if (cd > 0) append("\nCD$cd")
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        selected -> BattleTheme.Focus.copy(alpha = 0.22f)
                                        ready -> BattleTheme.Stage2
                                        else -> BattleTheme.Stage1
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (selected) BattleTheme.Focus else BattleTheme.Line,
                                    RoundedCornerShape(6.dp),
                                )
                                .clickable(enabled = ready) {
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
                                color = if (ready) BattleTheme.Text else BattleTheme.TextDim,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // 战斗确认：对弈台原生主按钮（Focus 实底，无金丝）
                BattlePrimaryButton(
                    text = "确认行动",
                    onClick = { vm.confirm() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (ui.state != null) {
            val skillPulse = ui.fxPulses.lastOrNull { it.kind != StrikeFxKind.NORMAL }
            BattleSkillVignette(pulse = skillPulse, modifier = Modifier.matchParentSize())
            val hurtPulse = ui.fxPulses.lastOrNull {
                it.targetIsPlayer && it.damage > 0 && !it.targetDefeated
            }
            BattleHurtFlash(pulse = hurtPulse, modifier = Modifier.matchParentSize())
        }

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
    isEnemy: Boolean = false,
    onClick: () -> Unit = {},
) {
    val elem = ElementTheme.forElement(unit.stats.element)
    val hpRatio = if (unit.maxHp <= 0) 0f else (unit.hp.toFloat() / unit.maxHp).coerceIn(0f, 1f)
    val dead = unit.hp <= 0
    val sideColor = if (isEnemy) BattleTheme.Enemy else BattleTheme.Ally
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    dead -> Color.Black.copy(alpha = 0.55f)
                    isActor -> BattleTheme.Focus.copy(alpha = 0.14f)
                    selected -> sideColor.copy(alpha = 0.16f)
                    else -> BattleTheme.Stage1
                }
            )
            .border(
                width = if (selected || isActor) 1.5.dp else 1.dp,
                color = when {
                    dead -> BattleTheme.Line
                    isActor -> BattleTheme.Focus
                    selected -> sideColor
                    else -> sideColor.copy(alpha = 0.45f)
                },
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(enabled = !dead, onClick = onClick)
            .padding(8.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(elem.from.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(elem.glyph, style = MaterialTheme.typography.labelSmall, color = elem.glow, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = unit.stats.characterId.substringAfterLast('_').ifEmpty { "?" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (dead) BattleTheme.TextDim else BattleTheme.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { hpRatio },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = BattleTheme.hpColor(hpRatio),
                trackColor = BattleTheme.Stage0,
            )
            Text(
                text = "${unit.hp.coerceAtLeast(0)}/${unit.maxHp}",
                style = MaterialTheme.typography.labelSmall,
                color = BattleTheme.TextDim,
                modifier = Modifier.padding(top = 2.dp),
            )
            val en = if (unit.maxEnergy <= 0) 0f else (unit.energy.toFloat() / unit.maxEnergy).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { en },
                modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 3.dp),
                color = BattleTheme.Ally,
                trackColor = BattleTheme.Stage0,
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
                append("环痕 +${outcome.rewardSoft}")
                if (outcome.rewardHard > 0) append("  纯环 +${outcome.rewardHard}")
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
        BattlePrimaryButton(
            text = "知道了",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 顶栏小开关：自动战斗 / 倍速（战术 HUD，Focus 色）。 */
@Composable
private fun SpeedToggle(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (active) BattleTheme.Stage0 else BattleTheme.TextDim,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) BattleTheme.Focus else BattleTheme.Stage1)
            .border(
                1.dp,
                if (active) BattleTheme.Focus else BattleTheme.Line,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
