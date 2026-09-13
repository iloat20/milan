package com.milan.game.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.infrastructure.CrashReporter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.milan.game.services.CharacterDataEntry
import com.milan.game.ui.components.GlassDialog
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.components.InkButton
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.components.PortraitTarget
import com.milan.game.ui.components.CardMetrics
import com.milan.game.ui.nav.GameNavBar
import com.milan.game.ui.nav.NavItem
import com.milan.game.ui.nav.ResourceBar
import com.milan.game.ui.effects.FluidBackground
import com.milan.game.ui.effects.inkSplash
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.BrandType
import com.milan.game.ui.theme.LocalWorldPalette
import com.milan.game.ui.theme.WorldTheme

/**
 * 主页（v4）。
 *
 * 布局：**全幅主视觉**（品牌条叠在立绘上）→ 下方留白区：快捷入口网格 + 英灵名录。
 */
@Composable
fun HomeScreen(
    onNav: (NavItem) -> Unit,
    onOpenGacha: () -> Unit,
    onOpenCollection: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    onOpenTower: () -> Unit = {},
    onOpenAchievements: () -> Unit = {},
    onOpenStory: () -> Unit = {},
    onOpenDailyMissions: () -> Unit = {},
    onOpenBattlePass: () -> Unit = {},
    onOpenAffinity: () -> Unit = {},
    onOpenArena: () -> Unit = {},
    onOpenEvent: () -> Unit = {},
    onOpenDungeon: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm: HomeViewModel = viewModel(factory = com.milan.game.di.AppGraph.factory)
    val homeUi by vm.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        val featuredWorld = WorldTheme.forWorld(homeUi.featured.world)
        androidx.compose.runtime.CompositionLocalProvider(
            LocalWorldPalette provides featuredWorld,
        ) {
            FluidBackground(Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize()) {
                val container = LocalWindowInfo.current.containerSize
                val heroHeight = if (container.width > container.height) 380.dp else 500.dp
                val homeListState = rememberLazyListState()
                val heroParallax by remember {
                    derivedStateOf {
                        val first = homeListState.layoutInfo.visibleItemsInfo.firstOrNull()
                        if (first != null && first.index == 0) {
                            first.offset.toFloat() / first.size.coerceAtLeast(1)
                        } else 0f
                    }
                }

                LazyColumn(
                    state = homeListState,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                ) {
                    // 全幅主视觉：品牌条叠在顶部，无左右边距
                    item {
                        Box(Modifier.fillMaxWidth()) {
                            Hero(
                                fixedH = heroHeight,
                                scrollProgress = heroParallax,
                                def = homeUi.featured,
                                onOpenCharacter = onOpenCharacter,
                                onOpenGacha = onOpenGacha,
                            )
                            Row(
                                modifier = Modifier
                                    .statusBarsPadding()
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(text = "织环", style = BrandType, color = AppTheme.Text1)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "MILAN",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppTheme.Text3,
                                    letterSpacing = 1.5.sp,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                Spacer(Modifier.weight(1f))
                                ResourceBar(compact = true)
                            }
                        }
                    }

                    item { Spacer(Modifier.height(28.dp)) }

                    item {
                        HomeSectionTitle("快捷入口")
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                    item {
                        QuickActions(
                            onOpenCollection, onOpenTower, onOpenStory,
                            onOpenDailyMissions, onOpenBattlePass, onOpenAffinity,
                            onOpenAchievements, onOpenArena, onOpenEvent, onOpenDungeon,
                        )
                    }

                    item { Spacer(Modifier.height(28.dp)) }

                    item { HomeSectionTitle("英灵名录") }
                    item { Spacer(Modifier.height(14.dp)) }
                    item { AvatarStrip(homeUi.avatarEntries, onOpenCharacter, onOpenCollection) }
                    item { Spacer(Modifier.height(12.dp)) }
                    item {
                        Text(
                            text = "英灵皆源自山海经与中国上古神话，依各自背景故事创作",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.Text3,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }

                GameNavBar(
                    active = NavItem.Home,
                    onSelect = onNav,
                )
            }
        }

        CrashDialogIfAny()
    }
}

// ── 主视觉：立绘居中 + 铭牌 + 底部 CTA（v4 去环纹/去呼吸光晕）──

@Composable
private fun Hero(
    fixedH: androidx.compose.ui.unit.Dp,
    scrollProgress: Float = 0f,
    def: CharacterDataEntry,
    onOpenCharacter: (String) -> Unit,
    onOpenGacha: () -> Unit,
) {
    val rarityColor = AppTheme.rarityColor(def.baseRarity)
    val parallaxOffset = scrollProgress * 0.35f

    Box(modifier = Modifier.fillMaxWidth().height(fixedH)) {
        Box(modifier = Modifier.matchParentSize().background(AppTheme.BgDeepest)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(rarityColor.copy(alpha = 0.16f), Color.Transparent),
                            radius = 900f,
                        )
                    ),
            )
            HeroPortrait(def, rarityColor, Modifier.fillMaxSize().graphicsLayer { translationY = parallaxOffset })

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, top = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = def.world,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.Text2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppTheme.Roundness.sm))
                        .background(AppTheme.BgDeepest.copy(alpha = 0.72f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Text(
                    text = AppTheme.rarityName(def.baseRarity),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (def.baseRarity >= 4) AppTheme.GoldTextOn else AppTheme.Text1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppTheme.Roundness.sm))
                        .background(rarityColor)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                AppTheme.BgDeepest.copy(alpha = 0.72f),
                                AppTheme.BgDeepest.copy(alpha = 0.98f),
                            )
                        )
                    )
                    .padding(start = 20.dp, top = 48.dp, end = 20.dp, bottom = 18.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onOpenCharacter(def.characterId) },
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = def.displayName,
                            style = MaterialTheme.typography.displaySmall,
                            color = AppTheme.Text1,
                        )
                        Text(
                            text = def.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.Text2,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(
                        text = "详情 ›",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = AppTheme.Frost,
                    )
                }
                Spacer(Modifier.height(16.dp))
                GoldButton(
                    text = "召  灵",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenGacha,
                )
            }
        }
    }
}

/** 旧 Halo 入口保留签名，内部已简化为无动画（v4）。 */
@Composable
private fun Halo(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.14f), Color.Transparent),
                        radius = 900f,
                    )
                ),
        )
    }
}

/** 主视觉立绘：稀有度渐变底 + 真实立绘（浮动缓动）。
 * 立绘资源名 = 角色 CharacterId（drawable/char_<rarity>_<pinyin>.webp），
 * 经 PortraitImage 动态加载，缺图自动回退首字占位。 */
@Composable
private fun HeroPortrait(def: CharacterDataEntry, rarityColor: Color, modifier: Modifier = Modifier) {
    // v4：去掉立绘漂浮呼吸，画面更静
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(rarityColor.copy(alpha = 0.18f), Color.Transparent),
                        radius = 1200f,
                    )
                ),
        )
        PortraitImage(
            characterId = def.characterId,
            rarity = def.baseRarity,
            name = def.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            aura = true,
        )
    }
}

/** 快捷入口：5×2 网格，更易扫读。 */
@Composable
private fun QuickActions(
    onOpenCollection: () -> Unit,
    onOpenTower: () -> Unit,
    onOpenStory: () -> Unit,
    onOpenDailyMissions: () -> Unit,
    onOpenBattlePass: () -> Unit,
    onOpenAffinity: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenArena: () -> Unit,
    onOpenEvent: () -> Unit,
    onOpenDungeon: () -> Unit,
) {
    data class Action(val label: String, val onClick: () -> Unit)
    val actions = listOf(
        Action("环痕", onOpenCollection),
        Action("爬塔", onOpenTower),
        Action("剧情", onOpenStory),
        Action("日常", onOpenDailyMissions),
        Action("纪行", onOpenBattlePass),
        Action("好感", onOpenAffinity),
        Action("成就", onOpenAchievements),
        Action("竞技", onOpenArena),
        Action("活动", onOpenEvent),
        Action("深渊", onOpenDungeon),
    )
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        actions.chunked(5).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { a ->
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(AppTheme.Roundness.md))
                            .background(AppTheme.BgMid)
                            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(AppTheme.Roundness.md))
                            .clickable(interactionSource = interaction, indication = null, onClick = a.onClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = a.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = AppTheme.Text1,
                        )
                    }
                }
            }
        }
    }
}

// ── 环痕名录：统一头像横滑 ──

/** 名录优先精选：运行时校验存在性——内容表 id 失配时该项跳过并按稀有度降序补足。 */

@Composable
private fun AvatarStrip(
    entries: List<HomeAvatarEntry>,
    onOpenCharacter: (String) -> Unit,
    onOpenCollection: () -> Unit,
) {
    if (entries.isEmpty()) return
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(entries, key = { it.def.characterId }, contentType = { "avatarEntry" }) { e ->
            val idx = entries.indexOf(e)
            var itemReady by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(idx * 60L)
                itemReady = true
            }
            val itemAlpha by animateFloatAsState(
                targetValue = if (itemReady) 1f else 0f,
                animationSpec = tween(300),
                label = "itemAlpha",
            )
            val itemOffset by animateFloatAsState(
                targetValue = if (itemReady) 0f else 10f,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
                label = "itemOffset",
            )
            val avatarInteraction = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .inkSplash(avatarInteraction)
                    .clickable(interactionSource = avatarInteraction, indication = null) { onOpenCharacter(e.def.characterId) }
                    .padding(end = 12.dp)
                    .graphicsLayer {
                        alpha = itemAlpha
                        translationY = itemOffset.dp.toPx()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AvatarCircle(e.def, Modifier.size(58.dp))
                Text(
                    text = e.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Text1,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(text = e.source, style = MaterialTheme.typography.labelSmall, color = AppTheme.Text2)
            }
        }
        // 尾部「查看更多」指示器：进环痕图鉴（勿再走空 id 详情，会落到 MissingCharacter）
        item(key = "moreIndicator", contentType = { "moreIndicator" }) {
            val moreInteraction = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .inkSplash(moreInteraction)
                    .clickable(interactionSource = moreInteraction, indication = null) { onOpenCollection() }
                    .padding(start = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(AppTheme.SurfaceNested)
                        .border(1.dp, AppTheme.Stroke, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium,
                        color = AppTheme.Frost,
                    )
                }
                Text(
                    text = "查看全部",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = AppTheme.Frost,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/** 名录头像：稀有度色环 + 立绘。 */
@Composable
private fun AvatarCircle(def: CharacterDataEntry, modifier: Modifier = Modifier) {
    val c = AppTheme.rarityColor(def.baseRarity)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(AppTheme.BgDeepest)
                .border(1.5.dp, c.copy(alpha = 0.7f), CircleShape),
        )
        PortraitImage(
            characterId = def.characterId,
            rarity = def.baseRarity,
            name = def.displayName,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            target = PortraitTarget.Avatar,
        )
    }
}

// ── 小标题：环痕标记 + 标题 + 英文副标（织环台） ──

@Composable
private fun HomeSectionTitle(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 2.dp, height = 14.dp)
                .background(AppTheme.ZhuSha),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.Text1,
        )
    }
}

// ── 上次异常退出现场回显 ──

@Composable
private fun CrashDialogIfAny() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.IO) {
            var r = CrashReporter.readAndClear() ?: ""
            if (r.isEmpty() && CrashReporter.previousBootIncomplete()) {
                r = "未捕获到托管异常，但上次启动未走完流程 —— 疑似 native 层崩溃。\n\n" +
                    "上次启动面包屑：\n" + (CrashReporter.previousBootTrace() ?: "(无)")
            }
            r
        }
        if (r.isNotEmpty()) {
            report = r
            show = true
        }
        CrashReporter.boot("home.oncreate.done")
    }

    GlassDialog(
        show = show,
        onDismiss = { show = false },
        title = "上次异常退出的现场",
        body = report.take(3000),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            InkButton(
                text = "复制",
                color = AppTheme.Frost,
                onClick = {
                    try {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("milan-crash", report))
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                    }
                    show = false
                },
            )
            InkButton(text = "关闭", color = AppTheme.Text2, onClick = { show = false })
        }
    }
}
