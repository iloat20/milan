package com.milan.game.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.theme.AppTheme

/**
 * 角色立绘（C# PortraitLoader 的 Compose 等价物，2026-08-08 低端机性能优化异步化）。
 *
 * 立绘资源名 = 角色 CharacterId（drawable-nodpi/char_*.webp，当前 31 角 31 图，另有 avatar_*.webp）。
 * 缺图仍必须占位兜底：直接 painterResource 引用不存在的资源会抛 NotFoundException 闪退，
 * 故先用 getIdentifier 探测资源是否存在，缺失时渲染「稀有度渐变 + 角色名首字」。
 *
 * 加载路径（性能优化）：getIdentifier 探测 → PortraitLoader 在 IO 线程按
 * [PortraitTarget] 采样解码（默认 Full=2x）→ 缓存命中直取；解码期间与失败
 * 均渲染占位，成功通过 Crossfade 淡入立绘。首帧不再同步解码大图。
 *
 * @param aura 是否在立绘脚下叠加稀有度氛围圈（v2 工程配套，默认关）。
 *            开启后内部用 Box 包裹：AuraHalo 作底层 + 立绘居中。
 */
@SuppressLint("DiscouragedApi") // 动态探测为设计使然：data.json 56 角色 / drawable 仅 28 张，缺图走占位
@Composable
fun PortraitImage(
    characterId: String,
    rarity: Int,
    modifier: Modifier = Modifier,
    name: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    target: PortraitTarget = PortraitTarget.Full,
    aura: Boolean = false,
    glowScale: Float = 1f,
) {
    if (aura) {
        Box(modifier) {
            AuraHalo(rarity = rarity, glowScale = glowScale, modifier = Modifier.fillMaxSize())
            PortraitImageContent(
                characterId = characterId,
                rarity = rarity,
                name = name,
                contentScale = contentScale,
                target = target,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        PortraitImageContent(
            characterId = characterId,
            rarity = rarity,
            name = name,
            contentScale = contentScale,
            target = target,
            modifier = modifier,
        )
    }
}

/** 立绘渲染核心（不含氛围圈）。原 PortraitImage 逻辑整体下沉于此。 */
@SuppressLint("DiscouragedApi")
@Composable
private fun PortraitImageContent(
    characterId: String,
    rarity: Int,
    modifier: Modifier,
    name: String?,
    contentScale: ContentScale,
    target: PortraitTarget,
) {
    val resources = LocalResources.current
    val context = LocalContext.current
    // 仅 Avatar 档优先专用裁脸资源；Thumb 仍采样全立绘（卡面构图需要）
    val portraitId = remember(characterId, target) {
        if (target == PortraitTarget.Avatar) {
            val avatarName = PortraitLoader.avatarResourceNameOf(characterId)
            val avatarId = PortraitLoader.resourceIdOf(resources, context.packageName, avatarName)
            if (avatarId != 0) avatarId
            else PortraitLoader.resourceIdOf(resources, context.packageName, characterId)
        } else {
            PortraitLoader.resourceIdOf(resources, context.packageName, characterId)
        }
    }

    if (portraitId == 0) {
        PortraitFallback(characterId, rarity, name, modifier)
        return
    }

    // LRU 同步命中时直接上屏（跳过 Crossfade 占位闪烁）。
    val cachedNow = remember(portraitId, target) { PortraitLoader.peek(portraitId, target) }
    if (cachedNow != null && !cachedNow.isRecycled) {
        ColorPortrait(
            bitmap = cachedNow,
            contentDescription = name ?: characterId,
            modifier = modifier,
            contentScale = contentScale,
        )
        return
    }

    val bitmap by produceState<Bitmap?>(initialValue = null, portraitId, target) {
        value = null
        value = PortraitLoader.load(resources, portraitId, target)
    }

    Crossfade(targetState = bitmap, animationSpec = tween(120), label = "portrait") { bmp ->
        if (bmp != null && !bmp.isRecycled) {
            ColorPortrait(
                bitmap = bmp,
                contentDescription = name ?: characterId,
                modifier = modifier,
                contentScale = contentScale,
            )
        } else {
            PortraitFallback(characterId, rarity, name, modifier)
        }
    }
}

/**
 * 全彩立绘（v4）：直接 Compose Image 绘制，不再叠水墨 ColorMatrix。
 * 旧 InkWashPortrait 把蓝/绿通道压到 0.25/0.38，全站立绘发灰；已废弃。
 */
@Composable
private fun ColorPortrait(
    bitmap: Bitmap,
    contentDescription: String,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

/**
 * 缺图占位（水墨画升级版）：多层墨迹溅射 + 书法首字 + 稀有度光晕 + 隔水金线。
 *
 * 设计意图：缺图不是「错误」，是「水墨留白」——用墨迹的浓淡层次暗示角色存在，
 * 书法首字点名身份，金线隔水框住画面，整体保持装裱册页的一致语言。
 */
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
        modifier = modifier.background(AppTheme.BgDeepest),
        contentAlignment = Alignment.Center,
    ) {
        // 多层墨迹溅射（模拟毛笔落纸的自然扩散）
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = minOf(cx, cy).coerceAtLeast(1f)

            // 第 1 层：极淡外围水痕（墨汁扩散最远处）
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c.copy(alpha = 0.04f), c.copy(alpha = 0.12f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    radius = maxR,
                ),
                radius = maxR,
            )
            // 第 2 层：中浓度墨晕（笔腹含墨处）
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c.copy(alpha = 0.10f), c.copy(alpha = 0.25f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(cx * 0.95f, cy * 1.02f),
                    radius = maxR * 0.68f,
                ),
                radius = maxR * 0.68f,
            )
            // 第 3 层：浓墨核心（笔尖着纸处）
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c.copy(alpha = 0.32f), c.copy(alpha = 0.06f)),
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    radius = (maxR * 0.36f).coerceAtLeast(1f),
                ),
                radius = (maxR * 0.36f).coerceAtLeast(1f),
            )
        }

        // 稀有度光晕（脚下椭圆光环，与 AuraHalo 同语言）
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.78f
            val rx = size.width * 0.38f
            val ry = size.height * 0.12f
            drawOval(
                brush = Brush.radialGradient(
                    listOf(c.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = rx.coerceAtLeast(1f),
                ),
                topLeft = Offset(cx - rx, cy - ry),
                size = Size(rx * 2f, ry * 2f),
            )
        }

        // 书法首字
        Text(
            text = initial,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = c.copy(alpha = 0.85f),
        )

        // 隔水金线（画心与装裱边分隔，与 CharacterCard 同语言）
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .then(
                    Modifier.drawBehind {
                        drawRect(
                            color = AppTheme.Gold.copy(alpha = 0.18f),
                            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                            size = Size(
                                size.width - 2.dp.toPx(),
                                size.height - 2.dp.toPx(),
                            ),
                        )
                    }
                ),
        )
    }
}
