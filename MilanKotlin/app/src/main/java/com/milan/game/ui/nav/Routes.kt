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
@Serializable
data object HomeRoute

@Serializable
data object GachaRoute

@Serializable
data object DeckRoute

@Serializable
data object ShopRoute

@Serializable
data object SettingsRoute

@Serializable
data object CollectionRoute

@Serializable
data object CharacterListRoute

@Serializable
data class CharacterDetailRoute(val characterId: String)

@Serializable
data class ProgressionRoute(val characterId: String)

/** [NavItem] → 对应 tab 路由（底部导航统一映射入口；命名 toNavRoute 避免与 androidx.navigation.toRoute 扩展同名）。 */
fun NavItem.toNavRoute(): Any = when (this) {
    NavItem.Home -> HomeRoute
    NavItem.Gacha -> GachaRoute
    NavItem.Deck -> DeckRoute
    NavItem.Shop -> ShopRoute
    NavItem.Settings -> SettingsRoute
}
