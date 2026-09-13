package com.milan.game.ui.gacha

import androidx.compose.material3.MaterialTheme

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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.milan.game.domain.progression.EconomyFormulas
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milan.game.infrastructure.MilanAudio
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.GachaPoolDataEntry
import com.milan.game.services.PullResult
import com.milan.game.ui.components.GlassDialog
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.components.InkButton
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.components.PortraitTarget
import com.milan.game.ui.nav.GameNavBar
import com.milan.game.ui.nav.ResourceBar
import com.milan.game.ui.nav.NavItem
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.effects.GachaBeamParticles
import com.milan.game.ui.effects.GachaStarBurst
import com.milan.game.ui.effects.RarityMeshBackdrop
import com.milan.game.ui.effects.inkSplash
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 寻访（抽卡）（v4）。
 *
 * 层次：标题+资源 → 池选择 → UP 展签 → 单抽/十连 CTA → 摘要 → 结果 → 底栏。
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
    val vm: GachaViewModel = viewModel(factory = com.milan.game.di.AppGraph.factory)
    val pools = vm.pools
    var selectedPoolId by rememberSaveable { mutableStateOf(pools.firstOrNull()?.poolId.orEmpty()) }
    val pool = pools.firstOrNull { it.poolId == selectedPoolId } ?: pools.firstOrNull()
    val eco by vm.economy.collectAsStateWithLifecycle()
    val gachaSlice by vm.gachaSlice.collectAsStateWithLifecycle()
    val meta by vm.meta.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val reveal by vm.reveal.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val batch by vm.batch.collectAsStateWithLifecycle()

    val pity = pool?.let { gachaSlice.pityByPool[it.poolId] } ?: 0
    var entered by remember { mutableStateOf(false) }
    var showTenConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { entered = true }

    fun buzz(effect: Int) {
        if (!meta.vibrationEnabled) return
        try {
            val view = (context as? android.app.Activity)?.window?.decorView ?: return
            HapticManager.performHapticFeedback(view, effect)
        } catch (_: Exception) { }
    }

    // R7-P1：Charge/Beam 也可 skip（visible 从 Charge 起为 true）
    BackHandler(enabled = reveal.visible || reveal.stage == RevealStage.Charge || reveal.stage == RevealStage.Beam) {
        vm.skipReveal()
    }

    fun doPull(tenPull: Boolean) {
        val p = pool ?: return
        vm.doPull(
            tenPull = tenPull,
            poolId = p.poolId,
            singleCost = p.singleCost,
            tenCost = p.tenCost,
            softCurrency = eco.softCurrency,
            onBuzz = { bestRarity ->
                when (bestRarity) {
                    4 -> HapticManager.gachaUrSpecial(context)
                    3 -> buzz(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
                    2 -> buzz(HapticFeedbackConstants.KEYBOARD_TAP)
                    else -> buzz(HapticFeedbackConstants.CLOCK_TICK)
                }
            },
            onSfx = { sfx -> MilanAudio.playSfx(sfx) },
            onError = { msg -> scope.launch { feedback.show(msg) } },
        )
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
                .padding(start = 18.dp, end = 18.dp, top = 14.dp)
                .verticalScroll(rememberScrollState())
                .graphicsLayer { alpha = entranceAlpha },
        ) {
            Row(Modifier.statusBarsPadding().fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(3.dp, 18.dp)
                        .background(AppTheme.ZhuSha),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "寻访",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.Text1,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "GACHA",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.Text3,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                ResourceBar()
            }
            Spacer(Modifier.height(16.dp))

            if (pool != null) {
                if (pools.size > 1) {
                    // 卡池纬带 chip：与主页快捷入口同语言
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pools.forEach { p ->
                            val selected = p.poolId == pool.poolId
                            val chipShape = RoundedCornerShape(AppTheme.Roundness.lg)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(chipShape)
                                    .background(
                                        if (selected) AppTheme.ZhuSha.copy(alpha = 0.18f) else AppTheme.BgMid.copy(alpha = 0.92f),
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) AppTheme.ZhuSha.copy(alpha = 0.55f) else AppTheme.Stroke,
                                        chipShape,
                                    )
                                    .clickable { selectedPoolId = p.poolId }
                                    .padding(end = 12.dp, top = 8.dp, bottom = 8.dp),
                            ) {
                                Box(
                                    Modifier
                                        .width(3.dp)
                                        .height(16.dp)
                                        .background(
                                            if (selected) AppTheme.ZhuSha else AppTheme.Text3.copy(alpha = 0.5f),
                                        ),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = p.displayName.ifEmpty { "常驻卡池" },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) AppTheme.Text1 else AppTheme.Text2,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                // ── UP 英雄展签：左大立绘 + 右信息 ──
                val featuredId = pool.featuredCharacterId.ifEmpty { pool.entries.firstOrNull()?.characterId.orEmpty() }
                val featuredDef = featuredId.takeIf { it.isNotEmpty() }?.let { vm.character(it) }
                val featuredRarity = pool.entries.firstOrNull { it.characterId == featuredId }?.rarityIndex
                    ?: featuredDef?.baseRarity ?: 3
                PoolHeroCard(
                    pool = pool,
                    featured = featuredDef,
                    featuredRarity = featuredRarity,
                    pity = pity,
                    featuredLost = gachaSlice.featuredLostByPool[pool.poolId] == true,
                    onOpenCharacter = { id -> onOpenCharacter(id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(20.dp))

            val heraldPityRatio = if ((pool?.hardPity ?: 0) > 0) pity.toFloat() / pool!!.hardPity.coerceAtLeast(1) else 0f
            CyberHerald(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                testMode = testMode,
                pityRatio = heraldPityRatio,
            )
            Spacer(Modifier.height(20.dp))

            // ── CTA：单抽 / 十连（主按钮 + 消耗副标）──
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val singleCost = pool?.singleCost ?: 0
                val tenCost = pool?.tenCost ?: 0
                PullCtaButton(
                    label = "单 抽",
                    cost = singleCost,
                    enabled = !busy && pool != null && eco.softCurrency >= singleCost,
                    onClick = { doPull(false) },
                    modifier = Modifier.weight(1f),
                    primary = false,
                )
                PullCtaButton(
                    label = "十 连",
                    cost = tenCost,
                    enabled = !busy && pool != null && eco.softCurrency >= tenCost,
                    onClick = { showTenConfirm = true },
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
            }
            pool?.let { p ->
                GlassDialog(
                    show = showTenConfirm,
                    onDismiss = { showTenConfirm = false },
                    title = "确认十连寻访",
                    body = "将消耗 ${p.tenCost} 环痕进行十次召唤。是否继续？",
                    buttons = {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            InkButton(
                                text = "取 消",
                                onClick = { showTenConfirm = false },
                                modifier = Modifier.weight(1f),
                            )
                            GoldButton(
                                text = "确认寻访",
                                onClick = {
                                    showTenConfirm = false
                                    doPull(true)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    },
                )
            }
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary.ifEmpty { "尚未寻访 · 机缘未至" },
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.Text2,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (results.isNotEmpty()) {
                    val shareInteraction = remember { MutableInteractionSource() }
                    Text(
                        text = "分享",
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.Gold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppTheme.Roundness.md))
                            .inkSplash(shareInteraction)
                            .clickable(interactionSource = shareInteraction, indication = null) {
                                scope.launch { PullShareCard.shareResults(context, results) }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Text(
                    text = "历 史 ›",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Frost,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppTheme.Roundness.md))
                        .clickable(onClick = onOpenHistory)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))

            if (results.isNotEmpty()) {
                ResultGridHeader(results = results, pity = pity, hardPity = pool?.hardPity ?: 0)
                Spacer(Modifier.height(6.dp))
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                results.chunked(5).forEachIndexed { rowIdx, row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEachIndexed { i, r ->
                            key("${r.characterId}_${rowIdx}_$i") {
                                GachaChip(
                                    r = r,
                                    delayMs = (rowIdx * 5 + i) * 55,
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

            GameNavBar(
                active = NavItem.Gacha,
                onSelect = onNav,
            )
        }

        if (flashAlpha > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(reveal.flashColor)
                    .graphicsLayer { alpha = flashAlpha },
            )
        }

        val isRevealActive = reveal.stage == RevealStage.Beam ||
            reveal.stage == RevealStage.Single ||
            reveal.stage == RevealStage.Ten
        // v4：去掉 Mesh 星云与多层粒子，只留一层极淡稀有度底光（CyberStage 内已有墨韵）
        if (isRevealActive && reveal.rarity >= 3) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                AppTheme.rarityColor(reveal.rarity).copy(alpha = 0.16f),
                                Color.Transparent,
                            ),
                            radius = 1200f,
                        )
                    ),
            )
        }

        if (reveal.visible) {
            CyberRevealLayer(
                stage = reveal.stage,
                singleDef = reveal.def,
                singleRarity = reveal.rarity,
                fortune = reveal.fortune,
                batch = reveal.staged ?: emptyList(),
                cardIn = reveal.cardIn,
                onSkip = { vm.skipReveal() },
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

/** UP 展签：全幅立绘 + 底部信息叠层（与主页 Hero 同构图）。 */
@Composable
private fun PoolHeroCard(
    pool: GachaPoolDataEntry,
    featured: CharacterDataEntry?,
    featuredRarity: Int,
    pity: Int,
    featuredLost: Boolean,
    onOpenCharacter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rc = AppTheme.rarityColor(featuredRarity)
    val shape = RoundedCornerShape(AppTheme.Roundness.lg)
    // 展签工艺：卡砖 offset + 稀有度染边 + 顶金丝（CodexCard 同语言，放大版）
    val stock = 5.dp
    Box(modifier = modifier.padding(end = stock, bottom = stock)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .graphicsLayer {
                    translationX = stock.toPx() * 0.55f
                    translationY = stock.toPx()
                    shadowElevation = 16f
                    ambientShadowColor = Color.Black
                    spotShadowColor = rc.copy(alpha = 0.45f)
                }
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            rc.copy(alpha = 0.35f),
                            Color(0xFF2A2E34),
                            Color(0xFF12151A),
                            Color(0xFF0A0C10),
                        ),
                    ),
                    shape,
                ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(shape)
                .background(AppTheme.BgMid)
                .border(1.dp, rc.copy(alpha = 0.55f), shape)
                .clickable(enabled = featured != null) { featured?.let { onOpenCharacter(it.characterId) } },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(rc.copy(alpha = 0.18f), Color.Transparent),
                            radius = 1000f,
                        )
                    ),
            )
            if (featured != null) {
                PortraitImage(
                    characterId = featured.characterId,
                    rarity = featuredRarity,
                    name = featured.displayName,
                    target = PortraitTarget.Full,
                    aura = true,
                    glowScale = 1.1f,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            // 顶金丝 + 底厚条
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .background(AppTheme.GoldHi.copy(alpha = 0.4f)),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.3f)),
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(AppTheme.Roundness.sm))
                    .background(AppTheme.ZhuSha)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    "UP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.Text1,
                )
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, AppTheme.BgDeepest.copy(alpha = 0.88f), AppTheme.BgDeepest),
                        )
                    )
                    .padding(start = 16.dp, top = 36.dp, end = 16.dp, bottom = 14.dp),
            ) {
                Text(
                    text = featured?.displayName ?: "环痕",
                    style = MaterialTheme.typography.headlineMedium,
                    color = AppTheme.Text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = pool.displayName.ifEmpty { "常驻卡池" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.Text2,
            )
            Spacer(Modifier.height(8.dp))
            Text(ratesLabel(pool), style = MaterialTheme.typography.labelMedium, color = AppTheme.Text3, lineHeight = 16.sp)
            if (pool.hardPity > 0) {
                Spacer(Modifier.height(10.dp))
                val softStart = EconomyFormulas.softPityStart(pool.hardPity)
                val inSoft = softStart > 0 && pity >= softStart
                val nearSoft = softStart > 0 && !inSoft && pity >= softStart - 5
                val pityColor = when {
                    inSoft -> AppTheme.ZhuSha
                    nearSoft -> AppTheme.Gold
                    else -> AppTheme.Frost
                }
                LinearProgressIndicator(
                    progress = { pity.toFloat() / pool.hardPity.coerceAtLeast(1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = pityColor,
                    trackColor = AppTheme.SurfaceNested,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "保底 $pity / ${pool.hardPity}",
                        style = MaterialTheme.typography.labelMedium,
                        color = pityColor,
                    )
                    if (featuredLost) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "下次必中",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = AppTheme.Frost,
                        )
                    }
                }
            }
        }
    }
    }
}

/** 抽卡 CTA：主/次按钮 + 环痕消耗。 */
@Composable
private fun PullCtaButton(
    label: String,
    cost: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (primary) {
            com.milan.game.ui.components.GoldButtonFull(
                text = label,
                enabled = enabled,
                onClick = onClick,
            )
        } else {
            InkButton(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                onClick = onClick,
                textSize = 15.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "环痕 $cost",
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) AppTheme.Text2 else AppTheme.Text3,
        )
    }
}

/** 结果区标题：本次稀有度分布。 */
@Composable
private fun ResultGridHeader(
    results: List<PullResult>,
    pity: Int,
    hardPity: Int,
    modifier: Modifier = Modifier,
) {
    val counts = remember(results) { results.groupBy { it.rarity }.mapValues { it.value.size } }
    val best = remember(results) { results.maxOfOrNull { it.rarity } ?: 1 }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "本次 ${results.size} 抽",
            fontWeight = FontWeight.Bold,
            color = AppTheme.Gold,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "最高 ${AppTheme.rarityName(best)}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = AppTheme.rarityColor(best),
        )
        Spacer(Modifier.weight(1f))
        for (r in 1..4) {
            val c = counts[r] ?: 0
            if (c > 0) {
                Text(
                    text = "${"★".repeat(r)}×$c",
                    color = AppTheme.rarityColor(r),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

internal data class RevealUiState(
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

/** 单张结果卡：立绘 + 稀有度名，SSR+ 静态稀有度色边（v4 去呼吸光）。 */
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
    val glowA = if (isUr) 0.55f else if (isEpic) 0.4f else 0.55f
    var shown by remember(batch) { mutableStateOf(false) }
    LaunchedEffect(batch) {
        delay(delayMs.toLong())
        shown = true
    }
    val chipScale by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(300),
        label = "chipScale",
    )
    val chipAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(300),
        label = "chipAlpha",
    )
    Column(
        modifier = modifier
            .padding(3.dp)
            .graphicsLayer { scaleX = chipScale; scaleY = chipScale; alpha = chipAlpha }
            .clip(RoundedCornerShape(AppTheme.Roundness.md))
            .background(
                when {
                    isUr -> Brush.verticalGradient(
                        listOf(
                            rc.copy(alpha = 0.4f + 0.35f * glowA),
                            AppTheme.BgMid.copy(alpha = 0.9f),
                        ),
                    )
                    isEpic -> Brush.verticalGradient(
                        listOf(rc.copy(alpha = 0.28f + 0.3f * glowA), AppTheme.BgMid),
                    )
                    else -> Brush.verticalGradient(
                        listOf(AppTheme.BgMid, AppTheme.SurfaceNested),
                    )
                },
            )
            .border(
                width = if (isUr) 2.dp else 1.dp,
                color = rc.copy(alpha = if (isEpic) glowA else 0.55f),
                shape = RoundedCornerShape(AppTheme.Roundness.md),
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
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(AppTheme.Roundness.md)),
                target = PortraitTarget.Avatar,
            )
            if (r.isNew && r.rarity >= 3) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 2.dp, top = 2.dp)
                        .clip(RoundedCornerShape(AppTheme.Roundness.xs))
                        .background(
                            Brush.linearGradient(
                                listOf(AppTheme.GoldHi, AppTheme.Gold, AppTheme.GoldDeep)
                            )
                        )
                        .border(0.5.dp, AppTheme.GoldHi.copy(alpha = 0.7f), RoundedCornerShape(AppTheme.Roundness.xs))
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "NEW",
                        fontSize = 7.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppTheme.GoldTextOn,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(AppTheme.rarityName(r.rarity), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = rc)
        Text(
            text = r.characterName,
            color = AppTheme.Text1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun ratesLabel(pool: GachaPoolDataEntry): String {
    val total = pool.rarityWeights.sum()
    if (total <= 0) return ""
    return pool.rarityWeights.mapIndexed { i, w ->
        val pct = w * 100f / total
        val pctText = if (pct % 1f == 0f) pct.toInt().toString() else String.format(Locale.US, "%.1f", pct)
        "${AppTheme.rarityName(i + 1)} $pctText%"
    }.joinToString(" · ")
}
