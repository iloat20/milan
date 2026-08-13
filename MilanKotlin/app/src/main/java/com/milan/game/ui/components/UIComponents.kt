package com.milan.game.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.theme.AppTheme

/**
 * 共享设计系统（C# UIHelper.cs / UI.cs 翻译）：玻璃面板、饰线标题、页底色、文本、头像、稀有度标签。
 * 单一调色板 AppTheme；GlassPanel 半透明深紫黑底 + 顶部内高光 + 发丝边。
 */

/** 玻璃拟态面板（C# UI.GlassPanel）：半透明深紫黑底 + 顶部内高光 + 发丝边。
 *  [gold] 仅用于选中 / 当期 UP 面板（熔金发丝线点睛）；[nested] 嵌套态略深以拉开层次。 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 14.dp,
    gold: Boolean = false,
    nested: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    // 底色：Surface 半透明（默认 α≈0.55）；嵌套态 #251242 @ α≈0.59。
    val fill = if (nested) Color(0x96251242) else AppTheme.Surface
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill, shape)
            .border(
                width = if (gold) 1.5.dp else 1.dp,
                color = if (gold) AppTheme.Gold.copy(alpha = 0.45f) else AppTheme.Stroke,
                shape = shape,
            ),
    ) {
        // 顶部内高光：白 α10% → 透明，覆盖上半部（裁进圆角）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(radius * 2)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                    ),
                ),
        )
        content()
    }
}

/** Twilight 页面底色：暮紫夜垂直三段渐变（C# UI.PageBackground 对角线版近似）。
 * 每次调用新 Brush，无共享状态。 */
@Composable
fun PageBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(AppTheme.BgDeepest, AppTheme.BgMid, AppTheme.BgDeepest)),
            ),
        content = content,
    )
}

/** 标题饰线行：左右 1dp 金线（或霜蓝）渐隐 + 中心 ◆ 标题，字距收紧（C# UI.TitleWithOrnament）。 */
@Composable
fun TitleWithOrnament(
    title: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp,
    frost: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrnamentLine(leftToRight = true, frost)
        Text(
            text = "◆ $title",
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = AppTheme.Text1,
            letterSpacing = 0.08.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        OrnamentLine(leftToRight = false, frost)
    }
}

@Composable
private fun RowScope.OrnamentLine(leftToRight: Boolean, frost: Boolean) {
    val c = if (frost) AppTheme.Frost else AppTheme.Gold
    val brush = if (leftToRight) {
        Brush.horizontalGradient(listOf(Color.Transparent, c.copy(alpha = 0.55f)))
    } else {
        Brush.horizontalGradient(listOf(c.copy(alpha = 0.55f), Color.Transparent))
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .height(1.dp)
            .background(brush),
    )
}

/** 分区小标题（C# ListFilterBar.SectionLabel：12sp 加粗次文字）。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = AppTheme.Text2,
        modifier = modifier.padding(bottom = 4.dp),
    )
}

/** 等宽数字文本（C# UI.Tabular）：货币 / 计数 / 数值面板，避免数字跳动。 */
@Composable
fun TabularText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    color: Color = AppTheme.Text1,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        fontFamily = FontFamily.Monospace,
        modifier = modifier,
    )
}

/** 圆角头像：稀有度色底 + 白色首字 + 阴影（C# UI.Avatar 近似）。 */
@Composable
fun Avatar(
    initial: String,
    rarity: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.rarityColor(rarity)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(c),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/** 圆形玻璃图标钮（C# ThemeButtons.IconCircle：44dp 玻璃圆钮 + 金色 glyph）。 */
@Composable
fun IconCircle(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(AppTheme.Surface, CircleShape)
            .border(1.dp, AppTheme.Stroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            fontSize = (size.value * 0.4f).sp,
            color = AppTheme.Gold,
        )
    }
}

/** 稀有度标签（HomeScreen Hero 同款：稀有度色描边胶囊）。 */
@Composable
fun RarityChip(
    text: String,
    rarity: Int,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.rarityColor(rarity)
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = c,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(c.copy(alpha = 0.27f))
            .border(1.dp, c, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
