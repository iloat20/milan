package com.milan.game.services

import com.milan.game.data.ArenaOpponent
import com.milan.game.data.ArenaSaveData
import com.milan.game.data.PvPBattleRecord
import com.milan.game.data.SeasonReward
import com.milan.game.domain.battle.BattleSimulator
import com.milan.game.domain.battle.TeamResonance
import com.milan.game.domain.battle.UnitStats
import kotlin.random.Random

/**
 * PVP竞技场服务。
 * 
 * 职责：
 * - 竞技场排名和积分管理
 * - 防守阵容设置
 * - 匹配对手
 * - PVP战斗结算
 * - 赛季奖励
 */
class ArenaService(
    private val core: ServiceCore,
    private val rng: Random = Random.Default,
) : ArenaApi {
    private val simulator = BattleSimulator(rng)
    
    /**
     * 获取竞技场数据（不存在则创建）。
     */
    override fun getArenaData(): ArenaSaveData {
        // R5-I5：懒创建改纯读——默认值由 sanitize/createDefault 保证非 null，不再锁外写存档。
        return core.saveData.arenaData ?: ArenaSaveData()
    }
    
    /**
     * 设置防守阵容。
     */
    override suspend fun setDefenseTeam(characterIds: List<String>): WriteOutcome {
        val arenaData = getArenaData()
        val ids = characterIds.distinct().take(5)
        
        // 验证角色已拥有
        val ownedIds = core.saveData.ownedCharacters.filterNotNull().mapTo(HashSet()) { it.characterId }
        if (ids.any { it !in ownedIds }) return WriteOutcome.Rejected
        
        val original = arenaData.defenseTeam
        return core.transaction(
            tag = "arena.defense",
            mutate = { arenaData.defenseTeam = ids },
            rollback = { arenaData.defenseTeam = original },
            onCommit = { core.publishProgressionChanged() },
        )
    }
    
    /**
     * 获取可挑战的对手列表。
     */
    override fun getOpponents(): List<ArenaOpponent> {
        val arenaData = getArenaData()
        val myPoints = arenaData.arenaPoints
        
        // 根据积分匹配相近的对手（±200积分范围内）
        val opponents = mutableListOf<ArenaOpponent>()
        
        // 生成模拟对手（实际游戏中应该从服务器获取）
        val simulatedOpponents = generateSimulatedOpponents(myPoints)
        
        return simulatedOpponents
    }
    
    /**
     * 生成模拟对手（开发用）。
     */
    private fun generateSimulatedOpponents(basePoints: Int): List<ArenaOpponent> {
        val opponents = mutableListOf<ArenaOpponent>()
        val names = listOf("影刃", "星使", "铁卫", "炎舞", "雷神", "冰霜", "暗影", "圣光")
        
        for (i in 1..5) {
            val points = (basePoints + rng.nextInt(-200, 201)).coerceIn(0, 9999)
            val level = rng.nextInt(1, 61)
            val teamPower = rng.nextInt(500, 5000)
            
            opponents.add(ArenaOpponent(
                characterId = "opponent_$i",
                name = names[rng.nextInt(names.size)] + "${rng.nextInt(1000, 9999)}",
                level = level,
                rank = calculateRank(points),
                teamPower = teamPower,
                defenseTeam = listOf("char_r_001", "char_sr_001", "char_ssr_001"),
                points = points,
                isOnline = rng.nextBoolean(),
            ))
        }
        
        return opponents
    }
    
    /**
     * 根据积分计算段位。
     */
    private fun calculateRank(points: Int): Int {
        return when {
            points >= 3000 -> 6  // 宗师
            points >= 2500 -> 5  // 钻石
            points >= 2000 -> 4  // 铂金
            points >= 1500 -> 3  // 黄金
            points >= 1000 -> 2  // 白银
            else -> 1            // 青铜
        }
    }
    
    /**
     * 挑战对手（R5-M1 由中文名「挑战对手」改为英文；R5-I2 补跨日重置）。
     * 2026-09-10：返回 [ArenaChallengeOutcome]，成功时携带胜负/回合/积分供 UI 结算演出。
     */
    override suspend fun challengeOpponent(opponent: ArenaOpponent): ArenaChallengeOutcome {
        val arenaData = getArenaData()
        val today = core.today()

        // 检查挑战次数（跨日视为 0 次，重置将在 mutate 内落地）
        val effectiveCount = if (arenaData.lastRefreshTime != today) 0 else arenaData.attackCount
        if (effectiveCount >= ArenaSaveData.DAILY_FREE_ATTACKS) {
            return ArenaChallengeOutcome.Rejected
        }

        // 获取玩家队伍
        val teamIds = core.saveData.getFormationIds()
        if (teamIds.isEmpty()) return ArenaChallengeOutcome.Rejected

        val myUnits = teamIds.mapNotNull { core.unitStatsFor(it) }
        if (myUnits.isEmpty()) return ArenaChallengeOutcome.Rejected

        val team = TeamResonance.apply(myUnits).toTypedArray()

        // 生成敌方队伍
        val enemyTeam = buildOpponentTeam(opponent)

        // 模拟战斗
        val result = simulator.simulate(team, enemyTeam, 50)
        val victory = result.victory
        val turns = result.turns
        val pointsDelta = if (victory) ArenaSaveData.WIN_POINTS else -ArenaSaveData.LOSE_POINTS
        
        val originalPoints = arenaData.arenaPoints
        val originalAttackCount = arenaData.attackCount
        val originalWinCount = arenaData.winCount
        val originalLoseCount = arenaData.loseCount
        val originalLastRefresh = arenaData.lastRefreshTime
        val originalBattleRecords = core.saveData.arenaBattleRecords
        
        val outcome = core.transaction(
            tag = "arena.attack",
            mutate = {
                // 跨日重置（R5-I2：与本次写同事务原子落盘，避免次数耗尽后永久锁死）
                if (arenaData.lastRefreshTime != today) {
                    arenaData.lastRefreshTime = today
                    arenaData.attackCount = 0
                }
                arenaData.attackCount++

                if (victory) {
                    arenaData.winCount++
                    arenaData.arenaPoints = (arenaData.arenaPoints + ArenaSaveData.WIN_POINTS)
                        .coerceAtMost(ArenaSaveData.MAX_POINTS)
                } else {
                    arenaData.loseCount++
                    arenaData.arenaPoints = (arenaData.arenaPoints - ArenaSaveData.LOSE_POINTS)
                        .coerceAtLeast(ArenaSaveData.MIN_POINTS)
                }
                // 战斗记录随事务原子落盘（此前在 onCommit 中写入，落盘已完成，
                // 新记录仅存内存——崩溃后丢失；现移入 mutate 保证原子性）
                core.saveData.arenaBattleRecords = core.saveData.arenaBattleRecords + PvPBattleRecord(
                    recordId = "pvp_${System.currentTimeMillis()}",
                    attackerId = "player",
                    defenderId = opponent.characterId,
                    attackerName = "玩家",
                    defenderName = opponent.name,
                    attackerTeam = teamIds,
                    defenderTeam = opponent.defenseTeam,
                    attackerPoints = originalPoints,
                    defenderPoints = opponent.points,
                    result = if (victory) "win" else "lose",
                    turns = turns,
                    timestamp = System.currentTimeMillis(),
                    pointsChanged = pointsDelta,
                )
            },
            rollback = {
                arenaData.arenaPoints = originalPoints
                arenaData.attackCount = originalAttackCount
                arenaData.winCount = originalWinCount
                arenaData.loseCount = originalLoseCount
                arenaData.lastRefreshTime = originalLastRefresh
                core.saveData.arenaBattleRecords = originalBattleRecords
            },
            onCommit = {
                core.publishProgressionChanged()
            },
        )
        return when (outcome) {
            WriteOutcome.Success -> ArenaChallengeOutcome.Completed(
                victory = victory,
                turns = turns,
                pointsDelta = pointsDelta,
                pointsAfter = arenaData.arenaPoints,
                opponentName = opponent.name,
            )
            WriteOutcome.Rejected -> ArenaChallengeOutcome.Rejected
            WriteOutcome.SaveFailed -> ArenaChallengeOutcome.SaveFailed
        }
    }
    
    /**
     * 生成对手队伍。
     */
    private fun buildOpponentTeam(opponent: ArenaOpponent): Array<UnitStats> {
        return opponent.defenseTeam.mapIndexed { index, charId ->
            val def = core.character(charId)
            UnitStats(
                // R5-T4：teamPower 低时随机扰动可让属性翻负，喂给 BattleSimulator 易产生
                // 负攻/负防/零血等脏输入；下限钳制（hp 至少 1，避免除零/即死）。
                atk = ((opponent.teamPower * 0.3).toInt() + rng.nextInt(-50, 51)).coerceAtLeast(0),
                def = ((opponent.teamPower * 0.2).toInt() + rng.nextInt(-30, 31)).coerceAtLeast(0),
                hp = ((opponent.teamPower * 0.5).toInt() + rng.nextInt(-100, 101)).coerceAtLeast(1),
                spd = rng.nextInt(80, 150),
                characterId = charId,
                element = def?.element ?: "",
            )
        }.toTypedArray()
    }
    
    /**
     * 获取竞技场排名信息。
     */
    override fun getArenaRank(): Pair<Int, String> {
        val arenaData = getArenaData()
        val rank = calculateRank(arenaData.arenaPoints)
        val rankTitle = when (rank) {
            6 -> "宗师"
            5 -> "钻石"
            4 -> "铂金"
            3 -> "黄金"
            2 -> "白银"
            else -> "青铜"
        }
        return Pair(rank, rankTitle)
    }
    
    /**
     * 获取赛季奖励。
     */
    override fun getSeasonRewards(): List<SeasonReward> {
        return listOf(
            SeasonReward(1, "冠军", 10000, 500, 500, "ur_weapon_001"),
            SeasonReward(2, "亚军", 8000, 300, 300, "ssr_weapon_001"),
            SeasonReward(3, "季军", 6000, 200, 200, "sr_weapon_001"),
            SeasonReward(10, "精英", 4000, 100, 100, null),
            SeasonReward(50, "勇士", 2000, 50, 50, null),
            SeasonReward(100, "斗士", 1000, 20, 20, null),
        )
    }
}
