package com.milan.game.services

import com.milan.game.data.BattleRecord
import com.milan.game.domain.progression.EconomyFormulas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 元进度聚合服务（2026-08-28 P1 重构：自 [GameService] 拆出）。
 *
 * 负责：设置项持久化、存档重置、战绩、每日商店、成就。
 * 边界：**不碰**抽卡/养成/爬塔/商店购买——那些分别属于其他四个聚合服务。
 *
 * 「元进度」= 不直接改变角色强度、但围绕核心玩法的系统（成就/每日/设置/纪录）。
 */
internal class MetaService(private val core: ServiceCore) {

    private val saveData get() = core.saveData

    // ─────────────────────────── 设置与数据管理 ───────────────────────────

    /**
     * 设置项持久化通用事务：先写入新值 → 落盘 → 失败回滚旧值。
     * （对齐存档事务范式：落盘失败回滚本次内存改动并返回 SaveFailed，回滚不广播事件。）
     * 整体持锁：old 快照读取移入临界区，防「读到过期值后回滚覆盖他人改动」。
     */
    private suspend fun <T> persistSetting(read: () -> T, write: (T) -> Unit, newValue: T): WriteOutcome =
        core.writeMutex.withLock {
            val old = read()
            core.transactionLocked(
                tag = "setting",
                mutate = { write(newValue) },
                rollback = { write(old) },
                // 设置项无事件广播，但需刷新状态快照：SettingsScreen 从 GameSnapshot 派生开关，
                // 成功落盘后由 refreshSnapshot 推进，UI 立即反映新值（I5）。
                onCommit = { core.refreshSnapshot() },
            )
        }

    /** 音效开关持久化（UI 层负责同步 MilanAudio 音量）。 */
    suspend fun setSoundEnabled(enabled: Boolean): WriteOutcome = persistSetting(
        read = { saveData.soundEnabled },
        write = { saveData.soundEnabled = it },
        newValue = enabled,
    )

    /** 振动开关持久化（GachaScreen 演出震动读取该字段）。 */
    suspend fun setVibrationEnabled(enabled: Boolean): WriteOutcome = persistSetting(
        read = { saveData.vibrationEnabled },
        write = { saveData.vibrationEnabled = it },
        newValue = enabled,
    )

    /** 推送开关持久化（推送系统尚未接入，先存档留位）。 */
    suspend fun setPushEnabled(enabled: Boolean): WriteOutcome = persistSetting(
        read = { saveData.pushEnabled },
        write = { saveData.pushEnabled = it },
        newValue = enabled,
    )

    /**
     * 重置存档为新档：删除存档文件并重载默认档，整体替换 saveData 引用，
     * 成功后广播货币/养成变更（各页面据此刷新）。
     * 删除失败返回 false 且不动内存（对齐事务范式：失败不产生任何变更）。
     */
    suspend fun resetSave(): Boolean = core.writeMutex.withLock {
        val fresh = withContext(Dispatchers.IO) { core.saveManager.reset() } ?: return@withLock false
        core.saveData = fresh
        core.publishCurrencyChanged()
        core.publishProgressionChanged()
        true
    }

    // ─────────────────────────── 战绩 ───────────────────────────

    /** 读取战绩（最近在前）。空列表返回空实例，调用方无需判 null。 */
    fun getBattleRecords(): List<BattleRecord> = saveData.battleRecords.filterNotNull()

    /**
     * 追加一条战绩并落盘。落盘失败回滚本次追加（不广播事件，战绩非经济）。
     * 列表上限 50 条，超出丢弃最旧记录。
     */
    suspend fun recordBattle(rec: BattleRecord?) {
        if (rec == null) return
        core.writeMutex.withLock {
            // 快照追加前列表：回滚时整体恢复。注意不能 dropLast——若「追加→超上限丢最旧→落盘失败」，
            // dropLast(1) 会把列表缩到 49 条，而存档仍是 50 条，内存与存档不一致（下次保存永久丢一条战绩）。
            // original 读取在临界区内：防「读到过期快照后回滚抹掉并发已落盘的记录」。
            val original = saveData.battleRecords
            core.transactionLocked(
                tag = "battle",
                mutate = { core.appendBattleRecordCapped(rec) },
                rollback = { saveData.battleRecords = original }, // 整体回滚，避免内存与存档不一致
                onCommit = { /* 战绩非经济，无事件广播 */ },
            )
        }
    }

    // ─────────────────────────── 每日商店（2026-08 二期） ───────────────────────────

    /**
     * 今日特惠槽位（确定性轮换：日期种子决定折扣包档位——同一天重开/跨端结果一致）。
     * 价格一律出自 [EconomyFormulas]（单一事实来源），禁止就地写数字。
     */
    fun dailyOffers(): List<DailyOffer> {
        val discountPack = if (core.today() % 2 == 0L) 2 else 1
        return listOf(
            DailyOffer(
                index = 0,
                kind = DailyOfferKind.FREE_SUPPLY,
                pack = 0,
                title = "每日补给",
                detail = "星尘 ${EconomyFormulas.dailyFreeSupplySoft()} ＋ 战票 ×${EconomyFormulas.dailyTicketGrant()}",
                costSoft = 0,
            ),
            DailyOffer(
                index = 1,
                kind = DailyOfferKind.DISCOUNT_PACK,
                pack = discountPack,
                title = "折扣碎片包 · ${if (discountPack == 2) "大" else "小"}",
                detail = "${EconomyFormulas.fragmentPackSize(discountPack)} 片星魂碎片 · 8 折",
                costSoft = EconomyFormulas.dailyDiscountPackCost(discountPack),
            ),
            DailyOffer(
                index = 2,
                kind = DailyOfferKind.TICKET_BUNDLE,
                pack = 0,
                title = "战票礼包",
                detail = "战票 ×${EconomyFormulas.dailyTicketBundleSize()}",
                costSoft = EconomyFormulas.dailyTicketBundleCost(),
            ),
        )
    }

    /** 今日已购槽位下标（存档日期与今天不一致 = 跨日未消费，返回空表）。 */
    fun dailyBoughtToday(): List<Int> =
        if (saveData.dailyShopDate == core.dayKey()) saveData.dailyShopBought.filterNotNull() else emptyList()

    /**
     * 购买每日特惠槽位 [index]：每槽每日限一次（跨日整体重置）；星尘不足 / 槽位非法 /
     * 已购过 → [WriteOutcome.Rejected]。效果与限购记录同一事务，落盘失败整体回滚。
     */
    suspend fun buyDailyOffer(index: Int): WriteOutcome = core.writeMutex.withLock {
        val offer = dailyOffers().firstOrNull { it.index == index }
            ?: return@withLock WriteOutcome.Rejected
        val key = core.dayKey()
        val rolledOver = saveData.dailyShopDate != key
        val bought = if (rolledOver) emptyList() else saveData.dailyShopBought.filterNotNull()
        if (index in bought) return@withLock WriteOutcome.Rejected

        // 效果参数（免费补给为正收入；付费档先扣星尘）
        var softDelta = -offer.costSoft
        var fragDelta = 0
        var ticketDelta = 0
        when (offer.kind) {
            DailyOfferKind.FREE_SUPPLY -> {
                softDelta = EconomyFormulas.dailyFreeSupplySoft()
                ticketDelta = EconomyFormulas.dailyTicketGrant()
            }

            DailyOfferKind.DISCOUNT_PACK -> fragDelta = EconomyFormulas.fragmentPackSize(offer.pack)

            DailyOfferKind.TICKET_BUNDLE -> ticketDelta = EconomyFormulas.dailyTicketBundleSize()
        }
        if (offer.costSoft > 0 && saveData.softCurrency < offer.costSoft) {
            return@withLock WriteOutcome.Rejected
        }
        // 免费补给的正收入同样防 Int 溢出（对齐 applyCurrencyDelta 的 P2-5：接近上限时 +2000 会翻负）
        if (softDelta > 0 && saveData.softCurrency.toLong() + softDelta > Int.MAX_VALUE) {
            return@withLock WriteOutcome.Rejected
        }

        val origSoft = saveData.softCurrency
        val ticketsExisted = saveData.items.any { it?.itemId == ServiceCore.BattleTicketItemId }
        val origTickets = core.itemCount(ServiceCore.BattleTicketItemId)
        val fragsExisted = saveData.items.any { it?.itemId == ServiceCore.StarFragmentItemId }
        val origFrags = core.itemCount(ServiceCore.StarFragmentItemId)
        val origDate = saveData.dailyShopDate
        val origBought = saveData.dailyShopBought

        core.transactionLocked(
            tag = "daily",
            mutate = {
                saveData.softCurrency += softDelta
                core.addItemDelta(ServiceCore.StarFragmentItemId, fragDelta)
                core.addItemDelta(ServiceCore.BattleTicketItemId, ticketDelta)
                saveData.dailyShopDate = key
                saveData.dailyShopBought = bought + index
            },
            rollback = {
                saveData.softCurrency = origSoft
                core.restoreItemCount(ServiceCore.StarFragmentItemId, fragsExisted, origFrags)
                core.restoreItemCount(ServiceCore.BattleTicketItemId, ticketsExisted, origTickets)
                saveData.dailyShopDate = origDate
                saveData.dailyShopBought = origBought
            },
            onCommit = { core.publishCurrencyChanged() },
        )
    }

    // ─────────────────────────── 成就（2026-08 二期） ───────────────────────────

    /** 全部成就的当前状态（解锁与否实时计算不落盘；claimed 以存档为准）。 */
    fun achievementStatuses(): List<AchievementStatus> {
        val claimed = saveData.claimedAchievementIds().toSet()
        val progress = achievementProgressSnapshot()
        return Achievements.ALL.map { def ->
            AchievementStatus(def, def.unlocked(progress), def.id in claimed)
        }
    }

    /** 成就进度快照（从存档推导，供定义侧纯函数判定；口径与 UI 展示一致）。 */
    private fun achievementProgressSnapshot(): Achievements.Progress = Achievements.Progress(
        ownedCount = saveData.ownedCharacters.count { it != null },
        // F3：改读永不清零的累计抽数。此前用 gachaCounters（保底计数，出货即归零），
        // 导致成就「寻访百次」的进度会随出货倒退（实测抽 120 次却显示 45，中途 74 → 0）。
        totalPulls = saveData.totalPullCount,
        towerBestFloor = saveData.towerBestFloor,
        formationSize = saveData.getFormationIds().size,
        softCurrency = saveData.softCurrency,
        fullLeveledChars = saveData.ownedCharacters.filterNotNull()
            .count { it.level >= core.maxLevelForStage(it.stage) },
    )

    /**
     * 领取成就奖励：未知 id / 已领取 / 未解锁 → Rejected；
     * 奖励发放与领取标记同一事务（失败整体回滚、成功经 publishCurrencyChanged 刷快照）。
     */
    suspend fun claimAchievement(id: String): WriteOutcome = core.writeMutex.withLock {
        val def = Achievements.byId[id] ?: return@withLock WriteOutcome.Rejected
        if (id in saveData.claimedAchievementIds()) return@withLock WriteOutcome.Rejected
        if (!def.unlocked(achievementProgressSnapshot())) return@withLock WriteOutcome.Rejected

        val origSoft = saveData.softCurrency
        val origHard = saveData.hardCurrency
        val ticketsExisted = saveData.items.any { it?.itemId == ServiceCore.BattleTicketItemId }
        val origTickets = core.itemCount(ServiceCore.BattleTicketItemId)
        val origClaimed = saveData.claimedAchievements

        core.transactionLocked(
            tag = "achievement",
            mutate = {
                if (def.rewardSoft > 0) saveData.softCurrency += def.rewardSoft
                if (def.rewardHard > 0) saveData.hardCurrency += def.rewardHard
                if (def.rewardTickets > 0) core.addItemDelta(ServiceCore.BattleTicketItemId, def.rewardTickets)
                saveData.claimedAchievements = saveData.claimedAchievements + id
            },
            rollback = {
                saveData.softCurrency = origSoft
                saveData.hardCurrency = origHard
                core.restoreItemCount(ServiceCore.BattleTicketItemId, ticketsExisted, origTickets)
                saveData.claimedAchievements = origClaimed
            },
            onCommit = { core.publishCurrencyChanged() },
        )
    }
}
