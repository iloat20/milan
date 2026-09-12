package com.milan.game.ui.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.data.StoryChapterDef
import com.milan.game.data.StoryStageDef
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.GameService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 章节行 UI 派生（随 snapshot revision 重算，替代 Screen 侧 remember(revision)）。 */
data class StoryChapterUi(
    val chapterId: String,
    val unlocked: Boolean,
    val progress: Float,
)

/**
 * 剧情页 ViewModel：章节列表 / 解锁与进度 / 关卡完结。
 *
 * Screen 不再直呼进程单例——章节卡与对话路由共用本 VM 的只读查询与写入口。
 * 2026-09-12：`chapterUiList` 随 snapshot 推进重算，消灭 Screen `remember(revision)`。
 */
class StoryViewModel(
    private val service: GameService,
) : ViewModel() {

    /** 章节定义（内容静态，进程内不变）。 */
    val chapters: List<StoryChapterDef> = service.getStoryChapters()

    /**
     * 存档 revision。关卡完结会推进 snapshot.revision。
     */
    val revision: StateFlow<Long> = service.snapshot
        .map { it.revision }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = service.snapshot.value.revision,
        )

    /** 章节解锁/进度列表（任意成功写后 snapshot 推进即刷新）。 */
    val chapterUiList: StateFlow<List<StoryChapterUi>> = service.snapshot
        .map {
            chapters.map { ch ->
                StoryChapterUi(
                    chapterId = ch.chapterId,
                    unlocked = service.isStoryChapterUnlocked(ch.chapterId),
                    progress = service.getStoryChapterProgress(ch.chapterId),
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = chapters.map { ch ->
                StoryChapterUi(
                    chapterId = ch.chapterId,
                    unlocked = service.isStoryChapterUnlocked(ch.chapterId),
                    progress = service.getStoryChapterProgress(ch.chapterId),
                )
            },
        )

    fun isChapterUnlocked(chapterId: String): Boolean = service.isStoryChapterUnlocked(chapterId)

    fun chapterProgress(chapterId: String): Float = service.getStoryChapterProgress(chapterId)

    fun isStageCompleted(stageId: String): Boolean = service.isStoryStageCompleted(stageId)

    fun canEnterStage(stageId: String): Boolean = service.canEnterStoryStage(stageId)

    fun findStage(stageId: String): StoryStageDef? = service.findStoryStageDef(stageId)

    /** 完结关卡（发奖 + 标记）；异常吞掉仅留业务失败语义（与原 NavHost 实现一致）。 */
    fun completeStage(stageId: String) {
        viewModelScope.launch {
            try {
                service.completeStoryStage(stageId)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    /** 对话好感选项落账（suspend 写，异常吞掉与原 Screen 一致）。 */
    fun grantAffinity(characterId: String, amount: Int) {
        viewModelScope.launch {
            try {
                service.grantAffinity(characterId, amount)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    /** 结局分支落档（ch08 三道光）；异常静默。 */
    fun setEndingBranch(branchId: String) {
        viewModelScope.launch {
            try {
                service.setStoryEndingBranch(branchId)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    /** 当前结局分支（null = 未选）。 */
    fun endingBranchId(): String? = service.getStoryEndingBranchId()

    /**
     * 说话者内容查询（2026-09-10）：DialogueScreen 私有 helper 此前直接摸
     * `AppGraph.service`（服务定位器泄漏）。改为由本 VM 提供查找函数注入。
     */
    fun characterOf(speakerId: String): CharacterDataEntry? = service.character(speakerId)
}
