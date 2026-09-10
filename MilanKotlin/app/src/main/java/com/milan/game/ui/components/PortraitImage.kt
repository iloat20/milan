package com.milan.game.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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

    // 2026-09-10 加载提速：LRU 同步命中时直接上屏，跳过 Crossfade 的「占位闪一下再淡入」，
    // 列表快速回滚/复用时首帧即完整立绘（观感接近同步加载，仍不阻塞主线程解码）。
    val cachedNow = remember(portraitId, target) { PortraitLoader.peek(portraitId, target) }
    if (cachedNow != null) {
        InkWashPortrait(
            bitmap = cachedNow,
            contentDescription = name ?: characterId,
            modifier = modifier,
            contentScale = contentScale,
        )
        return
    }

    val bitmap by produceState<Bitmap?>(initialValue = null, portraitId, target) {
        // P1-2：key（portraitId/target）变化时先清空旧值——produceState 在 key 变化后
        // 会保留旧 value 直到新协程产出，IO 解码至少跨一帧，切角色瞬间会闪一瞬上一张立绘。
        // 置 null 让 Crossfade 从占位淡入新图（占位闪烁优于「显示错误角色」）。
        value = null
        value = PortraitLoader.load(resources, portraitId, target)
    }

    // 首次解码：Crossfade 淡入；duration 压到 120ms，减少占位停留时间。
    Crossfade(targetState = bitmap, animationSpec = tween(120), label = "portrait") { bmp ->
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

/**
 * 水墨画滤镜立绘 v3：选择性暖色保留 + 平滑墨迹暗角 + 宣纸肌理叠加。
 *
 * 通过 Android 原生 Canvas + ColorMatrix 在绘制时实时应用滤镜，不修改原始 Bitmap。
 * 滤镜分三层叠加：
 *   1. ColorMatrix：降饱和但保留朱砂/金箔暖色（水墨画中红色/金色是视觉焦点）
 *   2. RadialGradient 暗角：单次径向渐变，从中心透明到边缘浓墨，模拟墨汁自然晕染
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
    // 暗角Paint：径向渐变模拟墨汁从边缘向内渗透
    val vignettePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
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
                scale(scale, scale)
                drawBitmap(bitmap, 0f, 0f, portraitPaint)
                restore()

                // ── 第 2 层：平滑墨迹暗角（单次径向渐变）──
                // 从画布中心透明到边缘浓墨，一笔完成，无逐层圆环的带状伪影
                val cx = w / 2f
                val cy = h / 2f
                val radius = maxOf(w, h) * 0.72f
                vignettePaint.shader = android.graphics.RadialGradient(
                    cx, cy, radius,
                    intArrayOf(0x000A0A0F.toInt(), 0x000A0A0F.toInt(), 0x300A0A0F.toInt(), 0x600A0A0F.toInt()),
                    floatArrayOf(0f, 0.45f, 0.75f, 1f),
                    android.graphics.Shader.TileMode.CLAMP,
                )
                drawCircle(cx, cy, radius, vignettePaint)

                // ── 第 3 层：宣纸纤维噪点 ──
                // 确定性散列生成稀疏白点，模拟生宣纸面的纤维纹理
                // R6-P2：步长随尺寸放大并封顶点数——大图 Full 每帧 drawPoint 上千次会掉帧
                grainPaint.color = 0x0AFFFFFF.toInt()
                grainPaint.alpha = 10
                grainPaint.style = Paint.Style.FILL
                val step = (maxOf(w, h) / 48f).coerceIn(14f, 36f)
                val maxGrainPoints = 900
                var grainPoints = 0
                var gy = 0f
                while (gy < h && grainPoints < maxGrainPoints) {
                    var gx = 0f
                    while (gx < w && grainPoints < maxGrainPoints) {
                        val hash = ((gx * 73856093).toInt() xor (gy * 19349663).toInt()) and 0xFF
                        if (hash < 14) {
                            grainPaint.alpha = 6 + (hash and 0x07)
                            drawPoint(gx, gy, grainPaint)
                            grainPoints++
                        }
                        gx += step
                    }
                    gy += step
                }
            }
        }
    }
}
