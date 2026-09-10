package com.milan.game.ui.effects

import androidx.compose.runtime.compositionLocalOf

/**
 * 动效减弱（无障碍，设计语言 P3）。
 *
 * true = 关闭高负载演出：抽卡仪式粒子、战斗 vignette 长闪烁、结算粒子等；
 * 只保留伤害飘字、结算淡入等必要信息动画。
 * 由 [com.milan.game.ui.nav.MilanNavHost] 从 MetaSlice 注入。
 */
val LocalReduceMotion = compositionLocalOf { false }
