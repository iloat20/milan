package com.milan.game.services

import com.milan.game.data.SaveProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 天赋前置索引测试（R4-05，2026-08-30 审查）。
 *
 * 修复前 [ServiceCore.prereqMap] 用懒初始化的 **可变** HashMap 做缓存，是全服务层唯一在
 * **无锁路径上写的共享可变状态**：`canAllocateTalent`（主线程，非 suspend 读接口）与
 * `allocateTalent`（writeMutex 内的后台线程）会并发 `getOrPut`，LinkedHashMap 并发写
 * 可能触发 resize 竞态 → 结构损坏甚至死循环；`loadContent` 置 null 与 `?.also{}` 之间
 * 还有 check-then-act 竞态，会让内容重载后仍返回旧树的前置映射。
 *
 * 修复后索引在 `talentTrees` 的 setter 里一次性构建为不可变 Map，读者只做查表。
 *
 * ⚠️ **测试定位说明**：并发竞态是概率性的（需要特定 resize 时序才能稳定复现），
 * 因此下面第三个用例是**回归网**（防止将来退化回「读者写共享缓存」的写法），
 * 而不是「修复前必然失败」的红灯证明。确定性的部分是前两个用例：
 * 索引内容必须与树定义逐节点一致，且内容重载后必须随之重建。
 */
class TalentPrereqIndexTest {

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

    @Test
    fun `prereqMap 与树定义逐节点一致`() {
        val service = GameService(MemoryProvider(), dataJson)
        assertTrue("应有天赋树", service.talentTrees.isNotEmpty())
        service.talentTrees.forEach { tree ->
            val expected = tree.nodes.associate { n -> n.nodeId to n.prerequisiteNodeIds }
            assertEquals("树 ${tree.treeId} 的前置映射与定义不一致", expected, service.prereqMap(tree))
        }
    }

    @Test
    fun `内容重载后索引随之重建且仍与定义一致`() {
        val service = GameService(MemoryProvider(), dataJson)
        val treeId = service.talentTrees.first().treeId
        val before = service.prereqMap(service.talentTrees.first { it.treeId == treeId })

        service.loadContent(dataJson) // 重载同一份内容

        val after = service.prereqMap(service.talentTrees.first { it.treeId == treeId })
        assertEquals("重载后前置映射应与重载前一致", before, after)

        // 兜底路径同样要重建索引（旧实现的缓存作废依赖 loadContent 手工置 null，易漏）
        service.loadContent(null)
        val fallback = service.prereqMap(service.talentTrees.first { it.treeId == treeId })
        assertEquals(
            "切到兜底内容后，索引必须随 talentTrees 重建而非沿用旧内容",
            service.talentTrees.first { it.treeId == treeId }.nodes
                .associate { n -> n.nodeId to n.prerequisiteNodeIds },
            fallback,
        )
    }

    @Test
    fun `并发读取 prereqMap 不产生结构损坏`() = runTest {
        val service = GameService(MemoryProvider(), dataJson)
        val trees = service.talentTrees
        coroutineScope {
            repeat(64) {
                launch(Dispatchers.Default) {
                    repeat(20) { trees.forEach { tree -> service.prereqMap(tree) } }
                }
            }
        }
        // 无 ConcurrentModificationException / 无挂死即通过
        assertEquals("并发读取后天赋树数量不变", trees.size, service.talentTrees.size)
    }
}
