package com.milan.game.ui.story

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.data.StoryChapterDef
import com.milan.game.data.StoryStageDef
import com.milan.game.data.StoryStageType
import com.milan.game.di.AppGraph
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.theme.AppTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * 剧情章节列表 + 关卡选择界面。
 * 只读查询与完结写入口统一经 [StoryViewModel]（组合根注入），Screen 不碰进程单例。
 */
@Composable
fun StoryScreen(
    onBack: () -> Unit,
    onOpenStage: (String) -> Unit,
) {
    val vm: StoryViewModel = viewModel(factory = AppGraph.factory)
    val revision by vm.revision.collectAsStateWithLifecycle()
    val chapters = remember { vm.chapters }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BgDeepest)
    ) {
        PageBackground {
            Column(modifier = Modifier.fillMaxSize()) {
                AppTopBar(title = "剧情", onBack = onBack)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    itemsIndexed(chapters) { index, chapter ->
                        StoryChapterCard(
                            vm = vm,
                            revision = revision,
                            chapter = chapter,
                            index = index,
                            // 解锁提示：主线=前一章；外传=好感门槛（Side 章不走前章链）。
                            unlockHint = unlockHintFor(chapters, index, chapter, vm),
                            onOpenStage = onOpenStage,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 章节卡片。
 *
 * 2026-09-02（按钮审查收口）：锁定章节点击不再静默——Snackbar 提示解锁条件
 * （[unlockHint] 由父层按前置章节标题生成）。
 */
@Composable
private fun StoryChapterCard(
    vm: StoryViewModel,
    revision: Long,
    chapter: StoryChapterDef,
    index: Int,
    unlockHint: String?,
    onOpenStage: (String) -> Unit,
) {
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    // key=revision：关卡完成推进 snapshot 后强制重取解锁/进度，避免 remember 永久缓存旧值
    val isUnlocked = remember(revision) { vm.isChapterUnlocked(chapter.chapterId) }
    val progress = remember(revision) { vm.chapterProgress(chapter.chapterId) }

    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isUnlocked) {
                    expanded = !expanded
                } else {
                    scope.launch {
                        feedback.show(unlockHint ?: "通关上一章全部关卡后解锁")
                    }
                }
            }
    ) {
        // 背景角色立绘（半透明）
        if (isUnlocked && chapter.coverCharacterId != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(AppTheme.Roundness.lg)),
            ) {
                PortraitImage(
                    characterId = chapter.coverCharacterId ?: "",
                    rarity = 4,
                    modifier = Modifier.fillMaxSize(),
                    name = null,
                )
                // 渐变遮罩
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    AppTheme.BgDeepest.copy(alpha = 0.95f),
                                )
                            )
                        )
                )
            }
        }

        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                // 章节头部
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chapterLabel(chapter, index),
                            color = AppTheme.Text3,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = chapter.title,
                            color = if (isUnlocked) AppTheme.Text1 else AppTheme.Text3,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = chapter.subtitle,
                            color = if (isUnlocked) AppTheme.Text2 else AppTheme.Text3,
                            fontSize = 12.sp,
                        )
                    }

                    if (isUnlocked) {
                        Column(
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                color = AppTheme.Gold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(AppTheme.Roundness.xxs)),
                                color = AppTheme.Gold,
                                trackColor = AppTheme.Surface,
                            )
                        }
                    } else {
                        Text(
                            text = "🔒",
                            fontSize = 20.sp,
                        )
                    }
                }

                // 展开关卡列表
                if (expanded && isUnlocked) {
                    Spacer(Modifier.height(12.dp))
                    chapter.stages.forEach { stage ->
                        StageRow(
                            vm = vm,
                            revision = revision,
                            stage = stage,
                            onOpenStage = onOpenStage,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 关卡行。
 *
 * 2026-09-02（按钮审查收口）：锁定关卡点击不再静默——Snackbar 点名前置关卡。
 */
@Composable
private fun StageRow(
    vm: StoryViewModel,
    revision: Long,
    stage: StoryStageDef,
    onOpenStage: (String) -> Unit,
) {
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val isCompleted = remember(revision) { vm.isStageCompleted(stage.stageId) }
    val canEnter = remember(revision) { vm.canEnterStage(stage.stageId) }

    val stageIcon = when (stage.type) {
        StoryStageType.DIALOGUE -> "💬"
        StoryStageType.BATTLE -> "⚔️"
        StoryStageType.CHOICE -> "🔀"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                if (canEnter) {
                    onOpenStage(stage.stageId)
                } else {
                    // 不可进入必因前置未通（canEnterStage 对无前置关卡恒 true），点名前置关卡名。
                    val hint = stage.prerequisiteStageId
                        ?.let { vm.findStage(it)?.title }
                        ?.let { "通关「$it」后解锁" }
                        ?: "通关前置关卡后解锁"
                    scope.launch { feedback.show(hint) }
                }
            }
            .background(
                if (canEnter) AppTheme.Surface.copy(alpha = 0.3f) else Color.Transparent,
                RoundedCornerShape(AppTheme.Roundness.sm),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isCompleted) "✅" else stageIcon,
            fontSize = 16.sp,
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stage.title,
                color = if (canEnter) AppTheme.Text1 else AppTheme.Text3,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            if (stage.recommendedLevel > 1) {
                Text(
                    text = "推荐等级 Lv.${stage.recommendedLevel}",
                    color = AppTheme.Text3,
                    fontSize = 10.sp,
                )
            }
        }

        val stageRewards = stage.rewards
        if (stageRewards != null && stageRewards.isNotEmpty()) {
            Text(
                text = stageRewards.joinToString(" ") { reward ->
                    when (reward.type) {
                        "soft_currency" -> "💎${reward.amount}"
                        "hard_currency" -> "💰${reward.amount}"
                        else -> ""
                    }
                },
                color = AppTheme.Text2,
                fontSize = 10.sp,
            )
        }
    }
}

/** 章节标签：主线第 X 章；好感外传统一「外传」。 */
private fun chapterLabel(chapter: StoryChapterDef, index: Int): String =
    if (chapter.requiredAffinityCharacterId != null) "外传"
    else "第${getChapterNumber(index)}章"

/**
 * 章节解锁提示。
 * 主线：点名前一章；外传：点名角色与好感等级（subtitle 已带等级，这里补角色名）。
 */
private fun unlockHintFor(
    chapters: List<StoryChapterDef>,
    index: Int,
    chapter: StoryChapterDef,
    vm: StoryViewModel,
): String? {
    val affinityChar = chapter.requiredAffinityCharacterId ?: return chapters.getOrNull(index - 1)
        ?.let { "通关「${it.title}」全部关卡后解锁" }
    val name = vm.characterOf(affinityChar)?.displayName ?: "该角色"
    return "将「$name」好感提升至 Lv.${chapter.requiredAffinityLevel} 后解锁"
}

/** 章节中文编号。 */
private fun getChapterNumber(index: Int): String = when (index) {
    0 -> "一"
    1 -> "二"
    2 -> "三"
    3 -> "四"
    4 -> "五"
    5 -> "六"
    6 -> "七"
    7 -> "八"
    8 -> "九"
    9 -> "十"
    else -> "${index + 1}"
}
