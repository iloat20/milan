package com.milan.game.services

import com.milan.game.data.CharacterSaveState
import com.milan.game.data.ItemSaveState
import com.milan.game.data.SaveData
import com.milan.game.data.SaveProvider
import com.milan.game.domain.progression.EconomyFormulas
import com.milan.game.infrastructure.eventbus.EventBus
import kotlin.random.Random
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 编队 + 无尽之塔服务层测试（2026-08 优化新增）：
 * setFormation 校验/事务回滚、runTowerFloor 胜负结算/奖励落盘/失败整体回滚。
 * 内容用双角色 JSON（BaseStats 显式给定，胜负由练度差锁死，不依赖随机种子）。
 */
class FormationTowerServiceTest {

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
            {
              "TreeId": "tree_dummy",
              "Nodes": [ { "NodeId": "n1", "Cost": 1 } ]
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

    private fun makeService(failSave: Boolean = false): GameService {
        val provider = FakeProvider(failSave)
        return GameService(provider, testContent, provider.traces::add, Random(42))
    }

    /** 直接在内存档中登记已拥有角色（绕过抽卡；编队/爬塔只依赖 owned 集合与内存态）。 */
    private fun GameService.own(vararg ids: Pair<String, Int>) {
        saveData.ownedCharacters = ids.map { (id, lv) ->
            CharacterSaveState(characterId = id, level = lv)
        }
    }

    private fun withService(block: suspend (GameService) -> Unit) = runTest {
        block(makeService())
    }

    // ── 编队 ──

    @Test
    fun setFormation_success_persistsAndSnapshots() = withService { svc ->
        svc.own("char_a" to 10, "char_b" to 10)
        val outcome = svc.setFormation(listOf("char_a", "char_b"))
        assertTrue(outcome is WriteOutcome.Success)
        assertEquals(listOf("char_a", "char_b"), svc.getFormation())
        assertEquals(listOf("char_a", "char_b"), svc.snapshot.value.formation)
    }

    @Test
    fun setFormation_rejectsUnownedAndOversize_dedupesInput() = withService { svc ->
        svc.own("char_a" to 1)
        assertTrue(svc.setFormation(listOf("ghost_id")) is WriteOutcome.Rejected)

        val six = (1..6).map { "char_a" } + "ghost" // 去重后 1 个有效 + 未拥有
        assertTrue(svc.setFormation(six) is WriteOutcome.Rejected)

        assertTrue(svc.setFormation(listOf("char_a", "char_a")) is WriteOutcome.Success)
        assertEquals(listOf("char_a"), svc.getFormation()) // 去重保序
    }

    /**
     * U2（2026-08-28 审查回归）：并发切换入队必须全部生效。
     * 修复前读-改-写在 UI 侧跨锁执行，两次点击基于同一份过期快照计算，
     * 后提交者整体覆盖前者 → 前一次点击被静默丢弃。
     */
    @Test
    fun toggleFormation_concurrentToggles_allApplied() = runTest {
        val svc = makeService()
        svc.own("char_a" to 10, "char_b" to 10, "char_c" to 10)

        listOf("char_a", "char_b", "char_c")
            .map { id -> launch { svc.toggleFormation(id) } }
            .forEach { it.join() }

        assertEquals(
            "并发切换应全部生效（实际=${svc.getFormation()}）",
            setOf("char_a", "char_b", "char_c"),
            svc.getFormation().toSet(),
        )
    }

    @Test
    fun toggleFormation_togglesOffAndRejectsUnownedOrFull() = runTest {
        val svc = makeService()
        // own() 是整体替换，故一次性登记全部角色
        svc.own(
            "char_a" to 10, "char_b" to 10,
            "m1" to 10, "m2" to 10, "m3" to 10, "m4" to 10, "m5" to 10,
        )
        assertEquals(WriteOutcome.Rejected, svc.toggleFormation("ghost_id")) // 未拥有

        assertEquals(WriteOutcome.Success, svc.toggleFormation("char_a"))
        assertEquals(listOf("char_a"), svc.getFormation())
        assertEquals(WriteOutcome.Success, svc.toggleFormation("char_a")) // 再次切换 = 出队
        assertEquals(emptyList<String>(), svc.getFormation())

        // 编队满（上限 5）后拒绝新成员
        repeat(5) { assertEquals(WriteOutcome.Success, svc.toggleFormation("m${it + 1}")) }
        assertEquals(SaveData.MAX_FORMATION_SIZE, svc.getFormation().size)
        assertEquals(WriteOutcome.Rejected, svc.toggleFormation("char_b")) // 已满
    }

    @Test
    fun setFormation_saveFailed_rollsBack() = runTest {
        val svc = makeService(failSave = true)
        svc.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = "char_a"))
        val before = svc.getFormation()

        val outcome = svc.setFormation(listOf("char_a"))
        assertTrue(outcome is WriteOutcome.SaveFailed)
        assertEquals(before, svc.getFormation()) // 回滚：内存与存档一致
        assertEquals(before, svc.snapshot.value.formation)
    }

    // ── 无尽之塔 ──

    @Test
    fun tower_rejected_whenFormationEmpty() = withService { svc ->
        svc.own("char_a" to 80)
        assertTrue(svc.runTowerFloor(1) is TowerOutcome.Rejected)
        svc.setFormation(listOf("char_a"))
        assertTrue(svc.runTowerFloor(0) is TowerOutcome.Rejected) // 非法层号
    }

    @Test
    fun tower_highLevelTeam_winsFloorOne_rewardsPersist() = withService { svc ->
        svc.own("char_a" to 80, "char_b" to 80)
        svc.setFormation(listOf("char_a", "char_b")) // 双火？b 是 Water——混编无双星，纯数值碾压
        // 二期门票：入场 -1 + 胜利返 +1 = 净 0
        svc.saveData.items = listOf(ItemSaveState(itemId = GameService.BattleTicketItemId, count = 1))

        val softBefore = svc.saveData.softCurrency
        val recordsBefore = svc.getBattleRecords().size

        val outcome = svc.runTowerFloor(1)
        assertTrue(outcome is TowerOutcome.Completed)
        val done = outcome as TowerOutcome.Completed
        assertTrue(done.victory)
        assertEquals(1, done.bestFloorAfter)
        assertEquals(1, svc.saveData.towerBestFloor)
        assertEquals(EconomyFormulas.towerRewardSoft(1), done.rewardSoft)
        assertEquals(softBefore + done.rewardSoft, svc.saveData.softCurrency)
        assertEquals(recordsBefore + 1, svc.getBattleRecords().size)
        assertEquals(1, svc.battleTickets())
    }

    @Test
    fun tower_lowLevelTeam_losesHighFloor_noReward() = withService { svc ->
        svc.own("char_b" to 1)
        svc.setFormation(listOf("char_b"))
        svc.saveData.items = listOf(ItemSaveState(itemId = GameService.BattleTicketItemId, count = 2))

        val softBefore = svc.saveData.softCurrency
        val recordsBefore = svc.getBattleRecords().size

        val outcome = svc.runTowerFloor(50) // 缩放 8.35 倍的 5 人敌队：单人 1 级必败
        assertTrue(outcome is TowerOutcome.Completed)
        val done = outcome as TowerOutcome.Completed
        assertTrue(!done.victory)
        assertEquals(0, done.rewardSoft)
        assertEquals(0, svc.saveData.towerBestFloor)
        assertEquals(softBefore, svc.saveData.softCurrency)
        assertEquals(recordsBefore + 1, svc.getBattleRecords().size) // 失败也留战绩
        assertEquals(1, svc.battleTickets()) // 2 - 1 门票，失败无返还
    }

    @Test
    fun tower_softCurrencyOverflow_rejected() = withService { svc ->
        // P3-7：softCurrency 接近 Int 上限时 +reward 会翻负 → 必须前置拦截
        svc.own("char_a" to 80)
        svc.setFormation(listOf("char_a"))
        svc.saveData.items = listOf(ItemSaveState(itemId = GameService.BattleTicketItemId, count = 10))
        svc.saveData.softCurrency = Int.MAX_VALUE - 100 // towerRewardSoft(1)=1000，加后溢出

        val outcome = svc.runTowerFloor(1)
        assertTrue("溢出时必须拒绝", outcome is TowerOutcome.Rejected)
        assertEquals("货币不应变动", Int.MAX_VALUE - 100, svc.saveData.softCurrency)
    }

    @Test
    fun tower_hardCurrencyOverflow_rejected() = withService { svc ->
        // P3-7：hardCurrency 接近 Int 上限时 +rewardHard 会翻负
        svc.own("char_a" to 80, "char_b" to 80)
        svc.setFormation(listOf("char_a", "char_b"))
        svc.saveData.items = listOf(ItemSaveState(itemId = GameService.BattleTicketItemId, count = 10))
        svc.saveData.softCurrency = Int.MAX_VALUE // 确保 soft 不先溢出
        svc.saveData.hardCurrency = Int.MAX_VALUE - 10 // towerRewardHard(5)=10，加后溢出

        val outcome = svc.runTowerFloor(5)
        assertTrue("hard 溢出时必须拒绝", outcome is TowerOutcome.Rejected)
        assertEquals("硬币不应变动", Int.MAX_VALUE - 10, svc.saveData.hardCurrency)
    }

    @Test
    fun tower_draw_noTicketConsumption() = runTest {
        // P3-7：极低攻击 + 高防坦克 vs 低攻敌人 → 50 回合平局 → 门票不消耗、货币不变
        val drawContent = """
            {
              "Characters": [
                {
                  "CharacterId": "tank_a",
                  "DisplayName": "铁壁",
                  "BaseRarity": 3,
                  "Element": "Earth",
                  "BaseStats": [10, 500, 50000, 5]
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
                    { "CharacterId": "tank_a", "RarityIndex": 3, "Weight": 1000 }
                  ]
                }
              ],
              "TalentTrees": [
                { "TreeId": "tree_dummy", "Nodes": [ { "NodeId": "n1", "Cost": 1 } ] }
              ]
            }
        """.trimIndent()
        val provider = FakeProvider()
        val svc = GameService(provider, drawContent, provider.traces::add, Random(42))
        svc.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = "tank_a", level = 1))
        svc.saveData.formation = listOf("tank_a")
        svc.saveData.items = listOf(ItemSaveState(itemId = GameService.BattleTicketItemId, count = 3))
        val softBefore = svc.saveData.softCurrency
        val recordsBefore = svc.getBattleRecords().size

        val outcome = svc.runTowerFloor(1)
        assertTrue("应返回平局", outcome is TowerOutcome.Draw)
        assertEquals("门票不变", 3, svc.battleTickets())
        assertEquals("星尘不变", softBefore, svc.saveData.softCurrency)
        // F5（2026-08-28 审查回归）：平局分支此前直接 return，战报从未落盘，
        // 与 runTowerFloor 注释「仅记录战报」及 TowerOutcome.Draw 的 KDoc 语义矛盾。
        assertEquals("平局也应记录战报", recordsBefore + 1, svc.getBattleRecords().size)
    }

    @Test
    fun tower_saveFailed_fullRollback() = runTest {
        val svc = makeService(failSave = true)
        svc.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = "char_a", level = 80))
        svc.saveData.formation = listOf("char_a")
        svc.saveData.items = listOf(ItemSaveState(itemId = GameService.BattleTicketItemId, count = 3))
        val softBefore = svc.saveData.softCurrency
        val recordsBefore = svc.getBattleRecords().size

        val outcome = svc.runTowerFloor(1)
        assertTrue(outcome is TowerOutcome.SaveFailed)
        assertEquals(softBefore, svc.saveData.softCurrency)
        assertEquals(0, svc.saveData.towerBestFloor)
        assertEquals(recordsBefore, svc.getBattleRecords().size)
        assertEquals(3, svc.battleTickets()) // 战票同样回滚
    }

    // ── 回归：复刷已通层不得无限产星尘（P3 审查 F1 经济永动机）──
    // 漏洞成因：胜利返票数 == 入场票数（净耗 0）+ 敌队 seed 由 floor 派生（已通层必胜）
    //          + 星尘奖励未按 newBest 门控 → 可无限复刷同一层刷星尘。

    @Test
    fun tower_replayClearedFloor_noSoftRewardAndConsumesTicket() = withService { svc ->
        svc.own("char_a" to 80)
        assertEquals(WriteOutcome.Success, svc.setFormation(listOf("char_a")))
        svc.saveData.items = listOf(ItemSaveState(itemId = GameService.BattleTicketItemId, count = 20))

        val first = svc.runTowerFloor(1)
        assertTrue("首次通关应完成", first is TowerOutcome.Completed)
        assertTrue("首层应获胜", (first as TowerOutcome.Completed).victory)
        val softAfterFirst = svc.saveData.softCurrency
        val ticketsAfterFirst = svc.battleTickets()

        // 复刷已通层：敌队由 floor 派生可复现，玩家已通过 → 必胜
        val replay = svc.runTowerFloor(1) as TowerOutcome.Completed
        assertTrue("复刷必胜（漏洞前提）", replay.victory)
        assertEquals("复刷已通层不得再发星尘", softAfterFirst, svc.saveData.softCurrency)
        assertEquals("复刷星尘奖励必须为 0", 0, replay.rewardSoft)
        assertEquals("复刷必须净耗 1 张战票", ticketsAfterFirst - 1, svc.battleTickets())
    }

    /**
     * F4（2026-08-28 审查回归）：爬塔胜利必须发放经验，经验条才能真正推进。
     * 修复前 `addExp` 全工程零生产调用点，唯一加经验路径 levelUp 的 expGain 恰让
     * totalExp 落到该等级累计下限 → expProgress 的 cur 恒为 0（实测 0/200、0/300…）。
     */
    @Test
    fun tower_victory_grantsExp_drivesLevelAndBar() = withService { svc ->
        svc.own("char_a" to 1)
        assertEquals(WriteOutcome.Success, svc.setFormation(listOf("char_a")))
        svc.saveData.items = listOf(ItemSaveState(itemId = GameService.BattleTicketItemId, count = 10))

        assertEquals("起手应无经验", 0, svc.getSave("char_a")!!.totalExp)

        val first = svc.runTowerFloor(1) as TowerOutcome.Completed
        assertTrue("首层应获胜", first.victory)
        assertEquals("应返回本次经验", EconomyFormulas.towerRewardExp(1), first.rewardExp)
        assertEquals(
            "经验应累计进 totalExp",
            EconomyFormulas.towerRewardExp(1),
            svc.getSave("char_a")!!.totalExp,
        )
        assertEquals("经验应驱动自动升级", 2, svc.getSave("char_a")!!.level)

        // 第 2 层经验量非整级 → 经验条应显示可见的非零进度
        svc.runTowerFloor(2)
        val (cur, need) = svc.expProgress("char_a")
        assertTrue("经验条必须显示非零进度（实测 $cur/$need）", cur > 0)
    }

    @Test
    fun tower_repeatedReplay_neverGrowsSoft() = withService { svc ->
        svc.own("char_a" to 80)
        svc.setFormation(listOf("char_a"))
        svc.saveData.items = listOf(ItemSaveState(itemId = GameService.BattleTicketItemId, count = 50))

        svc.runTowerFloor(1) // 首通
        val softAfterFirst = svc.saveData.softCurrency

        // 连续复刷 5 次：星尘不得增长（旧实现每次 +1000）
        repeat(5) { svc.runTowerFloor(1) }
        assertEquals("连续复刷不得产出任何星尘", softAfterFirst, svc.saveData.softCurrency)
        assertEquals("连续复刷 5 次应净耗 5 张战票", 45, svc.battleTickets())
    }
}
