package com.milan.game.domain.monetization

/**
 * 变现模型公式**单一事实来源**：通行证奖励换算、月卡定价。
 *
 * 2026-09-06 S5 创建：原 [com.milan.game.services.MonetizationService] 内联 `level * 2000` /
 * `level * 5` / `level * 3` 三处魔法数字违反 AGENTS.md 红线「禁止就地写数字」。
 * 收口至此，便于一次性调参与跨端共享。
 *
 * 铁律：任何「通行证某级奖励多少」的地方，都必须调用这里。
 */
object MonetizationFormulas {

    /**
     * 通行证免费轨星尘奖励 = level × 2000。
     *
     * 口径（2026-09-02 产品拍板）：1 级 2000 星尘起，50 级 100000 星尘封顶。
     */
    fun bpFreeRewardSoft(level: Int): Int = level.coerceAtLeast(1) * 2000

    /**
     * 通行证豪华轨钻石奖励 = level × 5（仅每 10 级发放一次）。
     *
     * 口径：10 级 50 钻石、20 级 100 钻石……50 级 250 钻石，整 10 倍级才出。
     */
    fun bpPremiumRewardHard(level: Int): Int = level.coerceAtLeast(1) * 5

    /**
     * 通行证豪华轨素材奖励 = level × 3（非整 10 倍级发放）。
     *
     * 口径：素材数量与等级线性相关，每级 3 个；与 [bpPremiumRewardHard] 互斥发放
     * （每 10 级发钻石，其余级别发素材）。
     */
    fun bpPremiumRewardMaterial(level: Int): Int = level.coerceAtLeast(1) * 3
}
