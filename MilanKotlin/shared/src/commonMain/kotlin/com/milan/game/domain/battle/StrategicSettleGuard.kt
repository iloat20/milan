package com.milan.game.domain.battle

/**
 * 策略战斗结算侧「胜利可信度」校验（R6-P2）。
 *
 * 状态机仍在 UI，服务端无法重放整场；但调用方若只传 `victory=true` 而不带日志，
 * 可被任意刷层。约定：结算必须附带本场 [StrikeEvent] 日志，并满足
 * 「最后一记击杀落在正确阵营」这一弱一致性——成本低，足以堵住无日志/反向断言的脚本调用。
 *
 * 彻底收口仍依赖状态机下沉领域层。
 */
object StrategicSettleGuard {

    /**
     * @param log 本场战斗日志（应与结算的 turns 同场）
     * @param playerTeamIds 我方 characterId 集合
     * @param victory 调用方断言的胜负
     * @return true = 日志与断言不矛盾，可继续发奖
     */
    fun isVictoryPlausible(
        log: List<StrikeEvent>,
        playerTeamIds: Set<String>,
        victory: Boolean,
    ): Boolean {
        // 无交战却称胜利：不可信
        if (log.isEmpty()) return !victory

        val lastDefeat = log.lastOrNull { it.targetDefeated } ?: return false

        return if (victory) {
            // 胜利：最后一记击杀必须落在敌方
            lastDefeat.targetId !in playerTeamIds
        } else {
            // 失败/平局按失败断言：最后一记击杀应落在我方（无击杀则不成立）
            lastDefeat.targetId in playerTeamIds
        }
    }
}
