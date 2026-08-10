package com.milan.game.ui.nav

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 类型安全路由单测（Navigation Compose 2.9 类型安全导航，Routes.kt）：
 * - NavItem → toNavRoute() 映射完整：5 个主 tab 各映射到唯一路由（when 穷举由编译器兜底，这里验证语义正确性）；
 * - @Serializable 路由 JSON 序列化往返一致：data class 路由（characterId 参数）+ data object 路由
 *   （Navigation 内部按路由序列化恢复状态，参数漂移=存档/导航状态全丢，同 data.json 键名红线一个道理）。
 */
class RoutesTest {

    private val json = Json

    /** 序列化往返助手：encode → decode 后与原始值相等。 */
    private fun <T> assertRoundTrip(serializer: KSerializer<T>, value: T) {
        val encoded = json.encodeToString(serializer, value)
        assertEquals(value, json.decodeFromString(serializer, encoded))
    }

    @Test
    fun `toNavRoute 映射完整_五个主 tab 各对应唯一路由`() {
        // NavItem 全量枚举逐个映射：目标路由两两不同（一个 tab 只能对应一个目的地）
        val mapped = NavItem.entries.map { it.toNavRoute() }
        assertEquals("NavItem 数量", 5, mapped.size)
        assertEquals("路由目标应互不相同", 5, mapped.toSet().size)

        // 语义逐一断言（data object 单例，equals 即实例同一）
        assertEquals(HomeRoute, NavItem.Home.toNavRoute())
        assertEquals(GachaRoute, NavItem.Gacha.toNavRoute())
        assertEquals(DeckRoute, NavItem.Deck.toNavRoute())
        assertEquals(ShopRoute, NavItem.Shop.toNavRoute())
        assertEquals(SettingsRoute, NavItem.Settings.toNavRoute())
    }

    @Test
    fun `data class 路由参数序列化往返一致`() {
        val detail = CharacterDetailRoute("char_ur_001")
        val encoded = json.encodeToString(CharacterDetailRoute.serializer(), detail)
        // 参数必须真实参与序列化（丢失 = 打开详情页时 characterId 为空）
        assert(encoded.contains("char_ur_001")) { "序列化结果应携带 characterId，实际：$encoded" }
        assertRoundTrip(CharacterDetailRoute.serializer(), detail)

        assertRoundTrip(ProgressionRoute.serializer(), ProgressionRoute("char_ssr_007"))
        assertRoundTrip(CharacterDetailRoute.serializer(), CharacterDetailRoute(""))
    }

    @Test
    fun `data object 路由序列化往返一致_主 tab 与子页全量`() {
        // 主 tab + 无参子页共 7 个 data object 路由：全部可序列化往返
        assertRoundTrip(HomeRoute.serializer(), HomeRoute)
        assertRoundTrip(GachaRoute.serializer(), GachaRoute)
        assertRoundTrip(DeckRoute.serializer(), DeckRoute)
        assertRoundTrip(ShopRoute.serializer(), ShopRoute)
        assertRoundTrip(SettingsRoute.serializer(), SettingsRoute)
        assertRoundTrip(CollectionRoute.serializer(), CollectionRoute)
        assertRoundTrip(CharacterListRoute.serializer(), CharacterListRoute)
    }

    @Test
    fun `data object 路由序列化反序列化应返回同一实例`() {
        // data object 的 decode 结果必须是单例（导航栈恢复后路由可比对，若每次新建实例则 hasRoute/currentDestination 比对失真）。
        // 编码格式由 kotlinx.serialization 决定（data object 默认空串），这里只验证行为不固化格式。
        val encoded = json.encodeToString(HomeRoute.serializer(), HomeRoute)
        val decoded = json.decodeFromString(HomeRoute.serializer(), encoded)
        assertSame("应返回单例 HomeRoute", HomeRoute, decoded)
    }
}
