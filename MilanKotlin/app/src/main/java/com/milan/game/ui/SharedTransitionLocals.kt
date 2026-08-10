package com.milan.game.ui

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

/**
 * SharedTransitionScope 注入点（自建）。
 *
 * 历史坑因：Compose 1.11（BOM 2026.04.01，对应 androidx-main）已移除官方
 * `androidx.compose.animation.LocalSharedTransitionScope`——SharedTransitionLayout 的 content
 * 直接以 [SharedTransitionScope] 为 receiver，不再提供 CompositionLocal。为让深层子项
 * （列表卡片 / 详情 Hero）也能拿到 scope 且保持「null 安全降级为普通渲染」语义，
 * 由 MainActivity 在 SharedTransitionLayout content 内 `CompositionLocalProvider` 注入。
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
