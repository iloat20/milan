package com.milan.game.ui.characters

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milan.game.data.CharacterSaveState
import com.milan.game.infrastructure.SpeechPlayer
import com.milan.game.ui.GameState
import com.milan.game.ui.LocalSharedTransitionScope
import com.milan.game.ui.OwnedCharacterView
import com.milan.game.ui.components.MissingCharacter
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.SectionTitle
import com.milan.game.ui.components.SubPageHero
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme
import com.milan.game.ui.theme.WorldTheme
import kotlin.math.max

// 角色详情面板标签（武器/属性/技能/故事/语音）
private val detailTabs = listOf("武器", "属性", "技能", "故事", "语音")

/**
 * 角色详情页（C# CharacterDetailActivity 翻译）。
 *
 * 布局：Hero（[SubPageHero]：立绘 + 底部渐隐 + 铭牌 + 悬浮操作）→ Tab 栏 + AnimatedContent 面板切换。
 * 面板走 [GameState.computeStats] / [GameState.computeStatsAt]，与养成/战斗同源。
 *
 * P2 未迁移（单 Activity 架构下简化）：视差立绘（Parallax3DPortraitView）、
 * 武器舞台帧动画（WeaponPreviewView）、错落入场动画（Motion.PlayEntrance）、
 * ProgressionChanged 事件就地刷新（Task 11 养成屏接入后按需恢复）。
 */
@Composable
fun CharacterDetailScreen(
    characterId: String,
    onBack: () -> Unit,
    onOpenProgression: (String) -> Unit,
    onSwitchCharacter: (String) -> Unit,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val def = remember(characterId) {
        GameState.service.character(characterId)
    }
    if (def == null) {
        // C# ResolveCharacter 失败 → Finish()；单 Activity 下渲染空态并给返回入口
        MissingCharacter(onBack, modifier)
        return
    }

    // 离开详情页即停播：避免 TTS 跨页残留朗读（引擎本身常驻复用，仅停当前 utterance）
    DisposableEffect(Unit) {
        onDispose { SpeechPlayer.stop() }
    }

    // P2-13/P3-5：订阅快照 revision，任何成功写操作后重组重读最新存档——此前
    // remember(characterId) 缓存 ownedSave 引用，依赖「GameService 原地修改同一对象」的
    // 脆弱契约（resetSave 整体替换存档后，缓存会指向失效对象）。
    val snap by GameState.snapshot.collectAsStateWithLifecycle()
    @Suppress("UNUSED_EXPRESSION")
    snap.revision
    // 路径 B：ownedSaves 随快照刷新（写操作后自动更新），替代 saveData.ownedCharacters.firstOrNull 直读
    val ownedSave = snap.ownedSaves[characterId]
    val owned = ownedSave != null
    // 未拥有兜底存档（Level/Stage/Stars=1）：纯渲染模型，按角色缓存即可（拥有后 ownedSave 优先）。
    val fallbackSave = remember(characterId) {
        CharacterSaveState(characterId = characterId, level = 1, stage = 1, stars = 1)
    }
    val save = ownedSave ?: fallbackSave
    // 视图为轻量值对象，每次重组直接构建（勿 remember 缓存，避免拿到陈旧 save 引用）
    val view = OwnedCharacterView(save, def)

    val world = WorldTheme.forWorld(view.world)
    val rarityCol = AppTheme.rarityColor(view.rarity)
    val (eFrom, _, _, eGlyph) = ElementTheme.forElement(view.element)

    // P3-1：属性计算 remember 化——快照驱动的重组不再重复全表推导，仅 save/def 变化时重算。
    // M3（2026-08-28 审查修复）：key 必须是**稳定值**。原用 remember(view)，而 view 每次重组
    // 都在此处新建（见上方 :86），OwnedCharacterView 又是无 equals 的普通 class →
    // key 恒不相等、记忆化完全失效，与注释意图相反。改用影响推导结果的稳定字段。
    val stats = remember(
        def, save.characterId, save.level, save.stage, save.stars, save.talentPoints.size,
    ) { GameState.computeStats(view) }
    // C# ComputeBaseStats：StatAtLevel(1, stage, 1f) —— stars=1 → 星级倍率 ×1.0
    val baseStats = remember(def, save.characterId, save.stage) {
        GameState.computeStatsAt(view, 1, max(1, save.stage), stars = 1)
    }

    val chars = remember { GameState.service.characters }
    // C# SwitchCharacter：全表循环切换（含未拥有角色，图鉴剪影也能左右浏览）
    fun switch(delta: Int) {
        val idx = chars.indexOfFirst { it.characterId == characterId }
        if (idx < 0) return
        val next = (idx + delta + chars.size) % chars.size
        onSwitchCharacter(chars[next].characterId)
    }

    val heroHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() * 0.56f
    }

    // Shared Element 作用域：AnimatedContent 提供（null 时退化为普通渲染，安全降级）
    // 立绘 sharedBounds（key 全局唯一 = "portrait_${characterId}"，与列表卡片同 key 配对）
    val sharedScope = LocalSharedTransitionScope.current
    val portraitModifier = if (sharedScope != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "portrait_${view.save.characterId}"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = ResizeMode.RemeasureToBounds,
            )
        }
    } else Modifier

    // 水墨视差：滚动时立绘滞后 30%，产生宣纸层叠深度感
    val scrollState = rememberScrollState()
    val heroParallax by remember {
        derivedStateOf { scrollState.value * 0.3f }
    }
    val parallaxPortraitModifier = portraitModifier.graphicsLayer { translationY = heroParallax }

    // ── 面板 Tab 切换状态 ──
    var selectedTab by remember { mutableIntStateOf(0) }

    // 入场淡入+微缩放动画：立绘从 0.95 缩放弹入、淡入 400ms
    var heroEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { heroEntered = true }
    val heroAlpha by animateFloatAsState(
        targetValue = if (heroEntered) 1f else 0f,
        animationSpec = tween(400),
        label = "heroAlpha",
    )
    val heroScale by animateFloatAsState(
        targetValue = if (heroEntered) 1f else 0.95f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
        label = "heroScale",
    )

    PageBackground(modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            SubPageHero(
                view = view,
                rarityCol = rarityCol,
                eFrom = eFrom,
                eGlyph = eGlyph,
                heroHeight = heroHeight,
                fadeHeight = 150.dp,
                owned = owned,
                onOpenProgression = onOpenProgression,
                portraitModifier = parallaxPortraitModifier,
                onBack = onBack,
                onPrev = { switch(-1) },
                onNext = { switch(1) },
                modifier = Modifier.graphicsLayer {
                    alpha = heroAlpha
                    scaleX = heroScale; scaleY = heroScale
                },
            )
            Spacer(Modifier.height(14.dp))

            // ── Tab 栏（云海仙气风格：冰蓝底 + 金箔选中高亮 + 滑动指示器）──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppTheme.Surface.copy(alpha = 0.5f))
                    .border(1.dp, AppTheme.Stroke, RoundedCornerShape(10.dp)),
            ) {
                // 选中项金色滑动指示器
                val indicatorOffset by animateDpAsState(
                    targetValue = (selectedTab * 100 / detailTabs.size).dp,
                    animationSpec = tween(250),
                    label = "tabIndicator",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(1f / detailTabs.size)
                        .offset(x = indicatorOffset)
                        .height(3.dp)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AppTheme.Gold),
                )
                Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                    detailTabs.forEachIndexed { index, label ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedTab = index }
                                .background(
                                    if (isSelected) AppTheme.Gold.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent,
                                )
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) AppTheme.Gold else AppTheme.Text2,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ── AnimatedContent 面板切换（横向滑入/淡入过渡）──
            Column(Modifier.padding(horizontal = 16.dp)) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn(tween(200)) + slideInHorizontally(tween(250)) { it / 6 } togetherWith
                            fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { -it / 6 }
                    },
                    label = "detailPanel",
                ) { tab ->
                    Column {
                        when (tab) {
                            0 -> {
                                // 武器面板
                                if (def.weapon.isNotBlank()) {
                                    WeaponPanel(def = def, view = view, owned = owned, rarityCol = rarityCol, worldColor = world)
                                } else {
                                    Text("暂无专属武器", fontSize = 14.sp, color = AppTheme.Text3)
                                }
                            }
                            1 -> {
                                // 属性面板
                                StatsPanel(view = view, owned = owned, stats = stats, baseStats = baseStats)
                            }
                            2 -> {
                                // 技能面板
                                SkillPanel(def.skills, worldColor = world)
                            }
                            3 -> {
                                // 故事面板
                                StoryPanel(view, worldColor = world)
                            }
                            4 -> {
                                // 语音面板
                                VoicePanel(def.voices, worldColor = world)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
