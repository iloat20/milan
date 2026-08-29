package com.milan.game.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.effects.inkSplash
import com.milan.game.ui.theme.AppTheme

/**
 * Obsidian & Gold 按钮体系（C# ThemeButtons.cs 翻译，ui-redesign-plan.md §2.1）：
 * - GoldButton: 金色主按钮（斜切角 + 垂直渐变 + 双描边 + 周期扫光）
 * - NeonButton: 霓虹描边次按钮
 * （I2 清理：DangerButton 零调用已删；危险操作统一用 NeonButton(color = AppTheme.Danger) 染红。）
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

/** 金色主按钮：召唤 / 出战 / 购买确认（熔金渐变 + 发丝高光 + 深金收边；C# ThemeButtons.Gold）。 */
@Composable
fun GoldButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    textSize: TextUnit = 16.sp,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(CutShape)
            .background(
                Brush.verticalGradient(listOf(AppTheme.GoldHi, AppTheme.Gold, AppTheme.GoldDeep)),
                CutShape,
            )
            .border(1.dp, Color.White.copy(alpha = 0.47f), CutShape)
            .clickable(enabled = enabled, onClick = onClick)
            .inkSplash()
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .border(1.5.dp, color, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .inkSplash()
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
