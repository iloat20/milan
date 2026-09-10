package com.milan.game.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 新手引导存档模型单测（第 8 节）。 */
class TutorialSaveDataTest {

    @Test
    fun markDone_stepsInOrder_untilComplete() {
        val t = TutorialSaveData()
        assertFalse(t.completed)
        assertEquals(TutorialSteps.INTRO, TutorialSteps.ORDER.firstOrNull { !t.hasDone(it) })

        t.markDone(TutorialSteps.INTRO)
        assertFalse(t.completed)
        assertEquals(TutorialSteps.FIRST_PULL, TutorialSteps.ORDER.firstOrNull { !t.hasDone(it) })

        TutorialSteps.ORDER.forEach { t.markDone(it) }
        assertTrue(t.completed)
        assertFalse(t.markDone(TutorialSteps.INTRO)) // 幂等
    }

    @Test
    fun markSkipped_blocksFurtherProgress() {
        val t = TutorialSaveData()
        assertTrue(t.markSkipped())
        assertFalse(t.markDone(TutorialSteps.INTRO))
        assertFalse(t.completed)
        assertTrue(t.skipped)
    }

    @Test
    fun markDone_outOfOrder_stillCompletesWhenAllDone() {
        val t = TutorialSaveData()
        t.markDone(TutorialSteps.FIRST_BATTLE)
        t.markDone(TutorialSteps.INTRO)
        t.markDone(TutorialSteps.FIRST_LEVEL)
        t.markDone(TutorialSteps.FIRST_PULL)
        t.markDone(TutorialSteps.FORM_TEAM)
        assertTrue(t.completed)
    }

    @Test
    fun serialization_roundTrip_preservesFlags() {
        val t = TutorialSaveData().also {
            it.markDone(TutorialSteps.INTRO)
            it.markDone(TutorialSteps.FIRST_PULL)
        }
        val json = kotlinx.serialization.json.Json.encodeToString(TutorialSaveData.serializer(), t)
        val back = kotlinx.serialization.json.Json.decodeFromString(TutorialSaveData.serializer(), json)
        assertTrue(back.hasDone(TutorialSteps.INTRO))
        assertTrue(back.hasDone(TutorialSteps.FIRST_PULL))
        assertFalse(back.completed)
        assertFalse(back.skipped)
    }

    @Test
    fun deserialization_missingFields_defaults() {
        val back = kotlinx.serialization.json.Json.decodeFromString(TutorialSaveData.serializer(), "{}")
        assertFalse(back.completed)
        assertFalse(back.skipped)
        assertTrue(back.doneSteps().isEmpty())
    }
}
