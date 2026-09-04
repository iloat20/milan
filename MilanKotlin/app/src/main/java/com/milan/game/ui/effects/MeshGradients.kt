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
// ══════════════════════════════════════════════════════════════════════════════

/** 稀有度对应的 Mesh Gradient 颜色组合（16 顶点） */
private val rarityMeshColors = mapOf(
    4 to listOf(  // UR: 金色奢华
        Color(0xFFFFD700), Color(0xFFFFA500), Color(0xFFFFE4B5), Color(0xFFDAA520), // 行0
        Color(0xFFFFF8DC), Color(0xFFB8860B), Color(0xFFFFDAB9), Color(0xFFCD853F), // 行1
        Color(0xFFFFFACD), Color(0xFFDAA520), Color(0xFFFFE4B5), Color(0xFFB8860B), // 行2
        Color(0xFFFFDAB9), Color(0xFFCD853F), Color(0xFFFFF8DC), Color(0xFFFFD700), // 行3
    ),
    3 to listOf(  // SSR: 紫金神秘
        Color(0xFF9370DB), Color(0xFFBA55D3), Color(0xFFDDA0DD), Color(0xFF8A2BE2),
        Color(0xFFD8BFD8), Color(0xFF9932CC), Color(0xFFE6E6FA), Color(0xFF7B68EE),
        Color(0xFFDDA0DD), Color(0xFF9370DB), Color(0xFFBA55D3), Color(0xFF8A2BE2),
        Color(0xFFD8BFD8), Color(0xFF9932CC), Color(0xFFE6E6FA), Color(0xFF7B68EE),
    ),
    2 to listOf(  // SR: 蓝金精英
        Color(0xFF4169E1), Color(0xFF6495ED), Color(0xFF87CEEB), Color(0xFF1E90FF),
        Color(0xFFB0C4DE), Color(0xFF00BFFF), Color(0xFFADD8E6), Color(0xFF4682B4),
        Color(0xFFB0E0E6), Color(0xFF4169E1), Color(0xFF6495ED), Color(0xFF1E90FF),
        Color(0xFFB0C4DE), Color(0xFF00BFFF), Color(0xFFADD8E6), Color(0xFF4682B4),
    ),
    1 to listOf(  // R: 绿白清新
        Color(0xFF3CB371), Color(0xFF66CDAA), Color(0xFF8FBC8F), Color(0xFF2E8B57),
        Color(0xFF90EE90), Color(0xFF32CD32), Color(0xFF98FB98), Color(0xFF00FA9A),
        Color(0xFF00FF7F), Color(0xFF3CB371), Color(0xFF66CDAA), Color(0xFF2E8B57),
        Color(0xFF90EE90), Color(0xFF32CD32), Color(0xFF98FB98), Color(0xFF00FA9A),
    )
)

/** 元素对应的 Mesh Gradient 颜色组合（16 顶点） */
private val elementMeshColors = mapOf(
    "Metal" to listOf(
        Color(0xFFC0C0C0), Color(0xFFD3D3D3), Color(0xFFA9A9A9), Color(0xFFDCDCDC),
        Color(0xFFB0B0B0), Color(0xFFE8E8E8), Color(0xFF909090), Color(0xFFF0F0F0),
        Color(0xFFA0A0A0), Color(0xFFC0C0C0), Color(0xFFD3D3D3), Color(0xFFDCDCDC),
        Color(0xFFB0B0B0), Color(0xFFE8E8E8), Color(0xFF909090), Color(0xFFF0F0F0),
    ),
    "Wood" to listOf(
        Color(0xFF228B22), Color(0xFF32CD32), Color(0xFF006400), Color(0xFF90EE90),
        Color(0xFF2E8B57), Color(0xFF3CB371), Color(0xFF8FBC8F), Color(0xFF00FF7F),
        Color(0xFF98FB98), Color(0xFF228B22), Color(0xFF32CD32), Color(0xFF90EE90),
        Color(0xFF2E8B57), Color(0xFF3CB371), Color(0xFF8FBC8F), Color(0xFF00FF7F),
    ),
    "Water" to listOf(
        Color(0xFF1E90FF), Color(0xFF00BFFF), Color(0xFF87CEEB), Color(0xFF4169E1),
        Color(0xFF6495ED), Color(0xFFB0C4DE), Color(0xFFADD8E6), Color(0xFF00CED1),
        Color(0xFF40E0D0), Color(0xFF1E90FF), Color(0xFF00BFFF), Color(0xFF4169E1),
        Color(0xFF6495ED), Color(0xFFB0C4DE), Color(0xFFADD8E6), Color(0xFF00CED1),
    ),
    "Flame" to listOf(
        Color(0xFFFF4500), Color(0xFFFF6347), Color(0xFFFF7F50), Color(0xFFDC143C),
        Color(0xFFFF0000), Color(0xFFFF8C00), Color(0xFFFFD700), Color(0xFFFF69B4),
        Color(0xFFFF1493), Color(0xFFFF4500), Color(0xFFFF6347), Color(0xFFDC143C),
        Color(0xFFFF0000), Color(0xFFFF8C00), Color(0xFFFFD700), Color(0xFFFF69B4),
    ),
    "Earth" to listOf(
        Color(0xFF8B4513), Color(0xFFD2691E), Color(0xFFCD853F), Color(0xFFA0522D),
        Color(0xFFDEB887), Color(0xFFD2B48C), Color(0xFFBC8F8F), Color(0xFFF4A460),
        Color(0xFFDAA520), Color(0xFF8B4513), Color(0xFFD2691E), Color(0xFFA0522D),
        Color(0xFFDEB887), Color(0xFFD2B48C), Color(0xFFBC8F8F), Color(0xFFF4A460),
    ),
    "Light" to listOf(
        Color(0xFFFFFACD), Color(0xFFFFF8DC), Color(0xFFFAFAD2), Color(0xFFFFEFD5),
        Color(0xFFFFE4B5), Color(0xFFF0E68C), Color(0xFFEEE8AA), Color(0xFFBDB76B),
        Color(0xFFFFD700), Color(0xFFFFFACD), Color(0xFFFFF8DC), Color(0xFFFFEFD5),
        Color(0xFFFFE4B5), Color(0xFFF0E68C), Color(0xFFEEE8AA), Color(0xFFBDB76B),
    ),
    "Shadow" to listOf(
        Color(0xFF2F4F4F), Color(0xFF696969), Color(0xFF708090), Color(0xFF778899),
        Color(0xFF2C2C2C), Color(0xFF3C3C3C), Color(0xFF4A4A4A), Color(0xFF585858),
        Color(0xFF1C1C1C), Color(0xFF2F4F4F), Color(0xFF696969), Color(0xFF778899),
        Color(0xFF2C2C2C), Color(0xFF3C3C3C), Color(0xFF4A4A4A), Color(0xFF585858),
    ),
    "Thunder" to listOf(
        Color(0xFFFFD700), Color(0xFFFFA500), Color(0xFFFF8C00), Color(0xFFFFB347),
        Color(0xFFFFCC33), Color(0xFFE6BE8A), Color(0xFFDAA520), Color(0xFFFFC125),
        Color(0xFFFFB90F), Color(0xFFFFD700), Color(0xFFFFA500), Color(0xFFFFB347),
        Color(0xFFFFCC33), Color(0xFFE6BE8A), Color(0xFFDAA520), Color(0xFFFFC125),
    )
)

// ══════════════════════════════════════════════════════════════════════════════
// 公开 Composable 入口
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun RarityMeshGradient(
    rarity: Int,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    val colors = rarityMeshColors[rarity] ?: rarityMeshColors[1]!!
    MeshGradientBox(colors = colors, animated = animated, modifier = modifier)
}

@Composable
fun ElementMeshGradient(
    element: String,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    val colors = elementMeshColors[element] ?: elementMeshColors["Light"]!!
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
