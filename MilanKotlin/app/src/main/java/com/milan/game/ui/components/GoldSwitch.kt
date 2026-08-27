package com.milan.game.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.milan.game.ui.theme.AppTheme

/**
 * 熔金开关（2026-08 UI 现代化）：替换设置页残留的原生 Material Switch。
 *
 * 视觉与全站按钮同语言：开启态熔金三段渐变轨道 + 白描边，关闭态玻璃底 + 发丝边；
 * 滑块弹簧位移（spring 0.65 阻尼，与 NavBar 按压弹性一致）。
 * 语义走 Role.Switch（TalkBack 与原生开关同播报）。
 */
@Composable
fun GoldSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackWidth = 46.dp
    val trackHeight = 26.dp
    val thumbSize = 20.dp
    val thumbTravel = trackWidth - thumbSize - 6.dp
    val thumbX by animateDpAsState(
        targetValue = if (checked) thumbTravel else 3.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "goldSwitchThumb",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(RoundedCornerShape(trackHeight / 2))
            .background(
                if (checked) {
                    Brush.verticalGradient(listOf(AppTheme.GoldHi, AppTheme.GoldDeep))
                } else {
                    Brush.verticalGradient(
                        listOf(AppTheme.Surface.copy(alpha = 0.9f), AppTheme.BgMid.copy(alpha = 0.9f)),
                    )
                }
            )
            .border(
                width = 1.dp,
                color = if (checked) Color.White.copy(alpha = 0.45f) else AppTheme.Stroke,
                shape = RoundedCornerShape(trackHeight / 2),
            )
            // toggleable 携带 Role.Switch + 选中态语义（TalkBack 与原生开关同播报），无涟漪
            .toggleable(
                value = checked,
                role = Role.Switch,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = thumbX)
                .size(thumbSize)
                .clip(CircleShape)
                .background(
                    if (checked) AppTheme.GoldTextOn else AppTheme.Text3,
                )
                .border(0.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
        ) {
            if (checked) {
                // 开启态滑块中心小金点（熔金轨道上的深色标记，替代图标依赖）
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AppTheme.GoldDeep)
                        .align(Alignment.Center),
                )
            }
        }
    }
}
