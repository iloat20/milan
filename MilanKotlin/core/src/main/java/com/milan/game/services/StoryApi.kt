package com.milan.game.services

import com.milan.game.data.StoryChapterDef
import com.milan.game.data.StorySaveData
import com.milan.game.data.StoryStageDef

/**
 * 剧情契约（2026-09-08 P1-5 接口化）。
 *
 * 方法面 = GameService 曾直接暴露给 UI 的**对外契约名**（StoryScreen / DialogueScreen /
 * MilanNavHost 剧情路由经 `GameState.service.xxx` 调用），由 [StoryService] 实现。
 * GameService 仅对 [completeStoryStage] 做显式 override 追加 onProgress 编排（每日任务/
 * 通行证上报 + 扫荡经验编排），底层章节/关卡逻辑在本接口实现中，行为与拆分前等价。
 */
interface StoryApi {
    /** 剧情存档数据（章节解锁 / 关卡完成 / 奖励领取态）。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    fun getStoryData(): StorySaveData

    /** 全部章节定义。 */
    fun getStoryChapters(): List<StoryChapterDef>

    /** 按 ID 取章节定义。 */
    fun getStoryChapter(chapterId: String): StoryChapterDef?

    /** 章节是否已解锁（主线：前一章通关；外传：好感门槛）。 */
    fun isStoryChapterUnlocked(chapterId: String): Boolean

    /** 章节通关进度（0.0~1.0）。 */
    fun getStoryChapterProgress(chapterId: String): Float

    /** 第一纪元结局分支 ID（null = 未选）。 */
    fun getStoryEndingBranchId(): String?

    /** 写入结局分支（ch08 三道光；空串 Rejected；重复写覆盖）。 */
    suspend fun setStoryEndingBranch(branchId: String): WriteOutcome

    /** 关卡是否已通关（含困难模式）。 */
    fun isStoryStageCompleted(stageId: String): Boolean

    /** 关卡当前是否可进入（前置关卡已通 + 门票足够）。 */
    fun canEnterStoryStage(stageId: String): Boolean

    /** 按 ID 找关卡定义。 */
    fun findStoryStageDef(stageId: String): StoryStageDef?

    /** 通关剧情关卡（写盘 + 首通奖励）。 */
    suspend fun completeStoryStage(stageId: String): WriteOutcome

    /** 领取关卡通关奖励（重复领 → Rejected）。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    suspend fun claimStoryReward(stageId: String): WriteOutcome

    /** 扫荡已通关关卡（普通模式）。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    suspend fun sweepStoryStage(stageId: String, times: Int): StorySweepOutcome

    /** 扫荡已通关关卡（困难模式，2 倍奖励/消耗）。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    suspend fun sweepStoryStageHard(stageId: String, times: Int): StorySweepOutcome

    /** 困难模式是否已通关。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    fun isStoryHardModeCompleted(stageId: String): Boolean

    /** 领取章节全通奖励。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    suspend fun claimStoryChapterReward(chapterId: String): WriteOutcome

    /** 剧情总进度统计（通关节数 / 完成率，供主页展示）。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    fun getStoryProgress(): StoryProgressStats
}
