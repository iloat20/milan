package com.milan.game.ui.nav

import kotlinx.serialization.Serializable

/**
 * 类型安全路由定义（Navigation Compose 2.9 类型安全导航，替代自研状态路由）。
 *
 * 路由模型与旧状态路由一一对应：
 * - 5 个主 tab 目的地（底部导航，各自渲染 GameNavBar）
 * - 4 个子页目的地（神谱图鉴 → 我的角色 → 角色详情 → 角色养成），压栈覆盖 tab，
 *   顶栏返回/系统返回逐层退出（Predictive Back 由 Navigation 自动接入）。
 *
 * data class 路由携带参数（characterId），编译期由 Navigation 校验，杜绝字符串路由笔误。
 */
/**
 * 主 tab 路由（5 项，与 NavItem 一一对应）。
 * M16：sealed interface 收口 [NavItem.toNavRoute] 的返回类型（原 `Any` 放弃编译期校验），
 * 编译器保证映射不会漏分支、不会把子页路由塞进底部导航。
 */
sealed interface TabRoute

@Serializable
data object HomeRoute : TabRoute

@Serializable
data object GachaRoute : TabRoute

@Serializable
data object DeckRoute : TabRoute

@Serializable
data object ShopRoute : TabRoute

@Serializable
data object SettingsRoute : TabRoute

@Serializable
data object CollectionRoute

@Serializable
data object CharacterListRoute

@Serializable
data class CharacterDetailRoute(val characterId: String)

@Serializable
data class ProgressionRoute(val characterId: String)

/** 角色检视（2026-09-11 接线）：子页，盖住底部 tab，返回回角色详情。 */
@Serializable
data class InspectionRoute(val characterId: String)

/** 无尽之塔（2026-08 终局内容）：子页，盖住底部 tab，返回逐层退出。 */
@Serializable
data object TowerRoute

/** 成就（2026-08 二期）：子页，盖住底部 tab，返回回主页。 */
@Serializable
data object AchievementRoute

/** 抽卡历史（2026-08 三期）：子页，盖住底部 tab，返回回抽卡页。 */
@Serializable
data object PullHistoryRoute

/** 剧情系统（2026-09）：子页，盖住底部 tab，返回回主页。 */
@Serializable
data object StoryRoute

/** 剧情关卡（2026-09）：子页，盖住底部 tab，返回回章节列表。 */
@Serializable
data class DialogueRoute(val stageId: String)

/** 每日任务（2026-09）：子页，盖住底部 tab，返回回主页。 */
@Serializable
data object DailyMissionRoute

/** Battle Pass 纪行（2026-09）：子页，盖住底部 tab，返回回主页。 */
@Serializable
data object BattlePassRoute

/** 角色好感度（2026-09）：子页，盖住底部 tab，返回回主页。 */
@Serializable
data object AffinityRoute

/** PVP 竞技场：子页，盖住底部 tab，返回回主页。 */
@Serializable
data object ArenaRoute

// PVE 副本路由 PvERoute 已删除（2026-09-06 S2，PvEService 死功能裁撤）。

/** 活动：子页，盖住底部 tab，返回回主页。 */
@Serializable
data object EventRoute

/** [NavItem] → 对应 tab 路由（底部导航统一映射入口；命名 toNavRoute 避免与 androidx.navigation.toRoute 扩展同名）。 */
fun NavItem.toNavRoute(): TabRoute = when (this) {
    NavItem.Home -> HomeRoute
    NavItem.Gacha -> GachaRoute
    NavItem.Deck -> DeckRoute
    NavItem.Shop -> ShopRoute
    NavItem.Settings -> SettingsRoute
}
