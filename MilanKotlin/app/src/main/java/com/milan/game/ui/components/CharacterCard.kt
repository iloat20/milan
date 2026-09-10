package com.milan.game.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milan.game.ui.LocalSharedTransitionScope
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme

/**
 * 角色收藏卡 — 实体 TCG 版式（2026-09-09）。
 *
 * ```
 * ┌────────────────────┐  CodexCard 纸厚 + 工艺框
 * │  [R]        [火]   │  稀有度章 + 元素徽
 * │ ┌────────────────┐ │
 * │ │                │ │  画心（立绘）
 * │ │      立绘      │ │
 * │ │                │ │
 * │ └────────────────┘ │
 * │ ── 鎏金分隔 ──      │
 * │  烛龙              │  名
 * │  昼夜之主          │  称号
 * │  ●●●●              │  稀有度点
 * └────────────────────┘
 * ```
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

    CodexCard(
        tier = rarity,
        modifier = modifier,
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // 顶栏：稀有度 + 元素
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RarityChip(rarity, rarityCol)
                Spacer(Modifier.weight(1f))
                ElementDot(elem.glyph, elem.glow)
            }

            // 画心窗（内衬金线）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .aspectRatio(CardMetrics.Aspect)
                    .clip(RoundedCornerShape(4.dp))
                    .border(0.75.dp, AppTheme.Gold.copy(alpha = 0.28f), RoundedCornerShape(4.dp))
                    .then(portraitModifier),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    elem.from.copy(alpha = 0.22f),
                                    AppTheme.BgDeepest.copy(alpha = 0.4f),
                                )
                            )
                        )
                )
                PortraitImage(
                    characterId = characterId,
                    rarity = rarity,
                    name = name,
                    modifier = Modifier.fillMaxSize(),
                    target = PortraitTarget.Full,
                    contentScale = ContentScale.Crop,
                    aura = true,
                    glowScale = 0.65f,
                )
                // 画心底衬渐变
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, AppTheme.BgDeepest.copy(alpha = 0.75f))
                            )
                        )
                )
                if (locked) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "未获得",
                            tint = AppTheme.Text2,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // 鎏金分隔线
            Box(
                Modifier
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                AppTheme.Gold.copy(alpha = 0.55f),
                                Color.Transparent,
                            )
                        )
                    )
            )

            // 铭牌
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 10.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
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
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 稀有度点：数量 = 星级
                    repeat(rarity) {
                        Box(
                            Modifier
                                .padding(end = 3.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(rarityCol)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = AppTheme.rarityName(rarity),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = rarityCol,
                    )
                }
                footer?.invoke(this)
            }
        }
    }
}

@Composable
private fun RarityChip(rarity: Int, col: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(col.copy(alpha = 0.9f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = AppTheme.rarityName(rarity),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (rarity >= 4) AppTheme.GoldTextOn else AppTheme.BgDeepest,
        )
    }
}

@Composable
private fun ElementDot(glyph: String, glow: Color) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(AppTheme.BgDeepest.copy(alpha = 0.65f))
            .border(1.dp, glow.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = glow,
        )
    }
}
