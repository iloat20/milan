package com.milan.game.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.milan.game.ui.GameState
import com.milan.game.ui.OwnedCharacterView
import com.milan.game.ui.components.NeonButton
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.components.PortraitTarget
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.nav.GameNavBar
import com.milan.game.ui.nav.NavItem
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme

/**
 * 卡组屏（P1-3 新建，替换原「建设中」占位页）：
 * 已拥有角色 2 列网格，点卡片弹出立绘大图预览（对标 Forge 悬浮大卡预览），
 * 预览层提供「查看详情」入口；空态引导前往寻访。底部导航常驻（主 tab 页）。
 */
@Composable
fun DeckScreen(
    onNav: (NavItem) -> Unit,
    onOpenCharacter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val owned = remember { GameState.owned() }
    var previewId by remember { mutableStateOf<String?>(null) }
    val preview = owned.firstOrNull { it.save.characterId == previewId }

    PageBackground(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(title = "卡 组", onBack = { onNav(NavItem.Home) })
            Spacer(Modifier.height(14.dp))
            Text(
                text = "已拥有  ${owned.size}  位角色",
                fontSize = 14.sp,
                color = AppTheme.Text2,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Spacer(Modifier.height(12.dp))

            if (owned.isEmpty()) {
                // 空态：无角色时引导前往寻访（对标 CharacterListScreen 空态语义）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "✦",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.Gold.copy(alpha = 0.6f),
                        )
                        Text(
                            text = "还没有角色，去寻访吧",
                            fontSize = 14.sp,
                            color = AppTheme.Text3,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        NeonButton(
                            text = "前往寻访",
                            onClick = { onNav(NavItem.Gacha) },
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 13.dp, end = 13.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    // P3-3：Lazy 容器补稳定 key（角色 Id），避免槽位复用导致筛选/状态错乱
                    itemsIndexed(owned, key = { _, ch -> ch.save.characterId }) { _, ch ->
                        DeckCard(ch) { previewId = ch.save.characterId }
                    }
                }
            }

            GameNavBar(
                active = NavItem.Deck,
                onSelect = onNav,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        // 立绘大图预览层（P1-3）：全屏遮罩 + 稀有度光晕，点空白关闭；「查看详情」进详情页
        if (preview != null) {
            val rc = AppTheme.rarityColor(preview.rarity)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.80f))
                    .clickable { previewId = null },
                contentAlignment = Alignment.Center,
            ) {
                // 稀有度光晕（径向渐变全屏，与 Gacha reveal 同语言）
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.radialGradient(listOf(rc.copy(alpha = 0.45f), Color.Transparent))),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 大立绘卡 220x312（与 Gacha reveal 卡片同尺寸语言）
                    Column(
                        modifier = Modifier
                            .size(width = 220.dp, height = 312.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(rc.copy(alpha = 0.34f), AppTheme.BgDeepest, AppTheme.BgDeepest),
                                ),
                            )
                            .border(2.dp, rc.copy(alpha = 0.85f), RoundedCornerShape(18.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PortraitImage(
                            characterId = preview.save.characterId,
                            rarity = preview.rarity,
                            name = preview.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            aura = true,
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(rc.copy(alpha = 0.16f))
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = preview.name,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.Text1,
                            )
                        }
                        Text(
                            text = AppTheme.rarityName(preview.rarity),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = rc,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    // 查看详情：先关预览层再进详情页（预览层与详情页不同屏，无 sharedBounds 配对）
                    NeonButton(
                        text = "查看详情",
                        onClick = {
                            val id = preview.save.characterId
                            previewId = null
                            onOpenCharacter(id)
                        },
                        modifier = Modifier.padding(top = 20.dp),
                    )
                    Text(
                        text = "点击空白处关闭",
                        fontSize = 12.sp,
                        color = AppTheme.Text3,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

/**
 * 卡组卡片（CharacterListScreen.ListCard 精简版）：
 * 外层稀有度描边圆角，内层 Surface 面板，立绘 + 名称 + 稀有度/元素。
 */
@Composable
private fun DeckCard(ch: OwnedCharacterView, onClick: () -> Unit) {
    val rarityCol = AppTheme.rarityColor(ch.rarity)
    val elem = ElementTheme.forElement(ch.element)
    Column(
        modifier = Modifier
            .padding(5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(rarityCol.copy(alpha = 110f / 255f), RoundedCornerShape(18.dp))
            .border(2.dp, rarityCol, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AppTheme.Surface, RoundedCornerShape(16.dp))
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(elem.from.copy(alpha = 0.22f), RoundedCornerShape(12.dp)),
            ) {
                PortraitImage(
                    characterId = ch.save.characterId,
                    rarity = ch.rarity,
                    name = ch.name,
                    modifier = Modifier.fillMaxSize(),
                    target = PortraitTarget.Thumb,
                    aura = true,
                    glowScale = 0.7f,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = ch.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = ch.title,
                fontSize = 11.sp,
                color = AppTheme.Text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 2.dp)) {
                Text(
                    text = AppTheme.rarityName(ch.rarity),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = rarityCol,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(text = elem.glyph + " " + ch.element, fontSize = 11.sp, color = elem.from)
            }
        }
    }
}
