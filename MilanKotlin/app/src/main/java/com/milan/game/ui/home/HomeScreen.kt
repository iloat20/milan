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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.milan.game.services.CharacterDataEntry
import com.milan.game.ui.GameState
import com.milan.game.ui.components.GlassDialog
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.components.NeonButton
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.components.PortraitTarget
import com.milan.game.ui.components.SealStamp
import com.milan.game.ui.nav.GameNavBar
import com.milan.game.ui.nav.NavItem
import com.milan.game.ui.nav.ResourceBar
import com.milan.game.ui.effects.FluidBackground
import com.milan.game.ui.theme.AppTheme

/**
 * 主页（水墨国风版）。
 * 资源栏 / 主视觉（Hero 光晕 + 立绘漂浮 + 铭牌）/ 动画按钮 / 丹青名录横滑条 / 底部导航。
 * 立绘经 PortraitImage 按角色 CharacterId 动态加载（drawable/char_<rarity>_<pinyin>.webp），
 * 缺图自动回退首字占位；R8 保留规则见 res/raw/keep.xml。
 */
@Composable
fun HomeScreen(
    onNav: (NavItem) -> Unit,
    onOpenGacha: () -> Unit,
    onOpenCollection: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    onOpenTower: () -> Unit = {},
    onOpenAchievements: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val titleAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(500),
        label = "titleAlpha",
    )
    val titleOffset by animateFloatAsState(
        targetValue = if (entered) 0f else 12f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "titleOffset",
    )

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        FluidBackground(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            // 资源栏：logo + 资源胶囊
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "丹青录",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.Gold,
                        letterSpacing = 2.6.sp,
                        modifier = Modifier.graphicsLayer {
                            alpha = titleAlpha
                            translationY = titleOffset.dp.toPx()
                        },
                    )
                    Text(
                        text = "山海经·神话卡牌",
                        fontSize = 10.sp,
                        color = AppTheme.Text2,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                ResourceBar()
            }

            // 主体：滚动
            Box(Modifier.weight(1f)) {
                val container = LocalWindowInfo.current.containerSize
                val heroHeight =
                    if (container.width > container.height) 300.dp else 430.dp
                val homeListState = rememberLazyListState()
                // 水墨视差：滚动进度驱动 Hero 滞后
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
                        start = 14.dp, top = 6.dp, end = 14.dp, bottom = 8.dp
                    ),
                ) {
                    item { Hero(heroHeight, heroParallax) }
                    item { Spacer(Modifier.height(10.dp)) }
                    item { HeroButtons(onOpenGacha, onOpenCollection, onOpenTower, onOpenAchievements) }
                    item { Spacer(Modifier.height(14.dp)) }
                    item { HomeSectionTitle("丹青名录", "ROSTER") }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { AvatarStrip(onOpenCharacter) }
                    item {
                        Text(
                            text = "✦ 立绘皆源自山海经与中国上古神话，依各自背景故事创作",
                            fontSize = 10.sp,
                            color = AppTheme.Text2,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                    }
                }
            }

            // 底部导航
            GameNavBar(
                active = NavItem.Home,
                onSelect = onNav,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        // 上次异常退出现场回显
        CrashDialogIfAny()

        // 朱印装饰：右上角
        SealStamp(
            text = "丹",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 52.dp, end = 14.dp)
                .alpha(0.55f),
        )
    }
}

// ── 主视觉：角色大图 + 底部铭牌 ──

@Composable
private fun Hero(fixedH: androidx.compose.ui.unit.Dp, scrollProgress: Float = 0f) {
    // 快照 revision 驱动：抽到更高稀有度角色后回主页，主视觉随之更新
    val snap by GameState.snapshot.collectAsStateWithLifecycle()
    val def = remember(snap.revision) { featuredCharacter() }
    val rarityColor = AppTheme.rarityColor(def.baseRarity)

    // 水墨视差：Hero 立绘以 0.4x 速率滚动，与内容层产生宣纸深度感
    val parallaxOffset = scrollProgress * 0.4f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(fixedH)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(18.dp))
            .background(AppTheme.Surface),
    ) {
        // 稀有度光晕（脉动，位于立绘之后）
        Halo(rarityColor, Modifier.fillMaxSize())

        // 主视觉立绘：稀有度光晕之上叠真实立绘（缺图自动回退首字占位）
        HeroPortrait(def, rarityColor, Modifier.fillMaxSize().graphicsLayer { translationY = parallaxOffset })

        // 底部暗化渐变 + 铭牌
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            AppTheme.BgDeepest.copy(alpha = 0f),
                            AppTheme.BgDeepest.copy(alpha = 0.5f),
                            AppTheme.BgDeepest.copy(alpha = 0.9f),
                        )
                    )
                )
                .padding(start = 18.dp, top = 48.dp, end = 18.dp, bottom = 16.dp),
        ) {
            // 顶部金色渐隐分隔线（水墨画轴风格）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, AppTheme.Gold.copy(alpha = 0.63f), Color.Transparent)
                        )
                    ),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = def.displayName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Text1,
            )
            Text(
                text = def.title,
                fontSize = 12.sp,
                color = AppTheme.Text2,
                modifier = Modifier.padding(top = 3.dp),
            )
            // 标签行：稀有度 + 世界
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = AppTheme.rarityName(def.baseRarity),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = rarityColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(rarityColor.copy(alpha = 0.27f))
                        .border(1.dp, rarityColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = def.world,
                    fontSize = 11.sp,
                    color = AppTheme.Text2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppTheme.Surface)
                        .border(1.dp, AppTheme.Stroke, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
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

/** 主视觉下方动作钮：金箔召唤 + 石青图鉴；次行无尽之塔 / 成就双入口。 */
@Composable
private fun HeroButtons(
    onOpenGacha: () -> Unit,
    onOpenCollection: () -> Unit,
    onOpenTower: () -> Unit,
    onOpenAchievements: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GoldButton("✦ 前往召唤", Modifier.weight(1f), onOpenGacha)
            Spacer(Modifier.width(12.dp))
            NeonButton("丹青图鉴", Modifier.weight(1f), onOpenCollection)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeonButton("♾ 无尽之塔", Modifier.weight(1f), onOpenTower)
            Spacer(Modifier.width(12.dp))
            NeonButton("✦ 成 就", Modifier.weight(1f), onOpenAchievements)
        }
    }
}

// ── 丹青名录：统一头像横滑 ──

/** 名录优先精选：运行时校验存在性——内容表 id 失配时该项跳过并按稀有度降序补足。 */
private val PickIds = listOf(
    "char_ur_zhulong", "char_ur_xingtian", "char_ssr_fenghuang",
    "char_sr_bifang", "char_sr_jingwei", "char_ssr_leishen",
)

/** 名录条目：解析后的内容定义 + 展示文案。 */
private data class AvatarEntry(
    val def: CharacterDataEntry,
    val name: String,
    val source: String,
)

@Composable
private fun rememberAvatarEntries(): List<AvatarEntry> {
    // 快照 revision 触发重算：新抽到角色也能进入名录补足序列
    val snap by GameState.snapshot.collectAsStateWithLifecycle()
    return remember(snap.revision) {
        val all = GameState.service.characters
        val byId = all.associateBy { it.characterId }
        val picked = PickIds.mapNotNull { byId[it] }
        val fill = all.filter { it.characterId !in PickIds.toSet() }
            .sortedByDescending { it.baseRarity }
        (picked + fill).take(6).map { def ->
            AvatarEntry(
                def = def,
                name = def.displayName.substringBefore(' '),
                source = AppTheme.rarityName(def.baseRarity),
            )
        }
    }
}

@Composable
private fun AvatarStrip(onOpenCharacter: (String) -> Unit) {
    val entries = rememberAvatarEntries()
    if (entries.isEmpty()) return
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(entries, key = { it.def.characterId }) { e ->
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
            Column(
                modifier = Modifier
                    .clickable { onOpenCharacter(e.def.characterId) }
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
            target = PortraitTarget.Thumb,
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
                .clip(RoundedCornerShape(2.dp))
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

// ── 主视觉角色选取 ──

private fun featuredCharacter(): CharacterDataEntry {
    // 存档角色可能在内容表查不到：先取已拥有最高稀有度（Def 非空）
    val best = GameState.owned()
        .filter { it.def != null }
        .maxByOrNull { it.rarity }
        ?.def
    if (best != null) return best

    val any = GameState.service.characters.firstOrNull()
    if (any != null) return any

    // 内容表彻底为空时的最后防线：占位角色，宁可难看也不能崩。
    return CharacterDataEntry(
        characterId = "placeholder",
        displayName = "未知存在",
        title = "数据缺失",
        world = "Shinwa",
        element = "Flame",
        baseRarity = 1,
        baseStats = listOf(10, 10, 100, 10),
    )
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
