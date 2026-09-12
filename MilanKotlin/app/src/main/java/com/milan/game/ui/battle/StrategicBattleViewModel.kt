package com.milan.game.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.domain.battle.BattlePhase
import com.milan.game.domain.battle.BattleState
import com.milan.game.domain.battle.BattleUnitState
import com.milan.game.domain.battle.PlayerAction
import com.milan.game.domain.battle.SkillTarget
import com.milan.game.services.GameService
import com.milan.game.services.TowerOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 战斗场景：塔层 vs 深渊层（2026-09-12 Dungeon keep 接真战斗）。 */
enum class StrategicBattleMode { TOWER, ABYSS }

/** 战斗 UI 状态（供 Screen 纯渲染）。 */
data class StrategicBattleUi(
    val floor: Int = 0,
    val mode: StrategicBattleMode = StrategicBattleMode.TOWER,
    val state: BattleState? = null,
    val currentActor: Int = 0,
    val selectedSkillId: String? = null,
    val selectedTarget: Int? = null,
    val needTarget: Boolean = false,
    val logLines: List<String> = emptyList(),
    val settling: Boolean = false,
    val outcome: TowerOutcome? = null,
    val finished: Boolean = false,
    /** 敌方回合进行中：技能栏必须禁用，防 delay 期间玩家再出招覆盖状态（R6-P0-4）。 */
    val enemyActing: Boolean = false,
    /** 本批新打击演出脉冲（Screen 播完后 clearFx）。 */
    val fxPulses: List<StrikeFxPulse> = emptyList(),
    /** 深渊局结算星级（塔局为 null）。 */
    val abyssStars: Int? = null,
    /** 深渊局写档结果。 */
    val abyssWrite: com.milan.game.services.WriteOutcome? = null,
)

/**
 * 策略回合战斗 ViewModel（2026-09-09 可操作战斗）。
 *
 * 流程：start → 玩家逐角色行动（选技能/选目标）→ 敌方回合 → 下一回合
 * → VICTORY/DEFEAT → settleStrategicBattle 写盘发奖。
 */
class StrategicBattleViewModel(
    private val service: GameService,
) : ViewModel() {

    private val _ui = MutableStateFlow(StrategicBattleUi())
    val ui: StateFlow<StrategicBattleUi> = _ui.asStateFlow()

    private var actedThisTurn = mutableSetOf<Int>()
    private var fxSeq = 0L
    /** 本局敌方回合/结算协程（R7-P0-4：重进必须取消旧局，防止覆盖新状态）。 */
    private var battleJob: kotlinx.coroutines.Job? = null

    fun start(floor: Int, mode: StrategicBattleMode = StrategicBattleMode.TOWER) {
        battleJob?.cancel()
        battleJob = null
        if (mode == StrategicBattleMode.ABYSS) {
            // 深渊：先计入挑战次数，再开策略战斗（结算在 settle 里 completeAbyssStage）
            viewModelScope.launch {
                service.challengeAbyss(floor)
                openBattle(floor, mode)
            }
        } else {
            openBattle(floor, mode)
        }
    }

    private fun openBattle(floor: Int, mode: StrategicBattleMode) {
        // 深渊走 EconomyFormulas 深渊档（属性 ×1.25、人数 +1、seed 盐不同）
        val state = if (mode == StrategicBattleMode.ABYSS) {
            service.initializeAbyssStrategicBattle(floor)
        } else {
            service.initializeStrategicBattle(floor)
        }
        actedThisTurn = mutableSetOf()
        fxSeq = 0L
        _ui.value = StrategicBattleUi(
            floor = floor,
            mode = mode,
            state = state,
            currentActor = firstAlivePlayer(state) ?: 0,
            logLines = listOf(
                if (mode == StrategicBattleMode.ABYSS) "深渊第 $floor 层 · 战斗开始"
                else "第 $floor 层 · 战斗开始",
            ),
        )
    }

    /** Screen 播完本批脉冲后清空，避免重组重播。 */
    fun clearFx() {
        _ui.value = _ui.value.copy(fxPulses = emptyList())
    }

    private fun buildPulses(
        events: List<com.milan.game.domain.battle.StrikeEvent>,
        energyCost: Int,
        playerIds: Set<String>,
    ): List<StrikeFxPulse> {
        val kind = strikeFxKindOf(energyCost)
        return events.map { e ->
            StrikeFxPulse(
                id = ++fxSeq,
                attackerId = e.attackerId,
                targetId = e.targetId,
                attackerIsPlayer = e.attackerId in playerIds,
                targetIsPlayer = e.targetId in playerIds,
                kind = kind,
                targetDefeated = e.targetDefeated,
                damage = e.damage,
                attackerElement = e.attackerElement,
            )
        }
    }

    fun selectSkill(skillId: String) {
        if (_ui.value.enemyActing || _ui.value.finished || _ui.value.settling) return
        val st = _ui.value.state ?: return
        val actor = st.playerTeam.getOrNull(_ui.value.currentActor) ?: return
        val skill = actor.skills.firstOrNull { it.skillId == skillId } ?: return
        val need = skill.target == SkillTarget.SINGLE_ENEMY || skill.target == SkillTarget.SINGLE_ALLY
        val defaultTarget = if (skill.target == SkillTarget.SINGLE_ENEMY) {
            firstAliveEnemy(st) ?: 0
        } else {
            _ui.value.currentActor
        }
        _ui.value = _ui.value.copy(
            selectedSkillId = skillId,
            needTarget = need,
            selectedTarget = if (need) defaultTarget else null,
        )
        // 群攻/自身：选完技能即可确认
        if (!need) confirm()
    }

    fun selectTarget(index: Int) {
        if (_ui.value.enemyActing) return
        _ui.value = _ui.value.copy(selectedTarget = index)
    }

    /** 确认行动（单体需已选目标）。 */
    fun confirm() {
        val cur = _ui.value
        val st = cur.state ?: return
        // R6-P0-4：敌方回合 / 结算中禁止再出招，否则用旧 st 双执行并被敌方结果覆盖
        if (cur.finished || cur.settling || cur.enemyActing) return
        val skillId = cur.selectedSkillId ?: return
        val actor = st.playerTeam.getOrNull(cur.currentActor) ?: return
        if (actor.hp <= 0) {
            advanceActor(st)
            return
        }
        val skill = actor.skills.firstOrNull { it.skillId == skillId } ?: return
        val target = if (cur.needTarget) cur.selectedTarget ?: return else 0
        if (actor.energy < skill.energyCost || (actor.cooldowns[skillId] ?: 0) > 0) return

        var next = service.executeStrategicAction(st, PlayerAction(cur.currentActor, skillId, target))
        val newEvents = next.log.drop(st.log.size)
        val lines = newEvents.map { strikeLabel(it) }
        val playerIds = st.playerTeam.map { it.stats.characterId }.toSet()
        val pulses = buildPulses(newEvents, skill.energyCost, playerIds)
        _ui.value = cur.copy(
            state = next,
            selectedSkillId = null,
            selectedTarget = null,
            needTarget = false,
            logLines = (cur.logLines + lines).takeLast(40),
            fxPulses = pulses,
        )
        actedThisTurn += cur.currentActor
        advanceActor(next)
    }

    private fun advanceActor(st: BattleState) {
        // 找下一个未行动且存活的玩家单位
        val next = st.playerTeam.indices.firstOrNull { i ->
            i !in actedThisTurn && (st.playerTeam.getOrNull(i)?.hp ?: 0) > 0
        }
        if (next != null) {
            _ui.value = _ui.value.copy(currentActor = next)
            return
        }
        // 玩家阶段结束 → 敌方回合（先上锁，防 delay 窗口双击）
        _ui.value = _ui.value.copy(
            enemyActing = true,
            selectedSkillId = null,
            selectedTarget = null,
            needTarget = false,
            logLines = _ui.value.logLines + "—— 敌方回合 ——",
        )
        battleJob = viewModelScope.launch {
            delay(350)
            var s = service.executeStrategicEnemyTurn(st)
            val eEvents = s.log.drop(st.log.size)
            val eLines = eEvents.map { strikeLabel(it) }
            s = service.updateStrategicTurnState(s)
            val phase = service.checkStrategicBattleResult(s)
            s = s.copy(phase = phase)
            actedThisTurn = mutableSetOf()
            val playerIds = s.playerTeam.map { it.stats.characterId }.toSet()
            // 敌方行动按普攻档演出（无技能上下文时用伤害粗分）
            val ePulses = eEvents.map { e ->
                StrikeFxPulse(
                    id = ++fxSeq,
                    attackerId = e.attackerId,
                    targetId = e.targetId,
                    attackerIsPlayer = e.attackerId in playerIds,
                    targetIsPlayer = e.targetId in playerIds,
                    kind = if (e.damage >= 120) StrikeFxKind.ULTIMATE else if (e.damage >= 60) StrikeFxKind.SKILL else StrikeFxKind.NORMAL,
                    targetDefeated = e.targetDefeated,
                    damage = e.damage,
                    attackerElement = e.attackerElement,
                )
            }
            _ui.value = _ui.value.copy(
                state = s,
                logLines = (_ui.value.logLines + eLines).takeLast(40),
                currentActor = firstAlivePlayer(s) ?: 0,
                selectedSkillId = null,
                needTarget = false,
                enemyActing = false,
                fxPulses = ePulses,
            )
            when (phase) {
                BattlePhase.VICTORY, BattlePhase.DEFEAT, BattlePhase.DRAW -> settle()
                else -> {
                    val auto = s.playerTeam.getOrNull(_ui.value.currentActor)
                    // 默认预选普攻，降低操作摩擦
                    if (auto != null && auto.hp > 0) {
                        val normal = auto.skills.firstOrNull { it.energyCost == 0 }
                        if (normal != null) {
                            _ui.value = _ui.value.copy(
                                selectedSkillId = normal.skillId,
                                needTarget = normal.target == SkillTarget.SINGLE_ENEMY,
                                selectedTarget = if (normal.target == SkillTarget.SINGLE_ENEMY) {
                                    firstAliveEnemy(s) ?: 0
                                } else null,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun settle() {
        val st = _ui.value.state ?: return
        val victory = st.phase == BattlePhase.VICTORY
        val floor = _ui.value.floor
        val mode = _ui.value.mode
        battleJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(settling = true)
            if (mode == StrategicBattleMode.ABYSS) {
                // 星级：无阵亡 3★ / 1 人阵亡 2★ / 其余胜局 1★；失败不发奖
                val dead = st.playerTeam.count { it.hp <= 0 }
                val stars = when {
                    !victory -> 0
                    dead == 0 -> 3
                    dead == 1 -> 2
                    else -> 1
                }
                val write = if (victory && stars > 0) {
                    service.completeAbyssStage(floor, stars)
                } else {
                    com.milan.game.services.WriteOutcome.Rejected
                }
                // 结算 UI 复用 BattleResultOverlay：用合成 Completed 展示星尘/星级语义
                val rewardSoft = if (write == com.milan.game.services.WriteOutcome.Success && stars > 0) {
                    floor * 500 * stars
                } else 0
                val synthetic = TowerOutcome.Completed(
                    victory = victory,
                    turns = st.turn,
                    rewardSoft = rewardSoft,
                    rewardHard = 0,
                    bestFloorAfter = floor,
                    log = st.log,
                    rewardExp = 0,
                    recordAdvanced = victory && stars > 0,
                )
                _ui.value = _ui.value.copy(
                    settling = false,
                    outcome = synthetic,
                    finished = true,
                    abyssStars = stars,
                    abyssWrite = write,
                )
            } else {
                // R6-P2：必须带日志，服务端 StrategicSettleGuard 校验末刀阵营
                val outcome = service.settleStrategicBattle(
                    floor = floor,
                    victory = victory,
                    turns = st.turn,
                    battleLog = st.log,
                )
                _ui.value = _ui.value.copy(settling = false, outcome = outcome, finished = true)
            }
        }
    }

    fun dismiss() {
        battleJob?.cancel()
        battleJob = null
        _ui.value = _ui.value.copy(outcome = null, finished = false, state = null)
    }

    override fun onCleared() {
        battleJob?.cancel()
        battleJob = null
        super.onCleared()
    }

    private fun firstAlivePlayer(st: BattleState): Int? =
        st.playerTeam.indexOfFirst { it.hp > 0 }.takeIf { it >= 0 }

    private fun firstAliveEnemy(st: BattleState): Int? =
        st.enemyTeam.indexOfFirst { it.hp > 0 }.takeIf { it >= 0 }

    private fun strikeLabel(e: com.milan.game.domain.battle.StrikeEvent): String {
        val tag = if (e.isElementReaction) "【${e.reactionName}】" else ""
        val kill = if (e.targetDefeated) " 击倒！" else ""
        return "$tag${e.attackerId} → ${e.targetId}  -${e.damage}$kill"
    }
}

/** 当前行动角色的技能是否可用。 */
fun skillReady(actor: BattleUnitState, skillId: String): Boolean {
    val skill = actor.skills.firstOrNull { it.skillId == skillId } ?: return false
    if (actor.energy < skill.energyCost) return false
    if ((actor.cooldowns[skillId] ?: 0) > 0) return false
    return true
}
