package com.milan.game.ui.tutorial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milan.game.data.TutorialSteps
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.components.NeonButton
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** 引导步骤文案（短、贴世界观，≤20 字）。 */
data class TutorialCopy(
    val title: String,
    val body: String,
    val cta: String,
    val tabHint: String?,
)

fun tutorialCopyOf(step: String): TutorialCopy = when (step) {
    TutorialSteps.INTRO -> TutorialCopy(
        title = "裂隙纪元",
        body = "三界碎片重连。去召唤英灵，把原初之环拼回来。",
        cta = "开始",
        tabHint = null,
    )
    TutorialSteps.FIRST_PULL -> TutorialCopy(
        title = "寻访英灵",
        body = "裂隙送来的第一份力量。去抽卡，聚齐同伴。",
        cta = "去寻访",
        tabHint = "抽卡",
    )
    TutorialSteps.FORM_TEAM -> TutorialCopy(
        title = "编队出战",
        body = "把刚到手的英灵放上阵，准备第一战。",
        cta = "去卡组",
        tabHint = "卡组",
    )
    TutorialSteps.FIRST_BATTLE -> TutorialCopy(
        title = "第一战",
        body = "无尽之塔在等你。打一场，看看他们的实力。",
        cta = "去战斗",
        tabHint = "主页",
    )
    TutorialSteps.FIRST_LEVEL -> TutorialCopy(
        title = "养成觉醒",
        body = "变强一点，再去寻访。循环就此开始。",
        cta = "去升级",
        tabHint = null,
    )
    else -> TutorialCopy(
        title = "裂隙还在扩张",
        body = "去收集更多英灵吧。",
        cta = "出发",
        tabHint = null,
    )
}

/**
 * 新手引导覆盖层（第 8 节 P0）。
 *
 * 非全屏锁死：半透明底 + 底部提示卡，可点「跳过」。
 * 完成步由业务成功路径（GameService）自动写入；本层只展示与跳过/开场确认。
 */
@Composable
fun TutorialOverlay(
    modifier: Modifier = Modifier,
    vm: TutorialViewModel = viewModel(factory = com.milan.game.di.AppGraph.factory),
    onNavigateTab: (String) -> Unit = {},
) {
    val step by vm.currentStep.collectAsStateWithLifecycle()
    val finished by vm.finished.collectAsStateWithLifecycle()
    val composableScope = androidx.compose.runtime.rememberCoroutineScope()
    var showIntro by remember { mutableStateOf(false) }

    LaunchedEffect(step) {
        showIntro = step == TutorialSteps.INTRO
    }

    if (finished || step == null) return

    val copy = tutorialCopyOf(step!!)
    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
        ) {
            // 轻遮罩：点击不关闭，避免误触底层；跳过是唯一出口（除业务完成）
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppTheme.Roundness.md))
                    .background(AppTheme.Surface.copy(alpha = 0.96f))
                    .border(1.dp, AppTheme.Gold.copy(alpha = 0.45f), RoundedCornerShape(AppTheme.Roundness.md))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = copy.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.Gold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "跳过",
                        fontSize = 12.sp,
                        color = AppTheme.Text3,
                        modifier = Modifier.clickable {
                            composableScope.launch { vm.skip() }
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = copy.body,
                    fontSize = 13.sp,
                    color = AppTheme.Text1,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (showIntro) {
                        GoldButton(
                            text = copy.cta,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                composableScope.launch { vm.completeStep(TutorialSteps.INTRO) }
                                showIntro = false
                            },
                        )
                    } else {
                        val tab = copy.tabHint
                        GoldButton(
                            text = copy.cta,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (tab != null) onNavigateTab(tab)
                                else onNavigateTab("home")
                            },
                        )
                    }
                }
            }
        }
    }
}
