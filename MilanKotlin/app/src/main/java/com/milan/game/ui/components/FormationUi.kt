package com.milan.game.ui.components

import com.milan.game.OwnedCharacterView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.domain.battle.TeamResonance
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme

/**
 * 编队共鸣文案（2026-08 编队系统）：数值一律取自 [TeamResonance] 常量（单一事实来源），
 * DeckScreen / TowerScreen 共用。无共鸣返回 null（调用方不渲染该行）。
 */
fun formationResonanceLabel(elements: List<String>): String? {
    if (elements.size < 2) return null
    val first = elements.first()
    if (first.isNotEmpty() && elements.all { it == first }) {
        return "同调共鸣 · 全员攻击 +${(TeamResonance.UNISON_ATK_BONUS * 100).toInt()}% " +
            "/ 速度 +${(TeamResonance.UNISON_SPD_BONUS * 100).toInt()}%"
    }
    val pairs = elements.filter { it.isNotEmpty() }
        .groupingBy { it }.eachCount()
        .count { it.value >= 2 }
    return if (pairs > 0) "双星共鸣 ×$pairs · 攻击 +${(TeamResonance.PAIR_ATK_BONUS * 100).toInt()}%" else null
}

/**
 * 出战编队槽位条（2026-08 编队系统，DeckScreen / TowerScreen 共用组件）：
 * [maxSlots] 个等宽槽位；已入队槽位显示缩略立绘 + 元素字 + 角色名，空槽显示「＋」。
 * 点击任意槽位回调其 characterId（空槽回调 null），入队/退队语义由调用方决定。
 */
@Composable
fun FormationBar(
    members: List<OwnedCharacterView>,
    maxSlots: Int,
    onSlotClick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "出战编队",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Text1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${members.size}/$maxSlots",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.Text2,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(maxSlots) { i ->
                val ch = members.getOrNull(i)
                val rarityCol = ch?.let { AppTheme.rarityColor(it.rarity) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.74f)
                        .clip(RoundedCornerShape(AppTheme.Roundness.md))
                        .background(AppTheme.Surface.copy(alpha = 0.55f))
                        .border(
                            1.dp,
                            rarityCol?.copy(alpha = 0.85f) ?: AppTheme.Stroke,
                            RoundedCornerShape(AppTheme.Roundness.md),
                        )
                        .clickable { onSlotClick(ch?.save?.characterId) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (ch == null) {
                        Text(text = "＋", fontSize = 18.sp, color = AppTheme.Text3)
                    } else {
                        // 迷你卡面（与 CharacterCard 同语言）：元素渐变底 + 立绘铺满 + 底部铭牌
                        val ei = ElementTheme.forElement(ch.element)
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(ei.from.copy(alpha = 0.36f), AppTheme.Surface),
                                    ),
                                ),
                        )
                        PortraitImage(
                            characterId = ch.save.characterId,
                            rarity = ch.rarity,
                            name = ch.name,
                            modifier = Modifier.fillMaxSize(),
                            target = PortraitTarget.Avatar,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                        // 底部铭牌：元素字 + 名字
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 4.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = ei.glyph, fontSize = 9.sp, color = ei.glow)
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = ch.name,
                                fontSize = 9.sp,
                                color = AppTheme.Text1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        formationResonanceLabel(members.map { it.element })?.let { label ->
            Text(
                text = "✦ $label",
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.Gold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
