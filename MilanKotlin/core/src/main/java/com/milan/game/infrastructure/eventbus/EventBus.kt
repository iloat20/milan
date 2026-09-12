package com.milan.game.infrastructure.eventbus

/**
 * 轻量事件总线（C# Milan.Infrastructure.EventBus.EventBus 翻译）。
 *
 * publish 只入队，必须由宿主定期调用 [dispatch] 才会真正派发。
 * 线程模型：所有静态状态的读写都在 gate 下进行，handler 调用放在锁外，
 * 避免 handler 内再订阅造成死锁。
 */
object EventBus {

    private val gate = Any()
    private val subs = mutableMapOf<Class<*>, MutableList<Subscriber>>()
    private val queue = ArrayDeque<Pair<Class<*>, Any>>()

    /** 队列上限，防止无人调用 [dispatch] 时事件无限堆积。 */
    private const val MAX_QUEUED = 512

    /** handler 抛异常时的留痕回调（由宿主注入 CrashReporter；core 层保持纯逻辑，不依赖 Android）。 */
    var handlerException: ((Class<*>, Exception) -> Unit)? = null

    // ── 内联包装器 ──
    // reified 需要 inline，而 public inline 函数不能访问私有状态 / 私有构造器（Kotlin API 限制），
    // 因此类型转换在 inline 层完成，真正的状态操作下沉到公开的 Raw 辅助函数。

    /** 订阅事件；[owner] 用于 [unsubscribeAll] 批量退订（对应 C# 委托 Target）。 */
    inline fun <reified T : Any> subscribe(owner: Any? = null, noinline handler: (T) -> Unit) {
        // 在 inline 层完成 as-T 转换（Raw 函数拿不到 reified T）
        subscribeRaw(T::class.java, owner, handler) { e -> handler(e as T) }
    }

    inline fun <reified T : Any> unsubscribe(noinline handler: (T) -> Unit) {
        unsubscribeRaw(T::class.java, handler)
    }

    inline fun <reified T : Any> publish(event: T) {
        publishRaw(T::class.java, event)
    }

    // ── 非内联实现（可访问私有状态）──

    /** 低层订阅：先移除同一个 handler 再追加，保证重复 subscribe 不会被调用多次。 */
    fun subscribeRaw(type: Class<*>, owner: Any?, raw: Any, invoke: (Any) -> Unit) {
        synchronized(gate) {
            val list = subs.getOrPut(type) { mutableListOf() }
            list.removeAll { it.raw === raw }
            list.add(Subscriber(owner, raw, invoke))
        }
    }

    /** 低层退订（按 handler 实例去重移除）。 */
    fun unsubscribeRaw(type: Class<*>, raw: Any) {
        synchronized(gate) {
            val list = subs[type] ?: return
            list.removeAll { it.raw === raw }
            if (list.isEmpty()) subs.remove(type)
        }
    }

    /** 该类型当前是否有订阅者。 */
    fun hasSubscribers(type: Class<*>): Boolean =
        synchronized(gate) { !subs[type].isNullOrEmpty() }

    /** 低层入队：无人 dispatch 时丢弃最旧事件，避免队列无界增长。 */
    fun publishRaw(type: Class<*>, event: Any) {
        // 2026-09-12：生产 UI 零订阅、全走 StateFlow——无订阅者时直接丢弃，写路径不空入队。
        // 须先 subscribe 再 publish；测试亦按此顺序。
        if (!hasSubscribers(type)) return
        synchronized(gate) {
            while (queue.size >= MAX_QUEUED) queue.removeFirst()
            queue.addLast(type to event)
        }
    }

    /** 当前待派发事件数（供宿主判断是否需要 [dispatch]）。 */
    val pendingCount: Int
        get() = synchronized(gate) { queue.size }

    /** 派发本轮开始时已入队的事件；handler 内再 publish 的新事件留到下一轮（防同类型无限循环）。 */
    fun dispatch() {
        var n: Int
        synchronized(gate) { n = queue.size }
        if (n <= 0) return

        for (i in 0 until n) {
            // 锁内取出一条（本轮开始时已入队的事件）；队列已被并发清空则提前结束本轮。
            val (type, ev) = synchronized(gate) { queue.removeFirstOrNull() } ?: break
            // 订阅者快照：handler 内 subscribe / unsubscribe 不影响本轮其余订阅者。
            val handlers = synchronized(gate) { subs[type]?.toList() }
            // 锁外调用：handler 内可安全地 subscribe / unsubscribe / publish。
            handlers?.forEach { sub ->
                try {
                    sub.invoke(ev)
                } catch (ex: Exception) {
                    // 单个 handler 失败不中断链，也不丢失后续事件。
                    println("[Milan] EventBus handler threw for ${type.name}: $ex")
                    try {
                        handlerException?.invoke(type, ex)
                    } catch (logEx: Exception) {
                        println("[Milan] EventBus 留痕钩子失败: ${logEx.message}")
                    }
                }
            }
        }
    }

    /** 清空订阅与队列。 */
    fun clear() = synchronized(gate) {
        subs.clear()
        queue.clear()
    }

    /** 仅清空队列（场景卸载时调用），保留订阅者。 */
    fun clearQueue() = synchronized(gate) { queue.clear() }

    /** 取消 [target] 的所有订阅（在 Activity 销毁时调用）。 */
    fun unsubscribeAll(target: Any) {
        synchronized(gate) {
            val types = subs.keys.toList()
            for (t in types) {
                val list = subs[t] ?: continue
                list.removeAll { it.owner === target }
                if (list.isEmpty()) subs.remove(t)
            }
        }
    }

    private class Subscriber(val owner: Any?, val raw: Any, val invoke: (Any) -> Unit)
}
