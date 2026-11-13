package com.hardbasseq.eq.ui.main

import com.hardbasseq.eq.audio.AudioCapabilities
import com.hardbasseq.eq.audio.EqualizerBandCapabilities
import com.hardbasseq.eq.audio.ProcessingSettings
import com.hardbasseq.eq.context.SoundContext
import com.hardbasseq.eq.dsp.CurveComposer
import com.hardbasseq.eq.dsp.EqualizerInterpolator
import com.hardbasseq.eq.dsp.HeadphoneComfortCurve
import com.hardbasseq.eq.dsp.HeadphonePower
import com.hardbasseq.eq.dsp.LoudnessCompensationCurve
import com.hardbasseq.eq.dsp.LoudnessMatch
import com.hardbasseq.eq.dsp.PreEqMacros
import com.hardbasseq.eq.dsp.PreEqMapper
import com.hardbasseq.eq.dsp.PreEqResponseMode
import com.hardbasseq.eq.dsp.SubsonicFilterCurve
import com.hardbasseq.eq.dsp.VirtualBassApproxCurve
import com.hardbasseq.eq.dsp.VolumeLevelMapper
import com.hardbasseq.eq.preset.BuiltInContextPresets
import com.hardbasseq.eq.preset.TargetPoint
import kotlin.math.abs

// Smaller than this, a classic band counts as sitting on the curve (no manual edit).
private const val MANUAL_DELTA_EPSILON_DB = 0.05f

/**
 * Everything that goes into the target curve, as plain values. Taken out of `MainViewModel`
 * (roadmap 06, P6-1) so the curve arithmetic can be tested without a view model, a repository or
 * a coroutine.
 */
data class CurveInputs(
    // M3 "Mein Kopfhörer": the correction curve of the active profile.
    val correctionCurve: List<TargetPoint>,
    // The active preset ("Klangstil").
    val presetCurve: List<TargetPoint>,
    // Whether headphone acoustics apply (comfort curve and loudness compensation ride on it).
    val headphoneAcoustics: Boolean,
    val headphonePower: HeadphonePower,
    val currentLevelDb: Float,
    val goalCurve: List<TargetPoint>,
    val context: SoundContext?,
    // Virtual bass runs for real in the own player only; for other apps it is approximated.
    val ownPlayerSession: Boolean,
    val preEqAvailable: Boolean,
    val settings: ProcessingSettings,
    // P5-3: the parametric bands, already evaluated (ParametricCurve.bandsToCurve).
    val parametricCurve: List<TargetPoint> = emptyList(),
)

object CurveCalculator {
    /**
     * M3 "Zielkurven kombinieren": correction and voicing are combined into one curve before
     * anything else - band mapping, headroom - happens.
     *
     * HeadphoneComfortCurve and the loudness compensation only join in while
     * [CurveInputs.headphoneAcoustics] is true: that is what makes the headphone mode audible
     * through the system Equalizer path, where true crossfeed cannot run.
     */
    fun combinedCurve(
        inputs: CurveInputs,
        includeSubsonic: Boolean = true,
    ): List<TargetPoint> {
        val power = inputs.headphonePower
        val headphoneCurve = if (inputs.headphoneAcoustics) HeadphoneComfortCurve.curve(power.bassDb) else emptyList()
        val loudnessCurve =
            if (inputs.headphoneAcoustics) {
                LoudnessCompensationCurve.forLevel(currentLevelDb = inputs.currentLevelDb, maxBoostDb = power.loudnessMaxDb)
            } else {
                emptyList()
            }
        return CurveComposer.combine(
            listOf(
                inputs.correctionCurve,
                inputs.presetCurve,
                headphoneCurve,
                loudnessCurve,
                inputs.goalCurve,
                inputs.parametricCurve,
                inputs.context?.let { BuiltInContextPresets.defaultFor(it).targetCurve }.orEmpty(),
                virtualBassApproxCurve(inputs),
                contextCurve(inputs, includeSubsonic),
            ),
        )
    }

    // Virtual Bass cannot run on other apps' audio; with the high-resolution pre-EQ an EQ
    // approximation of it is possible. Never for the own player, which has the real thing.
    private fun virtualBassApproxCurve(inputs: CurveInputs): List<TargetPoint> =
        if (!inputs.ownPlayerSession && inputs.preEqAvailable) {
            VirtualBassApproxCurve.forMix(inputs.settings.virtualBassMix)
        } else {
            emptyList()
        }

    // Subsonic high-pass + the active sound's own volume-dependent loudness compensation. Headphone
    // mode already has its own loudness curve, so the preset's one is skipped there rather than
    // stacking two boosts. Empty while both are off.
    private fun contextCurve(
        inputs: CurveInputs,
        includeSubsonic: Boolean,
    ): List<TargetPoint> {
        val settings = inputs.settings
        val presetLoudness =
            if (inputs.headphoneAcoustics) {
                emptyList()
            } else {
                VolumeLevelMapper.compensationCurve(inputs.currentLevelDb, settings.loudnessMaxBoostDb)
            }
        return CurveComposer.combine(
            listOf(
                if (includeSubsonic) SubsonicFilterCurve.forCutoff(settings.subsonicCutoffHz) else emptyList(),
                presetLoudness,
            ),
        )
    }

    /**
     * Gains for the 34-band pre-EQ (empty while the classic equalizer is in use). The classic band
     * sliders stay the editing surface: how far each one sits from the curve is carried over as a
     * manual delta that fades out towards its neighbours.
     */
    fun preEqGains(
        inputs: CurveInputs,
        capabilities: AudioCapabilities,
        classicBandGainsDb: Map<Int, Float>,
    ): List<Float> {
        if (capabilities.preEqCutoffsHz.isEmpty()) return emptyList()
        val settings = inputs.settings
        val macros = PreEqMacros(settings.macroBassDb, settings.macroPunchDb, settings.macroHaerteDb)
        val curve = combinedCurve(inputs)
        val fromCurve =
            EqualizerInterpolator.interpolateCurveToBands(
                curve,
                capabilities.bands,
                settings.macroBassDb,
                settings.macroPunchDb,
                settings.macroHaerteDb,
            )
        val deltas =
            capabilities.bands.mapNotNull { band ->
                val delta = (classicBandGainsDb[band.index] ?: 0f) - (fromCurve[band.index] ?: 0f)
                if (abs(delta) > MANUAL_DELTA_EPSILON_DB) TargetPoint(band.centerFreqHz.toFloat(), delta) else null
            }
        val responseMode =
            try {
                PreEqResponseMode.valueOf(settings.preEqResponseMode)
            } catch (e: IllegalArgumentException) {
                PreEqResponseMode.DEFAULT
            }
        return PreEqMapper.map(curve, macros, capabilities.preEqCutoffsHz, deltas, responseMode)
    }

    /**
     * The Original/EQ comparison: how much louder (+) or quieter (-) the EQ side is estimated to be,
     * from the band gains and the input gain. Used to match the loudness of the two sides.
     */
    fun eqLevelDeltaDb(
        settings: ProcessingSettings,
        bands: List<EqualizerBandCapabilities>,
    ): Float =
        LoudnessMatch.eqLevelDeltaDb(
            bands.map { it.centerFreqHz.toFloat() to (settings.bandGainsDb[it.index] ?: 0f) },
            settings.inputGainDb,
        )
}
