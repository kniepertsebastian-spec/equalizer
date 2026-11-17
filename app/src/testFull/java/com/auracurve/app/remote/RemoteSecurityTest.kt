package com.auracurve.app.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class RemoteSecurityTest {
    @Test
    fun `private addresses are rfc 1918 and ipv6 unique local or link local`() {
        listOf(
            "10.0.0.1",
            "172.16.0.1",
            "172.31.255.254",
            "192.168.43.1",
            "fd00::1",
            "fc12:3456::1",
            "fe80::1",
            "fe80::1%wlan0",
            "[fe80::2]",
            "::ffff:192.168.1.2",
        ).forEach { assertTrue(it, PrivateNetwork.isPrivate(it)) }
    }

    @Test
    fun `public loopback and non literal addresses are refused`() {
        listOf(
            "8.8.8.8",
            "172.15.0.1",
            "172.32.0.1",
            "192.169.0.1",
            "100.64.0.1",
            "127.0.0.1",
            "0.0.0.0",
            "::1",
            "2001:db8::1",
            "::ffff:8.8.8.8",
            "localhost",
            "unknown",
            "",
            "example.com",
            "192.168.1",
            "999.1.1.1",
        ).forEach { assertFalse(it, PrivateNetwork.isPrivate(it)) }
    }

    @Test
    fun `a connection needs both ends private`() {
        assertTrue(PrivateNetwork.allows("192.168.43.1", "192.168.43.20"))
        assertFalse(PrivateNetwork.allows("192.168.43.1", "8.8.8.8"))
        assertFalse(PrivateNetwork.allows("100.70.1.2", "192.168.43.20"))
    }

    @Test
    fun `tokens are 32 random bytes in base64url and compare exactly`() {
        val a = RemoteToken.generate()
        val b = RemoteToken.generate()
        assertEquals(43, a.length)
        assertTrue(a.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertNotEquals(a, b)
        assertTrue(RemoteToken.matches(a, a))
        assertFalse(RemoteToken.matches(a, b))
        assertFalse(RemoteToken.matches(a, a.dropLast(1)))
        assertFalse(RemoteToken.matches(null, a))
        assertFalse(RemoteToken.matches("", ""))
        assertFalse(RemoteToken.matches(a, null))
    }

    @Test
    fun `the bearer token is read from the authorization header`() {
        assertEquals("abc", RemoteToken.fromAuthorizationHeader("Bearer abc"))
        assertEquals("abc", RemoteToken.fromAuthorizationHeader("bearer abc"))
        assertNull(RemoteToken.fromAuthorizationHeader("Basic abc"))
        assertNull(RemoteToken.fromAuthorizationHeader("Bearer "))
        assertNull(RemoteToken.fromAuthorizationHeader(null))
    }

    @Test
    fun `ten failures within a minute block the ip for a minute`() {
        var now = 0L
        val limiter = AuthRateLimiter(nowMs = { now })
        repeat(9) { limiter.recordFailure("192.168.43.20") }
        assertFalse(limiter.isBlocked("192.168.43.20"))
        limiter.recordFailure("192.168.43.20")
        assertTrue(limiter.isBlocked("192.168.43.20"))
        assertFalse("other ips are not affected", limiter.isBlocked("192.168.43.21"))
        now = 59_999L
        assertTrue(limiter.isBlocked("192.168.43.20"))
        now = 60_000L
        assertFalse(limiter.isBlocked("192.168.43.20"))
    }

    @Test
    fun `failures older than a minute do not count`() {
        var now = 0L
        val limiter = AuthRateLimiter(nowMs = { now })
        repeat(9) { limiter.recordFailure("10.0.0.2") }
        now = 60_000L
        limiter.recordFailure("10.0.0.2")
        assertFalse(limiter.isBlocked("10.0.0.2"))
    }

    @Test
    fun `a ticket works once and only for 30 seconds`() {
        var now = 0L
        val tickets = WebSocketTickets(nowMs = { now })
        val first = tickets.issue()
        assertTrue(tickets.redeem(first))
        assertFalse("only once", tickets.redeem(first))

        val second = tickets.issue()
        now = 29_999L
        val third = tickets.issue()
        assertTrue(tickets.redeem(second))
        now = 29_999L + 30_000L
        assertFalse("expired", tickets.redeem(third))
        assertFalse(tickets.redeem(null))
        assertFalse(tickets.redeem("made-up"))
    }

    @Test
    fun `thermal statuses map to the contract names`() {
        assertEquals("NONE", SystemStatus.thermalName(0))
        assertEquals("MODERATE", SystemStatus.thermalName(2))
        assertEquals("SHUTDOWN", SystemStatus.thermalName(6))
        assertEquals("NONE", SystemStatus.thermalName(42))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `throttling passes the first value and then at most the latest one per period`() =
        runTest {
            val source =
                flow {
                    emit(1)
                    delay(1_000)
                    emit(2)
                    delay(1_000)
                    emit(3)
                    delay(10_000)
                    emit(4)
                }
            assertEquals(listOf(1, 3, 4), source.throttleLatest(5.seconds).toList())
        }
}
