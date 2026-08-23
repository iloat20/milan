package com.milan.game.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.theme.AppTheme

/**
 * 共享设计系统（C# UIHelper.cs / UI.cs 翻译）：玻璃面板、页底色、分区小标题。
 * 单一调色板 AppTheme；GlassPanel 半透明深紫黑底 + 顶部内高光 + 发丝边。
 * （I2 清理：TitleWithOrnament/TabularText/Avatar/IconCircle/RarityChip 零调用已删，
 * 稀有度标签与头像统一走 AppTheme.rarityColor + PortraitImage，避免双份实现漂移。）
 */

/** 玻璃拟态面板（C# UI.GlassPanel）：半透明深紫黑底 + 顶部内高光 + 发丝边。
 *  [highlighted] 仅用于选中 / 当期 UP 面板（熔金发丝线点睛）；[nested] 嵌套态略深以拉开层次。
 *  （M6：参数由 gold 更名——样式语义与业务态解耦，调用侧传 highlighted = 业务布尔。） */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 14.dp,
    highlighted: Boolean = false,
    nested: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    // 底色：Surface 半透明（默认 α≈0.55）；嵌套态 #251242 @ α≈0.59。
    val fill = if (nested) AppTheme.SurfaceNested else AppTheme.Surface
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill, shape)
            .border(
                width = if (highlighted) 1.5.dp else 1.dp,
                color = if (highlighted) AppTheme.Gold.copy(alpha = 0.45f) else AppTheme.Stroke,
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
