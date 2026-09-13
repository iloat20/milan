package com.milan.game.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.milan.game.ui.theme.AppTheme

/**
 * 立绘持光：轻 3D 倾角 + 径向高光跟手（与 CodexCard 对光同语言，力度更轻）。
 * 用于主页 Hero / 角色详情主视觉。
 */
@Composable
fun PortraitTiltBox(
    modifier: Modifier = Modifier,
    accent: Color = AppTheme.Gold,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    var nx by remember { mutableFloatStateOf(0.5f) }
    var ny by remember { mutableFloatStateOf(0.42f) }
    var lightOn by remember { mutableFloatStateOf(0f) }
    val light by animateFloatAsState(
        targetValue = lightOn,
        animationSpec = spring(dampingRatio = 0.85f),
        label = "portraitLight",
    )
    val rotX by animateFloatAsState(
        targetValue = (0.5f - ny) * 8f,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "portraitRotX",
    )
    val rotY by animateFloatAsState(
        targetValue = (nx - 0.5f) * 8f,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "portraitRotY",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationX = rotX
                rotationY = rotY
                cameraDistance = 16f * density
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        nx = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        ny = (offset.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        lightOn = 1f
                        tryAwaitRelease()
                        lightOn = 0f
                    },
                )
            }
            .drawWithContent {
                drawContent()
                if (light > 0.02f) {
                    val cx = size.width * nx
                    val cy = size.height * ny
                    drawRect(
                        brush = Brush.radialGradient(
                            0f to Color.White.copy(alpha = 0.14f * light),
                            0.35f to accent.copy(alpha = 0.10f * light),
                            1f to Color.Transparent,
                            center = Offset(cx, cy),
                            radius = size.minDimension * 0.85f,
                        ),
                    )
                }
            },
        contentAlignment = contentAlignment,
    ) {
        content()
    }
}
