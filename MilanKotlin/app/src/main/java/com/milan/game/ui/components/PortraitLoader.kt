package com.milan.game.ui.components

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.milan.game.infrastructure.CrashReporter
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
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
    // I8：维护运行时字节计数器，避免每次 put/trim 全表 sumOf（驱逐 k 个时原实现 k×n）。
    private var bytes = 0

    @Synchronized
    fun get(key: PortraitKey): V? = map[key]

    @Synchronized
    fun put(key: PortraitKey, value: V): V? {
        val previous = map.put(key, value)
        if (previous != null) bytes -= sizeOf(previous)
        bytes += sizeOf(value)
        trimToSize()
        return previous
    }

    @Synchronized
    fun size(): Int = map.size

    /** P3-7：清空缓存（onLowMemory / 内存压力临界档位）。 */
    @Synchronized
    fun clear() { map.clear(); bytes = 0 }

    /** P3-7：按比例收缩缓存（保留 [keepFraction] 的字节上限，如 0.5 = 剩 12MB）。 */
    @Synchronized
    fun trimFraction(keepFraction: Float) {
        val target = (maxBytes * keepFraction.coerceIn(0f, 1f)).toInt()
        while (bytes > target && map.isNotEmpty()) {
            val victim = map.entries.iterator().next().key
            val removed = map.remove(victim) ?: return
            bytes -= sizeOf(removed)
        }
    }

    /** 从最久未用开始逐出，直到总占用 ≤ 上限。 */
    private fun trimToSize() {
        while (bytes > maxBytes && map.isNotEmpty()) {
            val victim = map.entries.iterator().next().key
            val removed = map.remove(victim) ?: return
            bytes -= sizeOf(removed)
        }
    }
}

// ---- Android 接入（以下依赖 android.*，不可进 JVM 单测）----

object PortraitLoader {
    private val cache = PortraitLruCache<Bitmap>(PORTRAIT_CACHE_BYTES) { it.byteCount }

    /** 解码中（in-flight）任务表：同一键只允许一个解码协程，其余协程复用其结果（并发去重）。 */
    private val inflight = ConcurrentHashMap<PortraitKey, CompletableDeferred<Bitmap?>>()

    /** 资源 id 探测结果进程级记忆化（I11 补充）：Lazy 网格反复滚入滚出不再每次主线程反射查表。
     *  单包应用，name 即唯一键；getOrPut 非原子但幂等，并发双写无害。 */
    private val identifierCache = ConcurrentHashMap<String, Int>()

    /** 记忆化 drawable 探测：缺失返回 0（与 [android.content.res.Resources.getIdentifier] 语义一致）。 */
    fun resourceIdOf(
        resources: android.content.res.Resources,
        packageName: String,
        name: String,
    ): Int = identifierCache.getOrPut(name) { resources.getIdentifier(name, "drawable", packageName) }

    /**
     * 内存压力回调（P3-7）：PortraitImage 组合期注册到 applicationContext，
     * 低档位收缩一半缓存、临界档位/onLowMemory 全清——此前 24MB LRU 对系统内存压力无感知，
     * 低端机在大量立绘缓存后易触发更激进的系统回收。
     */
    val memoryCallbacks: android.content.ComponentCallbacks2 = object : android.content.ComponentCallbacks2 {
        @Suppress("DEPRECATION")
        override fun onTrimMemory(level: Int) {
            when {
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> cache.clear()
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> cache.trimFraction(0.5f)
            }
        }

        @Deprecated("Deprecated in Java", ReplaceWith("onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)"))
        override fun onLowMemory() {
            cache.clear()
        }

        override fun onConfigurationChanged(newConfig: android.content.res.Configuration) { }
    }

    /**
     * 异步解码：命中缓存直接返回；未命中 IO 线程按档位采样解码并入缓存；
     * 同一资源并发请求只解码一次（快速滚动时列表多处引用同一立绘，避免重复解码与内存峰值）；
     * 解码失败/资源损坏返回 null（调用方走占位，宁可难看也不能崩）。
     */
    suspend fun load(res: Resources, resId: Int, target: PortraitTarget): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = PortraitKey(resId, target.sample)
            try {
                cache.get(key) ?: decodeOnce(res, key, target)
            } catch (t: Throwable) {
                // C2：OutOfMemoryError 是 Error 非 Exception，必须在此兜底，否则低端机解码直接闪退。
                // 取消异常原样上抛以维持结构化并发语义；其余异常留痕后返回占位（宁可难看也不崩）。
                if (t is CancellationException) throw t
                CrashReporter.traceNonFatal("PortraitLoader.load(${key.resId})", t)
                null
            }
        }

    /** 单飞解码：占 in-flight 槽 → 解码 → 入缓存 → 完成；已有请求在解则直接等其结果。 */
    private suspend fun decodeOnce(res: Resources, key: PortraitKey, target: PortraitTarget): Bitmap? {
        val deferred = CompletableDeferred<Bitmap?>()
        // C3：直接用 putIfAbsent 的返回值；丢弃返回值再二次查表会在持有者已释放槽的间隙拿到 null，
        // 上层误判解码失败长期占位。这里 existing 即既有 deferred，非空则复用其结果。
        val existing = inflight.putIfAbsent(key, deferred)
        if (existing != null) return existing.await()
        try {
            val opts = BitmapFactory.Options().apply { inSampleSize = target.sample }
            val bmp = BitmapFactory.decodeResource(res, key.resId, opts)
            if (bmp != null) cache.put(key, bmp)
            deferred.complete(bmp)
            return bmp
        } catch (t: Throwable) {
            // C2：OOM(Error) 一并兜底；取消异常原样上抛，其余留痕后占位。
            if (t is CancellationException) throw t
            CrashReporter.traceNonFatal("PortraitLoader.decode(${key.resId})", t)
            deferred.complete(null)
            return null
        } finally {
            inflight.remove(key, deferred)
        }
    }
}
