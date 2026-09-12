package com.milan.game.ui.inspection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milan.game.data.CharacterAction
import com.milan.game.di.AppGraph
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.components.PortraitTarget
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme
import androidx.compose.foundation.gestures.detectDragGestures
import kotlin.math.abs

/**
 * 360° 角色检视页（2026-09-11 死功能接线骨架）。
 *
 * 本批交付：立绘手势视差（水平拖动旋转感）+ 记录检视次数 + 互动动作列表。
 * 拍照模式 / 真 3D 视差立绘迁移为后续批次。
 */
@Composable
fun InspectionScreen(
    characterId: String,
    onBack: () -> Unit,
) {
    val feedback = LocalFeedback.current
    val vm: InspectionViewModel = viewModel(
        key = "inspection_$characterId",
        factory = AppGraph.inspectionFactory(characterId),
    )
    val ui by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.toasts.collect { feedback.show(it) } }

    PageBackground {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(title = "检视 · ${ui.displayName}", onBack = onBack)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                InspectHero(
                    characterId = ui.characterId,
                    rarity = ui.rarity,
                    owned = ui.owned,
                    onInspect = vm::recordInspection,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "累计检视 ${ui.inspectionCount} 次",
                    color = AppTheme.Text2,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
                if (ui.hiddenUnlocked) {
                    Text(
                        "隐藏互动已解锁",
                        color = AppTheme.Gold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "互动动作",
                    color = AppTheme.Text3,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                if (ui.actions.isEmpty()) {
                    Text("暂无可用动作", color = AppTheme.Text3)
                } else {
                    for (action in ui.actions) {
                        ActionRow(
                            name = action.name,
                            desc = action.description,
                            voice = action.voiceLine,
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun InspectHero(
    characterId: String,
    rarity: Int,
    owned: Boolean,
    onInspect: () -> Unit,
) {
    // 手势视差：水平拖动轻微倾斜立绘（替代未迁移的 Parallax3D；骨架级 3D 感）
    var rotY by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(420.dp)
            .clip(RoundedCornerShape(AppTheme.Roundness.lg))
            .background(AppTheme.BgMid)
            .border(1.dp, AppTheme.Gold.copy(alpha = 0.35f), RoundedCornerShape(AppTheme.Roundness.lg))
            .pointerInput(characterId) {
                detectDragGestures { change, drag ->
                    change.consume()
                    rotY = (rotY + drag.x * 0.15f).coerceIn(-28f, 28f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        PortraitImage(
            characterId = characterId,
            rarity = rarity,
            name = characterId,
            target = PortraitTarget.Full,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotY
                    cameraDistance = 12f * density
                },
        )
        if (owned) {
            Text(
                "记 录 检 视",
                color = AppTheme.Gold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(AppTheme.Roundness.xl))
                    .background(AppTheme.Surface)
                    .border(1.dp, AppTheme.Gold.copy(alpha = 0.6f), RoundedCornerShape(AppTheme.Roundness.xl))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .clickable(onClick = onInspect),
            )
        } else {
            Text("未拥有该角色", color = AppTheme.Text3)
        }
        Text(
            "拖动旋转",
            color = AppTheme.Text3.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
        )
    }
}

@Composable
private fun ActionRow(name: String, desc: String, voice: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(AppTheme.Roundness.md))
            .background(AppTheme.Surface.copy(alpha = 0.55f))
            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(AppTheme.Roundness.md))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = AppTheme.Text1)
            Text(desc, color = AppTheme.Text3)
        }
        if (!voice.isNullOrEmpty()) {
            Text(voice, color = AppTheme.Text2, fontSize = 12.sp)
        }
    }
}
