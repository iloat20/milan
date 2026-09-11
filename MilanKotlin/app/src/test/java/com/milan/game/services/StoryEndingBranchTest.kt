package com.milan.game.services

import com.milan.game.data.SaveProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * P3 结局分支存档：setStoryEndingBranch / getStoryEndingBranchId。
 * 旧档无 EndingBranchId 键时反序列化为 null（向后兼容）。
 */
class StoryEndingBranchTest {

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
    fun `default ending is null and set overwrites`() = runTest {
        val provider = MemoryProvider()
        val svc = GameService(saveProvider = provider, contentJson = null)
        assertNull(svc.getStoryEndingBranchId())

        assertEquals(WriteOutcome.Success, svc.setStoryEndingBranch("end_dawn"))
        assertEquals("end_dawn", svc.getStoryEndingBranchId())

        assertEquals(WriteOutcome.Success, svc.setStoryEndingBranch("end_burn"))
        assertEquals("end_burn", svc.getStoryEndingBranchId())
    }

    @Test
    fun `blank ending rejected`() = runTest {
        val svc = GameService(saveProvider = MemoryProvider(), contentJson = null)
        assertEquals(WriteOutcome.Rejected, svc.setStoryEndingBranch(""))
        assertNull(svc.getStoryEndingBranchId())
    }
}
