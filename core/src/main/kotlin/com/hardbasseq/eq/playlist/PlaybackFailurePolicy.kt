package com.hardbasseq.eq.playlist

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// What to do when a track fails to play, free of Android/ExoPlayer so the rules are unit-testable.
//
// Before, every player error went straight to "skip to the next track". Without a network every
// track fails, so the whole queue was skipped, one toast after the other. The two causes need
// different answers:
// - NETWORK: the track may be fine, the connection is not. Pause and try the same track again
//   after a growing wait; do not touch the queue.
// - UNPLAYABLE: this track cannot be played (no stream, removed, expired link). Move on, but
//   only a few times in a row - if several tracks fail one after the other, something else is
//   wrong and the player should stop instead of eating the queue.
enum class PlaybackFailureKind { NETWORK, UNPLAYABLE }

sealed interface PlaybackFailureAction {
    /** Try the same track again after [delayMs]; [attempt] counts from 1. */
    data class RetryLater(
        val delayMs: Long,
        val attempt: Int,
    ) : PlaybackFailureAction

    /** Skip to the next track of the queue. */
    data object SkipToNext : PlaybackFailureAction

    /** Stop trying: stay paused on the current track until the user starts it again. */
    data object GiveUp : PlaybackFailureAction
}

object PlaybackFailureClassifier {
    /**
     * @param online whether the device reports a connection at all
     * @param networkErrorCode the player's own error code says "connection failed / timed out"
     * @param cause the exception behind the failure, if there is one; its chain is searched for
     *   typical connection failures (unknown host, timeout, refused, no route, reset)
     */
    fun classify(
        online: Boolean,
        networkErrorCode: Boolean = false,
        cause: Throwable? = null,
    ): PlaybackFailureKind =
        if (!online || networkErrorCode || hasConnectionCause(cause)) {
            PlaybackFailureKind.NETWORK
        } else {
            PlaybackFailureKind.UNPLAYABLE
        }

    private fun hasConnectionCause(cause: Throwable?): Boolean {
        var current = cause
        var depth = 0
        // The depth limit guards against cyclic cause chains.
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current.isConnectionFailure()) return true
            current = current.cause
            depth++
        }
        return false
    }

    private fun Throwable.isConnectionFailure(): Boolean =
        this is UnknownHostException ||
            this is SocketTimeoutException ||
            this is ConnectException ||
            this is NoRouteToHostException ||
            this is SocketException

    private const val MAX_CAUSE_DEPTH = 8
}

/**
 * Keeps the counters between failures. [onPlaybackStarted] (a track really plays) and [reset]
 * (the user picked something) clear them.
 */
class PlaybackFailurePolicy(
    private val maxConsecutiveSkips: Int = DEFAULT_MAX_CONSECUTIVE_SKIPS,
    private val maxNetworkRetries: Int = DEFAULT_MAX_NETWORK_RETRIES,
    private val baseDelayMs: Long = DEFAULT_BASE_DELAY_MS,
    private val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
) {
    private var consecutiveSkips = 0
    private var networkRetries = 0

    fun onFailure(kind: PlaybackFailureKind): PlaybackFailureAction =
        when (kind) {
            PlaybackFailureKind.NETWORK -> {
                if (networkRetries >= maxNetworkRetries) {
                    PlaybackFailureAction.GiveUp
                } else {
                    networkRetries++
                    PlaybackFailureAction.RetryLater(delayForAttempt(networkRetries), networkRetries)
                }
            }

            PlaybackFailureKind.UNPLAYABLE -> {
                if (consecutiveSkips >= maxConsecutiveSkips) {
                    PlaybackFailureAction.GiveUp
                } else {
                    consecutiveSkips++
                    PlaybackFailureAction.SkipToNext
                }
            }
        }

    /** A track is really playing: earlier failures no longer count. */
    fun onPlaybackStarted() = reset()

    fun reset() {
        consecutiveSkips = 0
        networkRetries = 0
    }

    // base, 2 * base, 4 * base ... capped at maxDelayMs.
    private fun delayForAttempt(attempt: Int): Long {
        var delay = baseDelayMs
        repeat(attempt - 1) {
            delay = (delay * 2).coerceAtMost(maxDelayMs)
        }
        return delay.coerceAtMost(maxDelayMs)
    }

    companion object {
        /** "At most three in a row": the fourth consecutive unplayable track stops instead. */
        const val DEFAULT_MAX_CONSECUTIVE_SKIPS = 3
        const val DEFAULT_MAX_NETWORK_RETRIES = 8
        const val DEFAULT_BASE_DELAY_MS = 2_000L
        const val DEFAULT_MAX_DELAY_MS = 30_000L
    }
}
