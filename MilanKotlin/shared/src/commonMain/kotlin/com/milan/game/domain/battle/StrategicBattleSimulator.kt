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
 * - 支持元素反应系统
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
                cooldowns = emptyMap(),
                buffs = emptyList(),
                debuffs = emptyList(),
                isPlayer = true,
                skills = getDefaultSkills(stats.characterId),
                elementReactionCooldown = 0,
            )
        }
        
        val enemyStates = enemyTeam.map { stats ->
            BattleUnitState(
                stats = stats,
                hp = stats.hp,
                maxHp = stats.hp,
                energy = 0,
                maxEnergy = 100,
                cooldowns = emptyMap(),
                buffs = emptyList(),
                debuffs = emptyList(),
                isPlayer = false,
                skills = getDefaultSkills(stats.characterId),
                elementReactionCooldown = 0,
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
     * characterId 含 `elite` 时追加深渊词缀技能（2026-09-12 深渊精英层）。
     */
    private fun getDefaultSkills(characterId: String): List<BattleSkill> {
        // 暂时返回通用技能，后续可以根据角色ID返回特定技能
        val base = listOf(
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
        if (!characterId.contains("elite", ignoreCase = true)) return base
        return base + BattleSkill(
            skillId = "abyss_elite_mutation",
            name = "深渊词缀·焚心",
            description = "精英词缀：高倍率单体并附加灼烧",
            type = SkillType.ACTIVE,
            energyCost = 40,
            cooldown = 3,
            power = 180,
            target = SkillTarget.SINGLE_ENEMY,
            effects = listOf(
                SkillEffect(EffectType.DAMAGE, 180, 1, 100),
                SkillEffect(EffectType.BURN, 8, 2, 70),
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

        // 攻击者的确定性变更：扣能量 + 置冷却。
        // 不就地 mutate 入参——产出新实例，由下方 commit 统一写回队伍列表。
        var newActor = actor.copy(
            energy = actor.energy - skill.energyCost,
            cooldowns = if (skill.cooldown > 0) {
                actor.cooldowns + (skill.skillId to skill.cooldown)
            } else actor.cooldowns,
        )

        val newLog = state.log.toMutableList()
        var newPlayerTeam = state.playerTeam
        var newEnemyTeam = state.enemyTeam

        // 替换式更新：把变更后的单位写回队伍列表（不 mutate 原对象）
        fun commitAlly(index: Int, unit: BattleUnitState) {
            newPlayerTeam = newPlayerTeam.toMutableList().also { it[index] = unit }
        }
        fun commitEnemy(index: Int, unit: BattleUnitState) {
            newEnemyTeam = newEnemyTeam.toMutableList().also { it[index] = unit }
        }

        when (skill.target) {
            SkillTarget.SINGLE_ENEMY -> {
                val idx = action.targetIndex
                val target = state.enemyTeam.getOrNull(idx) ?: return state
                val rawDamage = calculateDamage(actor, target, skill)
                val (damage, actorAfterTalent) = applyTalentCombatEffects(newActor, target, rawDamage)
                newActor = actorAfterTalent
                var newTarget = target.copy(hp = target.hp - damage)
                newLog.add(createStrikeEvent(state.turn, actor, newTarget, damage))

                // 尝试触发元素反应
                val reaction = tryTriggerElementReaction(newActor, newTarget, state.turn)
                newActor = reaction.attacker
                newTarget = reaction.defender
                newLog.addAll(reaction.log)
                commitEnemy(idx, newTarget)
            }
            SkillTarget.ALL_ENEMIES -> {
                state.enemyTeam.forEachIndexed { idx, target ->
                    val rawDamage = calculateDamage(actor, target, skill)
                    val (damage, actorAfterTalent) = applyTalentCombatEffects(newActor, target, rawDamage)
                    newActor = actorAfterTalent
                    var newTarget = target.copy(hp = target.hp - damage)
                    newLog.add(createStrikeEvent(state.turn, actor, newTarget, damage))

                    // 尝试触发元素反应（每个目标独立判定）
                    val reaction = tryTriggerElementReaction(newActor, newTarget, state.turn)
                    newActor = reaction.attacker
                    newTarget = reaction.defender
                    newLog.addAll(reaction.log)
                    commitEnemy(idx, newTarget)
                }
            }
            SkillTarget.SELF -> {
                newActor = applyEffects(newActor, skill.effects.filter { it.type == EffectType.HEAL })
            }
            SkillTarget.SINGLE_ALLY -> {
                val idx = action.targetIndex
                val target = state.playerTeam.getOrNull(idx) ?: return state
                val healed = applyEffects(target, skill.effects.filter { it.type == EffectType.HEAL })
                // 目标是施法者本人时只取 hp 变化，避免覆盖已算好的能量/冷却变更
                if (idx == action.actorIndex) newActor = newActor.copy(hp = healed.hp)
                else commitAlly(idx, healed)
            }
            SkillTarget.ALL_ALLIES -> {
                state.playerTeam.forEachIndexed { idx, target ->
                    val healed = applyEffects(target, skill.effects.filter { it.type == EffectType.HEAL })
                    if (idx == action.actorIndex) newActor = newActor.copy(hp = healed.hp)
                    else commitAlly(idx, healed)
                }
            }
        }

        // 应用增益/减益效果
        newActor = applyBuffDebuffEffects(newActor, skill.effects)

        // 更新攻击者上次使用的元素
        newActor = newActor.copy(lastElementUsed = actor.stats.element)

        commitAlly(action.actorIndex, newActor)

        return state.copy(
            playerTeam = newPlayerTeam,
            enemyTeam = newEnemyTeam,
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
    private fun applyEffects(target: BattleUnitState, effects: List<SkillEffect>): BattleUnitState {
        var newTarget = target
        for (effect in effects) {
            if (rng.nextInt(100) >= effect.chance) continue
            
            when (effect.type) {
                EffectType.HEAL -> {
                    newTarget = newTarget.copy(hp = (newTarget.hp + effect.value).coerceAtMost(newTarget.maxHp))
                }
                EffectType.DAMAGE -> {
                    // 伤害已在calculateDamage中处理
                }
                else -> {
                    // 其他效果在applyBuffDebuffEffects中处理
                }
            }
        }
        return newTarget
    }
    
    /**
     * 应用增益/减益效果。
     */
    private fun applyBuffDebuffEffects(actor: BattleUnitState, effects: List<SkillEffect>): BattleUnitState {
        var newActor = actor
        for (effect in effects) {
            if (rng.nextInt(100) >= effect.chance) continue
            
            when (effect.type) {
                EffectType.BUFF_ATK, EffectType.BUFF_DEF, EffectType.BUFF_SPD -> {
                    newActor = newActor.copy(buffs = newActor.buffs + BuffDebuff(effect.type, effect.value, effect.duration))
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
        return newActor
    }
    
    /**
     * 应用天赋防御/进攻效果到伤害结果，返回实际伤害值。
     * 闪避判定 → 伤害减免 → 吸血回复 → 反伤。
     */
    private fun applyTalentCombatEffects(
        attacker: BattleUnitState,
        defender: BattleUnitState,
        rawDamage: Int,
    ): Pair<Int, BattleUnitState> {
        // 闪避判定
        val dodged = rng.nextDouble() < defender.stats.dodgeRate.toDouble().coerceIn(0.0, 1.0)
        if (dodged) return 0 to attacker
        // 伤害减免
        val damage = (rawDamage * (1f - defender.stats.damageReduction)).toInt().coerceAtLeast(1)
        // 吸血
        var newAttacker = attacker
        if (damage > 0 && attacker.stats.lifesteal > 0f) {
            val heal = (damage * attacker.stats.lifesteal).toInt()
            newAttacker = newAttacker.copy(hp = (newAttacker.hp + heal).coerceAtMost(newAttacker.stats.hp))
        }
        // 反伤
        if (damage > 0 && defender.stats.thorn > 0f) {
            val thornDmg = (damage * defender.stats.thorn).toInt().coerceAtLeast(1)
            newAttacker = newAttacker.copy(hp = newAttacker.hp - thornDmg)
        }
        return damage to newAttacker
    }

    /**
     * 元素反应的纯函数结果（2026-09-08 P0-3）：变更后的攻守双方 + 产生的日志事件。
     * 原实现就地 mutate 入参的 attacker/defender，调用方无法区分「变更前/后」。
     */
    private data class ElementReactionResult(
        val attacker: BattleUnitState,
        val defender: BattleUnitState,
        val log: List<StrikeEvent>,
    )

    /**
     * 尝试触发元素反应。
     * 
     * @param attacker 攻击者
     * @param defender 防御者
     * @param turn 当前回合数
     * @return 元素反应产生的日志事件列表
     */
    private fun tryTriggerElementReaction(
        attacker: BattleUnitState,
        defender: BattleUnitState,
        turn: Int,
    ): ElementReactionResult {
        /** 未触发：原样返回攻守双方，无日志。 */
        fun none() = ElementReactionResult(attacker, defender, emptyList())
        
        // 检查攻击者是否有元素反应冷却
        if (attacker.elementReactionCooldown > 0) {
            return none()
        }
        
        // 获取攻击者使用的元素
        val attackerElement = attacker.lastElementUsed ?: attacker.stats.element
        if (attackerElement.isNullOrEmpty()) {
            return none()
        }
        
        // 获取防御者的元素（可能已经附着了其他元素）
        val defenderElement = defender.lastElementUsed ?: defender.stats.element
        
        // 检查是否能触发元素反应
        val reaction = ElementChart.getReaction(attackerElement, defenderElement)
        if (reaction == null) {
            return none()
        }
        
        // 计算触发概率
        val chance = ElementChart.reactionChance(attackerElement)
        if (rng.nextInt(100) >= chance) {
            return none()
        }
        
        // 触发元素反应
        val reactionDamage = (attacker.stats.atk * reaction.damageMultiplier * 0.5).toInt().coerceAtLeast(1)
        var newAttacker = attacker
        var newDefender = defender.copy(hp = defender.hp - reactionDamage)
        
        // 应用反应效果
        if (reaction.effectType != null && reaction.effectValue > 0) {
            when (reaction.effectType) {
                EffectType.HEAL -> {
                    newAttacker = newAttacker.copy(hp = (newAttacker.hp + reaction.effectValue).coerceAtMost(newAttacker.maxHp))
                }
                EffectType.BUFF_ATK, EffectType.BUFF_DEF, EffectType.BUFF_SPD -> {
                    newAttacker = newAttacker.copy(buffs = newAttacker.buffs + BuffDebuff(reaction.effectType, reaction.effectValue, reaction.effectDuration))
                }
                EffectType.DEBUFF_ATK, EffectType.DEBUFF_DEF, EffectType.DEBUFF_SPD -> {
                    newDefender = newDefender.copy(debuffs = newDefender.debuffs + BuffDebuff(reaction.effectType, reaction.effectValue, reaction.effectDuration))
                }
                EffectType.POISON -> {
                    newDefender = newDefender.copy(debuffs = newDefender.debuffs + BuffDebuff(EffectType.POISON, reaction.effectValue, reaction.effectDuration))
                }
                else -> {}
            }
        }
        
        // 设置元素反应冷却
        newAttacker = newAttacker.copy(elementReactionCooldown = ElementChart.REACTION_COOLDOWN)
        
        // 创建元素反应日志事件
        val event = StrikeEvent(
            turn = turn,
            attackerId = attacker.stats.characterId,
            attackerElement = attackerElement,
            targetId = defender.stats.characterId,
            targetElement = defenderElement ?: "",
            damage = reactionDamage,
            targetDefeated = newDefender.hp <= 0,
            isElementReaction = true,
            reactionName = reaction.reactionName,
        )
        return ElementReactionResult(newAttacker, newDefender, listOf(event))
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
        var newEnemyTeam = state.enemyTeam
        var newPlayerTeam = state.playerTeam

        state.enemyTeam.forEachIndexed { index, enemy ->
            if (enemy.hp <= 0) return@forEachIndexed

            // AI选择技能（简单策略：优先使用能量足够的最强技能）
            val availableSkills = enemy.skills.filter { skill ->
                enemy.energy >= skill.energyCost &&
                (enemy.cooldowns[skill.skillId] ?: 0) == 0
            }.sortedByDescending { it.power }

            val skill = availableSkills.firstOrNull() ?: enemy.skills.firstOrNull { it.type == SkillType.NORMAL }
            if (skill == null) return@forEachIndexed

            // 选择目标（简单策略：攻击血量最低的玩家）
            val target = state.playerTeam.filter { it.hp > 0 }.minByOrNull { it.hp }
            if (target == null) return@forEachIndexed
            val targetIndex = state.playerTeam.indexOfFirst { it === target }
            if (targetIndex < 0) return@forEachIndexed

            // 扣能量 + 置冷却：产出新实例，不 mutate 入参的 enemy
            var newEnemy = enemy.copy(
                energy = enemy.energy - skill.energyCost,
                cooldowns = if (skill.cooldown > 0) {
                    enemy.cooldowns + (skill.skillId to skill.cooldown)
                } else enemy.cooldowns,
            )

            // 计算伤害（stats 不随能量/冷却变化，用原 enemy 计算与旧实现等价）
            val rawDamage = calculateDamage(enemy, target, skill)
            val (damage, enemyAfterTalent) = applyTalentCombatEffects(newEnemy, target, rawDamage)
            newEnemy = enemyAfterTalent

            val newTarget = target.copy(hp = target.hp - damage)
            newLog.add(createStrikeEvent(state.turn, enemy, newTarget, damage))

            newEnemyTeam = newEnemyTeam.toMutableList().also { it[index] = newEnemy }
            newPlayerTeam = newPlayerTeam.toMutableList().also { it[targetIndex] = newTarget }
        }

        // 注意：旧实现只 copy(enemyTeam, log)，玩家掉血完全依赖「就地 mutate 共享对象」这一副作用生效。
        // 改为不可变后必须显式提交 playerTeam，否则敌方攻击将不再扣除玩家血量。
        return state.copy(
            playerTeam = newPlayerTeam,
            enemyTeam = newEnemyTeam,
            log = newLog,
        )
    }
    
    /**
     * 更新回合状态（冷却减少、能量增加、元素反应冷却减少等）。
     */
    fun updateTurnState(state: BattleState): BattleState {
        val newPlayerTeam = state.playerTeam.map { unit ->
            val newCooldowns = unit.cooldowns.mapValues { (_, value) -> if (value > 0) value - 1 else value }
            
            // 能量增加（基于速度）
            val energyGain = (unit.stats.spd / 10).coerceAtLeast(1)
            val newEnergy = (unit.energy + energyGain).coerceAtMost(unit.maxEnergy)
            
            // 元素反应冷却减少
            val newElementReactionCooldown = (unit.elementReactionCooldown - 1).coerceAtLeast(0)
            
            unit.copy(
                cooldowns = newCooldowns,
                energy = newEnergy,
                elementReactionCooldown = newElementReactionCooldown,
            )
        }
        
        val newEnemyTeam = state.enemyTeam.map { unit ->
            val newCooldowns = unit.cooldowns.mapValues { (_, value) -> if (value > 0) value - 1 else value }
            
            val energyGain = (unit.stats.spd / 10).coerceAtLeast(1)
            val newEnergy = (unit.energy + energyGain).coerceAtMost(unit.maxEnergy)
            
            // 元素反应冷却减少
            val newElementReactionCooldown = (unit.elementReactionCooldown - 1).coerceAtLeast(0)
            
            unit.copy(
                cooldowns = newCooldowns,
                energy = newEnergy,
                elementReactionCooldown = newElementReactionCooldown,
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
