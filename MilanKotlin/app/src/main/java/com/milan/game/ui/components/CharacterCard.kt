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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * 外层稀有度描边圆角 + 内层 Surface 面板 + 立绘框（元素淡底 + 共享元素过渡）+ 名称/称号 + 稀有度/元素行。
 * 差异走参数：
 *  - [locked] 未拥有蒙层（黑底 + 锁，立绘成剪影，图鉴页用）；
 *  - [animatedVisibilityScope] 共享元素过渡作用域（宿主页 NavHost composable 的 `this`；
 *    传 null 安全降级为普通渲染——旧接入点不传也不崩）；
 *  - [footer] 末行插槽（星级 / 「未获得」，可为 null）。
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
            .clip(RoundedCornerShape(18.dp))
            .background(rarityCol.copy(alpha = 110f / 255f), RoundedCornerShape(18.dp))
            .border(2.dp, rarityCol, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AppTheme.Surface, RoundedCornerShape(16.dp))
                .padding(12.dp),
        ) {
            // 立绘框：元素淡色底 + 头像（缺图时回退稀有度渐变 + 首字）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(elem.from.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                    .then(portraitModifier),
            ) {
                PortraitImage(
                    characterId = characterId,
                    rarity = rarity,
                    name = name,
                    modifier = Modifier.fillMaxSize(),
                    target = PortraitTarget.Thumb,
                    aura = true,
                    glowScale = 0.7f,
                )
                if (locked) {
                    // 未收录：黑蒙层 + 锁（立绘成剪影）
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "🔒", fontSize = 16.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
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
            Row(Modifier.padding(top = 2.dp)) {
                Text(
                    text = AppTheme.rarityName(rarity),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = rarityCol,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(text = elem.glyph + " " + element, fontSize = 11.sp, color = elem.from)
            }
            footer?.invoke(this)
        }
    }
}
