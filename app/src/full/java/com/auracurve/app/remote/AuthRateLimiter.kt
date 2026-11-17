package com.auracurve.app.remote

/**
 * Brakes token guessing in the hotspot (Pixel-Car V-01): after [maxFailures] wrong or missing
 * tokens from one IP within [windowMs], that IP gets `429 RATE_LIMITED` for [blockMs], whatever
 * it sends. [nowMs] is injectable for tests. Thread-safe.
 */
class AuthRateLimiter(
    private val maxFailures: Int = 10,
    private val windowMs: Long = 60_000L,
    private val blockMs: Long = 60_000L,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val failures = HashMap<String, ArrayDeque<Long>>()
    private val blockedUntil = HashMap<String, Long>()

    @Synchronized
    fun isBlocked(ip: String): Boolean {
        val until = blockedUntil[ip] ?: return false
        if (nowMs() < until) return true
        blockedUntil.remove(ip)
        return false
    }

    @Synchronized
    fun recordFailure(ip: String) {
        val now = nowMs()
        val recent = failures.getOrPut(ip) { ArrayDeque() }
        recent.addLast(now)
        while (recent.isNotEmpty() && now - recent.first() >= windowMs) recent.removeFirst()
        if (recent.size >= maxFailures) {
            blockedUntil[ip] = now + blockMs
            failures.remove(ip)
        }
        // Keep the table small: drop IPs that have not failed for a whole window.
        if (failures.size > MAX_TRACKED_IPS) failures.entries.removeAll { now - it.value.last() >= windowMs }
    }

    private companion object {
        const val MAX_TRACKED_IPS = 256
    }
}
