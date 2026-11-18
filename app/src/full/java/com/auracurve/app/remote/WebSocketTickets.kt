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
    private class Entry(
        val expiresMs: Long,
        val owner: String,
    )

    private val tickets = HashMap<String, Entry>()

    /** A ticket for the device [owner] (module 05: its WebSocket closes when the device is removed). */
    @Synchronized
    fun issue(owner: String = ""): String {
        val now = nowMs()
        tickets.entries.removeAll { it.value.expiresMs <= now }
        return newTicket().also { tickets[it] = Entry(now + ttlMs, owner) }
    }

    /** True once for a ticket that was issued and has not expired; the ticket is gone afterwards. */
    fun redeem(ticket: String?): Boolean = redeemOwner(ticket) != null

    /** The owner of a valid ticket, once; null for an unknown, used or expired one. */
    @Synchronized
    fun redeemOwner(ticket: String?): String? {
        if (ticket.isNullOrEmpty()) return null
        val entry = tickets.remove(ticket) ?: return null
        return entry.owner.takeIf { nowMs() < entry.expiresMs }
    }

    companion object {
        const val TTL_MS = 30_000L
        private const val TICKET_BYTES = 18
    }
}
