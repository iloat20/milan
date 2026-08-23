package com.milan.game.ui.nav

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.theme.AppTheme

/** 全局底部导航项（C# GameNavBar.NavItem 翻译，5 项，Material 标准图标（P2-6 统一图标语言））。 */
enum class NavItem(val icon: ImageVector, val label: String) {
    Home(Icons.Filled.Home, "主页"),
    Gacha(Icons.Filled.Star, "抽卡"),
    Deck(Icons.AutoMirrored.Filled.List, "卡组"),
    Shop(Icons.Filled.ShoppingCart, "商店"),
    Settings(Icons.Filled.Settings, "设置"),
}

/**
 * Obsidian & Gold 全局底部导航栏（5 项）。玻璃底座 + 选中项金色高亮面板 +
 * 顶部金色指示线 + 按压缩放反馈。导航目标由宿主决定，组件自身不依赖任何具体页面。
 */
@Composable
fun GameNavBar(
    active: NavItem,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 玻璃底座：暗紫玻璃 + 发丝描边（C# GlassPanel(0, gold:false)）
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    listOf(AppTheme.Surface.copy(alpha = 0.85f), AppTheme.Surface)
                ),
                shape = RoundedCornerShape(20.dp),
            )
            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(20.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem.entries.forEach { item ->
            NavCell(
                item = item,
                selected = item == active,
                onClick = { onSelect(item) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .padding(horizontal = 3.dp),
            )
        }
    }
}

/** 单个导航格：等宽长方形，图标 + 文字整体居中，选中态金面板 + 顶部金线。 */
@Composable
private fun NavCell(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "navScale")
    // 捕获参数副本：semantics 块内 receiver 也有 selected 属性，直接 `selected = selected` 会自赋值
    val isSelected = selected

    val glyphColor = if (selected) AppTheme.Gold else AppTheme.Frost.copy(alpha = 0.7f)
    val labelColor = if (selected) AppTheme.Gold else AppTheme.Text2

    Box(
        modifier = modifier
            .scale(scale)
            .graphicsLayer { alpha = if (pressed) 0.85f else 1f }
            .then(
                if (selected) Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(AppTheme.Gold.copy(alpha = 0.22f), AppTheme.Gold.copy(alpha = 0.10f))
                        ),
                        RoundedCornerShape(14.dp),
                    )
                    .border(1.dp, AppTheme.Gold.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                else Modifier
            )
            // P2-6 无障碍：把选中态与 Tab 角色暴露给 TalkBack（此前选中只靠视觉高亮）。
            // 用显式属性键（SemanticsProperties.Selected/Role），避免版本间扩展属性签名差异。
            .semantics {
                this[SemanticsProperties.Selected] = isSelected
                this[SemanticsProperties.Role] = Role.Tab
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 顶部金色指示线：绝对定位到顶边，不参与内容流（两端渐隐）。
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 4.dp)
                    .width(24.dp)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                AppTheme.Gold,
                                Color.Transparent,
                            )
                        )
                    ),
            )
        }

        // 图标 + 文字纵向堆叠居中
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = glyphColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = item.label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = labelColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
