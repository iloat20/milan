package com.milan.game.ui.effects

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.MeshGradientPainter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize

/**
 * Mesh Gradient 工具集 — Compose 1.12 官方 [MeshGradientPainter]。
 *
 * 参考：
 * - https://developer.android.com/develop/ui/compose/graphics/draw/mesh-gradient
 * - https://github.com/android/snippets/blob/main/compose/snippets/src/main/java/com/example/compose/snippets/graphics/MeshGradientSnippets.kt
 *
 * Compose 1.12 新特性：
 * - [MeshGradientPainter]：稳定的网格渐变 Painter
 * - 在 DrawScope block 中读取 State → 自动触发重绘
 * - `Modifier.paint(painter)` 应用到 Box
 */

// ══════════════════════════════════════════════════════════════════════════════
// 颜色配置（4×4 顶点网格 = 16 个顶点，行优先排列）
// v3 清债：稀有度/元素色列统一由 [rarityMeshColors16] / [elementMeshColors16]
// 从 AppTheme / ElementTheme 权威色板派生（见 EffectPalettes.kt），禁止就地硬编码。
// ══════════════════════════════════════════════════════════════════════════════

// ══════════════════════════════════════════════════════════════════════════════
// 公开 Composable 入口
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun RarityMeshGradient(
    rarity: Int,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    val colors = rarityMeshColors16(rarity)
    MeshGradientBox(colors = colors, animated = animated, modifier = modifier)
}

/**
 * 全屏稀有度氛围底（抽卡演出浮层用）。
 * 旧 [RarityMeshGradient] 强制 16:9，fillMaxSize 会被裁成顶部一条——演出层必须用本入口。
 */
@Composable
fun RarityMeshBackdrop(
    rarity: Int,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
) {
    val colors = rarityMeshColors16(rarity)
    val painter = if (animated) {
        rememberAnimatedMeshGradientPainter(colors)
    } else {
        rememberStaticMeshGradientPainter(colors)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 0.55f }
            .paint(painter),
    )
}

@Composable
fun ElementMeshGradient(
    element: String,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    val colors = elementMeshColors16(element)
    MeshGradientBox(colors = colors, animated = animated, modifier = modifier)
}

@Composable
fun MeshGradientBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    require(colors.size == 16) { "MeshGradient 需要 16 种颜色（4×4 顶点），实际 ${colors.size}" }
    MeshGradientBox(colors = colors, animated = animated, modifier = modifier)
}

// ══════════════════════════════════════════════════════════════════════════════
// 内部实现
// ══════════════════════════════════════════════════════════════════════════════

/**
 * 统一 Mesh Gradient 容器 — 使用 Box + Modifier.paint()（Compose 1.12 官方用法）。
 */
@Composable
private fun MeshGradientBox(
    colors: List<Color>,
    animated: Boolean,
    modifier: Modifier
) {
    val painter = if (animated) {
        rememberAnimatedMeshGradientPainter(colors)
    } else {
        rememberStaticMeshGradientPainter(colors)
    }

    // Compose 1.12 官方用法：Box + Modifier.paint()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .paint(painter)
    )
}

/**
 * 静态 MeshGradientPainter — 4×4 顶点网格。
 *
 * Compose 1.12 API：MeshGradientPainter(rows, columns) { setVertex(row, col, pos, color) }
 * rows=3, columns=3 → 4行4列顶点（3格需要4条线）
 */
@Composable
private fun rememberStaticMeshGradientPainter(colors: List<Color>) = remember(colors) {
    MeshGradientPainter(rows = 3, columns = 3) {
        // 行0
        setVertex(0, 0, Offset(0.0f, 0.0f), colors[0])
        setVertex(0, 1, Offset(0.33f, 0.0f), colors[1])
        setVertex(0, 2, Offset(0.67f, 0.0f), colors[2])
        setVertex(0, 3, Offset(1.0f, 0.0f), colors[3])
        // 行1
        setVertex(1, 0, Offset(0.0f, 0.33f), colors[4])
        setVertex(1, 1, Offset(0.33f, 0.33f), colors[5])
        setVertex(1, 2, Offset(0.67f, 0.33f), colors[6])
        setVertex(1, 3, Offset(1.0f, 0.33f), colors[7])
        // 行2
        setVertex(2, 0, Offset(0.0f, 0.67f), colors[8])
        setVertex(2, 1, Offset(0.33f, 0.67f), colors[9])
        setVertex(2, 2, Offset(0.67f, 0.67f), colors[10])
        setVertex(2, 3, Offset(1.0f, 0.67f), colors[11])
        // 行3
        setVertex(3, 0, Offset(0.0f, 1.0f), colors[12])
        setVertex(3, 1, Offset(0.33f, 1.0f), colors[13])
        setVertex(3, 2, Offset(0.67f, 1.0f), colors[14])
        setVertex(3, 3, Offset(1.0f, 1.0f), colors[15])
    }
}

/**
 * 动画 MeshGradientPainter — 顶点微动 + 颜色流动。
 *
 * Compose 1.12 核心特性：
 * - remember {} 无 key → Painter 只创建一次
 * - block 在 DrawScope 中每帧重执行
 * - offset / colorShift 是 State delegate，block 内读取最新值
 * - 自动触发重绘，无需 invalidate()
 */
@Composable
private fun rememberAnimatedMeshGradientPainter(colors: List<Color>): MeshGradientPainter {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh")

    val offset by infiniteTransition.animateFloat(
        initialValue = -0.08f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vertex_offset"
    )

    val colorShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "color_shift"
    )

    // Compose 1.12：Painter 只创建一次，block 内 State delegate 自动读取最新值
    return remember {
        MeshGradientPainter(rows = 3, columns = 3) {
            val o = offset
            val oH = o * 0.5f
            val n = colors.size
            val ci = ((colorShift * n).toInt()) % n

            // 行0
            setVertex(0, 0, Offset(0f + o, 0f + o), colors[(ci + 0) % n])
            setVertex(0, 1, Offset(0.33f, 0f - oH), colors[(ci + 1) % n])
            setVertex(0, 2, Offset(0.67f, 0f + oH), colors[(ci + 2) % n])
            setVertex(0, 3, Offset(1f - o, 0f + o), colors[(ci + 3) % n])
            // 行1
            setVertex(1, 0, Offset(0f + oH, 0.33f), colors[(ci + 4) % n])
            setVertex(1, 1, Offset(0.33f + o, 0.33f - o), colors[(ci + 5) % n])
            setVertex(1, 2, Offset(0.67f - o, 0.33f + o), colors[(ci + 6) % n])
            setVertex(1, 3, Offset(1f - oH, 0.33f), colors[(ci + 7) % n])
            // 行2
            setVertex(2, 0, Offset(0f + oH, 0.67f), colors[(ci + 8) % n])
            setVertex(2, 1, Offset(0.33f - o, 0.67f + o), colors[(ci + 9) % n])
            setVertex(2, 2, Offset(0.67f + o, 0.67f - o), colors[(ci + 10) % n])
            setVertex(2, 3, Offset(1f - oH, 0.67f), colors[(ci + 11) % n])
            // 行3
            setVertex(3, 0, Offset(0f + o, 1f - o), colors[(ci + 12) % n])
            setVertex(3, 1, Offset(0.33f, 1f + oH), colors[(ci + 13) % n])
            setVertex(3, 2, Offset(0.67f, 1f - oH), colors[(ci + 14) % n])
            setVertex(3, 3, Offset(1f - o, 1f - o), colors[(ci + 15) % n])
        }
    }
}
