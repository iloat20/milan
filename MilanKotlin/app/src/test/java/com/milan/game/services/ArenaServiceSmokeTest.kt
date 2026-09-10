package com.milan.game.services

import com.milan.game.data.SaveProvider
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ArenaService] 冒烟单测（2026-09-06 S8）。
 *
 * **非业务正确性断言，仅作回归网**：R5 审查发现 Arena/PvE/Social 三大系统零专用测试，
 * 任何架构重构（如 S1 withWriteLock 接线、S2 死功能裁撤）都可能悄然破坏其主路径
 * 而无任何断言保护。本测试只验证「构造 + 主要写操作不崩 + WriteOutcome 三态分发」，
 * 不覆盖积分/匹配/结算的具体数值正确性。
 *
 * PvE/Social 已随 S2 删除，仅 Arena 仍存活。
 */
class ArenaServiceSmokeTest {

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
            { "CharacterId": "char_a", "DisplayName": "测试甲", "BaseRarity": 3, "TalentTreeId": "tree_test" },
            { "CharacterId": "char_b", "DisplayName": "测试乙", "BaseRarity": 3, "TalentTreeId": "tree_test" },
            { "CharacterId": "char_c", "DisplayName": "测试丙", "BaseRarity": 3, "TalentTreeId": "tree_test" }
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

    private fun makeService(provider: FakeProvider = FakeProvider()): GameService =
        GameService(provider, testContent, { }, Random(42)).also { svc ->
            // 预置 3 个角色已拥有，供 setDefenseTeam 验证
            svc.saveData.ownedCharacters = listOf(
                com.milan.game.data.CharacterSaveState(characterId = "char_a"),
                com.milan.game.data.CharacterSaveState(characterId = "char_b"),
                com.milan.game.data.CharacterSaveState(characterId = "char_c"),
            )
        }

    @Test
    fun `getArenaData 不崩且返回非空对象`() {
        val service = makeService()
        val data = service.getArenaData()
        assertNotNull("竞技场数据不得为 null", data)
    }

    @Test
    fun `getOpponents 不崩且返回列表`() {
        val service = makeService()
        val opponents = service.getOpponents()
        assertNotNull("对手列表不得为 null", opponents)
    }

    @Test
    fun `setDefenseTeam 合法阵容返回 Success`() = runTest {
        val service = makeService()
        val outcome = service.setDefenseTeam(listOf("char_a", "char_b", "char_c"))
        assertEquals("合法阵容应成功", WriteOutcome.Success, outcome)
    }

    @Test
    fun `setDefenseTeam 未拥有角色返回 Rejected`() = runTest {
        val service = makeService()
        val outcome = service.setDefenseTeam(listOf("char_not_owned"))
        assertEquals("未拥有角色必须拒绝", WriteOutcome.Rejected, outcome)
    }

    @Test
    fun `setDefenseTeam 落盘失败回滚返回 SaveFailed`() = runTest {
        val service = makeService(FakeProvider(failSave = true))
        val outcome = service.setDefenseTeam(listOf("char_a"))
        assertEquals("落盘失败应返回 SaveFailed", WriteOutcome.SaveFailed, outcome)
    }

    @Test
    fun `getSeasonRewards 不崩且返回列表`() {
        val service = makeService()
        val rewards = service.getSeasonRewards()
        assertNotNull("赛季奖励列表不得为 null", rewards)
    }

    @Test
    fun `challengeOpponent 不崩且返回 ArenaChallengeOutcome 类型`() = runTest {
        val service = makeService()
        val opponents = service.getOpponents()
        if (opponents.isNotEmpty()) {
            val outcome = service.challengeOpponent(opponents.first())
            assertTrue(
                "挑战应返回 ArenaChallengeOutcome 类型（Completed/Rejected/SaveFailed）",
                outcome is com.milan.game.services.ArenaChallengeOutcome.Completed ||
                    outcome is com.milan.game.services.ArenaChallengeOutcome.Rejected ||
                    outcome is com.milan.game.services.ArenaChallengeOutcome.SaveFailed,
            )
        }
    }
}
