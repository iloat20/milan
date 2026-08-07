package com.milan.game.services

import com.milan.game.data.BattleRecord
import com.milan.game.data.CharacterSaveState
import com.milan.game.data.ItemSaveState
import com.milan.game.data.SaveProvider
import com.milan.game.infrastructure.eventbus.CurrencyChanged
import com.milan.game.infrastructure.eventbus.EventBus
import com.milan.game.infrastructure.eventbus.ProgressionChanged
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * GameService 编排层测试（C# 无对应测试，补事务关键路径）：
 * 抽卡/货币/养成的成功路径、落盘失败回滚且不广播、事件仅在成功时发出。
 * 内容用单角色池 JSON：抽卡结果完全确定（SSR 唯一候选），避免随机种子依赖。
 */
class GameServiceTest {

    private val testContent = """
        {
          "Characters": [
            {
              "CharacterId": "char_a",
              "DisplayName": "测试甲",
              "BaseRarity": 3,
              "TalentTreeId": "tree_test"
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
                { "CharacterId": "char_a", "RarityIndex": 3, "Weight": 1000 }
              ]
            }
          ],
          "TalentTrees": [
            {
              "TreeId": "tree_test",
              "Nodes": [
                { "NodeId": "t1", "Cost": 1 },
                { "NodeId": "t2", "Cost": 1, "PrerequisiteNodeIds": ["t1"] }
              ]
            }
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
        override fun delete() { stored = null }
        override fun exists(): Boolean = stored != null
        override fun loadBackup(): String? = null
    }

    /** 事件计数器：验证事件只在成功路径发出。 */
    private class EventCounter {
        var currency = 0
        var progression = 0

        init {
            EventBus.subscribe<CurrencyChanged> { currency++ }
            EventBus.subscribe<ProgressionChanged> { progression++ }
        }
    }

    @Before
    fun setUp() {
        EventBus.clear()
        EventBus.handlerException = null
    }

    private fun makeService(
        provider: FakeProvider = FakeProvider(),
        content: String? = testContent,
        seed: Long = 42,
    ): GameService = GameService(provider, content, provider.traces::add, Random(seed))

    // ── 抽卡 ──

    @Test
    fun pull_ten_success_deductsAndAwards() {
        val service = makeService()
        val events = EventCounter()

        val results = service.pull("pool_test", tenPull = true)

        assertEquals(10, results.size)
        assertTrue(results.all { it.success })
        assertTrue(results.all { it.characterId == "char_a" })
        // C# 对齐：isNew 在规划阶段统一判定（规划时 OwnedCharacters 尚未变更），
        // 首次十连 10 条全部判定为新角色，碎片补偿为 0（C# Pull line 236/260 同款行为）。
        assertTrue(results.all { it.isNew })
        assertTrue(results.all { it.fragmentsAwarded == 0 })
        assertEquals(999999 - 1600, service.saveData.softCurrency)
        // 发货阶段 10 条 isNew 全部 Add（C# OwnedCharacters.Add 同款，含重复条目）
        assertEquals(10, service.saveData.ownedCharacters.size)
        assertEquals(0, service.getStarFragments())
        // 单 SSR 池每抽必出保底档（SSR，value 3 >= minRarityForPity 3）：
        // 自然出货重置计数器（C# RollWithPity line 31 对齐），故十连后 counter 仍为 0。
        assertEquals(0, service.saveData.getGachaCounter("pool_test"))
        assertEquals(1, events.currency)
        assertEquals(0, events.progression)
    }

    @Test
    fun pull_single_success() {
        val service = makeService()
        val events = EventCounter()

        val results = service.pull("pool_test", tenPull = false)

        assertEquals(1, results.size)
        assertTrue(results[0].isNew)
        assertEquals(0, results[0].fragmentsAwarded)
        assertEquals(999999 - 160, service.saveData.softCurrency)
        assertEquals(1, events.currency)
    }

    @Test
    fun pull_insufficientFunds_returnsEmptyNoBroadcast() {
        val service = makeService()
        val events = EventCounter()
        service.saveData.softCurrency = 100

        val results = service.pull("pool_test", tenPull = true)

        assertTrue(results.isEmpty())
        assertEquals(100, service.saveData.softCurrency)
        assertTrue(service.saveData.ownedCharacters.isEmpty())
        assertEquals(0, events.currency)
    }

    @Test
    fun pull_unknownPool_returnsEmpty() {
        val service = makeService()

        val results = service.pull("no_such_pool", tenPull = true)

        assertTrue(results.isEmpty())
        assertEquals(999999, service.saveData.softCurrency)
    }

    @Test
    fun pull_saveFailure_rollsBackNoBroadcast() {
        val provider = FakeProvider(failSave = true)
        val service = makeService(provider)
        val events = EventCounter()

        val results = service.pull("pool_test", tenPull = true)

        assertTrue(results.isEmpty())
        assertEquals(999999, service.saveData.softCurrency)
        assertTrue(service.saveData.ownedCharacters.isEmpty())
        assertEquals(0, service.getStarFragments())
        assertEquals(0, service.saveData.getGachaCounter("pool_test"))
        assertEquals(0, events.currency)
        // 留痕：玩家可重试
        assertTrue(provider.traces.contains("pull.save.failed: rolled back"))
    }

    // ── 货币 ──

    @Test
    fun spendSoft_insufficient_returnsFalse() {
        val service = makeService()
        val events = EventCounter()
        service.saveData.softCurrency = 100

        assertFalse(service.spendSoft(101))
        assertEquals(100, service.saveData.softCurrency)
        assertEquals(0, events.currency)
    }

    @Test
    fun spendSoft_saveFailure_rollsBackNoBroadcast() {
        val service = makeService(FakeProvider(failSave = true))
        val events = EventCounter()

        assertFalse(service.spendSoft(10))
        assertEquals(999999, service.saveData.softCurrency)
        assertEquals(0, events.currency)
    }

    @Test
    fun addSoft_success_broadcasts() {
        val service = makeService()
        val events = EventCounter()

        assertTrue(service.addSoft(100))
        assertEquals(999999 + 100, service.saveData.softCurrency)
        assertEquals(1, events.currency)
    }

    @Test
    fun hardCurrency_spendAndAdd() {
        val service = makeService()
        val events = EventCounter()

        assertTrue(service.addHard(300))
        assertEquals(300, service.saveData.hardCurrency)
        assertEquals(1, events.currency)

        assertTrue(service.spendHard(50))
        assertEquals(250, service.saveData.hardCurrency)
        assertEquals(2, events.currency)

        assertFalse(service.spendHard(1000))
        assertEquals(250, service.saveData.hardCurrency)
        assertEquals(2, events.currency)
    }

    // ── 升级 ──

    @Test
    fun levelUp_success_broadcastsAndGrantsPoints() {
        val service = makeService()
        service.pull("pool_test", tenPull = true)
        // pull 之后创建计数器：只统计养成操作自身的事件
        val events = EventCounter()

        assertTrue(service.levelUp("char_a"))

        val save = service.getSave("char_a")!!
        assertEquals(2, save.level)
        assertEquals(1, save.unspentPoints)
        // levelCost(1) = 50
        assertEquals(999999 - 1600 - 50, service.saveData.softCurrency)
        assertEquals(1, events.currency)
        assertEquals(1, events.progression)
    }

    @Test
    fun levelUp_unknownCharacter_returnsFalse() {
        val service = makeService()
        val events = EventCounter()

        assertFalse(service.levelUp("char_a"))
        assertEquals(0, events.currency)
        assertEquals(0, events.progression)
    }

    @Test
    fun levelUp_insufficientSoft_returnsFalse() {
        val service = makeService()
        service.pull("pool_test", tenPull = true)
        val events = EventCounter()
        service.saveData.softCurrency = 10

        assertFalse(service.levelUp("char_a"))
        assertEquals(1, service.getSave("char_a")!!.level)
        assertEquals(0, events.currency)
    }

    @Test
    fun levelUp_atMaxLevel_returnsFalse() {
        val service = makeService()
        service.pull("pool_test", tenPull = true)
        val events = EventCounter()
        // Stage1 → 上限 20 级
        service.getSave("char_a")!!.level = 20

        assertFalse(service.levelUp("char_a"))
        assertEquals(20, service.getSave("char_a")!!.level)
        assertEquals(0, events.currency)
    }

    @Test
    fun levelUp_saveFailure_rollsBackNoBroadcast() {
        val service = makeService(FakeProvider(failSave = true))
        val events = EventCounter()
        // failSave 下抽卡同样回滚拿不到角色：直接播种拥有状态
        service.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = "char_a"))

        assertFalse(service.levelUp("char_a"))
        val save = service.getSave("char_a")!!
        assertEquals(1, save.level)
        assertEquals(0, save.unspentPoints)
        assertEquals(999999, service.saveData.softCurrency)
        assertEquals(0, events.currency)
    }

    // ── 突破 / 升星 ──

    @Test
    fun ascend_success() {
        val service = makeService()
        val events = EventCounter()
        // 直接播种拥有状态 + 100 碎片（不依赖抽卡产出，断言确定）
        service.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = "char_a"))
        service.saveData.items =
            listOf(ItemSaveState(itemId = GameService.StarFragmentItemId, count = 100))

        assertTrue(service.ascend("char_a"))

        val save = service.getSave("char_a")!!
        assertEquals(2, save.stage)
        // ascendFragments(1)=20，ascendSoft(1)=500
        assertEquals(80, service.getStarFragments())
        assertEquals(999999 - 500, service.saveData.softCurrency)
        assertEquals(1, events.currency)
        assertEquals(1, events.progression)
    }

    @Test
    fun ascend_insufficientFragments_returnsFalse() {
        val service = makeService()
        val events = EventCounter()
        service.pull("pool_test", tenPull = false) // 无重复，碎片 0

        assertFalse(service.ascend("char_a"))
        assertEquals(1, service.getSave("char_a")!!.stage)
        assertEquals(0, events.progression)
    }

    @Test
    fun starUp_success() {
        val service = makeService()
        val events = EventCounter()
        // 直接播种拥有状态 + 100 碎片（不依赖抽卡产出，断言确定）
        service.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = "char_a"))
        service.saveData.items =
            listOf(ItemSaveState(itemId = GameService.StarFragmentItemId, count = 100))

        assertTrue(service.starUp("char_a"))

        val save = service.getSave("char_a")!!
        assertEquals(2, save.stars)
        // starUpFragments(1)=20
        assertEquals(80, service.getStarFragments())
        assertEquals(1, events.currency)
        assertEquals(1, events.progression)
    }

    @Test
    fun starUp_insufficientFragments_returnsFalse() {
        val service = makeService()
        val events = EventCounter()
        service.pull("pool_test", tenPull = false)

        assertFalse(service.starUp("char_a"))
        assertEquals(1, service.getSave("char_a")!!.stars)
        assertEquals(0, events.progression)
    }

    // ── 天赋 ──

    @Test
    fun allocateTalent_requiresPrereqAndPoints() {
        val service = makeService()
        val events = EventCounter()
        service.saveData.ownedCharacters =
            listOf(CharacterSaveState(characterId = "char_a", unspentPoints = 3))

        // 前置 t1 未点亮 → 拒绝
        assertFalse(service.allocateTalent("char_a", "t2"))
        // 不存在的节点 → 拒绝
        assertFalse(service.allocateTalent("char_a", "no_such"))

        assertTrue(service.allocateTalent("char_a", "t1"))
        assertTrue(service.allocateTalent("char_a", "t2"))
        // 已点过 → 拒绝
        assertFalse(service.allocateTalent("char_a", "t1"))

        val save = service.getSave("char_a")!!
        assertEquals(1, save.unspentPoints)
        assertEquals(listOf("t1", "t2"), save.talentPoints)
        assertEquals(2, events.progression)
    }

    @Test
    fun allocateTalent_noPoints_returnsFalse() {
        val service = makeService()
        service.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = "char_a"))

        assertFalse(service.allocateTalent("char_a", "t1"))
    }

    // ── 战绩 ──

    @Test
    fun getBattleRecords_emptyWhenNone() {
        val service = makeService()

        assertTrue(service.getBattleRecords().isEmpty())
    }

    @Test
    fun recordBattle_capsAt50() {
        val service = makeService()
        repeat(52) { i ->
            service.recordBattle(BattleRecord(enemyName = "enemy$i", victory = i % 2 == 0))
        }

        val records = service.getBattleRecords()
        assertEquals(50, records.size)
        // 最旧的 2 条被丢弃
        assertEquals("enemy2", records.first().enemyName)
        assertEquals("enemy51", records.last().enemyName)
    }

    @Test
    fun recordBattle_saveFailure_rollsBack() {
        val service = makeService(FakeProvider(failSave = true))
        val events = EventCounter()

        service.recordBattle(BattleRecord(enemyName = "enemy0"))

        assertTrue(service.getBattleRecords().isEmpty())
        assertEquals(0, events.currency)
        assertEquals(0, events.progression)
    }
}
