package com.hardbasseq.eq.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class PlaybackFailurePolicyTest {
    // --- classification ---

    @Test
    fun offlineDevice_isANetworkFailure_whateverTheError() {
        assertEquals(PlaybackFailureKind.NETWORK, PlaybackFailureClassifier.classify(online = false))
        assertEquals(
            PlaybackFailureKind.NETWORK,
            PlaybackFailureClassifier.classify(online = false, cause = IOException("HTTP 404")),
        )
    }

    @Test
    fun playerNetworkErrorCode_isANetworkFailure() {
        assertEquals(
            PlaybackFailureKind.NETWORK,
            PlaybackFailureClassifier.classify(online = true, networkErrorCode = true),
        )
    }

    @Test
    fun connectionExceptionsAnywhereInTheCauseChain_areNetworkFailures() {
        listOf(UnknownHostException("host"), SocketTimeoutException("slow"), ConnectException("refused")).forEach { cause ->
            assertEquals(
                cause.javaClass.simpleName,
                PlaybackFailureKind.NETWORK,
                PlaybackFailureClassifier.classify(online = true, cause = RuntimeException("wrapped", IOException("io", cause))),
            )
        }
    }

    @Test
    fun otherErrorsWhileOnline_areUnplayable() {
        assertEquals(PlaybackFailureKind.UNPLAYABLE, PlaybackFailureClassifier.classify(online = true))
        // A plain IOException is what the SoundCloud client throws for an HTTP error status.
        assertEquals(
            PlaybackFailureKind.UNPLAYABLE,
            PlaybackFailureClassifier.classify(online = true, cause = IOException("HTTP 404")),
        )
    }

    @Test
    fun cyclicCauseChain_doesNotHang() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)

        assertEquals(PlaybackFailureKind.UNPLAYABLE, PlaybackFailureClassifier.classify(online = true, cause = a))
    }

    // --- unplayable tracks: at most three skips in a row ---

    @Test
    fun unplayableTracks_areSkippedThreeTimesThenTheFourthStops() {
        val policy = PlaybackFailurePolicy()

        repeat(3) { assertEquals(PlaybackFailureAction.SkipToNext, policy.onFailure(PlaybackFailureKind.UNPLAYABLE)) }
        assertEquals(PlaybackFailureAction.GiveUp, policy.onFailure(PlaybackFailureKind.UNPLAYABLE))
        assertEquals(PlaybackFailureAction.GiveUp, policy.onFailure(PlaybackFailureKind.UNPLAYABLE))
    }

    @Test
    fun aTrackThatPlays_resetsTheSkipCount() {
        val policy = PlaybackFailurePolicy()
        repeat(3) { policy.onFailure(PlaybackFailureKind.UNPLAYABLE) }

        policy.onPlaybackStarted()

        assertEquals(PlaybackFailureAction.SkipToNext, policy.onFailure(PlaybackFailureKind.UNPLAYABLE))
    }

    @Test
    fun userAction_resetsTheSkipCount() {
        val policy = PlaybackFailurePolicy()
        repeat(4) { policy.onFailure(PlaybackFailureKind.UNPLAYABLE) }

        policy.reset()

        assertEquals(PlaybackFailureAction.SkipToNext, policy.onFailure(PlaybackFailureKind.UNPLAYABLE))
    }

    // --- network failures: wait and retry, never skip ---

    @Test
    fun networkFailures_retryTheSameTrackWithGrowingDelay_andNeverSkip() {
        val policy = PlaybackFailurePolicy(baseDelayMs = 2_000L, maxDelayMs = 30_000L, maxNetworkRetries = 8)

        val actions = (1..8).map { policy.onFailure(PlaybackFailureKind.NETWORK) }

        assertTrue(actions.all { it is PlaybackFailureAction.RetryLater })
        val delays = actions.map { (it as PlaybackFailureAction.RetryLater).delayMs }
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L, 30_000L), delays)
        assertEquals((1..8).toList(), actions.map { (it as PlaybackFailureAction.RetryLater).attempt })
    }

    @Test
    fun networkFailures_giveUpAfterTheRetryLimit_insteadOfSkippingTheQueue() {
        val policy = PlaybackFailurePolicy(maxNetworkRetries = 2)

        policy.onFailure(PlaybackFailureKind.NETWORK)
        policy.onFailure(PlaybackFailureKind.NETWORK)

        assertEquals(PlaybackFailureAction.GiveUp, policy.onFailure(PlaybackFailureKind.NETWORK))
    }

    @Test
    fun noConnectionForTheWholeQueue_neverSkipsAnything() {
        val policy = PlaybackFailurePolicy()

        val actions = (1..20).map { policy.onFailure(PlaybackFailureKind.NETWORK) }

        assertTrue(actions.none { it == PlaybackFailureAction.SkipToNext })
    }

    @Test
    fun recovery_resetsTheRetryCountAndDelay() {
        val policy = PlaybackFailurePolicy()
        repeat(3) { policy.onFailure(PlaybackFailureKind.NETWORK) }

        policy.onPlaybackStarted()

        assertEquals(
            PlaybackFailureAction.RetryLater(delayMs = PlaybackFailurePolicy.DEFAULT_BASE_DELAY_MS, attempt = 1),
            policy.onFailure(PlaybackFailureKind.NETWORK),
        )
    }

    @Test
    fun networkAndUnplayableCounters_areIndependent() {
        val policy = PlaybackFailurePolicy()
        repeat(2) { policy.onFailure(PlaybackFailureKind.UNPLAYABLE) }

        // A dropped connection in between does not use up (or refill) the skip budget.
        policy.onFailure(PlaybackFailureKind.NETWORK)

        assertEquals(PlaybackFailureAction.SkipToNext, policy.onFailure(PlaybackFailureKind.UNPLAYABLE))
        assertEquals(PlaybackFailureAction.GiveUp, policy.onFailure(PlaybackFailureKind.UNPLAYABLE))
    }
}
