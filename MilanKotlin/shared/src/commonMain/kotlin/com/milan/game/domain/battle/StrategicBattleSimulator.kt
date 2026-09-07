package com.milan.game.domain.battle

import kotlin.random.Random

/**
 * 策略战斗模拟器（支持玩家输入和技能释放）。
 * 
 * 与原有BattleSimulator的区别：
 * - 支持玩家选择技能和目标
 * - 有能量系统和冷却系统
 * - 有增益/减益效果
 * - 支持多种目标类型
 */
class StrategicBattleSimulator(private val rng: Random) {

    /**
     * 初始化战斗状态。
     * 
     * @param playerTeam 玩家队伍
     * @param enemyTeam 敌方队伍
     * @return 初始战斗状态
     */
    fun initializeBattle(
        playerTeam: List<UnitStats>,
        enemyTeam: List<UnitStats>,
    ): BattleState {
        val playerStates = playerTeam.map { stats ->
            BattleUnitState(
                stats = stats,
                hp = stats.hp,
                maxHp = stats.hp,
                energy = 0,
                maxEnergy = 100,
                cooldowns = mutableMapOf(),
                buffs = mutableListOf(),
                debuffs = mutableListOf(),
                isPlayer = true,
                skills = getDefaultSkills(stats.characterId),
            )
        }
        
        val enemyStates = enemyTeam.map { stats ->
            BattleUnitState(
                stats = stats,
                hp = stats.hp,
                maxHp = stats.hp,
                energy = 0,
                maxEnergy = 100,
                cooldowns = mutableMapOf(),
                buffs = mutableListOf(),
                debuffs = mutableListOf(),
                isPlayer = false,
                skills = getDefaultSkills(stats.characterId),
            )
        }
        
        return BattleState(
            turn = 1,
            phase = BattlePhase.PLAYER_INPUT,
            playerTeam = playerStates,
            enemyTeam = enemyStates,
            log = emptyList(),
        )
    }
    
    /**
     * 获取默认技能（根据角色ID）。
     */
    private fun getDefaultSkills(characterId: String): List<BattleSkill> {
        // 暂时返回通用技能，后续可以根据角色ID返回特定技能
        return listOf(
            BattleSkill(
                skillId = "normal_attack",
                name = "普通攻击",
                description = "进行一次普通攻击",
                type = SkillType.NORMAL,
                energyCost = 0,
                cooldown = 0,
                power = 100,
                target = SkillTarget.SINGLE_ENEMY,
            ),
            BattleSkill(
                skillId = "skill_1",
                name = "技能一",
                description = "释放技能一，造成大量伤害",
                type = SkillType.ACTIVE,
                energyCost = 30,
                cooldown = 2,
                power = 150,
                target = SkillTarget.SINGLE_ENEMY,
                effects = listOf(
                    SkillEffect(EffectType.DAMAGE, 150, 1, 100),
                ),
            ),
            BattleSkill(
                skillId = "skill_2",
                name = "技能二",
                description = "释放技能二，攻击所有敌人",
                type = SkillType.ACTIVE,
                energyCost = 50,
                cooldown = 3,
                power = 120,
                target = SkillTarget.ALL_ENEMIES,
                effects = listOf(
                    SkillEffect(EffectType.DAMAGE, 120, 1, 100),
                ),
            ),
            BattleSkill(
                skillId = "ultimate",
                name = "终极技能",
                description = "释放终极技能，造成巨额伤害",
                type = SkillType.ULTIMATE,
                energyCost = 100,
                cooldown = 4,
                power = 200,
                target = SkillTarget.ALL_ENEMIES,
                effects = listOf(
                    SkillEffect(EffectType.DAMAGE, 200, 1, 100),
                    SkillEffect(EffectType.DEBUFF_DEF, 20, 2, 80),
                ),
            ),
        )
    }
    
    /**
     * 执行玩家行动。
     * 
     * @param state 当前战斗状态
     * @param action 玩家行动
     * @return 新的战斗状态
     */
    fun executePlayerAction(state: BattleState, action: PlayerAction): BattleState {
        val actor = state.playerTeam.getOrNull(action.actorIndex) ?: return state
        val skill = actor.skills.firstOrNull { it.skillId == action.skillId } ?: return state
        
        // 检查能量是否足够
        if (actor.energy < skill.energyCost) return state
        
        // 检查冷却是否就绪
        val currentCooldown = actor.cooldowns[skill.skillId] ?: 0
        if (currentCooldown > 0) return state
        
        // 扣除能量
        actor.energy -= skill.energyCost
        
        // 设置冷却
        if (skill.cooldown > 0) {
            actor.cooldowns[skill.skillId] = skill.cooldown
        }
        
        // 执行技能效果
        val newLog = state.log.toMutableList()
        val targetTeam = state.enemyTeam
        
        when (skill.target) {
            SkillTarget.SINGLE_ENEMY -> {
                val target = targetTeam.getOrNull(action.targetIndex) ?: return state
                val rawDamage = calculateDamage(actor, target, skill)
                val damage = applyTalentCombatEffects(actor, target, rawDamage)
                target.hp -= damage
                newLog.add(createStrikeEvent(state.turn, actor, target, damage))
            }
            SkillTarget.ALL_ENEMIES -> {
                for (target in targetTeam) {
                    val rawDamage = calculateDamage(actor, target, skill)
                    val damage = applyTalentCombatEffects(actor, target, rawDamage)
                    target.hp -= damage
                    newLog.add(createStrikeEvent(state.turn, actor, target, damage))
                }
            }
            SkillTarget.SELF -> {
                applyEffects(actor, skill.effects.filter { it.type == EffectType.HEAL })
            }
            SkillTarget.SINGLE_ALLY -> {
                val target = state.playerTeam.getOrNull(action.targetIndex) ?: return state
                applyEffects(target, skill.effects.filter { it.type == EffectType.HEAL })
            }
            SkillTarget.ALL_ALLIES -> {
                for (target in state.playerTeam) {
                    applyEffects(target, skill.effects.filter { it.type == EffectType.HEAL })
                }
            }
        }
        
        // 应用增益/减益效果
        applyBuffDebuffEffects(actor, skill.effects)
        
        return state.copy(
            log = newLog,
            pendingAction = null,
        )
    }
    
    /**
     * 计算伤害。
     */
    private fun calculateDamage(attacker: BattleUnitState, defender: BattleUnitState, skill: BattleSkill): Int {
        val baseDamage = (attacker.stats.atk * skill.power / 100).coerceAtLeast(1)
        // 无视防御：天赋 ignoreDefense 在防御计算前降低守方有效防御
        val effectiveDef = (defender.stats.def * (1f - attacker.stats.ignoreDefense)).toInt()
        val damage = (baseDamage - effectiveDef / 2).coerceAtLeast(1)
        
        // 元素克制
        val elementMultiplier = ElementChart.damageMultiplier(
            attacker.stats.element,
            defender.stats.element
        )
        
        // 天赋暴击加成（简化判定：按总暴击率直接掷骰）
        val totalCritRate = attacker.stats.critRate + attacker.stats.talentCritRate
        val totalCritDmg = attacker.stats.critDmg + attacker.stats.talentCritDamage
        val critMul = if (rng.nextDouble() < totalCritRate.coerceIn(0.0, 1.0)) {
            totalCritDmg.coerceAtLeast(1.0)
        } else 1.0

        return (damage * elementMultiplier * critMul).toInt().coerceAtLeast(1)
    }
    
    /**
     * 应用效果。
     */
    private fun applyEffects(target: BattleUnitState, effects: List<SkillEffect>) {
        for (effect in effects) {
            if (rng.nextInt(100) >= effect.chance) continue
            
            when (effect.type) {
                EffectType.HEAL -> {
                    target.hp = (target.hp + effect.value).coerceAtMost(target.maxHp)
                }
                EffectType.DAMAGE -> {
                    // 伤害已在calculateDamage中处理
                }
                else -> {
                    // 其他效果在applyBuffDebuffEffects中处理
                }
            }
        }
    }
    
    /**
     * 应用增益/减益效果。
     */
    private fun applyBuffDebuffEffects(actor: BattleUnitState, effects: List<SkillEffect>) {
        for (effect in effects) {
            if (rng.nextInt(100) >= effect.chance) continue
            
            when (effect.type) {
                EffectType.BUFF_ATK, EffectType.BUFF_DEF, EffectType.BUFF_SPD -> {
                    actor.buffs.add(BuffDebuff(effect.type, effect.value, effect.duration))
                }
                EffectType.DEBUFF_ATK, EffectType.DEBUFF_DEF, EffectType.DEBUFF_SPD -> {
                    // 减益效果需要应用到目标，这里简化处理
                }
                EffectType.STUN -> {
                    // 眩晕效果
                }
                EffectType.POISON -> {
                    // 中毒效果
                }
                EffectType.BURN -> {
                    // 灼烧效果
                }
                else -> {}
            }
        }
    }
    
    /**
     * 应用天赋防御/进攻效果到伤害结果，返回实际伤害值。
     * 闪避判定 → 伤害减免 → 吸血回复 → 反伤。
     */
    private fun applyTalentCombatEffects(
        attacker: BattleUnitState,
        defender: BattleUnitState,
        rawDamage: Int,
    ): Int {
        // 闪避判定
        val dodged = rng.nextDouble() < defender.stats.dodgeRate.toDouble().coerceIn(0.0, 1.0)
        if (dodged) return 0
        // 伤害减免
        val damage = (rawDamage * (1f - defender.stats.damageReduction)).toInt().coerceAtLeast(1)
        // 吸血
        if (damage > 0 && attacker.stats.lifesteal > 0f) {
            val heal = (damage * attacker.stats.lifesteal).toInt()
            attacker.hp = (attacker.hp + heal).coerceAtMost(attacker.stats.hp)
        }
        // 反伤
        if (damage > 0 && defender.stats.thorn > 0f) {
            val thornDmg = (damage * defender.stats.thorn).toInt().coerceAtLeast(1)
            attacker.hp -= thornDmg
        }
        return damage
    }

    /**
     * 创建攻击事件。
     */
    private fun createStrikeEvent(
        turn: Int,
        attacker: BattleUnitState,
        defender: BattleUnitState,
        damage: Int,
    ): StrikeEvent {
        return StrikeEvent(
            turn = turn,
            attackerId = attacker.stats.characterId,
            attackerElement = attacker.stats.element,
            targetId = defender.stats.characterId,
            targetElement = defender.stats.element,
            damage = damage,
            targetDefeated = defender.hp <= 0,
        )
    }
    
    /**
     * 执行敌方AI行动。
     */
    fun executeEnemyTurn(state: BattleState): BattleState {
        val newLog = state.log.toMutableList()
        val newEnemyTeam = state.enemyTeam.toMutableList()
        
        for (enemy in newEnemyTeam) {
            if (enemy.hp <= 0) continue
            
            // AI选择技能（简单策略：优先使用能量足够的最强技能）
            val availableSkills = enemy.skills.filter { skill ->
                enemy.energy >= skill.energyCost &&
                (enemy.cooldowns[skill.skillId] ?: 0) == 0
            }.sortedByDescending { it.power }
            
            val skill = availableSkills.firstOrNull() ?: enemy.skills.firstOrNull { it.type == SkillType.NORMAL }
            if (skill == null) continue
            
            // 扣除能量
            enemy.energy -= skill.energyCost
            
            // 设置冷却
            if (skill.cooldown > 0) {
                enemy.cooldowns[skill.skillId] = skill.cooldown
            }
            
            // 选择目标（简单策略：攻击血量最低的玩家）
            val target = state.playerTeam.filter { it.hp > 0 }.minByOrNull { it.hp }
            if (target == null) continue
            
            // 计算伤害
            val rawDamage = calculateDamage(enemy, target, skill)
            val damage = applyTalentCombatEffects(enemy, target, rawDamage)
            target.hp -= damage

            newLog.add(createStrikeEvent(state.turn, enemy, target, damage))
        }
        
        return state.copy(
            enemyTeam = newEnemyTeam,
            log = newLog,
        )
    }
    
    /**
     * 更新回合状态（冷却减少、能量增加等）。
     */
    fun updateTurnState(state: BattleState): BattleState {
        val newPlayerTeam = state.playerTeam.map { unit ->
            val newCooldowns = unit.cooldowns.toMutableMap()
            newCooldowns.forEach { (key, value) ->
                if (value > 0) newCooldowns[key] = value - 1
            }
            
            // 能量增加（基于速度）
            val energyGain = (unit.stats.spd / 10).coerceAtLeast(1)
            val newEnergy = (unit.energy + energyGain).coerceAtMost(unit.maxEnergy)
            
            unit.copy(
                cooldowns = newCooldowns,
                energy = newEnergy,
            )
        }
        
        val newEnemyTeam = state.enemyTeam.map { unit ->
            val newCooldowns = unit.cooldowns.toMutableMap()
            newCooldowns.forEach { (key, value) ->
                if (value > 0) newCooldowns[key] = value - 1
            }
            
            val energyGain = (unit.stats.spd / 10).coerceAtLeast(1)
            val newEnergy = (unit.energy + energyGain).coerceAtMost(unit.maxEnergy)
            
            unit.copy(
                cooldowns = newCooldowns,
                energy = newEnergy,
            )
        }
        
        return state.copy(
            turn = state.turn + 1,
            playerTeam = newPlayerTeam,
            enemyTeam = newEnemyTeam,
        )
    }
    
    /**
     * 检查战斗结果。
     */
    fun checkBattleResult(state: BattleState): BattlePhase {
        val playerAlive = state.playerTeam.any { it.hp > 0 }
        val enemyAlive = state.enemyTeam.any { it.hp > 0 }
        
        return when {
            !enemyAlive -> BattlePhase.VICTORY
            !playerAlive -> BattlePhase.DEFEAT
            state.turn >= 20 -> BattlePhase.DRAW  // 最大回合数限制
            else -> BattlePhase.PLAYER_INPUT
        }
    }
    
    /**
     * 获取可用的玩家行动。
     */
    fun getAvailableActions(state: BattleState, actorIndex: Int): List<PlayerAction> {
        val actor = state.playerTeam.getOrNull(actorIndex) ?: return emptyList()
        val actions = mutableListOf<PlayerAction>()
        
        for (skill in actor.skills) {
            // 检查能量和冷却
            if (actor.energy < skill.energyCost) continue
            val currentCooldown = actor.cooldowns[skill.skillId] ?: 0
            if (currentCooldown > 0) continue
            
            // 根据技能目标类型生成行动
            when (skill.target) {
                SkillTarget.SINGLE_ENEMY -> {
                    state.enemyTeam.forEachIndexed { index, enemy ->
                        if (enemy.hp > 0) {
                            actions.add(PlayerAction(actorIndex, skill.skillId, index))
                        }
                    }
                }
                SkillTarget.ALL_ENEMIES -> {
                    actions.add(PlayerAction(actorIndex, skill.skillId, 0))
                }
                SkillTarget.SELF -> {
                    actions.add(PlayerAction(actorIndex, skill.skillId, actorIndex))
                }
                SkillTarget.SINGLE_ALLY -> {
                    state.playerTeam.forEachIndexed { index, ally ->
                        if (ally.hp > 0 && index != actorIndex) {
                            actions.add(PlayerAction(actorIndex, skill.skillId, index))
                        }
                    }
                }
                SkillTarget.ALL_ALLIES -> {
                    actions.add(PlayerAction(actorIndex, skill.skillId, 0))
                }
            }
        }
        
        return actions
    }
}
