package com.milan.game.ui.characters

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
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

/**
 * 角色详情页（C# CharacterDetailActivity 翻译）。
 *
 * 布局：Hero（[SubPageHero]：立绘 + 底部渐隐 + 铭牌 + 悬浮操作）→ 五个面板
 * （武器 [WeaponPanel] / 属性 [StatsPanel] / 技能 [SkillPanel] / 故事 [StoryPanel] / 语音 [VoicePanel]，
 * P4-1 已拆分为同包 WeaponPanel.kt / WoWStatsPanel.kt / InfoPanels.kt）。
 * 属性面板走 [GameState.computeStats] / [GameState.computeStatsAt]，与养成/战斗同源。
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

    // P3-1：属性计算 remember 化——快照驱动的重组不再重复全表推导，仅 save/def 变化时重算
    val stats = remember(view) { GameState.computeStats(view) }
    // C# ComputeBaseStats：StatAtLevel(1, stage, 1f) —— stars=1 → 星级倍率 ×1.0
    val baseStats = remember(view) { GameState.computeStatsAt(view, 1, max(1, save.stage), stars = 1) }

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
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            )
        }
    } else Modifier

    PageBackground(modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
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
                portraitModifier = portraitModifier,
                onBack = onBack,
                onPrev = { switch(-1) },
                onNext = { switch(1) },
            )
            Spacer(Modifier.height(14.dp))

            Column(Modifier.padding(horizontal = 16.dp)) {
                if (def.weapon.isNotBlank()) {
                    SectionTitle("专 属 武 器")
                    Spacer(Modifier.height(10.dp))
                    WeaponPanel(def = def, view = view, owned = owned, rarityCol = rarityCol, worldColor = world)
                    Spacer(Modifier.height(16.dp))
                }

                SectionTitle("基 本 属 性")
                Spacer(Modifier.height(10.dp))
                StatsPanel(
                    view = view,
                    owned = owned,
                    stats = stats,
                    baseStats = baseStats,
                )
                Spacer(Modifier.height(16.dp))

                SectionTitle("技 能")
                Spacer(Modifier.height(10.dp))
                SkillPanel(def.skills, worldColor = world)
                Spacer(Modifier.height(16.dp))

                SectionTitle("背 景 故 事")
                Spacer(Modifier.height(10.dp))
                StoryPanel(view, worldColor = world)
                Spacer(Modifier.height(16.dp))

                SectionTitle("语 音 / 台 词")
                Spacer(Modifier.height(10.dp))
                VoicePanel(def.voices, worldColor = world)

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
