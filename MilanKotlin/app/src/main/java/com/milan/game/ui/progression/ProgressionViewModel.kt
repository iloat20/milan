package com.milan.game.ui.progression

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.OwnedCharacterView
import com.milan.game.data.CharacterSaveState
import com.milan.game.domain.battle.UnitStats
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.EconomySlice
import com.milan.game.services.GameService
import com.milan.game.services.TalentTreeData
import com.milan.game.services.WriteOutcome
import com.milan.game.ui.stats.CharacterStats
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 养成页 UI 状态（def/save/owned 随快照 revision 刷新；P2-13/P3-5 语义同详情页）。 */
data class ProgressionUiState(
    /** 内容定义；null → Screen 渲染 MissingCharacter 空态（C# ResolveCharacter 失败语义）。 */
    val def: CharacterDataEntry?,
    /** 拥有时的实时存档；未拥有为 Level/Stage/Stars=1 的兜底渲染模型。 */
    val save: CharacterSaveState,
    val owned: Boolean,
    /** 全表角色 id（左右切换用）。 */
    val characterIds: List<String>,
)

/**
 * 养成页只读查询契约（纯面板依赖此接口，不摸进程单例）。
 */
interface ProgressionQueries {
    fun maxLevelForStage(stage: Int): Int
    fun levelCost(level: Int): Int
    fun expProgress(): Pair<Int, Int>
    fun ascendFragments(stage: Int): Int
    fun ascendSoft(stage: Int): Int
    fun starUpFragments(stars: Int): Int
    fun talentTree(): TalentTreeData?
    fun canAllocateTalent(nodeId: String): Boolean
    fun computeStats(view: OwnedCharacterView): UnitStats
    fun computeStatsAt(view: OwnedCharacterView, level: Int, stage: Int, stars: Int = -1): UnitStats
}

/**
 * 角色养成页 ViewModel（2026-09-09 P1-6 E 批，按 characterId 建 VM 实例）。
 *
 * 行为与 VM 化前等价（纯状态搬移）：
 * - 订阅快照，任何成功写操作后重读最新存档（替代组合中读 revision 的重组触发）；
 * - I13 语义保留：busy in-flight 防重入，落盘期间禁用二次触发（防快速双击重复扣费）；
 * - Rejected 细分提示（满级/星尘不足/碎片不足/满星/天赋点不足）逐分支照搬；
 * - 公式/属性查询经 [ProgressionQueries] 暴露给纯面板（Screen 不再碰 GameState.service）。
 */
class ProgressionViewModel(
    private val characterId: String,
    private val service: GameService,
) : ViewModel(), ProgressionQueries {

    override fun maxLevelForStage(stage: Int): Int = service.maxLevelForStage(stage)
    override fun levelCost(level: Int): Int = service.levelCost(level)
    override fun expProgress(): Pair<Int, Int> = service.expProgress(characterId)
    override fun ascendFragments(stage: Int): Int = service.ascendFragments(stage)
    override fun ascendSoft(stage: Int): Int = service.ascendSoft(stage)
    override fun starUpFragments(stars: Int): Int = service.starUpFragments(stars)
    override fun talentTree(): TalentTreeData? = service.getTalentTree(characterId)
    override fun canAllocateTalent(nodeId: String): Boolean =
        service.canAllocateTalent(characterId, nodeId)
    override fun computeStats(view: OwnedCharacterView): UnitStats = CharacterStats.compute(view)
    override fun computeStatsAt(
        view: OwnedCharacterView,
        level: Int,
        stage: Int,
        stars: Int,
    ): UnitStats = CharacterStats.computeAt(view, level, stage, stars)

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<ProgressionUiState> = _uiState.asStateFlow()

    /** 经济切片（资源条 / 突破 / 升星按钮可用性依赖），无关字段变化不惊动本页。 */
    val economy: StateFlow<EconomySlice> = service.economy

    init {
        viewModelScope.launch {
            service.snapshot.collect { _uiState.value = buildState() }
        }
    }

    private fun buildState(): ProgressionUiState {
        val def = service.character(characterId)
        val ownedSave = service.snapshot.value.ownedSaves[characterId]
        return ProgressionUiState(
            def = def,
            // 未拥有兜底存档（Level/Stage/Stars=1）：保证面板可渲染、按钮禁用。
            save = ownedSave ?: CharacterSaveState(
                characterId = characterId, level = 1, stage = 1, stars = 1,
            ),
            owned = ownedSave != null,
            characterIds = service.characters.map { it.characterId },
        )
    }

    /** 养成写操作互斥（I13：in-flight 防重入）。 */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _toasts = Channel<String>(Channel.BUFFERED)
    val toasts: Flow<String> = _toasts.receiveAsFlow()

    /** 通用包裹：未拥有守卫 + busy 防重入 + busy 复位。 */
    private fun runWrite(block: suspend () -> Unit) {
        if (!uiState.value.owned) {
            _toasts.trySend("未拥有该角色")
            return
        }
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }

    /** 升级 n 级。 */
    fun levelUp(n: Int) = runWrite {
        when (service.levelUp(characterId, n)) {
            WriteOutcome.Success -> Unit
            WriteOutcome.Rejected -> {
                val cap = service.maxLevelForStage(uiState.value.save.stage)
                _toasts.send(if (cap <= uiState.value.save.level) "已满级" else "星尘不足")
            }
            WriteOutcome.SaveFailed -> _toasts.send("保存失败，请重试")
        }
    }

    /** 突破（升阶）。 */
    fun ascend() = runWrite {
        when (service.ascend(characterId)) {
            WriteOutcome.Success -> Unit
            WriteOutcome.Rejected -> {
                val frags = service.ascendFragments(uiState.value.save.stage)
                _toasts.send(if (service.getStarFragments() < frags) "星魂碎片不足" else "星尘不足")
            }
            WriteOutcome.SaveFailed -> _toasts.send("保存失败，请重试")
        }
    }

    /** 升星。 */
    fun starUp() = runWrite {
        when (service.starUp(characterId)) {
            WriteOutcome.Success -> Unit
            WriteOutcome.Rejected -> _toasts.send(
                if (uiState.value.save.stars >= (uiState.value.def?.maxStars ?: 0)) "已满星"
                else "星魂碎片不足"
            )
            WriteOutcome.SaveFailed -> _toasts.send("保存失败，请重试")
        }
    }

    /** 点亮天赋节点。 */
    fun allocateTalent(nodeId: String) = runWrite {
        when (service.allocateTalent(characterId, nodeId)) {
            WriteOutcome.Success -> Unit
            WriteOutcome.Rejected -> _toasts.send("无法满足前置或天赋点不足")
            WriteOutcome.SaveFailed -> _toasts.send("保存失败，请重试")
        }
    }
}
