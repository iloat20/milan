package com.milan.game.ui.story

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.data.DialogueLine
import com.milan.game.data.StoryChoice
import com.milan.game.data.StoryStageDef
import com.milan.game.data.StoryStageType
import com.milan.game.ui.GameState
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 视觉小说式对话界面（v2 增强版）。
 *
 * 特性：
 * - 真实角色立绘（PortraitImage）
 * - 打字机效果（逐字显示，可点击跳过）
 * - 自动播放模式（可调速：慢/中/快）
 * - 跳过按钮（跳过当前关卡全部对话）
 * - 选择分支UI（选项卡片+点击反馈）
 * - 半透明毛玻璃对话框
 * - 角色切换动画（淡入淡出）
 * - 墨汁泼溅进入/退出转场
 */
@Composable
fun DialogueScreen(
    stage: StoryStageDef,
    onStageComplete: () -> Unit,
    onBack: () -> Unit,
    /**
     * 选择分支跳转到指定关卡（M3 修复新增）。此前 `nextStageId` 分支直接退出整关，
     * 「跳转到目标关卡」的语义从未实现；现由 NavHost 侧执行「当前关完结 + 打开目标关」。
     * 无内容引用该能力时保持默认空实现，不影响既有线性关卡。
     */
    onNavigateStage: (String) -> Unit = {},
) {
    val dialogue = stage.dialogue ?: return
    var currentIndex by remember { mutableStateOf(0) }
    var displayedText by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(true) }
    var autoPlay by remember { mutableStateOf(false) }
    var autoPlaySpeed by remember { mutableStateOf(3) } // 秒/句
    var showSpeedMenu by remember { mutableStateOf(false) }

    // 进入/退出转场状态
    var showContent by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showContent = true
    }

    val currentLine = dialogue.getOrNull(currentIndex)
    val service = GameState.service
    // 2026-09-02：好感选项落账用（suspend 写操作须在协程调用）。
    val scope = rememberCoroutineScope()

    // 打字机效果
    LaunchedEffect(currentIndex, dialogue) {
        val line = dialogue.getOrNull(currentIndex) ?: return@LaunchedEffect
        isTyping = true
        displayedText = ""
        for (i in line.text.indices) {
            displayedText = line.text.substring(0, i + 1)
            delay(30) // 打字速度
        }
        isTyping = false
    }

    // 自动播放
    LaunchedEffect(autoPlay, autoPlaySpeed, currentIndex, isTyping) {
        if (autoPlay && !isTyping) {
            delay(autoPlaySpeed * 1000L)
            if (currentIndex < dialogue.lastIndex) {
                currentIndex++
            } else {
                exiting = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BgDeepest)
    ) {
        // 背景渐变
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppTheme.BgDeepest,
                            AppTheme.BgMid.copy(alpha = 0.8f),
                            AppTheme.BgDeepest,
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            // 角色立绘区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (currentLine != null && currentLine.speakerId != "narrator") {
                    // 角色立绘（真实图片）
                    PortraitImage(
                        characterId = currentLine.speakerId,
                        rarity = getCharacterRarity(currentLine.speakerId),
                        modifier = Modifier
                            .fillMaxHeight(0.85f)
                            .aspectRatio(0.7f),
                        name = getSpeakerName(currentLine.speakerId),
                    )
                } else if (currentLine != null) {
                    // 旁白：显示叙述者图标
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(40.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        AppTheme.Gold.copy(alpha = 0.2f),
                                        AppTheme.Gold.copy(alpha = 0.05f),
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "📖", fontSize = 36.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 控制栏（自动播放 + 速度 + 跳过）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 自动播放开关
                Box(
                    modifier = Modifier
                        .background(
                            if (autoPlay) AppTheme.Gold.copy(alpha = 0.2f) else AppTheme.Surface,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { autoPlay = !autoPlay }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (autoPlay) "⏸ 自动" else "▶ 自动",
                        color = if (autoPlay) AppTheme.Gold else AppTheme.Text3,
                        fontSize = 11.sp,
                    )
                }

                // 速度选择
                if (autoPlay) {
                    Box(
                        modifier = Modifier
                            .background(AppTheme.Surface, RoundedCornerShape(8.dp))
                            .clickable { showSpeedMenu = !showSpeedMenu }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "${autoPlaySpeed}秒/句",
                            color = AppTheme.Text2,
                            fontSize = 11.sp,
                        )
                    }
                }

                // 跳过按钮
                Box(
                    modifier = Modifier
                        .background(AppTheme.Danger.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .clickable { exiting = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "⏭ 跳过",
                        color = AppTheme.Danger,
                        fontSize = 11.sp,
                    )
                }
            }

            // 速度选择菜单
            AnimatedVisibility(
                visible = showSpeedMenu,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    listOf(1, 2, 3, 5).forEach { speed ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .background(
                                    if (autoPlaySpeed == speed) AppTheme.Gold.copy(alpha = 0.3f) else AppTheme.Surface,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    autoPlaySpeed = speed
                                    showSpeedMenu = false
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "${speed}秒",
                                color = if (autoPlaySpeed == speed) AppTheme.Gold else AppTheme.Text3,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 对话框
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isTyping) {
                            // 点击跳过打字效果
                            val line = dialogue.getOrNull(currentIndex)
                            if (line != null) {
                                displayedText = line.text
                                isTyping = false
                            }
                        } else if (currentLine?.choices != null) {
                            // 选择分支：不做操作，等待用户点击选项
                        } else {
                            // 点击下一句
                            if (currentIndex < dialogue.lastIndex) {
                                currentIndex++
                            } else {
                                exiting = true
                            }
                        }
                    }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    // 说话者
                    if (currentLine != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 说话者头像（小圆）
                            if (currentLine.speakerId != "narrator") {
                                PortraitImage(
                                    characterId = currentLine.speakerId,
                                    rarity = getCharacterRarity(currentLine.speakerId),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                    target = com.milan.game.ui.components.PortraitTarget.Thumb,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = getSpeakerName(currentLine.speakerId),
                                color = getSpeakerColor(currentLine.speakerId),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    // 对话文本（打字机效果）
                    Text(
                        text = displayedText,
                        color = AppTheme.Text1,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 选择分支
                    if (!isTyping && currentLine?.choices != null) {
                        Spacer(Modifier.height(12.dp))
                        currentLine.choices.forEachIndexed { index, choice ->
                            ChoiceCard(
                                choice = choice,
                                onClick = {
                                    // 2026-09-02：好感选项落账——affinityBonus 此前只渲染
                                    // "+N" 标签从未写存档（零接线）。现给当前说话角色入账；
                                    // 满级/落盘失败等 Rejected 静默忽略（剧情产出不打断流程）。
                                    // 旁白（narrator）没有好感归属，跳过。
                                    val speaker = currentLine.speakerId
                                    if (choice.affinityBonus > 0 && speaker != null && speaker != "narrator") {
                                        scope.launch {
                                            try { service.grantAffinity(speaker, choice.affinityBonus) } catch (_: Exception) { }
                                        }
                                    }
                                    if (choice.nextStageId != null) {
                                        // M3 修复：此前「暂不支持，直接退出」→ 点分支选项直接结束整关，
                                        // 与数据模型注释（nextStageId = 跳转目标关卡）相悖。
                                        // 现上抛跳转意图，由导航层完成「当前关完结 + 打开目标关」。
                                        onNavigateStage(choice.nextStageId)
                                    } else {
                                        // 无跳转目标：按本关内分支处理，继续后续台词
                                        if (currentIndex < dialogue.lastIndex) {
                                            currentIndex++
                                        } else {
                                            exiting = true
                                        }
                                    }
                                },
                            )
                            if (index < currentLine.choices.lastIndex) {
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // 提示文字
                    if (currentLine?.choices == null) {
                        Text(
                            text = if (isTyping) "点击跳过..." else "点击继续...",
                            color = AppTheme.Text3,
                            fontSize = 11.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        // 返回按钮
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clickable { exiting = true }
                .background(AppTheme.Surface.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = "← 返回",
                color = AppTheme.Text2,
                fontSize = 12.sp,
            )
        }

        // 进度指示器
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(AppTheme.Surface.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = "${currentIndex + 1} / ${dialogue.size}",
                color = AppTheme.Text2,
                fontSize = 12.sp,
            )
        }

        // 墨汁进入转场（开场覆盖后消失）
        InkSplashTransition(
            isActive = !showContent,
            isEntering = true,
            onAnimEnd = { showContent = true },
        )

        // 墨汁退出转场（收缩覆盖后回调）
        InkSplashTransition(
            isActive = exiting,
            isEntering = false,
            onAnimEnd = { onStageComplete() },
        )
    }
}

/**
 * 选择分支卡片。
 */
@Composable
private fun ChoiceCard(
    choice: StoryChoice,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        AppTheme.Gold.copy(alpha = 0.1f),
                        AppTheme.Surface,
                    )
                ),
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "▸",
                color = AppTheme.Gold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = choice.text,
                color = AppTheme.Text1,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            if (choice.affinityBonus > 0) {
                Text(
                    text = "+${choice.affinityBonus}",
                    color = AppTheme.Frost,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/** 获取说话者显示名称。 */
private fun getSpeakerName(speakerId: String): String = when (speakerId) {
    "narrator" -> "旁白"
    "char_ur_zhulong" -> "烛龙"
    "char_ur_wuxu" -> "虚无"
    "char_ur_xingtian" -> "刑天"
    "char_ur_taotie" -> "饕餮"
    else -> speakerId.removePrefix("char_").replace("_", " ").uppercase()
}

/** 获取角色稀有度（用于立绘加载）。 */
private fun getCharacterRarity(speakerId: String): Int = when {
    speakerId.contains("ur_") -> 4
    speakerId.contains("ssr_") -> 3
    speakerId.contains("sr_") -> 2
    else -> 1
}

/** 获取说话者主题色。 */
private fun getSpeakerColor(speakerId: String): Color = when {
    speakerId == "narrator" -> Color(0xFFBBBBBB)
    speakerId.contains("zhulong") -> Color(0xFFFF6347) // 火焰红
    speakerId.contains("wuxu") -> Color(0xFF9370DB) // 紫金
    speakerId.contains("xingtian") -> Color(0xFF4169E1) // 钢铁蓝
    speakerId.contains("taotie") -> Color(0xFFCD853F) // 金铜
    else -> AppTheme.Gold
}
