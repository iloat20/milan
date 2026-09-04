package com.milan.game.services

import com.milan.game.data.AbyssSaveData
import com.milan.game.data.DailyDungeonSaveData
import com.milan.game.data.DailyDungeonType
import com.milan.game.data.DungeonReward
import com.milan.game.domain.battle.BattleSimulator
import com.milan.game.domain.battle.TeamResonance
import com.milan.game.domain.battle.UnitStats
import kotlin.random.Random

/**
 * PVE内容服务（深渊 + 日常副本）。
 * 
 * 职责：
 * - 深渊挑战（多层多关卡，星级评价）
 * - 日常副本（经验/金币/材料/装备/碎片）
 * - 奖励发放
 */
internal class PvEService(
    private val core: ServiceCore,
    private val rng: Random = Random.Default,
) {
    private val simulator = BattleSimulator(rng)
    
    // ─────────────────────────── 深渊系统 ───────────────────────────
    
    /**
     * 获取深渊数据（不存在则创建）。
     */
    fun getAbyssData(): AbyssSaveData {
        // R5-I5：懒创建改纯读——默认值由 sanitize/createDefault 保证非 null，不再锁外写存档。
        return core.saveData.abyssData ?: AbyssSaveData()
    }
    
    /**
     * 挑战深渊关卡（R5-M1 由中文名「挑战深渊关卡」改为英文；R5-I2 补跨日重置）。
     */
    suspend fun challengeAbyssStage(floor: Int, stage: Int): WriteOutcome {
        val abyssData = getAbyssData()
        val today = core.today()
        
        // 验证关卡有效性
        if (floor < 1 || floor > AbyssSaveData.MAX_FLOORS) return WriteOutcome.Rejected
        if (stage < 1 || stage > AbyssSaveData.STAGES_PER_FLOOR) return WriteOutcome.Rejected
        
        // 检查挑战次数（跨日视为 0 次，重置将在 mutate 内落地）
        val effectiveCount = if (abyssData.lastResetTime != today) 0 else abyssData.challengeCount
        if (effectiveCount >= AbyssSaveData.DAILY_FREE_CHALLENGES) {
            return WriteOutcome.Rejected
        }
        
        // 获取玩家队伍
        val teamIds = core.saveData.getFormationIds()
        if (teamIds.isEmpty()) return WriteOutcome.Rejected
        
        val myUnits = teamIds.mapNotNull { core.unitStatsFor(it) }
        if (myUnits.isEmpty()) return WriteOutcome.Rejected
        
        val team = TeamResonance.apply(myUnits).toTypedArray()
        
        // 生成深渊敌人
        val enemyTeam = buildAbyssEnemies(floor, stage)
        
        // 模拟战斗
        val result = simulator.simulate(team, enemyTeam, 50)
        
        // 计算星级
        val stars = calculateStars(result, team)
        
        val originalFloor = abyssData.currentFloor
        val originalStage = abyssData.currentStage
        val originalStars = abyssData.stars
        val originalTotalStars = abyssData.totalStars
        val originalBestFloor = abyssData.bestFloor
        val originalChallengeCount = abyssData.challengeCount
        val originalLastReset = abyssData.lastResetTime
        
        return core.transaction(
            tag = "abyss.challenge",
            mutate = {
                // 跨日重置（R5-I2：与本次写同事务原子落盘）
                if (abyssData.lastResetTime != today) {
                    abyssData.lastResetTime = today
                    abyssData.challengeCount = 0
                }
                abyssData.challengeCount++
                
                if (result.victory) {
                    // 更新当前进度
                    if (stage >= AbyssSaveData.STAGES_PER_FLOOR) {
                        // 通关本层，进入下一层
                        abyssData.currentFloor = floor + 1
                        abyssData.currentStage = 1
                    } else {
                        // 进入本层下一关
                        abyssData.currentStage = stage + 1
                    }
                    
                    // 更新最佳记录
                    if (floor > abyssData.bestFloor) {
                        abyssData.bestFloor = floor
                    }
                    
                    // 更新星星数
                    val floorIndex = floor - 1
                    val currentStars = abyssData.stars.getOrNull(floorIndex) ?: 0
                    if (stars > currentStars) {
                        val newStars = abyssData.stars.toMutableList()
                        while (newStars.size <= floorIndex) newStars.add(0)
                        newStars[floorIndex] = stars
                        abyssData.stars = newStars
                        
                        // 重新计算总星星数
                        abyssData.totalStars = newStars.filterNotNull().sum()
                    }
                }
            },
            rollback = {
                abyssData.currentFloor = originalFloor
                abyssData.currentStage = originalStage
                abyssData.stars = originalStars
                abyssData.totalStars = originalTotalStars
                abyssData.bestFloor = originalBestFloor
                abyssData.challengeCount = originalChallengeCount
                abyssData.lastResetTime = originalLastReset
            },
            onCommit = {
                core.publishProgressionChanged()
            },
        )
    }
    
    /**
     * 计算深渊战斗星级。
     * 
     * 星级条件：
     * - 1星：通关
     * - 2星：回合数 ≤ 10
     * - 3星：全员存活
     */
    private fun calculateStars(
        result: com.milan.game.domain.battle.BattleResult,
        team: Array<UnitStats>,
    ): Int {
        if (!result.victory) return 0
        
        var stars = 1  // 通关获得1星
        
        if (result.turns <= 10) {
            stars++  // 回合数≤10获得2星
        }
        
        if (result.remainingHp == team.sumOf { it.hp }) {
            stars++  // 全员存活获得3星
        }
        
        return stars.coerceAtMost(AbyssSaveData.MAX_STARS_PER_FLOOR)
    }
    
    /**
     * 生成深渊敌人。
     */
    private fun buildAbyssEnemies(floor: Int, stage: Int): Array<UnitStats> {
        val abyssRng = Random(floor * 1000L + stage * 100L + 7L)
        val baseHp = 500 + floor * 100 + stage * 50
        val baseAtk = 50 + floor * 10 + stage * 5
        val baseDef = 30 + floor * 5 + stage * 3
        
        val enemyCount = when {
            stage == 3 -> 3  // Boss关
            stage == 2 -> 2  // 精英关
            else -> 2        // 普通关
        }
        
        val elements = listOf("Metal", "Wood", "Water", "Flame", "Earth", "Light", "Shadow", "Thunder")
        
        return Array(enemyCount) { i ->
            val isBoss = stage == 3 && i == 0
            UnitStats(
                atk = if (isBoss) baseAtk * 2 else baseAtk,
                def = if (isBoss) baseDef * 2 else baseDef,
                hp = if (isBoss) baseHp * 3 else baseHp,
                spd = 80 + abyssRng.nextInt(20),
                characterId = "abyss_f${floor}_s${stage}_e$i",
                element = elements[abyssRng.nextInt(elements.size)],
            )
        }
    }
    
    /**
     * 获取深渊关卡信息。
     */
    fun getAbyssStageInfo(floor: Int, stage: Int): DungeonReward {
        val baseExp = 100 + floor * 20
        val baseGold = 500 + floor * 100
        
        return DungeonReward(
            exp = baseExp,
            gold = baseGold,
            materials = mapOf(
                "material_exp" to (5 + floor),
                "material_gold" to (10 + floor * 2),
            ),
            equipmentChance = if (stage == 3) 0.3 else 0.1,
        )
    }
    
    /**
     * 获取深渊层数奖励。
     */
    fun getAbyssFloorRewards(): List<com.milan.game.data.AbyssFloorReward> {
        return listOf(
            com.milan.game.data.AbyssFloorReward(5, 10, 2000, 50, 50, null),
            com.milan.game.data.AbyssFloorReward(10, 20, 5000, 100, 100, "sr_weapon_001"),
            com.milan.game.data.AbyssFloorReward(15, 30, 10000, 200, 200, "ssr_weapon_001"),
            com.milan.game.data.AbyssFloorReward(20, 40, 20000, 500, 500, "ur_weapon_001"),
            com.milan.game.data.AbyssFloorReward(30, 60, 50000, 1000, 1000, "ur_character_001"),
        )
    }
    
    // ─────────────────────────── 日常副本系统 ───────────────────────────
    
    /**
     * 获取日常副本数据。
     */
    fun getDailyDungeonData(): DailyDungeonSaveData {
        // R5-I5：懒创建改纯读——默认值由 sanitize/createDefault 保证非 null，不再锁外写存档。
        return core.saveData.dailyDungeonData ?: DailyDungeonSaveData()
    }
    
    /**
     * 挑战日常副本（R5-M1 由中文名「挑战日常副本」改为英文；R5-I2 补跨日重置）。
     */
    suspend fun challengeDailyDungeon(type: DailyDungeonType, level: Int): WriteOutcome {
        val dungeonData = getDailyDungeonData()
        val today = core.today()
        
        // 验证关卡有效性
        if (level < 1 || level > type.levels) return WriteOutcome.Rejected
        
        // 检查挑战次数（跨日视为 0 次，重置将在 mutate 内落地）
        val currentCount = if (dungeonData.lastResetTime != today) 0
            else (dungeonData.challengeCounts[type.name] ?: 0)
        if (currentCount >= DailyDungeonSaveData.MAX_CHALLENGES_PER_TYPE) {
            return WriteOutcome.Rejected
        }
        
        // 获取玩家队伍
        val teamIds = core.saveData.getFormationIds()
        if (teamIds.isEmpty()) return WriteOutcome.Rejected
        
        val myUnits = teamIds.mapNotNull { core.unitStatsFor(it) }
        if (myUnits.isEmpty()) return WriteOutcome.Rejected
        
        val team = TeamResonance.apply(myUnits).toTypedArray()
        
        // 生成副本敌人
        val enemyTeam = buildDungeonEnemies(type, level)
        
        // 模拟战斗
        val result = simulator.simulate(team, enemyTeam, 50)
        
        // 计算奖励
        val reward = calculateDungeonReward(type, level, result.victory)
        
        val originalCounts = dungeonData.challengeCounts.toMap()
        val originalTotal = dungeonData.totalChallenges
        val originalSoft = core.saveData.softCurrency
        val originalLastReset = dungeonData.lastResetTime
        
        return core.transaction(
            tag = "dungeon.challenge",
            mutate = {
                // 跨日重置（R5-I2：与本次写同事务原子落盘，整个计数表清零）
                if (dungeonData.lastResetTime != today) {
                    dungeonData.lastResetTime = today
                    dungeonData.challengeCounts = emptyMap()
                }
                dungeonData.challengeCounts = dungeonData.challengeCounts + (type.name to (currentCount + 1))
                dungeonData.totalChallenges++
                
                if (result.victory) {
                    // 发放奖励
                    core.addCurrencyDelta(reward.gold, 0)
                    // TODO: 添加经验、材料、装备到背包
                }
            },
            rollback = {
                dungeonData.challengeCounts = originalCounts
                dungeonData.totalChallenges = originalTotal
                core.saveData.softCurrency = originalSoft
                dungeonData.lastResetTime = originalLastReset
            },
            onCommit = {
                core.publishCurrencyChanged()
            },
        )
    }
    
    /**
     * 生成副本敌人。
     */
    private fun buildDungeonEnemies(type: DailyDungeonType, level: Int): Array<UnitStats> {
        val dungeonRng = Random(type.ordinal * 1000L + level * 100L + 7L)
        val baseHp = 300 + level * 80
        val baseAtk = 30 + level * 8
        val baseDef = 20 + level * 4
        
        val elements = listOf("Metal", "Wood", "Water", "Flame", "Earth", "Light", "Shadow", "Thunder")
        
        return Array(2) { i ->
            UnitStats(
                atk = baseAtk,
                def = baseDef,
                hp = baseHp,
                spd = 80 + dungeonRng.nextInt(20),
                characterId = "dungeon_${type.name}_l${level}_e$i",
                element = elements[dungeonRng.nextInt(elements.size)],
            )
        }
    }
    
    /**
     * 计算副本奖励。
     */
    private fun calculateDungeonReward(type: DailyDungeonType, level: Int, victory: Boolean): DungeonReward {
        if (!victory) {
            return DungeonReward(exp = 0, gold = 0, materials = emptyMap())
        }
        
        val baseReward = when (type) {
            DailyDungeonType.EXP_DUNGEON -> DungeonReward(
                exp = 200 + level * 50,
                gold = 100 + level * 20,
                materials = mapOf("material_exp" to (5 + level)),
            )
            DailyDungeonType.GOLD_DUNGEON -> DungeonReward(
                exp = 50 + level * 10,
                gold = 500 + level * 100,
                materials = emptyMap(),
            )
            DailyDungeonType.MATERIAL_DUNGEON -> DungeonReward(
                exp = 100 + level * 20,
                gold = 200 + level * 40,
                materials = mapOf(
                    "material_ascend" to (3 + level / 2),
                    "material_star" to (1 + level / 3),
                ),
            )
            DailyDungeonType.EQUIPMENT_DUNGEON -> DungeonReward(
                exp = 100 + level * 20,
                gold = 200 + level * 40,
                materials = emptyMap(),
                equipmentChance = 0.2 + level * 0.05,
            )
            DailyDungeonType.FRAGMENT_DUNGEON -> DungeonReward(
                exp = 100 + level * 20,
                gold = 200 + level * 40,
                materials = mapOf("fragment" to (2 + level / 2)),
            )
        }
        
        return baseReward
    }
    
    /**
     * 获取日常副本剩余挑战次数。
     */
    fun getRemainingChallenges(type: DailyDungeonType): Int {
        val dungeonData = getDailyDungeonData()
        val currentCount = dungeonData.challengeCounts[type.name] ?: 0
        return (DailyDungeonSaveData.MAX_CHALLENGES_PER_TYPE - currentCount).coerceAtLeast(0)
    }
}
