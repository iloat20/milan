package com.milan.game.ui.gacha

import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Stroke
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.milan.game.ui.effects.GpuRevealLayer
import com.milan.game.ui.effects.HolographicFoilOverlay
import com.milan.game.ai.OnDeviceAgent
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
import com.milan.game.infrastructure.CrashReporter
import com.milan.game.infrastructure.MilanAudio
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.GachaPoolDataEntry
import com.milan.game.services.PullResult
import com.milan.game.ui.GameState
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.GoldButton
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
    val scope = rememberCoroutineScope()
    val pool = remember { GameState.service.pools.firstOrNull() }

    var pity by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<PullResult>>(emptyList()) }
    var summary by remember { mutableStateOf("") }
    var batch by remember { mutableIntStateOf(0) }
    var revealToken by remember { mutableIntStateOf(0) }
    var staged by remember { mutableStateOf<List<PullResult>?>(null) }
    var revealDef by remember { mutableStateOf<CharacterDataEntry?>(null) }
    var revealRarity by remember { mutableIntStateOf(1) }
    var showReveal by remember { mutableStateOf(false) }
    var fortune by remember { mutableStateOf("") }
    var cardIn by remember { mutableStateOf(false) }
    var flashVisible by remember { mutableStateOf(false) }
    var flashColor by remember { mutableStateOf(Color.White) }
    var riftSwell by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }

    LaunchedEffect(pool) {
        pity = pool?.let { GameState.service.saveData.getGachaCounter(it.poolId) } ?: 0
    }
    LaunchedEffect(Unit) { entered = true }

    /** 触觉反馈（View 级，兼容非 Composable 路径；设备不支持或设置关闭振动时静默）。 */
    fun buzz(effect: Int) {
        if (!GameState.service.saveData.vibrationEnabled) return
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
            pity = pool?.let { GameState.service.saveData.getGachaCounter(it.poolId) } ?: pity
        }
        showReveal = false
        flashVisible = false
        riftSwell = false
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

    /** 抽卡入口（C# DoPull）：余额检查 → pull → 兜底 → 演出编排。 */
    fun doPull(tenPull: Boolean) {
        if (busy) return
        val p = pool ?: return
        val cost = if (tenPull) p.tenCost else p.singleCost
        if (GameState.service.saveData.softCurrency < cost) {
            Toast.makeText(context, "星尘不足", Toast.LENGTH_SHORT).show()
            return
        }
        busy = true
        val pulled = GameState.service.pull(p.poolId, tenPull)
        val best = pulled.maxByOrNull { it.rarity }
        if (best == null || best.characterId == null) {
            // 空结果兜底（C# 同款：留痕 + 复位，绝不闪退）。
            // 注意：空结果 =「卡池无候选角色」或「落盘失败已回滚」两种失败之一，
            // 对玩家统一提示重试即可；具体原因由 CrashReporter 留痕区分
            // （gacha.pull.empty vs pull.save.failed: rolled back）。
            busy = false
            Toast.makeText(context, "抽卡失败，请重试", Toast.LENGTH_SHORT).show()
            try { CrashReporter.boot("gacha.pull.empty poolId=${p.poolId}") } catch (_: Exception) { }
            return
        }
        staged = pulled
        buzz(HapticFeedbackConstants.KEYBOARD_TAP)
        MilanAudio.playSfx("gacha_pull")
        revealDef = best.characterId?.let { GameState.service.character(it) }
        revealRarity = best.rarity
        flashColor = AppTheme.rarityColor(best.rarity)
        // 端侧 AI 签文（默认 Stub：离线、确定性；seed 含 token 保证每抽不同但可复现）
        val token = revealToken + 1
        fortune = revealDef?.let { OnDeviceAgent.current.fortune(it, it.characterId.hashCode().toLong() + token) } ?: ""
        cardIn = false
        revealToken = token
        scope.launch {
            // 阶段一：法阵脉冲
            riftSwell = true
            delay(420); if (token != revealToken) return@launch
            // 阶段二：稀有度白闪（淡入淡出由 animateFloatAsState 处理）
            flashVisible = true
            delay(480); if (token != revealToken) return@launch
            flashVisible = false
            delay(120); if (token != revealToken) return@launch
            // 阶段三：大立绘卡弹出（SSR/UR 重触觉 + reveal 音效）
            cardIn = true
            showReveal = true
            MilanAudio.playSfx("gacha_reveal")
            // CONFIRM 需 API 30（minSdk 29）：低版本回退 LONG_PRESS，其余路径不变
            if (revealRarity >= 3) {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
                buzz(confirm)
            } else {
                buzz(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            delay(1500); if (token != revealToken) return@launch
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
    // 开包 GPU 演出进度（0→1，与翻牌阶段同步），驱动能量环扩张
    val revealProgress by animateFloatAsState(
        targetValue = if (showReveal) 1f else 0f,
        animationSpec = tween(1500),
        label = "revealProgress",
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
                GlassPanel(modifier = Modifier.fillMaxWidth(), gold = true) {
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

            // ── 召唤法阵（C# RiftPortal 升级：中西融合法阵，八卦/回纹/符箓 + 紫金霓虹）──
            RitualArrayPortal(swell = riftSwell)
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

            // ── 结果网格：每行 5 个 chip，逐张缩放淡入（C# 5 列 LinearLayout + 依序动画）──
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(results.chunked(5)) { rowIdx, row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEachIndexed { i, r ->
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

        // ── 翻牌演出层：全屏遮罩 + 稀有度光晕 + 大立绘卡 + 跳过（C# FlipCardView 简化）──
        if (showReveal) {
            GpuRevealLayer(active = true, progress = revealProgress) {
            val def = revealDef
            val rc = AppTheme.rarityColor(revealRarity)
            val cardScale by animateFloatAsState(
                targetValue = if (cardIn) 1f else 0.80f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 300f),
                label = "card",
            )
            val cardAlpha by animateFloatAsState(
                targetValue = if (cardIn) 1f else 0f,
                animationSpec = tween(200),
                label = "cardAlpha",
            )
            // SSR/UR 光晕脉动（P0-2 演出分级）：稀有度≥3 时呼吸发光 + 光晕缩放，R/SR 保持静态
            val isEpic = revealRarity >= 3
            val glowTransition = rememberInfiniteTransition(label = "glow")
            val glowAlpha by glowTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 0.85f,
                animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
                label = "glowAlpha",
            )
            val glowScale by glowTransition.animateFloat(
                initialValue = 0.92f,
                targetValue = 1.10f,
                animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
                label = "glowScale",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.74f))
                    .clickable(onClick = ::skipReveal),
                contentAlignment = Alignment.Center,
            ) {
                // 稀有度光晕（径向渐变全屏；SSR/UR 脉动呼吸 + 缩放，UR 追加熔金叠层）
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = if (isEpic) glowScale else 1f
                            scaleY = if (isEpic) glowScale else 1f
                        }
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    rc.copy(alpha = if (isEpic) glowAlpha else 0.50f),
                                    Color.Transparent,
                                )
                            )
                        ),
                )
                // UR 专属：熔金爆发叠层（金光盖过紫光，配 UR 金色立绘边框）
                if (revealRarity >= 4) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    listOf(AppTheme.GoldHi.copy(alpha = glowAlpha * 0.8f), Color.Transparent)
                                )
                            ),
                    )
                }
                // 大立绘卡 220x312（C# FlipCardView 尺寸）+ 全息箔叠层
                Box(Modifier.size(width = 220.dp, height = 312.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = cardScale; scaleY = cardScale; alpha = cardAlpha }
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(rc.copy(alpha = 0.34f), AppTheme.BgDeepest, AppTheme.BgDeepest),
                            ),
                        )
                        .border(2.dp, rc.copy(alpha = 0.85f), RoundedCornerShape(18.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (def != null) {
                        PortraitImage(
                            characterId = def.characterId,
                            rarity = revealRarity,
                            name = def.displayName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            aura = true, // v2：抽卡揭晓稀有度光环
                        )
                    } else {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(AppTheme.rarityName(revealRarity), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = rc)
                        }
                    }
                    // 底部铭牌：角色名 + 稀有度名
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(rc.copy(alpha = 0.16f))
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(def?.displayName ?: "", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppTheme.Text1)
                    }
                    Text(
                        text = AppTheme.rarityName(revealRarity),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = rc,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    // 端侧 AI 命运签文（离线确定性生成；接大模型时自动升级）
                    if (fortune.isNotEmpty()) {
                        Text(
                            text = fortune,
                            fontSize = 10.sp,
                            color = AppTheme.Text2,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                }
                HolographicFoilOverlay(
                    Modifier.matchParentSize().clip(RoundedCornerShape(18.dp)),
                    alpha = 0.30f,
                )
                }
                // 跳过按钮（整屏也可点跳过）
                Text(
                    text = "跳过 ▶",
                    fontSize = 13.sp,
                    color = AppTheme.Gold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 22.dp, end = 18.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppTheme.Surface)
                        .border(1.dp, AppTheme.Gold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable(onClick = ::skipReveal)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            }
        }
    }
}

/**
 * 中西融合法阵（C# RiftPortal 升级）：八卦外环 + 回纹中环 + 符箓内环，紫金霓虹、缓旋。
 * 环与八卦刻度随 spin 旋转，八卦卦象与中心辉光保持正立，避免字号倒置。
 */
@Composable
private fun RitualArrayPortal(swell: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (swell) 1.45f else 1f,
        animationSpec = tween(700),
        label = "riftScale",
    )
    val spin by rememberInfiniteTransition(label = "arraySpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(28000), RepeatMode.Restart),
        label = "spin",
    )
    val trigrams = listOf("☰", "☱", "☲", "☳", "☴", "☵", "☶", "☷")
    val talismans = listOf("敕", "令", "罡", "玦")
    Box(
        Modifier.size(190.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        contentAlignment = Alignment.Center,
    ) {
        // 旋转法阵环（外庚金环 + 回纹虚线中环 + 内庚金环 + 八卦刻度）
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val R = size.minDimension / 2f
            drawCircle(
                color = AppTheme.Gold,
                radius = R * 0.95f,
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = AppTheme.Violet,
                radius = R * 0.76f,
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f), 0f)),
            )
            drawCircle(
                color = AppTheme.Gold,
                radius = R * 0.5f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx()),
            )
            for (i in 0 until 8) {
                val a = Math.toRadians((i * 45).toDouble())
                val x1 = cx + R * 0.78f * Math.sin(a).toFloat()
                val y1 = cy - R * 0.78f * Math.cos(a).toFloat()
                val x2 = cx + R * 0.92f * Math.sin(a).toFloat()
                val y2 = cy - R * 0.92f * Math.cos(a).toFloat()
                drawLine(
                    color = AppTheme.Gold,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
        }
        // 八卦卦象（正立，不随环旋转）
        trigrams.forEachIndexed { i, g ->
            val a = Math.toRadians((i * 45).toDouble())
            val rx = (78f * Math.sin(a)).toFloat()
            val ry = (-78f * Math.cos(a)).toFloat()
            Text(
                g,
                Modifier.align(Alignment.Center).offset(x = rx.dp, y = ry.dp),
                color = AppTheme.Gold,
                fontSize = 13.sp,
            )
        }
        // 中心能量辉光
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(
                Brush.radialGradient(
                    listOf(
                        AppTheme.Violet.copy(alpha = 0.55f),
                        AppTheme.Gold.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                ),
            ),
        )
        // 符箓内环（4 字，缓慢逆向旋转营造咒文流转）
        Box(Modifier.size(150.dp).graphicsLayer { rotationZ = -spin * 0.5f }, contentAlignment = Alignment.Center) {
            talismans.forEachIndexed { i, t ->
                val a = Math.toRadians((i * 90).toDouble())
                val rx = (46f * Math.sin(a)).toFloat()
                val ry = (-46f * Math.cos(a)).toFloat()
                Text(
                    t,
                    Modifier.align(Alignment.Center).offset(x = rx.dp, y = ry.dp),
                    color = AppTheme.Gold.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(text = "✦", fontSize = 44.sp, color = AppTheme.Gold, modifier = Modifier.align(Alignment.Center))
    }
}

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
                if (r.rarity >= 3) {
                    Brush.verticalGradient(listOf(rc.copy(alpha = 0.30f), AppTheme.Surface))
                } else {
                    Brush.verticalGradient(listOf(AppTheme.Surface, AppTheme.Surface.copy(alpha = 0.55f)))
                },
            )
            .border(1.dp, rc.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
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
