package com.milan.game.ui.nav

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.milan.game.data.AndroidSaveProvider
import com.milan.game.infrastructure.CrashReporter
import com.milan.game.ui.LocalSharedTransitionScope
import com.milan.game.ui.achievement.AchievementScreen
import com.milan.game.ui.components.InkSkeleton
import com.milan.game.ui.characters.CharacterDetailScreen
import com.milan.game.ui.characters.CharacterListScreen
import com.milan.game.ui.collection.CollectionScreen
import com.milan.game.ui.deck.DeckScreen
import com.milan.game.ui.gacha.GachaScreen
import com.milan.game.ui.gacha.PullHistoryScreen
import com.milan.game.ui.home.HomeScreen
import com.milan.game.ui.progression.ProgressionScreen
import com.milan.game.ui.shop.ShopScreen
import com.milan.game.ui.settings.SettingsScreen
import com.milan.game.ui.tower.TowerScreen
import com.milan.game.ui.story.StoryScreen
import com.milan.game.ui.story.DialogueScreen
import com.milan.game.ui.feedback.Feedback
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.theme.AppTheme
import com.milan.game.GameState

/**
 * 导航宿主（Navigation Compose 2.9 类型安全路由）：tab 切换 + 子页压栈覆盖。
 * SharedTransitionLayout 提供立绘共享元素过渡作用域。
 *
 * 从 MainActivity.kt 拆分（P3-12，2026-08-30 重构）。
 */
@Composable
internal fun MilanNavHost(openGachaOnStart: Boolean = false) {
    val navController = rememberNavController()

    // P3-11 配套：内容/存档已在 Application 后台线程初始化（见 MilanApp）。
    // 就绪前整棵导航树不组合——门控在此一处收口（Screen 已全部改走 AppGraph/VM）。
    // 附带移除旧「EventBus 每 250ms 兜底派发」空转轮询：全工程已零订阅者，
    // GameService 所有发布点均内联 dispatch，事件队列不存在滞留风险。
    // R4-04（2026-08-30 审查修复）：初始化失败必须有出口。
    val failure by GameState.failure.collectAsStateWithLifecycle()
    if (failure != null) {
        val context = LocalContext.current
        InkErrorScreen(
            reason = failure!!,
            onRetry = {
                GameState.ensureInitialized(
                    saveProvider = AndroidSaveProvider(context.applicationContext),
                    contentJson = runCatching {
                        context.assets.open("data.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }.getOrNull(),
                    onTrace = { CrashReporter.boot(it) },
                )
            },
        )
        return
    }

    val ready by GameState.ready.collectAsStateWithLifecycle()
    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            InkSkeleton()
        }
        return
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

    // I9：Glance 小组件点按直达抽卡页（启动即切 tab；正常启动 openGachaOnStart=false 无副作用）
    LaunchedEffect(Unit) {
        if (openGachaOnStart) navigateToTab(NavItem.Gacha)
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

    // R3 / I4：统一反馈宿主——全树经 LocalFeedback 取 Snackbar 入口（配 AppTheme 配色），
    // 替代各处 Toast/局部 toast 四套写法。Snackbar 浮层置于底部导航之上。
    val snackbarHost = remember { SnackbarHostState() }
    val feedback = remember { Feedback(snackbarHost) }
    // 动效减弱（无障碍）：全树注入，高负载演出读取后降级
    val reduceMotion by com.milan.game.di.AppGraph.service.meta
        .collectAsStateWithLifecycle()
    val reduceMotionOn = reduceMotion.reduceMotionEnabled
    CompositionLocalProvider(
        LocalFeedback provides feedback,
        // ResourceBar 等 Chrome 组件订阅经济切片（2026-09-10：消除 AppGraph.service 泄漏）
        LocalEconomySlice provides com.milan.game.di.AppGraph.service.economy,
        com.milan.game.ui.effects.LocalReduceMotion provides reduceMotionOn,
    ) {
        Box(Modifier.fillMaxSize()) {
            SharedTransitionLayout(Modifier.fillMaxSize()) {
        // Compose 1.11：SharedTransitionLayout 的 content 以 SharedTransitionScope 为 receiver，
        // 官方 LocalSharedTransitionScope 已移除，改用自建 CompositionLocal 注入（见 SharedTransitionLocals.kt）
        val sharedScope = this
        CompositionLocalProvider(LocalSharedTransitionScope provides sharedScope) {
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier.fillMaxSize(),
                // 水墨国风转场：墨汁泼入/干涸（InkTransitions）
                enterTransition = { InkTransitions.slideInFromRight },
                exitTransition = { InkTransitions.slideOutToLeft },
                popEnterTransition = { InkTransitions.slideInFromLeft },
                popExitTransition = { InkTransitions.slideOutToRight },
            ) {
                composable<HomeRoute>(
                    enterTransition = { InkTransitions.tabEnter },
                    exitTransition = { InkTransitions.tabExit },
                ) {
                    HomeScreen(
                        onNav = ::navigateToTab,
                        onOpenGacha = { navigateToTab(NavItem.Gacha) },
                        onOpenCollection = { navController.navigate(CollectionRoute) },
                        onOpenCharacter = ::openCharacter,
                        onOpenTower = { navController.navigate(TowerRoute) },
                        onOpenAchievements = { navController.navigate(AchievementRoute) },
                        onOpenStory = { navController.navigate(StoryRoute) },
                        onOpenDailyMissions = { navController.navigate(DailyMissionRoute) },
                        onOpenBattlePass = { navController.navigate(BattlePassRoute) },
                        onOpenAffinity = { navController.navigate(AffinityRoute) },
                        onOpenArena = { navController.navigate(ArenaRoute) },
                        onOpenEvent = { navController.navigate(EventRoute) },
                    )
                }
                composable<GachaRoute>(
                    enterTransition = { InkTransitions.tabEnter },
                    exitTransition = { InkTransitions.tabExit },
                ) {
                    GachaScreen(
                        onNav = ::navigateToTab,
                        onOpenCharacter = ::openCharacter,
                        onOpenHistory = { navController.navigate(PullHistoryRoute) },
                    )
                }
                composable<DeckRoute>(
                    enterTransition = { InkTransitions.tabEnter },
                    exitTransition = { InkTransitions.tabExit },
                ) {
                    DeckScreen(
                        onNav = ::navigateToTab,
                        onOpenCharacter = ::openCharacter,
                        animatedVisibilityScope = this,
                    )
                }
                composable<ShopRoute>(
                    enterTransition = { InkTransitions.tabEnter },
                    exitTransition = { InkTransitions.tabExit },
                ) {
                    ShopScreen(
                        onNav = ::navigateToTab,
                    )
                }
                composable<SettingsRoute>(
                    enterTransition = { InkTransitions.tabEnter },
                    exitTransition = { InkTransitions.tabExit },
                ) {
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
                        animatedVisibilityScope = this,
                    )
                }
                // 无尽之塔（2026-08 终局内容）：子页盖 tab，返回回主页
                composable<TowerRoute> {
                    TowerScreen(
                        onBack = { navController.popBackStack() },
                        onOpenDeck = { navigateToTab(NavItem.Deck) },
                    )
                }
                // 成就（2026-08 二期）：子页盖 tab，返回回主页
                composable<AchievementRoute> {
                    AchievementScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                // 抽卡历史（2026-08 三期）：子页盖 tab，返回回抽卡页
                composable<PullHistoryRoute> {
                    PullHistoryScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                // 剧情系统（2026-09）：子页盖 tab，返回回主页
                composable<StoryRoute> {
                    StoryScreen(
                        onBack = { navController.popBackStack() },
                        onOpenStage = { stageId -> navController.navigate(DialogueRoute(stageId)) },
                    )
                }
                // 剧情关卡（2026-09）：子页盖 tab，返回回章节列表
                composable<DialogueRoute> { entry ->
                    val route = entry.toRoute<DialogueRoute>()
                    // 剧情查询/完结统一走 StoryViewModel（AppGraph 注入），NavHost 不再摸 GameState.service
                    val storyVm: com.milan.game.ui.story.StoryViewModel =
                        androidx.lifecycle.viewmodel.compose.viewModel(factory = com.milan.game.di.AppGraph.factory)
                    val stage = remember(route.stageId) { storyVm.findStage(route.stageId) }
                    if (stage != null) {
                        DialogueScreen(
                            stage = stage,
                            onStageComplete = {
                                storyVm.completeStage(route.stageId)
                                navController.popBackStack()
                            },
                            // M3 修复：选择分支跳转到目标关卡——当前关完结（标记+发奖），
                            // 再压入目标关。目标为悬空引用（内容无此关）时兜底退出，避免黑屏。
                            onNavigateStage = { targetId ->
                                storyVm.completeStage(route.stageId)
                                if (storyVm.findStage(targetId) != null) {
                                    navController.navigate(DialogueRoute(targetId))
                                } else {
                                    navController.popBackStack()
                                }
                            },
                            onGrantAffinity = storyVm::grantAffinity,
                            onSelectEnding = storyVm::setEndingBranch,
                            characterOf = storyVm::characterOf,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                // 每日任务（2026-09）：子页盖 tab，返回回主页
                composable<DailyMissionRoute> {
                    com.milan.game.ui.missions.DailyMissionScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                // Battle Pass 纪行（2026-09）：子页盖 tab，返回回主页
                composable<BattlePassRoute> {
                    com.milan.game.ui.battlepass.BattlePassScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                // 角色好感度（2026-09）：子页盖 tab，返回回主页
                composable<AffinityRoute> {
                    com.milan.game.ui.affinity.AffinityScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCharacter = ::openCharacter,
                    )
                }
                // PVP 竞技场：子页盖 tab，返回回主页
                composable<ArenaRoute> {
                    com.milan.game.ui.arena.ArenaScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                // PVE 副本 composable 块已删除（2026-09-06 S2，PvEService 死功能裁撤）。
                // 活动：子页盖 tab，返回回主页
                composable<EventRoute> {
                    com.milan.game.ui.event.EventScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
    // 新手引导（第 8 节）：覆盖在导航树之上，可跳过；完成步由业务路径自动写入
    com.milan.game.ui.tutorial.TutorialOverlay(
        onNavigateTab = { label ->
            val item = NavItem.entries.firstOrNull { it.label == label }
            if (item != null) navigateToTab(item)
        },
    )
    SnackbarHost(
        hostState = snackbarHost,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp),
        snackbar = { data ->
            Snackbar(
                snackbarData = data,
                containerColor = AppTheme.BgMid,
                contentColor = AppTheme.Text1,
                actionColor = AppTheme.Gold,
            )
        },
    )
}
}
}

/**
 * 启动失败错误页（R4-04，2026-08-30 审查新增）。
 *
 * 此前初始化失败没有任何出口：玩家只看到转圈的加载动画，杀进程重进结果相同。
 * 本页给出失败原因（便于玩家反馈自查）与重试入口——重试走完整的 [GameState.ensureInitialized]
 * （失败时 initialized 仍为 false，可安全重试），成功后 failure 自动清空、UI 切回导航树。
 */
@Composable
private fun InkErrorScreen(reason: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(AppTheme.BgDeepest),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp),
        ) {
            Text(
                text = "启 动 失 败",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Text1,
            )
            Spacer(Modifier.height(14.dp))
            Text(text = reason, fontSize = 13.sp, color = AppTheme.Text3)
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .background(AppTheme.SurfaceNested, CircleShape)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 30.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "重 试",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Gold,
                )
            }
        }
    }
}

// InkLoadingIndicator 已移至 InkSkeleton.kt（水墨骨架屏加载组件）
