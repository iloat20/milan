package com.milan.game.ui.components

import androidx.compose.material3.MaterialTheme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.OwnedCharacterView
import com.milan.game.infrastructure.HapticManager
import com.milan.game.ui.theme.AppTheme
import kotlin.math.roundToInt

/**
 * 编队拖拽状态（2026-09-10 UX）。
 *
 * 长按角色卡进入拖拽，松手落在编队条上即入队。
 * 与「点开预览再加入编队」并存：拖拽是快捷路径，预览是详情路径。
 * 坐标系统一为 **root**（卡片局部 + positionInRoot）。
 */
class FormationDragState {
    var dragging: OwnedCharacterView? by mutableStateOf(null)
        internal set

    /** 手指在 root 坐标系的位置。 */
    var dragPosition: Offset by mutableStateOf(Offset.Zero)
        internal set

    /** 编队条在 root 坐标系的包围盒。 */
    internal var dropBounds: Rect? = null

    val isOverDropZone: Boolean
        get() = dragging != null && dropBounds?.contains(dragPosition) == true

    internal fun start(ch: OwnedCharacterView, rootPos: Offset) {
        dragging = ch
        dragPosition = rootPos
    }

    internal fun move(rootPos: Offset) {
        dragPosition = rootPos
    }

    /** 松手：若悬停在编队条上返回该角色，否则 null。 */
    internal fun end(): OwnedCharacterView? {
        val ch = dragging
        val over = isOverDropZone
        dragging = null
        return if (over) ch else null
    }
}

@Composable
fun rememberFormationDragState(): FormationDragState = remember { FormationDragState() }

/**
 * 角色卡长按拖到编队手势源。
 * 不拦截普通点击——短按仍由上层 clickable / CodexCard 处理（预览）。
 *
 * @param localToRoot 调用方注入「局部坐标 → root 坐标」换算（用 onGloballyPositioned 的 positionInRoot）。
 */
fun Modifier.formationDragSource(
    state: FormationDragState,
    character: OwnedCharacterView,
    localToRoot: () -> Offset,
    onDropOnFormation: (OwnedCharacterView) -> Unit,
): Modifier = composed {
    val view = LocalView.current
    this.pointerInput(character.save.characterId) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                HapticManager.buttonClick(view)
                state.start(character, localToRoot() + offset)
            },
            onDrag = { change, dragAmount ->
                change.consume()
                state.move(state.dragPosition + dragAmount)
            },
            onDragEnd = {
                val dropped = state.end()
                if (dropped != null) {
                    HapticManager.buttonClick(view)
                    onDropOnFormation(dropped)
                }
            },
            onDragCancel = { state.dragging = null },
        )
    }
}

/** 编队条拖放目标：测量 root 坐标写入 [FormationDragState]。 */
fun Modifier.formationDropTarget(state: FormationDragState): Modifier =
    onGloballyPositioned { coords ->
        val pos = coords.positionInRoot()
        state.dropBounds = Rect(
            left = pos.x,
            top = pos.y,
            right = pos.x + coords.size.width,
            bottom = pos.y + coords.size.height,
        )
    }

/**
 * 拖拽跟随手指的迷你卡（半透明 + 悬停抬起）。
 * 叠加在 Screen 根 Box 上，不拦截触摸。
 */
@Composable
fun FormationDragGhost(
    state: FormationDragState,
    modifier: Modifier = Modifier,
) {
    val ch = state.dragging ?: return
    val lift by animateFloatAsState(
        targetValue = if (state.isOverDropZone) 1.08f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "dragLift",
    )
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .offset {
                    IntOffset(
                        state.dragPosition.x.roundToInt() - 48,
                        state.dragPosition.y.roundToInt() - 64,
                    )
                }
                .graphicsLayer {
                    scaleX = lift
                    scaleY = lift
                    alpha = 0.92f
                    shadowElevation = 16f
                }
                .size(96.dp, 128.dp)
                .shadow(12.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(AppTheme.BgDeepest)
                .padding(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PortraitImage(
                characterId = ch.save.characterId,
                rarity = ch.rarity,
                name = ch.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(7.dp)),
                target = PortraitTarget.Thumb,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
        if (state.isOverDropZone) {
            Text(
                text = "松手入队",
                color = AppTheme.Gold,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppTheme.BgDeepest.copy(alpha = 0.88f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
