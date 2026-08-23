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
import com.milan.game.ui.components.CharacterCard
import com.milan.game.ui.components.ListFilter
import com.milan.game.ui.components.ListFilterBar
import com.milan.game.ui.components.ListSortMode
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme

/**
 * 角色列表屏（C# CharacterListActivity 的 Compose 版）：
 * 顶部栏 + 筛选条（搜索/稀有度/元素/排序）+ 2 列稀有度描边卡片网格。
 * 筛选状态 rememberSaveable 保留；数据取自进程级 GameState（Activity 重建后重算）。
 */
@Composable
fun CharacterListScreen(
    onBack: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
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
    val visible = remember(owned, rarityFilter, elementFilter, searchText, sort) {
        ListFilter.filterSort(
            owned, rarityFilter, elementFilter, searchText, sort,
            getName = { it.name },
            getRarity = { it.rarity },
            getElement = { it.element },
            getGroupKey = { it.world },
        )
    }

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
                    itemsIndexed(visible, key = { _, ch -> ch.save.characterId }) { _, ch ->
                        CharacterCard(
                            characterId = ch.save.characterId,
                            name = ch.name,
                            title = ch.title,
                            rarity = ch.rarity,
                            element = ch.element,
                            onClick = { onOpenCharacter(ch.save.characterId) },
                            animatedVisibilityScope = animatedVisibilityScope,
                            footer = {
                                Text(
                                    text = "★".repeat(ch.save.stars.coerceAtLeast(1)),
                                    fontSize = 11.sp,
                                    color = AppTheme.Gold,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 列表卡片（C# CharacterCard.ListCard）：已收敛至共享组件 CharacterCard（R1/I1）。
 * 见 ui/components/CharacterCard.kt——外层稀有度描边、立绘框（共享元素过渡）、
 * 名称/称号/稀有度/元素行统一实现，本页差异（星级）走 footer 插槽。
 */
