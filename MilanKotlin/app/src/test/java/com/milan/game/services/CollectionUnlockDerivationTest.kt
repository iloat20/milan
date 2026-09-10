package com.milan.game.services

import com.milan.game.data.CharacterSaveState
import com.milan.game.data.SaveProvider
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图鉴解锁态 = ownedCharacters 派生（2026-09-08 方案 B 概念收敛）。
 *
 * 回归保护：此前 `CollectionService.recordCharacterObtained`（唯一写入方）早已无人调用，
 * 读路径恒空 → 收集率/里程碑永不推进且无任何测试覆盖。本测试锁定新契约：
 * 拥有即解锁（重复抽卡不新增存档，ownedCharacters 按 charId 唯一），里程碑按拥有数门槛发放。
 */
class CollectionUnlockDerivationTest {

    private val dataJson: String =
        File("src/main/assets/data.json").takeIf { it.exists() }
            ?.readText(Charsets.UTF_8)
            ?: error("测试需真实内容文件：app/src/main/assets/data.json")

    private class MemoryProvider : SaveProvider {
        var stored: String? = null
        override fun save(json: String): Boolean {
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

    private fun newService(ownedIds: List<String>) =
        GameService(MemoryProvider(), dataJson, {}, Random(42)).also { svc ->
            if (ownedIds.isNotEmpty()) {
                svc.saveData.ownedCharacters = ownedIds.map { CharacterSaveState(characterId = it) }
            }
        }

    @Test
    fun `解锁数与进度由 ownedCharacters 派生`() {
        val empty = newService(emptyList())
        assertEquals("无拥有 → 解锁数 0", 0, empty.getCollectionUnlockedCount())
        assertEquals("无拥有 → 收集率 0", 0f, empty.getCollectionProgress())

        val all = newService(emptyList())
        val ownedIds = all.characters.take(3).map { it.characterId }
        all.saveData.ownedCharacters = ownedIds.map { CharacterSaveState(characterId = it) }

        assertEquals("拥有 3 → 解锁数 3", 3, all.getCollectionUnlockedCount())
        val expected = 3f / all.characters.size
        assertEquals("收集率 = 拥有数/内容总数", expected, all.getCollectionProgress(), 0.0001f)

        ownedIds.forEach { id ->
            val entry = all.getCollectionEntries().firstOrNull { it.characterId == id }
            assertTrue("拥有角色 ${id} 应解锁", entry?.unlocked == true)
        }
        val notOwned = all.characters.first { it.characterId !in ownedIds }
        val ghost = all.getCollectionEntries().firstOrNull { it.characterId == notOwned.characterId }
        assertFalse("未拥有角色不应解锁", ghost?.unlocked ?: true)
    }

    @Test
    fun `里程碑按拥有数门槛发放且不可重复领取`() = runTest {
        // 门槛 5：拥有 4 → 拒绝
        val four = newService(emptyList()).characters.take(4).map { it.characterId }
        val below = newService(four)
        assertEquals("拥有 4 领取门槛 5 应拒绝", WriteOutcome.Rejected, below.claimCollectionMilestone(5))

        // 拥有 5 → 成功，星尘 +2000
        val five = newService(emptyList()).characters.take(5).map { it.characterId }
        val at = newService(five)
        val before = at.saveData.softCurrency
        assertEquals("拥有 5 领取门槛 5 应成功", WriteOutcome.Success, at.claimCollectionMilestone(5))
        assertEquals("里程碑奖励应到账 2000 星尘", before + 2000, at.saveData.softCurrency)

        // 同门槛不可重复领取
        assertEquals("同里程碑重复领取应拒绝", WriteOutcome.Rejected, at.claimCollectionMilestone(5))

        val ms = at.getCollectionMilestones().first { it.required == 5 }
        assertTrue("领取后里程碑应标记 claimed", ms.claimed)
    }

    @Test
    fun `里程碑领取态跨重载持久`() = runTest {
        // 回归保护：SaveData.sanitize 的 R5-I5 ensure 清单曾漏 collectionData →
        // 领取态写进 `?: CollectionSaveData()` 兜底瞬态对象、从不回写存档，重启即丢 →
        // 同里程碑可无限重复领取刷货币。此用例钉死「重载后仍为已领取」。
        val provider = MemoryProvider()
        val ids = GameService(provider, dataJson, {}, Random(42)).characters.take(5).map { it.characterId }

        val first = GameService(provider, dataJson, {}, Random(42))
        first.saveData.ownedCharacters = ids.map { CharacterSaveState(characterId = it) }
        assertEquals("首次领取应成功", WriteOutcome.Success, first.claimCollectionMilestone(5))

        // 模拟重启：同一 provider 重载存档
        val second = GameService(provider, dataJson, {}, Random(42))
        assertEquals("重载后拥有数应保留", 5, second.getCollectionUnlockedCount())
        assertEquals("重载后同里程碑应仍处于已领取", WriteOutcome.Rejected, second.claimCollectionMilestone(5))
    }
}
