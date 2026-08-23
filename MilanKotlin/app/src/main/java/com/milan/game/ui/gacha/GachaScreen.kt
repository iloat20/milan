package com.milan.game.ui.gacha

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 寻访（抽卡）屏（C# GachaActivity.cs 翻译）。
 *
 * 结构：标题 + 资源胶囊 → 卡池信息（概率 / 保底进度）→ 池角色横排预览 →
 * 召唤法阵（静态渐变圆 + 抽卡脉冲）→ 单抽 / 十连 → 摘要行 → 5 列结果网格（逐张缩放淡入）→ 底部导航。
 *
 * 演出简化（C# RiftPortal / FlipCardView / WeaponFxView 动画视图 → Compose 编排）：
 * 法阵脉冲 → 稀有度白闪 → 大立绘卡弹出（可整屏点击跳过）→ 展示结果。
 * 编排用协程 + revealToken 作废（等价 C# Handler.PostSafe + _revealToken 语义）。
 */
@Composable
fun GachaScreen(
    onNav: (NavItem) -> Unit,
    onOpenCharacter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // R3/I4：反馈统一走 LocalFeedback（由 MainActivity 提供的 Snackbar 宿主）。
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val pool = remember { GameState.service.pools.firstOrNull() }
    // 路径 B：订阅状态快照——保底进度/余额/振动开关从快照派生（写操作后自动刷新），
    // 替代 saveData 直读 + 手动 pity 维护。
    val snap by GameState.snapshot.collectAsStateWithLifecycle()

    val pity = pool?.let { snap.pityByPool[it.poolId] } ?: 0
    var busy by remember { mutableStateOf(false) }
    // P3-8：结果列表与摘要用 rememberSaveable——旋转（配置变更）后恢复，
    // 玩家不会丢掉已付费抽卡的结果展示（PullResult 经 PullResultsSaver 编码为字符串列表）。
    var results by rememberSaveable(stateSaver = PullResultsSaver) {
        mutableStateOf<List<PullResult>>(emptyList())
    }
    var summary by rememberSaveable { mutableStateOf("") }
    var batch by remember { mutableIntStateOf(0) }
    var revealToken by remember { mutableIntStateOf(0) }
    var staged by remember { mutableStateOf<List<PullResult>?>(null) }
    var revealDef by remember { mutableStateOf<CharacterDataEntry?>(null) }
    var revealRarity by remember { mutableIntStateOf(1) }
    var showReveal by remember { mutableStateOf(false) }
    // 2026-08-20 赛博霓虹演出：阶段机（Charge 蓄能 → Beam 光柱 → Single/Ten 揭晓 → Done）
    var revealStage by remember { mutableStateOf(RevealStage.Done) }
    var fortune by remember { mutableStateOf("") }
    var cardIn by remember { mutableStateOf(false) }
    var flashVisible by remember { mutableStateOf(false) }
    var flashColor by remember { mutableStateOf(Color.White) }
    var entered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { entered = true }

    /** 触觉反馈（View 级，兼容非 Composable 路径；设备不支持或设置关闭振动时静默）。 */
    fun buzz(effect: Int) {
        if (!snap.vibrationEnabled) return
        try {
            val view = (context as? android.app.Activity)?.window?.decorView ?: return
            view.performHapticFeedback(effect)
        } catch (_: Exception) { }
    }

    /** 演出结束 / 跳过：展示结果、复位状态（C# FinishReveal）。 */
    fun finishReveal() {
        val list = staged
        if (list != null) {
            results = list
            summary = buildSummary(list)
            batch++
        }
        showReveal = false
        revealStage = RevealStage.Done
        flashVisible = false
        staged = null
        revealDef = null
        busy = false
    }

    /** 跳过演出：递增 token 作废挂起编排，立即出结果（C# SkipReveal）。 */
    fun skipReveal() {
        if (!busy || !showReveal) return
        revealToken++
        finishReveal()
    }

    // P1-3 修复：演出中系统返回（手势/Predictive Back）不得直接销毁组合——抽卡已扣款发货，
    // 直接返回会让玩家看不到结果；拦截并转跳过演出（等价点按跳过），随后再返回才退出页面。
    BackHandler(enabled = showReveal) { skipReveal() }

    /** 抽卡入口（C# DoPull）：余额检查 → pull → 兜底 → 演出编排。
     *  2026-08 主线程 IO 异步化：pull 为 suspend，落盘在 IO 线程执行，主线程不阻塞；
     *  演出编排与抽卡在同一协程内衔接（busy 在协程期间保持，防连点）。 */
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
            // 类型化结果：Success 携带产出；Rejected / SaveFailed 分别提示，
            // 替代「空列表 = 卡池数据异常」的误导性笼统文案（余额不足与落盘失败原因可区分）。
            val pulled = when (val outcome = GameState.service.pull(p.poolId, tenPull)) {
                is PullOutcome.Success -> outcome.results
                is PullOutcome.Rejected -> {
                    // 被拒绝：余额不足（前面已拦截）或卡池无候选产出；留痕 + 复位，绝不闪退
                    busy = false
                    feedback.show("抽卡失败，请重试")
                    try { CrashReporter.boot("gacha.pull.rejected poolId=${p.poolId}") } catch (_: Exception) { }
                    return@launch
                }
                is PullOutcome.SaveFailed -> {
                    // 落盘失败：扣款与发货已回滚，可安全重试（原因由 CrashReporter 留痕区分）
                    busy = false
                    feedback.show("保存失败，请重试")
                    try { CrashReporter.boot("gacha.pull.saveFailed poolId=${p.poolId}") } catch (_: Exception) { }
                    return@launch
                }
            }
            val best = pulled.maxByOrNull { it.rarity }
            if (best == null || best.characterId == null) {
                // Success 契约下理论不可达（plan 空会走 Rejected），防御性保留兜底
                busy = false
                feedback.show("抽卡失败，请重试")
                try { CrashReporter.boot("gacha.pull.empty poolId=${p.poolId}") } catch (_: Exception) { }
                return@launch
            }
            staged = pulled
            buzz(HapticFeedbackConstants.KEYBOARD_TAP)
            MilanAudio.playSfx("gacha_pull")
            revealDef = best.characterId.let { GameState.service.character(it) }
            revealRarity = best.rarity
            flashColor = AppTheme.rarityColor(best.rarity)
            // 端侧 AI 签文（默认 Stub：离线、确定性；seed 含 token 保证每抽不同但可复现）
            val token = revealToken + 1
            fortune = revealDef?.let { FortuneAgentRegistry.activeAgent.fortune(it, it.characterId.hashCode().toLong() + token) } ?: ""
            cardIn = false
            revealToken = token
            // 阶段一：蓄能（粒子汇聚 + 弧线环绕，CyberStage.ChargeCore）
            revealStage = RevealStage.Charge
            delay(420); if (token != revealToken) return@launch
            // 阶段二：次元光柱爆发（RiftBeam + 稀有度白闪叠放增强）
            revealStage = RevealStage.Beam
            flashVisible = true
            delay(480); if (token != revealToken) return@launch
            flashVisible = false
            delay(120); if (token != revealToken) return@launch
            // 阶段三：揭晓（单抽大立绘卡 / 十连 2×5 牌桌逐张翻开，onFlip 逐张反馈）
            cardIn = true
            showReveal = true
            revealStage = if (tenPull) RevealStage.Ten else RevealStage.Single
            MilanAudio.playSfx("gacha_reveal")
            // CONFIRM 需 API 30（minSdk 29）：低版本回退 LONG_PRESS，其余路径不变
            if (revealRarity >= 3) {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
                buzz(confirm)
            } else {
                buzz(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            delay(if (tenPull) 3600L else 1500L); if (token != revealToken) return@launch
            // 阶段四：结果
            finishReveal()
        }
    }

    val entranceAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(450),
        label = "entrance",
    )
    val flashAlpha by animateFloatAsState(
        targetValue = if (flashVisible) 1f else 0f,
        animationSpec = tween(420),
        label = "flash",
    )
    PageBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp)
                .graphicsLayer { alpha = entranceAlpha },
        ) {
            // ── 标题 + 资源胶囊（C# 顶部 header，无返回键，靠底部导航切换）──
            Row(Modifier.statusBarsPadding().fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "次元裂缝",
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
                // ── 卡池信息面板（池名 / 概率 / 保底进度，保底行霜蓝）──
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
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "保底进度  $pity / ${pool.hardPity}",
                                fontSize = 11.sp,
                                color = AppTheme.Frost,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // ── 池角色预览：横排圆形头像（点击进角色详情）──
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pool.entries) { entry ->
                        val def = GameState.service.character(entry.characterId)
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { def?.let { onOpenCharacter(it.characterId) } }
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

            // ── 待机能量枢纽（2026-08-20 赛博霓虹演出：替换旧八卦法阵）──
            CyberHerald(modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))

            Text(
                text = "─ 敕令開陣 ─",
                fontSize = 11.sp,
                color = AppTheme.Gold.copy(alpha = 0.6f),
                letterSpacing = 4.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(6.dp))

            // ── 单抽 / 十连（C# Neon + Gold 双按钮）──
            Row(Modifier.fillMaxWidth().padding(horizontal = 30.dp)) {
                NeonButton("单 抽", Modifier.weight(1f), onClick = { doPull(false) })
                Spacer(Modifier.width(14.dp))
                GoldButton("十 连", Modifier.weight(1f), onClick = { doPull(true) })
            }
            Spacer(Modifier.height(10.dp))

            // ── 摘要行（共 N 抽 · SSR+ N · 碎片 · 最新）──
            Text(
                text = summary,
                fontSize = 11.sp,
                color = AppTheme.Text2,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(6.dp))

            // ── 结果网格：每行 5 个 chip，逐张缩放淡入（C# 5 列 LinearLayout + 依序动画）
            // P3-3：chip 加稳定 key（位置 + 结果实例），避免 LazyColumn 槽位复用串态
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(results.chunked(5)) { rowIdx, row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEachIndexed { i, r ->
                            key(i, r) {
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

        // ── 稀有度白闪层（C# FlashView，抽到高稀有度全屏闪光）──
        if (flashAlpha > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(flashColor)
                    .graphicsLayer { alpha = flashAlpha },
            )
        }

        // ── 翻牌演出层（2026-08-20 赛博霓虹：蓄能 → 光柱 → 揭晓逐张，整屏点击可跳过）──
        if (showReveal) {
            CyberRevealLayer(
                stage = revealStage,
                singleDef = revealDef,
                singleRarity = revealRarity,
                fortune = fortune,
                batch = staged ?: emptyList(),
                cardIn = cardIn,
                onSkip = ::skipReveal,
                onFlip = { r ->
                    // 逐张翻开反馈：SSR/UR 才振 + 音效，R/SR 静默避免十连全程震动
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
 * PullResult 结果列表的 rememberSaveable Saver（P3-8：旋转后恢复抽卡结果展示）。
 * PullResult 字段全为基本类型/字符串，以管道分隔编码为 String 列表存入 Bundle；
 * 角色显示名/ID 不含 '|'（编码契约，见 GachaChip 渲染处）。
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

/** 单张抽卡结果 chip（C# CharacterCard.GachaChip 翻译）：立绘 + 稀有度名 + 角色名，高稀有度渐变发光底。 */
@Composable
private fun GachaChip(
    r: PullResult,
    delayMs: Int,
    batch: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rc = AppTheme.rarityColor(r.rarity)
    // 2026-08-20 霓虹化：SSR+ chip 呼吸发光（isEpic 在 chip 生命周期内不变，条件 remember 分支稳定安全）
    val isEpic = r.rarity >= 3
    val glowA by if (isEpic) {
        rememberInfiniteTransition(label = "chipGlow").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "chipGlowA",
        )
    } else {
        remember { mutableStateOf(0.7f) }
    }
    // 每批次演出后重放入场动画（batch 变化重置 shown）
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
                if (isEpic) {
                    Brush.verticalGradient(listOf(rc.copy(alpha = 0.30f + 0.35f * glowA), AppTheme.Surface))
                } else {
                    Brush.verticalGradient(listOf(AppTheme.Surface, AppTheme.Surface.copy(alpha = 0.55f)))
                },
            )
            .border(1.dp, rc.copy(alpha = if (isEpic) glowA else 0.55f), RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PortraitImage(
            characterId = r.characterId ?: "",
            rarity = r.rarity,
            name = r.characterName,
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)),
            target = PortraitTarget.Thumb,
        )
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

/** 概率标签（C# ratesLabel）："UR 1% · SSR 2% · SR 5% · R 92%"，百分比按权重占比算，整数去小数点。 */
private fun ratesLabel(pool: GachaPoolDataEntry): String {
    val total = pool.rarityWeights.sum()
    if (total <= 0) return ""
    return pool.rarityWeights.mapIndexed { i, w ->
        val pct = w * 100f / total
        val pctText = if (pct % 1f == 0f) pct.toInt().toString() else String.format(Locale.US, "%.1f", pct)
        "${AppTheme.rarityName(i + 1)} $pctText%"
    }.joinToString(" · ")
}

/** 摘要行（C# DoPull 结尾）：共 N 抽 · SSR+ N · 星魂碎片 +N ✦ 最新: 名（碎片为 0 不显示）。 */
private fun buildSummary(list: List<PullResult>): String {
    if (list.isEmpty()) return ""
    val ssr = list.count { it.rarity >= 3 }
    val frags = list.sumOf { it.fragmentsAwarded }
    val latest = list.maxByOrNull { it.rarity }?.characterName ?: ""
    val fragPart = if (frags > 0) " · 星魂碎片 +$frags" else ""
    return "共 ${list.size} 抽 · SSR+ $ssr$fragPart ✦ 最新: $latest"
}
