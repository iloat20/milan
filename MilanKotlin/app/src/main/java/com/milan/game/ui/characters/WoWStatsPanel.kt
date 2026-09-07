package com.milan.game.ui.characters

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.milan.game.domain.battle.UnitStats
import com.milan.game.ui.OwnedCharacterView
import com.milan.game.ui.components.WoWDivider
import com.milan.game.ui.theme.AppTheme

// ── 属性面板（C# BuildStatsPanel：魔兽世界风格）──
// P4-1（2026-08-27）：从 CharacterDetailScreen.kt 拆出，WoW 面板族独立成文件。

/** 属性面板页脚用的等宽数字样式（C# UI.Tabular）。 */
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** 次级属性（已下沉至 [StatsCalculator.deriveSecondary]，保留类型别名供面板引用）。 */
private typealias SecondaryStats = com.milan.game.domain.progression.SecondaryStats

@Composable
internal fun StatsPanel(
    view: OwnedCharacterView,
    owned: Boolean,
    stats: UnitStats,
    baseStats: UnitStats,
) {
    // 魔兽世界风格角色面板：暗色渐变底 + 金色双描边 + 四角菱形饰钉（C# WoWStatsFrame）
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(AppTheme.WoWPanelTop, AppTheme.WoWPanelBottom)))
            .border(2.dp, AppTheme.Gold, RoundedCornerShape(14.dp))
            .padding(5.dp)
            .border(1.dp, AppTheme.Gold.copy(alpha = 130f / 255f), RoundedCornerShape(12.dp)),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)) {
            // ── 主属性（对应魔兽 力量/敏捷/智力/耐力）──
            WoWSectionHeader("主 属 性")
            Spacer(Modifier.height(4.dp))

            val primaries = listOf(
                Primary("攻", AppTheme.Danger, "攻击", "Attack", stats.atk, stats.atk - baseStats.atk),
                Primary("防", AppTheme.Frost, "防御", "Defense", stats.def, stats.def - baseStats.def),
                Primary("命", AppTheme.Success, "生命", "Health", stats.hp, stats.hp - baseStats.hp),
                Primary("速", AppTheme.Gold, "速度", "Speed", stats.spd, stats.spd - baseStats.spd),
            )
            primaries.forEachIndexed { i, p ->
                WoWStatRow(p.glyph, p.col, p.cn, p.en, p.value.toString(), p.bonus, index = i)
                if (i < primaries.size - 1) WoWDivider()
            }

            // ── 次级属性（派生战斗属性，对应魔兽 暴击/急速/护甲/格挡）──
            Spacer(Modifier.height(6.dp))
            WoWGroupDivider()
            Spacer(Modifier.height(6.dp))
            WoWSectionHeader("次 级 属 性")
            Spacer(Modifier.height(4.dp))

            val secCur = com.milan.game.domain.progression.StatsCalculator.deriveSecondary(stats)
            val secBase = com.milan.game.domain.progression.StatsCalculator.deriveSecondary(baseStats)
            val secondaries = listOf(
                Secondary("暴", AppTheme.Warning, "暴击", "Critical", "${secCur.crit}%", secCur.crit - secBase.crit),
                Secondary("急", AppTheme.Violet, "急速", "Haste", "${secCur.haste}%", secCur.haste - secBase.haste),
                Secondary("甲", AppTheme.FrostDeep, "护甲", "Armor", secCur.armor.toString(), secCur.armor - secBase.armor),
                Secondary("挡", AppTheme.GoldDeep, "格挡", "Block", "${secCur.block}%", secCur.block - secBase.block),
            )
            secondaries.forEachIndexed { i, s ->
                WoWStatRow(s.glyph, s.col, s.cn, s.en, s.value, s.bonus, index = i + primaries.size)
                if (i < secondaries.size - 1) WoWDivider()
            }

            // 页脚：等级 / 星级 / 天赋点
            WoWDivider()
            Spacer(Modifier.height(4.dp))
            Text(
                "等级 Lv.${view.save.level}   ·   星级 ${"★".repeat(view.save.stars.coerceAtLeast(1))}" +
                    "   ·   天赋点 ${view.save.unspentPoints}" + if (owned) "" else "   ·   未拥有",
                fontSize = 12.sp,
                color = AppTheme.Text2,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        // 四角菱形饰钉（C# WoWStatsFrame.DrawDiamond，GoldHi）
        Canvas(Modifier.matchParentSize()) {
            val d = 4.5.dp.toPx()
            val inset = 5.dp.toPx() + 2.dp.toPx()
            fun diamond(cx: Float, cy: Float) {
                val p = Path().apply {
                    moveTo(cx, cy - d); lineTo(cx + d, cy); lineTo(cx, cy + d); lineTo(cx - d, cy); close()
                }
                drawPath(p, AppTheme.GoldHi)
            }
            diamond(inset, inset)
            diamond(size.width - inset, inset)
            diamond(inset, size.height - inset)
            diamond(size.width - inset, size.height - inset)
        }
    }
}

private data class Primary(val glyph: String, val col: Color, val cn: String, val en: String, val value: Int, val bonus: Int)
private data class Secondary(val glyph: String, val col: Color, val cn: String, val en: String, val value: String, val bonus: Int)

/** 魔兽风格属性行：圆形角色徽章 + 中英名称 + 等宽数值 + 绿色加成/红色降低 + 入场填充条。 */
@Composable
private fun WoWStatRow(
    glyph: String,
    col: Color,
    cn: String,
    en: String,
    valueText: String,
    bonus: Int,
    index: Int = 0,
) {
    // 入场填充动画：每行延迟 80ms，从 0 展开到 1
    var fillReady by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 80L)
        fillReady = 1f
    }
    val fillFraction by animateFloatAsState(
        targetValue = fillReady,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "statFill$index",
    )

    Column(Modifier.padding(vertical = 9.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatBadge(glyph, col)
            Column(Modifier.padding(start = 12.dp)) {
                Text(cn, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppTheme.Text1)
                Text(en, fontSize = 10.sp, color = AppTheme.Text3, letterSpacing = 0.08.em)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    valueText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Text1,
                    style = Tabular,
                )
                if (bonus != 0) {
                    val isPositive = bonus > 0
                    val indicatorColor = if (isPositive) AppTheme.WoWGreen else AppTheme.Danger
                    val arrow = if (isPositive) "↑" else "↓"
                    Text(
                        " $arrow${kotlin.math.abs(bonus)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = indicatorColor,
                        style = Tabular,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        // 入场填充条：角色色渐隐条，宽度随动画展开
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 46.dp, top = 2.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(AppTheme.Surface)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction = fillFraction)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(col.copy(alpha = 0.7f), col.copy(alpha = 0.2f))
                        )
                    )
            )
        }
    }
}

/** 圆形角色徽章：暗底 + 角色色描边 + 中文单字字形（C# StatBadge）。 */
@Composable
private fun StatBadge(glyph: String, col: Color) {
    Text(
        glyph,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = col,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(col.copy(alpha = 45f / 255f))
            .border(1.5.dp, col.copy(alpha = 195f / 255f), CircleShape),
    )
}

/** 居中分组标题：两侧金色渐隐线 + ◆ + 标题（C# WoWSectionHeader）。 */
@Composable
private fun WoWSectionHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LineGold(Modifier.weight(1f))
        Text(
            "◆",
            fontSize = 10.sp,
            color = AppTheme.Gold,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.Gold,
            letterSpacing = 0.2.em,
        )
        Text(
            "◆",
            fontSize = 10.sp,
            color = AppTheme.Gold,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        LineGold(Modifier.weight(1f))
    }
}

/** 金色渐隐发丝线（C# LineGold：透明→α130→透明）。 */
@Composable
private fun LineGold(modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        AppTheme.Gold.copy(alpha = 0f),
                        AppTheme.Gold.copy(alpha = 130f / 255f),
                        AppTheme.Gold.copy(alpha = 0f),
                    ),
                ),
            ),
    )
}

/** 组间分隔：线 + 中心 ◆ + 线（C# WoWGroupDivider）。 */
@Composable
private fun WoWGroupDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LineGold(Modifier.weight(1f))
        Text(
            "◆",
            fontSize = 11.sp,
            color = AppTheme.Gold,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        LineGold(Modifier.weight(1f))
    }
}
