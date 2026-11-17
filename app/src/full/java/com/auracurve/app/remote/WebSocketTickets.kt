package com.auracurve.app.remote

/**
 * One-time tickets for the WebSocket (Pixel-Car V-06): the token never goes into a URL. A ticket
 * is valid for [ttlMs] and only once. [nowMs] is injectable for tests. Thread-safe.
 */
class WebSocketTickets(
    private val ttlMs: Long = TTL_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val newTicket: () -> String = { RemoteToken.randomUrlSafe(TICKET_BYTES) },
) {
    private val tickets = HashMap<String, Long>()

    @Synchronized
    fun issue(): String {
        val now = nowMs()
        tickets.entries.removeAll { it.value <= now }
        return newTicket().also { tickets[it] = now + ttlMs }
    }

    /** True once for a ticket that was issued and has not expired; the ticket is gone afterwards. */
    @Synchronized
    fun redeem(ticket: String?): Boolean {
        if (ticket.isNullOrEmpty()) return false
        val expires = tickets.remove(ticket) ?: return false
        return nowMs() < expires
    }

    companion object {
        const val TTL_MS = 30_000L
        private const val TICKET_BYTES = 18
    }
}
