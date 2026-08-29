package com.milan.game.ui.collection

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milan.game.services.CharacterDataEntry
import com.milan.game.ui.GameState
import com.milan.game.ui.OwnedCharacterView
import com.milan.game.ui.components.CharacterCard
import com.milan.game.ui.components.ListFilter
import com.milan.game.ui.components.ListFilterBar
import com.milan.game.ui.components.ListSortMode
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme

/**
 * 神谱图鉴屏（替换原 PlaceholderScreen 占位）：
 * 全量角色收集总览 —— 顶部收集进度卡（X/52 + 四稀有度进度 + 「我的角色」入口），
 * 复用 ListFilterBar 全量筛选，2 列稀有度描边网格；
 * 已拥有：彩色立绘 + 金★星级；未拥有：立绘暗化蒙层 + 🔒 + 「未获得」。
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
    // 快照 revision 驱动拥有视图（范式对齐 Detail/Progression 页）：抽卡后图鉴进度随重组刷新。
    // all 为内容定义（进程内不变），无需 revision key；owned 随快照刷新，ownedById 派生自 owned。
    val snap by GameState.snapshot.collectAsStateWithLifecycle()
    val all = remember { GameState.service.characters }
    val owned = remember(snap.revision) { GameState.owned() }
    val ownedById = remember(owned) { owned.associateBy { it.save.characterId } }

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
            AppTopBar(title = "神 谱 图 鉴", onBack = onBack)
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
                    Text(
                        text = "没有符合条件的角色",
                        style = MaterialTheme.typography.bodyLarge,
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

/** 收集进度卡：总数 X/Y + 金色进度条 + 四稀有度统计 + 「我的角色」入口。 */
@Composable
private fun CollectionProgressHeader(
    all: List<CharacterDataEntry>,
    owned: List<OwnedCharacterView>,
    onOpenMyCharacters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val got = owned.size
    val total = all.size
    // 数值动画：进度条随收集推进平滑生长（对齐全站数值反馈语言，如 ResourceBar Chip 的 400ms 滚动）
    val fraction by animateFloatAsState(
        targetValue = if (total == 0) 0f else got.toFloat() / total,
        animationSpec = tween(400),
        label = "collectionProgress",
    )
    // 稀有度统计动态分组（防写死数字被内容数据打脸）
    val totalByRarity = all.groupingBy { it.baseRarity }.eachCount()
    val gotByRarity = owned.groupingBy { it.rarity }.eachCount()
    val rarityOrder = listOf(4, 3, 2, 1)

    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(AppTheme.Surface, shape)
            .border(1.dp, AppTheme.Stroke, shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "神谱收集",
                    style = MaterialTheme.typography.titleSmall,
                    color = AppTheme.Text1,
                )
                Text(
                    text = "$got  /  $total",
                    style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 0.sp),
                    color = AppTheme.Gold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // 我的角色入口（承接原占位页 actionLabel 按钮，链路不中断）
            Text(
                text = "我的角色  ›",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Gold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenMyCharacters)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        // 进度条（自绘圆角条：金 → 金半透明渐变）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppTheme.BgDeepest, RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(AppTheme.Gold, AppTheme.Gold.copy(alpha = 0.45f)),
                        ),
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (r in rarityOrder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = AppTheme.rarityName(r),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.rarityColor(r),
                    )
                    Text(
                        text = "  ${gotByRarity[r] ?: 0} / ${totalByRarity[r] ?: 0}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppTheme.Text2,
                    )
                }
            }
        }
    }
}

/**
 * 图鉴卡片（已收敛至共享组件 CharacterCard，R1/I1）：locked 蒙层与星级/未获得 footer 走参数插槽。
 */
