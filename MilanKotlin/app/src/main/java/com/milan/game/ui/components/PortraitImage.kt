package com.milan.game.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.milan.game.ui.theme.AppTheme

/**
 * 角色立绘（C# PortraitLoader 的 Compose 等价物，2026-08-08 低端机性能优化异步化）。
 *
 * 立绘资源名 = 角色 CharacterId（drawable/char_<rarity>_<pinyin>.png）。
 * data.json 有 56 个角色但 drawable 只有 28 张立绘 —— 缺图必须占位兜底，
 * 直接 painterResource 引用不存在的资源会抛 NotFoundException 闪退，
 * 故先用 getIdentifier 探测资源是否存在，缺失时渲染「稀有度渐变 + 角色名首字」。
 *
 * 加载路径（性能优化）：getIdentifier 探测 → PortraitLoader 在 IO 线程按
 * [PortraitTarget] 采样解码（默认 Full=2x）→ 缓存命中直取；解码期间与失败
 * 均渲染占位，成功通过 Crossfade 淡入立绘。首帧不再同步解码 3.8MB 大图。
 */
@Composable
fun PortraitImage(
    characterId: String,
    rarity: Int,
    name: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    target: PortraitTarget = PortraitTarget.Full,
) {
    val context = LocalContext.current
    // 资源名 = characterId；remember 缓存探测结果，避免重组反复查。
    // 探测成功直接得到资源 ID，失败返回 0（直接走占位，不发起无谓解码）。
    val portraitId = remember(characterId) {
        context.resources.getIdentifier(characterId, "drawable", context.packageName)
    }

    if (portraitId == 0) {
        PortraitFallback(characterId, rarity, name, modifier)
        return
    }

    val bitmap by produceState<Bitmap?>(initialValue = null, portraitId, target) {
        value = PortraitLoader.load(context.resources, portraitId, target)
    }

    Crossfade(targetState = bitmap, label = "portrait") { bmp ->
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = name ?: characterId,
                modifier = modifier,
                contentScale = contentScale,
            )
        } else {
            // 解码中 / 解码失败：占位兜底（宁可难看也不能崩）。
            PortraitFallback(characterId, rarity, name, modifier)
        }
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
