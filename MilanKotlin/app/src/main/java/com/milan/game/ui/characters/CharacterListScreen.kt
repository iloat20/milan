package com.milan.game.ui.characters

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.GameState
import com.milan.game.ui.OwnedCharacterView
import com.milan.game.ui.components.ListFilter
import com.milan.game.ui.components.ListFilterBar
import com.milan.game.ui.components.ListSortMode
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.components.PortraitTarget
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme

/** 稀有度标签（C# CharacterCard.RarityName：4=UR 3=SSR 2=SR 其余=R）。 */
private fun rarityName(r: Int): String = when (r) {
    4 -> "UR"
    3 -> "SSR"
    2 -> "SR"
    else -> "R"
}

/**
 * 角色列表屏（C# CharacterListActivity 的 Compose 版）：
 * 顶部栏 + 筛选条（搜索/稀有度/元素/排序）+ 2 列稀有度描边卡片网格。
 * 筛选状态 rememberSaveable 保留；数据取自进程级 GameState（Activity 重建后重算）。
 */
@Composable
fun CharacterListScreen(
    onBack: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 数据快照：单例存档 + 内容定义合并（C# OnCreate 时取一次，重建后重算）。
    val owned = remember { GameState.owned() }
    val total = remember { GameState.ownedCount }
    // 筛选状态（C# 里挂在 ListFilterBar 实例上，旋转/重建时由 Compose 保留）。
    var searchText by rememberSaveable { mutableStateOf("") }
    var rarityFilter by rememberSaveable { mutableIntStateOf(-1) }
    var elementFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(ListSortMode.RarityDesc) }

    val elements = remember(owned) { owned.map { it.element }.distinct() }
    val visible = ListFilter.filterSort(
        owned, rarityFilter, elementFilter, searchText, sort,
        getName = { it.name },
        getRarity = { it.rarity },
        getElement = { it.element },
        getGroupKey = { it.world },
    )

    PageBackground(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(title = "我 的 角 色", onBack = onBack)
            Spacer(Modifier.height(14.dp))

            // 数量（筛选后实时更新，C# _countLabel）
            Text(
                text = if (visible.size == total) "已拥有  $total  位角色"
                else "已显示  ${visible.size} / 已拥有 $total  位角色",
                fontSize = 14.sp,
                color = AppTheme.Text2,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Spacer(Modifier.height(12.dp))

            ListFilterBar(
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                rarityFilter = rarityFilter,
                onRarityFilterChange = { rarityFilter = it },
                elementFilter = elementFilter,
                onElementFilterChange = { elementFilter = it },
                sort = sort,
                onSortChange = { sort = it },
                elements = elements,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Spacer(Modifier.height(8.dp))

            if (owned.isEmpty() || visible.isEmpty()) {
                // 空态（C#：无任何角色或筛选无结果时同文案）
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Text(
                        text = "没有符合条件的角色",
                        fontSize = 14.sp,
                        color = AppTheme.Text3,
                        modifier = Modifier.padding(top = 80.dp),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 13.dp, end = 13.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    itemsIndexed(visible) { _, ch ->
                        ListCard(ch) { onOpenCharacter(ch.save.characterId) }
                    }
                }
            }
        }
    }
}

/**
 * 列表卡片（C# CharacterCard.ListCard）：
 * 外层稀有度描边圆角（α110 填充 + 18 圆角 + 2dp 描边），内层 Surface 圆角面板，
 * 立绘 + 名称 + 职阶 + 稀有度/元素行 + 星级。
 */
@Composable
private fun ListCard(ch: OwnedCharacterView, onClick: () -> Unit) {
    val rarityCol = AppTheme.rarityColor(ch.rarity)
    val elem = ElementTheme.forElement(ch.element)
    Column(
        modifier = Modifier
            .padding(5.dp) // C# margin 5dp
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
            // 立绘框：元素淡色底 + 头像（缺图时回退稀有度渐变 + 首字）
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
                    text = rarityName(ch.rarity),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = rarityCol,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(text = elem.glyph + " " + ch.element, fontSize = 11.sp, color = elem.from)
            }
            Text(
                text = "★".repeat(ch.save.stars.coerceAtLeast(1)),
                fontSize = 11.sp,
                color = AppTheme.Gold,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
