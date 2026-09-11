package com.milan.game.ui.tower

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.milan.game.domain.battle.ElementChart
import com.milan.game.domain.battle.StrikeEvent
import com.milan.game.domain.battle.UnitStats
import com.milan.game.services.TowerOutcome
import com.milan.game.ui.components.EntranceItem
import com.milan.game.ui.components.FormationBar
import com.milan.game.ui.components.GlyphBadge
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.components.InkButton
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.battle.StrategicBattleScreen
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme

/**
 * 无尽之塔（2026-08 终局内容，对标 StS 进阶难度 / Balatro 无尽模式的长线留存定位）：
 * - 挑战「最高层 +1」；胜利推进纪录并发放星尘（数值走 EconomyFormulas 单一事实来源）；
 * - 敌队按层数程序化生成且元素随机分布——编队的元素克制与共鸣成为爬层策略；
 * - 空编队时引导去卡组页组队；战斗为服务层纯模拟，本页只做状态与结果呈现。
 */
@Composable
fun TowerScreen(
    onBack: () -> Unit,
    onOpenDeck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // P1-6 E 批：派生状态与挑战状态机收敛进 TowerViewModel；
    // 战力/元素/奖励预览已预计算进 TowerUiState，本屏纯渲染。
    val vm: TowerViewModel = viewModel(factory = com.milan.game.di.AppGraph.factory)
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    // rememberSaveable：进程死亡/配置变更后策略战斗层可恢复（此前 remember 会丢）
    var showStrategic by rememberSaveable { mutableStateOf(false) }
    val best = ui.best
    val nextFloor = ui.nextFloor
    val ticketCost = ui.ticketCost
    val tickets = ui.tickets
    val canChallenge = ui.canChallenge

    // 编队成员战力预览（M5：成员已按编队槽位顺序在 VM 内映射）。
    val members = ui.members
    val teamPower = ui.teamPower

    Box(modifier = modifier.fillMaxSize()) {
    PageBackground(modifier = Modifier) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(title = "无 尽 之 塔", onBack = onBack)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
            ) {
                Spacer(Modifier.height(10.dp))

                // ── 纪录卡：当前最高层 + 编队战力（渐变金字 + 战票徽章）──
                EntranceItem(index = 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            Brush.verticalGradient(
                                listOf(AppTheme.Surface.copy(alpha = 0.85f), AppTheme.BgMid.copy(alpha = 0.6f)),
                            )
                        )
                        .border(1.dp, AppTheme.Gold.copy(alpha = 0.35f), MaterialTheme.shapes.large)
                        .padding(16.dp),
                ) {
                    Text(text = "历史最高", style = MaterialTheme.typography.bodySmall, color = AppTheme.Text2)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (best == 0) "未挑战" else "第 $best 层",
                            fontWeight = FontWeight.Bold,
                            style = androidx.compose.ui.text.TextStyle(
                                brush = if (best > 0) {
                                    Brush.verticalGradient(listOf(AppTheme.GoldHi, AppTheme.GoldDeep))
                                } else {
                                    Brush.verticalGradient(listOf(AppTheme.Text1, AppTheme.Text1))
                                },
                            ),
                        )
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphBadge(glyph = "⚔", from = AppTheme.Text1, to = AppTheme.Text3, glyphColor = AppTheme.Text2)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "$tickets · 战力 $teamPower",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.Text2,
                            )
                        }
                    }
                }
                }

                Spacer(Modifier.height(14.dp))
                FormationBar(
                    members = members,
                    maxSlots = com.milan.game.data.SaveData.MAX_FORMATION_SIZE,
                    onSlotClick = { onOpenDeck() },
                )

                // ── 战力对比预览（我方 vs 敌方）──
                if (members.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    BattlePreviewCard(
                        myTeam = ui.teamStats,
                        myElements = ui.teamElements,
                        floor = nextFloor,
                        myPower = teamPower,
                        enemy = ui.enemyPreview,
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (ui.formationEmpty) {
                    com.milan.game.ui.components.EmptyState(
                        icon = Icons.Outlined.Person,
                        title = "还没有出战编队",
                        subtitle = "先去卡组页点选角色入队",
                        actionText = "前往编队",
                        onAction = onOpenDeck,
                    )
                } else {
                    // 下一层挑战：奖励预览按 EconomyFormulas 计算，禁止就地写数字。
                    Text(
                        text = "第 $nextFloor 层 · 入场 ⚔$ticketCost · 预计通关星尘 ${ui.nextFloorRewardSoft}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.Text2,
                    )
                    if (!canChallenge) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "战票不足（需 $ticketCost 张）——去商店「每日补给」免费领取",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.Gold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    GoldButton(
                        text = when {
                            running -> "战斗中…"
                            !canChallenge -> "战票不足"
                            else -> "⚔ 挑战第 $nextFloor 层"
                        },
                        onClick = { vm.challengeNext() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canChallenge && !running,
                    )
                    // 策略挑战：可操作回合制（选技能/选目标）
                    if (canChallenge && !running && members.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        InkButton(
                            text = "策略挑战第 $nextFloor 层（可操作）",
                            onClick = { showStrategic = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canChallenge && !running,
                        )
                    }
                    // 已通层的复刷入口：低层速刷拿保底星尘收益（数值线性，低层仍有意义）。
                    if (best > 0) {
                        Spacer(Modifier.height(8.dp))
                        InkButton(
                            text = "复刷第 $best 层（⚔$ticketCost）",
                            onClick = { vm.retryBest() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canChallenge && !running,
                        )
                    }
                }

                // ── 结算卡（AnimatedVisibility 弹出演出；胜利金渐变底）──
                androidx.compose.animation.AnimatedVisibility(
                    visible = result is TowerOutcome.Completed,
                    enter = androidx.compose.animation.fadeIn(
                        androidx.compose.animation.core.tween(240)
                    ) + androidx.compose.animation.scaleIn(
                        initialScale = 0.94f,
                        animationSpec = androidx.compose.animation.core.tween(240),
                    ),
                ) {
                    // F2（2026-08-28 审查修复）：退出动画期间 content 仍会重组，而 result 可能已被
                    // 下一次挑战改写为 Draw / SaveFailed —— 不安全强转会抛 ClassCastException 闪退。
                    // 用 as? + 提前返回，与文件下方「本地快照」范式（:266）保持一致。
                    val done = result as? TowerOutcome.Completed ?: return@AnimatedVisibility
                    TowerResultCard(done = done, names = ui.characterNames)
                }
                val outcome = result  // 本地快照，解决委托属性无法 smart-cast
                when (outcome) {
                    TowerOutcome.Rejected -> Text(
                        text = "挑战被拒绝：请检查编队配置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.Text3,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TowerOutcome.SaveFailed -> Text(
                        text = "存档失败，本次结果已回滚，请重试。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.Text3,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    is TowerOutcome.Draw -> {
                        BattleReportSection(outcome.log, ui.characterNames)
                        Text(
                            text = "僵局（${outcome.turns} 回合）——双方仍存活，门票已退还。",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.Text2,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    else -> Unit
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // ── 全屏战斗结算覆盖层（2026-09 战斗视觉增强）──
    val completedResult = result as? TowerOutcome.Completed
    if (completedResult != null) {
        com.milan.game.ui.components.BattleResultOverlay(
            victory = completedResult.victory,
            turns = completedResult.turns,
            rewardSoft = completedResult.rewardSoft,
            rewardHard = completedResult.rewardHard,
            recordAdvanced = completedResult.recordAdvanced,
            bestFloorAfter = completedResult.bestFloorAfter,
            onDismiss = { vm.dismissResult() },
            log = completedResult.log,
        )
    }

    // ── 可操作策略战斗全屏层 ──
    if (showStrategic) {
        StrategicBattleScreen(
            floor = nextFloor,
            onExit = { showStrategic = false },
        )
    }
    }
}

// ── 战报（2026-08：BattleSimulator 回合明细 → 折叠式逐回合攻击流水）──

/** 战报折叠区：默认收起，展开后限高内部滚动（外层 verticalScroll 不受嵌套滚动干扰）。 */
@Composable
private fun BattleReportSection(log: List<StrikeEvent>, names: Map<String, String>) {
    if (log.isEmpty()) return
    // result 每次挑战整体替换 → remember(result 实例) 以 log 身份作 key，新战斗自动折叠复位
    var expanded by remember(log) { mutableStateOf(false) }
    Text(
        text = if (expanded) "▾ 收起战报" else "▸ 查看战报（${log.size} 次攻击）",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = AppTheme.Frost,
        modifier = Modifier
            .padding(top = 4.dp)
            .clickable { expanded = !expanded },
    )
    if (expanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .clip(MaterialTheme.shapes.small)
                .background(AppTheme.BgDeepest.copy(alpha = 0.55f))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            log.forEach { e -> StrikeRow(e, names) }
        }
    }
}

/** 单条攻击流水行：元素图标 + 攻击者 → 目标 + 伤害色阶 + 克制标记 + 击杀特效。 */
@Composable
private fun StrikeRow(e: StrikeEvent, names: Map<String, String>) {
    val counterMul = ElementChart.damageMultiplier(e.attackerElement, e.targetElement)
    val counter = counterMul > 1.05
    val defeated = e.targetDefeated

    // 元素身份
    val attackerEi = remember(e.attackerElement) { ElementTheme.forElement(e.attackerElement) }
    val targetEi = remember(e.targetElement) { ElementTheme.forElement(e.targetElement) }

    // 伤害色阶：低=白 中=金 高=橙 暴击=红
    val damageColor = when {
        defeated -> AppTheme.SealRed
        counter -> AppTheme.Gold
        e.damage > 200 -> AppTheme.Warning
        e.damage > 100 -> AppTheme.GoldHi
        else -> AppTheme.Text1
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(
                if (defeated) Modifier
                    .clip(RoundedCornerShape(AppTheme.Roundness.xs))
                    .background(AppTheme.Danger.copy(alpha = 0.08f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                else Modifier
            ),
    ) {
        // 回合号
        Text(
            text = "R${e.turn}",
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.Text3,
            modifier = Modifier.width(28.dp),
        )

        // 攻击者：元素图标 + 名称
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(72.dp),
        ) {
            Text(
                text = attackerEi.glyph,
                color = attackerEi.glow,
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(AppTheme.Roundness.xxs))
                    .background(attackerEi.glow.copy(alpha = 0.15f))
                    .padding(1.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = unitLabel(e.attackerId, e.attackerElement, names),
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.Text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        // 箭头
        Text(
            text = "→",
            color = AppTheme.Text3,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        // 目标：元素图标 + 名称
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(72.dp),
        ) {
            Text(
                text = targetEi.glyph,
                style = MaterialTheme.typography.labelMedium,
                color = targetEi.glow,
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(AppTheme.Roundness.xxs))
                    .background(targetEi.glow.copy(alpha = 0.15f))
                    .padding(1.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = unitLabel(e.targetId, e.targetElement, names),
                style = MaterialTheme.typography.labelSmall,
                color = if (defeated) AppTheme.Danger else AppTheme.Text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        // 伤害值：色阶 + 克制/击杀标记
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp),
        ) {
            Text(
                text = "-${e.damage}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFeatureSettings = "tnum",
                ),
                fontWeight = FontWeight.Bold,
                color = damageColor,
            )
            if (counter) {
                Text(
                    text = " 克",
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Gold,
                    modifier = Modifier
                        .padding(start = 3.dp)
                        .clip(RoundedCornerShape(AppTheme.Roundness.xxs))
                        .background(AppTheme.Gold.copy(alpha = 0.15f))
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
            if (defeated) {
                Text(
                    text = " †",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.SealRed,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
    }
}

/**
 * 爬塔结算卡（2026-08-28 P0 重构：自 TowerScreen 的 AnimatedVisibility 内联块抽出）。
 *
 * 抽出的两个目的：
 * 1. **可测试**：F2 的闪退就发生在结算卡渲染路径上（result 被下一次挑战改写后
 *    退出动画仍重组 → 不安全强转抛 ClassCastException）。内联块无法单独构造，
 *    抽成 composable 后可直接用 Compose UI Test 覆盖。
 * 2. **可读性**：原内联 50 行让 TowerScreen 主体更难读。
 */
@Composable
internal fun TowerResultCard(
    done: TowerOutcome.Completed,
    modifier: Modifier = Modifier,
    /** 战报单位名解析表（内容表静态映射；默认空 → 单位显示「未知」，测试内容无关）。 */
    names: Map<String, String> = emptyMap(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (done.victory) {
                    Brush.verticalGradient(
                        listOf(AppTheme.Gold.copy(alpha = 0.16f), AppTheme.Surface.copy(alpha = 0.7f)),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(AppTheme.Surface.copy(alpha = 0.7f), AppTheme.BgMid.copy(alpha = 0.6f)),
                    )
                }
            )
            .border(
                1.dp,
                if (done.victory) AppTheme.Gold.copy(alpha = 0.6f) else AppTheme.Stroke,
                MaterialTheme.shapes.medium,
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (done.victory) "✦ 攻克！用时 ${done.turns} 回合" else "✖ 止步于此（${done.turns} 回合）",
            style = MaterialTheme.typography.titleSmall,
            color = if (done.victory) AppTheme.Gold else AppTheme.Text2,
        )
        if (done.victory) {
            Text(text = "星尘 +${done.rewardSoft}", style = MaterialTheme.typography.bodyMedium, color = AppTheme.Text1)
            if (done.rewardHard > 0) {
                Text(
                    text = "◆ 钻石 +${done.rewardHard}（首次攻克里程碑）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.Frost,
                )
            }
            // M1：改用服务层显式判定。原条件 bestFloorAfter >= nextFloor 恒为 false
            //（nextFloor 在结算返回前已被快照刷新为 best+1），导致该文案永不显示。
            if (done.recordAdvanced) {
                Text(text = "纪录推进至第 ${done.bestFloorAfter} 层", style = MaterialTheme.typography.bodySmall, color = AppTheme.Text2)
            }
        }
        BattleReportSection(done.log, names)
        Text(
            text = "提示：敌方元素随层数轮转，用克制元素编队能显著降低损血。",
            style = MaterialTheme.typography.labelMedium,
            color = AppTheme.Text3,
        )
    }
}

// ── 战力对比预览卡片（2026-09 战斗视觉增强）──

/**
 * 战力对比预览：展示我方 vs 敌方阵容、元素分布、战力对比条。
 * 敌方数据由 [TowerViewModel] 预计算（[TowerEnemyPreview]，与 TowerService 同源）。
 */
@Composable
private fun BattlePreviewCard(
    myTeam: List<UnitStats>,
    myElements: List<String>,
    floor: Int,
    myPower: Int,
    enemy: TowerEnemyPreview?,
) {
    val enemyCount = enemy?.count ?: 0
    val enemyPower = enemy?.power ?: 0
    val enemyScale = enemy?.scale ?: 1.0
    val enemyElements = enemy?.elements.orEmpty()

    // 元素分布统计
    val myElementCounts = remember(myElements) {
        myElements.filter { it.isNotEmpty() }.groupingBy { it }.eachCount()
    }
    val enemyElementCountMap = remember(enemyElements) {
        enemyElements.groupingBy { it }.eachCount()
    }

    // 战力对比条比例
    val totalPower = myPower + enemyPower
    val myRatio = if (totalPower > 0) myPower.toFloat() / totalPower else 0.5f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Roundness.md))
            .background(AppTheme.Surface.copy(alpha = 0.5f))
            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(AppTheme.Roundness.md))
            .padding(12.dp),
    ) {
        // VS 标题
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "我方",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Frost,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "⚔ VS ⚔",
                fontWeight = FontWeight.Bold,
                color = AppTheme.Gold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "敌方",
                fontWeight = FontWeight.Bold,
                color = AppTheme.Danger,
            )
        }

        Spacer(Modifier.height(8.dp))

        // 战力对比条
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "$myPower",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = AppTheme.Frost,
            )
            Spacer(Modifier.width(8.dp))
            // 对比条
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(AppTheme.Roundness.xs))
                    .background(AppTheme.BgDeepest),
            ) {
                Row(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxWidth(myRatio)
                            .height(8.dp)
                            .clip(RoundedCornerShape(AppTheme.Roundness.xs))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(AppTheme.Frost.copy(alpha = 0.7f), AppTheme.Frost)
                                )
                            ),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(1f - myRatio)
                            .height(8.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(AppTheme.Danger, AppTheme.Danger.copy(alpha = 0.7f))
                                )
                            ),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$enemyPower",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = AppTheme.Danger,
            )
        }

        Spacer(Modifier.height(8.dp))

        // 元素分布
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 我方元素
            Column {
                Text(
                    text = "元素分布",
                    color = AppTheme.Text3,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    myElementCounts.forEach { (element, count) ->
                        val ei = ElementTheme.forElement(element)
                        ElementBadge(glyph = ei.glyph, color = ei.glow, count = count)
                    }
                }
            }
            // 敌方元素
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "敌方元素",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.Text3,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((element, count) in enemyElementCountMap) {
                        val ei = ElementTheme.forElement(element)
                        ElementBadge(glyph = ei.glyph, color = ei.glow, count = count)
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // 克制提示
        val counterElements = remember(myElements, enemyElements) {
            myElements.filter { it.isNotEmpty() }.distinct().flatMap { myEl ->
                enemyElements.filter { enemyEl ->
                    ElementChart.damageMultiplier(myEl, enemyEl) > 1.05
                }.map { myEl to it }
            }.distinct()
        }
        if (counterElements.isNotEmpty()) {
            val (myEl, _) = counterElements.first()
            Text(
                text = "✦ 你的${ElementTheme.forElement(myEl).glyph}元素对敌方有克制优势",
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.Gold,
            )
        }

        // 层数信息
        Spacer(Modifier.height(4.dp))
        Text(
            text = "第 $floor 层 · 敌方 ×$enemyCount · 属性倍率 ×${"%.1f".format(enemyScale)}",
            color = AppTheme.Text3,
        )
    }
}

/** 元素徽章：小圆 + 元素字 + 数量。 */
@Composable
private fun ElementBadge(glyph: String, color: Color, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(AppTheme.Roundness.xs))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(text = glyph, style = MaterialTheme.typography.labelSmall, color = color)
        if (count > 1) {
            Text(text = "×$count", style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(start = 2.dp))
        }
    }
}

/** 战报单位显示名：我方取内容表短名（names 由 VM 从内容表构建）；程序化敌方 tower_f{floor}_e{i} 显示「敌方N·元素」。 */
private fun unitLabel(
    characterId: String,
    element: String,
    names: Map<String, String>,
): String = when {
    characterId.startsWith("tower_f") ->
        "敌方·${ElementTheme.forElement(element).glyph}"
    else ->
        names[characterId]?.substringBefore(' ') ?: "未知"
}
