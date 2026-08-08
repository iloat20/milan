package com.milan.game.ui

import com.milan.game.ui.components.PortraitKey
import com.milan.game.ui.components.PortraitLruCache
import com.milan.game.ui.components.computeInSampleSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** PortraitLoader 纯 Kotlin 核心单测（Android 接入部分无法在 JVM 单测，留给真机验证）。 */
class PortraitLoaderTest {

    @Test
    fun `computeInSampleSize 需求不小于原图时返回 1`() {
        assertEquals(1, computeInSampleSize(832, 1186, 832, 1186))
        assertEquals(1, computeInSampleSize(832, 1186, 2000, 2000))
    }

    @Test
    fun `computeInSampleSize 按 2 的幂下采样到满足需求`() {
        // Thumb 档位语义（58dp 头像）：832×1186 → 208×296（4x）
        assertEquals(4, computeInSampleSize(832, 1186, 208, 296))
        // Full 档位语义（全屏 Hero）：832×1186 → 416×593（2x）
        assertEquals(2, computeInSampleSize(832, 1186, 416, 593))
    }

    @Test
    fun `computeInSampleSize 极小块继续下采样`() {
        assertEquals(8, computeInSampleSize(832, 1186, 104, 148))
    }

    @Test
    fun `PortraitKey 不同采样档位是不同键`() {
        assertNotEquals(PortraitKey(1, 2), PortraitKey(1, 4))
        assertEquals(PortraitKey(1, 2), PortraitKey(1, 2))
    }

    @Test
    fun `PortraitLruCache 超容量时逐出最久未用`() {
        val cache = PortraitLruCache<Int>(maxBytes = 100, sizeOf = { it })
        cache.put(PortraitKey(1, 2), 60)
        cache.put(PortraitKey(2, 2), 60) // 120 > 100 → 逐出键 1
        assertNull(cache.get(PortraitKey(1, 2)))
        assertEquals(60, cache.get(PortraitKey(2, 2)))
    }

    @Test
    fun `PortraitLruCache get 命中会刷新访问序`() {
        val cache = PortraitLruCache<Int>(maxBytes = 100, sizeOf = { it })
        cache.put(PortraitKey(1, 2), 40)
        cache.put(PortraitKey(2, 2), 40) // 80 ≤ 100 不逐出
        cache.get(PortraitKey(1, 2))     // 刷新键 1 访问序
        cache.put(PortraitKey(3, 2), 40) // 120 > 100 → 逐出最久未用的键 2
        assertNull(cache.get(PortraitKey(2, 2)))
        assertEquals(40, cache.get(PortraitKey(1, 2)))
        assertEquals(40, cache.get(PortraitKey(3, 2)))
    }
}
