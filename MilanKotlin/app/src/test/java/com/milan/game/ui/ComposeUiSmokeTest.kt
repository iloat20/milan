package com.milan.game.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI 测试基础设施冒烟测试（2026-08-28 P0）。
 *
 * 目的：验证 Robolectric + Compose UI Test 能在 JVM 上跑通。
 * 项目此前 9624 行 UI 代码**零测试覆盖**，UI 缺陷只能靠静态审查发现——
 * 第三轮审查的 14 个 bug 中有 6 个在 UI 层（含 1 个 Critical 闪退）。
 * 本测试是给 UI 层建立可回归验证能力的第一步。
 */
// SDK 降级原因（本机环境限制，非项目问题）：
// 1. targetSdk=37 超出当前 Robolectric 支持上限（maxSdkVersion=36）；
// 2. SDK 35/36 的 Robolectric 沙箱要求 Java 21，而本机仅有 JDK 17。
// 故降级到 34（Robolectric 在 Java 17 上支持的最高档，且高于 minSdk=29）。
// Compose 渲染路径不依赖 35+ 的新增 API，不影响测试有效性。
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComposeUiSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun composeRendersOnJvm() {
        composeRule.setContent {
            Text("Milan UI 测试就绪")
        }
        composeRule.onNodeWithText("Milan UI 测试就绪").assertExists()
    }
}
