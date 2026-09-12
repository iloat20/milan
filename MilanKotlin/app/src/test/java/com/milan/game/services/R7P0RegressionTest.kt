package com.milan.game.services

import com.milan.game.data.SaveProvider
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R7 P0 回归：签到同日拦截、深渊首通/新星发奖门控。
 */
class R7P0RegressionTest {

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

    private fun service(today: Long = 20_000L) =
        GameService(MemoryProvider(), dataJson, {}, Random(1), today = { today })

    @Test
    fun `签到同日二次调用应拒绝`() = runTest {
        val svc = service(today = 30_000L)
        assertEquals(WriteOutcome.Success, svc.signToday())
        assertEquals(WriteOutcome.Rejected, svc.signToday())
        assertTrue(svc.getCheckInStatus().signedToday)
    }

    @Test
    fun `签到跨日可再签`() = runTest {
        val p = MemoryProvider()
        val day1 = GameService(p, dataJson, {}, Random(1), today = { 31_000L })
        assertEquals(WriteOutcome.Success, day1.signToday())
        val day2 = GameService(p, dataJson, {}, Random(1), today = { 31_001L })
        assertFalse(day2.getCheckInStatus().signedToday)
        assertEquals(WriteOutcome.Success, day2.signToday())
    }

    @Test
    fun `深渊重复结算同层不重复发奖`() = runTest {
        val svc = service()
        val before = svc.saveData.softCurrency
        // 首通 3 星：1*500*3 = 1500
        assertEquals(WriteOutcome.Success, svc.completeAbyssStage(1, 3))
        val afterFirst = svc.saveData.softCurrency
        assertEquals(before + 1500, afterFirst)
        // 同层同星再结算：不发奖（奖励决策在 writeMutex 内重读 stars，见 DungeonService）
        assertEquals(WriteOutcome.Success, svc.completeAbyssStage(1, 3))
        assertEquals("重复结算不得再发星尘", afterFirst, svc.saveData.softCurrency)
    }

    @Test
    fun `深渊星数刷新纪录可补发差额语义下的全额新星奖励`() = runTest {
        val svc = service()
        val before = svc.saveData.softCurrency
        assertEquals(WriteOutcome.Success, svc.completeAbyssStage(1, 1)) // 500
        val mid = svc.saveData.softCurrency
        assertEquals(before + 500, mid)
        // 从 1 星刷到 3 星：isNewBestStars → 再发 1*500*3
        assertEquals(WriteOutcome.Success, svc.completeAbyssStage(1, 3))
        assertEquals(mid + 1500, svc.saveData.softCurrency)
    }

    @Test
    fun `深渊每日挑战次数跨日重置`() = runTest {
        val shared = MemoryProvider()
        val d1 = GameService(shared, dataJson, {}, Random(1), today = { 41_000L })
        d1.saveData.abyssData?.let {
            it.challengeCount = 3
            it.lastResetTime = 41_000L
        }
        assertEquals(0, d1.getAbyssStatus().remainingChallenges)
        // 触发一次成功落盘以持久化
        assertEquals(WriteOutcome.Success, d1.signToday())

        val d2 = GameService(shared, dataJson, {}, Random(1), today = { 41_001L })
        assertEquals("跨日读路径应显示满次数", 3, d2.getAbyssStatus().remainingChallenges)
    }

    @Test
    fun `datajson 天赋树 BranchIds 含 branch_ultimate`() {
        val declared = Regex("\"branch_ultimate\"").findAll(dataJson).count()
        assertTrue("BranchIds 应含 branch_ultimate（found=$declared）", declared >= 31)
    }
}
