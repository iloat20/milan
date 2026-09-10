package com.milan.game.services

import com.milan.game.data.SaveProvider
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 快照切片门控测试（2026-09-08 P0-4）。
 *
 * 背景：[GameSnapshot] 是单一扁平结构，17 个 Screen 全量订阅；任何 revision 推进
 * （哪怕只改了一个开关）都会让订阅作用域内的 composable 全部重算。
 * 切片（[EconomySlice] / [RosterSlice] / [ProgressSlice] / [GachaSlice] / [MetaSlice]）
 * 按关注点拆分，且**内容未变则不写入 StateFlow**（[ServiceCore] 的 `setIfChanged`），
 * 从而不发射新值、不惊动订阅者。
 *
 * 断言方式：StateFlow 未写入则 `value` 保持同一**引用**，故用 [assertSame] 直接验证
 * 「未发射」——比收集 Flow 事件更直接、且不受调度影响。
 *
 * 注意：本测试不触碰进程级单例 [com.milan.game.GameState]，避免与 Robolectric
 * 下 MilanApp 的异步注入产生竞态。
 */
class SnapshotSliceTest {

    private class FakeProvider : SaveProvider {
        var stored: String? = null
        override fun save(json: String): Boolean { stored = json; return true }
        override fun load(): String = stored ?: ""
        override fun delete() { stored = null }
        override fun exists(): Boolean = stored != null
        override fun loadBackup(): String? = null
    }

    /** content 传 null：走 GameContent 兜底副本，无需重复维护测试用 JSON。 */
    private fun service() = GameService(FakeProvider(), null, { }, Random(42))

    @Test
    fun metaSliceNotEmittedWhenOnlyCurrencyChanges() = runTest {
        val svc = service()

        // 先让 meta 切片偏离默认值，确保后续「引用未变」的比较有意义
        svc.setSoundEnabled(false)
        val metaAfterToggle = svc.meta.value
        assertEquals(false, metaAfterToggle.soundEnabled)

        val softBefore = svc.economy.value.softCurrency
        svc.grantSoft(100)

        // 货币切片应随发放更新
        assertTrue("货币切片应随发放更新", svc.economy.value.softCurrency > softBefore)
        // 设置切片内容未变 → 未写入 → 引用不变（即未发射新值）
        assertSame("只改货币时 meta 切片不应重新发射", metaAfterToggle, svc.meta.value)
        assertEquals(false, svc.meta.value.soundEnabled)
    }

    @Test
    fun economySliceNotEmittedWhenOnlySettingChanges() = runTest {
        val svc = service()
        svc.grantSoft(100)
        val economyAfterGrant = svc.economy.value

        svc.setVibrationEnabled(false)

        assertEquals(false, svc.meta.value.vibrationEnabled)
        assertSame(
            "只改设置时 economy 切片不应重新发射",
            economyAfterGrant,
            svc.economy.value,
        )
    }

    @Test
    fun slicesReflectLatestState() = runTest {
        val svc = service()
        svc.setSoundEnabled(true)
        svc.setVibrationEnabled(false)
        svc.grantSoft(250)

        assertEquals(true, svc.meta.value.soundEnabled)
        assertEquals(false, svc.meta.value.vibrationEnabled)
        assertTrue("切片应反映发放后的余额", svc.economy.value.softCurrency >= 250)
    }
}
