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


