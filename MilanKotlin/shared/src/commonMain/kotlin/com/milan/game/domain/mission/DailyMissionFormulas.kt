package com.milan.game.domain.mission

/**
 * 每日任务公式**单一事实来源**：活跃度上限、任务进度钳位。
 *
 * 2026-09-06 S5 创建：原 [com.milan.game.services.DailyMissionService] 内联
 * `coerceAtMost(100)` 活跃度封顶违反 AGENTS.md 红线「禁止就地写数字」。
 * 收口至此，便于一次性调参与跨端共享。
 */
object DailyMissionFormulas {

    /**
     * 每日活跃度上限 = 100。
     *
     * 口径（2026-09-02 产品拍板）：完成任务给活跃度，累计 100 封顶
     * （超出不再累计，对应活跃度宝箱档位 20/40/60/80/100）。
     */
    const val MAX_ACTIVITY_POINTS = 100

    /**
     * 累加活跃度并钳位到 [MAX_ACTIVITY_POINTS]。
     *
     * 调用方无须重复写 `coerceAtMost`，统一收口。
     */
    fun activityAfterReward(current: Int, reward: Int): Int =
        (current + reward).coerceAtMost(MAX_ACTIVITY_POINTS)
}
