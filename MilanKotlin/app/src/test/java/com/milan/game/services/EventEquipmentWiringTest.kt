package com.milan.game.services

import com.milan.game.data.EventRhythmSaveData
import com.milan.game.data.EventShopItem
import com.milan.game.data.EventTask
import com.milan.game.data.EventType
import com.milan.game.data.GameEvent
import com.milan.game.data.SaveProvider
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R5-I10/I12 P0 阻塞项回归测试（2026-09-04）。
 *
 * 覆盖三个「接线前必须先补的功能缺口」，每项都做**净变动**断言
 * （连续调用后看净增减，而非单次调用）—— R5 爬塔星尘永动机即因只断言单次调用而漏网。
 *
 * - C1 活动代币体系：奖励按 rewardType 入账、商店按 currencyType 扣款
 * - C2 活动激活：ensureActiveEvents 实例化模板并让四个写操作脱离恒 Rejected
 *
 * C3 装备发放测试见 EquipmentGrantWiringTest（2026-09-09 重建入库路径）。
 */
class EventEquipmentWiringTest {

    private class FakeProvider(var failSave: Boolean = false) : SaveProvider {
        var stored: String? = null
        override fun save(json: String): Boolean {
            if (failSave) return false
            stored = json
            return true
        }
        override fun load(): String = stored ?: ""
        override fun delete() { stored = null }
        override fun exists(): Boolean = stored != null
        override fun loadBackup(): String? = null
    }

    private val testContent = """
        {
          "Characters": [
            { "CharacterId": "char_a", "DisplayName": "测试甲", "BaseRarity": 3, "TalentTreeId": "tree_test" }
          ],
          "Pools": [
            {
              "PoolId": "pool_test", "RarityWeights": [0, 0, 1000, 0], "HardPity": 90,
              "SingleCost": 160, "TenCost": 1600,
              "Entries": [ { "CharacterId": "char_a", "RarityIndex": 3, "Weight": 1000 } ]
            }
          ],
          "TalentTrees": [
            { "TreeId": "tree_test", "Nodes": [ { "NodeId": "t1", "Cost": 1 } ] }
          ]
        }
    """.trimIndent()

    private fun makeService(provider: FakeProvider = FakeProvider(), soft: Int = 100_000): GameService =
        GameService(provider, testContent, { }, Random(42)).also { it.saveData.softCurrency = soft }

    // ═══════════════════ C2：活动激活路径 ═══════════════════

    @Test
    fun `激活前无任何活跃活动（复现休眠态）`() = runTest {
        val service = makeService()
        assertTrue("未激活时活跃活动必须为空（这正是 R5-C1~C3 漏洞休眠的根因）",
            service.getActiveEvents().isEmpty())
        assertTrue("默认模板应有 3 个", service.getDefaultEventDefinitions().size == 3)
    }

    @Test
    fun `ensureActiveEvents 激活后签到可用且钻石净增`() = runTest {
        val service = makeService()

        assertTrue("激活应成功", service.ensureActiveEvents() == WriteOutcome.Success)
        val signInEvent = service.getEventsByType(EventType.SIGN_IN).firstOrNull()
            ?: error("激活后应存在签到活动")

        val before = service.saveData.hardCurrency
        val outcome = service.signIn(signInEvent.eventId)

        assertTrue("签到应成功（此前 signIn 因 event 不存在恒 Rejected）",
            outcome == WriteOutcome.Success)
        // 首日奖励 = 100 + 0 * 20
        assertEquals("钻石应净增 100", before + 100, service.saveData.hardCurrency)
    }

    @Test
    fun `ensureActiveEvents 幂等且同毫秒实例化的 eventId 不碰撞`() = runTest {
        val service = makeService()

        service.ensureActiveEvents()
        val firstIds = service.getActiveEvents().map { it.eventId }
        service.ensureActiveEvents()
        val secondIds = service.getActiveEvents().map { it.eventId }

        assertEquals("应激活 3 个模板活动", 3, firstIds.size)
        assertEquals("同毫秒批量实例化不得产生重复 eventId", firstIds.size, firstIds.toSet().size)
        assertEquals("重复激活不得改变活动列表（幂等）", firstIds, secondIds)
    }

    @Test
    fun `激活后的签到活动带正确的可签天数`() = runTest {
        val service = makeService()
        service.ensureActiveEvents()

        val signInEvent = service.getEventsByType(EventType.SIGN_IN).first()

        // signInDays 缺失时 signIn() 的 `currentDays >= signInDays` 首日即成立 → 恒 Rejected
        assertEquals("七日签到活动的可签天数应为 7", 7, signInEvent.signInDays)
    }

    // ═══════════════════ C1：活动代币体系 ═══════════════════

    private fun serviceWithTokenTask(
        rewardType: String,
        service: GameService = makeService(),
    ): GameService = service.also {
        it.saveData.eventRhythmData = EventRhythmSaveData().also { d ->
            d.activeEvents = listOf(
                GameEvent(
                    eventId = "evt1",
                    eventType = EventType.LIMITED_DUNGEON.name,
                    startTime = 0,
                    endTime = Long.MAX_VALUE,
                    tasks = listOf(
                        EventTask(
                            taskId = "task1", target = 1,
                            rewardType = rewardType, rewardAmount = 300,
                        ),
                    ),
                ),
            )
            d.eventTaskProgress = mapOf("evt1" to mapOf("task1" to 1))
        }
    }

    @Test
    fun `活动代币奖励入账代币而非星尘`() = runTest {
        val service = serviceWithTokenTask("EVENT_CURRENCY")
        val beforeSoft = service.saveData.softCurrency
        val beforeToken = service.getEventCurrencyBalance("EVENT_CURRENCY")

        val outcome = service.claimEventTaskReward("evt1", "task1")

        assertTrue("领取应成功", outcome == WriteOutcome.Success)
        assertEquals(
            "星尘不得因代币奖励增加（旧实现 else 分支把 EVENT_CURRENCY 当星尘发）",
            beforeSoft, service.saveData.softCurrency,
        )
        assertEquals("活动代币应净增 300", beforeToken + 300,
            service.getEventCurrencyBalance("EVENT_CURRENCY"))
    }

    @Test
    fun `未知代币类型奖励既不入账也不错发星尘`() = runTest {
        val service = serviceWithTokenTask("NOT_A_REAL_CURRENCY")
        val beforeSoft = service.saveData.softCurrency

        val outcome = service.claimEventTaskReward("evt1", "task1")

        assertTrue("领取本身应成功（已领取门控仍须生效）", outcome == WriteOutcome.Success)
        assertEquals("未知代币不得错发星尘", beforeSoft, service.saveData.softCurrency)
        assertEquals("未知代币不得写入幽灵余额", 0,
            service.getEventCurrencyBalance("NOT_A_REAL_CURRENCY"))
    }

    private fun serviceWithTokenShop(
        balance: Int,
        price: Int = 3000,
        currencyType: String = "EVENT_CURRENCY",
        service: GameService = makeService(soft = 100_000),
    ): GameService = service.also {
        it.saveData.eventRhythmData = EventRhythmSaveData().also { d ->
            d.activeEvents = listOf(
                GameEvent(
                    eventId = "evt1",
                    eventType = EventType.LIMITED_DUNGEON.name,
                    startTime = 0,
                    endTime = Long.MAX_VALUE,
                    shopItems = listOf(
                        EventShopItem(
                            itemId = "item1", price = price,
                            currencyType = currencyType, maxRedemptions = 10,
                        ),
                    ),
                ),
            )
            d.eventCurrencyBalances = mapOf(currencyType to balance)
        }
    }

    @Test
    fun `活动商店按代币扣款而非星尘`() = runTest {
        val service = serviceWithTokenShop(balance = 5000)
        val beforeSoft = service.saveData.softCurrency

        val outcome = service.redeemEventShopItem("evt1", "item1", amount = 1)

        assertTrue("代币充足应兑换成功", outcome == WriteOutcome.Success)
        assertEquals("星尘不得被扣（旧实现一律扣星尘）", beforeSoft, service.saveData.softCurrency)
        assertEquals("代币应净减 3000", 2000, service.getEventCurrencyBalance("EVENT_CURRENCY"))
    }

    @Test
    fun `活动商店代币不足时拒绝且不得改用星尘垫付`() = runTest {
        val service = serviceWithTokenShop(balance = 100) // 星尘 100_000 充足，代币仅 100
        val beforeSoft = service.saveData.softCurrency
        val beforeToken = service.getEventCurrencyBalance("EVENT_CURRENCY")

        val outcome = service.redeemEventShopItem("evt1", "item1", amount = 1)

        assertTrue("代币不足必须拒绝", outcome == WriteOutcome.Rejected)
        assertEquals("不得改用星尘垫付", beforeSoft, service.saveData.softCurrency)
        assertEquals("代币不得被扣", beforeToken, service.getEventCurrencyBalance("EVENT_CURRENCY"))
    }

    @Test
    fun `多次兑换净扣代币且受兑换上限约束`() = runTest {
        val service = serviceWithTokenShop(balance = 50_000, price = 1000)
        val beforeToken = service.getEventCurrencyBalance("EVENT_CURRENCY")

        // 连续兑换 10 次（= maxRedemptions 上限）
        repeat(10) { service.redeemEventShopItem("evt1", "item1", amount = 1) }
        val afterTen = service.getEventCurrencyBalance("EVENT_CURRENCY")
        assertEquals("10 次兑换应净扣 10000", beforeToken - 10_000, afterTen)

        // 第 11 次应被上限拒绝
        val eleventh = service.redeemEventShopItem("evt1", "item1", amount = 1)
        assertTrue("超出 maxRedemptions 必须拒绝", eleventh == WriteOutcome.Rejected)
        assertEquals("拒绝后代币不得再变", afterTen, service.getEventCurrencyBalance("EVENT_CURRENCY"))
    }

    // C3 装备发放回归测试迁至 EquipmentGrantWiringTest（2026-09-09 重建 grantEquipment）。
}
