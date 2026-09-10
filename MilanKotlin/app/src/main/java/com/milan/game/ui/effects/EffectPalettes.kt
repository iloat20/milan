package com.milan.game.ui.effects

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme

/**
 * 特效层取色派生器（设计语言 v3 §5.1：全站唯一取色口）。
 *
 * 稀有度只认 [AppTheme.rarityColor] / [AppTheme.rarityGradient]，
 * 元素只认 [ElementTheme.forElement]；Mesh Gradient / 粒子所需的
 * 多级色列一律从权威色板按明度派生，**禁止在本层硬编码色值**。
 *
 * 历史坑因：旧「原神风」特效配色（UR #FFD700 / SSR #9370DB / SR #4169E1 / R #3CB371
 * 与旧元素表）曾散落在 MeshGradients / ParticleSystem / DynamicTheme 三处，
 * 与现行稀有度口径（月白/石青/朱砂/金箔）直接冲突——抽卡演出全屏 Mesh Gradient
 * 会闪出另一套色系。2026-09-09 P0 清债统一到本文件派生。
 */

/** 粒子特效色列（星爆 / 光柱 / 元素粒子）：深 → 基准 → 提亮 → 白。 */
internal fun rarityEffectColors(rarity: Int): List<Color> {
    val base = AppTheme.rarityColor(rarity)
    val grad = AppTheme.rarityGradient(rarity)
    return listOf(grad.first(), base, lerp(base, Color.White, 0.45f), Color.White)
}

/** 元素粒子色列：矿物深色 → 亮色 → 辉光 → 白。 */
internal fun elementEffectColors(element: String): List<Color> {
    val id = ElementTheme.forElement(element)
    return listOf(id.from, id.to, id.glow, Color.White)
}

/** Mesh Gradient 16 顶点色（4×4 行优先）：稀有度基准色派生。 */
internal fun rarityMeshColors16(rarity: Int): List<Color> {
    val base = AppTheme.rarityColor(rarity)
    val grad = AppTheme.rarityGradient(rarity)
    val deep = grad.first()
    val hi = grad.last()
    val light = lerp(base, Color.White, 0.45f)
    val pale = lerp(base, Color.White, 0.72f)
    val shadow = lerp(deep, Color.Black, 0.25f)
    return listOf(
        deep, base, light, shadow,   // 行0
        pale, deep, light, base,     // 行1
        light, deep, base, shadow,   // 行2
        pale, base, pale, deep,      // 行3
    )
}

/** Mesh Gradient 16 顶点色（4×4 行优先）：元素签名渐变派生。 */
internal fun elementMeshColors16(element: String): List<Color> {
    val id = ElementTheme.forElement(element)
    val light = lerp(id.to, Color.White, 0.5f)
    val deep = lerp(id.from, Color.Black, 0.25f)
    return listOf(
        id.from, id.glow, id.to, deep,   // 行0
        light, id.from, id.to, id.glow,  // 行1
        light, id.from, id.glow, deep,   // 行2
        light, id.to, light, id.from,    // 行3
    )
}
