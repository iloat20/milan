package com.milan.game.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme

/**
 * 名录/卡组共用筛选条（C# ListFilterBar.cs 翻译）：
 * 搜索框 + 稀有度 chips + 元素 chips + 排序 chips，横滑布局。
 */

/** 排序模式（C# ListSortMode 枚举）。 */
enum class ListSortMode { RarityDesc, NameAsc, WorldAsc, ElementAsc }

/** 筛选 + 排序投影（C# ListFilterBar.FilterSort：不修改入参，返回新列表）。 */
object ListFilter {

    /**
     * @param rarityFilter -1 表示「全部」，否则按稀有度精确过滤。
     * @param elementFilter null 表示「全部」。
     * @param searchText 按名称子串（忽略大小写）过滤，空白不生效。
     * @param sort 排序模式：稀有度↓ / 名称 / 阵营（分组键） / 元素。
     */
    fun <T> filterSort(
        source: List<T>,
        rarityFilter: Int,
        elementFilter: String?,
        searchText: String,
        sort: ListSortMode,
        getName: (T) -> String,
        getRarity: (T) -> Int,
        getElement: (T) -> String,
        getGroupKey: (T) -> String,
    ): List<T> {
        var q = source
        if (rarityFilter >= 0) q = q.filter { getRarity(it) == rarityFilter }
        if (!elementFilter.isNullOrEmpty()) q = q.filter { getElement(it) == elementFilter }
        val s = searchText.trim()
        if (s.isNotEmpty()) q = q.filter { getName(it).contains(s, ignoreCase = true) }

        val list = q.toMutableList()
        val cmp: Comparator<T> = when (sort) {
            ListSortMode.RarityDesc -> compareByDescending(getRarity)
            ListSortMode.NameAsc -> compareBy(getName)
            ListSortMode.WorldAsc -> compareBy(getGroupKey)
            ListSortMode.ElementAsc -> compareBy(getElement)
        }
        list.sortWith(cmp)
        return list
    }
}

/** 筛选条（C# ListFilterBar 的 Compose 版；[elements] 为可选元素列表，chips 全部/各元素 glyph）。 */
@Composable
fun ListFilterBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    rarityFilter: Int,
    onRarityFilterChange: (Int) -> Unit,
    elementFilter: String?,
    onElementFilterChange: (String?) -> Unit,
    sort: ListSortMode,
    onSortChange: (ListSortMode) -> Unit,
    elements: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        // ── 搜索框（玻璃面板 + 单行无边框输入）──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(AppTheme.Surface, MaterialTheme.shapes.medium)
                .border(1.dp, AppTheme.Stroke, MaterialTheme.shapes.medium),
        ) {
            BasicTextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = AppTheme.Text1),
                cursorBrush = SolidColor(AppTheme.Gold),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (searchText.isEmpty()) {
                            Text("搜索角色名称…", style = MaterialTheme.typography.bodyLarge, color = AppTheme.Text3)
                        }
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))

        // ── 稀有度 ──
        SectionLabel("稀有度")
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip("全部", rarityFilter == -1) { onRarityFilterChange(-1) }
            FilterChip("R", rarityFilter == 1) { onRarityFilterChange(1) }
            FilterChip("SR", rarityFilter == 2) { onRarityFilterChange(2) }
            FilterChip("SSR", rarityFilter == 3) { onRarityFilterChange(3) }
            FilterChip("UR", rarityFilter == 4) { onRarityFilterChange(4) }
        }
        Spacer(Modifier.height(6.dp))

        // ── 元素（chip 用元素 glyph）──
        SectionLabel("元素")
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip("全部", elementFilter == null) { onElementFilterChange(null) }
            for (e in elements) {
                FilterChip(ElementTheme.forElement(e).glyph, elementFilter == e) {
                    onElementFilterChange(e)
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        // ── 排序 ──
        SectionLabel("排序")
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip("稀有度↓", sort == ListSortMode.RarityDesc) { onSortChange(ListSortMode.RarityDesc) }
            FilterChip("名称", sort == ListSortMode.NameAsc) { onSortChange(ListSortMode.NameAsc) }
            FilterChip("阵营", sort == ListSortMode.WorldAsc) { onSortChange(ListSortMode.WorldAsc) }
            FilterChip("元素", sort == ListSortMode.ElementAsc) { onSortChange(ListSortMode.ElementAsc) }
        }
    }
}

/** 单个筛选 chip（C# ListFilterBar.AddChip：选中金底 / 未选玻璃底，12sp 加粗）。 */
@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) AppTheme.Gold else AppTheme.Text2,
        modifier = Modifier
            .clip(shape)
            .background(AppTheme.Surface, shape)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) AppTheme.Gold else AppTheme.Stroke,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
