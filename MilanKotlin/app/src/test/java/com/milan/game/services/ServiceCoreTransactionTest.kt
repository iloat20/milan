package com.milan.game.services

import com.milan.game.data.SaveManager
import com.milan.game.data.SaveProvider
import com.milan.game.domain.gacha.GachaEngine
import com.milan.game.domain.progression.ProgressionEngine
import com.milan.game.domain.progression.TalentEngine
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 事务模板失败路径测试。
 *
 * R4-06（2026-08-30 审查）：[ServiceCore.transactionLocked] 只覆盖了「落盘失败」一条失败路径，
 * 未覆盖「`mutate` 自身抛异常」。修复前的行为：
 *   mutate 已部分改写内存 → rollback 不执行 → save() 不执行 → onTrace 不留痕 →
 *   异常穿透后锁被释放，**残留的半截状态会被后续任意一次成功落盘持久化**，
 *   磁盘与内存静默分叉，且无任何排查线索。
 *
 * 本测试直接构造 [ServiceCore]（internal，同模块测试可见），不依赖具体业务服务。
 */
class ServiceCoreTransactionTest {

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

    private fun newCore(traces: MutableList<String>): ServiceCore = ServiceCore(
        saveManager = SaveManager(MemoryProvider()) { traces.add(it) },
        gacha = GachaEngine(Random(1)),
        talent = TalentEngine(),
        progression = ProgressionEngine(),
        rng = Random(1),
        onTrace = { traces.add(it) },
        today = { 0L },
    )

    @Test
    fun `mutate 抛异常时必须回滚并留痕`() = runTest {
        val traces = mutableListOf<String>()
        val core = newCore(traces)
        var rolledBack = false
        var committed = false

        val result = runCatching {
            core.transactionLocked(
                tag = "probe",
                mutate = { error("mutate boom") },
                rollback = { rolledBack = true },
                onCommit = { committed = true },
            )
        }

        assertTrue("异常必须原样上抛，不得被事务模板吞掉", result.isFailure)
        assertTrue(
            "mutate 抛异常时必须回滚内存——否则半截状态会被后续落盘持久化",
            rolledBack,
        )
        assertFalse("mutate 失败不得触发 onCommit（不广播）", committed)
        assertTrue(
            "必须留痕以便排查，实际 traces=${traces.joinToString(" | ")}",
            traces.any { it.startsWith("probe.mutate.threw") },
        )
    }

    @Test
    fun `rollback 自身抛异常时仍上抛 mutate 的异常`() = runTest {
        val traces = mutableListOf<String>()
        val core = newCore(traces)

        val result = runCatching {
            core.transactionLocked(
                tag = "probe2",
                mutate = { error("原始失败原因") },
                rollback = { error("回滚也失败") },
                onCommit = { },
            )
        }

        val thrown = result.exceptionOrNull()
        assertTrue("应抛出 mutate 的原始异常", thrown?.message == "原始失败原因")
        assertTrue(
            "回滚自身抛异常必须单独留痕（否则排查时看不到二次故障）",
            traces.any { it.startsWith("probe2.rollback.threw") },
        )
    }

    @Test
    fun `正常路径不受影响：落盘成功则提交且不留异常痕`() = runTest {
        val traces = mutableListOf<String>()
        val core = newCore(traces)
        var committed = false
        var rolledBack = false

        val outcome = core.transactionLocked(
            tag = "probe3",
            mutate = { },
            rollback = { rolledBack = true },
            onCommit = { committed = true },
        )

        assertTrue("落盘成功应返回 Success", outcome == WriteOutcome.Success)
        assertTrue("成功路径必须触发 onCommit", committed)
        assertFalse("成功路径不得回滚", rolledBack)
        assertTrue("成功路径不得留异常痕", traces.none { it.contains(".threw") })
    }
}
