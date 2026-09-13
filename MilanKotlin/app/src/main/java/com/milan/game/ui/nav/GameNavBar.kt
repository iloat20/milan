package com.milan.game.ui.nav

import androidx.compose.material3.MaterialTheme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.clip
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

/** 全局底部导航项。 */
enum class NavItem(val icon: ImageVector, val label: String) {
    Home(Icons.Outlined.Home, "主页"),
    Gacha(Icons.Outlined.Star, "抽卡"),
    Deck(Icons.AutoMirrored.Outlined.List, "卡组"),
    Shop(Icons.Outlined.ShoppingCart, "商店"),
    Settings(Icons.Outlined.Settings, "设置"),
}

/**
 * 底部导航（5 项）· 玻璃浮层。
 *
 * 材质：半透明砚墨 + 顶发丝 + 选中格釉光垫 + 朱砂短铭牌。
 * 不再是实心 BgMid 砖——内容从栏下透出一点，才像「浮在织环台上」。
 */
@Composable
fun GameNavBar(
    active: NavItem,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 顶发丝：与内容的分界，比硬切边柔和
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        AppTheme.BgDeepest.copy(alpha = 0.35f),
                    ),
                ),
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AppTheme.Stroke),
        )
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            AppTheme.SurfaceNested.copy(alpha = 0.94f),
                            AppTheme.BgMid.copy(alpha = 0.98f),
                        ),
                    ),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
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
}

/**
 * 单个导航格：图标 + 字标；选中 = 釉光垫 + 朱砂铭牌短线 + 金顶丝。
 */
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
    val plateAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "navPlate",
    )
    val barWidth by animateDpAsState(
        targetValue = if (selected) 18.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "navBarWidth",
    )

    val glyphColor = if (selected) AppTheme.ZhuShaHi else AppTheme.Text3
    val labelColor = if (selected) AppTheme.ZhuSha else AppTheme.Text3
    val plateShape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .scale(scale)
            .padding(horizontal = 3.dp)
            .graphicsLayer { alpha = if (pressed) 0.88f else 1f }
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
        // 选中釉光垫
        if (plateAlpha > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = plateAlpha * 0.9f }
                    .clip(plateShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                AppTheme.ZhuSha.copy(alpha = 0.16f),
                                AppTheme.ZhuSha.copy(alpha = 0.04f),
                            ),
                        ),
                        plateShape,
                    )
                    .border(
                        1.dp,
                        AppTheme.ZhuSha.copy(alpha = 0.22f),
                        plateShape,
                    ),
            )
        }

        // 顶金丝（仅选中）
        if (selected && barWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 1.dp)
                    .width(barWidth)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                AppTheme.Gold.copy(alpha = 0.2f),
                                AppTheme.GoldHi,
                                AppTheme.Gold.copy(alpha = 0.2f),
                            ),
                        ),
                        RoundedCornerShape(1.dp),
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
                modifier = Modifier
                    .size(if (selected) 23.dp else 21.dp)
                    .graphicsLayer { alpha = if (selected) 1f else 0.78f },
            )
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = labelColor,
                textAlign = TextAlign.Center,
                letterSpacing = if (selected) 1.sp else 0.5.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            // 选中铭牌短横（字下）
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .width(if (selected) 10.dp else 0.dp)
                    .height(1.5.dp)
                    .background(
                        AppTheme.ZhuSha.copy(alpha = if (selected) 0.9f else 0f),
                        RoundedCornerShape(1.dp),
                    ),
            )
        }
    }
}
