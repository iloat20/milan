package com.milan.game.ui.progression

import androidx.compose.material3.MaterialTheme

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.domain.battle.UnitStats
import com.milan.game.domain.progression.TalentEngine
import com.milan.game.services.TalentNodeData
import com.milan.game.OwnedCharacterView
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.components.InkButton
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
internal fun ResourceBar(
    softCurrency: Int,
    starFragments: Int,
) {
    val soft = softCurrency
    val frags = starFragments

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(AppTheme.Roundness.lg))
            .background(AppTheme.Surface)
            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(AppTheme.Roundness.lg))
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
        Text(glyph, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = col)
        Text(
            value,
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
    queries: ProgressionQueries,
    softCurrency: Int,
    onLevel: (Int) -> Unit,
    busy: Boolean = false,
) {
    val save = view.save
    val cap = queries.maxLevelForStage(save.stage)
    val soft = softCurrency
    val (cur, need) = queries.expProgress()

    val canLevel = owned && save.level < cap && soft >= queries.levelCost(save.level)

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lv.${save.level}",
                    style = MaterialTheme.typography.displayMedium.merge(Tabular),
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Gold,
                )
                Text(
                    "/ $cap",
                    color = AppTheme.Text2,
                    style = Tabular,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "$cur / $need EXP",
                    color = AppTheme.Text3,
                    style = Tabular,
                )
            }
            Spacer(Modifier.height(8.dp))

            // 经验条（暗轨 + 金填充，动画过渡）
            val targetFraction = cur.toFloat() / need.toFloat().coerceAtLeast(1f)
            val animatedFraction by animateFloatAsState(
                targetValue = targetFraction,
                animationSpec = tween(600, easing = LinearEasing),
                label = "expFill",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(AppTheme.Roundness.sm))
                    .background(AppTheme.BgDeepest.copy(alpha = 0.24f)),
            ) {
                // 金墨汁填充：从左向右平滑增长
                Box(
                    Modifier
                        .fillMaxWidth(animatedFraction.coerceIn(0f, 1f))
                        .fillMaxSize()
                        .clip(RoundedCornerShape(AppTheme.Roundness.sm))
                        .background(AppTheme.Gold),
                )
                // 填充前沿光效：微弱脉冲辉光
                Box(
                    Modifier
                        .fillMaxWidth(animatedFraction.coerceIn(0f, 1f))
                        .height(12.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        Modifier
                            .width(8.dp)
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, AppTheme.Gold.copy(alpha = 0.6f)),
                                ),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Row {
                LevelButton("升级 ×1", gold = true, enabled = canLevel && !busy) { onLevel(1) }
                LevelButton("升级 ×5", gold = false, enabled = canLevel && !busy) { onLevel(5) }
                LevelButton("升满", gold = false, enabled = canLevel && !busy) { onLevel(Int.MAX_VALUE) }
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
    // 禁用态视觉统一由 GoldButton / NeonButton 内部处理（ThemeButtons 内 alpha 0.45f），
    // 此处不再叠加外层 alpha —— 叠加会变成 0.4 × 0.45 ≈ 0.18，过暗。
    Box(
        Modifier
            .weight(1f)
            .padding(horizontal = 6.dp),
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
            InkButton(
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
    queries: ProgressionQueries,
    softCurrency: Int,
    starFragments: Int,
    onAscend: () -> Unit,
    busy: Boolean = false,
) {
    val save = view.save
    val soft = softCurrency
    val frags = starFragments
    val atMax = save.stage >= defMaxStage
    val aFrag = queries.ascendFragments(save.stage)
    val aSoft = queries.ascendSoft(save.stage)
    val canAscend = owned && !atMax && frags >= aFrag && soft >= aSoft

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (atMax) "突破阶段 ${save.stage} / $defMaxStage（已满）"
                    else "突破阶段 ${save.stage} / $defMaxStage",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Frost,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (atMax) "—" else "❖ ${n0(aFrag)}  +  ✦ ${n0(aSoft)}",
                    color = AppTheme.Text2,
                    style = Tabular,
                )
            }
            Spacer(Modifier.height(10.dp))

            // 禁用态视觉由 GoldButton 内部处理，此处不叠加 alpha（避免双重变暗）。
            Box(Modifier.fillMaxWidth()) {
                GoldButton(
                    text = "突 破",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAscend,
                    textSize = 15.sp,
                    enabled = canAscend && !busy,
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
    queries: ProgressionQueries,
    starFragments: Int,
    onStarUp: () -> Unit,
    busy: Boolean = false,
) {
    val save = view.save
    val frags = starFragments
    val starMax = save.stars >= defMaxStars
    val sFrag = queries.starUpFragments(save.stars)
    val canStar = owned && !starMax && frags >= sFrag

    // C# 注释铁律：不能用 PadLeft 拼星，Stars 为 0 会画出实心星；手动 repeat 并夹下限
    val filled = "★".repeat(save.stars.coerceAtLeast(0))
    val empty = "☆".repeat((defMaxStars - save.stars).coerceAtLeast(0))

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (starMax) "$filled 满星" else "$filled$empty  ${save.stars}/$defMaxStars",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Frost,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (starMax) "—" else "❖ ${n0(sFrag)}",
                    color = AppTheme.Text2,
                    style = Tabular,
                )
            }
            Spacer(Modifier.height(10.dp))

            // 禁用态视觉由 GoldButton 内部处理，此处不叠加 alpha（避免双重变暗）。
            Box(Modifier.fillMaxWidth()) {
                GoldButton(
                    text = "升 星",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStarUp,
                    textSize = 15.sp,
                    enabled = canStar && !busy,
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

/**
 * [StatsPanel] 的四组推导结果（M4：整体记忆化用）。
 * 当前属性 + 「升一级 / 突破 / 升星」三个预览，共 4 次 [ProgressionQueries.computeStats] 全量推导。
 */
private data class StatsBundle(
    val cur: UnitStats,
    val nextLv: UnitStats?,
    val nextStg: UnitStats?,
    val nextStar: UnitStats?,
)

@Composable
internal fun StatsPanel(
    view: OwnedCharacterView,
    defMaxStage: Int,
    defMaxStars: Int,
    queries: ProgressionQueries,
) {
    val save = view.save
    // M4（2026-08-28 审查修复）：4 次全属性推导（当前 + 升一级/突破/升星预览）
    // 原先在每次重组时无条件重算；改为按养成度键记忆化，只在相关字段变化时重算。
    val statsBundle = remember(
        save.characterId, save.level, save.stage, save.stars,
        save.talentPoints.size, defMaxStage, defMaxStars,
    ) {
        val cur = queries.computeStats(view)
        val cap = queries.maxLevelForStage(save.stage)
        StatsBundle(
            cur = cur,
            nextLv = if (save.level < cap) queries.computeStatsAt(view, save.level + 1, save.stage) else null,
            nextStg = if (save.stage < defMaxStage) queries.computeStatsAt(view, save.level, save.stage + 1) else null,
            nextStar = if (save.stars < defMaxStars) {
                queries.computeStatsAt(view, save.level, save.stage, save.stars + 1)
            } else null,
        )
    }
    val cur = statsBundle.cur
    val nextLv = statsBundle.nextLv
    val nextStg = statsBundle.nextStg
    val nextStar = statsBundle.nextStar

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
                    Text(r.cn, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = AppTheme.Text2)
                    Spacer(Modifier.weight(1f))
                    Text(
                        n0(r.value),
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
    view: OwnedCharacterView,
    owned: Boolean,
    queries: ProgressionQueries,
    onTalent: (String) -> Unit,
    busy: Boolean = false,
) {
    val save = view.save
    val characterId = save.characterId
    val tree = queries.talentTree()
    // C# 空树回退三分支；树节点为 null 时对应分支为空列（防养成界面静默空白）。
    // 2026-09-10 R6-P0-2：data.json 各树 BranchIds 历史漏写 branch_ultimate，但 t13 节点
    // 均挂在该分支——只读 tree.branchIds 会导致终极天赋整列不可见。这里取
    // BranchIds ∪ 节点 branchId 并集（保序、去重），内容修好后行为不变。
    val branches = run {
        val declared = tree?.branchIds.orEmpty()
        val fromNodes = tree?.nodes.orEmpty().map { it.branchId }.filter { it.isNotBlank() }
        (declared + fromNodes).distinct()
    }.ifEmpty {
        listOf(
            TalentEngine.BRANCH_POWER,
            TalentEngine.BRANCH_DEFENSE,
            TalentEngine.BRANCH_UTILITY,
        )
    }

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("天赋点", style = MaterialTheme.typography.bodyMedium, color = AppTheme.Text2)
                Text(
                    "× ${save.unspentPoints}",
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
                                && queries.canAllocateTalent(node.nodeId)
                            TalentNode(
                                node = node,
                                col = col,
                                allocated = isAlloc,
                                canAlloc = canAlloc,
                                enabled = canAlloc && !busy,
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
    TalentEngine.BRANCH_ULTIMATE -> AppTheme.Gold
    else -> AppTheme.Text2
}

/** 分支 → 中文名（C# branchName 字典；未知分支显示原始 id）。 */
private fun branchName(br: String): String = when (br) {
    TalentEngine.BRANCH_POWER -> "强攻"
    TalentEngine.BRANCH_DEFENSE -> "坚壁"
    TalentEngine.BRANCH_UTILITY -> "灵动"
    TalentEngine.BRANCH_ULTIMATE -> "终极"
    else -> br
}

/** 天赋节点：已点=分支色描边+✓，可点=淡色描边可点击，锁定=暗框（C# BuildTalentNode）。 */
@Composable
private fun TalentNode(
    node: TalentNodeData,
    col: Color,
    allocated: Boolean,
    canAlloc: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = when {
        allocated -> col.copy(alpha = 70f / 255f)
        canAlloc -> col.copy(alpha = 28f / 255f)
        else -> AppTheme.SurfaceNested
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
            .clip(RoundedCornerShape(AppTheme.Roundness.md))
            .background(bg)
            .border(strokeWidth.dp, stroke, RoundedCornerShape(AppTheme.Roundness.md))
            .clickable(enabled = canAlloc && enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            node.displayName + if (allocated) " ✓" else "",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (allocated || canAlloc) col else AppTheme.Text3,
            textAlign = TextAlign.Center,
        )
        Text(
            "耗费 ${node.cost}",
            color = if (allocated || canAlloc) AppTheme.Text2 else AppTheme.Text3,
            style = Tabular,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
