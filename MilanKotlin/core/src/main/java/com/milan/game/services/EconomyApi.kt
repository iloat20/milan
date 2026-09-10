package com.milan.game.services

/**
 * 经济与商店契约（2026-09-08 P1-5 接口化）。
 *
 * 方法面 = GameService 曾直接暴露给 UI 的**对外契约名**（ShopScreen / ProgressionScreen
 * 经 `GameState.service.xxx` 调用），由 [EconomyService] 实现。旧的 `addSoft` / `addHard`
 * 兼容别名（S6 @Deprecated）不进入本公共契约。
 */
interface EconomyApi {
    /** 当前战票数。 */
    fun battleTickets(): Int

    /** 扣除星尘（amount<=0 或余额不足拒绝）。 */
    suspend fun spendSoft(amount: Int): WriteOutcome

    /** 增加星尘（amount<=0 拒绝）。 */
    suspend fun grantSoft(amount: Int): WriteOutcome

    /** 扣除钻石（amount<=0 或余额不足拒绝）。 */
    suspend fun spendHard(amount: Int): WriteOutcome

    /** 增加钻石（amount<=0 拒绝）。 */
    suspend fun grantHard(amount: Int): WriteOutcome

    /** 购买星魂碎片包（pack=1 小包 / 2 大包）。 */
    suspend fun buyFragmentPack(pack: Int): WriteOutcome

    /** 钻石兑换星尘。 */
    suspend fun buyDiamondExchange(): WriteOutcome

    /** 星魂碎片兑换星尘（碎片回收阀门）。 */
    suspend fun exchangeFragmentsForSoft(): WriteOutcome
}
