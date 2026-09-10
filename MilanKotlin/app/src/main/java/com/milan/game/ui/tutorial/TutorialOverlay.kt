package com.milan.game.ui.tutorial

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import com.milan.game.ui.nav.NavItem
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** 引导步骤文案（短、贴世界观，≤20 字）。 [tabHint] 必须与 [NavItem.label] 一致。 */
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
        tabHint = NavItem.Gacha.label,
    )
    TutorialSteps.FORM_TEAM -> TutorialCopy(
        title = "编队出战",
        body = "把刚到手的英灵放上阵，准备第一战。",
        cta = "去卡组",
        tabHint = NavItem.Deck.label,
    )
    TutorialSteps.FIRST_BATTLE -> TutorialCopy(
        title = "第一战",
        body = "无尽之塔在等你。打一场，看看他们的实力。",
        cta = "去战斗",
        tabHint = NavItem.Home.label,
    )
    TutorialSteps.FIRST_LEVEL -> TutorialCopy(
        title = "养成觉醒",
        body = "变强一点，再去寻访。循环就此开始。",
        cta = "去升级",
        tabHint = NavItem.Home.label,
    )
    else -> TutorialCopy(
        title = "裂隙还在扩张",
        body = "去收集更多英灵吧。",
        cta = "出发",
        tabHint = NavItem.Home.label,
    )
}

/**
 * 新手引导覆盖层（第 8 节 P0）。
 *
 * **非阻塞**：半透明底仅作视觉提示，不吞触摸——玩家可直接点底层抽卡/编队/战斗。
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
    val composableScope = rememberCoroutineScope()
    var showIntro by remember { mutableStateOf(false) }

    LaunchedEffect(step) {
        showIntro = step == TutorialSteps.INTRO
    }

    if (finished || step == null) return

    val copy = tutorialCopyOf(step!!)
    Box(
        modifier = modifier
            .fillMaxSize()
            // 仅视觉压暗：不加 clickable / pointerInput，避免吞掉底层游戏操作
            .background(Color.Black.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 抬高到导航栏之上，避免盖住底部 5 Tab
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 92.dp)
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
                    val tab = copy.tabHint ?: NavItem.Home.label
                    GoldButton(
                        text = copy.cta,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTab(tab) },
                    )
                }
            }
        }
    }
}
