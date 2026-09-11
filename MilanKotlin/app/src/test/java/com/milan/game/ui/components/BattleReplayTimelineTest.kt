package com.milan.game.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.milan.game.domain.battle.StrikeEvent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 战局重演时间轴（v3 §7.4）渲染冒烟：空日志不崩、有日志显示回合摘要、可展开明细。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BattleReplayTimelineTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyLogRendersNothing() {
        composeRule.setContent {
            BattleReplayTimeline(log = emptyList())
        }
        composeRule.onNodeWithText("战局重演").assertDoesNotExist()
    }

    @Test
    fun logShowsSummaryAndExpandableDetail() {
        val log = listOf(
            StrikeEvent(
                turn = 1,
                attackerId = "a1",
                attackerElement = "Flame",
                targetId = "b1",
                targetElement = "Wood",
                damage = 42,
                targetDefeated = false,
            ),
            StrikeEvent(
                turn = 1,
                attackerId = "a1",
                attackerElement = "Flame",
                targetId = "b1",
                targetElement = "Wood",
                damage = 80,
                targetDefeated = true,
                isElementReaction = true,
                reactionName = "蒸发",
            ),
        )
        composeRule.setContent {
            BattleReplayTimeline(
                log = log,
                names = mapOf("a1" to "烛龙", "b1" to "木魅"),
            )
        }
        composeRule.onNodeWithText("战局重演").assertExists()
        composeRule.onNodeWithText("1 回合 · 2 击").assertExists()
    }
}
