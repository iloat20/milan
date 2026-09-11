package com.milan.game.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.domain.battle.StrikeEvent
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme

/**
 * 战局重演时间轴（v3 §7.4）——结算页「对弈台」可视化。
 *
 * 分两层：
 * 1. **回合条**：横向回合刻度，柱高 = 该回合总伤害，击杀回合描金/朱砂点。
 * 2. **攻击明细**（展开）：元素 glyph chip + 攻击者→目标 + 伤害 tnum。
 *
 * HUD 纪律（§5.7）：实底 Ink，禁玻璃；文字对比度优先，装饰退后。
 */
@Composable
fun BattleReplayTimeline(
    log: List<StrikeEvent>,
    modifier: Modifier = Modifier,
    names: Map<String, String> = emptyMap(),
    /** 默认收起，避免结算首屏被战报淹没。 */
    defaultExpanded: Boolean = false,
) {
    if (log.isEmpty()) return
    var expanded by remember(log) { mutableStateOf(defaultExpanded) }

    val byTurn = remember(log) {
        log.groupBy { it.turn }.toSortedMap()
    }
    val turnTotals = remember(byTurn) {
        byTurn.mapValues { (_, evts) -> evts.sumOf { it.damage } }
    }
    val maxTurnDamage = turnTotals.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Roundness.md))
            .background(AppTheme.BgDeepest.copy(alpha = 0.85f))
            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(AppTheme.Roundness.md))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "战局重演",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Gold,
                letterSpacing = 0.12.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${byTurn.size} 回合 · ${log.size} 击",
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.Text3,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (expanded) "▾ 收起" else "▸ 明细",
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.Frost,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppTheme.Roundness.sm))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── 回合柱状时间轴 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            byTurn.forEach { (turn, evts) ->
                val total = turnTotals[turn] ?: 0
                val hRatio = total.toFloat() / maxTurnDamage
                val hasKill = evts.any { it.targetDefeated }
                TurnBar(
                    turn = turn,
                    damage = total,
                    heightRatio = hRatio,
                    hasKill = hasKill,
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                byTurn.forEach { (turn, evts) ->
                    Text(
                        text = "T$turn",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.Text3,
                        fontWeight = FontWeight.Bold,
                    )
                    evts.forEach { e ->
                        StrikeTimelineRow(e, names)
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnBar(
    turn: Int,
    damage: Int,
    heightRatio: Float,
    hasKill: Boolean,
) {
    val anim by animateFloatAsState(
        targetValue = heightRatio.coerceIn(0.08f, 1f),
        animationSpec = tween(280),
        label = "turnBar",
    )
    val col = when {
        hasKill -> AppTheme.SealRed
        damage > 0 -> AppTheme.Frost
        else -> AppTheme.Text3
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(28.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height((8 + anim * 36).dp)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(col.copy(alpha = 0.85f))
                .border(
                    0.5.dp,
                    if (hasKill) AppTheme.GoldHi.copy(alpha = 0.7f) else Color.Transparent,
                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                ),
        )
        Text(
            text = "$turn",
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.Text3,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun StrikeTimelineRow(
    e: StrikeEvent,
    names: Map<String, String>,
) {
    val elem = ElementTheme.forElement(e.attackerElement)
    val atk = names[e.attackerId] ?: e.attackerId
    val tgt = names[e.targetId] ?: e.targetId
    val counter = com.milan.game.domain.battle.ElementChart
        .damageMultiplier(e.attackerElement, e.targetElement) > 1.05
    val dmgCol = when {
        e.targetDefeated -> AppTheme.SealRed
        e.damage >= 120 -> AppTheme.GoldHi
        e.damage >= 60 -> AppTheme.Gold
        else -> AppTheme.Text1
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Roundness.xs))
            .background(AppTheme.BgMid.copy(alpha = 0.7f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        // 元素 glyph 印章
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(elem.glow.copy(alpha = 0.2f))
                .border(1.dp, elem.glow.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = elem.glyph,
                style = MaterialTheme.typography.labelSmall,
                color = elem.glow,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = atk,
            style = MaterialTheme.typography.labelMedium,
            color = AppTheme.Text1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(64.dp),
        )
        Text(
            text = if (counter) "破→" else "→",
            style = MaterialTheme.typography.labelSmall,
            color = if (counter) AppTheme.Gold else AppTheme.Text3,
        )
        Text(
            text = tgt,
            style = MaterialTheme.typography.labelMedium,
            color = AppTheme.Text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(64.dp),
        )
        Spacer(Modifier.weight(1f))
        if (e.isElementReaction && e.reactionName.isNotEmpty()) {
            Text(
                text = e.reactionName,
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.Frost,
            )
            Spacer(Modifier.width(4.dp))
        }
        if (e.targetDefeated) {
            Text(
                text = "击杀",
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.SealRed,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = "-${e.damage}",
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = dmgCol,
        )
    }
}
