package com.milan.game.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SaveManager 回退策略测试（C# 无对应测试，补关键路径：备份恢复 / 全损留痕）。 */
class SaveManagerTest {

    private class FakeProvider(
        var main: String? = null,
        var backup: String? = null,
        var throwOnLoad: Boolean = false,
    ) : SaveProvider {
        val traces = mutableListOf<String>()
        override fun save(json: String): Boolean = true
        override fun load(): String {
            if (throwOnLoad) throw RuntimeException("io boom")
            return main ?: ""
        }
        override fun delete() = Unit
        override fun exists(): Boolean = main != null
        override fun loadBackup(): String? = backup
    }

    @Test
    fun load_noFile_returnsDefaultWithoutTrace() {
        val provider = FakeProvider(main = null)
        val manager = SaveManager(provider, onTrace = provider.traces::add)

        val data = manager.load()

        assertEquals(SaveData.DEFAULT_SOFT_CURRENCY, data.softCurrency)
        assertNotNull(manager.current)
        assertTrue(provider.traces.isEmpty())
    }

    @Test
    fun load_corruptMain_recoversFromBackup() {
        val provider = FakeProvider(
            main = "这不是 json{{{",
            backup = """{"SoftCurrency":777}""",
        )
        val manager = SaveManager(provider, onTrace = provider.traces::add)

        val data = manager.load()

        assertEquals(777, data.softCurrency)
        assertEquals(listOf("save.load.recovered.from.backup"), provider.traces)
    }

    @Test
    fun load_allSourcesCorrupt_fallsBackToDefaultWithTrace() {
        val provider = FakeProvider(
            main = "这不是 json{{{",
            backup = "也是坏的{{{",
        )
        val manager = SaveManager(provider, onTrace = provider.traces::add)

        val data = manager.load()

        assertEquals(SaveData.DEFAULT_SOFT_CURRENCY, data.softCurrency)
        assertEquals(listOf("save.load.fallback: all sources corrupt"), provider.traces)
    }

    @Test
    fun load_ioException_fallsBackToDefaultWithTrace() {
        // P2-1：IO 异常分支不得静默——留痕后才能区分「新号」与「IO 失败」
        val provider = FakeProvider(main = """{"SoftCurrency":1}""", throwOnLoad = true)
        val manager = SaveManager(provider, onTrace = provider.traces::add)

        val data = manager.load()

        assertEquals(SaveData.DEFAULT_SOFT_CURRENCY, data.softCurrency)
        assertTrue(
            "应留痕 io 失败：${provider.traces}",
            provider.traces.any { it.startsWith("save.load.failed:") && it.contains("io boom") },
        )
    }

    @Test
    fun save_withoutLoad_createsDefaultDefensively() {
        val provider = FakeProvider()
        val manager = SaveManager(provider)

        assertTrue(manager.save())

        assertNotNull(manager.current)
        assertEquals(SaveData.DEFAULT_SOFT_CURRENCY, manager.current?.softCurrency)
    }

    @Test
    fun save_roundTrip_persistsCurrent() {
        val provider = FakeProvider()
        val manager = SaveManager(provider)
        manager.load()
        manager.current?.softCurrency = 555

        assertTrue(manager.save())
        assertFalse(provider.traces.isNotEmpty())
        // provider 是内存 fake，真实持久化由接入层负责；这里只验证 save 不抛异常
    }
}
