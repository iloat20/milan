package com.milan.game.services

import com.milan.game.data.CharacterSaveState
import com.milan.game.data.ItemSaveState
import com.milan.game.data.SaveProvider
import com.milan.game.domain.progression.EconomyFormulas
import com.milan.game.infrastructure.eventbus.EventBus
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 二期系统测试（2026-08）：爬塔战票门槛/返还、每日商店限购与跨日重置、成就领取一次性。
 * today 提供器注入固定「今天」，全部断言不依赖真实时钟。
 */
/**
 * 测试基准余额（与 GameServiceTest 同思路）：多步经济用例需要充裕起点。
 * 刻意不复用 [com.milan.game.data.SaveData.DEFAULT_SOFT_CURRENCY]——那是产品数值，
 * 产品数值调整不应牵动测试断言。
 */
private const val RICH_SOFT = 1_000_000

class MetaProgressionTest {

    private val testContent = """
        {
          "Characters": [
            {
              "CharacterId": "char_a",
              "DisplayName": "测试甲",
              "BaseRarity": 3,
              "Element": "Flame",
              "BaseStats": [500, 100, 20000, 30]
            },
            {
              "CharacterId": "char_b",
              "DisplayName": "测试乙",
              "BaseRarity": 2,
              "Element": "Water",
              "BaseStats": [400, 90, 18000, 25]
            }
          ],
          "Pools": [
            {
              "PoolId": "pool_test",
              "RarityWeights": [0, 0, 1000, 0],
              "HardPity": 90,
              "SingleCost": 160,
              "TenCost": 1600,
              "Entries": [
                { "CharacterId": "char_a", "RarityIndex": 3, "Weight": 1000 },
                { "CharacterId": "char_b", "RarityIndex": 3, "Weight": 1000 }
              ]
            }
          ],
          "TalentTrees": [
            { "TreeId": "tree_dummy", "Nodes": [ { "NodeId": "n1", "Cost": 1 } ] }
          ]
        }
    """.trimIndent()

    private class FakeProvider(var failSave: Boolean = false) : SaveProvider {
        var stored: String? = null
        val traces = mutableListOf<String>()
        override fun save(json: String): Boolean {
            if (failSave) return false
            stored = json
            return true
        }

        override fun load(): String = stored ?: ""
        override fun delete() {
            stored = null
        }

        override fun exists(): Boolean = stored != null
        override fun loadBackup(): String? = null
    }

    @Before
    fun setUp() {
        EventBus.clear()
    }

    private fun makeService(
        failSave: Boolean = false,
        day: Long = 20_000L,
        soft: Int = RICH_SOFT,
    ): GameService {
        val provider = FakeProvider(failSave)
        return GameService(
            saveProvider = provider,
            contentJson = testContent,
            onTrace = provider.traces::add,
            rng = Random(42),
            today = { day },
        ).also { it.saveData.softCurrency = soft }
    }

    private fun GameService.own(vararg ids: Pair<String, Int>) {
        saveData.ownedCharacters = ids.map { (id, lv) ->
            CharacterSaveState(characterId = id, level = lv)
        }
    }

    /** 直写道具（测试预置；生产路径一律走 addItemDelta）。 */
    private fun GameService.grantTickets(n: Int) {
        saveData.items = if (n > 0) listOf(ItemSaveState(itemId = GameService.BattleTicketItemId, count = n)) else emptyList()
    }

    private fun GameService.ticketCount(): Int =
        saveData.items.firstOrNull { it?.itemId == GameService.BattleTicketItemId }?.count ?: 0

    // ── 爬塔战票 ──

    @Test
    fun tower_withoutTickets_rejected() = runTest {
        val svc = makeService()
        svc.own("char_a" to 80)
        svc.saveData.formation = listOf("char_a")
        assertTrue(svc.runTowerFloor(1) is TowerOutcome.Rejected)
        assertEquals(0, svc.saveData.towerBestFloor)
    }

    @Test
    fun tower_victory_refundsTicket_netZero() = runTest {
        val svc = makeService()
        svc.own("char_a" to 80)
        svc.saveData.formation = listOf("char_a")
        svc.grantTickets(1)

        val outcome = svc.runTowerFloor(1)
        assertTrue(outcome is TowerOutcome.Completed)
        assertTrue((outcome as TowerOutcome.Completed).victory)
        // F1：入场 -1 + 胜利返 +1 = 净 0（仅刷新纪录才返票；复刷已通层净耗 1 张）；
        // 星尘同样只在刷新纪录时发放，本用例为首通故照发。
        assertEquals(1, svc.ticketCount())
        assertEquals(EconomyFormulas.towerRewardSoft(1), svc.saveData.softCurrency - RICH_SOFT)
    }

    @Test
    fun tower_defeat_consumesTicket() = runTest {
        val svc = makeService()
        svc.own("char_b" to 1)
        svc.saveData.formation = listOf("char_b")
        svc.grantTickets(2)

        val outcome = svc.runTowerFloor(50)
        assertTrue(outcome is TowerOutcome.Completed && !outcome.victory)
        assertEquals(1, svc.ticketCount()) // 2 - 1 门票，失败无返还
    }

    @Test
    fun tower_saveFailed_ticketRolledBack() = runTest {
        val svc = makeService(failSave = true)
        svc.own("char_a" to 80)
        svc.saveData.formation = listOf("char_a")
        svc.grantTickets(3)

        assertTrue(svc.runTowerFloor(1) is TowerOutcome.SaveFailed)
        assertEquals(3, svc.ticketCount())
    }

    // ── 每日商店 ──

    @Test
    fun dailyOffers_deterministicPerDay() {
        val svc = makeService(day = 20_000L) // 偶数日 → 折扣大包
        assertEquals(svc.dailyOffers(), svc.dailyOffers())
        val packOffer = svc.dailyOffers().first { it.kind == DailyOfferKind.DISCOUNT_PACK }
        assertEquals(2, packOffer.pack)
        assertEquals(EconomyFormulas.dailyDiscountPackCost(2), packOffer.costSoft)

        val oddDay = makeService(day = 20_001L) // 奇数日 → 折扣小包
        val oddPack = oddDay.dailyOffers().first { it.kind == DailyOfferKind.DISCOUNT_PACK }
        assertEquals(1, oddPack.pack)
    }

    @Test
    fun daily_freeSupply_oncePerDay_grantsSoftAndTickets() = runTest {
        val svc = makeService()
        val softBefore = svc.saveData.softCurrency

        assertTrue(svc.buyDailyOffer(0) is WriteOutcome.Success)
        assertEquals(softBefore + EconomyFormulas.dailyFreeSupplySoft(), svc.saveData.softCurrency)
        assertEquals(EconomyFormulas.dailyTicketGrant(), svc.ticketCount())
        assertEquals(listOf(0), svc.dailyBoughtToday())

        assertTrue(svc.buyDailyOffer(0) is WriteOutcome.Rejected) // 同日重复领取
    }

    @Test
    fun daily_ticketBundle_costsSoft_addsTickets() = runTest {
        val svc = makeService()
        val softBefore = svc.saveData.softCurrency

        assertTrue(svc.buyDailyOffer(2) is WriteOutcome.Success)
        assertEquals(softBefore - EconomyFormulas.dailyTicketBundleCost(), svc.saveData.softCurrency)
        assertEquals(EconomyFormulas.dailyTicketBundleSize(), svc.ticketCount())
    }

    @Test
    fun daily_discountPack_discountedPrice() = runTest {
        val svc = makeService(day = 20_000L)
        val softBefore = svc.saveData.softCurrency
        val cost = EconomyFormulas.dailyDiscountPackCost(2)
        val size = EconomyFormulas.fragmentPackSize(2)
        assertTrue(cost in 1 until softBefore)

        assertTrue(svc.buyDailyOffer(1) is WriteOutcome.Success)
        assertEquals(softBefore - cost, svc.saveData.softCurrency)
        val fragItem = svc.saveData.items.firstOrNull { it?.itemId == GameService.StarFragmentItemId }
        assertEquals(size, fragItem?.count)
    }

    @Test
    fun daily_insufficientSoft_rejected() = runTest {
        val svc = makeService()
        svc.saveData.softCurrency = 10
        assertTrue(svc.buyDailyOffer(2) is WriteOutcome.Rejected)
        assertEquals(emptyList<Int>(), svc.dailyBoughtToday())
    }

    @Test
    fun daily_dayRollover_resetsBought() = runTest {
        val provider = FakeProvider()
        val day1 = GameService(provider, testContent, {}, Random(42), today = { 20_000L })
        assertTrue(day1.buyDailyOffer(0) is WriteOutcome.Success)
        assertEquals(listOf(0), day1.dailyBoughtToday())

        // 同一存档、次日实例：已购清零，可再次购买
        val day2 = GameService(provider, testContent, {}, Random(42), today = { 20_001L })
        assertEquals(emptyList<Int>(), day2.dailyBoughtToday())
        assertTrue(day2.buyDailyOffer(0) is WriteOutcome.Success)
        assertEquals("20001", day2.saveData.dailyShopDate)
    }

    @Test
    fun daily_saveFailed_fullRollback() = runTest {
        val svc = makeService(failSave = true)
        val softBefore = svc.saveData.softCurrency

        assertTrue(svc.buyDailyOffer(0) is WriteOutcome.SaveFailed)
        assertEquals(softBefore, svc.saveData.softCurrency)
        assertEquals(0, svc.ticketCount())
        assertEquals("", svc.saveData.dailyShopDate)
        assertEquals(emptyList<Int>(), svc.dailyBoughtToday())
    }

    // ── 成就 ──

    @Test
    fun achievement_claimOnce_grantsReward() = runTest {
        val svc = makeService()
        svc.own("char_a" to 80) // first_summon 解锁（owned>=1）
        val softBefore = svc.saveData.softCurrency

        assertTrue(svc.claimAchievement("first_summon") is WriteOutcome.Success)
        assertEquals(softBefore + 500, svc.saveData.softCurrency)
        assertEquals(listOf("first_summon"), svc.saveData.claimedAchievementIds())

        assertTrue(svc.claimAchievement("first_summon") is WriteOutcome.Rejected) // 已领取
    }

    @Test
    fun achievement_lockedOrUnknown_rejected() = runTest {
        val svc = makeService()
        svc.own("char_a" to 80)

        assertTrue(svc.claimAchievement("roster_10") is WriteOutcome.Rejected) // 仅 1 人未解锁
        assertTrue(svc.claimAchievement("ghost_id") is WriteOutcome.Rejected) // 未知 id
        assertEquals(emptyList<String>(), svc.saveData.claimedAchievementIds())
    }

    @Test
    fun achievement_towerRewardGrantsTickets() = runTest {
        val svc = makeService()
        svc.own("char_a" to 80)
        svc.saveData.formation = listOf("char_a")
        svc.grantTickets(1)
        assertTrue(svc.runTowerFloor(1) is TowerOutcome.Completed)

        val status = svc.achievementStatuses().first { it.def.id == "tower_first" }
        assertTrue(status.unlocked)
        assertTrue(svc.claimAchievement("tower_first") is WriteOutcome.Success)
        // 战票 1（塔胜利净 0）+ 成就奖 2 = 3
        assertEquals(3, svc.ticketCount())
    }

    @Test
    fun achievement_saveFailed_rollback() = runTest {
        val svc = makeService(failSave = true)
        svc.own("char_a" to 80)
        val softBefore = svc.saveData.softCurrency

        assertTrue(svc.claimAchievement("first_summon") is WriteOutcome.SaveFailed)
        assertEquals(softBefore, svc.saveData.softCurrency)
        assertEquals(emptyList<String>(), svc.saveData.claimedAchievementIds())
    }

    /**
     * F3（2026-08-28 审查回归）：累计型成就进度**不得随出货倒退**。
     * 此前 totalPulls 取 `gachaCounters`（保底计数，出货即归零），实测抽 120 次却显示 45，
     * 且第 75 抽时从 74 直接掉到 0。现改读永不清零的 `SaveData.totalPullCount`。
     */
    @Test
    fun achievement_pullProgress_neverRegresses() = runTest {
        val svc = makeService()
        svc.saveData.softCurrency = 10_000_000

        var prev = 0
        repeat(120) { i ->
            svc.pull("pool_test", false)
            val now = svc.saveData.totalPullCount
            assertTrue("累计抽数不得倒退（第 ${i + 1} 抽：$prev -> $now）", now >= prev)
            prev = now
        }
        assertEquals("累计抽数应等于实际抽卡次数", 120, svc.saveData.totalPullCount)
    }

    @Test
    fun achievement_pulls100_unlocksAfter100Pulls() = runTest {
        val svc = makeService()
        svc.saveData.softCurrency = 10_000_000
        repeat(100) { svc.pull("pool_test", false) }

        val status = svc.achievementStatuses().first { it.def.id == "pulls_100" }
        assertTrue("抽满 100 次后成就应解锁（实际 totalPullCount=${svc.saveData.totalPullCount}）", status.unlocked)
        assertTrue(svc.claimAchievement("pulls_100") is WriteOutcome.Success)
    }
}
