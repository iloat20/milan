package com.milan.game.ui.progression

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milan.game.di.AppGraph
import com.milan.game.OwnedCharacterView
import com.milan.game.ui.components.EntranceItem
import com.milan.game.ui.components.MissingCharacter
import com.milan.game.ui.components.PageBackground
import com.milan.game.ui.components.SectionTitle
import com.milan.game.ui.components.SubPageHero
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.theme.AppTheme
import com.milan.game.ui.theme.ElementTheme

/**
 * 养成系统全屏页（C# ProgressionActivity 翻译，暗夜神性·诸神黄昏）。
 *
 * 布局：Hero（[SubPageHero]：立绘 + 底部渐隐 + 铭牌 + 返回 / 左右切换）→ 资源条（星尘 + 星魂碎片）
 * → 五个玻璃面板：等级与经验 / 突破 / 升星 / 属性 / 天赋树
 * （P4-2 已拆分为同包 ProgressionPanels.kt，本文件只留编排）。
 *
 * 2026-09-09 P1-6 E 批：def/save/owned 派生与四类养成写操作（I13 防重入 + Rejected 细分提示）
 * 全部收敛进 [ProgressionViewModel]；属性推导（computeStatsAt）为纯展示计算，仍在面板内。
 */
@Composable
fun ProgressionScreen(
    characterId: String,
    onBack: () -> Unit,
    onSwitchCharacter: (String) -> Unit,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    // R3/I4：反馈统一走 LocalFeedback（由 MainActivity 提供的 Snackbar 宿主）。
    val feedback = LocalFeedback.current

    // P1-6 E 批：按 characterId 建 VM 实例（切角色 = 换路由参数 = 新 VM）。
    // 构造经 AppGraph 组合根工厂注入服务——Screen 不再触碰 GameState.service。
    val vm: ProgressionViewModel = viewModel(
        key = characterId,
        factory = AppGraph.progressionFactory(characterId),
    )
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    // 经济资源走 economy 切片（无关字段变化不重组本页）。
    val eco by vm.economy.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.toasts.collect { feedback.show(it) } }

    val def = ui.def
    if (def == null) {
        // C# ResolveCharacter 失败 → Finish()；单 Activity 下渲染空态并给返回入口
        MissingCharacter(onBack, modifier)
        return
    }

    // 拥有时的实时存档（VM 随快照刷新）；未拥有为 Level/Stage/Stars=1 兜底渲染模型
    val owned = ui.owned
    val save = ui.save
    val view = OwnedCharacterView(save, def, vm.talentTree())

    val rarityCol = AppTheme.rarityColor(view.rarity)
    val (eFrom, _, _, eGlyph) = ElementTheme.forElement(view.element)

    val chars = ui.characterIds
    fun switch(delta: Int) {
        val idx = chars.indexOf(characterId)
        if (idx < 0) return
        val next = (idx + delta + chars.size) % chars.size
        onSwitchCharacter(chars[next])
    }

    // P1-6 E 批：写动作透传 VM（未拥有守卫 / I13 防重入 / Rejected 细分提示全在 VM 内）。
    fun onLevel(n: Int) = vm.levelUp(n)
    fun onAscend() = vm.ascend()
    fun onStarUp() = vm.starUp()
    fun onTalent(nodeId: String) = vm.allocateTalent(nodeId)

    val heroHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() * 0.46f
    }

    val scrollState = rememberScrollState()
    val scrollProgress by remember {
        derivedStateOf {
            if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue else 0f
        }
    }

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
                onBack = onBack,
                onPrev = { switch(-1) },
                onNext = { switch(1) },
                portraitModifier = Modifier.graphicsLayer {
                    translationY = scrollProgress * 120f
                    alpha = 1f - scrollProgress * 0.15f
                },
                animatedVisibilityScope = animatedVisibilityScope,
            )
            Spacer(Modifier.height(12.dp))

            ResourceBar(
                softCurrency = eco.softCurrency,
                starFragments = eco.starFragments,
            )
            Spacer(Modifier.height(12.dp))

            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                SectionTitle("等 级 与 经 验")
                Spacer(Modifier.height(10.dp))
                EntranceItem(index = 0) {
                    LevelPanel(
                        view = view,
                        owned = owned,
                        queries = vm,
                        softCurrency = eco.softCurrency,
                        onLevel = ::onLevel,
                        busy = busy,
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
                        queries = vm,
                        softCurrency = eco.softCurrency,
                        starFragments = eco.starFragments,
                        onAscend = ::onAscend,
                        busy = busy,
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
                        queries = vm,
                        starFragments = eco.starFragments,
                        onStarUp = ::onStarUp,
                        busy = busy,
                    )
                }
                Spacer(Modifier.height(14.dp))

                SectionTitle("属 性")
                Spacer(Modifier.height(10.dp))
                EntranceItem(index = 3) {
                    StatsPanel(
                        view = view,
                        defMaxStage = def.maxStage,
                        defMaxStars = def.maxStars,
                        queries = vm,
                    )
                }
                Spacer(Modifier.height(14.dp))

                SectionTitle("天 赋")
                Spacer(Modifier.height(10.dp))
                EntranceItem(index = 4) {
                    TalentPanel(
                        view = view,
                        owned = owned,
                        queries = vm,
                        onTalent = ::onTalent,
                        busy = busy,
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
