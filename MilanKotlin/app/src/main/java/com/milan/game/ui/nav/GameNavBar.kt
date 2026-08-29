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

/** 全局底部导航项（水墨国风版：墨色玻璃底座 + 金箔选中态）。 */
enum class NavItem(val icon: ImageVector, val label: String) {
    Home(Icons.Filled.Home, "主页"),
    Gacha(Icons.Filled.Star, "抽卡"),
    Deck(Icons.AutoMirrored.Filled.List, "卡组"),
    Shop(Icons.Filled.ShoppingCart, "商店"),
    Settings(Icons.Filled.Settings, "设置"),
}

/**
 * 水墨国风底部导航栏（5 项）。墨色玻璃底座 + 金箔选中高亮面板 +
 * 顶部金箔指示线 + 按压缩放反馈。
 */
@Composable
fun GameNavBar(
    active: NavItem,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
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

/** 单个导航格：等宽长方形，图标 + 文字整体居中，选中态金箔面板 + 顶部金线。 */
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
        // 顶部金箔指示线
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
