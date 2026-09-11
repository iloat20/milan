package com.milan.game.services

import com.milan.game.data.SaveProvider
import com.milan.game.data.SeasonSaveData
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 签到 / 赛季跨重载持久化回归（2026-09-08 共享 R5-I5 ensure 不变量）。
 *
 * 背景：`SaveData.sanitize` 的 ensure 清单曾漏 dailyCheckInData/seasonData/collectionData →
 * 三个子系统写事务 mutate 的是 `?: XxxData()` 兜底瞬态对象、从不回写 saveData.xxxData →
 * 领取/签到态重启即丢、可无限重复领取货币。补漏后此组用例钉死持久化契约。
 * （collectionData 侧见 [CollectionUnlockDerivationTest]。）
 */
class CheckInSeasonPersistenceTest {

    private val dataJson: String =
        File("src/main/assets/data.json").takeIf { it.exists() }
            ?.readText(Charsets.UTF_8)
            ?: error("测试需真实内容文件：app/src/main/assets/data.json")

    private class MemoryProvider : SaveProvider {
        var stored: String? = null
        override fun save(json: String): Boolean {
            stored = json
            return true
        }

        override fun load(): String = stored ?: ""
        override fun delete() {
            stored = null
        }

        override fun exists(): Boolean = stored != null
        override fun loadBackup(): String? = null
    }

    /** 固定「今天」，保证两个实例看到同一签到/赛季周期。 [day] 可推进以模拟跨日。 */
    private fun newService(provider: MemoryProvider, day: Long = 20_000L) =
        GameService(provider, dataJson, {}, Random(42), today = { day })

    @Test
    fun `每日签到状态跨重载持久`() = runTest {
        val provider = MemoryProvider()

        val first = newService(provider, day = 20_000L)
        assertEquals("首次签到应成功", WriteOutcome.Success, first.signToday())
        assertEquals("签到后累计应 1", 1, first.getCheckInStatus().totalDays)
        assertTrue("同日已签", first.getCheckInStatus().signedToday)
        assertEquals("同日重复签到应拒绝", WriteOutcome.Rejected, first.signToday())

        // 模拟重启：同一 provider 重载存档（推进一天，R7-P0-2 同日不可连签）
        val reloaded = newService(provider, day = 20_001L)
        assertFalse("跨日应显示今日未签", reloaded.getCheckInStatus().signedToday)
        assertEquals("重载后累计签到应保留", 1, reloaded.getCheckInStatus().totalDays)

        assertEquals("跨日二次签到应成功", WriteOutcome.Success, reloaded.signToday())
        assertEquals("跨日二次签到应计入下一天（累计 2）", 2, reloaded.getCheckInStatus().totalDays)
    }

    @Test
    fun `赛季奖励领取态跨重载持久`() = runTest {
        val provider = MemoryProvider()

        val first = newService(provider)
        // 直接置积分到首档门槛（不走 recordWin 攒分，保持用例只测持久化）
        first.saveData.seasonData?.seasonPoints = SeasonSaveData.SEASON_REWARD_MILESTONES[0]
        val before = first.saveData.softCurrency
        assertEquals("首档奖励首次领取应成功", WriteOutcome.Success, first.claimSeasonReward(0))
        assertEquals("首档奖励应到账 5000 星尘", before + 5000, first.saveData.softCurrency)

        // 模拟重启：同一 provider 重载存档
        val reloaded = newService(provider)
        assertEquals(
            "重载后同档赛季奖励应仍为已领取（不可重复领取）",
            WriteOutcome.Rejected,
            reloaded.claimSeasonReward(0),
        )
    }
}
