package com.milan.game.ui.gacha

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.PullResult
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.components.PortraitTarget
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.RitualType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

// P4-2（2026-08-27）：揭晓卡牌族从 CyberStage.kt 拆出（分镜 3/4：Single / Ten）。
// 2026-09 重做：十连改为「卡背阶梯入场 → 蓄势停顿 → 逐张 3D 翻面 → 高稀有度爆点」。

/** 装裱册页卡背：玄墨绫绢 + 金箔内框 + 朱砂「丹」印。 */
@Composable
internal fun CyberCardBack(
    modifier: Modifier = Modifier,
    sealSize: Dp = 32.dp,
    corner: Dp = 6.dp,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1A1E28), Color(0xFF0C0E14))),
                shape,
            )
            .border(1.dp, AppTheme.Gold.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val sp = 10.dp.toPx()
            val line = Color.White.copy(alpha = 0.04f)
            var y = sp
            while (y < size.height) {
                drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
                y += sp
            }
            var x = sp
            while (x < size.width) {
                drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
                x += sp
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(7.dp)
                .border(0.75.dp, AppTheme.Gold.copy(alpha = 0.28f), RoundedCornerShape(4.dp)),
        )
        Box(
            Modifier
                .size(sealSize)
                .background(AppTheme.SealRed.copy(alpha = 0.92f), RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("丹", color = AppTheme.Text1, fontSize = (sealSize.value * 0.48f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 单抽揭晓卡：自下翻入 + 立绘画心 + 双层描边 + UR 流光 + 楷书稀有度 + 命运签文。
 */
@Composable
internal fun SingleCard(
    def: CharacterDataEntry?,
    rarity: Int,
    fortune: String,
    cardIn: Boolean,
    onOpenCharacter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val animY = remember { Animatable(0.18f) }
    val animX = remember { Animatable(0.72f) }
    val animOff = remember { Animatable(80f) }
    val animRot = remember { Animatable(-6f) }
    LaunchedEffect(cardIn) {
        if (!cardIn) return@LaunchedEffect
        animOff.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
        animY.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 240f))
        animX.animateTo(1.04f, tween(110))
        animX.animateTo(1f, tween(200))
        animRot.animateTo(0f, tween(280))
    }
    val frame = AppTheme.rarityColor(rarity).copy(alpha = 0.9f)
    val isUr = rarity >= 4
    val isSsr = rarity == 3
    val shimmerProgress by if (isUr) {
        rememberInfiniteTransition(label = "singleShimmer").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
            label = "singleShimmerP",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val innerBorderColor = when {
        isUr -> AppTheme.GoldHi.copy(alpha = 0.75f)
        isSsr -> Color(0xFFC0C0C0).copy(alpha = 0.55f)
        else -> AppTheme.Gold.copy(alpha = 0.3f)
    }
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = animX.value
                scaleY = animY.value
                translationY = animOff.value
                rotationZ = animRot.value
            }
            .clickable { def?.let { onOpenCharacter(it.characterId) } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(240.dp)
                .height(320.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(AppTheme.Roundness.lg))
                    .background(frame, RoundedCornerShape(AppTheme.Roundness.lg))
                    .border(1.5.dp, frame, RoundedCornerShape(AppTheme.Roundness.lg))
                    .padding(2.5.dp)
                    .clip(RoundedCornerShape(AppTheme.Roundness.md))
                    .border(
                        if (isUr) 1.5.dp else 1.dp,
                        innerBorderColor,
                        RoundedCornerShape(AppTheme.Roundness.md),
                    ),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                        .clip(RoundedCornerShape(AppTheme.Roundness.md)),
                ) {
                    PortraitImage(
                        characterId = def?.characterId.orEmpty(),
                        rarity = rarity,
                        name = def?.displayName,
                        aura = true,
                        glowScale = 1f + rarity * 0.18f,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(72.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                                ),
                            ),
                    )
                    Text(
                        text = def?.displayName.orEmpty(),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = rarityLabel(rarity),
                    color = if (isUr) AppTheme.GoldTextOn else AppTheme.BgDeepest,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(AppTheme.Roundness.xs))
                        .background(AppTheme.rarityColor(rarity))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )

            }
            if (isUr) {
                Canvas(Modifier.matchParentSize().padding(0.5.dp)) {
                    val strokeW = 2f.dp.toPx()
                    val corner = 14f.dp.toPx()
                    val basePath = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                left = 0f, top = 0f,
                                right = size.width, bottom = size.height,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
                            ),
                        )
                    }
                    val pm = PathMeasure()
                    pm.setPath(basePath, forceClosed = true)
                    val totalLen = pm.length
                    val glowLen = totalLen * 0.22f
                    val startDist = shimmerProgress * totalLen
                    val seg = Path()
                    pm.getSegment(startDist, (startDist + glowLen).coerceAtMost(totalLen), seg, true)
                    if (startDist + glowLen > totalLen) {
                        val tail = Path()
                        pm.getSegment(0f, (startDist + glowLen) % totalLen, tail, true)
                        seg.addPath(tail)
                    }
                    drawPath(
                        seg,
                        style = Stroke(
                            width = strokeW + 2f.dp.toPx(),
                            cap = StrokeCap.Round,
                        ),
                        color = AppTheme.GoldHi.copy(alpha = 0.95f),
                    )
                }
            }
        }
        Text(
            text = rarityLabel(rarity),
            style = RitualType.copy(
                fontSize = if (isUr) 36.sp else if (isSsr) 30.sp else 24.sp,
                lineHeight = if (isUr) 42.sp else 34.sp,
                color = frame,
            ),
            modifier = Modifier.padding(top = 12.dp),
        )
        if (fortune.isNotEmpty()) {
            Text(
                text = fortune,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
            )
        }
    }
}

/**
 * 十连牌桌（2026-09 重做）：
 * 1) 卡背自下阶梯飞入（55ms 交错）
 * 2) 全体卡背蓄势 220ms
 * 3) 左→右 3D 翻面；SSR 闪色+震屏，UR 金箔爆裂+悬停更久
 * 4) 翻完后高稀有度卡保持微光，稍候由上层收场
 */
@Composable
internal fun TenTable(
    batch: List<PullResult>,
    onFlip: (Int) -> Unit,
    onOpenCharacter: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAllFlipped: (() -> Unit)? = null,
) {
    val flipped = remember(batch) { mutableStateListOfNulls(batch.size) }
    val entered = remember(batch) { mutableStateListOfNulls(batch.size) }
    LaunchedEffect(batch) {
        // 阶段一：阶梯入场
        batch.indices.forEach { i ->
            delay(if (i == 0) 80L else 55L)
            if (i < entered.size) entered[i] = true
        }
        delay(220)
        // 阶段二：逐张翻开
        batch.indices.forEach { i ->
            val prev = batch.getOrNull(i - 1)
            val delayMs = when {
                i == 0 -> 40L
                prev == null -> 40L
                prev.rarity >= 4 -> 480L
                prev.rarity == 3 -> 380L
                prev.rarity == 2 -> 300L
                else -> 240L
            }
            delay(delayMs)
            if (i < flipped.size) {
                flipped[i] = true
                onFlip(batch[i].rarity)
            }
        }
        delay(350)
        onAllFlipped?.invoke()
    }
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        batch.chunked(5).forEachIndexed { rowIdx, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEachIndexed { i, r ->
                    val idx = rowIdx * 5 + i
                    TenCard(
                        result = r,
                        entered = entered.getOrElse(idx) { false },
                        flipped = flipped.getOrElse(idx) { false },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.72f),
                        onClick = { if (r.success) r.characterId?.let(onOpenCharacter) },
                    )
                }
            }
        }
    }
}

/** 生成可变布尔列表的辅助（避免 remember 内直接 mutableStateListOf 初始化竞态）。 */
private fun mutableStateListOfNulls(size: Int): androidx.compose.runtime.snapshots.SnapshotStateList<Boolean> {
    val list = androidx.compose.runtime.mutableStateListOf<Boolean>()
    repeat(size) { list.add(false) }
    return list
}

/**
 * 十连单卡：卡背飞入 → rotationY 3D 翻面。
 * R/SR 快翻；SSR 翻开闪朱砂；UR 金箔爆裂 + 边框流光。
 */
@Composable
internal fun TenCard(
    result: PullResult,
    entered: Boolean,
    flipped: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enterY by animateFloatAsState(
        targetValue = if (entered) 0f else 120f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 180f),
        label = "tenEnterY",
    )
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(220),
        label = "tenEnterA",
    )
    val enterRot by animateFloatAsState(
        targetValue = if (entered) 0f else 12f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 160f),
        label = "tenEnterR",
    )
    val flip = remember { Animatable(0f) }
    val rarity = result.rarity
    val isUr = rarity >= 4
    val isSsr = rarity == 3
    val burstAnim = remember { Animatable(0f) }
    val flashAnim = remember { Animatable(0f) }
    val popAnim = remember { Animatable(1f) }
    var faceUp by remember { mutableStateOf(false) }

    LaunchedEffect(flipped) {
        if (flipped && !faceUp) {
            // 3D 翻面：0→180，中点换面
            flip.animateTo(180f, tween(if (rarity >= 3) 320 else 240, easing = FastOutSlowInEasing))
            faceUp = true
            if (rarity >= 3) {
                flashAnim.snapTo(1f)
                flashAnim.animateTo(0f, tween(if (isUr) 560 else 380))
                popAnim.snapTo(if (isUr) 1.12f else 1.06f)
                popAnim.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 220f))
            }
            if (isUr) {
                burstAnim.snapTo(0f)
                burstAnim.animateTo(1f, tween(520))
            }
        }
    }

    val frame = AppTheme.rarityColor(rarity)
    val shape = RoundedCornerShape(7.dp)
    val burstDots = remember(result.characterId) {
        val r = Random(result.characterId.hashCode().toLong())
        List(16) { i ->
            val angle = i * (360f / 16f) + r.nextFloat() * 14f
            val dist = 0.28f + r.nextFloat() * 0.4f
            Pair(angle, dist)
        }
    }
    // UR 翻开后边框微光
    val idleGlow by if (faceUp && isUr) {
        rememberInfiniteTransition(label = "urIdle").animateFloat(
            initialValue = 0.45f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "urIdleG",
        )
    } else {
        remember { mutableFloatStateOf(0.7f) }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = enterY
                alpha = enterAlpha
                rotationZ = enterRot
                rotationY = flip.value
                cameraDistance = 14f * density
                scaleX = popAnim.value
                scaleY = popAnim.value
            }
            .clip(shape)
            .clickable(enabled = faceUp) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        val showFace = flip.value > 90f
        if (showFace) {
            // 反镜像补偿 rotationY>90 的水平翻转
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { scaleX = -1f },
            ) {
                TenCardFace(result = result, frame = frame, shape = shape, idleGlow = idleGlow)
            }
            if (flashAnim.value > 0f) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { scaleX = -1f }
                        .background(frame.copy(alpha = flashAnim.value * 0.4f)),
                )
            }
            if (isUr && burstAnim.value in 0.001f..0.999f) {
                Canvas(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { scaleX = -1f },
                ) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = size.minDimension * 0.7f
                    val alpha = (1f - burstAnim.value) * 0.95f
                    // 放射光线
                    repeat(8) { i ->
                        val rad = Math.toRadians((i * 45f).toDouble()).toFloat()
                        val len = maxR * (0.4f + burstAnim.value * 0.7f)
                        drawLine(
                            color = AppTheme.GoldHi.copy(alpha = alpha * 0.7f),
                            start = Offset(cx, cy),
                            end = Offset(cx + cos(rad) * len, cy + sin(rad) * len),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                    burstDots.forEach { (angle, dist) ->
                        val rad = Math.toRadians(angle.toDouble()).toFloat()
                        val d = maxR * dist * burstAnim.value
                        val px = cx + cos(rad) * d
                        val py = cy + sin(rad) * d
                        val dotR = (3.4f - burstAnim.value * 2.2f).dp.toPx()
                        drawCircle(AppTheme.Gold.copy(alpha = alpha), dotR, Offset(px, py))
                    }
                }
            }
        } else {
            CyberCardBack(
                modifier = Modifier.matchParentSize(),
                sealSize = 22.dp,
                corner = 7.dp,
            )
        }
    }
}

@Composable
private fun TenCardFace(
    result: PullResult,
    frame: Color,
    shape: RoundedCornerShape,
    idleGlow: Float,
) {
    val rarity = result.rarity
    val isUr = rarity >= 4
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(frame.copy(alpha = if (isUr) 0.42f else 0.32f), Color(0xFF0A0C10)),
                ),
                shape,
            )
            .border(
                width = if (isUr) 1.6.dp else 1.25.dp,
                color = frame.copy(alpha = if (isUr) idleGlow else 0.85f),
                shape = shape,
            )
            .padding(2.dp)
            .clip(shape)
            .border(0.5.dp, AppTheme.Gold.copy(alpha = if (isUr) 0.5f else 0.32f), shape),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(1.5.dp)
                .clip(RoundedCornerShape(5.dp)),
        ) {
            PortraitImage(
                characterId = result.characterId.orEmpty(),
                rarity = rarity,
                name = result.characterName,
                aura = rarity >= 3,
                target = PortraitTarget.Full,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(26.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xD908090C)),
                        ),
                    ),
            )
            Text(
                text = result.characterName,
                color = AppTheme.Text1,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 4.dp, start = 2.dp, end = 2.dp),
            )
            Text(
                text = rarityLabel(rarity),
                color = if (isUr) AppTheme.GoldTextOn else AppTheme.BgDeepest,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .background(frame.copy(alpha = 0.95f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            )
            if (result.isNew) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .background(AppTheme.Gold, RoundedCornerShape(2.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                ) {
                    Text("NEW", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = AppTheme.GoldTextOn)
                }
            }
            if (rarity >= 3) {
                // 高稀有度底部光晕条
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, frame.copy(alpha = 0.85f), Color.Transparent),
                            ),
                        ),
                )
            }
        }
    }
}

/** 十连收场条：最高稀有度仪式字 + SSR+/UR 计数。 */
@Composable
internal fun TenCurtainCall(
    batch: List<PullResult>,
    modifier: Modifier = Modifier,
) {
    if (batch.isEmpty()) return
    val maxR = batch.maxOfOrNull { it.rarity } ?: 1
    val ssrPlus = batch.count { it.rarity >= 3 }
    val ur = batch.count { it.rarity >= 4 }
    val title = when {
        maxR >= 4 -> "金箔 · 神谕降临"
        maxR == 3 -> "朱砂 · 名士现世"
        maxR == 2 -> "石青 · 灵犀一点"
        else -> "松烟 · 墨迹初成"
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = RitualType.copy(
                fontSize = 28.sp,
                lineHeight = 36.sp,
                color = AppTheme.rarityColor(maxR),
            ),
        )
        Text(
            text = buildString {
                append("SSR+ × $ssrPlus")
                if (ur > 0) append("   ·   UR × $ur")
            },
            fontSize = 12.sp,
            color = AppTheme.Text2,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}


