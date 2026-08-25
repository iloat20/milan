package com.milan.game.ui.characters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milan.game.data.CharacterSaveState
import com.milan.game.domain.battle.UnitStats
import com.milan.game.infrastructure.SpeechPlayer
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.SkillData
import com.milan.game.ui.GameState
import com.milan.game.ui.LocalSharedTransitionScope
import com.milan.game.ui.OwnedCharacterView
import com.milan.game.ui.components.GlassArrow
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.HeroNameplate
import com.milan.game.ui.components.MissingCharacter
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.components.SectionTitle
import com.milan.game.ui.components.WoWDivider
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme
import com.milan.game.ui.theme.WorldPalette
import com.milan.game.ui.theme.WorldTheme
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 角色详情页（C# CharacterDetailActivity 翻译）。
 *
 * 布局：Hero（立绘 + 底部渐隐 + 铭牌 + 悬浮操作）→ 五个面板（武器 / 属性 / 技能 / 故事 / 语音）。
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

    val stats = GameState.computeStats(view)
    // C# ComputeBaseStats：StatAtLevel(1, stage, 1f) —— stars=1 → 星级倍率 ×1.0
    val baseStats = GameState.computeStatsAt(view, 1, max(1, save.stage), stars = 1)

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppTheme.BgMid, AppTheme.BgDeepest))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            HeroRegion(
                view = view,
                owned = owned,
                rarityCol = rarityCol,
                eFrom = eFrom,
                eGlyph = eGlyph,
                heroHeight = heroHeight,
                animatedVisibilityScope = animatedVisibilityScope,
                onBack = onBack,
                onOpenProgression = onOpenProgression,
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

// ── HERO：立绘铺满 + 渐隐融入 + 浮层铭牌 + 悬浮操作 ──

@Composable
private fun HeroRegion(
    view: OwnedCharacterView,
    owned: Boolean,
    rarityCol: Color,
    eFrom: Color,
    eGlyph: String,
    heroHeight: Dp,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    onBack: () -> Unit,
    onOpenProgression: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    // Shared Element 作用域：AnimatedContent 提供（null 时退化为普通渲染，安全降级）
    val sharedScope = LocalSharedTransitionScope.current
    // 立绘 Box：sharedBounds（key 全局唯一 = "portrait_${characterId}"，与列表卡片同 key 配对）
    val portraitModifier = if (sharedScope != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "portrait_${view.save.characterId}"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            )
        }
    } else Modifier

    Box(Modifier.fillMaxWidth().height(heroHeight)) {
        // 立绘（C# Parallax3DPortraitView；P2 视差，先用静态铺满）
        PortraitImage(
            characterId = view.save.characterId,
            rarity = view.rarity,
            name = view.name,
            modifier = Modifier.fillMaxSize().then(portraitModifier),
            contentScale = ContentScale.Crop,
            aura = true, // v2：稀有度脚下光环（详情页主立绘）
        )

        // 底部渐隐遮罩：立绘下缘柔和融入背景
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, AppTheme.BgDeepest))),
        )

        // 未拥有遮罩
        if (!owned) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 150f / 255f)),
            )
            Text(
                "🔒 未获得",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Text2,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        HeroNameplate(
            view = view,
            rarityCol = rarityCol,
            eFrom = eFrom,
            eGlyph = eGlyph,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 悬浮操作：返回（左上）+ 左右切换（两侧）+ 养成入口（右上，仅已拥有）
        Text(
            "‹ 返 回",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.Gold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(AppTheme.Surface)
                .border(1.dp, AppTheme.Gold.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(onClick = onBack),
        )
        GlassArrow("‹", Modifier.align(Alignment.CenterStart), onPrev, contentDescription = "上一个")
        GlassArrow("›", Modifier.align(Alignment.CenterEnd), onNext, contentDescription = "下一个")
        if (owned) {
            Text(
                "养 成 ▲",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Gold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp, end = 14.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppTheme.Surface)
                    .border(1.dp, AppTheme.Gold.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 9.dp)
                    .clickable(onClick = { onOpenProgression(view.save.characterId) }),
            )
        }
    }
}


// ── 武器面板（C# BuildWeaponPanel）──

@Composable
private fun WeaponPanel(
    def: CharacterDataEntry,
    view: OwnedCharacterView,
    owned: Boolean,
    rarityCol: Color,
    worldColor: WorldPalette,
) {
    val (eFrom, _, _, eGlyph) = ElementTheme.forElement(view.element)

    GlassPanel(modifier = Modifier.fillMaxWidth(), highlighted = owned) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // 武器舞台：全稀有度展示专属武器（SR/R 武器现已补齐 lore）
            if (def.weaponVfx.isNotBlank()) {
                WeaponStage(weaponVfx = def.weaponVfx, rarityCol = rarityCol, eFrom = eFrom, weaponName = def.weapon)
                Spacer(Modifier.height(10.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    eGlyph,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = eFrom,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(eFrom.copy(alpha = 45f / 255f)),
                )
                Text(
                    def.weapon,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Gold,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 120f / 255f),
                            offset = Offset(0f, 1f),
                            blurRadius = 4f,
                        ),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                )
                // 专属标签（按稀有度动态：UR/SSR/SR/R 专属）
                val ownTag = when (view.rarity) {
                    4 -> "UR 专属"
                    3 -> "SSR 专属"
                    2 -> "SR 专属"
                    else -> "R 专属"
                }
                Text(
                    ownTag,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = rarityCol,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(rarityCol.copy(alpha = 45f / 255f))
                        .border(1.dp, rarityCol.copy(alpha = 150f / 255f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

            if (def.weaponVfx.isNotBlank()) {
                Text(
                    "武器特效 · ${def.weaponVfx}",
                    fontSize = 12.sp,
                    color = worldColor.glow,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Text(
                def.weaponDesc,
                fontSize = 14.sp,
                color = worldColor.textSecondary,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * 武器概念图内存缓存：详情页左右切换角色会高频重进 [WeaponStage]，
 * 无缓存时每次都重复 IO 解码 512² WebP（约 1MB/张）。LRU 上限 8 张 ≈ 8MB，
 * 超出自动驱逐最旧；键为武器 vfx 名。
 */
private val WeaponArtCache = LruCache<String, Bitmap>(8)

/**
 * 武器舞台：圆角暗底 + 元素径向晕染 + 稀有度描边光环（C# WeaponPreviewView 静态帧）。
 * 优先显示 AI 概念图 PNG（assets/weapons/{vfx}.png），缺失回退武器名（C# 的 Canvas 几何回退为 P2）。
 */
@Composable
private fun WeaponStage(
    weaponVfx: String,
    rarityCol: Color,
    eFrom: Color,
    weaponName: String,
) {
    val context = LocalContext.current
    // 武器图已转 WebP（assets/weapons/{vfx}.webp）；IO 线程按 2x 采样解码
    // （1024×1024 原图、显示仅 160.dp 高，解码内存降 4 倍），首帧不卡主线程。
    val weaponBmp by produceState<Bitmap?>(initialValue = null, weaponVfx) {
        // P1-2：key（weaponVfx）变化时先清空旧值，否则切角色瞬间会闪一瞬上一角色的武器图
        value = null
        value = withContext(Dispatchers.IO) {
            WeaponArtCache.get(weaponVfx) ?: runCatching {
                context.assets.open("weapons/$weaponVfx.webp").use { ins ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeStream(ins, null, opts)
                }
            }.getOrNull()?.also { WeaponArtCache.put(weaponVfx, it) }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF101018))
            .border(2.dp, rarityCol.copy(alpha = 200f / 255f), RoundedCornerShape(14.dp)),
    ) {
        // 元素晕染（C# RadialGradient α75→0）
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(eFrom.copy(alpha = 75f / 255f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset.Unspecified,
                        radius = 0.72f,
                    ),
                ),
        )
        val bmp = weaponBmp
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = weaponName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
            )
        } else {
            // 回退：居中武器名（C# Canvas 几何绘制为 P2）
            Text(
                weaponName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Gold.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

// ── 属性面板（C# BuildStatsPanel：魔兽世界风格）──

/** 属性面板页脚用的等宽数字样式（C# UI.Tabular）。 */
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** 魔兽风格加成绿（C# WoWGreen = Color.Rgb(70, 255, 130)，static readonly 等价物）。 */
private val WoWGreen = Color(0xFF46FF82)

/** 次级属性（C# DeriveSecondary 透明公式，仅面板展示）。 */
private data class SecondaryStats(val crit: Int, val haste: Int, val armor: Int, val block: Int)

private fun deriveSecondary(s: UnitStats): SecondaryStats = SecondaryStats(
    crit = (8 + s.atk / 120).coerceIn(8, 60),
    haste = (5 + s.spd * 2).coerceIn(5, 50),
    armor = (s.def * 1.6 + s.hp * 0.05).toInt(),
    block = (3 + s.def / 200).coerceIn(3, 30),
)

@Composable
private fun StatsPanel(
    view: OwnedCharacterView,
    owned: Boolean,
    stats: UnitStats,
    baseStats: UnitStats,
) {
    // 魔兽世界风格角色面板：暗色渐变底 + 金色双描边 + 四角菱形饰钉（C# WoWStatsFrame）
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF16101C), Color(0xFF0B0712))))
            .border(2.dp, AppTheme.Gold, RoundedCornerShape(14.dp))
            .padding(5.dp)
            .border(1.dp, AppTheme.Gold.copy(alpha = 130f / 255f), RoundedCornerShape(12.dp)),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)) {
            // ── 主属性（对应魔兽 力量/敏捷/智力/耐力）──
            WoWSectionHeader("主 属 性")
            Spacer(Modifier.height(4.dp))

            val primaries = listOf(
                Primary("攻", AppTheme.Danger, "攻击", "Attack", stats.atk, stats.atk - baseStats.atk),
                Primary("防", AppTheme.Frost, "防御", "Defense", stats.def, stats.def - baseStats.def),
                Primary("命", AppTheme.Success, "生命", "Health", stats.hp, stats.hp - baseStats.hp),
                Primary("速", AppTheme.Gold, "速度", "Speed", stats.spd, stats.spd - baseStats.spd),
            )
            primaries.forEachIndexed { i, p ->
                WoWStatRow(p.glyph, p.col, p.cn, p.en, p.value.toString(), p.bonus)
                if (i < primaries.size - 1) WoWDivider()
            }

            // ── 次级属性（派生战斗属性，对应魔兽 暴击/急速/护甲/格挡）──
            Spacer(Modifier.height(6.dp))
            WoWGroupDivider()
            Spacer(Modifier.height(6.dp))
            WoWSectionHeader("次 级 属 性")
            Spacer(Modifier.height(4.dp))

            val secCur = deriveSecondary(stats)
            val secBase = deriveSecondary(baseStats)
            val secondaries = listOf(
                Secondary("暴", AppTheme.Warning, "暴击", "Critical", "${secCur.crit}%", secCur.crit - secBase.crit),
                Secondary("急", AppTheme.Violet, "急速", "Haste", "${secCur.haste}%", secCur.haste - secBase.haste),
                Secondary("甲", AppTheme.FrostDeep, "护甲", "Armor", secCur.armor.toString(), secCur.armor - secBase.armor),
                Secondary("挡", AppTheme.GoldDeep, "格挡", "Block", "${secCur.block}%", secCur.block - secBase.block),
            )
            secondaries.forEachIndexed { i, s ->
                WoWStatRow(s.glyph, s.col, s.cn, s.en, s.value, s.bonus)
                if (i < secondaries.size - 1) WoWDivider()
            }

            // 页脚：等级 / 星级 / 天赋点
            WoWDivider()
            Spacer(Modifier.height(4.dp))
            Text(
                "等级 Lv.${view.save.level}   ·   星级 ${"★".repeat(view.save.stars.coerceAtLeast(1))}" +
                    "   ·   天赋点 ${view.save.unspentPoints}" + if (owned) "" else "   ·   未拥有",
                fontSize = 12.sp,
                color = AppTheme.Text2,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        // 四角菱形饰钉（C# WoWStatsFrame.DrawDiamond，GoldHi）
        Canvas(Modifier.matchParentSize()) {
            val d = 4.5.dp.toPx()
            val inset = 5.dp.toPx() + 2.dp.toPx()
            fun diamond(cx: Float, cy: Float) {
                val p = Path().apply {
                    moveTo(cx, cy - d); lineTo(cx + d, cy); lineTo(cx, cy + d); lineTo(cx - d, cy); close()
                }
                drawPath(p, AppTheme.GoldHi)
            }
            diamond(inset, inset)
            diamond(size.width - inset, inset)
            diamond(inset, size.height - inset)
            diamond(size.width - inset, size.height - inset)
        }
    }
}

private data class Primary(val glyph: String, val col: Color, val cn: String, val en: String, val value: Int, val bonus: Int)
private data class Secondary(val glyph: String, val col: Color, val cn: String, val en: String, val value: String, val bonus: Int)

/** 魔兽风格属性行：圆形角色徽章 + 中英名称 + 等宽数值 + 绿色加成（C# WoWStatRow）。 */
@Composable
private fun WoWStatRow(
    glyph: String,
    col: Color,
    cn: String,
    en: String,
    valueText: String,
    bonus: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 9.dp),
    ) {
        StatBadge(glyph, col)
        Column(Modifier.padding(start = 12.dp)) {
            Text(cn, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppTheme.Text1)
            Text(en, fontSize = 10.sp, color = AppTheme.Text3, letterSpacing = 0.08.em)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                valueText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Text1,
                style = Tabular,
            )
            if (bonus > 0) {
                Text(
                    " +$bonus",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WoWGreen,
                    style = Tabular,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/** 圆形角色徽章：暗底 + 角色色描边 + 中文单字字形（C# StatBadge）。 */
@Composable
private fun StatBadge(glyph: String, col: Color) {
    Text(
        glyph,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = col,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(col.copy(alpha = 45f / 255f))
            .border(1.5.dp, col.copy(alpha = 195f / 255f), CircleShape),
    )
}

/** 居中分组标题：两侧金色渐隐线 + ◆ + 标题（C# WoWSectionHeader）。 */
@Composable
private fun WoWSectionHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LineGold(Modifier.weight(1f))
        Text(
            "◆",
            fontSize = 10.sp,
            color = AppTheme.Gold,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.Gold,
            letterSpacing = 0.2.em,
        )
        Text(
            "◆",
            fontSize = 10.sp,
            color = AppTheme.Gold,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        LineGold(Modifier.weight(1f))
    }
}

/** 金色渐隐发丝线（C# LineGold：透明→α130→透明）。 */
@Composable
private fun LineGold(modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        AppTheme.Gold.copy(alpha = 0f),
                        AppTheme.Gold.copy(alpha = 130f / 255f),
                        AppTheme.Gold.copy(alpha = 0f),
                    ),
                ),
            ),
    )
}

/** 组间分隔：线 + 中心 ◆ + 线（C# WoWGroupDivider）。 */
@Composable
private fun WoWGroupDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LineGold(Modifier.weight(1f))
        Text(
            "◆",
            fontSize = 11.sp,
            color = AppTheme.Gold,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        LineGold(Modifier.weight(1f))
    }
}

// ── 技能 / 故事 / 语音面板 ──

@Composable
private fun SkillPanel(
    skills: List<SkillData>,
    worldColor: WorldPalette,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (skills.isEmpty()) {
                Text("暂无技能", fontSize = 14.sp, color = worldColor.textSecondary)
                return@Column
            }
            skills.forEach { sk ->
                val (from, _, _, _) = ElementTheme.forElement(sk.element)
                val typeColor = when (sk.type) {
                    "Ultimate" -> AppTheme.Warning
                    "Active" -> from
                    else -> worldColor.textSecondary
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                ) {
                    Text(
                        "[${sk.type}]",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = typeColor,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        sk.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = worldColor.textPrimary,
                    )
                }
                Text(
                    sk.description,
                    fontSize = 12.sp,
                    color = worldColor.textSecondary,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun StoryPanel(
    view: OwnedCharacterView,
    worldColor: WorldPalette,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                view.lore,
                fontSize = 14.sp,
                color = worldColor.textPrimary,
                lineHeight = 18.sp,
            )
            if (view.def?.story.isNullOrBlank().not()) {
                Text(
                    view.def.story.orEmpty(),
                    fontSize = 14.sp,
                    color = worldColor.textSecondary,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (!view.def?.faction.isNullOrBlank()) {
                Text(
                    "所属势力：${view.def.faction}",
                    fontSize = 12.sp,
                    color = worldColor.glow,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun VoicePanel(
    voices: List<String>,
    worldColor: WorldPalette,
) {
    val context = LocalContext.current
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (voices.isEmpty()) {
                Text("暂无语音", fontSize = 14.sp, color = worldColor.textSecondary)
                return@Column
            }
            voices.forEach { v ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 6.dp),
                ) {
                    Text(
                        "▸",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = worldColor.glow,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(
                        v,
                        fontSize = 14.sp,
                        color = worldColor.textSecondary,
                        lineHeight = 17.sp,
                        modifier = Modifier.weight(1f),
                    )
                    // TTS 播报（2026-08：面板从纯文本升级为可播；引擎惰性初始化，失败静默）
                    Text(
                        "▶",
                        fontSize = 13.sp,
                        color = AppTheme.Gold,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .clip(CircleShape)
                            .clickable { SpeechPlayer.speak(context, v) }
                            .padding(4.dp),
                    )
                }
            }
        }
    }
}
