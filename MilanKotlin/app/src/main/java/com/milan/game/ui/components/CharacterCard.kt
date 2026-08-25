package com.milan.game.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.LocalSharedTransitionScope
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme

/**
 * 角色卡（R1/I1 统一：DeckCard / ListCard / CollectionCard 三份逐字重复模板收敛至此）。
 *
 * 2026-08 卡牌化重构（对标主流卡牌的呈现惯例——立绘即卡面）：
 * - 立绘占卡面主体：竖版定比（aspectRatio 0.82），不再是小缩略图条；
 * - 元素渐变打底（from→to 对角渐入 Surface），替代纯色淡底；
 * - 稀有度角标（左上胶囊）+ 元素徽章（右上圆片）悬浮在卡面上，替代文本行罗列；
 * - 卡面底部暗化渐变承托视觉重心；铭牌区收进下栏（名字/称号/footer 插槽）。
 *
 * 差异走参数：
 *  - [locked] 未拥有蒙层（黑底 + 锁，立绘成剪影，图鉴页用）；
 *  - [animatedVisibilityScope] 共享元素过渡作用域（宿主页 NavHost composable 的 `this`；
 *    传 null 安全降级为普通渲染——旧接入点不传也不崩）；
 *  - [footer] 铭牌区末行插槽（星级 / 「未获得」，可为 null）。
 * 共享元素 key 与详情页 Hero 同约定：`portrait_<characterId>`（全 app 唯一，见 CharacterDetailScreen.HeroRegion）。
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

    Column(
        modifier = modifier
            .padding(5.dp) // C# margin 5dp
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.Surface, RoundedCornerShape(16.dp))
            .border(2.dp, rarityCol, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        // ── 卡面主视觉：立绘占主导（C# 卡牌语义：art 即 card face）──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .then(portraitModifier),
        ) {
            // 元素渐变打底（缺图回退时它就是底色）
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                elem.from.copy(alpha = 0.34f),
                                elem.to.copy(alpha = 0.12f),
                                AppTheme.Surface,
                            ),
                        ),
                    ),
            )
            // 立绘（Crop 填满卡面；缺图回退稀有度渐变 + 首字）
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

            // 稀有度角标（左上胶囊）
            Text(
                text = AppTheme.rarityName(rarity),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.BgDeepest,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(rarityCol.copy(alpha = 0.88f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )

            // 元素徽章（右上圆片）
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, elem.glow.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = elem.glyph, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = elem.glow)
            }

            // 底部暗化渐变（承托铭牌视觉重心）
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        ),
                    ),
            )

            if (locked) {
                // 未收录：黑蒙层 + 锁（立绘成剪影）；盖住角标之上保持剪影语义
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

        // ── 铭牌区：名字 / 称号 / footer（星级等）──
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = title,
                fontSize = 11.sp,
                color = AppTheme.Text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            footer?.invoke(this)
        }
    }
}
