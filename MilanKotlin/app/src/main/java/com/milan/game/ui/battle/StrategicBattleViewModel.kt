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

/** 战斗 UI 状态（供 Screen 纯渲染）。 */
data class StrategicBattleUi(
    val floor: Int = 0,
    val state: BattleState? = null,
    val currentActor: Int = 0,
    val selectedSkillId: String? = null,
    val selectedTarget: Int? = null,
    val needTarget: Boolean = false,
    val logLines: List<String> = emptyList(),
    val settling: Boolean = false,
    val outcome: TowerOutcome? = null,
    val finished: Boolean = false,
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

    fun start(floor: Int) {
        val state = service.initializeStrategicBattle(floor)
        actedThisTurn = mutableSetOf()
        _ui.value = StrategicBattleUi(
            floor = floor,
            state = state,
            currentActor = firstAlivePlayer(state) ?: 0,
            logLines = listOf("第 ${floor} 层 · 战斗开始"),
        )
    }

    fun selectSkill(skillId: String) {
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
        _ui.value = _ui.value.copy(selectedTarget = index)
    }

    /** 确认行动（单体需已选目标）。 */
    fun confirm() {
        val cur = _ui.value
        val st = cur.state ?: return
        if (cur.finished || cur.settling) return
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
        val lines = next.log.drop(st.log.size).map { strikeLabel(it) }
        _ui.value = cur.copy(
            state = next,
            selectedSkillId = null,
            selectedTarget = null,
            needTarget = false,
            logLines = (cur.logLines + lines).takeLast(40),
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
        // 玩家阶段结束 → 敌方回合
        viewModelScope.launch {
            _ui.value = _ui.value.copy(logLines = _ui.value.logLines + "—— 敌方回合 ——")
            delay(350)
            var s = service.executeStrategicEnemyTurn(st)
            val eLines = s.log.drop(st.log.size).map { strikeLabel(it) }
            s = service.updateStrategicTurnState(s)
            val phase = service.checkStrategicBattleResult(s)
            s = s.copy(phase = phase)
            actedThisTurn = mutableSetOf()
            _ui.value = _ui.value.copy(
                state = s,
                logLines = (_ui.value.logLines + eLines).takeLast(40),
                currentActor = firstAlivePlayer(s) ?: 0,
                selectedSkillId = null,
                needTarget = false,
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
        viewModelScope.launch {
            _ui.value = _ui.value.copy(settling = true)
            val outcome = service.settleStrategicBattle(_ui.value.floor, victory, st.turn)
            _ui.value = _ui.value.copy(settling = false, outcome = outcome, finished = true)
        }
    }

    fun dismiss() {
        _ui.value = _ui.value.copy(outcome = null, finished = false, state = null)
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
