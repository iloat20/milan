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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.PullResult
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.components.PortraitTarget
import com.milan.game.ui.theme.AppTheme
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
 *   1. Charge 蓄能：粒子从屏缘汇聚 + 双色弧线环绕 + 能量球脉动（≈420ms）
 *   2. Beam  光柱：次元裂缝开启，光柱上冲 + 火花上升 + 底部能量环扩散（≈480ms）
 *   3. Single 单抽揭晓：故障翻入大立绘卡 + 稀有度光效 + 签文（≈1500ms，可跳过）
 *   4. Ten   十连牌桌：2×5 卡背飞入、逐张翻牌（间隔 300ms），SSR/UR 翻开瞬间震屏
 *   5. Done  收尾（组件卸载）
 *
 * 全程整屏可点击跳过（onSkip），点卡片直接进入角色详情（onOpenCharacter）。
 */
enum class RevealStage { Charge, Beam, Single, Ten, Done }

/** 赛博霓虹演出色板（仅本文件使用；不动 AppTheme —— 该文件与他人并行修改中）。 */
private object CyberPalette {
    /** 霓虹青（主色） */
    val Cyan = Color(0xFF00E5FF)
    /** 霓虹品红（辅色） */
    val Magenta = Color(0xFFFF2DD1)
    /** 暮紫辉光 */
    val VioletGlow = Color(0xFF7A5CFF)
    /** 演出深底 */
    val DeepBg = Color(0xFF05060F)
    /** 光柱核心（近白） */
    val BeamCore = Color(0xFFE0F9FF)
    /** 网格线 */
    val Grid = Color(0x2600E5FF)
}

/** CyberHerald 内核辉光渐变色板（文件级常量：配合 drawWithCache 消除逐帧 Brush/色表分配）。 */
private val HeraldGlowColors = listOf(
    CyberPalette.BeamCore,
    CyberPalette.Cyan.copy(alpha = 0.7f),
    CyberPalette.VioletGlow.copy(alpha = 0.2f),
    Color.Transparent,
)

/** 演出粒子（蓄能汇聚 / 光柱火花共用；坐标归一化，绘制时乘画布尺寸）。 */
private class CyberParticle(
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

/** 故障双影文字：青/品红残影错位 + 主色正文。 */
@Composable
private fun GlitchText(
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

/**
 * 蓄能漩涡（Charge 阶段）：粒子从四边汇聚至中心 + 三环弧线旋转 + 中心能量球脉动。
 * 中心点取画布 42% 高度（给上方稀有度大字留位）。
 */
@Composable
private fun ChargeCore(modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nano -> time = nano / 1_000_000_000f }
        }
    }
    val particles = remember {
        val r = Random(20260821)
        List(36) { i ->
            val side = i % 4
            val sx = when (side) {
                0 -> r.nextFloat()
                1 -> 1f
                else -> r.nextFloat()
            }
            val sy = when (side) {
                2 -> r.nextFloat()
                3 -> 1f
                else -> r.nextFloat()
            }
            CyberParticle(
                startX = sx,
                startY = sy,
                endX = 0.5f,
                endY = 0.42f,
                delaySec = r.nextFloat() * 0.25f,
                durSec = 0.35f + r.nextFloat() * 0.35f,
                radiusDp = 1.5f + r.nextFloat() * 2.5f,
                color = if (r.nextBoolean()) CyberPalette.Cyan else CyberPalette.Magenta,
            )
        }
    }
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.42f
        particles.forEach { p ->
            val local = ((time - p.delaySec) / p.durSec).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val e = 1f - (1f - local) * (1f - local)
            drawCircle(
                color = p.color.copy(alpha = (1f - local) * 0.9f),
                radius = p.radiusDp.dp.toPx(),
                center = Offset(lerp(p.startX * size.width, cx, e), lerp(p.startY * size.height, cy, e)),
            )
        }
        repeat(3) { i ->
            val r = (70f + i * 26f).dp.toPx()
            val start = (time * 130f + i * 120f) % 360f
            drawArc(
                color = if (i % 2 == 0) CyberPalette.Cyan.copy(alpha = 0.5f) else CyberPalette.Magenta.copy(alpha = 0.4f),
                startAngle = start,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        val pulse = 0.85f + 0.15f * sin(time * 5f)
        val radius = 110.dp.toPx() * pulse
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    CyberPalette.BeamCore,
                    CyberPalette.Cyan.copy(alpha = 0.6f),
                    CyberPalette.VioletGlow.copy(alpha = 0.15f),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = radius,
            ),
            radius = radius,
            center = Offset(cx, cy),
        )
    }
}

/**
 * 次元光柱（Beam 阶段）：光柱自底部上冲 + 火花沿柱上升 + 顶部辉光 + 底部能量环扩散。
 */
@Composable
private fun RiftBeam(modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nano -> time = nano / 1_000_000_000f }
        }
    }
    val sparks = remember {
        val r = Random(20260822)
        List(26) {
            CyberParticle(
                startX = 0.5f,
                startY = 1f,
                endX = 0.42f + r.nextFloat() * 0.16f,
                endY = r.nextFloat() * 0.85f,
                delaySec = r.nextFloat() * 0.5f,
                durSec = 0.45f + r.nextFloat() * 0.4f,
                radiusDp = 1.2f + r.nextFloat() * 2.2f,
                color = if (r.nextBoolean()) CyberPalette.Cyan else CyberPalette.BeamCore,
            )
        }
    }
    Canvas(modifier) {
        val cx = size.width / 2f
        val progress = (time / 0.55f).coerceIn(0f, 1f)
        val e = 1f - (1f - progress) * (1f - progress)
        val beamH = size.height * e
        val w = 26.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    CyberPalette.Cyan.copy(alpha = 0.65f),
                    CyberPalette.BeamCore.copy(alpha = 0.95f),
                    CyberPalette.Cyan.copy(alpha = 0.65f),
                    Color.Transparent,
                ),
                startY = size.height - beamH,
                endY = size.height,
            ),
            topLeft = Offset(cx - w / 2f, size.height - beamH),
            size = Size(w, beamH),
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, CyberPalette.VioletGlow.copy(alpha = 0.35f), Color.Transparent),
                startY = size.height - beamH,
                endY = size.height,
            ),
            topLeft = Offset(cx - 84.dp.toPx(), size.height - beamH),
            size = Size(168.dp.toPx(), beamH),
        )
        drawCircle(
            color = CyberPalette.Cyan.copy(alpha = 0.55f),
            radius = 26.dp.toPx(),
            center = Offset(cx, size.height - beamH),
        )
        val ringR = 30.dp.toPx() + progress * 170.dp.toPx()
        drawCircle(
            color = CyberPalette.BeamCore.copy(alpha = (1f - progress) * 0.5f),
            radius = ringR,
            center = Offset(cx, size.height),
            style = Stroke(3.dp.toPx()),
        )
        drawCircle(
            color = CyberPalette.Cyan.copy(alpha = (1f - progress) * 0.35f),
            radius = ringR * 0.7f,
            center = Offset(cx, size.height),
            style = Stroke(1.5.dp.toPx()),
        )
        sparks.forEach { p ->
            val local = ((time - p.delaySec) / p.durSec).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            drawCircle(
                color = p.color.copy(alpha = (1f - local) * 0.9f),
                radius = p.radiusDp.dp.toPx(),
                center = Offset(
                    lerp(p.startX * size.width, p.endX * size.width, local),
                    lerp(p.startY * size.height, p.endY * size.height, local),
                ),
            )
        }
    }
}

/** 故障闪烁层：随机青/品红条带（seed 每 90ms 重掷，营造数据错乱感）。 */
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

/** 赛博卡背（十连牌桌待翻面）：深底 + 网格 + 品红能量环 + 中央徽记。 */
@Composable
private fun CyberCardBack(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CyberPalette.DeepBg.copy(alpha = 0.92f))
            .border(1.dp, CyberPalette.Cyan.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val sp = 10.dp.toPx()
            var y = sp
            while (y < size.height) {
                drawLine(CyberPalette.Cyan.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), 1f)
                y += sp
            }
            var x = sp
            while (x < size.width) {
                drawLine(CyberPalette.Cyan.copy(alpha = 0.10f), Offset(x, 0f), Offset(x, size.height), 1f)
                x += sp
            }
            drawCircle(
                color = CyberPalette.Magenta.copy(alpha = 0.30f),
                radius = size.minDimension * 0.22f,
                center = center,
                style = Stroke(1.5.dp.toPx()),
            )
        }
        Text("✦", color = CyberPalette.Cyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * 待机能量枢纽（替换旧八卦法阵的常驻演出，GachaScreen 主界面抽卡区）。
 * 虚线外环 + 品红旋转刻度弧 + 青紫内核脉动；180° 周期 14s，内核呼吸 1.8s。
 */
@Composable
fun CyberHerald(modifier: Modifier = Modifier) {
    val spin by rememberInfiniteTransition(label = "herald").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "heraldSpin",
    )
    val pulse by rememberInfiniteTransition(label = "heraldPulse").animateFloat(
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
                    val d = size.minDimension
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
        Text("✦", color = CyberPalette.BeamCore, fontSize = 30.sp)
    }
}

/**
 * 单抽揭晓卡（Single 阶段）：故障翻入（scaleY 弹簧弹跳 + scaleX 抖动 + 下移归位），
 * 稀有度描边 + 立绘氛围圈（aura）+ 底部铭牌 + 稀有度故障大字 + 命运签文。点卡进角色详情。
 */
@Composable
private fun SingleCard(
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
        Box(
            Modifier
                .width(232.dp)
                .height(300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.5.dp, frame, RoundedCornerShape(14.dp)),
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
private fun TenTable(
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
            delay(300L)
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

/** 十连单卡：飞入（透明度 + 下移）→ 翻牌（scaleX 两段动画，中点切正/背面）。 */
@Composable
private fun TenCard(
    result: PullResult,
    up: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enterY by animateFloatAsState(if (up) 0f else 46f, tween(320), label = "tenY")
    val cardAlpha by animateFloatAsState(if (up) 1f else 0f, tween(260), label = "tenAlpha")
    val flip = remember { Animatable(1f) }
    var faceUp by remember { mutableStateOf(false) }
    LaunchedEffect(up) {
        if (up && !faceUp) {
            flip.animateTo(0f, tween(130))
            faceUp = true
            flip.animateTo(1f, tween(150))
        }
    }
    val frame = AppTheme.rarityColor(result.rarity).copy(alpha = 0.7f)
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
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, frame, RoundedCornerShape(8.dp)),
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
        } else {
            CyberCardBack(Modifier.fillMaxSize())
        }
    }
}

/**
 * 演出总装：深底 + 全息网格 + 故障层 + 按 [RevealStage] 渲染阶段内容 + 震屏 + 整屏点击跳过。
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
                        text = "次元裂缝 · 充能中",
                        color = CyberPalette.Cyan,
                        fontSize = 15.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp),
                    )
                }
                RevealStage.Beam -> {
                    RiftBeam(Modifier.fillMaxSize())
                    GlitchText(
                        text = "裂缝开启",
                        color = CyberPalette.BeamCore,
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