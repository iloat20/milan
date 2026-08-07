package com.milan.game.domain.progression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 天赋引擎测试（翻译 C# TalentTests）。
 */
class TalentEngineTest {

    private val engine = TalentEngine()

    @Test
    fun canAllocate_alreadyAllocated_returnsFalse() {
        val alloc = listOf("n1")
        assertFalse(engine.canAllocate("n1", alloc, emptyMap()))
    }

    @Test
    fun canAllocate_rootNodeWithoutPrereq_returnsTrue() {
        assertTrue(engine.canAllocate("root", emptyList(), emptyMap()))
    }

    @Test
    fun canAllocate_allPrereqsMet_returnsTrue() {
        val alloc = listOf("a", "b")
        val pre = mapOf("c" to listOf("a", "b"))
        assertTrue(engine.canAllocate("c", alloc, pre))
    }

    @Test
    fun canAllocate_missingPrereq_returnsFalse() {
        val alloc = listOf("a")
        val pre = mapOf("c" to listOf("a", "b"))
        assertFalse(engine.canAllocate("c", alloc, pre))
    }

    // C# 侧 null 前置数组（Kotlin Map 值声明可空以保留该语义）
    @Test
    fun canAllocate_nullPrereqArray_returnsTrue() {
        val pre: Map<String, List<String>?> = mapOf("c" to null)
        assertTrue(engine.canAllocate("c", emptyList(), pre))
    }

    @Test
    fun totalPoints_sumsCosts() {
        val costs = mapOf("a" to 1, "b" to 2, "c" to 3)
        assertEquals(6, engine.totalPoints(costs))
    }
}
