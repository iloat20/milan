package com.milan.game.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
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
 * 或直接给 PortraitImage 传 showAura = true（推荐，零侵入）。
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
        // 用旋转椭圆近似（Canvas 支持原生椭圆 drawOval，无需旋转；此处保留函数以便将来做动效）
        rotate(0f, pivot = androidx.compose.ui.geometry.Offset(cx, cy)) {
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(base.copy(alpha = intensity), base.copy(alpha = 0f)),
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    radius = rx.coerceAtLeast(1f),
                ),
                topLeft = androidx.compose.ui.geometry.Offset(cx - rx, cy - ry),
                size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f),
            )
        }
        // 第二层更紧的高光核心
        drawOval(
            color = base.copy(alpha = intensity * 0.6f),
            topLeft = androidx.compose.ui.geometry.Offset(cx - rx * 0.55f, cy - ry * 0.55f),
            size = androidx.compose.ui.geometry.Size(rx * 1.1f, ry * 1.1f),
        )
    }
}

/**
 * 带脚下氛围圈的立绘容器。立绘（透明背景）居中，氛围圈在其脚下作为底层。
 * 直接复用此容器可保证 7 个调用点零改动接入 v2 光环。
 */
@Composable
fun PortraitWithAura(
    characterId: String,
    rarity: Int,
    modifier: Modifier = Modifier,
    name: String? = null,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Crop,
    target: PortraitTarget = PortraitTarget.Full,
    glowScale: Float = 1f,
) {
    Box(modifier = modifier) {
        AuraHalo(rarity = rarity, glowScale = glowScale, modifier = Modifier.fillMaxSize())
        PortraitImage(
            characterId = characterId,
            rarity = rarity,
            name = name,
            contentScale = contentScale,
            target = target,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
