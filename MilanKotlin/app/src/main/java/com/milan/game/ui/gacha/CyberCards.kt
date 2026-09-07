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

/** 装裱册页卡背（十连牌桌待翻面）：绫绢底 + 金箔隔水双线 + 中央朱砂落印。 */
@Composable
internal fun CyberCardBack(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CyberPalette.DeepBg)
            .border(1.dp, CyberPalette.Magenta.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // 绫绢底：淡墨经纬织纹（低 alpha 网格，喻绢丝）
        Canvas(Modifier.fillMaxSize()) {
            val sp = 12.dp.toPx()
            var y = sp
            while (y < size.height) {
                drawLine(CyberPalette.Grid, Offset(0f, y), Offset(size.width, y), 1f)
                y += sp
            }
            var x = sp
            while (x < size.width) {
                drawLine(CyberPalette.Grid, Offset(x, 0f), Offset(x, size.height), 1f)
                x += sp
            }
        }
        // 隔水：内层金箔细线框
        Box(
            Modifier
                .fillMaxSize()
                .padding(7.dp)
                .border(1.dp, CyberPalette.Magenta.copy(alpha = 0.28f), RoundedCornerShape(6.dp)),
        )
        // 画心留白区（更深墨色衬托落印）
        Box(
            Modifier
                .fillMaxSize()
                .padding(14.dp)
                .background(CyberPalette.DeepBg.copy(alpha = 0.55f))
                .border(1.dp, CyberPalette.Cyan.copy(alpha = 0.16f), RoundedCornerShape(4.dp)),
        )
        // 中央朱砂落印（方章）
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(CyberPalette.Cyan.copy(alpha = 0.82f))
                .border(1.dp, CyberPalette.Cyan.copy(alpha = 0.55f), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("墨", color = CyberPalette.BeamCore, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
                    .clip(RoundedCornerShape(14.dp))
                    .background(frame, RoundedCornerShape(14.dp))
                    .border(1.5.dp, frame, RoundedCornerShape(14.dp))
                    .padding(2.5.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .border(innerBorderWidth.dp, innerBorderColor, RoundedCornerShape(11.dp)),
            ) {
                // 画心：立绘
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                        .clip(RoundedCornerShape(9.dp)),
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
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppTheme.rarityColor(rarity))
                        .border(1.dp, AppTheme.rarityColor(rarity).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
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
 * 十连牌桌（Ten 阶段）：2×5 卡背飞入 + 逐张翻牌（间隔 300ms）。
 * 翻到正面瞬间回调 onFlip(rarity)，供上层触发分级音效/触觉/震屏。
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
            // P1 修复：串行 forEach 中延时必须是固定间隔——原 delay(300L * i) 会二次累积，
            // 第 i 张实际在 Σ300k 毫秒时刻翻开（第 6 张起 >3600ms），而 GachaScreen 在 Ten 阶段
            // 固定 3600ms 后收场卸载演出层，导致第 6~10 张永远不翻、其 SSR/UR 音效/震动/震屏丢失。
            // 固定 delay(300L) 后末张恰为 2700ms < 3600ms 收场线，全部翻牌可见且反馈完整。
            // P4-3：按稀有度分级翻牌节奏——UR 更慢（更多期待感），R 快速闪过。
            val delayMs = if (i == 0) 200L else when {
                batch[i - 1].rarity >= 4 -> 500L  // UR 后：慢翻（期待感）
                batch[i - 1].rarity == 3 -> 400L  // SSR 后：稍慢
                batch[i - 1].rarity == 2 -> 300L  // SR 后：标准
                else -> 250L                        // R 后：快速闪过
            }
            delay(delayMs)
            if (i < flipped.size) {
                flipped[i] = true
                onFlip(batch[i].rarity)
            }
        }
    }
    Column(
        modifier = modifier.padding(horizontal = 18.dp, vertical = 64.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        batch.chunked(5).forEachIndexed { rowIdx, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { i, r ->
                    TenCard(
                        result = r,
                        up = flipped[rowIdx * 5 + i],
                        modifier = Modifier.weight(1f).aspectRatio(0.72f),
                        onClick = { if (r.success) r.characterId?.let(onOpenCharacter) },
                    )
                }
            }
        }
    }
}

/** 十连单卡：飞入（透明度 + 下移）→ 翻牌（scaleX 两段动画，中点切正/背面）。
 *  UR 翻开瞬间叠加金色粒子迸发（12 颗金点从中心放射扩散）。 */
@Composable
internal fun TenCard(
    result: PullResult,
    up: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enterY by animateFloatAsState(if (up) 0f else 46f, tween(320), label = "tenY")
    val cardAlpha by animateFloatAsState(if (up) 1f else 0f, tween(260), label = "tenAlpha")
    val flip = remember { Animatable(1f) }
    var faceUp by remember { mutableStateOf(false) }
    val isUr = result.rarity >= 4
    // UR 金箔爆裂：翻开后 400ms 内从中心扩散 12 颗金点
    val burstAnim = remember { Animatable(0f) }
    LaunchedEffect(up) {
        if (up && !faceUp) {
            flip.animateTo(0f, tween(130))
            faceUp = true
            flip.animateTo(1f, tween(150))
            if (isUr) {
                burstAnim.snapTo(0f)
                burstAnim.animateTo(1f, tween(400))
            }
        }
    }
    val frame = AppTheme.rarityColor(result.rarity).copy(alpha = 0.7f)
    // 固定种子保证每次渲染一致
    val burstDots = remember {
        val r = kotlin.random.Random(result.characterId.hashCode().toLong())
        List(12) { i ->
            val angle = i * 30f + r.nextFloat() * 15f
            val dist = 0.35f + r.nextFloat() * 0.25f
            Pair(angle, dist)
        }
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = enterY
                alpha = cardAlpha
                scaleX = flip.value
            }
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (faceUp) {
            // 双层描边：外层稀有度色 + 内层金箔隔水线
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(frame, RoundedCornerShape(8.dp))
                    .border(1.dp, frame, RoundedCornerShape(8.dp))
                    .padding(2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(0.75.dp, AppTheme.Gold.copy(alpha = 0.28f), RoundedCornerShape(6.dp)),
            ) {
                // 画心
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .clip(RoundedCornerShape(4.dp)),
                ) {
                    PortraitImage(
                        characterId = result.characterId.orEmpty(),
                        rarity = result.rarity,
                        name = result.characterName,
                        aura = true,
                        target = PortraitTarget.Thumb,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(20.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))),
                    )
                    Text(
                        text = rarityLabel(result.rarity),
                        color = frame,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                    )
                }
            }
            // UR 金箔爆裂层
            if (isUr && burstAnim.value > 0f && burstAnim.value < 1f) {
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = size.minDimension * 0.45f
                    val alpha = (1f - burstAnim.value) * 0.85f
                    burstDots.forEach { (angle, dist) ->
                        val rad = Math.toRadians(angle.toDouble()).toFloat()
                        val d = maxR * dist * burstAnim.value
                        val px = cx + cos(rad) * d
                        val py = cy + sin(rad) * d
                        val dotR = (3f - burstAnim.value * 2f).dp.toPx()
                        drawCircle(
                            color = AppTheme.Gold.copy(alpha = alpha),
                            radius = dotR,
                            center = Offset(px, py),
                        )
                    }
                }
            }
        } else {
            CyberCardBack(Modifier.fillMaxSize())
        }
    }
}
