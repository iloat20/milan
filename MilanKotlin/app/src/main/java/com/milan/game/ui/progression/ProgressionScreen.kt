package com.milan.game.ui.progression

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.milan.game.data.CharacterSaveState
import com.milan.game.services.WriteOutcome
import com.milan.game.ui.GameState
import com.milan.game.ui.OwnedCharacterView
import com.milan.game.ui.components.EntranceItem
import com.milan.game.ui.components.MissingCharacter
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.SectionTitle
import com.milan.game.ui.components.SubPageHero
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme
import kotlinx.coroutines.launch

/**
 * 养成系统全屏页（C# ProgressionActivity 翻译，暗夜神性·诸神黄昏）。
 *
 * 布局：Hero（[SubPageHero]：立绘 + 底部渐隐 + 铭牌 + 返回 / 左右切换）→ 资源条（星尘 + 星魂碎片）
 * → 五个玻璃面板：等级与经验 / 突破 / 升星 / 属性 / 天赋树
 * （P4-2 已拆分为同包 ProgressionPanels.kt，本文件只留编排与写操作回调）。
 *
 * 所有消费操作走 [GameState.service]（先校验后扣、落盘失败回滚），失败用 Snackbar 提示
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
    // R3/I4：反馈统一走 LocalFeedback（由 MainActivity 提供的 Snackbar 宿主）。
    val feedback = LocalFeedback.current

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
    // 路径 B：ownedSaves 随快照刷新（写操作后自动更新），替代 saveData.ownedCharacters.firstOrNull 直读
    val ownedSave = snap.ownedSaves[characterId]
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

    // 2026-08 主线程 IO 异步化：养成写操作为 suspend（落盘在 IO 线程），用页面协程调用
    val scope = rememberCoroutineScope()
    // I13：in-flight 防重入——养成写操作落盘期间禁用二次触发，避免快速双击重复扣费。
    var busy by remember { mutableStateOf(false) }
    // R3/I4：反馈统一走 LocalFeedback（Snackbar 宿主）。
    fun notify(msg: String) { scope.launch { feedback.show(msg) } }
    /** 包裹一次养成写操作：自带 in-flight 防重入 + 落盘后复位。 */
    fun runProgression(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch { try { block() } finally { busy = false } }
    }

    fun onLevel(n: Int) {
        if (!owned) { notify("未拥有该角色"); return }
        runProgression {
            when (GameState.service.levelUp(characterId, n)) {
                WriteOutcome.Success -> {}
                WriteOutcome.Rejected -> {
                    val cap = GameState.service.maxLevelForStage(save.stage)
                    notify(if (cap <= save.level) "已满级" else "星尘不足")
                }
                WriteOutcome.SaveFailed -> notify("保存失败，请重试")
            }
        }
    }

    fun onAscend() {
        if (!owned) { notify("未拥有该角色"); return }
        runProgression {
            when (GameState.service.ascend(characterId)) {
                WriteOutcome.Success -> {}
                WriteOutcome.Rejected -> {
                    val frags = GameState.service.ascendFragments(save.stage)
                    notify(if (GameState.service.getStarFragments() < frags) "星魂碎片不足" else "星尘不足")
                }
                WriteOutcome.SaveFailed -> notify("保存失败，请重试")
            }
        }
    }

    fun onStarUp() {
        if (!owned) { notify("未拥有该角色"); return }
        runProgression {
            when (GameState.service.starUp(characterId)) {
                WriteOutcome.Success -> {}
                WriteOutcome.Rejected -> notify(if (save.stars >= def.maxStars) "已满星" else "星魂碎片不足")
                WriteOutcome.SaveFailed -> notify("保存失败，请重试")
            }
        }
    }

    fun onTalent(nodeId: String) {
        if (!owned) { notify("未拥有该角色"); return }
        runProgression {
            when (GameState.service.allocateTalent(characterId, nodeId)) {
                WriteOutcome.Success -> {}
                WriteOutcome.Rejected -> notify("无法满足前置或天赋点不足")
                WriteOutcome.SaveFailed -> notify("保存失败，请重试")
            }
        }
    }

    val heroHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() * 0.46f
    }

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
                EntranceItem(index = 0) {
                    LevelPanel(
                        view = view,
                        owned = owned,
                        onLevel = ::onLevel,
                    )
                }
                Spacer(Modifier.height(14.dp))

                SectionTitle("突 破")
                Spacer(Modifier.height(10.dp))
                EntranceItem(index = 1) {
                    AscendPanel(
                        view = view,
                        defMaxStage = def.maxStage,
                        owned = owned,
                        onAscend = ::onAscend,
                    )
                }
                Spacer(Modifier.height(14.dp))

                SectionTitle("升 星")
                Spacer(Modifier.height(10.dp))
                EntranceItem(index = 2) {
                    StarPanel(
                        view = view,
                        defMaxStars = def.maxStars,
                        owned = owned,
                        onStarUp = ::onStarUp,
                    )
                }
                Spacer(Modifier.height(14.dp))

                SectionTitle("属 性")
                Spacer(Modifier.height(10.dp))
                EntranceItem(index = 3) {
                    StatsPanel(view = view, defMaxStage = def.maxStage, defMaxStars = def.maxStars)
                }
                Spacer(Modifier.height(14.dp))

                SectionTitle("天 赋")
                Spacer(Modifier.height(10.dp))
                EntranceItem(index = 4) {
                    TalentPanel(
                        characterId = characterId,
                        view = view,
                        owned = owned,
                        onTalent = ::onTalent,
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
