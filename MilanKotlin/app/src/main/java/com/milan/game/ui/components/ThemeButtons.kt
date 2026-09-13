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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.infrastructure.HapticManager
import com.milan.game.ui.theme.AppTheme

/**
 * 水墨进阶 v4 按钮体系：
 * - [GoldButton] / [GildedButton]: 朱砂主 CTA（兼容旧名，视觉为印章色块）
 * - [InkButton]: 次级单线描边
 *
 * 去装饰：无斜切角、无环痕、无内双线。按压 scale 0.97 + 触觉；最小热区 48dp。
 */

/** 按钮按压缩放。 */
@Composable
private fun pressScale(interactionSource: MutableInteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "btnPress",
    ).value
}

/** 兼容别名 → [GoldButton]。 */
@Composable
fun GildedButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    textSize: TextUnit = 16.sp,
    enabled: Boolean = true,
) = GoldButton(text, modifier, onClick, textSize, enabled)

/**
 * 主 CTA：朱砂实底印章块。
 * API 名保留 GoldButton 以兼容全站调用；视觉为朱砂，金箔只服务稀有度。
 */
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
    val shape = RoundedCornerShape(AppTheme.Roundness.md)
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(shape)
            .background(
                if (pressed) AppTheme.ZhuShaDeep else AppTheme.ZhuSha,
                shape,
            )
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) {
                if (enabled) {
                    HapticManager.buttonClick(view)
                    onClick()
                }
            }
            .padding(horizontal = 28.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = textSize,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.Text1,
            letterSpacing = 1.sp,
        )
    }
}

/** @deprecated v4 命名为 [InkButton]。 */
@Deprecated("v4 改名为 InkButton", ReplaceWith("InkButton(text, modifier, onClick, textSize, color, enabled)"))
@Composable
fun NeonButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    textSize: TextUnit = 14.sp,
    color: Color = AppTheme.Frost,
    enabled: Boolean = true,
) = InkButton(text, modifier, onClick, textSize, color, enabled)

/** 次级按钮：单线描边 + 极淡底，无内环。 */
@Composable
fun InkButton(
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
    val shape = RoundedCornerShape(AppTheme.Roundness.md)
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(shape)
            .background(
                if (pressed) color.copy(alpha = 0.12f) else Color.Transparent,
                shape,
            )
            .border(1.dp, color.copy(alpha = 0.55f), shape)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) {
                if (enabled) {
                    HapticManager.buttonClick(view)
                    onClick()
                }
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = textSize,
            fontWeight = FontWeight.Medium,
            color = color,
            letterSpacing = 0.5.sp,
        )
    }
}
