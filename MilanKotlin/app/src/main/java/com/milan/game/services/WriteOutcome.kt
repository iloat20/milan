package com.milan.game.services

/**
 * 写操作结果（2026-08 类型化：替代裸 Boolean，UI 可按失败原因精确提示）。
 *
 * 与事务范式配合：写操作要么成功落盘并广播（[Success]），要么因
 * 预算/前置校验失败被拒（[Rejected]，内存零变更），要么落盘失败已回滚
 * （[SaveFailed]，内存与磁盘一致、可安全重试，不会产生任何广播）。
 */
sealed interface WriteOutcome {
    /** 成功：已落盘并广播（或推进状态快照）。 */
    data object Success : WriteOutcome

    /** 被拒绝：余额不足 / 碎片不足 / 未拥有 / 已达上限 / 非法请求。内存零变更。 */
    data object Rejected : WriteOutcome

    /** 落盘失败：本次内存改动已回滚（回滚路径不广播），可重试。 */
    data object SaveFailed : WriteOutcome
}

/**
 * 抽卡结果（2026-08 类型化：替代「空列表 = 失败」的隐式约定）。
 *
 * 空列表语义此前在 UI 层被笼统提示为「卡池数据异常」，实际可能是余额不足
 * 或落盘失败回滚——类型化后各失败原因可精确提示（GachaScreen）。
 */
sealed interface PullOutcome {
    /** 成功：产出非空（plan 为空会走 [Rejected]，绝不返回空 Success）。 */
    data class Success(val results: List<PullResult>) : PullOutcome

    /** 被拒绝：未知卡池 / 空卡池 / 余额不足 / 无候选产出。扣款与发货均未发生。 */
    data object Rejected : PullOutcome

    /** 落盘失败：扣款与发货已回滚（含碎片幻影处理），可重试。 */
    data object SaveFailed : PullOutcome
}
