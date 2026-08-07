package com.milan.game.infrastructure.eventbus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** EventBus 队列 + 派发语义测试（C# EventBus 无对应测试，补关键路径：去重 / 快照 / 队列上限 / 异常隔离）。 */
class EventBusTest {

    @Before
    fun setUp() {
        // EventBus 是进程级单例，避免用例间状态残留
        EventBus.clear()
        EventBus.handlerException = null
    }

    @Test
    fun publish_queuesWithoutDispatching() {
        val received = mutableListOf<String>()
        EventBus.subscribe<String> { received.add(it) }

        EventBus.publish("a")
        EventBus.publish("b")

        assertTrue(received.isEmpty())
        assertEquals(2, EventBus.pendingCount)
    }

    @Test
    fun dispatch_deliversInOrder() {
        val received = mutableListOf<String>()
        EventBus.subscribe<String> { received.add(it) }

        EventBus.publish("a")
        EventBus.publish("b")
        EventBus.dispatch()

        assertEquals(listOf("a", "b"), received)
        assertEquals(0, EventBus.pendingCount)
    }

    @Test
    fun subscribe_sameHandlerTwice_deliversOnce() {
        var count = 0
        val handler: (String) -> Unit = { count++ }
        EventBus.subscribe(handler = handler)
        EventBus.subscribe(handler = handler)

        EventBus.publish("x")
        EventBus.dispatch()

        assertEquals(1, count)
    }

    @Test
    fun unsubscribe_removesHandler() {
        var count = 0
        val handler: (String) -> Unit = { count++ }
        EventBus.subscribe(handler = handler)

        EventBus.unsubscribe(handler)
        EventBus.publish("x")
        EventBus.dispatch()

        assertEquals(0, count)
    }

    @Test
    fun unsubscribeAll_target_keepsOtherOwners() {
        val ownerA = Any()
        val ownerB = Any()
        val fromA = mutableListOf<String>()
        val fromB = mutableListOf<String>()
        EventBus.subscribe<String>(owner = ownerA) { fromA.add(it) }
        EventBus.subscribe<String>(owner = ownerB) { fromB.add(it) }

        EventBus.unsubscribeAll(ownerA)
        EventBus.publish("x")
        EventBus.dispatch()

        assertTrue(fromA.isEmpty())
        assertEquals(listOf("x"), fromB)
    }

    @Test
    fun dispatch_subscribeInsideHandler_appliesToLaterEventsInSameRound() {
        var earlyCount = 0
        var lateCount = 0
        val late: (String) -> Unit = { lateCount++ }
        EventBus.subscribe<String> { s ->
            earlyCount++
            if (earlyCount == 1) EventBus.subscribe(handler = late)
        }

        EventBus.publish("a")
        EventBus.publish("b")
        EventBus.dispatch()

        // 与 C# Dispatch 语义一致：每个事件出队时重新查订阅表，
        // handler 内新增的订阅从本轮后续事件开始生效。
        assertEquals(2, earlyCount)
        assertEquals(1, lateCount)

        EventBus.publish("c")
        EventBus.dispatch()
        assertEquals(2, lateCount)
    }

    @Test
    fun dispatch_publishInsideHandler_defersToNextRound() {
        val received = mutableListOf<String>()
        EventBus.subscribe<String> { s ->
            received.add(s)
            if (s == "a") EventBus.publish("b")
        }

        EventBus.publish("a")
        EventBus.dispatch()
        assertEquals(listOf("a"), received)

        EventBus.dispatch()
        assertEquals(listOf("a", "b"), received)
    }

    @Test
    fun publish_overQueueLimit_dropsOldest() {
        val received = mutableListOf<String>()
        EventBus.subscribe<String> { received.add(it) }

        repeat(513) { i -> EventBus.publish("e$i") }

        assertEquals(512, EventBus.pendingCount)
        EventBus.dispatch()
        assertEquals(512, received.size)
        assertEquals("e1", received.first())
        assertEquals("e512", received.last())
    }

    @Test
    fun dispatch_handlerThrows_otherHandlersStillRunAndTraceInvoked() {
        val received = mutableListOf<String>()
        val traced = mutableListOf<Pair<Class<*>, Exception>>()
        EventBus.handlerException = { type, ex -> traced.add(type to ex) }
        EventBus.subscribe<String> { throw IllegalStateException("boom") }
        EventBus.subscribe<String> { received.add(it) }

        EventBus.publish("x")
        EventBus.dispatch()

        assertEquals(listOf("x"), received)
        assertEquals(1, traced.size)
        assertEquals(String::class.java, traced[0].first)
        assertTrue(traced[0].second is IllegalStateException)
    }

    @Test
    fun dispatch_traceHookThrows_doesNotBreakDispatch() {
        val received = mutableListOf<String>()
        EventBus.handlerException = { _, _ -> throw RuntimeException("log boom") }
        EventBus.subscribe<String> { throw IllegalStateException("boom") }
        EventBus.subscribe<String> { received.add(it) }

        EventBus.publish("x")
        EventBus.dispatch()

        assertEquals(listOf("x"), received)
    }

    @Test
    fun dispatch_separatesEventTypes() {
        val strings = mutableListOf<String>()
        val ints = mutableListOf<Int>()
        EventBus.subscribe<String> { strings.add(it) }
        EventBus.subscribe<Int> { ints.add(it) }

        EventBus.publish("s")
        EventBus.publish(1)
        EventBus.dispatch()

        assertEquals(listOf("s"), strings)
        assertEquals(listOf(1), ints)
    }

    @Test
    fun objectEvent_publishesAndDispatches() {
        var count = 0
        EventBus.subscribe<CurrencyChanged> { count++ }

        EventBus.publish(CurrencyChanged)
        assertEquals(1, EventBus.pendingCount)
        EventBus.dispatch()
        assertEquals(1, count)
    }

    @Test
    fun clearQueue_dropsPendingButKeepsSubscribers() {
        var count = 0
        EventBus.subscribe<String> { count++ }

        EventBus.publish("a")
        EventBus.clearQueue()
        EventBus.dispatch()

        assertEquals(0, count)
        assertEquals(0, EventBus.pendingCount)

        EventBus.publish("b")
        EventBus.dispatch()
        assertEquals(1, count)
    }
}
