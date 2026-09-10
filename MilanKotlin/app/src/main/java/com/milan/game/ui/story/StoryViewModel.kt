package com.milan.game.ui.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.data.StoryChapterDef
import com.milan.game.data.StoryStageDef
import com.milan.game.services.GameService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 剧情页 ViewModel：章节列表 / 解锁与进度 / 关卡完结。
 *
 * Screen 不再直呼进程单例——章节卡与对话路由共用本 VM 的只读查询与写入口。
 */
class StoryViewModel(
    private val service: GameService,
) : ViewModel() {

    /** 章节定义（内容静态，进程内不变）。 */
    val chapters: List<StoryChapterDef> = service.getStoryChapters()

    /**
     * 存档 revision。关卡完结会推进 snapshot.revision，Screen 用它作 remember key，
     * 使章节进度/完成态在返回本页时重组刷新（此前 remember 不带 key，进度会卡在首次取值）。
     */
    val revision: StateFlow<Long> = service.snapshot
        .map { it.revision }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = service.snapshot.value.revision,
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
            } catch (_: Exception) {
            }
        }
    }

    /** 对话好感选项落账（suspend 写，异常吞掉与原 Screen 一致）。 */
    fun grantAffinity(characterId: String, amount: Int) {
        viewModelScope.launch {
            try {
                service.grantAffinity(characterId, amount)
            } catch (_: Exception) {
            }
        }
    }
}
