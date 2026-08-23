package com.milan.game.services

import com.milan.game.data.BattleRecord
import com.milan.game.data.CharacterSaveState
import com.milan.game.data.ItemSaveState
import com.milan.game.data.SaveProvider
import com.milan.game.domain.progression.EconomyFormulas
import com.milan.game.infrastructure.eventbus.CurrencyChanged
import com.milan.game.infrastructure.eventbus.EventBus
import com.milan.game.infrastructure.eventbus.ProgressionChanged
import com.milan.game.services.PullOutcome
import com.milan.game.services.WriteOutcome
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
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

    private class FakeProvider(var failSave: Boolean = false, var failDelete: Boolean = false) : SaveProvider {
        var stored: String? = null
        var backup: String? = null // 建模 .bak 备份链（AndroidSaveProvider 语义）
        val traces = mutableListOf<String>()
        override fun save(json: String): Boolean {
            if (failSave) return false
            stored?.let { backup = it } // 写档前把旧主档滚为备份
            stored = json
            return true
        }
        override fun load(): String = stored ?: ""
        override fun delete() {
            if (failDelete) throw RuntimeException("delete failed")
            stored = null
            backup = null // 与修复后的 AndroidSaveProvider 一致：整条备份链一起清
        }
        override fun exists(): Boolean = stored != null
        override fun loadBackup(): String? = backup
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
    fun pull_ten_success_deductsAndAwards() = runTest {
        val service = makeService()
        val events = EventCounter()

        val outcome = service.pull("pool_test", tenPull = true)
        assertTrue(outcome is PullOutcome.Success)
        val results = (outcome as PullOutcome.Success).results

        assertEquals(10, results.size)
        assertTrue(results.all { it.success })
        assertTrue(results.all { it.characterId == "char_a" })
        // 单角色池十连：首次命中为新角色（isNew=true，0 碎片），
        // 同批重复出现的 9 次按「重复角色」补偿碎片（SSR=20）——修复历史 C# 缺陷：
        // 此前 10 条全判 isNew、0 碎片，且 ownedCharacters 写入 10 条重复条目
        // （下次载入 sanitize 去重，碎片永久丢失）。
        assertTrue(results.first().isNew)
        assertTrue(results.drop(1).all { !it.isNew })
        assertEquals(0, results.first().fragmentsAwarded)
        assertTrue(results.drop(1).all { it.fragmentsAwarded == 20 })
        assertEquals(999999 - 1600, service.saveData.softCurrency)
        // 新角色只追加一条拥有条目；9 次重复 → 9×20 = 180 碎片
        assertEquals(1, service.saveData.ownedCharacters.size)
        assertEquals(180, service.getStarFragments())
        // 单 SSR 池每抽必出保底档（SSR，value 3 >= minRarityForPity 3）：
        // 自然出货重置计数器（C# RollWithPity line 31 对齐），故十连后 counter 仍为 0。
        assertEquals(0, service.saveData.getGachaCounter("pool_test"))
        assertEquals(1, events.currency)
        assertEquals(0, events.progression)
    }

    @Test
    fun pull_ten_ownedCharacter_allDuplicatesAwardFragments() = runTest {
        // 已拥有角色后的十连：全部按重复补偿，不再追加拥有条目（防无限刷拥有数）
        val service = makeService()
        service.pull("pool_test", tenPull = true) // 首次十连：拥有 char_a × 1 + 180 碎片
        val events = EventCounter()

        val outcome = service.pull("pool_test", tenPull = true)
        assertTrue(outcome is PullOutcome.Success)
        val results = (outcome as PullOutcome.Success).results

        assertEquals(10, results.size)
        assertTrue(results.all { !it.isNew })
        assertTrue(results.all { it.fragmentsAwarded == 20 })
        assertEquals(1, service.saveData.ownedCharacters.size) // 拥有数不增长
        assertEquals(180 + 200, service.getStarFragments()) // +10×20
        assertEquals(1, events.currency) // 计数器在首次十连后才创建，本次仅 1 次广播
    }

    @Test
    fun pull_single_success() = runTest {
        val service = makeService()
        val events = EventCounter()

        val outcome = service.pull("pool_test", tenPull = false)
        assertTrue(outcome is PullOutcome.Success)
        val results = (outcome as PullOutcome.Success).results

        assertEquals(1, results.size)
        assertTrue(results[0].isNew)
        assertEquals(0, results[0].fragmentsAwarded)
        assertEquals(999999 - 160, service.saveData.softCurrency)
        assertEquals(1, events.currency)
    }

    @Test
    fun pull_insufficientFunds_rejectedNoBroadcast() = runTest {
        val service = makeService()
        val events = EventCounter()
        service.saveData.softCurrency = 100

        val outcome = service.pull("pool_test", tenPull = true)

        assertEquals(PullOutcome.Rejected, outcome)
        assertEquals(100, service.saveData.softCurrency)
        assertTrue(service.saveData.ownedCharacters.isEmpty())
        assertEquals(0, events.currency)
    }

    @Test
    fun pull_unknownPool_rejected() = runTest {
        val service = makeService()

        assertEquals(PullOutcome.Rejected, service.pull("no_such_pool", tenPull = true))
        assertEquals(999999, service.saveData.softCurrency)
    }

    @Test
    fun pull_saveFailure_rollsBackNoBroadcast() = runTest {
        val provider = FakeProvider(failSave = true)
        val service = makeService(provider)
        val events = EventCounter()

        val outcome = service.pull("pool_test", tenPull = true)

        assertEquals(PullOutcome.SaveFailed, outcome)
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
    fun spendSoft_insufficient_rejected() = runTest {
        val service = makeService()
        val events = EventCounter()
        service.saveData.softCurrency = 100

        assertEquals(WriteOutcome.Rejected, service.spendSoft(101))
        assertEquals(100, service.saveData.softCurrency)
        assertEquals(0, events.currency)
    }

    @Test
    fun spendSoft_saveFailure_rollsBackNoBroadcast() = runTest {
        val service = makeService(FakeProvider(failSave = true))
        val events = EventCounter()

        assertEquals(WriteOutcome.SaveFailed, service.spendSoft(10))
        assertEquals(999999, service.saveData.softCurrency)
        assertEquals(0, events.currency)
    }

    @Test
    fun addSoft_success_broadcasts() = runTest {
        val service = makeService()
        val events = EventCounter()

        assertEquals(WriteOutcome.Success, service.addSoft(100))
        assertEquals(999999 + 100, service.saveData.softCurrency)
        assertEquals(1, events.currency)
    }

    @Test
    fun hardCurrency_spendAndAdd() = runTest {
        val service = makeService()
        val events = EventCounter()

        assertEquals(WriteOutcome.Success, service.addHard(300))
        assertEquals(300, service.saveData.hardCurrency)
        assertEquals(1, events.currency)

        assertEquals(WriteOutcome.Success, service.spendHard(50))
        assertEquals(250, service.saveData.hardCurrency)
        assertEquals(2, events.currency)

        assertEquals(WriteOutcome.Rejected, service.spendHard(1000))
        assertEquals(250, service.saveData.hardCurrency)
        assertEquals(2, events.currency)
    }

    @Test
    fun currency_negativeAmounts_rejected() = runTest {
        // P2-6：负数金额防御——spendSoft(-50) 若直传会变相加钱，一律拒绝且零变更
        val service = makeService()
        val events = EventCounter()

        assertEquals(WriteOutcome.Rejected, service.spendSoft(-50))
        assertEquals(WriteOutcome.Rejected, service.addSoft(-50))
        assertEquals(WriteOutcome.Rejected, service.spendHard(-10))
        assertEquals(WriteOutcome.Rejected, service.addHard(-10))
        assertEquals(WriteOutcome.Rejected, service.spendSoft(0))
        assertEquals(999999, service.saveData.softCurrency)
        assertEquals(0, service.saveData.hardCurrency)
        assertEquals(0, events.currency)
    }

    @Test
    fun currency_overflow_rejected() = runTest {
        // P2-5：Int 溢出防护——余额接近上限时加大额奖励不得静默回绕成负/小值
        val service = makeService()
        service.saveData.softCurrency = Int.MAX_VALUE

        assertEquals(WriteOutcome.Rejected, service.addSoft(1)) // MAX+1 溢出 → 拒绝
        assertEquals(Int.MAX_VALUE, service.saveData.softCurrency)
    }

    // ── 升级 ──

    @Test
    fun levelUp_success_broadcastsAndGrantsPoints() = runTest {
        val service = makeService()
        service.pull("pool_test", tenPull = true)
        // pull 之后创建计数器：只统计养成操作自身的事件
        val events = EventCounter()

        assertEquals(WriteOutcome.Success, service.levelUp("char_a"))

        val save = service.getSave("char_a")!!
        assertEquals(2, save.level)
        assertEquals(1, save.unspentPoints)
        // levelCost(1) = 50
        assertEquals(999999 - 1600 - 50, service.saveData.softCurrency)
        assertEquals(1, events.currency)
        assertEquals(1, events.progression)
    }

    @Test
    fun levelUp_unknownCharacter_rejected() = runTest {
        val service = makeService()
        val events = EventCounter()

        assertEquals(WriteOutcome.Rejected, service.levelUp("char_a"))
        assertEquals(0, events.currency)
        assertEquals(0, events.progression)
    }

    @Test
    fun levelUp_insufficientSoft_rejected() = runTest {
        val service = makeService()
        service.pull("pool_test", tenPull = true)
        val events = EventCounter()
        service.saveData.softCurrency = 10

        assertEquals(WriteOutcome.Rejected, service.levelUp("char_a"))
        assertEquals(1, service.getSave("char_a")!!.level)
        assertEquals(0, events.currency)
    }

    @Test
    fun levelUp_atMaxLevel_rejected() = runTest {
        val service = makeService()
        service.pull("pool_test", tenPull = true)
        val events = EventCounter()
        // Stage1 → 上限 20 级
        service.getSave("char_a")!!.level = 20

        assertEquals(WriteOutcome.Rejected, service.levelUp("char_a"))
        assertEquals(20, service.getSave("char_a")!!.level)
        assertEquals(0, events.currency)
    }

    @Test
    fun levelUp_saveFailure_rollsBackNoBroadcast() = runTest {
        val service = makeService(FakeProvider(failSave = true))
        val events = EventCounter()
        // failSave 下抽卡同样回滚拿不到角色：直接播种拥有状态
        service.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = "char_a"))

        assertEquals(WriteOutcome.SaveFailed, service.levelUp("char_a"))
        val save = service.getSave("char_a")!!
        assertEquals(1, save.level)
        assertEquals(0, save.unspentPoints)
        assertEquals(999999, service.saveData.softCurrency)
        assertEquals(0, events.currency)
    }

    @Test
    fun levelUp_saveFailure_preservesPartialExp() = runTest {
        // P1-1 回归：升级前角色带有 addExp 攒下的经验零头（totalExp=50, level=1），
        // 落盘失败回滚必须恢复原值——此前回滚用「按等级重算累计经验」，
        // 会把 totalExp 写成 cumulativeExp(1)=0，零头从内存消失，与磁盘旧档分叉，
        // 下次成功保存即永久丢失 50 经验。
        val service = makeService(FakeProvider(failSave = true))
        val events = EventCounter()
        service.saveData.ownedCharacters =
            listOf(CharacterSaveState(characterId = "char_a", totalExp = 50))

        assertEquals(WriteOutcome.SaveFailed, service.levelUp("char_a"))

        val save = service.getSave("char_a")!!
        assertEquals(1, save.level)
        assertEquals(50, save.totalExp) // 零头保留，内存与磁盘一致
        assertEquals(0, save.unspentPoints)
        assertEquals(999999, service.saveData.softCurrency)
        assertEquals(0, events.currency)
        assertEquals(0, events.progression)
    }

    @Test
    fun levelUp_keepsTotalExpConsistent() = runTest {
        val service = makeService()
        service.pull("pool_test", tenPull = true)
        val events = EventCounter()

        assertEquals(WriteOutcome.Success, service.levelUp("char_a"))

        val save = service.getSave("char_a")!!
        assertEquals(2, save.level)
        // #1 修复：levelUp 改为累加已完成等级的累计经验，totalExp 与等级一致；
        // 升级后停在新等级起点，本层进度为 0，need=expForLevel(2)=200。
        val (cur, need) = service.expProgress("char_a")
        assertEquals(0, cur)
        assertEquals(200, need)
        assertEquals(1, events.currency)
        assertEquals(1, events.progression)
    }

    @Test
    fun addExp_accumulatesAndFillsBar() = runTest {
        val service = makeService()
        service.pull("pool_test", tenPull = true)
        val events = EventCounter()

        // 未达升级的少量经验：等级不变，经验条应有进度（#1 经验条死掉的根因修复）
        val gained = service.addExp("char_a", 50)
        assertEquals(0, gained)

        val save = service.getSave("char_a")!!
        assertEquals(1, save.level)
        assertEquals(50, save.totalExp) // 累计经验真值增长
        val (cur, need) = service.expProgress("char_a")
        assertEquals(50, cur) // 经验条随奖励推进
        assertEquals(100, need) // expForLevel(1)=100
        assertEquals(1, events.progression) // 经验变化也广播，UI 重算
    }

    @Test
    fun addExp_autoLevelsAndCapsAtStageMax() = runTest {
        val service = makeService()
        service.pull("pool_test", tenPull = true)
        val events = EventCounter()

        // 一次性灌足够升到 3 级的经验：100(→L2) + 200(→L3) = 300
        val gained = service.addExp("char_a", 300)
        assertEquals(2, gained)

        val save = service.getSave("char_a")!!
        assertEquals(3, save.level)
        assertEquals(2, save.unspentPoints) // 每级 +1 天赋点
        val (cur, need) = service.expProgress("char_a")
        assertEquals(0, cur) // 恰好停在 L3 起点
        assertEquals(300, need)
        assertEquals(1, events.progression)
    }

    @Test
    fun addExp_saveFailure_rollsBack() = runTest {
        val service = makeService(FakeProvider(failSave = true))
        val events = EventCounter()
        service.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = "char_a"))

        val gained = service.addExp("char_a", 50)
        assertEquals(0, gained)
        val save = service.getSave("char_a")!!
        assertEquals(0, save.totalExp)
        assertEquals(1, save.level)
        assertEquals(0, events.progression)
    }

    // ── 突破 / 升星 ──

    @Test
    fun ascend_success() = runTest {
        val service = makeService()
        val events = EventCounter()
        // 直接播种拥有状态 + 100 碎片（不依赖抽卡产出，断言确定）
        service.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = "char_a"))
        service.saveData.items =
            listOf(ItemSaveState(itemId = GameService.StarFragmentItemId, count = 100))

        assertEquals(WriteOutcome.Success, service.ascend("char_a"))

        val save = service.getSave("char_a")!!
        assertEquals(2, save.stage)
        // ascendFragments(1)=20，ascendSoft(1)=500
        assertEquals(80, service.getStarFragments())
        assertEquals(999999 - 500, service.saveData.softCurrency)
        assertEquals(1, events.currency)
        assertEquals(1, events.progression)
    }

    @Test
    fun ascend_relevelsBankedExp() = runTest {
        // P2-12：角色带「银行经验」（满级后 addExp 继续累计）突破 → 等级随新上限重推导、
        // 差额级数补发天赋点，玩家不为已用经验换到的等级重复付费
        val service = makeService()
        val events = EventCounter()
        // Stage1 上限 20 级；累计经验推到 20 级 + 再攒 500 经验（level=20, totalExp>cumulativeExp(20)）
        service.saveData.ownedCharacters = listOf(
            CharacterSaveState(
                characterId = "char_a",
                level = 20,
                totalExp = EconomyFormulas.cumulativeExp(20) + 500,
                unspentPoints = 19,
            ),
        )
        service.saveData.items =
            listOf(ItemSaveState(itemId = GameService.StarFragmentItemId, count = 100))

        assertEquals(WriteOutcome.Success, service.ascend("char_a"))

        val save = service.getSave("char_a")!!
        assertEquals(2, save.stage)
        // cumulativeExp(20)=19000，+500=19500 → expToLevel 推到 20 级（cumulativeExp(21)=21000>19500）
        // 上限变 40，等级保持 20，无差额 → 点数不变
        assertEquals(20, save.level)
        assertEquals(19, save.unspentPoints)
        assertEquals(1, events.progression)
    }

    @Test
    fun ascend_relevelsAcrossStageCap() = runTest {
        // P2-12 延伸：银行经验足够跨过旧上限（20 级 → 21 级需要累计 21000 经验）
        val service = makeService()
        service.saveData.ownedCharacters = listOf(
            CharacterSaveState(
                characterId = "char_a",
                level = 20,
                totalExp = EconomyFormulas.cumulativeExp(20) + 2000, // = 21000，恰好够 21 级
                unspentPoints = 19,
            ),
        )
        service.saveData.items =
            listOf(ItemSaveState(itemId = GameService.StarFragmentItemId, count = 100))

        assertEquals(WriteOutcome.Success, service.ascend("char_a"))

        val save = service.getSave("char_a")!!
        assertEquals(2, save.stage)
        assertEquals(21, save.level) // 重推导升到 21
        assertEquals(20, save.unspentPoints) // 补发 1 点
    }

    @Test
    fun ascend_insufficientFragments_rejected() = runTest {
        val service = makeService()
        val events = EventCounter()
        service.pull("pool_test", tenPull = false) // 无重复，碎片 0

        assertEquals(WriteOutcome.Rejected, service.ascend("char_a"))
        assertEquals(1, service.getSave("char_a")!!.stage)
        assertEquals(0, events.progression)
    }

    @Test
    fun starUp_success() = runTest {
        val service = makeService()
        val events = EventCounter()
        // 直接播种拥有状态 + 100 碎片（不依赖抽卡产出，断言确定）
        service.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = "char_a"))
        service.saveData.items =
            listOf(ItemSaveState(itemId = GameService.StarFragmentItemId, count = 100))

        assertEquals(WriteOutcome.Success, service.starUp("char_a"))

        val save = service.getSave("char_a")!!
        assertEquals(2, save.stars)
        // starUpFragments(1)=20
        assertEquals(80, service.getStarFragments())
        assertEquals(1, events.currency)
        assertEquals(1, events.progression)
    }

    @Test
    fun starUp_insufficientFragments_rejected() = runTest {
        val service = makeService()
        val events = EventCounter()
        service.pull("pool_test", tenPull = false)

        assertEquals(WriteOutcome.Rejected, service.starUp("char_a"))
        assertEquals(1, service.getSave("char_a")!!.stars)
        assertEquals(0, events.progression)
    }

    // ── 天赋 ──

    @Test
    fun allocateTalent_requiresPrereqAndPoints() = runTest {
        val service = makeService()
        val events = EventCounter()
        service.saveData.ownedCharacters =
            listOf(CharacterSaveState(characterId = "char_a", unspentPoints = 3))

        // 前置 t1 未点亮 → 拒绝
        assertEquals(WriteOutcome.Rejected, service.allocateTalent("char_a", "t2"))
        // 不存在的节点 → 拒绝
        assertEquals(WriteOutcome.Rejected, service.allocateTalent("char_a", "no_such"))

        assertEquals(WriteOutcome.Success, service.allocateTalent("char_a", "t1"))
        assertEquals(WriteOutcome.Success, service.allocateTalent("char_a", "t2"))
        // 已点过 → 拒绝
        assertEquals(WriteOutcome.Rejected, service.allocateTalent("char_a", "t1"))

        val save = service.getSave("char_a")!!
        assertEquals(1, save.unspentPoints)
        assertEquals(listOf("t1", "t2"), save.talentPoints)
        assertEquals(2, events.progression)
    }

    @Test
    fun allocateTalent_noPoints_rejected() = runTest {
        val service = makeService()
        service.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = "char_a"))

        assertEquals(WriteOutcome.Rejected, service.allocateTalent("char_a", "t1"))
    }

    // ── 战绩 ──

    @Test
    fun getBattleRecords_emptyWhenNone() = runTest {
        val service = makeService()

        assertTrue(service.getBattleRecords().isEmpty())
    }

    @Test
    fun recordBattle_capsAt50() = runTest {
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
    fun recordBattle_saveFailure_rollsBack() = runTest {
        val service = makeService(FakeProvider(failSave = true))
        val events = EventCounter()

        service.recordBattle(BattleRecord(enemyName = "enemy0"))

        assertTrue(service.getBattleRecords().isEmpty())
        assertEquals(0, events.currency)
        assertEquals(0, events.progression)
    }

    @Test
    fun recordBattle_atCap_saveFailure_restoresFull50() = runTest {
        // 锁死回归：曾用 dropLast(1) 回滚 ——「追加→超上限丢最旧→落盘失败」时会缩到 49 条，
        // 与存档（50 条）不一致，下次成功保存永久丢一条战绩。
        val provider = FakeProvider()
        val service = makeService(provider)
        repeat(50) { i ->
            service.recordBattle(BattleRecord(enemyName = "enemy$i", victory = i % 2 == 0))
        }

        provider.failSave = true
        service.recordBattle(BattleRecord(enemyName = "enemy50"))

        val records = service.getBattleRecords()
        assertEquals(50, records.size)
        // 最旧记录仍在（整体回滚到追加前，而非 dropLast(1)）
        assertEquals("enemy0", records.first().enemyName)
        assertEquals("enemy49", records.last().enemyName)
    }

    // ── 商店 ──

    @Test
    fun buyFragmentPack_success_deductsAndAwards() = runTest {
        val service = makeService()
        val events = EventCounter()

        assertEquals(WriteOutcome.Success, service.buyFragmentPack(1))
        assertEquals(999999 - 1000, service.saveData.softCurrency)
        assertEquals(10, service.getStarFragments())

        // 大包在既有条目上累加
        assertEquals(WriteOutcome.Success, service.buyFragmentPack(2))
        assertEquals(999999 - 1000 - 5500, service.saveData.softCurrency)
        assertEquals(70, service.getStarFragments())
        assertEquals(2, events.currency)
        assertEquals(0, events.progression)
    }

    @Test
    fun buyFragmentPack_insufficientFunds_rejectedNoEvent() = runTest {
        val service = makeService()
        service.spendSoft(999999)
        val events = EventCounter()

        assertEquals(WriteOutcome.Rejected, service.buyFragmentPack(1))
        assertEquals(0, service.saveData.softCurrency)
        assertEquals(0, service.getStarFragments())
        assertEquals(0, events.currency)
    }

    @Test
    fun buyFragmentPack_invalidPack_rejected() = runTest {
        val service = makeService()

        assertEquals(WriteOutcome.Rejected, service.buyFragmentPack(0))
        assertEquals(WriteOutcome.Rejected, service.buyFragmentPack(3))
        assertEquals(999999, service.saveData.softCurrency)
        assertEquals(0, service.getStarFragments())
    }

    @Test
    fun buyFragmentPack_saveFailure_rollsBackNewItem() = runTest {
        val provider = FakeProvider()
        val service = makeService(provider)
        provider.failSave = true

        assertEquals(WriteOutcome.SaveFailed, service.buyFragmentPack(1))
        assertEquals(999999, service.saveData.softCurrency)
        assertEquals(0, service.getStarFragments())
        // 新增条目整体移除，不残留幽灵道具
        assertEquals(0, service.saveData.items.size)
    }

    @Test
    fun buyFragmentPack_saveFailure_rollsBackExistingItem() = runTest {
        val provider = FakeProvider()
        val service = makeService(provider)
        assertEquals(WriteOutcome.Success, service.buyFragmentPack(1)) // 先成功一次，items 已有条目

        provider.failSave = true
        assertEquals(WriteOutcome.SaveFailed, service.buyFragmentPack(1))
        assertEquals(10, service.getStarFragments()) // 回滚到购买前
        assertEquals(999999 - 1000, service.saveData.softCurrency)
    }

    @Test
    fun buyDiamondExchange_success_and_insufficient() = runTest {
        val service = makeService()
        val events = EventCounter()

        // 初始钻石为 0：不足以兑换
        assertEquals(WriteOutcome.Rejected, service.buyDiamondExchange())
        assertEquals(0, events.currency)

        assertEquals(WriteOutcome.Success, service.addHard(100))
        assertEquals(WriteOutcome.Success, service.buyDiamondExchange())
        assertEquals(0, service.saveData.hardCurrency)
        assertEquals(999999 + 20000, service.saveData.softCurrency)
        // addHard 与兑换各广播一次
        assertEquals(2, events.currency)
    }

    @Test
    fun buyDiamondExchange_saveFailure_rollsBack() = runTest {
        val provider = FakeProvider()
        val service = makeService(provider)
        assertEquals(WriteOutcome.Success, service.addHard(100))

        provider.failSave = true
        assertEquals(WriteOutcome.SaveFailed, service.buyDiamondExchange())
        assertEquals(100, service.saveData.hardCurrency)
        assertEquals(999999, service.saveData.softCurrency)
    }

    // ── 设置与重置 ──

    @Test
    fun setSoundEnabled_persistsAndToggles() = runTest {
        val service = makeService()
        assertTrue(service.saveData.soundEnabled) // 默认开

        assertEquals(WriteOutcome.Success, service.setSoundEnabled(false))
        assertFalse(service.saveData.soundEnabled)
        assertEquals(WriteOutcome.Success, service.setSoundEnabled(true))
        assertTrue(service.saveData.soundEnabled)
    }

    @Test
    fun setSoundEnabled_saveFailure_rollsBack() = runTest {
        val provider = FakeProvider()
        val service = makeService(provider)

        provider.failSave = true
        assertEquals(WriteOutcome.SaveFailed, service.setSoundEnabled(false))
        assertTrue(service.saveData.soundEnabled) // 回滚到旧值，不广播
    }

    @Test
    fun setVibrationAndPush_persistIndependently() = runTest {
        val service = makeService()
        assertEquals(WriteOutcome.Success, service.setVibrationEnabled(false))
        assertEquals(WriteOutcome.Success, service.setPushEnabled(false))
        assertFalse(service.saveData.vibrationEnabled)
        assertFalse(service.saveData.pushEnabled)
        assertTrue(service.saveData.soundEnabled) // 互不影响
    }

    @Test
    fun resetSave_clearsProgressAndRestoresDefaults() = runTest {
        val service = makeService()
        service.pull("pool_test", tenPull = true)
        assertEquals(WriteOutcome.Success, service.addSoft(1000))
        service.setSoundEnabled(false)
        assertFalse(service.saveData.ownedCharacters.isEmpty())

        assertTrue(service.resetSave())

        assertEquals(999999, service.saveData.softCurrency)
        assertEquals(0, service.saveData.hardCurrency)
        assertTrue(service.saveData.ownedCharacters.isEmpty())
        assertTrue(service.saveData.items.isEmpty())
        assertTrue(service.saveData.soundEnabled) // 设置随档重置
    }

    @Test
    fun resetSave_deleteFailure_keepsMemoryAndReturnsFalse() = runTest {
        val provider = FakeProvider()
        val service = makeService(provider)
        assertEquals(WriteOutcome.Success, service.addSoft(1000))

        provider.failDelete = true
        assertFalse(service.resetSave())
        assertEquals(999999 + 1000, service.saveData.softCurrency) // 内存未动
    }

    @Test
    fun resetSave_repersistsOnNextSave() = runTest {
        val provider = FakeProvider()
        val service = makeService(provider)
        service.pull("pool_test", tenPull = true)

        assertTrue(service.resetSave())
        assertTrue(service.saveData.ownedCharacters.isEmpty())

        // 重置后新档可正常落盘（FakeProvider.stored 已被 delete 清空，save 会重建）
        assertEquals(WriteOutcome.Success, service.addSoft(500))
        assertEquals(999999 + 500, service.saveData.softCurrency)
    }

    @Test
    fun resetSave_clearsBackupChain() = runTest {
        // P1-2 契约锁：重置存档必须连同备份链一起清掉（AndroidSaveProvider.delete 清 main/bak/tmp），
        // 否则旧档残留在 .bak（主档被删后 save() 不再滚 bak），日后主档损坏时
        // loadBackup() 会把玩家已明确删除的进度原样复活。
        val provider = FakeProvider()
        val service = makeService(provider)
        service.pull("pool_test", tenPull = true)
        service.save() // 确保旧档已滚入 backup
        assertTrue(provider.backup != null)

        assertTrue(service.resetSave())

        assertEquals(null, provider.loadBackup()) // 备份链随重置清空
        assertTrue(service.saveData.ownedCharacters.isEmpty())
    }

    // ── 路径 B：快照携带角色级数据（UI 读快照替代 saveData 直读）──

    @Test
    fun pull_updatesSnapshot_pityAndOwnedSaves() = runTest {
        // 契约：每次成功写操作后快照刷新——pityByPool 与存档口径一致，ownedSaves 携带新角色
        val service = makeService()

        val outcome = service.pull("pool_test", tenPull = false)
        assertTrue(outcome is PullOutcome.Success)

        val snap = service.snapshot.value
        // 保底计数：单 SSR 池自然出货重置计数器 → 快照与存档实时值一致（0）
        assertEquals(service.saveData.getGachaCounter("pool_test"), snap.pityByPool["pool_test"])
        // ownedSaves 携带新拥有角色，且为独立拷贝（非存档同一引用）
        assertEquals(1, snap.ownedSaves.size)
        assertEquals(1, snap.ownedSaves["char_a"]?.level)
        assertEquals(1, snap.ownedSaves["char_a"]?.stage)
        assertTrue(snap.ownedSaves["char_a"] !== service.getSave("char_a"))
    }

    @Test
    fun levelUp_updatesSnapshot_ownedSaves() = runTest {
        // 契约：养成写操作后快照角色存档同步更新（UI 无需重读存档）
        val service = makeService()
        service.pull("pool_test", tenPull = true)

        assertEquals(WriteOutcome.Success, service.levelUp("char_a"))

        val save = service.getSave("char_a")!!
        val snap = service.snapshot.value
        assertEquals(2, snap.ownedSaves["char_a"]?.level) // 与存档一致
        assertEquals(save.level, snap.ownedSaves["char_a"]?.level)
        assertEquals(save.unspentPoints, snap.ownedSaves["char_a"]?.unspentPoints)
    }

    @Test
    fun resetSave_clearsSnapshot_ownedSavesAndPity() = runTest {
        // 契约：resetSave 整体替换存档后快照角色级数据同步清空（拷贝语义防陈旧引用）
        val service = makeService()
        service.pull("pool_test", tenPull = true)
        assertEquals(1, service.snapshot.value.ownedSaves.size)

        assertTrue(service.resetSave())

        assertTrue(service.snapshot.value.ownedSaves.isEmpty())
        assertEquals(0, service.snapshot.value.pityByPool["pool_test"] ?: -1)
    }
}
