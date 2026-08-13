package com.milan.game.ui.collection

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.services.CharacterDataEntry
import com.milan.game.ui.GameState
import com.milan.game.ui.LocalSharedTransitionScope
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

/** 稀有度标签（与 CharacterListScreen 同语义：4=UR 3=SSR 2=SR 其余=R）。 */
private fun rarityName(r: Int): String = when (r) {
    4 -> "UR"
    3 -> "SSR"
    2 -> "SR"
    else -> "R"
}

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
    // 数据快照（进程级单例）：全量内容 + 已拥有视图（C# OnCreate 取一次语义）
    val all = remember { GameState.service.characters }
    val owned = remember { GameState.owned() }
    val ownedById = remember(owned) { owned.associateBy { it.save.characterId } }

    var searchText by rememberSaveable { mutableStateOf("") }
    var rarityFilter by rememberSaveable { mutableIntStateOf(-1) }
    var elementFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(ListSortMode.RarityDesc) }

    // 元素 chips 用全量角色的元素（图鉴视角：未拥有的元素也应可筛）
    val elements = remember(all) { all.map { it.element }.distinct() }
    val visible = ListFilter.filterSort(
        all, rarityFilter, elementFilter, searchText, sort,
        getName = { it.displayName },
        getRarity = { it.baseRarity },
        getElement = { it.element },
        getGroupKey = { it.world },
    )

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
                    itemsIndexed(visible, key = { _, def -> def.characterId }) { _, def ->
                        CollectionCard(
                            def = def,
                            ownedView = ownedById[def.characterId],
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = { onOpenCharacter(def.characterId) },
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
    val fraction = if (total == 0) 0f else got.toFloat() / total
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Text1,
                )
                Text(
                    text = "$got  /  $total",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Gold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // 我的角色入口（承接原占位页 actionLabel 按钮，链路不中断）
            Text(
                text = "我的角色  ›",
                fontSize = 13.sp,
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
                        text = rarityName(r),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.rarityColor(r),
                    )
                    Text(
                        text = "  ${gotByRarity[r] ?: 0} / ${totalByRarity[r] ?: 0}",
                        fontSize = 11.sp,
                        color = AppTheme.Text2,
                    )
                }
            }
        }
    }
}

/**
 * 图鉴卡片（ListCard 同构样式 + 未拥有态）：
 * 稀有度描边 + 元素淡底立绘框；未拥有时立绘叠黑蒙层 + 🔒，星级行显示「未获得」。
 */
@Composable
private fun CollectionCard(
    def: CharacterDataEntry,
    ownedView: OwnedCharacterView?,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
) {
    val rarity = def.baseRarity
    val rarityCol = AppTheme.rarityColor(rarity)
    val elem = ElementTheme.forElement(def.element)
    // Shared Element：与详情页 Hero 同 key 配对（未拥有同样可过渡，详情页自带遮罩）
    val sharedScope = LocalSharedTransitionScope.current
    val portraitModifier = if (sharedScope != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "portrait_${def.characterId}"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            )
        }
    } else Modifier

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
                    .background(elem.from.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                    .then(portraitModifier),
            ) {
                PortraitImage(
                    characterId = def.characterId,
                    rarity = rarity,
                    name = def.displayName,
                    modifier = Modifier.fillMaxSize(),
                    target = PortraitTarget.Thumb,
                    aura = true,
                    glowScale = 0.7f,
                )
                if (ownedView == null) {
                    // 未收录：黑蒙层 + 锁（立绘成剪影）
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "🔒", fontSize = 16.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = def.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = def.title,
                fontSize = 11.sp,
                color = AppTheme.Text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 2.dp)) {
                Text(
                    text = rarityName(rarity),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = rarityCol,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(text = elem.glyph + " " + def.element, fontSize = 11.sp, color = elem.from)
            }
            Text(
                text = if (ownedView != null) "★".repeat(ownedView.save.stars.coerceAtLeast(1)) else "未获得",
                fontSize = 11.sp,
                color = if (ownedView != null) AppTheme.Gold else AppTheme.Text3,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
