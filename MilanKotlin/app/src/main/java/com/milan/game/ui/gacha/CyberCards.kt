package com.milan.game.ui.gacha

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.Stroke
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

// P4-2（2026-08-27）：揭晓卡牌族从 CyberStage.kt 拆出（分镜 3/4：Single / Ten）。

/** 赛博卡背（十连牌桌待翻面）：深底 + 网格 + 品红能量环 + 中央徽记。 */
@Composable
internal fun CyberCardBack(modifier: Modifier = Modifier) {
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
