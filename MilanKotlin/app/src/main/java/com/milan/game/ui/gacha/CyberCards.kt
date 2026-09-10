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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.PullResult
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.components.PortraitTarget
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// P4-2（2026-08-27）：揭晓卡牌族从 CyberStage.kt 拆出（分镜 3/4：Single / Ten）。

/** 装裱册页卡背（单抽/十连共用）：玄墨绫绢 + 金箔内框 + 朱砂「丹」印。 */
@Composable
internal fun CyberCardBack(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
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
                .border(0.75.dp, AppTheme.Gold.copy(alpha = 0.28f), RoundedCornerShape(4.dp))
        )
        Box(
            Modifier
                .size(32.dp)
                .background(AppTheme.SealRed.copy(alpha = 0.92f), RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("丹", color = AppTheme.Text1, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 单抽揭晓卡（Single 阶段）：故障翻入（scaleY 弹簧弹跳 + scaleX 抖动 + 下移归位），
 * 稀有度描边 + 立绘氛围圈（aura）+ 底部铭牌 + 稀有度故障大字 + 命运签文。点卡进角色详情。
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
    val animY = remember { Animatable(0.12f) }
    val animX = remember { Animatable(0.55f) }
    val animOff = remember { Animatable(60f) }
    LaunchedEffect(cardIn) {
        if (!cardIn) return@LaunchedEffect
        animOff.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
        animY.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 260f))
        animX.animateTo(1.05f, tween(120))
        animX.animateTo(1f, tween(220))
    }
    val frame = AppTheme.rarityColor(rarity).copy(alpha = 0.85f)
    // UR 金色流光边框：3 秒一圈沿矩形移动
    val isUr = rarity >= 4
    val isSsr = rarity == 3
    val shimmerProgress by if (isUr) {
        rememberInfiniteTransition(label = "singleShimmer").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
            label = "singleShimmerP",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    // SSR 内层描边银色；UR 金色
    val innerBorderColor = when {
        isUr -> AppTheme.Gold.copy(alpha = 0.65f)
        isSsr -> Color(0xFFC0C0C0).copy(alpha = 0.55f)
        else -> AppTheme.Gold.copy(alpha = 0.30f)
    }
    val innerBorderWidth = when {
        isUr -> 1.5f; isSsr -> 1.2f; else -> 1f
    }
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = animX.value
                scaleY = animY.value
                translationY = animOff.value
            }
            .clickable { def?.let { onOpenCharacter(it.characterId) } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 双层描边：外层稀有度色 + 内层金箔/银箔隔水线
        Box(
            Modifier
                .width(232.dp)
                .height(300.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 基础卡片内容（裁剪到圆角）
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(AppTheme.Roundness.lg))
                    .background(frame, RoundedCornerShape(AppTheme.Roundness.lg))
                    .border(1.5.dp, frame, RoundedCornerShape(AppTheme.Roundness.lg))
                    .padding(2.5.dp)
                    .clip(RoundedCornerShape(AppTheme.Roundness.md))
                    .border(innerBorderWidth.dp, innerBorderColor, RoundedCornerShape(AppTheme.Roundness.md)),
            ) {
                // 画心：立绘
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
                            .height(64.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))),
                    )
                    Text(
                        text = def?.displayName.orEmpty(),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                // 稀有度朱砂落印（右上角方章）
                Text(
                    text = rarityLabel(rarity),
                    color = AppTheme.BgDeepest,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(AppTheme.Roundness.xs))
                        .background(AppTheme.rarityColor(rarity))
                        .border(1.dp, AppTheme.rarityColor(rarity).copy(alpha = 0.6f), RoundedCornerShape(AppTheme.Roundness.xs))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            // UR 流光边框层：金色高光沿矩形边框匀速移动
            if (isUr) {
                Canvas(Modifier.matchParentSize().padding(0.5.dp)) {
                    val strokeW = 2f.dp.toPx()
                    val corner = 14f.dp.toPx()
                    val basePath = androidx.compose.ui.graphics.Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                left = 0f, top = 0f,
                                right = size.width, bottom = size.height,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
                            )
                        )
                    }
                    val pm = androidx.compose.ui.graphics.PathMeasure()
                    pm.setPath(basePath, forceClosed = true)
                    val totalLen = pm.length
                    val glowLen = totalLen * 0.22f
                    val startDist = shimmerProgress * totalLen
                    val seg = androidx.compose.ui.graphics.Path()
                    pm.getSegment(startDist, (startDist + glowLen).coerceAtMost(totalLen), seg, true)
                    if (startDist + glowLen > totalLen) {
                        val tail = androidx.compose.ui.graphics.Path()
                        pm.getSegment(0f, (startDist + glowLen) % totalLen, tail, true)
                        seg.addPath(tail)
                    }
                    drawPath(
                        seg,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = strokeW + 2f.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        ),
                        color = AppTheme.Gold.copy(alpha = 0.9f),
                    )
                }
            }
        }
        GlitchText(rarityLabel(rarity), frame, 22.sp, Modifier.padding(top = 10.dp))
        Text(
            text = fortune,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp, start = 28.dp, end = 28.dp),
        )
    }
}

/**
 * 十连牌桌（Ten 阶段，2026-09-09 重做）：
 * 两行实体卡阶梯入场 → 从左到右逐张翻开 → SSR/UR 翻开时全卡闪色。
 */
@Composable
internal fun TenTable(
    batch: List<PullResult>,
    onFlip: (Int) -> Unit,
    onOpenCharacter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flipped = remember { mutableStateListOf<Boolean>().apply { repeat(batch.size) { add(false) } } }
    LaunchedEffect(batch) {
        batch.indices.forEach { i ->
            // 固定间隔，避免二次累积导致后半批翻不开（见旧注释 P1）
            val delayMs = if (i == 0) 180L else when {
                batch[i - 1].rarity >= 4 -> 520L
                batch[i - 1].rarity == 3 -> 420L
                batch[i - 1].rarity == 2 -> 320L
                else -> 260L
            }
            delay(delayMs)
            if (i < flipped.size) {
                flipped[i] = true
                onFlip(batch[i].rarity)
            }
        }
    }
    Column(
        modifier = modifier.padding(horizontal = 14.dp, vertical = 56.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        batch.chunked(5).forEachIndexed { rowIdx, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { i, r ->
                    TenCard(
                        result = r,
                        up = flipped[rowIdx * 5 + i],
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.71f),
                        onClick = { if (r.success) r.characterId?.let(onOpenCharacter) },
                    )
                }
            }
        }
    }
}

/** 十连单卡：卡背 → 实体卡翻面。高稀有度有金箔爆裂 + 闪框。 */
@Composable
internal fun TenCard(
    result: PullResult,
    up: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enterY by animateFloatAsState(if (up) 0f else 40f, tween(300), label = "tenY")
    val cardAlpha by animateFloatAsState(if (up) 1f else 0f, tween(240), label = "tenAlpha")
    val flip = remember { Animatable(1f) }
    var faceUp by remember { mutableStateOf(false) }
    val rarity = result.rarity
    val isUr = rarity >= 4
    val isSsr = rarity == 3
    val burstAnim = remember { Animatable(0f) }
    val flashAnim = remember { Animatable(0f) }

    LaunchedEffect(up) {
        if (up && !faceUp) {
            flip.animateTo(0f, tween(120))
            faceUp = true
            flip.animateTo(1f, tween(160))
            if (rarity >= 3) {
                flashAnim.snapTo(1f)
                flashAnim.animateTo(0f, tween(if (isUr) 520 else 360))
            }
            if (isUr) {
                burstAnim.snapTo(0f)
                burstAnim.animateTo(1f, tween(450))
            }
        }
    }

    val frame = AppTheme.rarityColor(rarity)
    val shape = RoundedCornerShape(6.dp)
    val burstDots = remember {
        val r = kotlin.random.Random(result.characterId.hashCode().toLong())
        List(14) { i ->
            val angle = i * (360f / 14f) + r.nextFloat() * 12f
            val dist = 0.3f + r.nextFloat() * 0.35f
            Pair(angle, dist)
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = enterY
                alpha = cardAlpha
                scaleX = flip.value
                cameraDistance = 12f * density
            }
            .clip(shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (faceUp) {
            // 实体卡：外边 + 金内线 + 画心
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(frame.copy(alpha = 0.35f), Color(0xFF0A0C10))
                        ),
                        shape,
                    )
                    .border(1.25.dp, frame.copy(alpha = 0.85f), shape)
                    .padding(2.dp)
                    .clip(shape)
                    .border(0.5.dp, AppTheme.Gold.copy(alpha = 0.35f), shape),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(1.5.dp)
                        .clip(RoundedCornerShape(4.dp)),
                ) {
                    PortraitImage(
                        characterId = result.characterId.orEmpty(),
                        rarity = rarity,
                        name = result.characterName,
                        aura = rarity >= 3,
                        target = PortraitTarget.Full,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // 底铭牌
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(22.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xCC08090C))
                                )
                            ),
                    )
                    Text(
                        text = result.characterName ?: "",
                        color = AppTheme.Text1,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 3.dp, start = 2.dp, end = 2.dp),
                    )
                    // 稀有度角
                    Text(
                        text = rarityLabel(rarity),
                        color = if (isUr) AppTheme.GoldTextOn else AppTheme.BgDeepest,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(3.dp)
                            .background(frame.copy(alpha = 0.92f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }
            }

            // 稀有度全卡闪
            if (flashAnim.value > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(frame.copy(alpha = flashAnim.value * 0.35f))
                )
            }

            // UR 金箔爆裂
            if (isUr && burstAnim.value > 0f && burstAnim.value < 1f) {
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = size.minDimension * 0.55f
                    val alpha = (1f - burstAnim.value) * 0.9f
                    burstDots.forEach { (angle, dist) ->
                        val rad = Math.toRadians(angle.toDouble()).toFloat()
                        val d = maxR * dist * burstAnim.value
                        val px = cx + cos(rad) * d
                        val py = cy + sin(rad) * d
                        val dotR = (3.2f - burstAnim.value * 2f).dp.toPx()
                        drawCircle(AppTheme.Gold.copy(alpha = alpha), dotR, Offset(px, py))
                    }
                }
            }
        } else {
            // 卡背：丹青绫绢 + 金印
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1A1E28), Color(0xFF0C0E14))
                        ),
                        shape,
                    )
                    .border(1.dp, AppTheme.Gold.copy(alpha = 0.4f), shape),
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
                        .padding(6.dp)
                        .border(0.75.dp, AppTheme.Gold.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                )
                Box(
                    Modifier
                        .size(28.dp)
                        .background(AppTheme.SealRed.copy(alpha = 0.9f), RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("丹", color = AppTheme.Text1, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
