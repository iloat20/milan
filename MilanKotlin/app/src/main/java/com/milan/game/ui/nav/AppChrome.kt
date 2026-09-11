package com.milan.game.ui.nav

import androidx.compose.material3.MaterialTheme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import kotlin.math.sin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milan.game.services.EconomySlice
import com.milan.game.ui.formatCount
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 经济切片 CompositionLocal（2026-09-10）：
 * ResourceBar 跨多个 Screen 复用，此前直接摸 `AppGraph.service.economy`（服务定位器泄漏）。
 * 现由 MilanNavHost 在 ready 后一次性 provide，Chrome 组件只订阅本地流。
 */
val LocalEconomySlice = compositionLocalOf<StateFlow<EconomySlice>> {
    MutableStateFlow(EconomySlice(0, 0, 0, 0))
}

/**
 * 统一顶栏（子页面用）：返回箭头 + 标题 + 资源胶囊（C# AppChrome.AppTopBar 翻译）。
 * 主页不需要。返回动作由宿主决定（切回主页 / 收起子页）。
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
            .padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 返回箭头（P2-7 无障碍：48dp 尺寸保证触控热区 ≥48dp，等效 minimumInteractiveComponentSize；
        // P2-6：补 contentDescription，TalkBack 不再读裸字形「‹」）
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
                fontWeight = FontWeight.Bold,
                color = AppTheme.Gold,
            )
        }
        AnimatedContent(
            targetState = title,
            transitionSpec = {
                slideInHorizontally { it / 3 } + fadeIn(tween(250)) togetherWith
                    slideOutHorizontally { -it / 3 } + fadeOut(tween(200))
            },
            label = "topBarTitle",
        ) { t ->
            // 水墨浮动：标题文字以 3s 周期微幅上下浮动 ±1.5dp，如墨滴在水面轻荡
            val infiniteTransition = rememberInfiniteTransition(label = "titleFloat")
            val phase by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 6.2832f, // 2π
                animationSpec = InfiniteRepeatableSpec(
                    animation = tween(durationMillis = 3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "titlePhase",
            )
            Text(
                text = t,
                style = MaterialTheme.typography.titleLarge.copy(
                    shadow = Shadow(
                        color = AppTheme.Gold.copy(alpha = 0.6f),
                        blurRadius = 8f,
                        offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                    ),
                ),
                fontWeight = FontWeight.Bold,
                color = AppTheme.Text1,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer {
                    // 水墨呼吸：透明度在 0.84~1.0 间波动
                    alpha = 0.92f + sin(phase) * 0.08f
                },
            )
        }
        if (showResource) {
            Spacer(Modifier.weight(1f))
            // 顶栏空间紧：compact 只显示双货币；完整四资源见 Home/Gacha 与 Shop 面板
            ResourceBar(compact = true)
        }
    }
}

/**
 * 资源胶囊：✦ 星尘 / ◆ 钻石 / ❖ 星魂碎片 / ⚔ 战票（与 Shop ResourcePanel 同符号口径）。
 * 主页与子页面复用。订阅 **economy 切片**；[compact] 时仅显示双货币（窄顶栏）。
 * 2026-09-10：补齐碎片/战票，消除「抽卡消耗星尘时看不到碎片」与商店面板口径分裂。
 */
@Composable
fun ResourceBar(modifier: Modifier = Modifier, compact: Boolean = false) {
    val economyFlow = LocalEconomySlice.current
    val eco by economyFlow.collectAsStateWithLifecycle()

    Row(
        modifier = modifier
            .background(AppTheme.Surface, RoundedCornerShape(AppTheme.Roundness.lg))
            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(AppTheme.Roundness.lg))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Chip("✦", eco.softCurrency, AppTheme.Gold)
        Spacer(Modifier.width(8.dp))
        Chip("◆", eco.hardCurrency, AppTheme.Frost)
        if (!compact) {
            Spacer(Modifier.width(8.dp))
            Chip("❖", eco.starFragments, AppTheme.Frost)
            Spacer(Modifier.width(8.dp))
            Chip("⚔", eco.battleTickets, AppTheme.Text2)
        }
    }
}

/** 单资源条目：字形 + 数值（C# Chip）。数值变化时 400ms 滚动动画（P1-4 数值反馈）。 */
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
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            text = formatCount(animated),
            fontWeight = FontWeight.Bold,
            color = AppTheme.Text1,
        )
    }
}
