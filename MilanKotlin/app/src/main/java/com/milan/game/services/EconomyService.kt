package com.milan.game.services

import com.milan.game.domain.progression.EconomyFormulas
import kotlinx.coroutines.sync.withLock

/**
 * 经济聚合服务（2026-08-28 P1 重构：自 [GameService] 拆出）。
 *
 * 负责：星尘/钻石增减、商店购买（碎片包、钻石兑换、碎片回收）、战票查询。
 * 边界：**不碰**抽卡/养成/爬塔/成就——那些分别属于其他四个聚合服务。
 *
 * 全部遵循事务范式：先校验可负担 → 改内存 → 落盘 → 失败回滚 → 仅成功才广播。
 * 返回 [WriteOutcome]：Rejected = 预算不足/非法请求（零变更）；SaveFailed = 落盘失败（已回滚）。
 * 定价一律走 [EconomyFormulas]（单一事实来源），禁止就地写数字。
 */
internal class EconomyService(private val core: ServiceCore) {

    private val saveData get() = core.saveData

    /** 当前战票数（UI 展示/门槛预判；权威判定在 TowerService 临界区内复核）。 */
    fun battleTickets(): Int = core.itemCount(ServiceCore.BattleTicketItemId)

    /**
     * 扣除星尘。amount<=0（含误传负数）或余额不足或落盘失败时不做任何变更并返回非 Success。
     * 负数金额防御（P2-6）：`spendSoft(-50)` 若直传会变相加钱，一律拒绝。
     */
    suspend fun spendSoft(amount: Int): WriteOutcome =
        if (amount <= 0) WriteOutcome.Rejected else applyCurrencyDelta(-amount, 0)

    /** 增加星尘。amount<=0 或落盘失败时不做任何变更并返回非 Success。 */
    suspend fun grantSoft(amount: Int): WriteOutcome =
        if (amount <= 0) WriteOutcome.Rejected else applyCurrencyDelta(amount, 0)

    /** 扣除钻石。amount<=0 或余额不足或落盘失败时不做任何变更并返回非 Success。 */
    suspend fun spendHard(amount: Int): WriteOutcome =
        if (amount <= 0) WriteOutcome.Rejected else applyCurrencyDelta(0, -amount)

    /** 增加钻石。amount<=0 或落盘失败时不做任何变更并返回非 Success。 */
    suspend fun grantHard(amount: Int): WriteOutcome =
        if (amount <= 0) WriteOutcome.Rejected else applyCurrencyDelta(0, amount)

    private suspend fun applyCurrencyDelta(softDelta: Int, hardDelta: Int): WriteOutcome =
        core.withWriteLock {
            if (softDelta == 0 && hardDelta == 0) return@withWriteLock WriteOutcome.Rejected
            // 用 Long 预算校验：既拦截负余额，也拦截 Int 溢出（P2-5）——
            // 溢出为负会被当「不足」静默拒绝，溢出为正则会通过校验后破坏性改写余额并落盘。
            if (softDelta != 0) {
                val after = saveData.softCurrency.toLong() + softDelta
                if (after < 0 || after > Int.MAX_VALUE) return@withWriteLock WriteOutcome.Rejected
            }
            if (hardDelta != 0) {
                val after = saveData.hardCurrency.toLong() + hardDelta
                if (after < 0 || after > Int.MAX_VALUE) return@withWriteLock WriteOutcome.Rejected
            }

            val origSoft = saveData.softCurrency
            val origHard = saveData.hardCurrency
            core.transactionLocked(
                tag = "currency",
                mutate = {
                    saveData.softCurrency += softDelta
                    saveData.hardCurrency += hardDelta
                },
                rollback = {
                    saveData.softCurrency = origSoft
                    saveData.hardCurrency = origHard
                },
                onCommit = { core.publishCurrencyChanged() },
            )
        }

    // ─────────────────────────── 商店 ───────────────────────────

    /**
     * 购买星魂碎片包（pack=1 小包 / 2 大包）。非法档位、星尘不足 → Rejected；落盘失败 → SaveFailed。
     * 并发契约（2026-08 审查修复）：预算校验必须在 writeMutex 临界区内完成——
     * 校验在锁外、扣减在锁内的写法存在竞态窗口（落盘挂起点让出线程期间，
     * 另一入口可插入并通过过期校验 → 负余额）。
     */
    suspend fun buyFragmentPack(pack: Int): WriteOutcome = core.withWriteLock {
        val frags = EconomyFormulas.fragmentPackSize(pack)
        val cost = EconomyFormulas.fragmentPackCost(pack)
        if (frags <= 0 || cost <= 0) return@withWriteLock WriteOutcome.Rejected
        if (saveData.softCurrency < cost) return@withWriteLock WriteOutcome.Rejected

        val origSoft = saveData.softCurrency
        val itemExisted = saveData.items.any { it?.itemId == ServiceCore.StarFragmentItemId }
        val origFrags = core.itemCount(ServiceCore.StarFragmentItemId)
        core.transactionLocked(
            tag = "shop",
            mutate = {
                saveData.softCurrency -= cost
                core.addItemDelta(ServiceCore.StarFragmentItemId, frags)
            },
            rollback = {
                // 回滚（不广播）：restoreItemCount 统一处理「新增条目整条移除 / 既有条目恢复数量」
                saveData.softCurrency = origSoft
                core.restoreItemCount(ServiceCore.StarFragmentItemId, itemExisted, origFrags)
            },
            onCommit = { core.publishCurrencyChanged() },
        )
    }

    /**
     * 钻石兑换星尘。钻石不足 → Rejected；落盘失败 → SaveFailed。
     * 整体持锁（同 buyFragmentPack 的并发契约）；星尘收入带 Int 溢出拦截
     * （对齐 applyCurrencyDelta 的 P2-5：接近上限时兑换会翻负，拒绝优于破坏性改写）。
     */
    suspend fun buyDiamondExchange(): WriteOutcome = core.withWriteLock {
        val cost = EconomyFormulas.diamondExchangeCost()
        val yield = EconomyFormulas.diamondExchangeYield()
        if (saveData.hardCurrency < cost) return@withWriteLock WriteOutcome.Rejected
        if (saveData.softCurrency.toLong() + yield > Int.MAX_VALUE) return@withWriteLock WriteOutcome.Rejected

        val origHard = saveData.hardCurrency
        val origSoft = saveData.softCurrency
        core.transactionLocked(
            tag = "shop",
            mutate = {
                saveData.hardCurrency -= cost
                saveData.softCurrency += yield
            },
            rollback = {
                // 回滚（不广播）：统一「保存原值恢复」范式（此前减法恢复虽数学等价，但与全局不一致）
                saveData.hardCurrency = origHard
                saveData.softCurrency = origSoft
            },
            onCommit = { core.publishCurrencyChanged() },
        )
    }

    /**
     * 星魂碎片兑换星尘（2026-08 三期）：碎片过剩玩家的回收阀门。
     * 汇率单一事实来源在 [EconomyFormulas.fragmentExchangeBatch]/[fragmentExchangeYield]
     * （回收单价 80 ✦/片 < 商店购入价 100 ✦/片，双向流通必有损耗防套利）。
     */
    suspend fun exchangeFragmentsForSoft(): WriteOutcome = core.withWriteLock {
        val batch = EconomyFormulas.fragmentExchangeBatch()
        val yield = EconomyFormulas.fragmentExchangeYield()
        if (batch <= 0 || yield <= 0) return@withWriteLock WriteOutcome.Rejected
        if (core.itemCount(ServiceCore.StarFragmentItemId) < batch) return@withWriteLock WriteOutcome.Rejected
        if (saveData.softCurrency.toLong() + yield > Int.MAX_VALUE) return@withWriteLock WriteOutcome.Rejected

        val origSoft = saveData.softCurrency
        // 预检已保证碎片条目存在且数量 ≥ batch：直接原地减，不产生新条目、不会出现幽灵零道具
        val itemExisted = true
        val origFrags = core.itemCount(ServiceCore.StarFragmentItemId)
        core.transactionLocked(
            tag = "shop",
            mutate = {
                saveData.items.firstOrNull { it?.itemId == ServiceCore.StarFragmentItemId }
                    ?.let { it.count -= batch }
                saveData.softCurrency += yield
            },
            rollback = {
                saveData.softCurrency = origSoft
                core.restoreItemCount(ServiceCore.StarFragmentItemId, itemExisted, origFrags)
            },
            onCommit = { core.publishCurrencyChanged() },
        )
    }
}
