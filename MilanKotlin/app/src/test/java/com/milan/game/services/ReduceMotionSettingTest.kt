package com.milan.game.services

import com.milan.game.data.SaveData
import com.milan.game.data.SaveProvider
import com.milan.game.infrastructure.eventbus.EventBus
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 动效减弱开关（设计语言 P3 无障碍）。 */
class ReduceMotionSettingTest {

    private class FakeProvider : SaveProvider {
        var stored: String? = null
        val traces = mutableListOf<String>()
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

    private val testContent = """
        {
          "Characters": [
            { "CharacterId": "char_a", "DisplayName": "甲", "BaseRarity": 3 }
          ],
          "Pools": [
            {
              "PoolId": "pool_test",
              "RarityWeights": [0, 0, 1000, 0],
              "HardPity": 90,
              "SingleCost": 160,
              "TenCost": 1600,
              "Entries": [ { "CharacterId": "char_a", "RarityIndex": 3, "Weight": 1000 } ]
            }
          ],
          "TalentTrees": [
            { "TreeId": "t", "Nodes": [ { "NodeId": "n", "Cost": 1 } ] }
          ]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        EventBus.clear()
    }

    @Test
    fun defaultOff_toggleOn_persistsAndSlice() = runTest {
        val provider = FakeProvider()
        val svc = GameService(provider, testContent, provider.traces::add, Random(1))
        assertFalse(svc.saveData.reduceMotionEnabled)
        assertFalse(svc.meta.value.reduceMotionEnabled)

        assertTrue(svc.setReduceMotionEnabled(true) is WriteOutcome.Success)
        assertTrue(svc.saveData.reduceMotionEnabled)
        assertTrue(svc.meta.value.reduceMotionEnabled)

        // 旧档无键：JSON 重载后默认 false
        val bare = SaveData()
        provider.stored = bare.toJson()
        val reloaded = GameService(provider, testContent, {}, Random(2))
        assertFalse(reloaded.saveData.reduceMotionEnabled)
    }

    @Test
    fun fontScaleTier_clampsAndPersists() = runTest {
        val provider = FakeProvider()
        val svc = GameService(provider, testContent, provider.traces::add, Random(3))
        assertEquals(0, svc.meta.value.fontScaleTier)

        assertTrue(svc.setFontScaleTier(2) is WriteOutcome.Success)
        assertEquals(2, svc.saveData.fontScaleTier)
        assertEquals(2, svc.meta.value.fontScaleTier)

        assertTrue(svc.setFontScaleTier(9) is WriteOutcome.Rejected)
        assertEquals(2, svc.saveData.fontScaleTier)
    }
}
