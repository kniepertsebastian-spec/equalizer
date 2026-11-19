package com.auracurve.app.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fixed address via DuckDNS: input checks, one update, and the loop that follows address and settings.
@OptIn(ExperimentalCoroutinesApi::class)
class DynDnsTest {
    private val token = "a7c4d0ad-114e-40ef-ba1d-d217904a50f2"
    private val config = DynDnsConfig("basti-auto", token)

    @Test
    fun `names are cleaned, anything else is refused`() {
        assertEquals("basti-auto", DuckDns.normalizeName(" Basti-Auto "))
        assertEquals("basti-auto", DuckDns.normalizeName("basti-auto.duckdns.org"))
        assertEquals("basti-auto", DuckDns.normalizeName("http://basti-auto.duckdns.org:8080/#/eq"))
        listOf("", "-x", "x-", "a b", "a.b", "ä", "x".repeat(64)).forEach { bad ->
            assertNull(bad, DuckDns.normalizeName(bad))
        }
        assertEquals("basti-auto.duckdns.org", config.host)
    }

    @Test
    fun `the token must look like the one duckdns shows, and stays out of toString`() {
        assertEquals(token, DuckDns.normalizeToken(" ${token.uppercase()} "))
        assertNull(DuckDns.normalizeToken("abc"))
        assertNull(DuckDns.normalizeToken(token + "0"))
        assertFalse(config.toString().contains(token))
    }

    @Test
    fun `one update sends the private address and reports the answer`() =
        runTest {
            val urls = mutableListOf<String>()
            var answer = "OK"
            val fetch: suspend (String) -> String = { url ->
                urls += url
                answer
            }
            val updater = DynDnsUpdater(fetch, { 42L })
            assertTrue(updater.update(config, "10.40.5.7"))
            assertEquals("https://www.duckdns.org/update?domains=basti-auto&token=$token&ip=10.40.5.7", urls.single())
            assertEquals(DynDnsStatus("10.40.5.7", 42L, failed = false), updater.status.value)

            answer = "KO"
            assertFalse(updater.update(config, "10.40.5.8"))
            assertEquals(DynDnsStatus("10.40.5.7", 42L, failed = true), updater.status.value)

            val broken = DynDnsUpdater({ error("no internet") }, { 0L })
            assertFalse(broken.update(config, "10.40.5.7"))
            assertTrue(broken.status.value.failed)
        }

    @Test
    fun `follows address and settings, retries without internet, refreshes hourly`() =
        runTest {
            val ips = mutableListOf<String>()
            var online = true
            val fetch: suspend (String) -> String = { url ->
                ips += url.substringAfter("&ip=")
                if (online) "OK" else error("offline")
            }
            val updater = DynDnsUpdater(fetch, { testScheduler.currentTime })
            val configs = MutableStateFlow<DynDnsConfig?>(config)
            val addresses = MutableStateFlow<String?>("10.40.5.7")
            backgroundScope.launch { updater.keepUpdated(configs, addresses) }
            runCurrent()
            assertEquals(listOf("10.40.5.7"), ips)

            // New hotspot address: written at once.
            addresses.value = "192.168.77.3"
            runCurrent()
            assertEquals(listOf("10.40.5.7", "192.168.77.3"), ips)

            // No network: nothing to write, nothing sent.
            addresses.value = null
            runCurrent()
            assertEquals(2, ips.size)

            // Offline: retries after 15 s, then 30 s.
            online = false
            addresses.value = "172.20.10.4"
            runCurrent()
            assertEquals(3, ips.size)
            assertTrue(updater.status.value.failed)
            advanceTimeBy(DynDnsUpdater.RETRY_FIRST_MS + 1)
            assertEquals(4, ips.size)
            online = true
            advanceTimeBy(2 * DynDnsUpdater.RETRY_FIRST_MS + 1)
            assertEquals(5, ips.size)
            assertEquals("172.20.10.4", updater.status.value.ip)
            assertFalse(updater.status.value.failed)

            // Hourly refresh.
            advanceTimeBy(DynDnsUpdater.REFRESH_MS + 1)
            assertEquals(6, ips.size)

            // Removed in the settings: no more updates, status cleared.
            configs.value = null
            runCurrent()
            assertEquals(DynDnsStatus(), updater.status.value)
            advanceTimeBy(DynDnsUpdater.REFRESH_MS * 2)
            assertEquals(6, ips.size)
        }
}
