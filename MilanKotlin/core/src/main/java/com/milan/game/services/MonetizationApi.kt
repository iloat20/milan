package com.milan.game.services

import com.milan.game.data.BattlePassReward
import com.milan.game.data.ChargeMilestone
import com.milan.game.data.ChargeTier
import com.milan.game.data.MonetizationSaveData

/**
 * 商业化契约（2026-09-08 P1-5 接口化）。
 *
 * 方法面 = GameService 曾直接暴露给 UI 的**对外契约名**（BattlePassScreen / 充值入口 /
 * 月卡页经 `GameState.service.xxx` 调用），由 [MonetizationService] 实现。
 * 月卡 / 通行证 / 充值档位三组；`charge` 目前为模拟支付（无 Billing 接入，待产品定位）。
 *
 * 注意 [grantBattlePassExp] 契约为单参（broadcast=true 语义，主动发放要广播快照）；
 * 编排侧（onProgress 静默累加）调用实现类内部的双参变体，不进入本契约。
 */
interface MonetizationApi {
    /** 商业化存档数据（月卡/通行证/累计充值）。 */
    fun getMonetizationData(): MonetizationSaveData

    /** 购买月卡（扣钻石；生效期内续费叠加天数）。 */
    @Deprecated("P2-11: 月卡UI零调用", level = DeprecationLevel.WARNING)
    suspend fun activateMonthlyCard(cost: Int): WriteOutcome

    /** 领取月卡每日奖励（今日已领 → Rejected）。 */
    @Deprecated("P2-11: 月卡UI零调用", level = DeprecationLevel.WARNING)
    suspend fun claimMonthlyCardReward(): WriteOutcome

    /** 月卡剩余天数。 */
    @Deprecated("P2-11: 月卡UI零调用", level = DeprecationLevel.WARNING)
    fun getMonthlyCardDaysLeft(): Int

    /** 购买豪华通行证。 */
    suspend fun purchaseBattlePass(cost: Int): WriteOutcome

    /** 增加通行证经验（主动发放，成功广播快照）。 */
    suspend fun grantBattlePassExp(amount: Int): WriteOutcome

    /** 领取通行证等级奖励。 */
    suspend fun claimBattlePassReward(level: Int): WriteOutcome

    /** 通行证定义奖励列表。 */
    fun getBattlePassRewards(): List<BattlePassReward>

    /** 充值（模拟支付：扣模拟档位 + 加钻石 + 累计充值进度）。 */
    suspend fun charge(tierId: String, hardCurrency: Int, costCents: Int): WriteOutcome

    /** 领取累计充值里程碑奖励。 */
    @Deprecated("P2-11: 充值里程碑UI零调用", level = DeprecationLevel.WARNING)
    suspend fun claimChargeMilestone(amountCents: Int): WriteOutcome

    /** 充值档位列表。 */
    @Deprecated("P2-11: 充值档位UI零调用", level = DeprecationLevel.WARNING)
    fun getChargeTiers(): List<ChargeTier>

    /** 累计充值里程碑定义。 */
    @Deprecated("P2-11: 充值里程碑UI零调用", level = DeprecationLevel.WARNING)
    fun getChargeMilestones(): List<ChargeMilestone>
}
