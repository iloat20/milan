package com.milan.game.ui.nav

import androidx.compose.material3.MaterialTheme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.milan.game.ui.theme.AppTheme

/** 全局底部导航项。 */
enum class NavItem(val icon: ImageVector, val label: String) {
    Home(Icons.Outlined.Home, "主页"),
    Gacha(Icons.Outlined.Star, "抽卡"),
    Deck(Icons.AutoMirrored.Outlined.List, "卡组"),
    Shop(Icons.Outlined.ShoppingCart, "商店"),
    Settings(Icons.Outlined.Settings, "设置"),
}

/**
 * 底部导航（5 项）。v4：砚墨实底 + 顶部短线选中态。
 * 无选中面板渐变、无底部圆环。
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
            .background(AppTheme.BgMid)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem.entries.forEach { item ->
            NavCell(
                item = item,
                selected = item == active,
                onClick = { onSelect(item) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            )
        }
    }
}

/** 单个导航格：图标 + 文字；选中朱砂 + 顶部短线。 */
@Composable
private fun NavCell(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "navScale")
    val barWidth by animateDpAsState(
        targetValue = if (selected) 20.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "navBarWidth",
    )

    val glyphColor = if (selected) AppTheme.ZhuSha else AppTheme.Text3
    val labelColor = if (selected) AppTheme.ZhuSha else AppTheme.Text3

    Box(
        modifier = modifier
            .scale(scale)
            .graphicsLayer { alpha = if (pressed) 0.85f else 1f }
            .semantics {
                this[SemanticsProperties.Selected] = selected
                this[SemanticsProperties.Role] = Role.Tab
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected && barWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 2.dp)
                    .width(barWidth)
                    .height(2.dp)
                    .background(AppTheme.ZhuSha, RoundedCornerShape(1.dp)),
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
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = labelColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
