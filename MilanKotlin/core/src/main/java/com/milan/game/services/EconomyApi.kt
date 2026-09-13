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

    /** 扣除环痕（amount<=0 或余额不足拒绝）。 */
    suspend fun spendSoft(amount: Int): WriteOutcome

    /** 增加环痕（amount<=0 拒绝）。 */
    suspend fun grantSoft(amount: Int): WriteOutcome

    /** 扣除纯环（amount<=0 或余额不足拒绝）。 */
    suspend fun spendHard(amount: Int): WriteOutcome

    /** 增加纯环（amount<=0 拒绝）。 */
    suspend fun grantHard(amount: Int): WriteOutcome

    /** 购买残玦包（pack=1 小包 / 2 大包）。 */
    suspend fun buyFragmentPack(pack: Int): WriteOutcome

    /** 纯环兑换环痕。 */
    suspend fun buyDiamondExchange(): WriteOutcome

    /** 残玦兑换环痕（碎片回收阀门）。 */
    suspend fun exchangeFragmentsForSoft(): WriteOutcome
}
