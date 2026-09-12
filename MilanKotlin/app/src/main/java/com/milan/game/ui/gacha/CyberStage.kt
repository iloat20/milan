package com.milan.game.ui.gacha

import androidx.compose.material3.MaterialTheme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.PullResult
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.RitualType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 丹青典藏抽卡演出（2026-09 从赛博霓虹全面迁入典藏仪式语言）。
 *
 * 设计：
 * - 纯 Compose / Canvas，零新依赖
 * - 色板独立（CyberPalette 命名保留以最小化侵入，值已是朱砂/金箔/石青/浓墨）
 * - 分镜：Charge 蓄墨 → Beam 开卷 → Single/Ten 揭晓 → Done
 *
 * 全程整屏可点击跳过；点卡片进角色详情。
 */
enum class RevealStage { Charge, Beam, Single, Ten, Done }

/** 演出色板（命名 Cyan/Magenta/VioletGlow 为历史遗留，值已是水墨国风）。 */
internal object CyberPalette {
    val Cyan = AppTheme.SealRed
    val Magenta = AppTheme.Gold
    val VioletGlow = AppTheme.Frost
    val DeepBg = AppTheme.BgDeepest
    val BeamCore = AppTheme.Text1
    val Grid = AppTheme.Text1.copy(alpha = 0.12f)
}

private val HeraldGlowColors = listOf(
    CyberPalette.BeamCore,
    CyberPalette.Cyan.copy(alpha = 0.55f),
    CyberPalette.Magenta.copy(alpha = 0.2f),
    Color.Transparent,
)

/** 演出粒子（坐标归一化，绘制时乘画布尺寸）。 */
internal class CyberParticle(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val delaySec: Float,
    val durSec: Float,
    val radiusDp: Float,
    val color: Color,
)

/** 稀有度短标签（演出内自足）。 */
internal fun rarityLabel(r: Int): String = when (r) {
    4 -> "UR"
    3 -> "SSR"
    2 -> "SR"
    else -> "R"
}

/**
 * 仪式楷书标题：金/稀有度色主字 + 极淡外描边晕。
 * 替代旧 GlitchText 故障残影——典藏时刻不该「闪屏坏字」。
 */
@Composable
internal fun RitualTitle(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = RitualType.copy(
                fontSize = fontSize,
                lineHeight = fontSize * 1.25f,
                color = color.copy(alpha = 0.35f),
            ),
            modifier = Modifier.padding(2.dp),
        )
        Text(
            text = text,
            style = RitualType.copy(
                fontSize = fontSize,
                lineHeight = fontSize * 1.25f,
                color = color,
            ),
        )
    }
}

/**
 * 宣纸墨韵背景：中心晕光 + 慢速漂浮墨点 + 发丝经纬（无赛博滚动网格/故障条）。
 */
@Composable
private fun InkWashBackdrop(
    modifier: Modifier = Modifier,
    rarity: Int = 1,
) {
    // 2026-09-12：仅 RESUMED 推进，避免后台 infinite 空转
    val time = com.milan.game.ui.effects.rememberAnimTime()
    val dust = remember {
        val r = Random(20260903)
        List(36) {
            Triple(r.nextFloat(), r.nextFloat(), r.nextFloat())
        }
    }
    val accent = AppTheme.rarityColor(rarity)
    Canvas(modifier) {
        // 中心稀有度晕
        val cx = size.width * 0.5f
        val cy = size.height * 0.42f
        val coreR = size.minDimension * 0.55f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    accent.copy(alpha = 0.18f),
                    accent.copy(alpha = 0.05f),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = coreR,
            ),
            radius = coreR,
            center = Offset(cx, cy),
        )
        // 四角压暗
        drawRect(
            brush = Brush.radialGradient(
                listOf(Color.Transparent, CyberPalette.DeepBg.copy(alpha = 0.55f)),
                center = Offset(cx, cy),
                radius = size.maxDimension * 0.72f,
            ),
        )
        // 发丝经纬（静）
        val step = 48.dp.toPx()
        var y = step
        while (y < size.height) {
            drawLine(CyberPalette.Grid, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        var x = step
        while (x < size.width) {
            drawLine(CyberPalette.Grid, Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
        // 漂浮墨尘
        dust.forEach { (px, py, phase) ->
            val tw = (sin(time * 1.4f + phase * 6.28f) + 1f) * 0.5f
            val ox = sin(time * 0.35f + phase * 4f) * 12f
            val oy = cos(time * 0.28f + phase * 3f) * 10f
            drawCircle(
                color = CyberPalette.BeamCore.copy(alpha = 0.08f + tw * 0.18f),
                radius = (1.1f + tw * 1.4f).dp.toPx(),
                center = Offset(px * size.width + ox, py * size.height + oy),
            )
        }
        // 稀有度金尘（SSR+）
        if (rarity >= 3) {
            val goldCount = if (rarity >= 4) 18 else 10
            repeat(goldCount) { i ->
                val ph = i * 0.37f
                val gx = (0.5f + 0.4f * sin(time * 0.6f + ph)) * size.width
                val gy = (0.35f + 0.3f * cos(time * 0.5f + ph * 1.3f)) * size.height
                drawCircle(
                    color = accent.copy(alpha = 0.25f + 0.2f * sin(time * 2f + ph)),
                    radius = 1.6.dp.toPx(),
                    center = Offset(gx, gy),
                )
            }
        }
    }
}

/**
 * 待机召唤法阵（主界面抽卡区）：
 * 稀有度驱动的金箔敕令环 + 朱砂印核 + 轨道墨点；
 * pityRatio 越高脉动越快、金色越浓。
 */
@Composable
fun CyberHerald(
    modifier: Modifier = Modifier,
    testMode: Boolean = false,
    pityRatio: Float = 0f,
) {
    val pulseDuration = (1800 - (pityRatio * 900f)).toInt().coerceIn(900, 1800)
    val spin by if (testMode) remember { mutableFloatStateOf(0f) }
    else rememberInfiniteTransition(label = "herald").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "heraldSpin",
    )
    val pulse by if (testMode) remember { mutableFloatStateOf(0.5f) }
    else rememberInfiniteTransition(label = "heraldPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(pulseDuration, easing = LinearEasing), RepeatMode.Reverse),
        label = "heraldPulse",
    )
    val ringAlpha = 0.4f + pityRatio * 0.4f
    Box(
        modifier = modifier
            .size(220.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        CyberPalette.Cyan.copy(alpha = 0.12f + pityRatio * 0.1f),
                        CyberPalette.DeepBg.copy(alpha = 0.92f),
                    ),
                ),
            )
            .border(1.5.dp, CyberPalette.Cyan.copy(alpha = ringAlpha), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val d = size.minDimension.coerceAtLeast(1f)
                    val c = Offset(d / 2f, d / 2f)
                    val glowBrush = Brush.radialGradient(HeraldGlowColors, center = c, radius = d * 2f)
                    onDrawBehind {
                        // 外虚线敕令环
                        drawCircle(
                            color = CyberPalette.Magenta.copy(alpha = 0.35f + pityRatio * 0.25f),
                            radius = d * 0.47f,
                            center = c,
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                            ),
                        )
                        // 旋转金弧
                        val arcSweep = 120f + pityRatio * 70f
                        drawArc(
                            color = CyberPalette.Magenta.copy(alpha = 0.5f + pityRatio * 0.25f),
                            startAngle = spin,
                            sweepAngle = arcSweep,
                            useCenter = false,
                            topLeft = Offset(d * 0.08f, d * 0.08f),
                            size = Size(d * 0.84f, d * 0.84f),
                            style = Stroke(2.dp.toPx()),
                        )
                        // 反向细弧
                        drawArc(
                            color = CyberPalette.Cyan.copy(alpha = 0.35f),
                            startAngle = -spin * 0.7f,
                            sweepAngle = 60f,
                            useCenter = false,
                            topLeft = Offset(d * 0.16f, d * 0.16f),
                            size = Size(d * 0.68f, d * 0.68f),
                            style = Stroke(1.2.dp.toPx()),
                        )
                        val r = d * (0.16f + 0.03f * pulse + pityRatio * 0.02f)
                        scale(scaleX = r / d, scaleY = r / d, pivot = c) {
                            drawCircle(brush = glowBrush, radius = d, center = c)
                        }
                    }
                },
        )
        if (!testMode) InkParticles(Modifier.fillMaxSize())
        Text(
            text = "敕",
            style = RitualType.copy(
                fontSize = 34.sp,
                lineHeight = 40.sp,
                color = AppTheme.Text1,
            ),
        )
    }
}

@Composable
private fun InkParticles(modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nano -> time = nano / 1_000_000_000f }
        }
    }
    val particles = remember {
        val r = Random(20260829)
        List(10) { i ->
            val radius = 0.34f + r.nextFloat() * 0.14f
            val speed = 0.45f + r.nextFloat() * 0.3f
            val phase = r.nextFloat() * 6.28f
            val wobbleAmp = 0.02f + r.nextFloat() * 0.03f
            val dotRadius = 2.2f + r.nextFloat() * 2f
            Triple(radius, speed, phase) to Pair(wobbleAmp, dotRadius)
        }
    }
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseR = size.minDimension * 0.42f
        particles.forEach { (orbit, props) ->
            val (radius, speed, phase) = orbit
            val (wobbleAmp, dotR) = props
            val angle = time * speed + phase
            val r = baseR * radius * (1f + wobbleAmp * sin(angle * 2.3f))
            val px = cx + r * cos(angle)
            val py = cy + r * sin(angle) * 0.85f
            drawCircle(
                color = CyberPalette.Cyan.copy(alpha = 0.3f),
                radius = dotR.dp.toPx(),
                center = Offset(px, py),
            )
        }
    }
}

/**
 * 演出总装：墨韵深底 + 阶段内容 + 震屏 + 整屏跳过。
 * 翻到 SSR/UR 触发震屏（Single 进入按稀有度，Ten 翻开按单卡稀有度）。
 */
@Composable
fun CyberRevealLayer(
    stage: RevealStage,
    singleDef: CharacterDataEntry?,
    singleRarity: Int,
    fortune: String,
    batch: List<PullResult>,
    cardIn: Boolean,
    onSkip: () -> Unit,
    onFlip: (Int) -> Unit,
    onOpenCharacter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var shakeAmp by remember { mutableFloatStateOf(0f) }
    // 序号作重启键：连续同稀有度翻牌需逐次重放（P2 坑因保留）
    var shakeSeq by remember { mutableIntStateOf(0) }
    val shakeX = remember { Animatable(0f) }
    val shakeY = remember { Animatable(0f) }
    LaunchedEffect(shakeSeq) {
        if (shakeAmp <= 0f) return@LaunchedEffect
        repeat(7) {
            shakeX.animateTo((Random.nextFloat() - 0.5f) * shakeAmp, tween(45))
            shakeY.animateTo((Random.nextFloat() - 0.5f) * shakeAmp, tween(45))
        }
        shakeX.animateTo(0f, tween(90))
        shakeY.animateTo(0f, tween(90))
    }
    LaunchedEffect(stage) {
        if (stage == RevealStage.Single) {
            shakeAmp = when {
                singleRarity >= 4 -> 14f
                singleRarity == 3 -> 7f
                else -> 0f
            }
            shakeSeq++
        }
    }
    val innerOnFlip: (Int) -> Unit = { r ->
        shakeAmp = when {
            r >= 4 -> 14f
            r == 3 -> 8f
            else -> 0f
        }
        shakeSeq++
        onFlip(r)
    }
    val tapSrc = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CyberPalette.DeepBg.copy(alpha = 0.97f))
            .clickable(interactionSource = tapSrc, indication = null) { onSkip() },
    ) {
        InkWashBackdrop(Modifier.fillMaxSize(), rarity = singleRarity.coerceAtLeast(batch.maxOfOrNull { it.rarity } ?: 1))
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = shakeX.value
                    translationY = shakeY.value
                },
        ) {
            when (stage) {
                RevealStage.Charge -> {
                    ChargeCore(rarity = singleRarity, Modifier.fillMaxSize())
                    RitualTitle(
                        text = when {
                            singleRarity >= 4 -> "古卷展开 · 浓墨蓄力"
                            singleRarity == 3 -> "金箔凝聚 · 丹青觉醒"
                            singleRarity == 2 -> "墨迹汇聚 · 灵犀涌动"
                            else -> "敕令開陣 · 充能中"
                        },
                        color = CyberPalette.Cyan,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 36.dp),
                    )
                }
                RevealStage.Beam -> {
                    RiftBeam(rarity = singleRarity, Modifier.fillMaxSize())
                    RitualTitle(
                        text = when {
                            singleRarity >= 4 -> "敕令开卷 · 万古回响"
                            singleRarity == 3 -> "敕令开卷 · 金光乍现"
                            else -> "敕令开卷"
                        },
                        color = when {
                            singleRarity >= 4 -> CyberPalette.Magenta
                            else -> CyberPalette.BeamCore
                        },
                        fontSize = 22.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 140.dp),
                    )
                }
                RevealStage.Single -> {
                    // 2026-09-12 UR 全屏幕：叠在卡面之上，1.6s 自动收
                    var urCurtain by remember { mutableStateOf(singleRarity >= 4) }
                    if (urCurtain) {
                        UrCurtainReveal(
                            characterName = singleDef?.displayName.orEmpty(),
                            visible = true,
                            onDismiss = { urCurtain = false },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    SingleCard(
                        def = singleDef,
                        rarity = singleRarity,
                        fortune = fortune,
                        cardIn = cardIn,
                        onOpenCharacter = onOpenCharacter,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    if (singleRarity >= 2) {
                        RitualTitle(
                            text = "${rarityLabel(singleRarity)}!",
                            color = AppTheme.rarityColor(singleRarity),
                            fontSize = if (singleRarity >= 4) 40.sp else 32.sp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 48.dp),
                        )
                    }
                }
                RevealStage.Ten -> {
                    var tenSettled by remember { mutableStateOf(false) }
                    TenTable(
                        batch = batch,
                        onFlip = innerOnFlip,
                        onOpenCharacter = onOpenCharacter,
                        onAllFlipped = { tenSettled = true },
                        modifier = Modifier.align(Alignment.Center),
                    )
                    val maxR = batch.maxOfOrNull { it.rarity } ?: 1
                    if (!tenSettled) {
                        RitualTitle(
                            text = "十连 · 寻访",
                            color = AppTheme.Text2,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 28.dp),
                        )
                    } else {
                        RitualTitle(
                            text = "${rarityLabel(maxR)}",
                            color = AppTheme.rarityColor(maxR),
                            fontSize = if (maxR >= 4) 36.sp else 28.sp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 28.dp),
                        )
                        if (maxR >= 4) {
                            var urCurtainTen by remember { mutableStateOf(true) }
                            if (urCurtainTen) {
                                UrCurtainReveal(
                                    characterName = batch.maxByOrNull { it.rarity }?.characterName.orEmpty(),
                                    visible = true,
                                    onDismiss = { urCurtainTen = false },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    if (tenSettled) {
                        TenCurtainCall(
                            batch = batch,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 52.dp),
                        )
                    }
                }
                RevealStage.Done -> Unit
            }
        }
        Text(
            text = "点击跳过",
            color = Color.White.copy(alpha = 0.38f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
