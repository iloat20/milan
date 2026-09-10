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
import androidx.compose.ui.draw.scale
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
import com.milan.game.ui.components.NeonButton
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

/**
 * 主页 · 丹青典藏馆大厅（2026-09-09 重设计）。
 *
 * 版式：品牌条 → 主视觉展柜（Hero）→ 主 CTA + 快捷入口横滑 → 名录横滑 → 底部导航。
 * 刻意去掉 2×4 文字按钮墙，改「一条主行动 + 可横滑次要入口」，首屏焦点只在展柜。
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
    modifier: Modifier = Modifier,
) {
    val vm: HomeViewModel = viewModel(factory = com.milan.game.di.AppGraph.factory)
    val homeUi by vm.uiState.collectAsStateWithLifecycle()
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val titleAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(500),
        label = "titleAlpha",
    )

    Box(modifier = modifier.fillMaxSize()) {
        FluidBackground(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            // ── 品牌条：左 Logo + 右资源 ──
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "丹青录",
                    style = BrandType,
                    color = AppTheme.Gold,
                    modifier = Modifier.graphicsLayer { alpha = titleAlpha },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "COLLECTION",
                    fontSize = 9.sp,
                    color = AppTheme.Text3,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Spacer(Modifier.weight(1f))
                ResourceBar(compact = true)
            }

            Box(Modifier.weight(1f)) {
                val container = LocalWindowInfo.current.containerSize
                val heroHeight = if (container.width > container.height) 320.dp else 400.dp
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, top = 4.dp, end = 16.dp, bottom = 12.dp
                    ),
                ) {
                    // 展柜主视觉
                    item { Hero(heroHeight, heroParallax, homeUi.featured, onOpenCharacter) }
                    item { Spacer(Modifier.height(14.dp)) }

                    // 主 CTA：召唤（唯一金色大按钮）
                    item {
                        GoldButton(
                            text = "前往召唤",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpenGacha,
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }

                    // 快捷入口：横滑 chip（次要行动不抢首屏）
                    item {
                        QuickActions(
                            onOpenCollection, onOpenTower, onOpenStory,
                            onOpenDailyMissions, onOpenBattlePass, onOpenAffinity,
                            onOpenAchievements, onOpenArena, onOpenEvent,
                        )
                    }
                    item { Spacer(Modifier.height(18.dp)) }

                    // 名录
                    item { HomeSectionTitle("丹青名录", "ROSTER") }
                    item { Spacer(Modifier.height(10.dp)) }
                    item { AvatarStrip(homeUi.avatarEntries, onOpenCharacter, onOpenCollection) }
                    item { Spacer(Modifier.height(8.dp)) }
                    item {
                        Text(
                            text = "立绘皆源自山海经与中国上古神话，依各自背景故事创作",
                            fontSize = 10.sp,
                            color = AppTheme.Text3,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                }
            }

            GameNavBar(
                active = NavItem.Home,
                onSelect = onNav,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        CrashDialogIfAny()
    }
}

// ── 主视觉：展柜式大图 ──

@Composable
private fun Hero(
    fixedH: androidx.compose.ui.unit.Dp,
    scrollProgress: Float = 0f,
    def: CharacterDataEntry,
    onOpenCharacter: (String) -> Unit,
) {
    val rarityColor = AppTheme.rarityColor(def.baseRarity)
    val parallaxOffset = scrollProgress * 0.4f

    Box(modifier = Modifier.fillMaxWidth().height(fixedH)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(CardMetrics.Corner))
                .background(AppTheme.BgMid)
                .border(1.dp, AppTheme.Stroke, RoundedCornerShape(CardMetrics.Corner)),
        ) {
            Halo(rarityColor, Modifier.fillMaxSize())
            HeroPortrait(def, rarityColor, Modifier.fillMaxSize().graphicsLayer { translationY = parallaxOffset })

            // 底部玻璃铭牌（信息托底，可读）
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                AppTheme.BgDeepest.copy(alpha = 0f),
                                AppTheme.BgDeepest.copy(alpha = 0.72f),
                                AppTheme.BgDeepest.copy(alpha = 0.94f),
                            )
                        )
                    )
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        onOpenCharacter(def.characterId)
                    }
                    .padding(start = 16.dp, top = 40.dp, end = 16.dp, bottom = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = def.displayName,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                        color = AppTheme.Text1,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = AppTheme.rarityName(def.baseRarity),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (def.baseRarity >= 4) AppTheme.GoldTextOn else AppTheme.BgDeepest,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(rarityColor)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = def.title,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = AppTheme.Text2,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "查看详情 ›",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppTheme.Gold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** 稀有度径向光晕：呼吸脉动（水墨画意韵）。 */
@Composable
private fun Halo(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "halo")
    val scale by t.animateFloat(
        initialValue = 1f, targetValue = 1.22f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "haloScale",
    )
    val alpha by t.animateFloat(
        initialValue = 0.30f, targetValue = 0.62f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "haloAlpha",
    )
    Box(
        modifier = modifier
            .alpha(alpha)
            .scale(scale)
            .background(
                Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.45f), Color.Transparent),
                    radius = 900f,
                )
            ),
    )
}

/** 主视觉立绘：稀有度渐变底 + 真实立绘（浮动缓动）。
 * 立绘资源名 = 角色 CharacterId（drawable/char_<rarity>_<pinyin>.webp），
 * 经 PortraitImage 动态加载，缺图自动回退首字占位。 */
@Composable
private fun HeroPortrait(def: CharacterDataEntry, rarityColor: Color, modifier: Modifier = Modifier) {
    // 立绘漂浮（±10dp 缓动，伪 3D 呼吸感）
    val t = rememberInfiniteTransition(label = "float")
    val offsetY by t.animateFloat(
        initialValue = 0f, targetValue = -10f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "floatY",
    )
    Box(
        modifier = modifier.offset { IntOffset(0, offsetY.dp.roundToPx()) },
        contentAlignment = Alignment.Center,
    ) {
        // 稀有度渐变底（立绘为透明底 PNG 时提供视觉支撑）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(rarityColor.copy(alpha = 0.28f), Color.Transparent),
                        radius = 1400f,
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

/**
 * 快捷入口：横滑 chip。首屏不堆 8 个大按钮，主 CTA 已给「召唤」。
 */
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
) {
    data class Action(val label: String, val onClick: () -> Unit)
    val actions = listOf(
        Action("图鉴", onOpenCollection),
        Action("爬塔", onOpenTower),
        Action("剧情", onOpenStory),
        Action("日常", onOpenDailyMissions),
        Action("纪行", onOpenBattlePass),
        Action("好感", onOpenAffinity),
        Action("成就", onOpenAchievements),
        Action("竞技", onOpenArena),
        Action("活动", onOpenEvent),
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp),
    ) {
        items(actions.size) { i ->
            val a = actions[i]
            val interaction = remember { MutableInteractionSource() }
            Text(
                text = a.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AppTheme.Text1,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppTheme.Roundness.lg))
                    .background(AppTheme.BgMid)
                    .border(1.dp, AppTheme.Stroke, RoundedCornerShape(AppTheme.Roundness.lg))
                    .inkSplash(interaction)
                    .clickable(interactionSource = interaction, indication = null, onClick = a.onClick)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

// ── 丹青名录：统一头像横滑 ──

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
            .padding(horizontal = 14.dp, vertical = 2.dp),
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
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Text1,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(text = e.source, fontSize = 8.sp, color = AppTheme.Text2)
            }
        }
        // 尾部「查看更多」指示器：进神谱图鉴（勿再走空 id 详情，会落到 MissingCharacter）
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
                // 圆形占位 + 右箭头
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(AppTheme.Surface)
                        .border(1.5.dp, AppTheme.Gold.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "›",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.Gold,
                    )
                }
                Text(
                    text = "查看全部",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Gold,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/** 名录头像：水墨笔触圆环 + 真实立绘（缺图自动回退首字）。 */
@Composable
private fun AvatarCircle(def: CharacterDataEntry, modifier: Modifier = Modifier) {
    val c = AppTheme.rarityColor(def.baseRarity)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // 水墨笔触圆环：多层不同粗细/透明度的环叠加，模拟毛笔蘸墨的自然笔触
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = minOf(cx, cy)
            // 外环：浓墨主笔
            drawCircle(
                color = c.copy(alpha = 0.75f),
                radius = maxR,
                style = Stroke(width = 3.dp.toPx()),
            )
            // 中环：淡墨渗化
            drawCircle(
                color = c.copy(alpha = 0.35f),
                radius = maxR - 2.dp.toPx(),
                style = Stroke(width = 1.5.dp.toPx()),
            )
            // 内环：极淡水痕
            drawCircle(
                color = c.copy(alpha = 0.15f),
                radius = maxR - 4.dp.toPx(),
                style = Stroke(width = 0.8.dp.toPx()),
            )
        }
        // 背底：浓墨深色衬托
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(AppTheme.BgDeepest),
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

// ── 小标题：左金线 + 标题 + 英文副标（水墨画轴风格） ──

@Composable
private fun HomeSectionTitle(title: String, en: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(15.dp)
                .clip(RoundedCornerShape(AppTheme.Roundness.xxs))
                .background(AppTheme.GoldHi),
        )
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.Text1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp),
        )
        Text(
            text = en,
            fontSize = 9.sp,
            color = AppTheme.Text2,
            letterSpacing = 1.sp,
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
            NeonButton(
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
            NeonButton(text = "关闭", color = AppTheme.Text2, onClick = { show = false })
        }
    }
}
