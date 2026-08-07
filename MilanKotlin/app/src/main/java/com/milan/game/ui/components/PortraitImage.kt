package com.milan.game.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.milan.game.ui.theme.AppTheme

/**
 * 角色立绘（C# PortraitLoader 的 Compose 等价物）。
 *
 * 立绘资源名 = 角色 CharacterId（drawable/char_<rarity>_<pinyin>.png）。
 * data.json 有 56 个角色但 drawable 只有 28 张立绘 —— 缺图必须占位兜底，
 * 直接 painterResource 引用不存在的资源会抛 NotFoundException 闪退，
 * 故先用 getIdentifier 探测资源是否存在，缺失时渲染「稀有度渐变 + 角色名首字」。
 */
@Composable
fun PortraitImage(
    characterId: String,
    rarity: Int,
    name: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    // 资源名 = characterId；remember 缓存探测结果，避免重组反复查。
    // 探测成功直接得到资源 ID（painterResource 的 Int 重载），失败返回 0。
    val portraitId = remember(characterId) {
        context.resources.getIdentifier(characterId, "drawable", context.packageName)
    }

    if (portraitId != 0) {
        Image(
            painter = painterResource(portraitId),
            contentDescription = name ?: characterId,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        PortraitFallback(characterId, rarity, name, modifier)
    }
}

/** 缺图占位：稀有度径向渐变 + 首字（C# 占位语义：宁可难看也不能崩）。 */
@Composable
private fun PortraitFallback(
    characterId: String,
    rarity: Int,
    name: String?,
    modifier: Modifier,
) {
    val c = AppTheme.rarityColor(rarity)
    val initial = (name?.take(1) ?: characterId.take(1)).uppercase()
    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                listOf(c.copy(alpha = 0.30f), Color.Transparent),
                radius = 900f,
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = c.copy(alpha = 0.85f),
        )
    }
}
