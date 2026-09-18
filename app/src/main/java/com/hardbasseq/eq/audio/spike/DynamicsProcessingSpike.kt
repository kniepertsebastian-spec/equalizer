package com.hardbasseq.eq.audio.spike

import android.media.audiofx.DynamicsProcessing

enum class DynamicsProcessingStage {
    INPUT_GAIN,
    PRE_EQ,
    MBC,
    LIMITER,
}

data class DynamicsProcessingStageResult(
    val stage: DynamicsProcessingStage,
    val succeeded: Boolean,
    val detail: String,
)

object DynamicsProcessingSpike {

    /**
     * Probes each `DynamicsProcessing` stage in isolation on [audioSessionId]:
     * one throwaway engine per stage, with every other stage left disabled
     * in its [DynamicsProcessing.Config]. This is roadmap M0's "Input-Gain,
     * EQ, MBC sowie Limiter einzeln testen" — it only tells us whether this
     * device's engine accepts each stage at all, not how it sounds.
     *
     * Any exception (unsupported parameter, lost control, dead engine, ...)
     * is caught and reported per-stage rather than crashing the app or
     * aborting the remaining probes — per roadmap §7, these are states to
     * model, not crashes.
     */
    fun probeStages(audioSessionId: Int): List<DynamicsProcessingStageResult> =
        DynamicsProcessingStage.entries.map { stage -> probeStage(audioSessionId, stage) }

    private fun probeStage(
        audioSessionId: Int,
        stage: DynamicsProcessingStage,
    ): DynamicsProcessingStageResult {
        var dp: DynamicsProcessing? = null
        return try {
            val config = buildConfig(stage)
            dp = DynamicsProcessing(0, audioSessionId, config).also { it.enabled = true }
            val detail = describe(dp, stage)
            DynamicsProcessingStageResult(stage, succeeded = true, detail = detail)
        } catch (e: Exception) {
            DynamicsProcessingStageResult(stage, succeeded = false, detail = e.message ?: e.toString())
        } finally {
            dp?.release()
        }
    }

    private fun describe(dp: DynamicsProcessing, stage: DynamicsProcessingStage): String =
        when (stage) {
            DynamicsProcessingStage.INPUT_GAIN -> {
                dp.setInputGainAllChannelsTo(0f)
                "inputGain readback=${dp.getInputGainByChannelIndex(0)} dB"
            }
            DynamicsProcessingStage.PRE_EQ -> {
                val eq = dp.getPreEqByChannelIndex(0)
                "preEq bandCount=${eq.bandCount}"
            }
            DynamicsProcessingStage.MBC -> {
                val mbc = dp.getMbcByChannelIndex(0)
                "mbc bandCount=${mbc.bandCount}"
            }
            DynamicsProcessingStage.LIMITER -> {
                val limiter = dp.getLimiterByChannelIndex(0)
                "limiter enabled=${limiter.isEnabled}"
            }
        }

    private fun buildConfig(stage: DynamicsProcessingStage): DynamicsProcessing.Config {
        val preEqInUse = stage == DynamicsProcessingStage.PRE_EQ
        val mbcInUse = stage == DynamicsProcessingStage.MBC
        val limiterInUse = stage == DynamicsProcessingStage.LIMITER
        return DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            /* channelCount = */ 2,
            preEqInUse,
            if (preEqInUse) 1 else 0,
            mbcInUse,
            if (mbcInUse) 1 else 0,
            /* postEqInUse = */ false,
            /* postEqBandCount = */ 0,
            limiterInUse,
        ).build()
    }
}
