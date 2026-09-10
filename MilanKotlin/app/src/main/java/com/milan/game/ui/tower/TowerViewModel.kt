package com.milan.game.ui.tower

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.OwnedCharacterView
import com.milan.game.ownedView
import com.milan.game.domain.battle.UnitStats
import com.milan.game.domain.progression.EconomyFormulas
import com.milan.game.services.GameService
import com.milan.game.services.TowerOutcome
import com.milan.game.ui.stats.CharacterStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** 爬塔下一层敌方预览（与 TowerService.buildTowerEnemies 同源公式，纯展示）。 */
data class TowerEnemyPreview(
    val floor: Int,
    val count: Int,
    val power: Int,
    val scale: Double,
    val elements: List<String>,
)

/**
 * 爬塔页 UI 状态（纪录/战票/编队/展示推导，随快照 revision 重算）。
 * Screen 只消费本类型渲染，不再调用 EconomyFormulas / CharacterStats。
 */
data class TowerUiState(
    val best: Int,
    val nextFloor: Int,
    val tickets: Int,
    val ticketCost: Int,
    val canChallenge: Boolean,
    /** 编队成员——M5 语义：按编队槽位顺序映射（不是拥有顺序）。 */
    val members: List<OwnedCharacterView>,
    val formationEmpty: Boolean,
    /** 战报单位名解析表（内容表静态映射，进程内不变；战报流水 unitLabel 用）。 */
    val characterNames: Map<String, String> = emptyMap(),
    /** 下一层通关预计星尘（EconomyFormulas 预计算，Screen 禁止就地写数字）。 */
    val nextFloorRewardSoft: Int = 0,
    /** 编队总战力（纯展示推导）。 */
    val teamPower: Int = 0,
    /** 编队成员实时战斗属性（与 Detail/Progression 同口径，按槽位序）。 */
    val teamStats: List<UnitStats> = emptyList(),
    /** 编队成员元素列表（元素克制预览）。 */
    val teamElements: List<String> = emptyList(),
    /** 下一层敌方预览（数量/战力/元素，与服务层生成逻辑同源）。 */
    val enemyPreview: TowerEnemyPreview? = null,
)

/**
 * 无尽之塔页 ViewModel（2026-09-09 P1-6 E 批）。
 *
 * 行为与 VM 化前等价（纯状态搬移）：
 * - 战斗为服务层纯模拟，交互状态机（running/result）从 Composable remember 收敛进 VM；
 * - M5 语义保留：成员按编队槽位顺序映射；
 * - 战力/元素/奖励预览（CharacterStats / EconomyFormulas）预计算进 [TowerUiState]，
 *   Screen 纯渲染；
 * - result 含 Rejected/SaveFailed/Draw 全量结算呈现，[dismissResult] 由结算覆盖层关闭时调用。
 */
class TowerViewModel(
    private val service: GameService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<TowerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // P0 fan-out：merge 经济/进度/编队切片（combine 在纯 JVM 单测会踩 Main dispatcher）。
            merge(service.economy, service.progressSlice, service.roster)
                .collect { _uiState.value = buildState() }
        }
    }

    private fun buildState(): TowerUiState {
        val best = service.progressSlice.value.towerBestFloor
        val tickets = service.economy.value.battleTickets
        val formation = service.roster.value.formation
        val ownedById = service.saveData.ownedCharacters.mapNotNull { ch ->
            ch ?: return@mapNotNull null
            ch.characterId to service.ownedView(ch)
        }.toMap()
        val cost = EconomyFormulas.towerTicketCost()
        val members = formation.mapNotNull { ownedById[it] }
        val teamStats = members.map { CharacterStats.compute(it) }
        val nextFloor = best + 1
        return TowerUiState(
            best = best,
            nextFloor = nextFloor,
            tickets = tickets,
            ticketCost = cost,
            canChallenge = tickets >= cost,
            members = members,
            formationEmpty = formation.isEmpty(),
            characterNames = service.characters.associate { it.characterId to it.displayName },
            nextFloorRewardSoft = EconomyFormulas.towerRewardSoft(nextFloor),
            teamPower = teamStats.sumOf { it.atk },
            teamStats = teamStats,
            teamElements = members.map { it.element },
            enemyPreview = buildEnemyPreview(nextFloor),
        )
    }

    /** 敌方预览：公式与 TowerService.buildTowerEnemies 同源（展示用途，不写存档）。 */
    private fun buildEnemyPreview(floor: Int): TowerEnemyPreview {
        val elements = listOf("Metal", "Wood", "Water", "Flame", "Earth", "Light", "Shadow", "Thunder")
        val rng = kotlin.random.Random(floor * 1_000_003L + 7L)
        val count = EconomyFormulas.towerEnemyCount(floor)
        val scale = EconomyFormulas.towerEnemyStatScale(floor)
        val base = EconomyFormulas.towerEnemyBaseStats()
        val power = (base[0] * scale * count).toInt()
        val enemyElements = List(count) { elements[rng.nextInt(elements.size)] }
        return TowerEnemyPreview(
            floor = floor,
            count = count,
            power = power,
            scale = scale,
            elements = enemyElements,
        )
    }

    /** 战斗进行中标记（结算覆盖层/按钮禁用）。 */
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** 最近一次挑战结算（null = 无）；整体替换，新战斗覆盖旧结算。 */
    private val _result = MutableStateFlow<TowerOutcome?>(null)
    val result: StateFlow<TowerOutcome?> = _result.asStateFlow()

    /** 挑战指定层（nextFloor / best 由 Screen 决定；guard 与原 onClick 逐条等价）。 */
    private fun challengeFloor(floor: Int) {
        if (_running.value) return
        if (service.economy.value.battleTickets < EconomyFormulas.towerTicketCost()) return
        viewModelScope.launch {
            _running.value = true
            try {
                _result.value = service.runTowerFloor(floor)
            } finally {
                _running.value = false
            }
        }
    }

    /** 挑战「最高层 +1」。 */
    fun challengeNext() = challengeFloor(_uiState.value.nextFloor)

    /** 复刷已通最高层（低层速刷拿保底星尘）。 */
    fun retryBest() = challengeFloor(_uiState.value.best)

    /** 结算覆盖层关闭（F2：清空 result，避免退出动画期间状态残留）。 */
    fun dismissResult() {
        _result.value = null
    }
}
