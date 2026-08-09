package com.milan.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.infrastructure.CrashReporter
import com.milan.game.infrastructure.MilanAudio
import com.milan.game.ui.characters.CharacterDetailScreen
import com.milan.game.ui.characters.CharacterListScreen
import com.milan.game.ui.components.NeonButton
import com.milan.game.ui.gacha.GachaScreen
import com.milan.game.ui.home.HomeScreen
import com.milan.game.ui.nav.AppTopBar
import com.milan.game.ui.nav.NavItem
import com.milan.game.ui.progression.ProgressionScreen
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.MilanTheme

/**
 * 单一宿主的游戏入口（C# 多 Activity 结构的 Compose 单 Activity 等价物）。
 *
 * 路由模型：5 个主 tab + 2 类子页（名录图鉴 / 角色详情）。
 * 子页打开时盖住 tab 内容，顶栏返回键回到 tab 层。
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
}

/** 导航路由快照：data class 值相等则不重启过渡（每次重组新建实例，equals 判定）。 */
private data class NavRoute(
    val tab: NavItem,
    val collectionOpen: Boolean,
    val listOpen: Boolean,
    val detailId: String?,
    val progressionId: String?,
)

/** 导航宿主：tab 切换 + 子页覆盖。SharedTransitionLayout 提供立绘过渡作用域。 */
@Composable
private fun MilanNavHost() {
    var tab by rememberSaveable { mutableStateOf(NavItem.Home) }
    var collectionOpen by rememberSaveable { mutableStateOf(false) }
    var listOpen by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var progressionId by rememberSaveable { mutableStateOf<String?>(null) }

    val onBack = { collectionOpen = false; listOpen = false; progressionId = null; detailId = null }

    // 路由快照：AnimatedContent 的 targetState（值相等不重启过渡）
    val route = NavRoute(tab, collectionOpen, listOpen, detailId, progressionId)

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                // 子页进出场：fade + 轻微上滑（D4，替换硬切）
                (fadeIn(tween(280)) + slideInVertically(initialOffsetY = { it / 24 }))
                    .togetherWith(fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { -it / 24 }))
            },
            label = "nav",
        ) { r ->
            // 子页优先：角色养成 > 角色详情 > 角色列表 > 名录图鉴（快照判断，避免 smart-cast 问题）
            when {
                r.progressionId != null -> ProgressionScreen(
                    characterId = r.progressionId,
                    onBack = { progressionId = null },
                    onSwitchCharacter = { id -> progressionId = id },
                )
                r.detailId != null -> CharacterDetailScreen(
                    characterId = r.detailId,
                    onBack = onBack,
                    onOpenProgression = { id -> progressionId = id },
                    onSwitchCharacter = { id -> detailId = id },
                )
                r.listOpen -> CharacterListScreen(
                    onBack = onBack,
                    onOpenCharacter = { id -> detailId = id },
                )
                r.collectionOpen -> PlaceholderScreen(
                    title = "神谱图鉴",
                    onBack = onBack,
                    actionLabel = "我的角色",
                    onAction = { listOpen = true },
                )
                else -> when (r.tab) {
                    NavItem.Home -> HomeScreen(
                        onNav = { tab = it },
                        onOpenGacha = { tab = NavItem.Gacha },
                        onOpenCollection = { collectionOpen = true },
                        onOpenCharacter = { id -> detailId = id },
                    )
                    NavItem.Gacha -> GachaScreen(
                        onNav = { tab = it },
                        onOpenCharacter = { id -> detailId = id },
                    )
                    NavItem.Deck -> PlaceholderScreen(
                        title = "卡组",
                        onBack = { tab = NavItem.Home },
                        navItem = NavItem.Deck,
                        onNav = { tab = it },
                    )
                    NavItem.Shop -> PlaceholderScreen(
                        title = "商店",
                        onBack = { tab = NavItem.Home },
                        navItem = NavItem.Shop,
                        onNav = { tab = it },
                    )
                    NavItem.Settings -> PlaceholderScreen(
                        title = "设置",
                        onBack = { tab = NavItem.Home },
                        navItem = NavItem.Settings,
                        onNav = { tab = it },
                    )
                }
            }
        }
    }
}

/**
 * 建设中占位页：顶栏 + 居中提示（TODO: 替换为各页真实实现）。
 * [navItem] 非空时在底部渲染导航条（主 tab 页），空则仅顶栏（子页）。
 * [actionLabel]/[onAction] 非空时在占位提示下方渲染一个入口按钮（如「我的角色」）。
 */
@Composable
private fun PlaceholderScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    navItem: NavItem? = null,
    onNav: ((NavItem) -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(AppTheme.BgDeepest, AppTheme.BgMid))
            ),
    ) {
        AppTopBar(title = title, onBack = onBack)
        Box(
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "✦",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Gold.copy(alpha = 0.6f),
                )
                Text(
                    text = "建设中…",
                    fontSize = 14.sp,
                    color = AppTheme.Text2,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (actionLabel != null && onAction != null) {
                    NeonButton(
                        text = actionLabel,
                        onClick = onAction,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            }
        }
        if (navItem != null && onNav != null) {
            com.milan.game.ui.nav.GameNavBar(
                active = navItem,
                onSelect = onNav,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
