package com.milan.game.ui.progression

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.milan.game.domain.progression.TalentEngine
import com.milan.game.services.TalentNodeData
import com.milan.game.services.WriteOutcome
import com.milan.game.ui.GameState
import com.milan.game.ui.OwnedCharacterView
import com.milan.game.ui.components.GlassArrow
import com.milan.game.ui.components.GlassPanel
import com.milan.game.ui.components.GoldButton
import com.milan.game.ui.components.HeroNameplate
import com.milan.game.ui.components.MissingCharacter
import com.milan.game.ui.components.NeonButton
import com.milan.game.ui.components.PortraitImage
import com.milan.game.ui.components.SectionTitle
import com.milan.game.ui.components.WoWDivider
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 养成系统全屏页（C# ProgressionActivity 翻译，暗夜神性·诸神黄昏）。
 *
 * 布局：Hero（立绘 + 底部渐隐 + 铭牌 + 返回 / 左右切换）→ 资源条（星尘 + 星魂碎片）
 * → 五个玻璃面板：等级与经验 / 突破 / 升星 / 属性 / 天赋树。
 *
 * 所有消费操作走 [GameState.service]（先校验后扣、落盘失败回滚），失败用 Toast 提示
 * 并保持面板原状；成功路径由 GameService 同步广播 CurrencyChanged / ProgressionChanged，
 * 本屏订阅后自动重组刷新（与 C# OnResume 订阅 / OnPause 退订等价）。
 */
@Composable
fun ProgressionScreen(
    characterId: String,
    onBack: () -> Unit,
    onSwitchCharacter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // 状态快照刷新节拍（2026-08 现代化）：GameService 成功写操作后推进 snapshot.revision，
    // 组合中读取 revision 建立重组依赖 → 全屏重读最新存档（替代「EventBus tick 轻标记」）。
    // 失败路径不推进 revision——状态未变，无需重组。
    val snap by GameState.service.snapshot.collectAsStateWithLifecycle()
    // 组合中读取 revision：只 collect 不读字段不会触发重组（revision 是唯一重组触发器）
    @Suppress("UNUSED_EXPRESSION")
    snap.revision

    val def = GameState.service.character(characterId)
    if (def == null) {
        // C# ResolveCharacter 失败 → Finish()；单 Activity 下渲染空态并给返回入口
        MissingCharacter(onBack, modifier)
        return
    }

    // 每次重组重新查存档：GameService 原地修改，直接读最新值（勿 remember 缓存）
    val ownedSave = GameState.service.saveData.ownedCharacters.firstOrNull { it?.characterId == characterId }
    val owned = ownedSave != null
    // C# 未拥有兜底存档（Level/Stage/Stars=1），保证面板可渲染、按钮禁用
    val save = ownedSave ?: CharacterSaveState(characterId = characterId, level = 1, stage = 1, stars = 1)
    val view = OwnedCharacterView(save, def)

    val rarityCol = AppTheme.rarityColor(view.rarity)
    val (eFrom, _, _, eGlyph) = ElementTheme.forElement(view.element)

    val chars = GameState.service.characters
    fun switch(delta: Int) {
        val idx = chars.indexOfFirst { it.characterId == characterId }
        if (idx < 0) return
        val next = (idx + delta + chars.size) % chars.size
        onSwitchCharacter(chars[next].characterId)
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    // 2026-08 主线程 IO 异步化：养成写操作为 suspend（落盘在 IO 线程），用页面协程调用
    val scope = rememberCoroutineScope()

    fun onLevel(n: Int) {
        if (!owned) { toast("未拥有该角色"); return }
        scope.launch {
            when (GameState.service.levelUp(characterId, n)) {
                WriteOutcome.Success -> {}
                WriteOutcome.Rejected -> {
                    val cap = GameState.service.maxLevelForStage(save.stage)
                    toast(if (cap <= save.level) "已满级" else "星尘不足")
                }
                WriteOutcome.SaveFailed -> toast("保存失败，请重试")
            }
        }
    }

    fun onAscend() {
        if (!owned) { toast("未拥有该角色"); return }
        scope.launch {
            when (GameState.service.ascend(characterId)) {
                WriteOutcome.Success -> {}
                WriteOutcome.Rejected -> {
                    val frags = GameState.service.ascendFragments(save.stage)
                    toast(if (GameState.service.getStarFragments() < frags) "星魂碎片不足" else "星尘不足")
                }
                WriteOutcome.SaveFailed -> toast("保存失败，请重试")
            }
        }
    }

    fun onStarUp() {
        if (!owned) { toast("未拥有该角色"); return }
        scope.launch {
            when (GameState.service.starUp(characterId)) {
                WriteOutcome.Success -> {}
                WriteOutcome.Rejected -> toast(if (save.stars >= def.maxStars) "已满星" else "星魂碎片不足")
                WriteOutcome.SaveFailed -> toast("保存失败，请重试")
            }
        }
    }

    fun onTalent(nodeId: String) {
        if (!owned) { toast("未拥有该角色"); return }
        scope.launch {
            when (GameState.service.allocateTalent(characterId, nodeId)) {
                WriteOutcome.Success -> {}
                WriteOutcome.Rejected -> toast("无法满足前置或天赋点不足")
                WriteOutcome.SaveFailed -> toast("保存失败，请重试")
            }
        }
    }

    val heroHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() * 0.46f
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
                rarityCol = rarityCol,
                eFrom = eFrom,
                eGlyph = eGlyph,
                heroHeight = heroHeight,
                onBack = onBack,
                onPrev = { switch(-1) },
                onNext = { switch(1) },
            )
            Spacer(Modifier.height(12.dp))

            ResourceBar()
            Spacer(Modifier.height(12.dp))

            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                SectionTitle("等 级 与 经 验")
                Spacer(Modifier.height(10.dp))
                LevelPanel(
                    view = view,
                    owned = owned,
                    onLevel = ::onLevel,
                )
                Spacer(Modifier.height(14.dp))

                SectionTitle("突 破")
                Spacer(Modifier.height(10.dp))
                AscendPanel(
                    view = view,
                    defMaxStage = def.maxStage,
                    owned = owned,
                    onAscend = ::onAscend,
                )
                Spacer(Modifier.height(14.dp))

                SectionTitle("升 星")
                Spacer(Modifier.height(10.dp))
                StarPanel(
                    view = view,
                    defMaxStars = def.maxStars,
                    owned = owned,
                    onStarUp = ::onStarUp,
                )
                Spacer(Modifier.height(14.dp))

                SectionTitle("属 性")
                Spacer(Modifier.height(10.dp))
                StatsPanel(view = view, defMaxStage = def.maxStage, defMaxStars = def.maxStars)
                Spacer(Modifier.height(14.dp))

                SectionTitle("天 赋")
                Spacer(Modifier.height(10.dp))
                TalentPanel(
                    characterId = characterId,
                    view = view,
                    owned = owned,
                    onTalent = ::onTalent,
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ── HERO：立绘铺满 + 渐隐融入 + 浮层铭牌 + 返回 / 左右切换 ──

@Composable
private fun HeroRegion(
    view: OwnedCharacterView,
    rarityCol: Color,
    eFrom: Color,
    eGlyph: String,
    heroHeight: Dp,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(heroHeight)) {
        // 立绘（C# Parallax3DPortraitView；P2 视差，先用静态铺满）
        PortraitImage(
            characterId = view.save.characterId,
            rarity = view.rarity,
            name = view.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            aura = true,
        )

        // 底部渐隐遮罩：立绘下缘柔和融入背景
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(140.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, AppTheme.BgDeepest))),
        )

        HeroNameplate(
            view = view,
            rarityCol = rarityCol,
            eFrom = eFrom,
            eGlyph = eGlyph,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 返回（左上）
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
        GlassArrow("‹", Modifier.align(Alignment.CenterStart), onPrev)
        GlassArrow("›", Modifier.align(Alignment.CenterEnd), onNext)
    }
}


/** 等宽数字样式（C# UI.Tabular）。 */
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** 千分位格式化（C# ToString("N0")）。 */
private fun n0(v: Int): String = String.format(Locale.US, "%,d", v)

// ── 资源条（C# BuildResourceBar）──

@Composable
private fun ResourceBar() {
    val soft = GameState.service.saveData.softCurrency
    val frags = GameState.service.getStarFragments()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.Surface)
            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(14.dp))
            .padding(start = 16.dp, end = 16.dp),
    ) {
        Chip("✦", AppTheme.Gold, "星尘 ${n0(soft)}")
        Spacer(Modifier.weight(1f))
        Chip("❖", AppTheme.Frost, "星魂碎片 ${n0(frags)}")
    }
}

@Composable
private fun Chip(glyph: String, col: Color, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(glyph, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = col)
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = col,
            style = Tabular,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

// ── 等级 / 经验（C# BuildLevelPanel）──

@Composable
private fun LevelPanel(
    view: OwnedCharacterView,
    owned: Boolean,
    onLevel: (Int) -> Unit,
) {
    val save = view.save
    val cap = GameState.service.maxLevelForStage(save.stage)
    val soft = GameState.service.saveData.softCurrency
    val (cur, need) = GameState.service.expProgress(save.characterId)

    val canLevel = owned && save.level < cap && soft >= GameState.service.levelCost(save.level)

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lv.${save.level}",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Gold,
                    style = Tabular,
                )
                Text(
                    "/ $cap",
                    fontSize = 14.sp,
                    color = AppTheme.Text2,
                    style = Tabular,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "$cur / $need EXP",
                    fontSize = 12.sp,
                    color = AppTheme.Text3,
                    style = Tabular,
                )
            }
            Spacer(Modifier.height(8.dp))

            // 经验条（暗轨 + 金填充，权重控制比例）
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x3C000000)),
            ) {
                // 满级时 cur==need，填充占满整条（C# weight 用 max(0.001, …) 防除零）
                Box(
                    Modifier
                        .fillMaxSize()
                        .weight(cur.toFloat().coerceAtLeast(0.001f))
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppTheme.Gold),
                )
                Box(Modifier.weight((need - cur).toFloat().coerceAtLeast(0.001f)))
            }
            Spacer(Modifier.height(12.dp))

            Row {
                LevelButton("升级 ×1", gold = true, enabled = canLevel) { onLevel(1) }
                LevelButton("升级 ×5", gold = false, enabled = canLevel) { onLevel(5) }
                LevelButton("升满", gold = false, enabled = canLevel) { onLevel(Int.MAX_VALUE) }
            }
        }
    }
}

/** 等宽升级按钮：禁用时半透明（C# 按钮 Enabled + Alpha 0.4 的 Compose 等价）。 */
@Composable
private fun RowScope.LevelButton(
    text: String,
    gold: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .padding(horizontal = 6.dp)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        if (gold) {
            GoldButton(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                onClick = onClick,
                textSize = 15.sp,
                enabled = enabled,
            )
        } else {
            NeonButton(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                onClick = onClick,
                textSize = 15.sp,
                enabled = enabled,
            )
        }
    }
}

// ── 突破（C# BuildAscendPanel）──

@Composable
private fun AscendPanel(
    view: OwnedCharacterView,
    defMaxStage: Int,
    owned: Boolean,
    onAscend: () -> Unit,
) {
    val save = view.save
    val soft = GameState.service.saveData.softCurrency
    val frags = GameState.service.getStarFragments()
    val atMax = save.stage >= defMaxStage
    val aFrag = GameState.service.ascendFragments(save.stage)
    val aSoft = GameState.service.ascendSoft(save.stage)
    val canAscend = owned && !atMax && frags >= aFrag && soft >= aSoft

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (atMax) "突破阶段 ${save.stage} / $defMaxStage（已满）"
                    else "突破阶段 ${save.stage} / $defMaxStage",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Frost,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (atMax) "—" else "❖ ${n0(aFrag)}  +  ✦ ${n0(aSoft)}",
                    fontSize = 13.sp,
                    color = AppTheme.Text2,
                    style = Tabular,
                )
            }
            Spacer(Modifier.height(10.dp))

            Box(Modifier.fillMaxWidth().alpha(if (canAscend) 1f else 0.4f)) {
                GoldButton(
                    text = "突 破",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAscend,
                    textSize = 15.sp,
                    enabled = canAscend,
                )
            }
        }
    }
}

// ── 升星（C# BuildStarPanel）──

@Composable
private fun StarPanel(
    view: OwnedCharacterView,
    defMaxStars: Int,
    owned: Boolean,
    onStarUp: () -> Unit,
) {
    val save = view.save
    val frags = GameState.service.getStarFragments()
    val starMax = save.stars >= defMaxStars
    val sFrag = GameState.service.starUpFragments(save.stars)
    val canStar = owned && !starMax && frags >= sFrag

    // C# 注释铁律：不能用 PadLeft 拼星，Stars 为 0 会画出实心星；手动 repeat 并夹下限
    val filled = "★".repeat(save.stars.coerceAtLeast(0))
    val empty = "☆".repeat((defMaxStars - save.stars).coerceAtLeast(0))

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (starMax) "$filled 满星" else "$filled$empty  ${save.stars}/$defMaxStars",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Frost,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (starMax) "—" else "❖ ${n0(sFrag)}",
                    fontSize = 13.sp,
                    color = AppTheme.Text2,
                    style = Tabular,
                )
            }
            Spacer(Modifier.height(10.dp))

            Box(Modifier.fillMaxWidth().alpha(if (canStar) 1f else 0.4f)) {
                GoldButton(
                    text = "升 星",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStarUp,
                    textSize = 15.sp,
                    enabled = canStar,
                )
            }
        }
    }
}

// ── 属性（C# FillStats：当前值 + 下一级 / 突破 / 升星预测）──

private data class StatRow(
    val cn: String,
    val value: Int,
    val nl: Int?,
    val ns: Int?,
    val nstar: Int?,
)

@Composable
private fun StatsPanel(
    view: OwnedCharacterView,
    defMaxStage: Int,
    defMaxStars: Int,
) {
    val save = view.save
    val cur = GameState.computeStats(view)
    val cap = GameState.service.maxLevelForStage(save.stage)
    val nextLv = if (save.level < cap) GameState.computeStatsAt(view, save.level + 1, save.stage) else null
    val nextStg = if (save.stage < defMaxStage) GameState.computeStatsAt(view, save.level, save.stage + 1) else null
    val nextStar = if (save.stars < defMaxStars) {
        GameState.computeStatsAt(view, save.level, save.stage, save.stars + 1)
    } else null

    val rows = listOf(
        StatRow("攻击", cur.atk, nextLv?.atk, nextStg?.atk, nextStar?.atk),
        StatRow("防御", cur.def, nextLv?.def, nextStg?.def, nextStar?.def),
        StatRow("生命", cur.hp, nextLv?.hp, nextStg?.hp, nextStar?.hp),
        StatRow("速度", cur.spd, nextLv?.spd, nextStg?.spd, nextStar?.spd),
    )

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            rows.forEach { r ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 7.dp, bottom = 7.dp),
                ) {
                    Text(r.cn, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppTheme.Text2)
                    Spacer(Modifier.weight(1f))
                    Text(
                        n0(r.value),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.Text1,
                        style = Tabular,
                    )
                }

                // 预测子行：有哪项预测显示哪项（C# StringBuilder 拼接，青字）
                val parts = buildList {
                    r.nl?.let { add("Lv+1 → ${n0(it)}") }
                    r.ns?.let { add("突破 → ${n0(it)}") }
                    r.nstar?.let { add("升星 → ${n0(it)}") }
                }
                if (parts.isNotEmpty()) {
                    Text(
                        parts.joinToString("   ·   "),
                        fontSize = 12.sp,
                        color = AppTheme.Frost,
                        style = Tabular,
                    )
                }

                WoWDivider()
            }
        }
    }
}

// ── 天赋（C# BuildTalentPanel + FillTalent + BuildTalentNode）──

@Composable
private fun TalentPanel(
    characterId: String,
    view: OwnedCharacterView,
    owned: Boolean,
    onTalent: (String) -> Unit,
) {
    val save = view.save
    val tree = GameState.service.getTalentTree(characterId)
    // C# 空树回退三分支；树节点为 null 时对应分支为空列（防养成界面静默空白）
    val branches = tree?.branchIds
        ?: listOf(TalentEngine.BRANCH_POWER, TalentEngine.BRANCH_DEFENSE, TalentEngine.BRANCH_UTILITY)

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("天赋点", fontSize = 13.sp, color = AppTheme.Text2)
                Text(
                    "× ${save.unspentPoints}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Gold,
                    style = Tabular,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(10.dp))

            Row {
                branches.forEach { br ->
                    val col = branchColor(br)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                    ) {
                        Text(
                            branchName(br),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = col,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))

                        val nodes = tree?.nodes?.filter { it.branchId == br }.orEmpty()
                        nodes.forEach { node ->
                            val isAlloc = save.talentPoints.contains(node.nodeId)
                            val canAlloc = !isAlloc && owned
                                && save.unspentPoints >= node.cost
                                && GameState.service.canAllocateTalent(characterId, node.nodeId)
                            TalentNode(
                                node = node,
                                col = col,
                                allocated = isAlloc,
                                canAlloc = canAlloc,
                                onClick = { onTalent(node.nodeId) },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 分支 → 颜色（C# branchColor 字典；未知分支回退次文字色）。 */
private fun branchColor(br: String): Color = when (br) {
    TalentEngine.BRANCH_POWER -> AppTheme.Danger
    TalentEngine.BRANCH_DEFENSE -> AppTheme.Frost
    TalentEngine.BRANCH_UTILITY -> AppTheme.Violet
    else -> AppTheme.Text2
}

/** 分支 → 中文名（C# branchName 字典；未知分支显示原始 id）。 */
private fun branchName(br: String): String = when (br) {
    TalentEngine.BRANCH_POWER -> "强攻"
    TalentEngine.BRANCH_DEFENSE -> "坚壁"
    TalentEngine.BRANCH_UTILITY -> "灵动"
    else -> br
}

/** 天赋节点：已点=分支色描边+✓，可点=淡色描边可点击，锁定=暗框（C# BuildTalentNode）。 */
@Composable
private fun TalentNode(
    node: TalentNodeData,
    col: Color,
    allocated: Boolean,
    canAlloc: Boolean,
    onClick: () -> Unit,
) {
    val bg = when {
        allocated -> col.copy(alpha = 70f / 255f)
        canAlloc -> col.copy(alpha = 28f / 255f)
        else -> Color(0x1E14101E)
    }
    val stroke = when {
        allocated -> col
        canAlloc -> col
        else -> AppTheme.Stroke
    }
    val strokeWidth = if (allocated || canAlloc) 2 else 1

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(strokeWidth.dp, stroke, RoundedCornerShape(12.dp))
            .clickable(enabled = canAlloc, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            node.displayName + if (allocated) " ✓" else "",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (allocated || canAlloc) col else AppTheme.Text3,
            textAlign = TextAlign.Center,
        )
        Text(
            "耗费 ${node.cost}",
            fontSize = 11.sp,
            color = if (allocated || canAlloc) AppTheme.Text2 else AppTheme.Text3,
            style = Tabular,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
