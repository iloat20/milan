package com.milan.game.ui

import com.milan.game.GameState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.milan.game.data.SaveProvider
import com.milan.game.infrastructure.eventbus.EventBus
import com.milan.game.ui.deck.DeckScreen
import com.milan.game.ui.feedback.Feedback
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.gacha.GachaScreen
import com.milan.game.ui.nav.NavItem
import com.milan.game.ui.theme.MilanTheme
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GachaScreen + DeckScreen Compose UI 测试。
 *
 * 覆盖：
 * - GachaScreen 标题 / 按钮 / 底部导航渲染
 * - GachaScreen「单抽」按钮可点击不崩溃
 * - DeckScreen 空态引导渲染
 * - DeckScreen「前往寻访」导航回调触发
 *
 * 约束：GameState 是进程级单例；@BeforeClass 先 [GameState.resetForTest] 再注入 testContent，
 * 保证本类开始时状态干净（Robolectric 实例化的 MilanApp 可能已异步注入真实 data.json，
 * 不重置会让本类注入变 no-op——2026-09-02 G05 假失败的根因）。测试方法间共享同一实例。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GachaDeckScreenTest {

    companion object {
        /**
         * 最小化测试内容：1 个 SSR 角色 + 1 个卡池，足以让 GachaScreen/DeckScreen 渲染。
         */
        private val testContent = """
            {
              "Characters": [
                {
                  "CharacterId": "char_alpha",
                  "DisplayName": "苍岚",
                  "Title": "测试角色",
                  "BaseRarity": 3,
                  "Element": "Water",
                  "World": "Shinwa"
                }
              ],
              "Pools": [
                {
                  "PoolId": "pool_main",
                  "DisplayName": "常驻卡池",
                  "RarityWeights": [0, 0, 1000, 0],
                  "HardPity": 90,
                  "SoftPityStart": 75,
                  "SingleCost": 160,
                  "TenCost": 1600,
                  "FeaturedCharacterId": "",
                  "Entries": [
                    { "CharacterId": "char_alpha", "RarityIndex": 3, "Weight": 1000 }
                  ]
                }
              ]
            }
        """.trimIndent()

        @BeforeClass
        @JvmStatic
        fun initGameState() {
            // 确保 EventBus 干净
            EventBus.clear()
            EventBus.handlerException = null
            // 先重置再注入：清掉可能已由 MilanApp.onCreate 异步注入的真实 data.json，
            // 使本类注入稳定生效（幂等早退会吞掉本类注入 → G05 曾因找不到「常驻卡池」假失败）。
            GameState.resetForTest()
            GameState.ensureInitialized(
                saveProvider = FakeTestProvider(),
                contentJson = testContent,
                onTrace = { },
            )
        }
    }

    /** 测试专用内存存档——与 GameServiceTest.FakeProvider 同构。 */
    private class FakeTestProvider : SaveProvider {
        private var stored: String? = null
        override fun save(json: String): Boolean { stored = json; return true }
        override fun load(): String = stored ?: ""
        override fun delete() { stored = null }
        override fun exists(): Boolean = stored != null
        override fun loadBackup(): String? = null
    }

    @get:Rule
    val composeRule = createComposeRule()

    // ═══════════════════════════════════════
    //  GachaScreen
    // ═══════════════════════════════════════

    @Test
    fun `G01 - GachaScreen renders title and pull buttons`() {
        // testMode 禁用 CyberHerald 无限动画，避免 AppNotIdleException
        composeRule.setContent {
            MilanTheme {
                val snackbarHost = SnackbarHostState()
                val feedback = Feedback(snackbarHost)
                CompositionLocalProvider(LocalFeedback provides feedback) {
                    GachaScreen(
                        onNav = { },
                        onOpenCharacter = { },
                        onOpenHistory = { },
                        testMode = true,
                    )
                }
            }
        }
        // 推进时钟让入场动画（tween 450ms）完成，使 Column alpha=1
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()
        // 标题（在视口顶部，无需滚动）
        composeRule.onNodeWithText("丹青寻访").assertIsDisplayed()
        // 单抽 / 十连按钮（在 CyberHerald 下方，需滚动到底部）
        composeRule.onNodeWithText("单 抽").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("十 连").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `G02 - GachaScreen renders bottom navigation bar`() {
        composeRule.setContent {
            MilanTheme {
                val snackbarHost = SnackbarHostState()
                val feedback = Feedback(snackbarHost)
                CompositionLocalProvider(LocalFeedback provides feedback) {
                    GachaScreen(
                        onNav = { },
                        onOpenCharacter = { },
                        onOpenHistory = { },
                        testMode = true,
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()
        // 底部导航标签（在 Column 底部，需逐个滚动）
        composeRule.onNodeWithText("主页").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("抽卡").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("卡组").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("商店").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("设置").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `G03 - GachaScreen single pull button click does not crash`() {
        var navCalled = false
        composeRule.setContent {
            MilanTheme {
                val snackbarHost = SnackbarHostState()
                val feedback = Feedback(snackbarHost)
                CompositionLocalProvider(LocalFeedback provides feedback) {
                    GachaScreen(
                        onNav = { navCalled = true },
                        onOpenCharacter = { },
                        onOpenHistory = { },
                        testMode = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        // 点击「单抽」——余额足够（默认新档 1600 星尘），应触发抽卡流程不崩溃
        composeRule.onNodeWithText("单 抽").performClick()
        composeRule.waitForIdle()
        // 不崩溃即通过；nav 回调未被触发（抽卡不导航）
        assert(!navCalled) { "onNav should not be called during pull" }
    }

    @Test
    fun `G04 - GachaScreen ten pull button click does not crash`() {
        composeRule.setContent {
            MilanTheme {
                val snackbarHost = SnackbarHostState()
                val feedback = Feedback(snackbarHost)
                CompositionLocalProvider(LocalFeedback provides feedback) {
                    GachaScreen(
                        onNav = { },
                        onOpenCharacter = { },
                        onOpenHistory = { },
                        testMode = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("十 连").performClick()
        composeRule.waitForIdle()
        // 十连同样不崩溃即通过
    }

    @Test
    fun `G05 - GachaScreen pool info panel shows pool name`() {
        composeRule.setContent {
            MilanTheme {
                val snackbarHost = SnackbarHostState()
                val feedback = Feedback(snackbarHost)
                CompositionLocalProvider(LocalFeedback provides feedback) {
                    GachaScreen(
                        onNav = { },
                        onOpenCharacter = { },
                        onOpenHistory = { },
                        testMode = true,
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()
        // 池名断言做「内容无关」：卡池信息面板展示当前选中池（默认首池）的池名。
        // 背景：GameState 是进程级单例，注入内容取决于谁先初始化成功——
        // 单类跑时本类 @BeforeClass 注入 testContent（单池「常驻卡池」）；全量套件中
        // Robolectric 会实例化 MilanApp，其 onCreate 后台异步注入真实 data.json（首池
        // 「次元裂缝 · 常驻」），使 @BeforeClass 的注入变为 no-op。故按运行时实际首池断言。
        // 用存在性而非 assertIsDisplayed：Robolectric 字体度量下 CJK 文本可能被量成近零宽
        // （实测 4px），displayed 检查受布局 settle 时序影响易误报；渲染覆盖面由 G01/G02 承担。
        val pools = GameState.service.pools
        assertTrue("测试环境必须至少有一个卡池", pools.isNotEmpty())
        val firstName = pools.first().displayName.ifEmpty { "常驻卡池" }
        val matches = composeRule.onAllNodesWithText(firstName).fetchSemanticsNodes()
        assertTrue("池名「$firstName」应展示在卡池信息区（chips 或面板）", matches.isNotEmpty())
    }

    @Test
    fun `G06 - GachaScreen history button triggers callback`() {
        var historyOpened = false
        composeRule.setContent {
            MilanTheme {
                val snackbarHost = SnackbarHostState()
                val feedback = Feedback(snackbarHost)
                CompositionLocalProvider(LocalFeedback provides feedback) {
                    GachaScreen(
                        onNav = { },
                        onOpenCharacter = { },
                        onOpenHistory = { historyOpened = true },
                        testMode = true,
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()
        // 「历 史 ›」在摘要行（CyberHerald 下方），需先滚动到底部再点击
        composeRule.onNodeWithText("历 史 ›").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("历 史 ›").performClick()
        composeRule.waitForIdle()
        assert(historyOpened) { "onOpenHistory should be called when clicking history button" }
    }

    // ═══════════════════════════════════════
    //  DeckScreen
    // ═══════════════════════════════════════

    @Test
    fun `D01 - DeckScreen renders title and empty state guidance`() {
        composeRule.setContent {
            MilanTheme {
                val snackbarHost = SnackbarHostState()
                val feedback = Feedback(snackbarHost)
                CompositionLocalProvider(LocalFeedback provides feedback) {
                    DeckScreen(
                        onNav = { },
                        onOpenCharacter = { },
                    )
                }
            }
        }
        // 顶部标题
        composeRule.onNodeWithText("卡 组").assertIsDisplayed()
        // 空态引导文字（新档无角色）— EmptyState 拆成 title + subtitle 两行
        composeRule.onNodeWithText("还没有角色").assertIsDisplayed()
        composeRule.onNodeWithText("去寻访吧").assertIsDisplayed()
    }

    @Test
    fun `D02 - DeckScreen empty state shows navigate-to-gacha button`() {
        var navTarget: NavItem? = null
        composeRule.setContent {
            MilanTheme {
                val snackbarHost = SnackbarHostState()
                val feedback = Feedback(snackbarHost)
                CompositionLocalProvider(LocalFeedback provides feedback) {
                    DeckScreen(
                        onNav = { navTarget = it },
                        onOpenCharacter = { },
                    )
                }
            }
        }
        composeRule.onNodeWithText("前往寻访").performClick()
        assert(navTarget == NavItem.Gacha) { "Should navigate to Gacha tab, got: $navTarget" }
    }

    @Test
    fun `D03 - DeckScreen renders bottom navigation bar`() {
        composeRule.setContent {
            MilanTheme {
                val snackbarHost = SnackbarHostState()
                val feedback = Feedback(snackbarHost)
                CompositionLocalProvider(LocalFeedback provides feedback) {
                    DeckScreen(
                        onNav = { },
                        onOpenCharacter = { },
                    )
                }
            }
        }
        composeRule.onNodeWithText("主页").assertIsDisplayed()
        composeRule.onNodeWithText("抽卡").assertIsDisplayed()
        composeRule.onNodeWithText("卡组").assertIsDisplayed()
        composeRule.onNodeWithText("商店").assertIsDisplayed()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
    }

    @Test
    fun `D04 - DeckScreen owned count shows zero for fresh save`() {
        composeRule.setContent {
            MilanTheme {
                val snackbarHost = SnackbarHostState()
                val feedback = Feedback(snackbarHost)
                CompositionLocalProvider(LocalFeedback provides feedback) {
                    DeckScreen(
                        onNav = { },
                        onOpenCharacter = { },
                    )
                }
            }
        }
        composeRule.onNodeWithText("已拥有  0  位角色").assertIsDisplayed()
    }
}
