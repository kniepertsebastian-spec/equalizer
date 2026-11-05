package com.hardbasseq.eq.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class SessionIdleShutdownTest {
    private val timeout = 10.minutes

    @Test
    fun `closes after the timeout when there is no session and the app is closed`() =
        runTest {
            val events = mutableListOf<Unit>()
            val job =
                launch {
                    idleShutdown(MutableStateFlow(false), MutableStateFlow(false), timeout).toList(events)
                }

            advanceTimeBy(timeout - 1.minutes)
            runCurrent()
            assertEquals(0, events.size)

            advanceTimeBy(2.minutes)
            runCurrent()
            assertEquals(1, events.size)
            job.join()
        }

    @Test
    fun `never closes while a session is open`() =
        runTest {
            val events = mutableListOf<Unit>()
            val job =
                launch {
                    idleShutdown(MutableStateFlow(true), MutableStateFlow(false), timeout).toList(events)
                }

            advanceTimeBy(timeout * 5)
            runCurrent()

            assertEquals(0, events.size)
            job.cancel()
        }

    @Test
    fun `never closes while the app is on screen`() =
        runTest {
            val events = mutableListOf<Unit>()
            val job =
                launch {
                    idleShutdown(MutableStateFlow(false), MutableStateFlow(true), timeout).toList(events)
                }

            advanceTimeBy(timeout * 5)
            runCurrent()

            assertEquals(0, events.size)
            job.cancel()
        }

    @Test
    fun `a session that opens in time restarts the wait from zero`() =
        runTest {
            val hasSession = MutableStateFlow(false)
            val events = mutableListOf<Unit>()
            val job =
                launch {
                    idleShutdown(hasSession, MutableStateFlow(false), timeout).toList(events)
                }

            advanceTimeBy(timeout - 1.minutes)
            hasSession.value = true
            runCurrent()
            advanceTimeBy(timeout * 3)
            runCurrent()
            assertEquals("a session was open the whole time", 0, events.size)

            hasSession.value = false
            runCurrent()
            advanceTimeBy(timeout - 1.minutes)
            runCurrent()
            assertEquals("the wait starts over once the session is gone", 0, events.size)

            advanceTimeBy(2.minutes)
            runCurrent()
            assertEquals(1, events.size)
            job.join()
        }

    @Test
    fun `opening the app while idle restarts the wait`() =
        runTest {
            val visible = MutableStateFlow(false)
            val events = mutableListOf<Unit>()
            val job =
                launch {
                    idleShutdown(MutableStateFlow(false), visible, timeout).toList(events)
                }

            advanceTimeBy(timeout - 1.minutes)
            visible.value = true
            runCurrent()
            advanceTimeBy(timeout * 2)
            runCurrent()
            assertEquals(0, events.size)

            visible.value = false
            runCurrent()
            advanceTimeBy(timeout - 1.minutes)
            runCurrent()
            assertEquals(0, events.size)

            advanceTimeBy(2.minutes)
            runCurrent()
            assertEquals(1, events.size)
            job.join()
        }

    @Test
    fun `the flow ends after the first signal`() =
        runTest {
            val events = idleShutdown(MutableStateFlow(false), MutableStateFlow(false), timeout).toList()

            assertEquals(1, events.size)
        }
}
