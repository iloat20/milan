package com.milan.game.domain.battle

/**
 * 战斗技能系统。
 * 
 * 设计：
 * - 每个角色有1个普通攻击和2个主动技能
 * - 主动技能需要消耗能量（通过攻击积累）
 * - 技能有冷却时间
 * - 玩家可以选择释放哪个技能和攻击哪个目标
 */
data class BattleSkill(
    val skillId: String,
    val name: String,
    val description: String,
    val type: SkillType,
    val energyCost: Int = 0,  // 能量消耗（0=普通攻击）
    val cooldown: Int = 0,  // 冷却回合数
    val power: Int = 100,  // 伤害倍率（%）
    val target: SkillTarget = SkillTarget.SINGLE_ENEMY,
    val effects: List<SkillEffect> = emptyList(),
)

/**
 * 技能类型。
 */
enum class SkillType {
    NORMAL,    // 普通攻击
    ACTIVE,    // 主动技能
    ULTIMATE,  // 终极技能
    PASSIVE,   // 被动技能
}

/**
 * 技能目标类型。
 */
enum class SkillTarget {
    SINGLE_ENEMY,    // 单个敌人
    ALL_ENEMIES,     // 所有敌人
    SELF,            // 自身
    SINGLE_ALLY,     // 单个队友
    ALL_ALLIES,      // 所有队友
}

/**
 * 技能效果。
 */
data class SkillEffect(
    val type: EffectType,
    val value: Int,
    val duration: Int = 1,  // 持续回合数
    val chance: Int = 100,  // 触发概率（%）
)

/**
 * 效果类型。
 */
enum class EffectType {
    DAMAGE,      // 伤害
    HEAL,        // 治疗
    BUFF_ATK,    // 攻击增益
    BUFF_DEF,    // 防御增益
    BUFF_SPD,    // 速度增益
    DEBUFF_ATK,  // 攻击减益
    DEBUFF_DEF,  // 防御减益
    DEBUFF_SPD,  // 速度减益
    STUN,        // 眩晕
    POISON,      // 中毒
    BURN,        // 灼烧
}

/**
 * 战斗中的角色状态。
 */
data class BattleUnitState(
    val stats: UnitStats,
    var hp: Int,
    val maxHp: Int,
    var energy: Int = 0,
    val maxEnergy: Int = 100,
    var cooldowns: MutableMap<String, Int> = mutableMapOf(),
    var buffs: MutableList<BuffDebuff> = mutableListOf(),
    var debuffs: MutableList<BuffDebuff> = mutableListOf(),
    val isPlayer: Boolean,
    val skills: List<BattleSkill> = emptyList(),
)

/**
 * 增益/减益效果。
 */
data class BuffDebuff(
    val type: EffectType,
    val value: Int,
    var remainingTurns: Int,
)

/**
 * 玩家输入（策略选择）。
 */
data class PlayerAction(
    val actorIndex: Int,  // 行动角色索引
    val skillId: String,  // 使用的技能ID
    val targetIndex: Int,  // 目标索引
)

/**
 * 战斗状态（用于UI显示和交互）。
 */
data class BattleState(
    val turn: Int,
    val phase: BattlePhase,
    val playerTeam: List<BattleUnitState>,
    val enemyTeam: List<BattleUnitState>,
    val log: List<StrikeEvent> = emptyList(),
    val pendingAction: PlayerAction? = null,  // 待确认的玩家行动
)

/**
 * 战斗阶段。
 */
enum class BattlePhase {
    PLAYER_INPUT,    // 玩家输入阶段
    ANIMATING,       // 动画播放阶段
    ENEMY_TURN,      // 敌方行动阶段
    CHECK_RESULT,    // 结果检查阶段
    VICTORY,         // 胜利
    DEFEAT,          // 失败
    DRAW,            // 平局
}
