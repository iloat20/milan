package com.milan.game.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CodexCard 工艺分级卡框渲染测试（设计语言 v3 §5.2）。
 *
 * 覆盖：四档稀有度（R/SR/SSR/UR）渲染不崩溃、内容槽透出、onClick 回调触发。
 * 绘制层（织纹/流光/角标）走 Canvas 无语义节点，此处只做语义与行为断言；
 * 视觉验收由 P2 逐页 judge 截图对照执行（见设计语言 §10 路线图）。
 * SDK 降级到 34 的原因同 [com.milan.game.ui.ComposeUiSmokeTest]。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CodexCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun codexCardRendersAllFourTiers() {
        composeRule.setContent {
            androidx.compose.foundation.layout.Column {
                CodexCard(tier = 1, modifier = Modifier.size(120.dp, 160.dp)) { Text("tier-R") }
                CodexCard(tier = 2, modifier = Modifier.size(120.dp, 160.dp)) { Text("tier-SR") }
                CodexCard(tier = 3, modifier = Modifier.size(120.dp, 160.dp)) { Text("tier-SSR") }
                CodexCard(tier = 4, modifier = Modifier.size(120.dp, 160.dp)) { Text("tier-UR") }
            }
        }
        composeRule.onNodeWithText("tier-R").assertExists()
        composeRule.onNodeWithText("tier-SR").assertExists()
        composeRule.onNodeWithText("tier-SSR").assertExists()
        composeRule.onNodeWithText("tier-UR").assertExists()
    }

    @Test
    fun codexCardClickInvokesCallback() {
        var clicks = 0
        composeRule.setContent {
            CodexCard(
                tier = 4,
                modifier = Modifier.size(120.dp, 160.dp),
                onClick = { clicks++ },
            ) { Text("可点击卡") }
        }
        composeRule.onNodeWithText("可点击卡").performClick()
        assertEquals(1, clicks)
    }
}
