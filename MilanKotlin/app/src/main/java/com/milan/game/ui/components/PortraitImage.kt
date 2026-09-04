package com.milan.game.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
    val portraitId = remember(characterId) {
        // I11 补充：进程级记忆化（PortraitLoader.resourceIdOf），Lazy 网格不再每次反射查表
        PortraitLoader.resourceIdOf(resources, context.packageName, characterId)
    }

    if (portraitId == 0) {
        PortraitFallback(characterId, rarity, name, modifier)
        return
    }

    val bitmap by produceState<Bitmap?>(initialValue = null, portraitId, target) {
        // P1-2：key（portraitId/target）变化时先清空旧值——produceState 在 key 变化后
        // 会保留旧 value 直到新协程产出，IO 解码至少跨一帧，切角色瞬间会闪一瞬上一张立绘。
        // 置 null 让 Crossfade 从占位淡入新图（占位闪烁优于「显示错误角色」）。
        value = null
        value = PortraitLoader.load(resources, portraitId, target)
    }

    Crossfade(targetState = bitmap, label = "portrait") { bmp ->
        if (bmp != null) {
            // 水墨画滤镜：降饱和 + 提对比 + 暖色偏移，模拟宣纸上的墨彩效果
            InkWashPortrait(
                bitmap = bmp,
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

/** 缺图占位：水墨晕染底 + 墨迹圆环 + 首字（水墨画风格兜底）。 */
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
        // 水墨晕染底：径向渐变模拟墨汁在宣纸上晕开
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = minOf(cx, cy) * 0.9f
            // 外圈：极淡水痕
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c.copy(alpha = 0.08f), c.copy(alpha = 0.22f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
            )
            // 内圈：浓墨核心
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c.copy(alpha = 0.35f), c.copy(alpha = 0.08f)),
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    radius = r * 0.45f,
                ),
                radius = r * 0.45f,
            )
        }
        // 首字：金色楷书风格
        Text(
            text = initial,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = c.copy(alpha = 0.85f),
        )
    }
}

/**
 * 水墨画滤镜立绘 v2：选择性暖色保留 + 墨迹晕染暗角 + 宣纸肌理叠加。
 *
 * 通过 Android 原生 Canvas + ColorMatrix 在绘制时实时应用滤镜，不修改原始 Bitmap。
 * 滤镜分三层叠加：
 *   1. ColorMatrix：降饱和但保留朱砂/金箔暖色（水墨画中红色/金色是视觉焦点）
 *   2. RadialGradient 暗角：模拟墨汁从边缘向内渗透的晕染效果
 *   3. 宣纸噪点纹理：细微颗粒感模拟生宣纸面
 */
@Composable
private fun InkWashPortrait(
    bitmap: Bitmap,
    contentDescription: String,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    // 水墨画 ColorMatrix v2：选择性降饱和
    // 传统水墨中朱砂(红)和金箔(黄)保持鲜艳，冷色(蓝/绿)大幅降饱和
    val inkWashFilter = remember {
        ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    // R     G     B     A     — 保留红色通道能量，蓝色压低
                    0.55f, 0.12f, 0.03f, 0f, 10f,   // 红通道：保留较多 R，暖色突出
                    0.08f, 0.38f, 0.04f, 0f, 4f,     // 绿通道：中度保留
                    0.03f, 0.08f, 0.25f, 0f, 0f,     // 蓝通道：大幅压低，冷色褪去
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
    val portraitPaint = remember { Paint().apply { colorFilter = inkWashFilter } }
    // 暗角Paint：多层半透明浓墨圆环模拟墨迹渗透（API 29 兼容，不用 reset()）
    val ringPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    // 宣纸噪点Paint
    val grainPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.apply {
                // ── 第 1 层：水墨滤镜立绘 ──
                save()
                val scale = maxOf(w / bitmap.width, h / bitmap.height)
                val dx = (w - bitmap.width * scale) / 2f
                val dy = (h - bitmap.height * scale) / 2f
                translate(dx, dy)
                // R4-02（2026-08-30 审查修复）：此前只 translate 不 scale，
                // 「按缩放后尺寸算出的 dx/dy」+「1:1 原图绘制」两个错误叠加，
                // 立绘既缩水又被上移出容器（Thumb 档仅占 35% 宽、上移 198px）。
                scale(scale, scale)
                drawBitmap(bitmap, 0f, 0f, portraitPaint)
                restore()

                // ── 第 2 层：墨迹暗角晕染 ──
                // 从外向内逐层绘制半透明浓墨圆环，模拟墨汁从边缘渗透的效果
                val cx = w / 2f
                val cy = h / 2f
                val radius = maxOf(w, h) * 0.72f
                val rings = 8
                for (i in rings downTo 0) {
                    val fraction = i.toFloat() / rings
                    val ringRadius = radius * (0.55f + 0.45f * fraction)
                    val alpha = ((1f - fraction) * 0.30f * 255).toInt().coerceIn(0, 76)
                    ringPaint.color = (alpha shl 24) or 0x0A0A0F.toInt()
                    ringPaint.style = Paint.Style.FILL
                    drawCircle(cx, cy, ringRadius, ringPaint)
                }

                // ── 第 3 层：宣纸纤维噪点 ──
                // 确定性散列生成稀疏白点，模拟生宣纸面的纤维纹理
                grainPaint.color = 0x0AFFFFFF.toInt()
                grainPaint.alpha = 10
                grainPaint.style = Paint.Style.FILL
                val step = 14f
                var gy = 0f
                while (gy < h) {
                    var gx = 0f
                    while (gx < w) {
                        val hash = ((gx * 73856093).toInt() xor (gy * 19349663).toInt()) and 0xFF
                        if (hash < 14) {
                            grainPaint.alpha = 6 + (hash and 0x07)
                            drawPoint(gx, gy, grainPaint)
                        }
                        gx += step
                    }
                    gy += step
                }
            }
        }
    }
}
