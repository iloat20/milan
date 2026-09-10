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
 * 战斗中的角色状态（2026-09-08 P0-3：**不可变**）。
 *
 * 此前 `hp` / `energy` 为 `var`，`cooldowns` / `buffs` / `debuffs` 为可变集合，
 * 而外层 [BattleState] 是 data class —— `copy()` 只做浅拷贝，新旧状态共享同一批单位对象，
 * 模拟器就地 mutate 会**同时污染入参**，返回值与入参实为同一份数据
 * （回归测试见 StrategicBattleImmutabilityTest）。
 *
 * 现全部改为 `val` + 只读集合：任何变更都必须 `copy()` 出新实例（替换式更新），
 * 由 [StrategicBattleSimulator] 负责把新单位换回队伍列表。
 * 由此状态可安全快照 / 撤销 / 回放 / AI 预演，且无别名突变风险。
 */
data class BattleUnitState(
    val stats: UnitStats,
    val hp: Int,
    val maxHp: Int,
    val energy: Int = 0,
    val maxEnergy: Int = 100,
    val cooldowns: Map<String, Int> = emptyMap(),
    val buffs: List<BuffDebuff> = emptyList(),
    val debuffs: List<BuffDebuff> = emptyList(),
    val isPlayer: Boolean,
    val skills: List<BattleSkill> = emptyList(),
    val elementReactionCooldown: Int = 0,  // 元素反应冷却回合数
    val lastElementUsed: String? = null,  // 上次使用的元素（用于元素反应判定）
)

/**
 * 增益/减益效果（2026-09-08 P0-3：不可变）。
 *
 * 此前 `remainingTurns` 为 `var`，在战斗模拟中被就地修改。
 * 现改为 `val`，任何变更都必须 `copy()` 出新实例。
 */
data class BuffDebuff(
    val type: EffectType,
    val value: Int,
    val remainingTurns: Int,
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
