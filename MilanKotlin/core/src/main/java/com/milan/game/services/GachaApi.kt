package com.milan.game.services

import com.milan.game.data.PullLogEntry

/**
 * 抽卡契约（2026-09-08 P1-5 接口化）。
 *
 * 方法面 = GameService 曾直接暴露给 UI 的**对外契约名**（GachaScreen / PullHistoryScreen 经
 * `GameState.service.xxx` 调用），由 [GachaService] 实现。GameService 仅对
 * [pull] 做显式 override 追加 onProgress 编排（每日任务 + 通行证经验上报），底层事务
 * 逻辑在本接口实现中，行为与拆分前等价。
 */
interface GachaApi {
    /** 重复角色按稀有度补偿的星魂碎片数量（纯公式查询）。 */
    fun fragmentsForRarity(rarity: Int): Int

    /** 抽卡历史快照（时间正序，最旧在前；UI 自行倒序展示）。 */
    fun pullHistory(): List<PullLogEntry>

    /** 指定池保底进度（硬/软保底区间 + UP 定轨状态）。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    fun getPityStatus(poolId: String): PityStatus

    /** 抽卡（单抽 / 十连），整体事务：先算产出、确认后扣款、落盘失败回滚。 */
    suspend fun pull(poolId: String, tenPull: Boolean): PullOutcome
}
