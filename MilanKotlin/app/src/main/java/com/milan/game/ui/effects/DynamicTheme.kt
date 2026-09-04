package com.milan.game.ui.effects

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ══════════════════════════════════════════════════════════════════════════════
// 动态主题系统 — 元素/稀有度驱动的色彩变幻
// ══════════════════════════════════════════════════════════════════════════════

/**
 * 元素主题色容器（通过 [LocalElementColors] 注入组合树）。
 *
 * @property primary 主元素色（大面积底色/渐变起点）
 * @property secondary 辅助色（渐变终点/强调色）
 * @property glow 发光色（粒子/光效/描边）
 * @property brush 背景渐变画刷（primary → secondary 垂直渐变）
 */
@Immutable
data class ElementColors(
    val primary: Color = Color.Unspecified,
    val secondary: Color = Color.Unspecified,
    val glow: Color = Color.Unspecified,
    val brush: Brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
)

/** 组合局部：当前元素主题色。 */
val LocalElementColors = compositionLocalOf { ElementColors() }

// ══════════════════════════════════════════════════════════════════════════════
// 元素色表（水墨国风配色）
// ══════════════════════════════════════════════════════════════════════════════

/**
 * 元素 → [ElementColors] 映射表。
 *
 * 色彩灵感来源：中国传统五行 + 光暗雷，采用水墨画色彩体系（降低饱和度、提亮明度）。
 */
private val ElementColorMap = mapOf(
    "Metal"   to ElementColors(Color(0xFFC0C0C0), Color(0xFFFFD700), Color(0xFFE8E8E8)),
    "Wood"    to ElementColors(Color(0xFF228B22), Color(0xFF90EE90), Color(0xFF4CAF50)),
    "Water"   to ElementColors(Color(0xFF1E90FF), Color(0xFF87CEEB), Color(0xFF42A5F5)),
    "Flame"   to ElementColors(Color(0xFFFF4500), Color(0xFFFF6347), Color(0xFFFF5722)),
    "Earth"   to ElementColors(Color(0xFF8B4513), Color(0xFFD2691E), Color(0xFFA1887F)),
    "Light"   to ElementColors(Color(0xFFFFFACD), Color(0xFFFFD700), Color(0xFFFFF176)),
    "Shadow"  to ElementColors(Color(0xFF2F4F4F), Color(0xFF696969), Color(0xFF455A64)),
    "Thunder" to ElementColors(Color(0xFFFFD700), Color(0xFFFFA500), Color(0xFFFFB300)),
)

/** 默认元素色（无匹配时回退）。 */
private val DefaultElementColors = ElementColors(
    Color(0xFF666666), Color(0xFF999999), Color(0xFFAAAAAA),
)

// ══════════════════════════════════════════════════════════════════════════════
// DynamicTheme Composable
// ══════════════════════════════════════════════════════════════════════════════

/**
 * 动态主题包装器——根据游戏元素和稀有度动态改变子树配色。
 *
 * 用法：
 * ```kotlin
 * DynamicTheme(element = "Flame", rarity = 4) {
 *     // 子树内可通过 LocalElementColors.current 获取元素色
 *     Text("UR 火焰角色", color = LocalElementColors.current.glow)
 * }
 * ```
 *
 * @param element 游戏元素名称（"Metal"/"Wood"/"Water"/"Flame"/"Earth"/"Light"/"Shadow"/"Thunder"）
 * @param rarity 稀有度等级（1=R, 2=SR, 3=SSR, 4=UR），影响发光强度
 * @param active 是否激活色彩偏移（false 时透传内容，无色彩叠加）
 * @param modifier 修饰符
 * @param content 子树内容
 */
@Composable
fun DynamicTheme(
    element: String,
    rarity: Int = 1,
    active: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val baseColors = ElementColorMap[element] ?: DefaultElementColors

    // 稀有度强度系数：UR=1.0, SSR=0.8, SR=0.6, R=0.4
    val intensity = when (rarity) {
        4 -> 1.0f
        3 -> 0.8f
        2 -> 0.6f
        else -> 0.4f
    }

    // 动画过渡（300ms 柔和渐变）
    val animDuration = 300
    val targetPrimary = if (active) baseColors.primary else Color.Transparent
    val targetSecondary = if (active) baseColors.secondary else Color.Transparent
    val targetGlow = if (active) baseColors.glow else Color.Transparent

    val animPrimary by animateColorAsState(targetPrimary, tween(animDuration), label = "dynPrimary")
    val animSecondary by animateColorAsState(targetSecondary, tween(animDuration), label = "dynSecondary")
    val animGlow by animateColorAsState(targetGlow, tween(animDuration), label = "dynGlow")

    // 按强度混合颜色（强度越低越透明）
    val mixedPrimary = animPrimary.copy(alpha = animPrimary.alpha * intensity)
    val mixedSecondary = animSecondary.copy(alpha = animSecondary.alpha * intensity)
    val mixedGlow = animGlow.copy(alpha = animGlow.alpha * intensity)

    val colors = remember(mixedPrimary, mixedSecondary, mixedGlow) {
        ElementColors(
            primary = mixedPrimary,
            secondary = mixedSecondary,
            glow = mixedGlow,
            brush = Brush.verticalGradient(listOf(mixedPrimary, mixedSecondary)),
        )
    }

    CompositionLocalProvider(LocalElementColors provides colors) {
        Box(modifier = modifier) {
            content()
        }
    }
}

/**
 * 便捷扩展：在任意组合函数中快速获取当前元素色。
 *
 * ```kotlin
 * @Composable
 * fun MyComponent() {
 *     val colors = currentElementColors()
 *     Box(Modifier.background(colors.brush))
 * }
 * ```
 */
@Composable
@ReadOnlyComposable
fun currentElementColors(): ElementColors = LocalElementColors.current
