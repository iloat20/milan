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

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .padding(5.dp)
            .clip(MaterialTheme.shapes.large)
            .background(AppTheme.Surface, MaterialTheme.shapes.large)
            .border(1.5.dp, rarityCol.copy(alpha = 0.6f), MaterialTheme.shapes.large)
            .pointerInput(Unit) {
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
        // ── 卡面主视觉：立绘占主导 ──
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
            Text(
                text = AppTheme.rarityName(rarity),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (rarity >= 4) AppTheme.GoldTextOn else AppTheme.BgDeepest,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(rarityCol.copy(alpha = 0.88f))
                    .border(0.5.dp, rarityCol, MaterialTheme.shapes.extraSmall)
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

        // ── 铭牌区：名字 / 称号 / footer ──
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
            footer?.invoke(this)
        }
    }
}
