package com.milan.game.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.milan.game.ui.theme.AppTheme

/**
 * 脚下氛围圈（稀有度光环）— v2 工程配套。
 *
 * 在立绘脚下渲染一圈稀有度色的径向光晕 + 柔和椭圆光圈，强化「稀有度即光环」的
 * 视觉语言（v2 master-spec §4.2）。立绘本身为透明背景（v2 铁律），光环作为
 * 独立底层叠加，UI 可随时开关 / 换色，不影响立绘资源。
 *
 * 使用：
 *   Box(Modifier.fillMaxSize()) { AuraHalo(rarity = 4); PortraitImage(...) }
 * 或直接给 PortraitImage 传 aura = true（推荐，零侵入）。
 *
 * @param rarity 稀有度 1=R 2=SR 3=SSR 4=UR
 * @param glowScale 光晕半径系数（默认 1f，卡面小图可降到 0.7f）
 */
@Composable
fun AuraHalo(
    rarity: Int,
    modifier: Modifier = Modifier,
    glowScale: Float = 1f,
) {
    val base = AppTheme.rarityColor(rarity)
    // 稀有度越高，光晕越亮、外圈越宽
    val intensity = when (rarity) {
        4 -> 0.55f
        3 -> 0.42f
        2 -> 0.32f
        else -> 0.24f
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // 脚下椭圆光圈：中心约在画布底部 78% 处，宽占 70%
        val cx = w / 2f
        val cy = h * 0.82f
        val rx = w * 0.42f * glowScale
        val ry = h * 0.16f * glowScale
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(base.copy(alpha = intensity), base.copy(alpha = 0f)),
                center = Offset(cx, cy),
                radius = rx.coerceAtLeast(1f),
            ),
            topLeft = Offset(cx - rx, cy - ry),
            size = Size(rx * 2f, ry * 2f),
        )
        // 第二层更紧的高光核心
        drawOval(
            color = base.copy(alpha = intensity * 0.6f),
            topLeft = Offset(cx - rx * 0.55f, cy - ry * 0.55f),
            size = Size(rx * 1.1f, ry * 1.1f),
        )
    }
}
