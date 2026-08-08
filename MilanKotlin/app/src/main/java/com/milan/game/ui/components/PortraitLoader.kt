package com.milan.game.ui.components

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 角色立绘加载（C# PortraitLoader 的 Kotlin 等价物，2026-08-08 低端机性能优化）。
 *
 * 立绘资源 832×1186 ≈ 3.8MB/张：头像场景全尺寸同步解码是首屏卡顿主因，
 * 统一按显示档位采样（Full=2x / Thumb=4x），单图内存降 16 倍。
 *
 * 分层：本文件前半部为纯 Kotlin（可 JVM 单测），Android 接入（object
 * PortraitLoader）在 Task 2 追加于文件后半部 —— 纯逻辑不得依赖 android.*。
 */
enum class PortraitTarget(val sample: Int) {
    /** 全屏/大图（Hero/详情/养成/reveal）：416×593 ≈ 0.95MB。 */
    Full(2),

    /** 小图（≤58dp 头像/chip）：208×296 ≈ 0.24MB。 */
    Thumb(4),
}

/**
 * 采样率计算（BitmapFactory 文档标准算法）：
 * 需求尺寸不小于原图 → 1；否则按 2 的幂下采样，直到半尺寸不满足需求为止。
 */
fun computeInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
    var sample = 1
    if (srcH > reqH || srcW > reqW) {
        val halfH = srcH / 2
        val halfW = srcW / 2
        while (halfH / sample >= reqH && halfW / sample >= reqW) {
            sample *= 2
        }
    }
    return sample
}

/** 缓存键：资源 ID × 采样档位（同一资源不同档位互不驱逐）。 */
data class PortraitKey(val resId: Int, val sample: Int)

/** 缓存容量上限（spec D3）：24MB，低端机友好。 */
const val PORTRAIT_CACHE_BYTES = 24 * 1024 * 1024

/**
 * 最小 LRU 缓存（Android LruCache 语义的纯 Kotlin 复刻，可 JVM 单测）。
 * 线程安全：全部操作 synchronized；accessOrder=true 保证 get 命中刷新访问序。
 */
class PortraitLruCache<V : Any>(
    private val maxBytes: Int,
    private val sizeOf: (V) -> Int,
) {
    private val map = LinkedHashMap<PortraitKey, V>(0, 0.75f, true)

    @Synchronized
    fun get(key: PortraitKey): V? = map[key]

    @Synchronized
    fun put(key: PortraitKey, value: V): V? {
        val previous = map.put(key, value)
        trimToSize()
        return previous
    }

    @Synchronized
    fun size(): Int = map.size

    /** 从最久未用开始逐出，直到总占用 ≤ 上限。 */
    private fun trimToSize() {
        while (true) {
            val total = map.entries.sumOf { sizeOf(it.value) }
            if (total <= maxBytes || map.isEmpty()) return
            map.remove(map.entries.iterator().next().key)
        }
    }
}

// ---- Android 接入（以下依赖 android.*，不可进 JVM 单测）----

object PortraitLoader {
    private val cache = PortraitLruCache<Bitmap>(PORTRAIT_CACHE_BYTES) { it.byteCount }

    /**
     * 异步解码：命中缓存直接返回；未命中 IO 线程按档位采样解码并入缓存；
     * 解码失败/资源损坏返回 null（调用方走占位，宁可难看也不能崩）。
     */
    suspend fun load(res: Resources, resId: Int, target: PortraitTarget): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = PortraitKey(resId, target.sample)
            cache.get(key) ?: runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = target.sample }
                BitmapFactory.decodeResource(res, resId, opts)?.also { cache.put(key, it) }
            }.getOrNull()
        }
}
