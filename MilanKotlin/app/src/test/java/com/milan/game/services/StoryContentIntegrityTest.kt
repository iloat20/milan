package com.milan.game.services

import com.milan.game.data.StoryStageType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 剧情内容完整性测试（2026-09-11 三幕九章 + P3 外传/结局）。
 *
 * 内容定义在 [StoryService.STORY_CHAPTERS] 代码内，不走 data.json；
 * 说话者/封面必须对齐 data.json 真实 CharacterId，CHOICE 需带好感。
 * 与 DataJsonContentTest 同源：以资产文件为准（GameContent 为 internal）。
 */
class StoryContentIntegrityTest {

    private val dataJson: String =
        File("src/main/assets/data.json").takeIf { it.exists() }
            ?.readText(Charsets.UTF_8)
            ?: error("测试需真实内容文件：app/src/main/assets/data.json")

    private val knownIds: Set<String> =
        Regex("\"CharacterId\"\\s*:\\s*\"(char_[^\"]+)\"")
            .findAll(dataJson)
            .map { it.groupValues[1] }
            .toSet()

    private val mainChapters = StoryService.STORY_CHAPTERS.filter { it.requiredAffinityCharacterId == null }
    private val sideChapters = StoryService.STORY_CHAPTERS.filter { it.requiredAffinityCharacterId != null }

    private fun allSpeakerIds(): Sequence<String> = sequence {
        StoryService.STORY_CHAPTERS.forEach { ch ->
            ch.stages.forEach { stage ->
                stage.dialogue?.forEach { line ->
                    yield(line.speakerId)
                    line.choices?.forEach { choice ->
                        choice.affinityCharacterId?.let { yield(it) }
                    }
                }
            }
        }
    }

    @Test
    fun `main has nine chapters of five stages and side stories exist`() {
        assertEquals("expect 9 main chapters", 9, mainChapters.size)
        mainChapters.forEach { ch ->
            assertEquals("main chapter ${ch.chapterId} stage count", 5, ch.stages.size)
        }
        assertTrue("expect affinity side stories", sideChapters.isNotEmpty())
        sideChapters.forEach { ch ->
            assertTrue("side ${ch.chapterId} too long", ch.stages.size in 2..3)
            assertTrue(
                "side ${ch.chapterId} affinity gate missing",
                ch.requiredAffinityCharacterId != null && ch.requiredAffinityLevel >= 1,
            )
            assertTrue(
                "side ${ch.chapterId} affinity char unknown",
                knownIds.contains(ch.requiredAffinityCharacterId),
            )
        }
        val ids = StoryService.STORY_CHAPTERS.flatMap { ch -> ch.stages.map { it.stageId } }
        assertEquals("stage ids unique", ids.size, ids.toSet().size)
    }

    @Test
    fun `ch08 three lights write ending branches`() {
        val stage = StoryService.STORY_CHAPTERS
            .flatMap { it.stages }
            .firstOrNull { it.stageId == "ch08_s5" }
        assertNotNull("ch08_s5 missing", stage)
        val endings = stage!!.dialogue.orEmpty()
            .flatMap { it.choices.orEmpty() }
            .mapNotNull { it.endingBranchId }
        assertEquals("expect 3 ending branches", 3, endings.size)
        assertEquals(
            "ending ids",
            setOf("end_dawn", "end_debt", "end_burn"),
            endings.toSet(),
        )
    }

    @Test
    fun `cover character ids exist in content`() {
        StoryService.STORY_CHAPTERS.forEach { ch ->
            val cover = ch.coverCharacterId
            if (cover != null) {
                assertTrue(
                    "cover $cover of ${ch.chapterId} missing from GameContent",
                    knownIds.contains(cover),
                )
            }
        }
    }

    @Test
    fun `all speakers and affinity targets are narrator or known characters`() {
        allSpeakerIds().forEach { id ->
            assertTrue(
                "unknown speaker/affinity id: $id",
                id == "narrator" || knownIds.contains(id),
            )
        }
    }

    @Test
    fun `no legacy invalid taotie ur id`() {
        val banned = "char_ur_taotie"
        assertTrue(
            "$banned must not appear (real id is char_sr_taotie)",
            allSpeakerIds().none { it == banned } &&
                StoryService.STORY_CHAPTERS.none { it.coverCharacterId == banned },
        )
    }

    @Test
    fun `prerequisite chain is consistent within chapter`() {
        StoryService.STORY_CHAPTERS.forEach { ch ->
            ch.stages.forEachIndexed { index, stage ->
                if (index == 0) {
                    // 首关可无前置，或显式指向前一章末关
                    if (stage.prerequisiteStageId != null) {
                        val prereq = StoryService.STORY_CHAPTERS
                            .flatMap { it.stages }
                            .firstOrNull { it.stageId == stage.prerequisiteStageId }
                        assertNotNull(
                            "${stage.stageId} prereq ${stage.prerequisiteStageId} missing",
                            prereq,
                        )
                    }
                } else {
                    assertEquals(
                        "${stage.stageId} should chain to previous",
                        ch.stages[index - 1].stageId,
                        stage.prerequisiteStageId,
                    )
                }
            }
        }
    }

    @Test
    fun `every act has at least two choice stages with affinity`() {
        val choiceStages = StoryService.STORY_CHAPTERS
            .flatMap { it.stages }
            .filter { it.type == StoryStageType.CHOICE }
        assertTrue("need >=2 CHOICE stages in P0 content", choiceStages.size >= 2)
        choiceStages.forEach { stage ->
            val choices = stage.dialogue.orEmpty().flatMap { it.choices.orEmpty() }
            assertTrue("${stage.stageId} has no choices", choices.isNotEmpty())
            assertTrue(
                "${stage.stageId} has no affinity bonus",
                choices.any { it.affinityBonus > 0 },
            )
            choices.filter { it.affinityBonus > 0 }.forEach { c ->
                val target = c.affinityCharacterId
                if (target != null) {
                    assertTrue("choice target $target unknown", knownIds.contains(target))
                }
            }
        }
    }

    @Test
    fun `battle stages use rift bestiary ids and have recommended level`() {
        StoryService.STORY_CHAPTERS
            .flatMap { it.stages }
            .filter { it.type == StoryStageType.BATTLE }
            .forEach { stage ->
                val enemies = stage.enemyIds.orEmpty()
                assertTrue("${stage.stageId} has no enemies", enemies.isNotEmpty())
                assertTrue(
                    "${stage.stageId} enemy ids should use rift_ bestiary: $enemies",
                    enemies.all { it.startsWith("rift_") },
                )
                assertTrue("${stage.stageId} recommendedLevel < 1", stage.recommendedLevel >= 1)
            }
    }
}
