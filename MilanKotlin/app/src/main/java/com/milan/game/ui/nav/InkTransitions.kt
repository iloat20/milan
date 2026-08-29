package com.milan.game.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * 水墨国风页面转场动画常量。
 * - 子页：右进左出（墨汁泼入/干涸）
 * - Tab：交叉淡入淡出（平等切换）
 * - 返回：左进右出（Predictive Back 兼容）
 */
object InkTransitions {

    /** 子页进入：右滑 + 淡入 + 微缩 */
    val slideInFromRight: EnterTransition =
        slideInHorizontally(tween(200)) { it / 3 } +
                fadeIn(tween(200)) +
                scaleIn(tween(200), initialScale = 0.97f)

    /** 子页退出：左滑 + 淡出 */
    val slideOutToLeft: ExitTransition =
        slideOutHorizontally(tween(150)) { -it / 3 } +
                fadeOut(tween(150))

    /** 返回进入：右滑 + 淡入 */
    val slideInFromLeft: EnterTransition =
        slideInHorizontally(tween(150)) { -it / 3 } +
                fadeIn(tween(150))

    /** 返回退出：左滑 + 淡出（墨迹干涸） */
    val slideOutToRight: ExitTransition =
        slideOutHorizontally(tween(200)) { it / 3 } +
                fadeOut(tween(200))

    /** Tab 切换：交叉淡入淡出 + 微缩 */
    val tabEnter: EnterTransition =
        fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.97f)

    val tabExit: ExitTransition =
        fadeOut(tween(180))
}
