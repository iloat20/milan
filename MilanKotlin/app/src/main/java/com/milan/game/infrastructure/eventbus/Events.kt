package com.milan.game.infrastructure.eventbus

/**
 * 事件类型定义（翻译 C# Events.cs + GameEvents.cs）。
 */

/** 经济变动（星尘 / 钻石）标记事件。
 * 作为"轻标记"使用：handler 收到后直接重读 GameState 当前值，不依赖负载里的旧数值，
 * 避免队列中连续多次扣费时负载过期导致显示陈旧。 */
object CurrencyChanged

/** 养成变动（升级 / 突破 / 天赋加点）标记事件。
 * 作为"轻标记"使用（无负载）：handler 收到后直接重读当前角色存档的实时值。 */
object ProgressionChanged

/** 抽卡结果事件。 */
data class GachaResultEvent(
    val itemId: String,
    val isNew: Boolean,
    val rarity: Int,
)

/** 角色升级事件。 */
data class CharacterLevelUpEvent(
    val characterId: String,
    val newLevel: Int,
)

/** 角色突破事件。 */
data class CharacterStageUpEvent(
    val characterId: String,
    val newStage: Int,
)

/** 天赋加点事件。 */
data class TalentAllocatedEvent(
    val characterId: String,
    val nodeId: String,
)

/** 角色升星事件。 */
data class CharacterBreakthroughEvent(
    val characterId: String,
)

/** 战斗结算事件。 */
data class BattleCompletedEvent(
    val stageId: String,
    val victory: Boolean,
)
