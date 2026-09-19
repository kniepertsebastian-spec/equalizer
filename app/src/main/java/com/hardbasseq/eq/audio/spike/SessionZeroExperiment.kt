package com.hardbasseq.eq.audio.spike

import android.media.audiofx.Equalizer

data class SessionZeroProbeResult(
    val attachSucceeded: Boolean,
    val detail: String,
)

/**
 * Purely experimental probe for roadmap §2's open question on session `0`
 * (the global output mix): does this device even allow *constructing* an
 * [Equalizer] bound to it? The effect is never enabled here – only
 * attached and immediately released – so this can never audibly affect
 * device-wide playback, regardless of the result.
 *
 * A successful attach here is **not** a supported feature and must never
 * be advertised as one. Session-`0` insert effects are officially
 * deprecated (roadmap §2); this only documents observed behavior on this
 * specific device/OS build for engineering reference, per the explicit
 * roadmap instruction to treat it "ausschließlich als Experiment", never
 * as a guarantee.
 */
object SessionZeroExperiment {
    fun probe(): SessionZeroProbeResult {
        var equalizer: Equalizer? = null
        return try {
            equalizer = Equalizer(0, 0)
            SessionZeroProbeResult(
                attachSucceeded = true,
                detail =
                    "Construction succeeded, ${equalizer.numberOfBands} bands reported. " +
                        "Never enabled; released immediately.",
            )
        } catch (e: Exception) {
            SessionZeroProbeResult(attachSucceeded = false, detail = e.message ?: e.toString())
        } finally {
            equalizer?.release()
        }
    }
}
