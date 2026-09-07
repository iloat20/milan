package com.milan.game.ui.gacha

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import com.milan.game.infrastructure.HapticManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.milan.game.domain.progression.EconomyFormulas
import com.milan.game.ai.FortuneAgentRegistry
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milan.game.infrastructure.CrashReporter
import com.milan.game.infrastructure.MilanAudio
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.GachaPoolDataEntry
import com.milan.game.services.PullOutcome
import com.milan.game.services.PullResult
import com.milan.game.ui.GameState
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.components.NeonButton
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.components.PortraitTarget
import com.milan.game.ui.nav.GameNavBar
import com.milan.game.ui.nav.ResourceBar
import com.milan.game.ui.nav.NavItem
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.effects.GachaBeamParticles
import com.milan.game.ui.effects.GachaStarBurst
import com.milan.game.ui.effects.RarityMeshGradient
import com.milan.game.ui.effects.inkSplash
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 寻访（抽卡）屏（水墨国风版）。
 *
 * 结构：标题 + 资源胶囊 → 卡池信息（概率 / 保底进度）→ 池角色横排预览 →
 * 召唤法阵（水墨丹青）→ 单抽 / 十连 → 摘要行 → 5 列结果网格（逐张缩放淡入）→ 底部导航。
 *
 * 演出：法阵脉冲 → 稀有度白闪 → 大立绘卡弹出（可整屏点击跳过）→ 展示结果。
 */
@Composable
fun GachaScreen(
    onNav: (NavItem) -> Unit,
    onOpenCharacter: (String) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    testMode: Boolean = false,
) {
    val context = LocalContext.current
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    // 多卡池支持：选中态 rememberSaveable 持久化；内容表改动导致旧 id 失配时回落首池
    val pools = remember { GameState.service.pools }
    var selectedPoolId by rememberSaveable { mutableStateOf(pools.firstOrNull()?.poolId.orEmpty()) }
    val pool = pools.firstOrNull { it.poolId == selectedPoolId } ?: pools.firstOrNull()
    // 订阅状态快照——保底进度/余额/振动开关从快照派生
    val snap by GameState.snapshot.collectAsStateWithLifecycle()

    val pity = pool?.let { snap.pityByPool[it.poolId] } ?: 0
    var busy by remember { mutableStateOf(false) }
    // 结果列表与摘要用 rememberSaveable——旋转后恢复
    var results by rememberSaveable(stateSaver = PullResultsSaver) {
        mutableStateOf<List<PullResult>>(emptyList())
    }
    var summary by rememberSaveable { mutableStateOf("") }
    var batch by remember { mutableIntStateOf(0) }
    // 演出状态聚合
    var reveal by remember { mutableStateOf(RevealUiState()) }
    var entered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { entered = true }

    /** 触觉反馈（统一委托 HapticManager）。 */
    fun buzz(effect: Int) {
        if (!snap.vibrationEnabled) return
        try {
            val view = (context as? android.app.Activity)?.window?.decorView ?: return
            HapticManager.performHapticFeedback(view, effect)
        } catch (_: Exception) { }
    }

    /** 演出结束 / 跳过：展示结果、复位状态。 */
    fun finishReveal() {
        val list = reveal.staged
        if (list != null) {
            results = list
            summary = buildSummary(list)
            batch++
        }
        reveal = RevealUiState(token = reveal.token)
        busy = false
    }

    /** 跳过演出：仅递增 token 作废挂起编排。
     *  finishReveal 由动画协程检测 token 变化后调用——
     *  避免 busy=false 在协程退出前被复位，引发新旧 doPull 并发。 */
    fun skipReveal() {
        if (!busy || !reveal.visible) return
        reveal = reveal.copy(token = reveal.token + 1)
    }

    // 演出中系统返回拦截——转跳过演出
    BackHandler(enabled = reveal.visible) { skipReveal() }

    /** 抽卡入口：余额检查 → pull → 兜底 → 演出编排。 */
    fun doPull(tenPull: Boolean) {
        if (busy) return
        val p = pool ?: return
        val cost = if (tenPull) p.tenCost else p.singleCost
        if (snap.softCurrency < cost) {
            scope.launch { feedback.show("星尘不足") }
            return
        }
        busy = true
        scope.launch {
            try {
                val pulled = when (val outcome = GameState.service.pull(p.poolId, tenPull)) {
                    is PullOutcome.Success -> outcome.results
                    is PullOutcome.Rejected -> {
                        busy = false
                        feedback.show("抽卡失败，请重试")
                        try { CrashReporter.boot("gacha.pull.rejected poolId=${p.poolId}") } catch (_: Exception) { }
                        return@launch
                    }
                    is PullOutcome.SaveFailed -> {
                        busy = false
                        feedback.show("保存失败，请重试")
                        try { CrashReporter.boot("gacha.pull.saveFailed poolId=${p.poolId}") } catch (_: Exception) { }
                        return@launch
                    }
                }
                val best = pulled.maxByOrNull { it.rarity }
                if (best == null || best.characterId == null) {
                    busy = false
                    feedback.show("抽卡失败，请重试")
                    try { CrashReporter.boot("gacha.pull.empty poolId=${p.poolId}") } catch (_: Exception) { }
                    return@launch
                }
                val bestDef = best.characterId.let { GameState.service.character(it) }
                val token = reveal.token + 1
                // 按稀有度分级触觉反馈：UR=丰富振动，SSR=CONFIRM，SR=KEYBOARD_TAP，R=CLOCK_TICK
                when (best.rarity) {
                    4 -> HapticManager.gachaUrSpecial(context)
                    3 -> buzz(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
                    2 -> buzz(HapticFeedbackConstants.KEYBOARD_TAP)
                    else -> buzz(HapticFeedbackConstants.CLOCK_TICK)
                }
                MilanAudio.playSfx("gacha_pull")
                // 稀有度分级演出时长：UR 拉满仪式感，R 快速过场
                val chargeMs = when (best.rarity) {
                    4 -> 900L   // UR：浓墨蓄力
                    3 -> 600L   // SSR：金箔凝聚
                    2 -> 420L   // SR：默认水墨
                    else -> 300L // R：快速闪烁
                }
                val beamMs = when (best.rarity) {
                    4 -> 640L   // UR：光柱扩展
                    3 -> 520L   // SSR：金箔光柱
                    else -> 480L // R/SR：标准
                }
                val revealMs = when {
                    tenPull -> when (best.rarity) {
                        4 -> 4800L  // UR 十连：加长展示
                        3 -> 4000L  // SSR 十连
                        else -> 3600L
                    }
                    else -> when (best.rarity) {
                        4 -> 3000L  // UR 单抽：仪式感
                        3 -> 2400L  // SSR 单抽
                        2 -> 1500L  // SR 单抽
                        else -> 1000L // R 单抽：一闪而过
                    }
                }
                // 阶段一：蓄能
                reveal = RevealUiState(
                    token = token,
                    staged = pulled,
                    def = bestDef,
                    rarity = best.rarity,
                    flashColor = AppTheme.rarityColor(best.rarity),
                    fortune = bestDef?.let { FortuneAgentRegistry.activeAgent.fortune(it, it.characterId.hashCode().toLong() + token) } ?: "",
                    stage = RevealStage.Charge,
                )
                delay(chargeMs)
                if (token != reveal.token) { finishReveal(); return@launch }
                // 阶段二：光柱爆发
                reveal = reveal.copy(stage = RevealStage.Beam, flashVisible = true)
                delay(beamMs)
                if (token != reveal.token) { finishReveal(); return@launch }
                reveal = reveal.copy(flashVisible = false)
                delay(120)
                if (token != reveal.token) { finishReveal(); return@launch }
                // 阶段三：揭晓
                reveal = reveal.copy(
                    cardIn = true,
                    visible = true,
                    stage = if (tenPull) RevealStage.Ten else RevealStage.Single,
                )
                MilanAudio.playSfx("gacha_reveal")
                if (best.rarity >= 3) {
                    val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
                    buzz(confirm)
                } else {
                    buzz(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                delay(revealMs)
                if (token != reveal.token) { finishReveal(); return@launch }
                // 阶段四：结果
                finishReveal()
            } catch (e: Exception) {
                busy = false
                feedback.show("抽卡异常，请重试")
                try { CrashReporter.traceNonFatal("gacha.pull.exception", e) } catch (_: Exception) { }
            }
        }
    }

    val entranceAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(450),
        label = "entrance",
    )
    val flashAlpha by animateFloatAsState(
        targetValue = if (reveal.flashVisible) 1f else 0f,
        animationSpec = tween(if (reveal.rarity >= 4) 560 else 420),
        label = "flash",
    )
    PageBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp)
                .verticalScroll(rememberScrollState())
                .graphicsLayer { alpha = entranceAlpha },
        ) {
            // ── 标题 + 资源胶囊 ──
            Row(Modifier.statusBarsPadding().fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "丹青寻访",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Gold,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.weight(1f))
                ResourceBar()
            }
            Spacer(Modifier.height(14.dp))

            if (pool != null) {
                // ── 池选择 chips ──
                if (pools.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pools.forEach { p ->
                            val selected = p.poolId == pool.poolId
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = p.displayName.ifEmpty { "常驻卡池" },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) AppTheme.GoldTextOn else AppTheme.Text2,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (selected) AppTheme.Gold else AppTheme.Surface)
                                        .border(1.dp, if (selected) AppTheme.Gold else AppTheme.Stroke, RoundedCornerShape(16.dp))
                                        .clickable { selectedPoolId = p.poolId }
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                )
                                // 选中池金色指示线
                                if (selected) {
                                    Box(
                                        Modifier
                                            .padding(top = 4.dp)
                                            .width(20.dp)
                                            .height(2.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(AppTheme.Gold),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // ── 卡池信息面板 ──
                GlassPanel(modifier = Modifier.fillMaxWidth(), highlighted = true) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = pool.displayName.ifEmpty { "常驻卡池" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.Gold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(ratesLabel(pool), fontSize = 11.sp, color = AppTheme.Text2)
                        if (pool.hardPity > 0) {
                            Spacer(Modifier.height(8.dp))
                            val softStart = EconomyFormulas.softPityStart(pool.hardPity)
                            val inSoft = softStart > 0 && pity >= softStart
                            // 即将到达软保底（差 5 抽内）：提前预警
                            val nearSoft = softStart > 0 && !inSoft && pity >= softStart - 5
                            val pityColor = when {
                                inSoft -> AppTheme.GoldHi
                                nearSoft -> AppTheme.Warning
                                else -> AppTheme.Frost
                            }
                            LinearProgressIndicator(
                                progress = { pity.toFloat() / pool.hardPity.coerceAtLeast(1) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = pityColor,
                                trackColor = AppTheme.Surface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = buildString {
                                    append("保底进度  $pity / ${pool.hardPity}")
                                    when {
                                        inSoft -> append(" · 软保底爬坡中 ↑↑")
                                        nearSoft -> {
                                            val remaining = softStart - pity
                                            append(" · 即将进入软保底（还差 $remaining 抽）")
                                        }
                                    }
                                },
                                fontSize = 11.sp,
                                color = pityColor,
                            )
                        }
                        // UP 定轨行
                        val upDef = pool.featuredCharacterId
                            .takeIf { it.isNotEmpty() }
                            ?.let { GameState.service.character(it) }
                        if (upDef != null) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "UP",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.GoldTextOn,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AppTheme.Gold)
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = upDef.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.Gold,
                                )
                                if (snap.featuredLostByPool[pool.poolId] == true) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "上次歪了 · 下次必中",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.Frost,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // ── 池角色预览：横排圆形头像 ──
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pool.entries, key = { it.characterId }, contentType = { "poolEntry" }) { entry ->
                        val def = GameState.service.character(entry.characterId)
                        val entryInteraction = remember { MutableInteractionSource() }
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .inkSplash(entryInteraction)
                                .clickable(interactionSource = entryInteraction, indication = null) { def?.let { onOpenCharacter(it.characterId) } }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PortraitImage(
                                characterId = entry.characterId,
                                rarity = entry.rarityIndex,
                                name = def?.displayName,
                                modifier = Modifier.size(52.dp).clip(CircleShape),
                                target = PortraitTarget.Thumb,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = def?.displayName ?: entry.characterId,
                                fontSize = 9.sp,
                                color = AppTheme.Text2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // ── 待机能量枢纽（水墨丹青法阵）──
            CyberHerald(modifier = Modifier.align(Alignment.CenterHorizontally), testMode = testMode)
            Spacer(Modifier.height(16.dp))

            Text(
                text = "─ 敕令開陣 ─",
                fontSize = 11.sp,
                color = AppTheme.Gold.copy(alpha = 0.6f),
                letterSpacing = 4.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(6.dp))

            // ── 单抽 / 十连 ──
            Row(Modifier.fillMaxWidth().padding(horizontal = 30.dp)) {
                // I3 修复：演出/结算期间禁用（此前按钮视觉如常但 doPull 首行 if(busy) return 静默吞点击）
                NeonButton("单 抽", Modifier.weight(1f), enabled = !busy, onClick = { doPull(false) })
                Spacer(Modifier.width(14.dp))
                GoldButton("十 连", Modifier.weight(1f), enabled = !busy, onClick = { doPull(true) })
            }
            Spacer(Modifier.height(10.dp))

            // ── 摘要行 ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary,
                    fontSize = 11.sp,
                    color = AppTheme.Text2,
                    modifier = Modifier.weight(1f),
                )
                if (results.isNotEmpty()) {
                    val shareInteraction = remember { MutableInteractionSource() }
                    Text(
                        text = "分享",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.Gold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .inkSplash(shareInteraction)
                            // U1：绘制 + 压缩 + 写盘已 suspend 化（移出主线程），此处在协程中调用
                            .clickable(interactionSource = shareInteraction, indication = null) { scope.launch { PullShareCard.shareResults(context, results) } }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Text(
                    text = "历 史 ›",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Frost,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenHistory)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))

            // ── 本次统计卡片 ──
            PullStatsPanel(
                results = results,
                pity = pity,
                hardPity = pool?.hardPity ?: 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))

            // ── 结果网格 ──
            // P5 修复：将 LazyColumn 替换为 Column——外层 Column 已有 verticalScroll，
            // 嵌套同方向可滚动容器（LazyColumn）在 Compose BOM 2026+ 会抛
            // IllegalStateException（"Nesting scrollable in the same direction"），
            // 十连结果 10 项触发布局计算，单抽 1 项刚好不触发。
            // 结果列表最多 10 项（2×5），Column 全量渲染无性能问题。
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                results.chunked(5).forEachIndexed { rowIdx, row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEachIndexed { i, r ->
                            key("${r.characterId}_${rowIdx}_$i") {
                                GachaChip(
                                    r = r,
                                    delayMs = (rowIdx * 5 + i) * 60,
                                    batch = batch,
                                    onOpen = { r.characterId?.let(onOpenCharacter) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            // ── 底部导航 ──
            GameNavBar(
                active = NavItem.Gacha,
                onSelect = onNav,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        // ── 稀有度白闪层 ──
        if (flashAlpha > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(reveal.flashColor)
                    .graphicsLayer { alpha = flashAlpha },
            )
        }

        // ── 粒子特效层（抽卡演出）──
        val isRevealActive = reveal.stage == RevealStage.Beam ||
            reveal.stage == RevealStage.Single ||
            reveal.stage == RevealStage.Ten
        if (isRevealActive && reveal.rarity >= 2) {
            // Mesh Gradient 背景（稀有度驱动的动态渐变）
            RarityMeshGradient(
                rarity = reveal.rarity,
                modifier = Modifier.fillMaxSize(),
            )
            GachaStarBurst(
                rarity = reveal.rarity,
                active = true,
                modifier = Modifier.fillMaxSize(),
            )
            GachaBeamParticles(
                rarity = reveal.rarity,
                active = reveal.stage == RevealStage.Beam,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ── 翻牌演出层 ──
        if (reveal.visible) {
            CyberRevealLayer(
                stage = reveal.stage,
                singleDef = reveal.def,
                singleRarity = reveal.rarity,
                fortune = reveal.fortune,
                batch = reveal.staged ?: emptyList(),
                cardIn = reveal.cardIn,
                onSkip = ::skipReveal,
                onFlip = { r ->
                    if (r >= 3) {
                        buzz(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
                        MilanAudio.playSfx("gacha_reveal")
                    }
                },
                onOpenCharacter = onOpenCharacter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 抽卡演出状态机。
 */
private data class RevealUiState(
    val token: Int = 0,
    val staged: List<PullResult>? = null,
    val def: CharacterDataEntry? = null,
    val rarity: Int = 1,
    val visible: Boolean = false,
    val stage: RevealStage = RevealStage.Done,
    val fortune: String = "",
    val cardIn: Boolean = false,
    val flashVisible: Boolean = false,
    val flashColor: Color = Color.White,
)

/**
 * PullResult 结果列表的 rememberSaveable Saver。
 */
private val PullResultsSaver = listSaver<List<PullResult>, String>(
    save = { list -> list.map { r ->
        "${r.success}|${r.characterId.orEmpty()}|${r.characterName}|${r.rarity}|${r.isNew}|${r.fragmentsAwarded}"
    } },
    restore = { strs -> strs.mapNotNull { s ->
        val p = s.split('|', limit = 6)
        if (p.size < 6) null
        else PullResult(
            success = p[0].toBooleanStrictOrNull() ?: false,
            characterId = p[1].ifEmpty { null },
            characterName = p[2],
            rarity = p[3].toIntOrNull() ?: 0,
            isNew = p[4].toBooleanStrictOrNull() ?: false,
            fragmentsAwarded = p[5].toIntOrNull() ?: 0,
        )
    } },
)

/** 单张抽卡结果 chip（水墨国风版）：立绘 + 稀有度名 + 角色名，高稀有度渐变发光底。 */
@Composable
private fun GachaChip(
    r: PullResult,
    delayMs: Int,
    batch: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rc = AppTheme.rarityColor(r.rarity)
    val isEpic = r.rarity >= 3
    val isUr = r.rarity >= 4
    // UR 脉动更快更亮（600ms），SSR 标准（900ms），其他静态
    val glowA by if (isEpic) {
        rememberInfiniteTransition(label = "chipGlow").animateFloat(
            initialValue = if (isUr) 0.45f else 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(if (isUr) 600 else 900),
                RepeatMode.Reverse,
            ),
            label = "chipGlowA",
        )
    } else {
        remember { mutableStateOf(0.7f) }
    }
    var shown by remember(batch) { mutableStateOf(false) }
    LaunchedEffect(batch) {
        delay(delayMs.toLong())
        shown = true
    }
    val chipScale by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(280),
        label = "chipScale",
    )
    val chipAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(280),
        label = "chipAlpha",
    )
    Column(
        modifier = modifier
            .padding(3.dp)
            .graphicsLayer { scaleX = chipScale; scaleY = chipScale; alpha = chipAlpha }
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isUr -> Brush.verticalGradient(
                        listOf(
                            rc.copy(alpha = 0.45f + 0.35f * glowA),
                            AppTheme.Surface.copy(alpha = 0.85f),
                        ),
                    )
                    isEpic -> Brush.verticalGradient(
                        listOf(rc.copy(alpha = 0.30f + 0.35f * glowA), AppTheme.Surface),
                    )
                    else -> Brush.verticalGradient(
                        listOf(AppTheme.Surface, AppTheme.Surface.copy(alpha = 0.55f)),
                    )
                },
            )
            .border(
                width = if (isUr) 2.dp else 1.dp,
                color = rc.copy(alpha = if (isEpic) glowA else 0.55f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            PortraitImage(
                characterId = r.characterId ?: "",
                rarity = r.rarity,
                name = r.characterName,
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)),
                target = PortraitTarget.Thumb,
            )
            // 「新角色」闪光标记：SSR+/UR 首次获取时在立绘右上角显示金色 NEW 徽章
            if (r.isNew && r.rarity >= 3) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 2.dp, top = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppTheme.Gold)
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "NEW",
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.GoldTextOn,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(AppTheme.rarityName(r.rarity), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = rc)
        Text(
            text = r.characterName,
            fontSize = 11.sp,
            color = AppTheme.Text1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 概率标签 */
private fun ratesLabel(pool: GachaPoolDataEntry): String {
    val total = pool.rarityWeights.sum()
    if (total <= 0) return ""
    return pool.rarityWeights.mapIndexed { i, w ->
        val pct = w * 100f / total
        val pctText = if (pct % 1f == 0f) pct.toInt().toString() else String.format(Locale.US, "%.1f", pct)
        "${AppTheme.rarityName(i + 1)} $pctText%"
    }.joinToString(" · ")
}

/** 摘要行 */
private fun buildSummary(list: List<PullResult>): String {
    if (list.isEmpty()) return ""
    val ssr = list.count { it.rarity >= 3 }
    val frags = list.sumOf { it.fragmentsAwarded }
    val latest = list.maxByOrNull { it.rarity }?.characterName ?: ""
    val fragPart = if (frags > 0) " · 星魂碎片 +$frags" else ""
    return "共 ${list.size} 抽 · SSR+ $ssr$fragPart ✦ 最新: $latest"
}

/** 抽卡结果统计卡片：数量/稀有度分布/保底进度 */
@Composable
private fun PullStatsPanel(
    results: List<PullResult>,
    pity: Int,
    hardPity: Int,
    modifier: Modifier = Modifier,
) {
    if (results.isEmpty()) return
    val counts = remember(results) { results.groupBy { it.rarity }.mapValues { it.value.size } }
    val best = remember(results) { results.maxOfOrNull { it.rarity } ?: 1 }
    val softStart = remember(hardPity) { EconomyFormulas.softPityStart(hardPity) }
    val softRemaining = if (softStart > 0) (softStart - pity).coerceAtLeast(0) else 0

    GlassPanel(modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("本次统计", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppTheme.Gold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("总计 ${results.size} 抽", fontSize = 11.sp, color = AppTheme.Text2)
                Text(
                    "最高 ${AppTheme.rarityName(best)}",
                    fontSize = 11.sp,
                    color = AppTheme.rarityColor(best),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in 1..4) {
                    val c = counts[r] ?: 0
                    if (c > 0) {
                        Text(
                            "${"★".repeat(r)}×$c",
                            fontSize = 11.sp,
                            color = AppTheme.rarityColor(r),
                        )
                    }
                }
            }
            if (hardPity > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { pity.toFloat() / hardPity.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = AppTheme.Gold,
                    trackColor = AppTheme.Surface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "保底进度  $pity / $hardPity" +
                        if (softRemaining > 0) "  · 软保底还差 $softRemaining 抽" else "",
                    fontSize = 10.sp,
                    color = AppTheme.Text3,
                )
            }
        }
    }
}
