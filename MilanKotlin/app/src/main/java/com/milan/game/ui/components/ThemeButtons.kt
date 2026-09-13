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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
 * 织环按钮体系（v4.1 仪式化）：
 * - [GoldButton] / [GildedButton]: 朱砂**印章块**——纵向釉光 + 顶金丝 + 底厚条
 * - [InkButton]: 青瓷发丝描边，玻璃淡底
 *
 * 纪律：金箔只作顶丝/描边点缀，不包满框；主 CTA 用朱砂，不用大面积金。
 */

@Composable
private fun pressScale(interactionSource: MutableInteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
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
 * 主 CTA：朱砂印章块。
 * API 名保留 GoldButton；视觉 = 印泥釉面 + 顶金丝 + 纸厚底缘，不是平色圆角砖。
 */
@Composable
fun GoldButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    textSize: TextUnit = 16.sp,
    enabled: Boolean = true,
    letterSpacing: TextUnit = 2.sp,
    /** 列表行紧凑壳：更矮更窄，不撑破 ArtifactPanel 行高。 */
    compact: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = pressScale(interactionSource)
    val view = LocalView.current
    val shape = RoundedCornerShape(AppTheme.Roundness.md)
    val pressed by interactionSource.collectIsPressedAsState()
    val padH = if (compact) 14.dp else 28.dp
    val padV = if (compact) 8.dp else 15.dp
    val minH = if (compact) 36.dp else 52.dp
    val typeSize = if (compact && textSize.value > 14f) 13.sp else textSize
    val letter = if (compact && letterSpacing.value > 2f) 1.sp else letterSpacing

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (enabled && !compact) (10f + if (pressed) 2f else 0f) else 0f
                ambientShadowColor = Color.Black
                spotShadowColor = AppTheme.ZhuSha.copy(alpha = 0.55f)
            }
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        if (pressed) AppTheme.ZhuShaDeep else AppTheme.ZhuShaHi,
                        AppTheme.ZhuSha,
                        if (pressed) Color(0xFF7A281F) else AppTheme.ZhuShaDeep,
                    ),
                ),
                shape = shape,
            )
            .drawBehind {
                val h = size.height
                val w = size.width
                val topW = if (compact) 1.2f else 1.6f
                val thick = if (compact) 2.5f else 3.5f
                drawLine(
                    color = AppTheme.GoldHi.copy(alpha = 0.55f),
                    start = Offset(0f, 1.2f),
                    end = Offset(w, 1.2f),
                    strokeWidth = topW,
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.12f),
                    start = Offset(0f, 3.5f),
                    end = Offset(w, 3.5f),
                    strokeWidth = if (compact) 1.8f else 2.5f,
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.28f),
                    topLeft = Offset(0f, h - thick),
                    size = androidx.compose.ui.geometry.Size(w, thick),
                )
            }
            .defaultMinSize(minWidth = 48.dp, minHeight = minH)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) {
                if (enabled) {
                    HapticManager.buttonClick(view)
                    onClick()
                }
            }
            .padding(horizontal = padH, vertical = padV),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = typeSize,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.Text1,
            letterSpacing = letter,
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

/**
 * 次级按钮：青瓷发丝 + 玻璃淡底，按压填色。
 * 与主 CTA 拉开层级：无投影、无金丝。
 */
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
    val scale = pressScale(interactionSource)
    val view = LocalView.current
    val shape = RoundedCornerShape(AppTheme.Roundness.md)
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        if (pressed) color.copy(alpha = 0.16f) else AppTheme.Surface,
                        if (pressed) color.copy(alpha = 0.08f) else Color.Transparent,
                    ),
                ),
                shape = shape,
            )
            .border(1.dp, color.copy(alpha = if (pressed) 0.75f else 0.45f), shape)
            .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) {
                if (enabled) {
                    HapticManager.buttonClick(view)
                    onClick()
                }
            }
            .padding(horizontal = 22.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = textSize,
            fontWeight = FontWeight.Medium,
            color = color,
            letterSpacing = 1.2.sp,
        )
    }
}

/** 全宽主 CTA 便捷壳（主页「召灵」等）：仪式字距。 */
@Composable
fun GoldButtonFull(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GoldButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        textSize = 18.sp,
        letterSpacing = 5.sp,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    )
}

