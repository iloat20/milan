package com.milan.game.services

import com.milan.game.data.ArenaSaveData
import com.milan.game.data.EventRhythmSaveData
import com.milan.game.data.EventType
import com.milan.game.data.GameEvent
import com.milan.game.data.EventTask
import com.milan.game.data.SaveData
import com.milan.game.data.SaveProvider
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R5 经济漏洞修复回归测试（2026-09-03）。
 *
 * 覆盖本轮修复的经济类缺陷：负数穿透、重复领取永动机、回滚遗漏。
 * 每项都做「净变动」断言（连续调用后净增减），而非单次调用。
 */
class EconomyGuardRegressionTest {

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

    // ── C6 删除（2026-09-06 S2）：公会捐献属死功能 SocialService 已删 ──

    // ── C1：活动任务奖励不可重复领取 ──

    private fun serviceWithClaimableTask(provider: FakeProvider = FakeProvider()): GameService {
        val service = makeService(provider)
        service.saveData.eventRhythmData = EventRhythmSaveData().also { d ->
            d.activeEvents = listOf(
                GameEvent(
                    eventId = "evt1",
                    eventType = EventType.SIGN_IN.name,
                    startTime = 0,
                    endTime = Long.MAX_VALUE,
                    tasks = listOf(EventTask(taskId = "task1", target = 1, rewardType = "SOFT_CURRENCY", rewardAmount = 500)),
                ),
            )
            // 任务进度已达 target
            d.eventTaskProgress = mapOf("evt1" to mapOf("task1" to 1))
        }
        return service
    }

    @Test
    fun `活动任务奖励只能领取一次`() = runTest {
        val service = serviceWithClaimableTask()
        val before = service.saveData.softCurrency

        val first = service.claimEventTaskReward("evt1", "task1")
        val afterFirst = service.saveData.softCurrency
        val second = service.claimEventTaskReward("evt1", "task1")

        assertTrue("首次领取应成功", first == WriteOutcome.Success)
        assertEquals("首次领取 +500", before + 500, afterFirst)
        assertTrue("第二次领取必须拒绝（防永动机）", second == WriteOutcome.Rejected)
        assertEquals("拒绝后星尘不得再变", afterFirst, service.saveData.softCurrency)
    }

    // ── C2：活动商店兑换负数/余额不足拒绝 ──

    private fun serviceWithShop(provider: FakeProvider = FakeProvider(), soft: Int = 100_000): GameService {
        val service = makeService(provider, soft)
        service.saveData.eventRhythmData = EventRhythmSaveData().also { d ->
            d.activeEvents = listOf(
                GameEvent(
                    eventId = "evt1",
                    eventType = EventType.SIGN_IN.name,
                    startTime = 0,
                    endTime = Long.MAX_VALUE,
                    shopItems = listOf(
                        com.milan.game.data.EventShopItem(itemId = "item1", price = 1000, maxRedemptions = 10),
                    ),
                ),
            )
        }
        return service
    }

    @Test
    fun `活动商店负数数量拒绝且星尘不变`() = runTest {
        val service = serviceWithShop()
        val before = service.saveData.softCurrency

        val outcome = service.redeemEventShopItem("evt1", "item1", amount = -1)

        assertTrue("负数数量必须拒绝", outcome == WriteOutcome.Rejected)
        assertEquals("星尘不得因负数数量反向增加", before, service.saveData.softCurrency)
    }

    @Test
    fun `活动商店余额不足拒绝`() = runTest {
        val service = serviceWithShop(soft = 100) // 余额 100 < 单价 1000
        val before = service.saveData.softCurrency

        val outcome = service.redeemEventShopItem("evt1", "item1", amount = 1)

        assertTrue("余额不足必须拒绝", outcome == WriteOutcome.Rejected)
        assertEquals("拒绝后星尘不变", before, service.saveData.softCurrency)
    }

    // ── C4：通行证奖励落盘失败必须回滚货币 ──

    @Test
    fun `通行证奖励落盘失败回滚货币`() = runTest {
        val provider = FakeProvider(failSave = true)
        val service = makeService(provider)
        service.saveData.monetizationData = com.milan.game.data.MonetizationSaveData().also {
            it.battlePassLevel = 5
        }
        val beforeSoft = service.saveData.softCurrency
        val beforeHard = service.saveData.hardCurrency

        val outcome = service.claimBattlePassReward(1)

        assertTrue("落盘失败应返回 SaveFailed", outcome == WriteOutcome.SaveFailed)
        assertEquals("星尘必须回滚", beforeSoft, service.saveData.softCurrency)
        assertEquals("钻石必须回滚", beforeHard, service.saveData.hardCurrency)
    }

    // ── C7 删除（2026-09-06 S2）：装备强化属死功能 EquipmentService 已删 ──

    // ── I9：通行证经验随核心行为增长 ──

    @Test
    fun `抽卡成功后通行证经验增加`() = runTest {
        val service = makeService()
        service.saveData.monetizationData = com.milan.game.data.MonetizationSaveData()
        val before = service.getMonetizationData().battlePassExp

        val outcome = service.pull("pool_test", tenPull = false)

        assertTrue("抽卡应成功", outcome is PullOutcome.Success)
        assertTrue(
            "通行证经验应随抽卡增长（此前 addBattlePassExp 零接线，经验恒 0）",
            service.getMonetizationData().battlePassExp > before,
        )
    }
}
