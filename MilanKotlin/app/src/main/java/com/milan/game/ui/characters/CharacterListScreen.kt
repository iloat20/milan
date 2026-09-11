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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milan.game.services.CharacterDataEntry
import com.milan.game.ui.components.CharacterCard
import com.milan.game.ui.components.ArtifactPanel
import com.milan.game.ui.components.ListFilter
import com.milan.game.ui.components.ListFilterBar
import com.milan.game.ui.components.ListSortMode
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme

/**
 * 角色列表屏（水墨国风版）：
 * 顶部栏 + 筛选条（搜索/稀有度/元素/排序）+ 2 列稀有度描边卡片网格。
 * 筛选状态 rememberSaveable 保留；数据取自 CharacterListViewModel（AppGraph 注入）。
 */
@Composable
fun CharacterListScreen(
    onBack: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    // P1-6 D 批：owned/ownedCount/ownedIds/roster 收敛进 [CharacterListViewModel]
    //（owned 随快照刷新；roster 为内容定义，进程内不变）。
    val vm: CharacterListViewModel = viewModel(factory = com.milan.game.di.AppGraph.factory)
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val owned = ui.owned
    val total = ui.ownedCount
    val roster = vm.roster
    val ownedIds = ui.ownedIds
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

    val gridState = rememberLazyGridState()
    val scrollOffset by remember {
        derivedStateOf { gridState.firstVisibleItemScrollOffset.toFloat() }
    }

    PageBackground(modifier = modifier, scrollOffset = scrollOffset) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(title = "我 的 角 色", onBack = onBack)
            Spacer(Modifier.height(14.dp))

            Text(
                text = if (visible.size == total) "已拥有  $total  位角色"
                else "已显示  ${visible.size} / 已拥有 $total  位角色",
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.Text2,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Spacer(Modifier.height(8.dp))

            CompletionPanel(roster = roster, ownedIds = ownedIds, modifier = Modifier.padding(horizontal = 18.dp))
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    com.milan.game.ui.components.EmptyState(
                        icon = if (owned.isEmpty()) Icons.Outlined.Person else Icons.Outlined.Search,
                        title = if (owned.isEmpty()) "还没有角色" else "没有符合条件的角色",
                        subtitle = if (owned.isEmpty()) "去寻访吧" else "试试调整筛选条件",
                        modifier = Modifier.padding(top = 60.dp),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 13.dp, end = 13.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    itemsIndexed(visible, key = { _, ch -> ch.save.characterId }, contentType = { _, _ -> "characterCard" }) { index, ch ->
                        val parallax by remember {
                            derivedStateOf {
                                val first = gridState.layoutInfo.visibleItemsInfo.firstOrNull()
                                    ?: return@derivedStateOf 0f
                                val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
                                    ?: return@derivedStateOf 0f
                                val total = last.index - first.index + 1
                                val pos = (index - first.index).toFloat() / total.coerceAtLeast(1)
                                pos.coerceIn(-0.5f, 0.5f) * 2f
                            }
                        }
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
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppTheme.Gold,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            },
                            modifier = Modifier
                                .animateItem()
                                .graphicsLayer { translationY = parallax * 8f },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 图鉴完成度头（水墨国风版）：总收集进度条 + 分稀有度 owned/total 徽标
 */
@Composable
private fun CompletionPanel(
    roster: List<CharacterDataEntry>,
    ownedIds: Set<String>,
    modifier: Modifier = Modifier,
) {
    val totalRoster = roster.size
    val ownedCount = roster.count { it.characterId in ownedIds }
    val pct = if (totalRoster == 0) 0 else ownedCount * 100 / totalRoster
    ArtifactPanel(modifier = modifier) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("图鉴收集", color = AppTheme.Text2, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "$ownedCount / $totalRoster · $pct%",
                    color = AppTheme.Gold,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            LinearProgressIndicator(
                progress = { if (totalRoster == 0) 0f else ownedCount / totalRoster.toFloat() },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(AppTheme.Roundness.xxs)),
                color = AppTheme.Gold,
                trackColor = AppTheme.BgMid,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (r in 4 downTo 1) {
                    val rarityTotal = roster.count { it.baseRarity == r }
                    if (rarityTotal > 0) {
                        val rarityOwned = roster.count { it.baseRarity == r && it.characterId in ownedIds }
                        Text(
                            "${AppTheme.rarityName(r)} $rarityOwned/$rarityTotal",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.rarityColor(r),
                        )
                    }
                }
            }
        }
    }
}
