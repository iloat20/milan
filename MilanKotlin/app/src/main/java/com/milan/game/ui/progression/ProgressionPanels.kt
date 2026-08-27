package com.milan.game.ui.progression

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milan.game.domain.progression.TalentEngine
import com.milan.game.services.TalentNodeData
import com.milan.game.ui.GameState
import com.milan.game.ui.OwnedCharacterView
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.components.NeonButton
import com.milan.game.ui.components.WoWDivider
import com.milan.game.ui.theme.AppTheme
import java.util.Locale

// ── 养成页五个玻璃面板 + 资源条 ──
// P4-2（2026-08-27）：从 ProgressionScreen.kt 拆出，页面主函数只留编排与写操作回调。

/** 等宽数字样式（C# UI.Tabular）。 */
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** 千分位格式化（C# ToString("N0")）。 */
private fun n0(v: Int): String = String.format(Locale.US, "%,d", v)

// ── 资源条（C# BuildResourceBar）──

@Composable
internal fun ResourceBar() {
    // 快照订阅（范式对齐 AppChrome.ResourceBar）：不再依赖外层 revision「碰巧」触发本组件重组
    val snap by GameState.snapshot.collectAsStateWithLifecycle()
    val soft = snap.softCurrency
    val frags = snap.starFragments

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.Surface)
            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(14.dp))
            .padding(start = 16.dp, end = 16.dp),
    ) {
        Chip("✦", AppTheme.Gold, "星尘 ${n0(soft)}")
        Spacer(Modifier.weight(1f))
        Chip("❖", AppTheme.Frost, "星魂碎片 ${n0(frags)}")
    }
}

@Composable
private fun Chip(glyph: String, col: Color, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(glyph, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = col)
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = col,
            style = Tabular,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

// ── 等级 / 经验（C# BuildLevelPanel）──

@Composable
internal fun LevelPanel(
    view: OwnedCharacterView,
    owned: Boolean,
    onLevel: (Int) -> Unit,
) {
    val save = view.save
    val cap = GameState.service.maxLevelForStage(save.stage)
    val soft = GameState.service.snapshot.value.softCurrency
    val (cur, need) = GameState.service.expProgress(save.characterId)

    val canLevel = owned && save.level < cap && soft >= GameState.service.levelCost(save.level)

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lv.${save.level}",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Gold,
                    style = Tabular,
                )
                Text(
                    "/ $cap",
                    fontSize = 14.sp,
                    color = AppTheme.Text2,
                    style = Tabular,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "$cur / $need EXP",
                    fontSize = 12.sp,
                    color = AppTheme.Text3,
                    style = Tabular,
                )
            }
            Spacer(Modifier.height(8.dp))

            // 经验条（暗轨 + 金填充，权重控制比例）
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x3C000000)),
            ) {
                // 满级时 cur==need，填充占满整条（C# weight 用 max(0.001, …) 防除零）
                Box(
                    Modifier
                        .fillMaxSize()
                        .weight(cur.toFloat().coerceAtLeast(0.001f))
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppTheme.Gold),
                )
                Box(Modifier.weight((need - cur).toFloat().coerceAtLeast(0.001f)))
            }
            Spacer(Modifier.height(12.dp))

            Row {
                LevelButton("升级 ×1", gold = true, enabled = canLevel) { onLevel(1) }
                LevelButton("升级 ×5", gold = false, enabled = canLevel) { onLevel(5) }
                LevelButton("升满", gold = false, enabled = canLevel) { onLevel(Int.MAX_VALUE) }
            }
        }
    }
}

/** 等宽升级按钮：禁用时半透明（C# 按钮 Enabled + Alpha 0.4 的 Compose 等价）。 */
@Composable
private fun RowScope.LevelButton(
    text: String,
    gold: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .padding(horizontal = 6.dp)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        if (gold) {
            GoldButton(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                onClick = onClick,
                textSize = 15.sp,
                enabled = enabled,
            )
        } else {
            NeonButton(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                onClick = onClick,
                textSize = 15.sp,
                enabled = enabled,
            )
        }
    }
}

// ── 突破（C# BuildAscendPanel）──

@Composable
internal fun AscendPanel(
    view: OwnedCharacterView,
    defMaxStage: Int,
    owned: Boolean,
    onAscend: () -> Unit,
) {
    val save = view.save
    val soft = GameState.service.snapshot.value.softCurrency
    val frags = GameState.service.getStarFragments()
    val atMax = save.stage >= defMaxStage
    val aFrag = GameState.service.ascendFragments(save.stage)
    val aSoft = GameState.service.ascendSoft(save.stage)
    val canAscend = owned && !atMax && frags >= aFrag && soft >= aSoft

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (atMax) "突破阶段 ${save.stage} / $defMaxStage（已满）"
                    else "突破阶段 ${save.stage} / $defMaxStage",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Frost,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (atMax) "—" else "❖ ${n0(aFrag)}  +  ✦ ${n0(aSoft)}",
                    fontSize = 13.sp,
                    color = AppTheme.Text2,
                    style = Tabular,
                )
            }
            Spacer(Modifier.height(10.dp))

            Box(Modifier.fillMaxWidth().alpha(if (canAscend) 1f else 0.4f)) {
                GoldButton(
                    text = "突 破",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAscend,
                    textSize = 15.sp,
                    enabled = canAscend,
                )
            }
        }
    }
}

// ── 升星（C# BuildStarPanel）──

@Composable
internal fun StarPanel(
    view: OwnedCharacterView,
    defMaxStars: Int,
    owned: Boolean,
    onStarUp: () -> Unit,
) {
    val save = view.save
    val frags = GameState.service.getStarFragments()
    val starMax = save.stars >= defMaxStars
    val sFrag = GameState.service.starUpFragments(save.stars)
    val canStar = owned && !starMax && frags >= sFrag

    // C# 注释铁律：不能用 PadLeft 拼星，Stars 为 0 会画出实心星；手动 repeat 并夹下限
    val filled = "★".repeat(save.stars.coerceAtLeast(0))
    val empty = "☆".repeat((defMaxStars - save.stars).coerceAtLeast(0))

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (starMax) "$filled 满星" else "$filled$empty  ${save.stars}/$defMaxStars",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Frost,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (starMax) "—" else "❖ ${n0(sFrag)}",
                    fontSize = 13.sp,
                    color = AppTheme.Text2,
                    style = Tabular,
                )
            }
            Spacer(Modifier.height(10.dp))

            Box(Modifier.fillMaxWidth().alpha(if (canStar) 1f else 0.4f)) {
                GoldButton(
                    text = "升 星",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStarUp,
                    textSize = 15.sp,
                    enabled = canStar,
                )
            }
        }
    }
}

// ── 属性（C# FillStats：当前值 + 下一级 / 突破 / 升星预测）──

private data class StatRow(
    val cn: String,
    val value: Int,
    val nl: Int?,
    val ns: Int?,
    val nstar: Int?,
)

@Composable
internal fun StatsPanel(
    view: OwnedCharacterView,
    defMaxStage: Int,
    defMaxStars: Int,
) {
    val save = view.save
    val cur = GameState.computeStats(view)
    val cap = GameState.service.maxLevelForStage(save.stage)
    val nextLv = if (save.level < cap) GameState.computeStatsAt(view, save.level + 1, save.stage) else null
    val nextStg = if (save.stage < defMaxStage) GameState.computeStatsAt(view, save.level, save.stage + 1) else null
    val nextStar = if (save.stars < defMaxStars) {
        GameState.computeStatsAt(view, save.level, save.stage, save.stars + 1)
    } else null

    val rows = listOf(
        StatRow("攻击", cur.atk, nextLv?.atk, nextStg?.atk, nextStar?.atk),
        StatRow("防御", cur.def, nextLv?.def, nextStg?.def, nextStar?.def),
        StatRow("生命", cur.hp, nextLv?.hp, nextStg?.hp, nextStar?.hp),
        StatRow("速度", cur.spd, nextLv?.spd, nextStg?.spd, nextStar?.spd),
    )

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            rows.forEach { r ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 7.dp, bottom = 7.dp),
                ) {
                    Text(r.cn, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppTheme.Text2)
                    Spacer(Modifier.weight(1f))
                    Text(
                        n0(r.value),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.Text1,
                        style = Tabular,
                    )
                }

                // 预测子行：有哪项预测显示哪项（C# StringBuilder 拼接，青字）
                val parts = buildList {
                    r.nl?.let { add("Lv+1 → ${n0(it)}") }
                    r.ns?.let { add("突破 → ${n0(it)}") }
                    r.nstar?.let { add("升星 → ${n0(it)}") }
                }
                if (parts.isNotEmpty()) {
                    Text(
                        parts.joinToString("   ·   "),
                        fontSize = 12.sp,
                        color = AppTheme.Frost,
                        style = Tabular,
                    )
                }

                WoWDivider()
            }
        }
    }
}

// ── 天赋（C# BuildTalentPanel + FillTalent + BuildTalentNode）──

@Composable
internal fun TalentPanel(
    characterId: String,
    view: OwnedCharacterView,
    owned: Boolean,
    onTalent: (String) -> Unit,
) {
    val save = view.save
    val tree = GameState.service.getTalentTree(characterId)
    // C# 空树回退三分支；树节点为 null 时对应分支为空列（防养成界面静默空白）
    val branches = tree?.branchIds
        ?: listOf(TalentEngine.BRANCH_POWER, TalentEngine.BRANCH_DEFENSE, TalentEngine.BRANCH_UTILITY)

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("天赋点", fontSize = 13.sp, color = AppTheme.Text2)
                Text(
                    "× ${save.unspentPoints}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Gold,
                    style = Tabular,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(10.dp))

            Row {
                branches.forEach { br ->
                    val col = branchColor(br)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                    ) {
                        Text(
                            branchName(br),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = col,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))

                        val nodes = tree?.nodes?.filter { it.branchId == br }.orEmpty()
                        nodes.forEach { node ->
                            val isAlloc = save.talentPoints.contains(node.nodeId)
                            val canAlloc = !isAlloc && owned
                                && save.unspentPoints >= node.cost
                                && GameState.service.canAllocateTalent(characterId, node.nodeId)
                            TalentNode(
                                node = node,
                                col = col,
                                allocated = isAlloc,
                                canAlloc = canAlloc,
                                onClick = { onTalent(node.nodeId) },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 分支 → 颜色（C# branchColor 字典；未知分支回退次文字色）。 */
private fun branchColor(br: String): Color = when (br) {
    TalentEngine.BRANCH_POWER -> AppTheme.Danger
    TalentEngine.BRANCH_DEFENSE -> AppTheme.Frost
    TalentEngine.BRANCH_UTILITY -> AppTheme.Violet
    else -> AppTheme.Text2
}

/** 分支 → 中文名（C# branchName 字典；未知分支显示原始 id）。 */
private fun branchName(br: String): String = when (br) {
    TalentEngine.BRANCH_POWER -> "强攻"
    TalentEngine.BRANCH_DEFENSE -> "坚壁"
    TalentEngine.BRANCH_UTILITY -> "灵动"
    else -> br
}

/** 天赋节点：已点=分支色描边+✓，可点=淡色描边可点击，锁定=暗框（C# BuildTalentNode）。 */
@Composable
private fun TalentNode(
    node: TalentNodeData,
    col: Color,
    allocated: Boolean,
    canAlloc: Boolean,
    onClick: () -> Unit,
) {
    val bg = when {
        allocated -> col.copy(alpha = 70f / 255f)
        canAlloc -> col.copy(alpha = 28f / 255f)
        else -> Color(0x1E14101E)
    }
    val stroke = when {
        allocated -> col
        canAlloc -> col
        else -> AppTheme.Stroke
    }
    val strokeWidth = if (allocated || canAlloc) 2 else 1

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(strokeWidth.dp, stroke, RoundedCornerShape(12.dp))
            .clickable(enabled = canAlloc, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            node.displayName + if (allocated) " ✓" else "",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (allocated || canAlloc) col else AppTheme.Text3,
            textAlign = TextAlign.Center,
        )
        Text(
            "耗费 ${node.cost}",
            fontSize = 11.sp,
            color = if (allocated || canAlloc) AppTheme.Text2 else AppTheme.Text3,
            style = Tabular,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
