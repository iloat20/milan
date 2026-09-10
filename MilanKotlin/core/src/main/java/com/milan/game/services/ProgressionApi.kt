package com.milan.game.services

/**
 * 角色养成契约（2026-09-08 P1-5 接口化）。
 *
 * 方法面 = GameService 曾直接暴露给 UI 的**对外契约名**（ProgressionScreen /
 * ProgressionPanels / AffinityScreen / 剧情对话 经 `GameState.service.xxx` 调用），由
 * [ProgressionService] 实现。GameService 仅对 [levelUp] 做显式 override 追加 onProgress
 * 编排（每日任务 + 通行证经验上报），底层事务逻辑在本接口实现中，行为与拆分前等价。
 * 好感度（S4 下沉自 SocialService）同属本服务，一并纳入契约。
 */
interface ProgressionApi {
    /** 当前经验条进度（本等级内已积累 / 本级所需）。 */
    fun expProgress(charId: String): Pair<Int, Int>

    /** 升级 n 级（默认 1）。 */
    suspend fun levelUp(charId: String, n: Int = 1): WriteOutcome

    /** 增加经验（⚠️ 自身持写锁，临界区内调用方不可调用）。@return 实际升的级数。 */
    suspend fun grantExp(charId: String, amount: Int): Int

    /** 突破（Stage+1）。 */
    suspend fun ascend(charId: String): WriteOutcome

    /** 升星（Stars+1）。 */
    suspend fun starUp(charId: String): WriteOutcome

    /** 取角色天赋树（含节点与前置关系）。 */
    fun getTalentTree(charId: String): TalentTreeData?

    /** 预判某天赋节点当前是否可点亮（不落盘）。 */
    fun canAllocateTalent(charId: String, nodeId: String): Boolean

    /** 点亮天赋节点。 */
    suspend fun allocateTalent(charId: String, nodeId: String): WriteOutcome

    /** 全员好感度（characterId → 好感值）。 */
    fun getCharacterAffinityData(): Map<String, Int>

    /** 增加好感度（amount<=0 拒绝；满级钳位）。 */
    suspend fun grantAffinity(characterId: String, amount: Int): WriteOutcome

    /** 赠送礼物（扣除礼物道具 + 加好感，整体事务）。 */
    suspend fun giftAffinity(characterId: String): WriteOutcome
}
