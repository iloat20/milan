package com.milan.game.ui.collection

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milan.game.services.CharacterDataEntry
import com.milan.game.OwnedCharacterView
import com.milan.game.ui.components.CharacterCard
import com.milan.game.ui.components.ListFilter
import com.milan.game.ui.components.ListFilterBar
import com.milan.game.ui.components.ListSortMode
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme

/**
 * 环痕图鉴屏：全量英灵收集总览 —— 顶部收集进度卡（X/total + 四稀有度进度 + 「我的角色」入口），
 * 复用 ListFilterBar 全量筛选，2 列稀有度描边网格；
 * 已拥有：彩色立绘 + 金★星级；未拥有：立绘暗化蒙层 + 「未获得」。
 * 点击任意角色进详情（未拥有详情页已有遮罩，图鉴支持全量浏览）。
 */
@Composable
fun CollectionScreen(
    onBack: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    onOpenMyCharacters: () -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    // P1-6 D 批：all/owned/ownedById 收敛进 [CollectionViewModel]（owned 随快照刷新，
    // 抽卡后图鉴进度随重组更新；all 为内容定义，进程内不变）。
    val vm: CollectionViewModel = viewModel(factory = com.milan.game.di.AppGraph.factory)
    val all = vm.all
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val owned = ui.owned
    val ownedById = ui.ownedById

    var searchText by rememberSaveable { mutableStateOf("") }
    var rarityFilter by rememberSaveable { mutableIntStateOf(-1) }
    var elementFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(ListSortMode.RarityDesc) }

    // 元素 chips 用全量角色的元素（图鉴视角：未拥有的元素也应可筛）
    val elements = remember(all) { all.map { it.element }.distinct() }
    val visible = remember(all, rarityFilter, elementFilter, searchText, sort) {
        ListFilter.filterSort(
            all, rarityFilter, elementFilter, searchText, sort,
            getName = { it.displayName },
            getRarity = { it.baseRarity },
            getElement = { it.element },
            getGroupKey = { it.world },
        )
    }

    PageBackground(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(title = "环 痕 图 鉴", onBack = onBack)
            Spacer(Modifier.height(12.dp))

            CollectionProgressHeader(
                all = all,
                owned = owned,
                onOpenMyCharacters = onOpenMyCharacters,
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

            if (visible.isEmpty()) {
                // 空态：全量页只在筛选无结果时出现
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    com.milan.game.ui.components.EmptyState(
                        icon = Icons.Outlined.Search,
                        title = "没有符合条件的角色",
                        subtitle = "试试调整筛选条件",
                        modifier = Modifier.padding(top = 60.dp),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 13.dp, end = 13.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    itemsIndexed(visible, key = { _, def -> def.characterId }, contentType = { _, _ -> "characterCard" }) { _, def ->
                        val ownedView = ownedById[def.characterId]
                        CharacterCard(
                            characterId = def.characterId,
                            name = def.displayName,
                            title = def.title,
                            rarity = def.baseRarity,
                            element = def.element,
                            onClick = { onOpenCharacter(def.characterId) },
                            locked = ownedView == null,
                            animatedVisibilityScope = animatedVisibilityScope,
                            footer = {
                                Text(
                                    text = if (ownedView != null) "★".repeat(ownedView.save.stars.coerceAtLeast(1)) else "未获得",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (ownedView != null) AppTheme.Gold else AppTheme.Text3,
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

/** 收集进度：左侧环形进度 + 右侧稀有度纬带 + 「我的角色」。 */
@Composable
private fun CollectionProgressHeader(
    all: List<CharacterDataEntry>,
    owned: List<OwnedCharacterView>,
    onOpenMyCharacters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val got = owned.size
    val total = all.size
    val fraction by animateFloatAsState(
        targetValue = if (total == 0) 0f else got.toFloat() / total,
        animationSpec = tween(400),
        label = "collectionProgress",
    )
    val totalByRarity = all.groupingBy { it.baseRarity }.eachCount()
    val gotByRarity = owned.groupingBy { it.rarity }.eachCount()
    val rarityOrder = listOf(4, 3, 2, 1)

    val shape = RoundedCornerShape(AppTheme.Roundness.xl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppTheme.Surface, shape)
            .border(1.dp, AppTheme.Stroke, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 环形进度
        Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2
                val d = size.minDimension - stroke
                val topLeft = Offset(inset, inset)
                val sz = Size(d, d)
                drawArc(
                    color = AppTheme.BgDeepest,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = sz,
                    style = Stroke(stroke),
                )
                drawArc(
                    color = AppTheme.Gold,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = sz,
                    style = Stroke(stroke),
                )
                drawCircle(
                    color = AppTheme.Text3.copy(alpha = 0.12f),
                    radius = d / 2 - stroke,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$got",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Gold,
                )
                Text(
                    text = "/ $total",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.Text3,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "环痕收集",
                style = MaterialTheme.typography.titleSmall,
                color = AppTheme.Text1,
            )
            Text(
                text = "英灵归位进度",
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.Text3,
            )
            Spacer(Modifier.height(10.dp))
            // 稀有度纬带
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (r in rarityOrder) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = AppTheme.rarityName(r),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.rarityColor(r),
                            modifier = Modifier.width(28.dp),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(AppTheme.Roundness.xxs))
                                .background(AppTheme.BgDeepest),
                        ) {
                            val rf = (gotByRarity[r] ?: 0).toFloat() /
                                (totalByRarity[r] ?: 1).coerceAtLeast(1)
                            Box(
                                Modifier
                                    .fillMaxWidth(rf.coerceIn(0f, 1f))
                                    .height(4.dp)
                                    .background(AppTheme.rarityColor(r)),
                            )
                        }
                        Text(
                            text = " ${(gotByRarity[r] ?: 0)}/${totalByRarity[r] ?: 0}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.Text2,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "我的角色  ›",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Frost,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppTheme.Roundness.md))
                    .border(1.dp, AppTheme.Frost.copy(alpha = 0.4f), RoundedCornerShape(AppTheme.Roundness.md))
                    .clickable(onClick = onOpenMyCharacters)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * 图鉴卡片（已收敛至共享组件 CharacterCard，R1/I1）：locked 蒙层与星级/未获得 footer 走参数插槽。
 */
