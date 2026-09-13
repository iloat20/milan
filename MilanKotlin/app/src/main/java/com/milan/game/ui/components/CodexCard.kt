package com.milan.game.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.milan.game.ui.effects.HolographicFoilOverlay
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementIdentity
import com.milan.game.ui.theme.ElementTheme
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * CodexCard — **实体收藏卡外壳**。
 *
 * v4.3 实体感（真厚度 + 可投影 + 点按持卡对光）：
 * ```
 *  ① 卡砖层（下偏移 + 系统阴影）→ 卡「垫」在台面上
 *  ② 纸厚 ply → 真卡切边
 *  ③ 卡面倒角（顶/左高光、底/右暗边）
 *  ④ 点按：rotationX/Y 倾转 + 对光高光/箔面跟手（设计语言「持卡对光」）
 *  ⑤ 纸纹 / UR 箔
 * ```
 * 铁律：`graphicsLayer` **不得** `clip = true`，否则系统阴影被裁进卡内。
 * 可点按卡默认开启倾角；网格无 onClick 的卡走静态壳。
 */
@Composable
fun CodexCard(
    tier: Int,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(CardMetrics.Corner),
    /** 卡边厚度（纸厚感）；网格里可传 2.dp 减负。 */
    edge: Dp = CardMetrics.Edge,
    /** 元素 id（Metal/Water…），驱动框色与角标。 */
    element: String? = null,
    /** 角色 id，微调纸纹/角标形态（同稀有度不同角色可辨）。 */
    characterId: String? = null,
    onClick: (() -> Unit)? = null,
    /** 点按持卡对光；默认仅可点卡开启。揭晓卡可单独打开（不导航）。 */
    enableTilt: Boolean = onClick != null,
    content: @Composable BoxScope.() -> Unit,
) {
    val rarityCol = AppTheme.rarityColor(tier)
    val grad = AppTheme.rarityGradient(tier)
    val elem: ElementIdentity? = element?.let { ElementTheme.forElement(it) }

    val frameColor = elem?.glow ?: rarityCol
    val borderWidth = when {
        tier >= 4 -> 2.dp
        tier == 3 -> 1.5.dp
        tier == 2 -> 1.25.dp
        else -> 1.dp
    }
    val borderAlpha = when {
        tier >= 4 -> 0.9f
        tier == 3 -> 0.75f
        tier == 2 -> 0.55f
        else -> 0.4f
    }

    val ssrBreath by rememberInfiniteTransition(label = "codex_ssr_breath").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "ssr_glow",
    )
    val urFlow by rememberInfiniteTransition(label = "codex_ur_flow").animateFloat(
        initialValue = 0.02f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse),
        label = "ur_flow",
    )

    val urBorderBrush = if (tier >= 4) {
        val hiPos = 0.05f + urFlow
        Brush.sweepGradient(
            0f to frameColor.copy(alpha = borderAlpha),
            hiPos * 0.5f to frameColor.copy(alpha = borderAlpha),
            hiPos to AppTheme.GoldHi,
            (hiPos + 0.18f).coerceAtMost(0.99f) to (elem?.from ?: rarityCol).copy(alpha = borderAlpha),
            1f to frameColor.copy(alpha = borderAlpha),
        )
    } else null

    // 点按持卡：捏起 + 对光倾转（网格无 onClick 时默认关，揭晓卡可 enableTilt=true）
    val tiltEnabled = enableTilt
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    var foilX by remember { mutableFloatStateOf(0.5f) }
    var foilY by remember { mutableFloatStateOf(0.42f) }
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    val lift = remember { Animatable(1f) }
    val lightAmt = remember { Animatable(0f) }

    val corner = CardMetrics.Corner
    val innerShape = RoundedCornerShape(corner - edge)
    val stockHash = remember(characterId) { (characterId ?: "").hashCode() }
    val thickness = when {
        tier >= 4 -> 6.dp
        tier == 3 -> 5.dp
        tier == 2 -> 4.dp
        else -> 3.dp
    }

    // 外层预留厚度，避免卡砖叠进邻居格子
    Box(modifier = modifier.padding(end = thickness, bottom = thickness)) {
        // 整卡 3D：砖 + 面同轴倾转；相机距离拉开透视
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationX = rotX.value
                    rotationY = rotY.value
                    cameraDistance = 18f * density
                    scaleX = lift.value
                    scaleY = lift.value
                },
        ) {
            // ① 卡砖：下偏移实体层 + 系统阴影（禁止 clip，否则阴影被裁）
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = thickness.toPx() * 0.65f
                        translationY = thickness.toPx()
                        this.scaleX = 0.98f
                        this.scaleY = 0.98f
                        shadowElevation = (14f + tier * 3.5f) * lift.value
                        ambientShadowColor = Color.Black
                        spotShadowColor = rarityCol.copy(alpha = 0.45f + lightAmt.value * 0.25f)
                    }
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                frameColor.copy(alpha = 0.35f),
                                Color(0xFF2A2E34),
                                Color(0xFF12151A),
                                Color(0xFF0A0C10),
                            ),
                        ),
                        shape,
                    )
                    .drawWithContent {
                        val w = size.width
                        val h = size.height
                        val t = thickness.toPx()
                        val plyA = if (tier >= 4) 0.22f else 0.14f
                        drawLine(
                            color = Color.White.copy(alpha = plyA),
                            start = Offset(w - t * 0.4f, 0f),
                            end = Offset(w - t * 0.4f, h),
                            strokeWidth = 0.6f,
                        )
                        drawLine(
                            color = Color.White.copy(alpha = plyA * 0.85f),
                            start = Offset(0f, h - t * 0.4f),
                            end = Offset(w, h - t * 0.4f),
                            strokeWidth = 0.6f,
                        )
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.35f),
                            ),
                        )
                    },
            )

            // ② 卡面
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                frameColor.copy(alpha = 0.28f),
                                AppTheme.SurfaceNested,
                                AppTheme.BgDeepest,
                                Color(0xFF080A0C),
                            ),
                        ),
                        shape,
                    )
                    .then(
                        if (tiltEnabled) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = { offset ->
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        val nx = (offset.x / w).coerceIn(0f, 1f)
                                        val ny = (offset.y / h).coerceIn(0f, 1f)
                                        foilX = nx
                                        foilY = ny
                                        // 触点 → 倾角：像捏着卡角对着光转
                                        val maxTilt = CardMetrics.TiltMaxDeg
                                        val targetY = (nx - 0.5f) * 2f * maxTilt
                                        val targetX = -(ny - 0.5f) * 2f * maxTilt
                                        scope.launch { rotX.animateTo(targetX, spring(dampingRatio = 0.7f)) }
                                        scope.launch { rotY.animateTo(targetY, spring(dampingRatio = 0.7f)) }
                                        scope.launch { lightAmt.animateTo(1f, tween(110)) }
                                        scope.launch { lift.animateTo(1.04f, spring(dampingRatio = 0.65f)) }
                                        tryAwaitRelease()
                                        scope.launch {
                                            rotX.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 200f))
                                        }
                                        scope.launch {
                                            rotY.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 200f))
                                        }
                                        scope.launch { lightAmt.animateTo(0f, tween(260)) }
                                        scope.launch { lift.animateTo(1f, spring()) }
                                        currentOnClick?.invoke()
                                    },
                                )
                            }
                        } else Modifier
                    ),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(innerShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    frameColor.copy(alpha = 0.22f),
                                    AppTheme.BgMid,
                                    AppTheme.BgDeepest,
                                ),
                            ),
                            innerShape,
                        )
                        .border(
                            width = borderWidth,
                            brush = urBorderBrush ?: Brush.linearGradient(
                                listOf(
                                    frameColor.copy(alpha = borderAlpha),
                                    (elem?.from ?: grad.first()).copy(alpha = borderAlpha * 0.65f),
                                    frameColor.copy(alpha = borderAlpha * 0.85f),
                                ),
                            ),
                            shape = innerShape,
                        )
                        .padding(if (tier >= 4) 2.dp else 1.dp)
                        .drawWithContent {
                            drawContent()
                            drawPaperGrain(stockHash)
                            drawPrintLighting()
                            if (elem != null) {
                                drawElementAccent(elem.glow)
                            }
                            drawCharacterCornerAccent(stockHash, frameColor, ssrBreath)
                            if (tier >= 4) {
                                drawFoilSheen(urFlow)
                            }
                            drawTiltLight(
                                tiltX = rotX.value,
                                tiltY = rotY.value,
                                intensity = lightAmt.value,
                                frameColor = frameColor,
                                gold = tier >= 4,
                            )
                        },
                ) {
                    content()
                    if (tier >= 4) {
                        HolographicFoilOverlay(
                            modifier = Modifier.fillMaxSize(),
                            active = true,
                            touchX = foilX,
                            touchY = foilY,
                        )
                    }
                }

                // ③ 倒角/切缝：随倾角吃光加强
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawFaceBevel(frameColor, tier, lightAmt.value, rotX.value, rotY.value)
                        },
                )
            }
        }
    }
}

/** 卡牌度量。 */
object CardMetrics {
    /** 卡角（实体卡偏小圆角）。 */
    val Corner = 10.dp

    /** 卡边内缩（工艺框与卡边的纸厚）。 */
    val Edge = 4.dp

    /** 实体卡侧厚条宽（右/底）。 */
    val Thickness = 3.dp

    /** 点按持卡最大倾角（度）。 */
    const val TiltMaxDeg = 13f

    /** 画心/整卡比例 ≈ 0.71（近 5:7）。 */
    const val Aspect = 0.71f
}

/**
 * 点按对光：径向高光斑跟倾角走。
 * rotationY > 0（右缘抬起）→ 光斑偏左；rotationX < 0（顶缘抬起）→ 光斑偏下。
 */
private fun DrawScope.drawTiltLight(
    tiltX: Float,
    tiltY: Float,
    intensity: Float,
    frameColor: Color,
    gold: Boolean,
) {
    if (intensity < 0.02f) return
    val max = CardMetrics.TiltMaxDeg
    val nx = (0.5f - (tiltY / max) * 0.35f).coerceIn(0.1f, 0.9f)
    val ny = (0.5f + (tiltX / max) * 0.35f).coerceIn(0.1f, 0.9f)
    val cx = size.width * nx
    val cy = size.height * ny
    val radius = size.minDimension * 0.78f
    drawRect(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.20f * intensity),
            0.28f to Color.White.copy(alpha = 0.10f * intensity),
            0.55f to frameColor.copy(alpha = 0.14f * intensity),
            1f to Color.Transparent,
            center = Offset(cx, cy),
            radius = radius,
        ),
    )
    if (gold) {
        drawRect(
            brush = Brush.radialGradient(
                0f to AppTheme.GoldHi.copy(alpha = 0.16f * intensity),
                0.4f to AppTheme.Gold.copy(alpha = 0.08f * intensity),
                1f to Color.Transparent,
                center = Offset(cx + size.width * 0.08f, cy - size.height * 0.06f),
                radius = radius * 0.7f,
            ),
        )
    }
    val edgeA = 0.12f * intensity
    if (tiltY > 1f) {
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = edgeA), Color.Transparent),
                startX = 0f,
                endX = size.width * 0.22f,
            ),
        )
    } else if (tiltY < -1f) {
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = edgeA)),
                startX = size.width * 0.78f,
                endX = size.width,
            ),
        )
    }
    if (tiltX < -1f) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = edgeA * 0.85f)),
                startY = size.height * 0.78f,
                endY = size.height,
            ),
        )
    } else if (tiltX > 1f) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = edgeA * 0.85f), Color.Transparent),
                startY = 0f,
                endY = size.height * 0.22f,
            ),
        )
    }
}

/**
 * 卡面倒角：顶/左吃光高光 + 与下层卡砖的暗缝；倾角时吃光一侧加强。
 */
private fun DrawScope.drawFaceBevel(
    frameColor: Color,
    tier: Int,
    light: Float = 0f,
    tiltX: Float = 0f,
    tiltY: Float = 0f,
) {
    val w = size.width
    val h = size.height
    val bevel = 1.8f
    val lightBoost = 1f + light * 0.9f
    val topA = (0.16f * lightBoost).coerceAtMost(0.42f)
    val leftA = (0.11f * lightBoost).coerceAtMost(0.36f)
    drawLine(
        color = Color.White.copy(alpha = topA),
        start = Offset(0f, bevel * 0.5f),
        end = Offset(w, bevel * 0.5f),
        strokeWidth = bevel,
    )
    drawLine(
        color = Color.White.copy(alpha = leftA),
        start = Offset(bevel * 0.5f, 0f),
        end = Offset(bevel * 0.5f, h),
        strokeWidth = bevel,
    )
    val botDark = (0.45f + if (tiltX > 1f) 0.12f * light else 0f).coerceAtMost(0.65f)
    val rightDark = (0.4f + if (tiltY < -1f) 0.12f * light else 0f).coerceAtMost(0.6f)
    drawLine(
        color = Color.Black.copy(alpha = rightDark),
        start = Offset(w - 0.8f, 0f),
        end = Offset(w - 0.8f, h),
        strokeWidth = 1.4f,
    )
    drawLine(
        color = Color.Black.copy(alpha = botDark),
        start = Offset(0f, h - 0.8f),
        end = Offset(w, h - 0.8f),
        strokeWidth = 1.4f,
    )
    if (tier >= 4) {
        drawLine(
            color = AppTheme.GoldHi.copy(alpha = 0.28f + light * 0.2f),
            start = Offset(0f, 1.2f),
            end = Offset(w, 1.2f),
            strokeWidth = 0.8f,
        )
        drawLine(
            color = frameColor.copy(alpha = 0.25f + light * 0.15f),
            start = Offset(1.2f, 0f),
            end = Offset(1.2f, h),
            strokeWidth = 0.8f,
        )
    }
}

/** 印刷层打光：顶光 + 底影。 */
private fun DrawScope.drawPrintLighting() {
    drawRect(
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.07f),
                Color.White.copy(alpha = 0.02f),
                Color.Transparent,
            ),
            endY = size.height * 0.28f,
        ),
    )
    drawRect(
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.72f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.22f),
        ),
    )
}

/** 元素角标条（左上短竖）。 */
private fun DrawScope.drawElementAccent(glow: Color) {
    val cw = 3.dp.toPx()
    val ch = 18.dp.toPx()
    drawRoundRect(
        color = glow.copy(alpha = 0.75f),
        topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
        size = Size(cw, ch),
        cornerRadius = CornerRadius(1.dp.toPx()),
    )
}

/** 纸纹：稀疏噪点，hash 控制种子密度，不同角色观感略有差。 */
private fun DrawScope.drawPaperGrain(hash: Int) {
    val seed = abs(hash)
    val step = (10 + seed % 5).dp.toPx()
    val alphaBase = 0.018f + (seed % 3) * 0.004f
    var y = step * 0.5f
    while (y < size.height) {
        var x = step * 0.35f
        while (x < size.width) {
            val h = ((x * 13 + y * 7 + seed) * 0.013f) % 1f
            if (h < 0.22f) {
                drawCircle(
                    color = Color.White.copy(alpha = alphaBase + h * 0.02f),
                    radius = 0.6f.dp.toPx(),
                    center = Offset(x, y),
                )
            }
            x += step
        }
        y += step
    }
}

/**
 * 角色角标：hash 决定形态（直角 / 双线 / 点阵），SSR+ 带呼吸。
 */
private fun DrawScope.drawCharacterCornerAccent(hash: Int, color: Color, breath: Float) {
    val mode = abs(hash) % 3
    val inset = 6.dp.toPx()
    val len = 12.dp.toPx()
    val sw = 1.4f
    val a = if (breath > 0f) breath else 0.55f
    val w = size.width
    val h = size.height
    when (mode) {
        0 -> {
            drawLine(color.copy(alpha = a), Offset(w - inset - len, h - inset), Offset(w - inset, h - inset), sw)
            drawLine(color.copy(alpha = a), Offset(w - inset, h - inset - len), Offset(w - inset, h - inset), sw)
        }
        1 -> {
            drawLine(color.copy(alpha = a * 0.9f), Offset(inset, h - inset), Offset(inset + len * 0.7f, h - inset), sw)
            drawLine(
                color.copy(alpha = a * 0.9f),
                Offset(inset, h - inset - 4.dp.toPx()),
                Offset(inset + len * 0.5f, h - inset - 4.dp.toPx()),
                sw,
            )
        }
        else -> {
            val r = 1.5f.dp.toPx()
            drawCircle(color.copy(alpha = a), r, Offset(w - inset, h - inset))
            drawCircle(color.copy(alpha = a * 0.7f), r, Offset(w - inset - 5.dp.toPx(), h - inset))
            drawCircle(color.copy(alpha = a * 0.5f), r, Offset(w - inset, h - inset - 5.dp.toPx()))
        }
    }
}

/** UR 箔面扫光。 */
private fun DrawScope.drawFoilSheen(flow: Float) {
    val hiPos = 0.05f + flow
    drawRect(
        brush = Brush.sweepGradient(
            0f to Color.Transparent,
            hiPos * 0.5f to Color.Transparent,
            hiPos to AppTheme.GoldHi.copy(alpha = 0.12f),
            (hiPos + 0.22f).coerceAtMost(0.99f) to Color.Transparent,
            1f to Color.Transparent,
        ),
    )
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
            endY = size.height * 0.35f,
        ),
    )
}
