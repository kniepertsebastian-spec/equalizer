package com.hardbasseq.eq.link

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiUrlTest {
    @Test
    fun `appends the client id to a url without query`() {
        assertEquals("https://api/x?client_id=NEW", ApiUrl.withClientId("https://api/x", "NEW"))
    }

    @Test
    fun `appends it after existing parameters`() {
        assertEquals("https://api/x?limit=50&offset=10&client_id=NEW", ApiUrl.withClientId("https://api/x?limit=50&offset=10", "NEW"))
    }

    @Test
    fun `replaces a stale client id wherever it sits`() {
        assertEquals("https://api/x?limit=50&client_id=NEW", ApiUrl.withClientId("https://api/x?limit=50&client_id=OLD", "NEW"))
        assertEquals("https://api/x?limit=50&client_id=NEW", ApiUrl.withClientId("https://api/x?client_id=OLD&limit=50", "NEW"))
        assertEquals("https://api/x?a=1&b=2&client_id=NEW", ApiUrl.withClientId("https://api/x?a=1&client_id=OLD&b=2", "NEW"))
    }
}
