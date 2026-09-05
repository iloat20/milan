package com.milan.game.ui.gacha

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.PullResult
import com.milan.game.ui.theme.AppTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 赛博霓虹抽卡演出（2026-08-20 新演出层，替换旧「八卦法阵 + 大立绘弹出」方案）。
 *
 * 设计决策：
 * - 纯 Compose / Canvas 实现，零新依赖（RenderEffect 需 API 31+，弃用，全版本表现一致）。
 * - 色板独立于此文件（AppTheme 与他人并行修改，直接改会冲突/被覆盖）。
 * - 与 GpuEffects（AGSL RuntimeShader）解耦：新演出全 Canvas 自绘。
 * - 稀有度语言沿用全局惯例：R=1 / SR=2 / SSR=3 / UR=4，颜色走 AppTheme.rarityColor。
 *
 * 分镜（由 [RevealStage] 驱动，GachaScreen 持有 stage 并按序推进）：
 *   1. Charge 蓄能：粒子从屏缘汇聚 + 双色弧线环绕 + 能量球脉动（≈420ms）→ CyberCharge.kt
 *   2. Beam  光柱：次元裂缝开启，光柱上冲 + 火花上升 + 底部能量环扩散（≈480ms）→ CyberBeam.kt
 *   3. Single 单抽揭晓：故障翻入大立绘卡 + 稀有度光效 + 签文（≈1500ms，可跳过）→ CyberCards.kt
 *   4. Ten   十连牌桌：2×5 卡背飞入、逐张翻牌（间隔 300ms），SSR/UR 翻开瞬间震屏 → CyberCards.kt
 *   5. Done  收尾（组件卸载）
 *
 * 全程整屏可点击跳过（onSkip），点卡片直接进入角色详情（onOpenCharacter）。
 * P4-2（2026-08-27）：按分镜拆分四文件，本文件保留阶段枚举 / 色板 / 共享粒子与文字件 /
 * 背景与故障层 / 待机枢纽 [CyberHerald] / 总装层 [CyberRevealLayer]。
 */
enum class RevealStage { Charge, Beam, Single, Ten, Done }

/**
 * 水墨国风演出色板（仅演出族文件使用；不动 AppTheme —— 该文件与他人并行修改中）。
 * 从赛博霓虹（青/品红/紫）迁移到水墨调性（朱砂/金箔/石青/浓墨/宣纸白）。
 * 命名保持 Cyan/Magenta/VioletGlow 等不变，以最小化对 CyberCards/Beam/Charge 的侵入。
 */
internal object CyberPalette {
    /** 朱砂红（主色）。命名 Cyan 为历史遗留（原赛博霓虹色），改名需同步 CyberCards/Beam/Charge。 */
    val Cyan = AppTheme.SealRed
    /** 金箔（辅色）。命名 Magenta 为历史遗留。 */
    val Magenta = AppTheme.Gold
    /** 石青淡彩。命名 VioletGlow 为历史遗留。 */
    val VioletGlow = AppTheme.Frost
    /** 浓墨深底。 */
    val DeepBg = AppTheme.BgDeepest
    /** 宣纸白（光柱核心）。 */
    val BeamCore = AppTheme.Text1
    /** 淡墨网格线。 */
    val Grid = AppTheme.Text1.copy(alpha = 0.15f)
}

/** CyberHerald 内核辉光渐变色板（文件级常量：配合 drawWithCache 消除逐帧 Brush/色表分配）。 */
private val HeraldGlowColors = listOf(
    CyberPalette.BeamCore,
    CyberPalette.Cyan.copy(alpha = 0.7f),
    CyberPalette.VioletGlow.copy(alpha = 0.2f),
    Color.Transparent,
)

/** 演出粒子（蓄能汇聚 / 光柱火花共用；坐标归一化，绘制时乘画布尺寸）。 */
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

/** 水墨双影文字：朱砂/金箔残影错位 + 主色正文。 */
@Composable
internal fun GlitchText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Text(
            text = text,
            color = CyberPalette.Cyan,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = 2.dp),
        )
        Text(
            text = text,
            color = CyberPalette.Magenta,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = (-2).dp),
        )
        Text(text = text, color = color, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

/**
 * 全息网格背景：透视会聚垂直线 + 缓速前滚水平线 + 星尘闪烁。
 * 时间用 withFrameNanos 驱动（演出层存活期间持续刷新；卸载即取消协程）。
 */
@Composable
private fun NeonGridBackdrop(modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nano -> time = nano / 1_000_000_000f }
        }
    }
    val stars = remember {
        val r = Random(20260820)
        List(24) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    Canvas(modifier) {
        val spacing = 44.dp.toPx()
        val scroll = (time * 52f) % spacing
        var y = scroll
        while (y < size.height) {
            drawLine(CyberPalette.Grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += spacing
        }
        val vpX = size.width * 0.5f
        val vpY = size.height * 0.32f
        for (i in -5..5) {
            val bottom = vpX + i * 86.dp.toPx()
            val top = vpX + i * 12.dp.toPx()
            drawLine(CyberPalette.Grid, Offset(bottom, size.height), Offset(top, vpY), strokeWidth = 1f)
        }
        stars.forEach { (x, y, phase) ->
            val tw = (sin(time * 2f + phase * 6.28f) + 1f) * 0.5f
            drawCircle(
                color = CyberPalette.Cyan.copy(alpha = 0.10f + tw * 0.25f),
                radius = 1.2.dp.toPx(),
                center = Offset(x * size.width, y * size.height),
            )
        }
    }
}

/** 水墨晕染闪烁层：随机朱砂/金箔条带（seed 每 90ms 重掷，营造墨迹渗透感）。 */
@Composable
private fun GlitchField(modifier: Modifier = Modifier) {
    var seed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(90)
            seed = Random.nextInt()
        }
    }
    Canvas(modifier) {
        if (seed == 0) return@Canvas
        val rnd = Random(seed)
        repeat(4) { i ->
            val y = rnd.nextFloat() * size.height
            val h = (2f + rnd.nextFloat() * 9f).dp.toPx()
            val x = rnd.nextFloat() * size.width * 0.35f
            val w = size.width * (0.2f + rnd.nextFloat() * 0.55f)
            drawRect(
                color = if (i % 2 == 0) CyberPalette.Cyan else CyberPalette.Magenta,
                topLeft = Offset(x, y),
                size = Size(w, h),
                alpha = 0.05f + rnd.nextFloat() * 0.09f,
            )
        }
    }
}

/**
 * 待机能量枢纽（替换旧八卦法阵的常驻演出，GachaScreen 主界面抽卡区）。
 * 虚线外环 + 金箔旋转刻度弧 + 朱砂内核脉动；180° 周期 14s，内核呼吸 1.8s。
 */
@Composable
fun CyberHerald(modifier: Modifier = Modifier, testMode: Boolean = false) {
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
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "heraldPulse",
    )
    Box(
        modifier = modifier
            .size(196.dp)
            .clip(CircleShape)
            .background(CyberPalette.DeepBg.copy(alpha = 0.85f))
            .border(1.dp, CyberPalette.Cyan.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // 性能：drawWithCache 按 size 缓存辉光渐变 Brush——spin/pulse 只在 onDrawBehind 内读取，
        // 动画帧仅触发重绘、不重建缓存；此前 Canvas 每帧 new Brush.radialGradient 持续分配。
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val d = size.minDimension.coerceAtLeast(1f)
                    val c = Offset(d / 2f, d / 2f)
                    val glowBrush = Brush.radialGradient(HeraldGlowColors, center = c, radius = d * 2f)
                    onDrawBehind {
                        drawCircle(
                            color = CyberPalette.Cyan.copy(alpha = 0.5f),
                            radius = d * 0.46f,
                            center = c,
                            style = Stroke(1.5.dp.toPx()),
                        )
                        drawArc(
                            color = CyberPalette.Magenta.copy(alpha = 0.45f),
                            startAngle = spin,
                            sweepAngle = 120f,
                            useCenter = false,
                            topLeft = Offset(d * 0.08f, d * 0.08f),
                            size = Size(d * 0.84f, d * 0.84f),
                            style = Stroke(2.dp.toPx()),
                        )
                        val r = d * (0.16f + 0.025f * pulse)
                        // 脉冲用 scale 变换实现：渐变按常量半径构建后整体缩放，
                        // 与「每帧按 r 重建渐变」数学等价（径向渐变均匀缩放不变），零逐帧分配。
                        scale(scaleX = r / d, scaleY = r / d, pivot = c) {
                            drawCircle(brush = glowBrush, radius = d, center = c)
                        }
                    }
                },
        )
        // 墨粒轨道：8 颗墨点绕中心旋转，营造水墨丹青仪式感
        if (!testMode) InkParticles(Modifier.fillMaxSize())
        Text("✦", color = CyberPalette.BeamCore, fontSize = 30.sp)
    }
}

/**
 * 待机枢纽内嵌墨粒轨道：8 颗墨点沿椭圆轨道绕中心旋转，
 * 半径/速度/相位各异，配合 sin 波产生有机呼吸感。
 */
@Composable
private fun InkParticles(modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nano -> time = nano / 1_000_000_000f }
        }
    }
    // 固定种子保证每次渲染一致
    val particles = remember {
        val r = Random(20260829)
        List(8) { i ->
            val radius = 0.34f + r.nextFloat() * 0.14f // 0.34~0.48（归一化，乘画布半径）
            val speed = 0.45f + r.nextFloat() * 0.3f   // 0.45~0.75 rad/s
            val phase = r.nextFloat() * 6.28f
            val wobbleAmp = 0.02f + r.nextFloat() * 0.03f
            val dotRadius = 2.5f + r.nextFloat() * 2f   // dp
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
            val py = cy + r * sin(angle) * 0.85f // 椭圆压扁
            drawCircle(
                color = CyberPalette.Cyan.copy(alpha = 0.28f),
                radius = dotR.dp.toPx(),
                center = Offset(px, py),
            )
        }
    }
}

/**
 * 演出总装：浓墨深底 + 水墨网格 + 晕染层 + 按 [RevealStage] 渲染阶段内容 + 震屏 + 整屏点击跳过。
 * 翻到 SSR/UR 触发震屏（Single 进入时按稀有度，Ten 翻开时按单卡稀有度）。
 * stage == Done 时调用方不应组合本组件（外层 if 控制）。
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
    // P2 修复：以自增序号作重启键——十连中连续同稀有度翻牌会写入相同幅度
    // （两张 SSR 都设 14f），以幅度为 key 时 LaunchedEffect 值不变不会重启，第二次震屏丢失；
    // 序号每次触发必然变化，保证逐次重放。
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
        NeonGridBackdrop(Modifier.fillMaxSize())
        GlitchField(Modifier.fillMaxSize())
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
                    ChargeCore(Modifier.fillMaxSize())
                    GlitchText(
                        text = when {
                            singleRarity >= 4 -> "古卷展开 · 浓墨蓄力"
                            singleRarity == 3 -> "金箔凝聚 · 丹青觉醒"
                            singleRarity == 2 -> "墨迹汇聚 · 灵力涌动"
                            else -> "次元裂缝 · 充能中"
                        },
                        color = CyberPalette.Cyan,
                        fontSize = 15.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp),
                    )
                }
                RevealStage.Beam -> {
                    RiftBeam(Modifier.fillMaxSize())
                    GlitchText(
                        text = when {
                            singleRarity >= 4 -> "裂缝开启 · 万古回响"
                            singleRarity == 3 -> "裂缝开启 · 金光乍现"
                            else -> "裂缝开启"
                        },
                        color = when {
                            singleRarity >= 4 -> CyberPalette.Magenta
                            else -> CyberPalette.BeamCore
                        },
                        fontSize = 22.sp,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 150.dp),
                    )
                }
                RevealStage.Single -> {
                    SingleCard(
                        def = singleDef,
                        rarity = singleRarity,
                        fortune = fortune,
                        cardIn = cardIn,
                        onOpenCharacter = onOpenCharacter,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    if (singleRarity >= 2) {
                        GlitchText(
                            text = "${rarityLabel(singleRarity)}!",
                            color = AppTheme.rarityColor(singleRarity),
                            fontSize = 34.sp,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
                        )
                    }
                }
                RevealStage.Ten -> {
                    TenTable(
                        batch = batch,
                        onFlip = innerOnFlip,
                        onOpenCharacter = onOpenCharacter,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    val maxR = batch.maxOfOrNull { it.rarity } ?: 1
                    if (maxR >= 2) {
                        GlitchText(
                            text = "${rarityLabel(maxR)}!",
                            color = AppTheme.rarityColor(maxR),
                            fontSize = 30.sp,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 26.dp),
                        )
                    }
                }
                RevealStage.Done -> Unit
            }
        }
        Text(
            text = "点击跳过",
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
        )
    }
}
