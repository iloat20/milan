package com.milan.game.ui.components

import androidx.compose.material3.MaterialTheme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.delay

/**
 * 列表入场动效（2026-08 UI 现代化：对齐 Home/Gacha 已有的动效语言，铺到其余列表页）。
 *
 * 交错淡入 + 上浮：index 越大延迟越长（8 档封顶，长列表尾部不再等待）。
 * appear 标记以组合身份 remember——滚动复用同一条目时不重放动画。
 */
@Composable
fun EntranceItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(8)) * 40L)
        appeared = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = LinearEasing),
        label = "entranceAlpha",
    )
    val translation by animateFloatAsState(
        targetValue = if (appeared) 0f else 26f,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "entranceTranslate",
    )
    Box(
        modifier
            .graphicsLayer {
                this.alpha = alpha
                this.translationY = translation
            }
    ) {
        content()
    }
}

/**
 * 渐变徽章（2026-08 UI 现代化）：替换商店/成就/塔页散落的 emoji 字形（✦◆❖⚔★）。
 * 深色玻璃圆底 + 双色渐变描边 + 中心字形，尺寸小（26dp），不喧宾夺主。
 */
@Composable
fun GlyphBadge(
    glyph: String,
    from: Color,
    to: Color,
    modifier: Modifier = Modifier,
    glyphColor: Color = AppTheme.Text1,
) {
    Box(
        modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(from.copy(alpha = 0.22f), to.copy(alpha = 0.22f))))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(from.copy(alpha = 0.75f), to.copy(alpha = 0.45f))),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = glyphColor,
        )
    }
}
