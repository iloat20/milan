package com.milan.game.ui.tower

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.milan.game.services.TowerOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 爬塔结算卡 UI 测试（2026-08-28 P0：项目第一批 Compose UI 测试）。
 *
 * 覆盖两条审查发现的回归：
 * - **F2（Critical）**：结算卡原本在 `AnimatedVisibility` 里对 result 做不安全强转
 *   `result as TowerOutcome.Completed`。退出动画期间 content 仍会重组，而 result 可能已被
 *   下一次挑战改写为 Draw/SaveFailed → ClassCastException 闪退。
 * - **M1**：「纪录推进至第 N 层」原条件 `bestFloorAfter >= nextFloor` 恒为 false
 *   （nextFloor 在结算返回前已被快照刷新为 best+1），文案永不显示。
 *
 * SDK 降级到 34 的原因见 ComposeUiSmokeTest 的类注释（本机 JDK 17 环境限制）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TowerResultCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun victoryWithRecord_showsRewardAndRecordText() {
        composeRule.setContent {
            TowerResultCard(
                done = TowerOutcome.Completed(
                    victory = true,
                    turns = 8,
                    rewardSoft = 1000,
                    rewardHard = 10,
                    bestFloorAfter = 10,
                    recordAdvanced = true,
                ),
            )
        }
        composeRule.onNodeWithText("✦ 攻克！用时 8 回合").assertIsDisplayed()
        composeRule.onNodeWithText("星尘 +1000").assertIsDisplayed()
        composeRule.onNodeWithText("◆ 钻石 +10（首次攻克里程碑）").assertIsDisplayed()
        // M1 回归：recordAdvanced=true 时必须显示纪录推进文案
        composeRule.onNodeWithText("纪录推进至第 10 层").assertIsDisplayed()
    }

    @Test
    fun victoryWithoutRecord_hidesRecordText() {
        composeRule.setContent {
            TowerResultCard(
                done = TowerOutcome.Completed(
                    victory = true,
                    turns = 6,
                    rewardSoft = 0,
                    bestFloorAfter = 10,
                    recordAdvanced = false, // 复刷已通层：未刷新纪录
                ),
            )
        }
        composeRule.onNodeWithText("纪录推进至第 10 层").assertIsNotDisplayed()
        composeRule.onNodeWithText("星尘 +0").assertIsDisplayed()
    }

    @Test
    fun defeat_showsStopTextAndNoRewards() {
        composeRule.setContent {
            TowerResultCard(
                done = TowerOutcome.Completed(
                    victory = false,
                    turns = 50,
                    rewardSoft = 0,
                    bestFloorAfter = 3,
                ),
            )
        }
        composeRule.onNodeWithText("✖ 止步于此（50 回合）").assertIsDisplayed()
        composeRule.onNodeWithText("星尘 +0").assertIsNotDisplayed()
    }

    /**
     * F2 回归：结算结果在下一次挑战中被改写为 Draw 时，退出动画期间不得崩溃。
     *
     * 修复前：`result as TowerOutcome.Completed` → ClassCastException 闪退。
     * 修复后：`result as? ... ?: return@AnimatedVisibility` 静默跳过。
     * 本测试不显式断言异常——断言树上的任何异常都会让测试失败，
     * 通过即证明该路径不再抛出 ClassCastException。
     */
    @Test
    fun whenResultSwitchesToDraw_cardDisappearsWithoutCrash() {
        var result by mutableStateOf<TowerOutcome>(
            TowerOutcome.Completed(
                victory = true,
                turns = 5,
                rewardSoft = 1000,
                bestFloorAfter = 1,
                recordAdvanced = true,
            ),
        )

        composeRule.setContent {
            AnimatedVisibility(visible = result is TowerOutcome.Completed) {
                val done = result as? TowerOutcome.Completed ?: return@AnimatedVisibility
                TowerResultCard(done = done)
            }
        }
        composeRule.onNodeWithText("星尘 +1000").assertIsDisplayed()

        // 下一次挑战返回平局 → 结算卡进入退出动画，期间 content 仍会重组
        result = TowerOutcome.Draw(turns = 50)
        composeRule.waitForIdle()

        // 动画结束后结算卡彻底移除；关键是整个过程未抛 ClassCastException
        composeRule.onNodeWithText("星尘 +1000").assertIsNotDisplayed()
    }

    /** 同上：改写为 SaveFailed 时同样不得崩溃。 */
    @Test
    fun whenResultSwitchesToSaveFailed_cardDisappearsWithoutCrash() {
        var result by mutableStateOf<TowerOutcome>(
            TowerOutcome.Completed(victory = true, turns = 5, rewardSoft = 1000, bestFloorAfter = 1),
        )
        composeRule.setContent {
            AnimatedVisibility(visible = result is TowerOutcome.Completed) {
                val done = result as? TowerOutcome.Completed ?: return@AnimatedVisibility
                TowerResultCard(done = done)
            }
        }
        composeRule.onNodeWithText("星尘 +1000").assertIsDisplayed()

        result = TowerOutcome.SaveFailed
        composeRule.waitForIdle()
        composeRule.onNodeWithText("星尘 +1000").assertIsNotDisplayed()
    }
}
