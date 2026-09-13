package com.milan.game.ui.nav

import androidx.compose.material3.MaterialTheme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milan.game.services.EconomySlice
import com.milan.game.ui.formatCount
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 经济切片 CompositionLocal：由 MilanNavHost 在 ready 后 provide，Chrome 只订阅本地流。
 */
val LocalEconomySlice = compositionLocalOf<StateFlow<EconomySlice>> {
    MutableStateFlow(EconomySlice(0, 0, 0, 0))
}

/**
 * 统一顶栏（子页面用）：返回 + 标题 + 资源。
 * v4：静态标题，无金影、无呼吸浮动。
 */
@Composable
fun AppTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showResource: Boolean = true,
) {
    Row(
        modifier = modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(start = 8.dp, top = 8.dp, end = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onBack)
                .semantics { contentDescription = "返回" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                color = AppTheme.Text2,
            )
        }
        AnimatedContent(
            targetState = title,
            transitionSpec = {
                slideInHorizontally { it / 4 } + fadeIn(tween(200)) togetherWith
                    slideOutHorizontally { -it / 4 } + fadeOut(tween(150))
            },
            label = "topBarTitle",
        ) { t ->
            Text(
                text = t,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = AppTheme.Text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showResource) {
            Spacer(Modifier.weight(1f))
            ResourceBar(compact = true)
        }
    }
}

/**
 * 资源胶囊：字形 + 数值。
 * v4：无边框，仅极淡底。
 */
@Composable
fun ResourceBar(modifier: Modifier = Modifier, compact: Boolean = false) {
    val economyFlow = LocalEconomySlice.current
    val eco by economyFlow.collectAsStateWithLifecycle()

    Row(
        modifier = modifier
            .background(AppTheme.SurfaceNested, RoundedCornerShape(AppTheme.Roundness.lg))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Chip(com.milan.game.ui.theme.CurrencyNames.SOFT_GLYPH, eco.softCurrency, AppTheme.Gold)
        Spacer(Modifier.width(10.dp))
        Chip(com.milan.game.ui.theme.CurrencyNames.HARD_GLYPH, eco.hardCurrency, AppTheme.Frost)
        if (!compact) {
            Spacer(Modifier.width(10.dp))
            Chip(com.milan.game.ui.theme.CurrencyNames.FRAG_GLYPH, eco.starFragments, AppTheme.Frost)
            Spacer(Modifier.width(10.dp))
            Chip("⚔", eco.battleTickets, AppTheme.Text2)
        }
    }
}

/** 单资源条目：字形 + 数值。数值变化 400ms 动画。 */
@Composable
private fun Chip(glyph: String, value: Int, color: Color) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = tween(400),
        label = "chipValue",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics {
            contentDescription = "$glyph $value"
        },
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            text = formatCount(animated),
            fontWeight = FontWeight.Medium,
            color = AppTheme.Text1,
        )
    }
}
