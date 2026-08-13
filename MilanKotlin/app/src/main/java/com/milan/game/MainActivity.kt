package com.milan.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.milan.game.infrastructure.CrashReporter
import com.milan.game.infrastructure.MilanAudio
import com.milan.game.ui.LocalSharedTransitionScope
import com.milan.game.ui.characters.CharacterDetailScreen
import com.milan.game.ui.characters.CharacterListScreen
import com.milan.game.ui.collection.CollectionScreen
import com.milan.game.ui.deck.DeckScreen
import com.milan.game.ui.gacha.GachaScreen
import com.milan.game.ui.home.HomeScreen
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.nav.CharacterDetailRoute
import com.milan.game.ui.nav.CharacterListRoute
import com.milan.game.ui.nav.CollectionRoute
import com.milan.game.ui.nav.DeckRoute
import com.milan.game.ui.nav.GachaRoute
import com.milan.game.ui.nav.HomeRoute
import com.milan.game.ui.nav.NavItem
import com.milan.game.ui.nav.ProgressionRoute
import com.milan.game.ui.nav.SettingsRoute
import com.milan.game.ui.nav.ShopRoute
import com.milan.game.ui.nav.toNavRoute
import com.milan.game.ui.progression.ProgressionScreen
import com.milan.game.ui.shop.ShopScreen
import com.milan.game.ui.settings.SettingsScreen
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.MilanTheme

/**
 * 单一宿主的游戏入口（C# 多 Activity 结构的 Compose 单 Activity 等价物）。
 * 路由模型：Navigation Compose 2.9 类型安全路由——5 个主 tab（各页内自行渲染 GameNavBar）
 * + 4 层子页（神谱图鉴 → 我的角色 → 角色详情 → 角色养成），子页压栈盖住 tab，
 * 顶栏返回 / 系统返回（Predictive Back）逐层退出。路由定义见 ui/nav/Routes.kt。
 * 立绘共享元素过渡：SharedTransitionLayout 包 NavHost，作用域经自建
 * LocalSharedTransitionScope 注入；composable 的 AnimatedContentScope receiver
 * 直接作为各 Screen 的 animatedVisibilityScope 参数（见 CharacterListScreen/DetailScreen）。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.boot("main.onCreate")
        // targetSdk≥35 强制 edge-to-edge：内容延伸到系统栏区域，由各组件用 insets 内边距避让
        enableEdgeToEdge()
        MilanAudio.playBgm("theme") // 启动 BGM（无资源静默，见 MilanAudio）
        setContent {
            MilanTheme {
                MilanNavHost()
            }
        }
    }

    /**
     * BGM 生命周期接线：App 退后台（锁屏/切走）暂停 BGM 并释放 ExoPlayer 省资源，
     * 回前台自动恢复上次曲目（保留 bgmTarget 意图，见 MilanAudio.pauseBackground/resumeForeground）。
     * 之前缺失此接线，BGM 在后台持续播放。
     */
    override fun onStop() {
        super.onStop()
        MilanAudio.pauseBackground()
    }

    override fun onStart() {
        super.onStart()
        MilanAudio.resumeForeground()
    }
}

/** 导航宿主（Navigation Compose 2.9 类型安全路由）：tab 切换 + 子页压栈覆盖。
 *  SharedTransitionLayout 提供立绘共享元素过渡作用域。 */
@Composable
private fun MilanNavHost() {
    val navController = rememberNavController()

    // P3-10：EventBus 宿主级兜底派发——GameService 写操作内联 dispatch 之外的来源
    // （未来 widget/后台/其他模块 publish）也能被消费，防事件滞留队列（上限 512 只是保险丝）。
    LaunchedEffect(Unit) {
        while (true) {
            com.milan.game.infrastructure.eventbus.EventBus.dispatch()
            kotlinx.coroutines.delay(250)
        }
    }

    // ── 导航辅助（C# 语义翻译）──

    /** 切换主 tab：标准 bottom-nav 模式（saveState/restoreState 保持各 tab 状态）。 */
    fun navigateToTab(item: NavItem) {
        navController.navigate(item.toNavRoute()) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** 打开角色详情：子页压栈（可从任一 tab / 列表进入）。 */
    fun openCharacter(id: String) {
        navController.navigate(CharacterDetailRoute(id)) { launchSingleTop = true }
    }

    /** 左右切换角色：替换当前详情/养成 entry（保持「返回即回上级」原语义，不累积返回栈）。 */
    fun switchCharacter(id: String) {
        val current = navController.currentDestination ?: return
        val route: Any =
            if (current.hasRoute<ProgressionRoute>()) ProgressionRoute(id) else CharacterDetailRoute(id)
        navController.navigate(route) {
            popUpTo(current.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        // Compose 1.11：SharedTransitionLayout 的 content 以 SharedTransitionScope 为 receiver，
        // 官方 LocalSharedTransitionScope 已移除，改用自建 CompositionLocal 注入（见 SharedTransitionLocals.kt）
        val sharedScope = this
        CompositionLocalProvider(LocalSharedTransitionScope provides sharedScope) {
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier.fillMaxSize(),
                // 统一过渡：fade + 轻微上滑（D4，与原 AnimatedContent 一致；Predictive Back 由 Navigation 自动接入）
                enterTransition = { fadeIn(tween(280)) + slideInVertically(initialOffsetY = { it / 24 }) },
                exitTransition = { fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { -it / 24 }) },
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        onNav = ::navigateToTab,
                        onOpenGacha = { navigateToTab(NavItem.Gacha) },
                        onOpenCollection = { navController.navigate(CollectionRoute) },
                        onOpenCharacter = ::openCharacter,
                    )
                }
                composable<GachaRoute> {
                    GachaScreen(
                        onNav = ::navigateToTab,
                        onOpenCharacter = ::openCharacter,
                    )
                }
                composable<DeckRoute> {
                    DeckScreen(
                        onNav = ::navigateToTab,
                        onOpenCharacter = ::openCharacter,
                    )
                }
                composable<ShopRoute> {
                    ShopScreen(
                        onNav = ::navigateToTab,
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        onNav = ::navigateToTab,
                    )
                }
                composable<CollectionRoute> {
                    CollectionScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCharacter = ::openCharacter,
                        onOpenMyCharacters = { navController.navigate(CharacterListRoute) },
                        animatedVisibilityScope = this,
                    )
                }
                composable<CharacterListRoute> {
                    CharacterListScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCharacter = ::openCharacter,
                        animatedVisibilityScope = this,
                    )
                }
                composable<CharacterDetailRoute> { entry ->
                    val route = entry.toRoute<CharacterDetailRoute>()
                    CharacterDetailScreen(
                        characterId = route.characterId,
                        onBack = { navController.popBackStack() },
                        onOpenProgression = { id ->
                            navController.navigate(ProgressionRoute(id)) { launchSingleTop = true }
                        },
                        onSwitchCharacter = ::switchCharacter,
                        animatedVisibilityScope = this,
                    )
                }
                composable<ProgressionRoute> { entry ->
                    val route = entry.toRoute<ProgressionRoute>()
                    ProgressionScreen(
                        characterId = route.characterId,
                        onBack = { navController.popBackStack() },
                        onSwitchCharacter = ::switchCharacter,
                    )
                }
            }
        }
    }
}
