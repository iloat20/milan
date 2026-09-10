package com.milan.game.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.infrastructure.HapticManager
import com.milan.game.ui.effects.inkSplash
import com.milan.game.ui.theme.AppTheme

/**
 * Obsidian & Gold 按钮体系（C# ThemeButtons.cs 翻译，ui-redesign-plan.md §2.1）：
 * - GoldButton: 金色主按钮（斜切角 + 垂直渐变 + 双描边 + 周期扫光）
 * - NeonButton: 霓虹描边次按钮
 * （I2 清理：DangerButton 零调用已删；危险操作统一用 NeonButton(color = AppTheme.Danger) 染红。）
 *
 * 2026-09-10 UX：
 * - 按压微缩（0.96）+ 松手回弹，与 CodexCard 同一套「实体触感」语言
 * - 点击轻触觉（HapticManager.buttonClick）
 * - 最小可点区 48dp（Material 触达目标），小字按钮不再难点
 */

/** 左上/右下 6dp 斜切角形状（C# CutCornerButton.BuildPath）。
 *  显式实现 Shape 接口：density 是显式参数，`density.toPx(6.dp)` 为 Density 接口成员方法，不受 lambda 接收者推断影响。 */
private val CutShape: Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cut = 6.dp.value * density.density
        val path = Path().apply {
            moveTo(cut, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - cut)
            lineTo(size.width - cut, size.height)
            lineTo(0f, size.height)
            lineTo(0f, cut)
            close()
        }
        return Outline.Generic(path)
    }
}

/** 按钮按压缩放（shared press feel）。 */
@Composable
private fun pressScale(interactionSource: MutableInteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "btnPress",
    ).value
}

/** 金色主按钮：召唤 / 出战 / 购买确认（熔金渐变 + 发丝高光 + 深金收边；C# ThemeButtons.Gold）。 */
@Composable
fun GoldButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    textSize: TextUnit = 16.sp,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = pressScale(interactionSource)
    val view = LocalView.current
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CutShape)
            .background(
                Brush.verticalGradient(listOf(AppTheme.GoldHi, AppTheme.Gold, AppTheme.GoldDeep)),
                CutShape,
            )
            .border(1.dp, Color.White.copy(alpha = 0.47f), CutShape)
            .inkSplash(interactionSource)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) {
                if (enabled) {
                    HapticManager.buttonClick(view)
                    onClick()
                }
            }
            .padding(horizontal = 32.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
            color = AppTheme.GoldTextOn,
        )
    }
}

/** 霓虹描边次按钮：透明底 + 描边 + 内发光（C# ThemeButtons.Neon，默认霜蓝）。 */
@Composable
fun NeonButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    textSize: TextUnit = 14.sp,
    color: Color = AppTheme.Frost,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = pressScale(interactionSource)
    val view = LocalView.current
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(AppTheme.Roundness.md))
            .background(color.copy(alpha = 0.06f), RoundedCornerShape(AppTheme.Roundness.md))
            .border(1.5.dp, color, RoundedCornerShape(AppTheme.Roundness.md))
            .inkSplash(interactionSource)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) {
                if (enabled) {
                    HapticManager.buttonClick(view)
                    onClick()
                }
            }
            .padding(horizontal = 28.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}
