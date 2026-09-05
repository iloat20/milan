package com.milan.game.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.LocalSharedTransitionScope
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme
import kotlinx.coroutines.launch

/**
 * 角色卡 — 水墨国风版（宣纸底 + 印章稀有度 + 金箔边框）。
 *
 * 2026-08 水墨国风重构：
 * - 卡面底色从暗紫玻璃切换到墨色宣纸感；
 * - 边框从纯色切换到金箔描边（UR 时增强）；
 * - 稀有度角标改为朱砂/金箔印章风格；
 * - 元素渐变打底保留但降低饱和度适配水墨调性。
 */
@Composable
fun CharacterCard(
    characterId: String,
    name: String,
    title: String,
    rarity: Int,
    element: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val rarityCol = AppTheme.rarityColor(rarity)
    val elem = ElementTheme.forElement(element)
    val sharedScope = LocalSharedTransitionScope.current
    val portraitModifier = if (sharedScope != null && animatedVisibilityScope != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "portrait_$characterId"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            )
        }
    } else Modifier

    // ── 按压缩放动画（水墨卡牌微交互）──
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // ── 双层描边（装裱册页：外层稀有度色 + 内层金箔隔水线）──
    // 外框粗细按稀有度分级：UR 2dp / SSR 1.5dp / SR 1dp / R 0.75dp
    val outerBorder = when {
        rarity >= 4 -> 2.dp
        rarity == 3 -> 1.5.dp
        rarity == 2 -> 1.dp
        else -> 0.75.dp
    }
    val outerBorderAlpha = when {
        rarity >= 4 -> 0.85f
        rarity == 3 -> 0.75f
        else -> 0.6f
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .padding(outerBorder / 2)
            .clip(MaterialTheme.shapes.large)
            .background(rarityCol.copy(alpha = outerBorderAlpha), MaterialTheme.shapes.large)
            .border(outerBorder, rarityCol.copy(alpha = outerBorderAlpha), MaterialTheme.shapes.large)
            .padding(2.5.dp)
            .clip(MaterialTheme.shapes.large)
            .border(1.dp, AppTheme.Gold.copy(alpha = 0.30f), MaterialTheme.shapes.large)
            .pointerInput(characterId) {
                detectTapGestures(
                    onPress = {
                        scope.launch { scale.animateTo(0.96f, spring()) }
                        tryAwaitRelease()
                        scope.launch { scale.animateTo(1f, spring()) }
                        onClick()
                    }
                )
            },
    ) {
        // ── 卡面主视觉：立绘占主导（装裱册页：画心 + 隔水金线内框）──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .then(portraitModifier),
        ) {
            // 元素渐变打底（水墨风：降低饱和度）
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                elem.from.copy(alpha = 0.28f),
                                elem.to.copy(alpha = 0.10f),
                                AppTheme.Surface,
                            ),
                        ),
                    ),
            )
            // 立绘
            PortraitImage(
                characterId = characterId,
                rarity = rarity,
                name = name,
                modifier = Modifier.fillMaxSize(),
                target = PortraitTarget.Thumb,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                aura = true,
                glowScale = 0.7f,
            )

            // 稀有度角标（印章风格：圆角 + 金箔/朱砂底色）
            // UR: 1.15x 缩放 + 1dp 描边（更醒目）
            // SSR: 全 alpha 正常尺寸
            // R: 0.6 alpha 低调处理
            val rarityBadgeScale = when {
                rarity >= 4 -> 1.15f // UR
                rarity == 3 -> 1f    // SSR
                else -> 0.85f        // SR/R
            }
            val rarityBadgeAlpha = when {
                rarity >= 4 -> 1f    // UR
                rarity == 3 -> 1f    // SSR
                rarity == 2 -> 0.85f // SR
                else -> 0.6f         // R
            }
            val rarityBadgeBorder = when {
                rarity >= 4 -> 1.dp  // UR: 加粗描边
                else -> 0.5.dp
            }
            Text(
                text = AppTheme.rarityName(rarity),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (rarity >= 4) AppTheme.GoldTextOn else AppTheme.BgDeepest,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .graphicsLayer {
                        scaleX = rarityBadgeScale
                        scaleY = rarityBadgeScale
                        alpha = rarityBadgeAlpha
                    }
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(rarityCol.copy(alpha = 0.88f))
                    .border(rarityBadgeBorder, rarityCol, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )

            // 元素徽章（右上圆片）
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(AppTheme.BgDeepest.copy(alpha = 0.5f))
                    .border(1.dp, elem.glow.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = elem.glyph, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = elem.glow)
            }

            // 底部暗化渐变
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, AppTheme.BgDeepest.copy(alpha = 0.7f)),
                        ),
                    ),
            )

            if (locked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "🔒", fontSize = 18.sp)
                }
            }
        }

        // ── 铭牌区：名字 / 称号 / rarity 标签 / footer ──
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = AppTheme.Text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.Text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 稀有度文字标签（底部铭牌区，与顶部印章呼应）
            Text(
                text = AppTheme.rarityName(rarity),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = rarityCol,
                modifier = Modifier.padding(top = 2.dp),
            )
            footer?.invoke(this)
        }
    }
}
