package com.milan.game.ui.gacha

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.RitualType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * UR 全屏揭晓幕（2026-09-12 游戏性）：金色径向爆发 + 超大 UR 字 + 角色名。
 * 叠在 [CyberRevealLayer] 之上，短时自动收起；可点跳过（由外层 skip 一并关闭）。
 */
@Composable
internal fun UrCurtainReveal(
    characterName: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (isActive && t < 1f) {
            withFrameNanos { nano ->
                // ~1.6s 从 0→1
                t = (t + 1f / (1.6f * 60f)).coerceAtMost(1f)
            }
        }
        delay(200)
        onDismiss()
    }
    val pulse by rememberInfiniteTransition(label = "urPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "urPulseAnim",
    )
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // 全屏金幕
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f * (1f - t * 0.35f))),
        )
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.42f
            val r = (0.15f + t * 0.85f) * size.minDimension * 1.2f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        AppTheme.GoldHi.copy(alpha = 0.55f * (1f - t * 0.4f) + pulse * 0.1f),
                        AppTheme.Gold.copy(alpha = 0.2f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "UR",
                style = RitualType.copy(
                    fontSize = (72 + pulse * 8).sp,
                    color = AppTheme.GoldHi,
                    letterSpacing = 12.sp,
                ),
                fontWeight = FontWeight.Bold,
            )
            if (characterName.isNotBlank()) {
                Text(
                    text = characterName,
                    color = AppTheme.Text1,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                text = "神谕降临",
                color = AppTheme.Gold.copy(alpha = 0.9f),
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
