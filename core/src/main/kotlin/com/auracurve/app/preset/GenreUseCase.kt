package com.auracurve.app.preset

import com.auracurve.app.dsp.EqualizerInterpolator
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where or how a genre is played. Each use case reshapes a genre's own "Moderate" tuning instead
 * of replacing it, so "Uptempo – Car" still sounds like Uptempo, only adapted to the cabin. The
 * reshaping is small on purpose (a few dB at most): it moves energy to where the playback system
 * actually works rather than adding boost, so headroom stays where the genre left it.
 *
 * [description] is the "when is this the best choice" text the preset list shows; [playerDescription]
 * the same for the built-in player's speaker-safe variant (roadmap 15, [GenreUseCaseTuning.applyForPlayer]).
 */
enum class GenreUseCase(
    val id: String,
    val label: String,
    val description: String,
    val playerDescription: String,
) {
    CAR(
        "car",
        "Car",
        "Best in the car. The closed cabin already boosts the deep sub by several dB, so the sub " +
            "is pulled back here while kick, vocals and hi-hats get a lift against road noise. Gentle " +
            "loudness compensation keeps it full at low volume. The Car listening mode adds its own " +
            "cabin correction on top: try this one with the mode off first.",
        "Best in the car with the built-in player. The cabin already lifts the deep sub, so nothing " +
            "below 60 Hz is boosted; kick, vocals and hi-hats get a lift against road noise and gentle " +
            "loudness compensation keeps it full at low volume. A 36 Hz filter spares the door speakers.",
    ),
    BLUETOOTH_SPEAKER(
        "bluetooth_speaker",
        "Bluetooth speaker",
        "Best on a small Bluetooth speaker. Bass below 70 Hz that the driver cannot play is dropped " +
            "instead of wasting headroom, the low end moves up to the 100–130 Hz kick range and " +
            "virtual bass fills in the rest (built-in player). A tighter limiter keeps the driver " +
            "from distorting.",
        "Best on a small Bluetooth speaker with the built-in player. Nothing below 75 Hz is boosted, " +
            "a 65 Hz filter keeps the driver from moving on bass it cannot play, the kick moves up to " +
            "100–130 Hz and virtual bass fills in the rest. A tighter limiter guards the membrane.",
    ),
    HEADPHONES(
        "headphones",
        "Headphones",
        "Best on headphones and in-ears. They play deep sub cleanly, so the sub gets a little more, " +
            "while the 3 kHz presence and 6–7 kHz sibilance, tiring this close to the ear, are eased. " +
            "The compressor works looser for more dynamics.",
        "Best on in-ears and headphones with the built-in player. They play deep sub cleanly, so the " +
            "sub gets more and the subsonic filter moves down to 22 Hz; the tiring 3 kHz presence and " +
            "6–7 kHz sibilance are eased and the compressor works looser for more dynamics.",
    ),
    LOUD(
        "loud",
        "Loud",
        "Best at high volume, like a party or open windows. At high level the ear already hears " +
            "more bass and treble, so the bass macros and the harsh 3–5 kHz range are pulled back, " +
            "compressor and limiter work tighter and a subsonic filter keeps woofers from moving on " +
            "rumble nobody hears. Loud in the car: pick this one and switch the Car listening mode on.",
        "Strong bass at high volume with the built-in player. The kick carries the bass instead of the " +
            "deep sub, the harsh 3–5 kHz range comes down, compressor and limiter work tighter and a " +
            "subsonic filter at 42 Hz or above keeps speakers from bottoming out at party level.",
    ),
    QUIET(
        "quiet",
        "Quiet",
        "Best at low volume, like a quiet drive at night or background music. Loudness " +
            "compensation adds bass and air as you turn down and fades out as you turn up, so the " +
            "genre still sounds full without being loud.",
        "Moderate and full at low volume with the built-in player. Loudness compensation adds bass and " +
            "air as you turn down and fades out as you turn up; the compressor works looser, so quiet " +
            "listening keeps its dynamics.",
    ),
    ;

    companion object {
        fun fromId(id: String): GenreUseCase? = entries.find { it.id == id }
    }
}

/** Turns a genre's base tuning into its use-case variants (see [GenreUseCase]). */
object GenreUseCaseTuning {
    // Below this a small speaker driver plays nothing useful (see BuiltInContextPresets.BluetoothSpeaker).
    private const val SPEAKER_LOW_LIMIT_HZ = 75f

    // Below this the car cabin already lifts the sub by several dB; the player variant boosts none of it.
    private const val PLAYER_CAR_SUB_LIMIT_HZ = 60f

    // Curve points closer than this (ratio) to an existing one are not added twice.
    private const val MERGE_RATIO = 1.06f

    fun presetId(
        genre: GenrePreset,
        useCase: GenreUseCase,
    ): String = "${genre.id}_use_${useCase.id}"

    /**
     * The use-case variant of [genre]. Which tuning applies follows the base's source scope: a
     * genre tuned for the built-in player ([PresetSourceScope.OWN_PLAYER]) gets the speaker-safe
     * player equivalents ([applyForPlayer]), every other genre the system-EQ tuning below.
     */
    fun apply(
        genre: GenrePreset,
        useCase: GenreUseCase,
    ): Preset {
        if (genre.base.metadata.sourceScope == PresetSourceScope.OWN_PLAYER) return applyForPlayer(genre, useCase)
        val base = genre.base
        val reshaped =
            reshape(base.targetCurve, adjustment(useCase)).let { curve ->
                if (useCase == GenreUseCase.BLUETOOTH_SPEAKER) curve.capBelow(SPEAKER_LOW_LIMIT_HZ, 0f) else curve
            }
        val tuned = base.copy(id = presetId(genre, useCase), name = "${genre.displayName} – ${useCase.label}", targetCurve = reshaped)
        return when (useCase) {
            // Cabin gain lifts the sub, so the Bass shelf is eased; the compressor holds the
            // level a little steadier against road noise.
            GenreUseCase.CAR ->
                tuned.copy(
                    macroBassDb = base.macroBassDb - 0.5f,
                    mbcThresholdDb = (base.mbcThresholdDb - 1f).coerceIn(-30f, 0f),
                    mbcRatio = (base.mbcRatio + 0.3f).coerceIn(1f, 6f),
                    loudnessMaxBoostDb = 6f,
                    subsonicCutoffHz = 30f,
                )
            // The Bass shelf reaches far below what the driver plays; most of it goes, Punch
            // (70-160 Hz) stays. Tighter dynamics and a lower limiter ceiling protect the membrane.
            GenreUseCase.BLUETOOTH_SPEAKER ->
                tuned.copy(
                    macroBassDb = base.macroBassDb * 0.3f,
                    requestedHeadroomDb = base.requestedHeadroomDb + 0.5f,
                    mbcEnabled = true,
                    mbcThresholdDb = (base.mbcThresholdDb - 2f).coerceIn(-30f, 0f),
                    mbcRatio = (base.mbcRatio + 0.5f).coerceIn(1f, 6f),
                    limiter = LimiterConfig(enabled = true, thresholdDb = -1.0f),
                    loudnessMaxBoostDb = 6f,
                    subsonicCutoffHz = 65f,
                    virtualBassMix = 0.6f,
                )
            // Close to the eardrum extra Härte only tires; looser compression keeps the transients.
            GenreUseCase.HEADPHONES ->
                tuned.copy(
                    macroHaerteDb = minOf(base.macroHaerteDb, 0f),
                    mbcThresholdDb = (base.mbcThresholdDb + 1f).coerceIn(-30f, 0f),
                    mbcRatio = (base.mbcRatio - 0.2f).coerceIn(1f, 6f),
                    subsonicCutoffHz = 20f,
                )
            // Equal-loudness: at high SPL bass and treble already sound stronger, so the macros
            // come down and the dynamics stage works harder to keep the system out of distortion.
            GenreUseCase.LOUD ->
                tuned.copy(
                    macroBassDb = base.macroBassDb - 1.0f,
                    macroPunchDb = base.macroPunchDb - 0.5f,
                    macroHaerteDb = base.macroHaerteDb - 0.5f,
                    requestedHeadroomDb = base.requestedHeadroomDb + 1.0f,
                    mbcEnabled = true,
                    mbcThresholdDb = (base.mbcThresholdDb - 2f).coerceIn(-30f, 0f),
                    mbcRatio = (base.mbcRatio + 0.5f).coerceIn(1f, 6f),
                    limiter = LimiterConfig(enabled = true, thresholdDb = -1.0f),
                    subsonicCutoffHz = 25f,
                )
            // The volume-dependent loudness compensation does the real work; it fades out as the
            // volume goes up, so it never adds boost where it is not needed.
            GenreUseCase.QUIET -> tuned.copy(loudnessMaxBoostDb = 8f)
        }
    }

    /**
     * Roadmap 15: the use-case variant of a genre for the built-in player. The player has no
     * loudness normalization in front of its EQ and drives speakers directly, so every variant
     * keeps virtual bass off (except on a small Bluetooth speaker, where it replaces the bass the
     * driver cannot play), carries a subsonic high-pass and a limiter ceiling of at least -1 dB
     * wherever speakers play it. Headphones and quiet listening move little air, so their limiter
     * may sit a little higher.
     */
    fun applyForPlayer(
        genre: GenrePreset,
        useCase: GenreUseCase,
    ): Preset {
        val base = genre.base
        val reshaped =
            reshape(base.targetCurve, playerAdjustment(useCase)).let { curve ->
                when (useCase) {
                    GenreUseCase.CAR -> curve.capBelow(PLAYER_CAR_SUB_LIMIT_HZ, 0f)
                    GenreUseCase.BLUETOOTH_SPEAKER -> curve.capBelow(SPEAKER_LOW_LIMIT_HZ, 0f)
                    else -> curve
                }
            }
        val tuned =
            base.copy(
                id = presetId(genre, useCase),
                name = "${genre.displayName} – ${useCase.label}",
                targetCurve = reshaped,
                virtualBassMix = 0f,
            )
        return when (useCase) {
            // Kräftiger Bass bei lauter Musik: the kick (95 Hz) carries the bass, the deep sub and
            // the harsh 3-5 kHz come down, and the subsonic filter sits high enough that the cone
            // does not run out of travel at party level.
            GenreUseCase.LOUD ->
                tuned.copy(
                    macroBassDb = base.macroBassDb - 0.3f,
                    macroPunchDb = base.macroPunchDb + 0.2f,
                    macroHaerteDb = base.macroHaerteDb - 0.6f,
                    mbcEnabled = true,
                    mbcThresholdDb = (base.mbcThresholdDb - 2f).coerceIn(-30f, 0f),
                    mbcRatio = (base.mbcRatio + 0.5f).coerceIn(1f, 6f),
                    limiter = LimiterConfig(enabled = true, thresholdDb = -1.2f),
                    loudnessMaxBoostDb = 0f,
                    subsonicCutoffHz = maxOf(base.subsonicCutoffHz, 42f),
                )
            // Moderat bei leiser Musik: loudness compensation (fading out as the volume goes up)
            // keeps the genre full; looser compression, the limiter rarely touched at this level.
            GenreUseCase.QUIET ->
                tuned.copy(
                    mbcThresholdDb = (base.mbcThresholdDb + 1f).coerceIn(-30f, 0f),
                    mbcRatio = (base.mbcRatio - 0.2f).coerceIn(1f, 6f),
                    limiter = LimiterConfig(enabled = true, thresholdDb = -0.8f),
                    loudnessMaxBoostDb = 7.5f,
                    subsonicCutoffHz = 30f,
                )
            // Besser bei In-Ears: they play deep sub cleanly and move no cone that could bottom
            // out, so the sub gets more and the subsonic filter goes down; presence and sibilance
            // are eased, the compressor works looser for more dynamics.
            GenreUseCase.HEADPHONES ->
                tuned.copy(
                    macroBassDb = base.macroBassDb + 0.3f,
                    macroHaerteDb = minOf(base.macroHaerteDb, 0f),
                    mbcThresholdDb = (base.mbcThresholdDb + 1.5f).coerceIn(-30f, 0f),
                    mbcRatio = (base.mbcRatio - 0.3f).coerceIn(1f, 6f),
                    limiter = LimiterConfig(enabled = true, thresholdDb = -0.6f),
                    loudnessMaxBoostDb = 0f,
                    subsonicCutoffHz = 22f,
                )
            // As the system-EQ Car variant, but the cabin gain already lifts the sub by several
            // dB, so the player keeps everything below 60 Hz at or under 0 dB.
            GenreUseCase.CAR ->
                tuned.copy(
                    macroBassDb = base.macroBassDb - 0.3f,
                    mbcEnabled = true,
                    mbcThresholdDb = (base.mbcThresholdDb - 1f).coerceIn(-30f, 0f),
                    mbcRatio = (base.mbcRatio + 0.3f).coerceIn(1f, 6f),
                    limiter = LimiterConfig(enabled = true, thresholdDb = -1.0f),
                    loudnessMaxBoostDb = 6f,
                    subsonicCutoffHz = 36f,
                )
            // Small Bluetooth box: nothing below 75 Hz is boosted, the kick moves to 100-130 Hz and
            // virtual bass fills in what the driver cannot play.
            GenreUseCase.BLUETOOTH_SPEAKER ->
                tuned.copy(
                    macroBassDb = base.macroBassDb * 0.25f,
                    mbcEnabled = true,
                    mbcThresholdDb = (base.mbcThresholdDb - 2f).coerceIn(-30f, 0f),
                    mbcRatio = (base.mbcRatio + 0.5f).coerceIn(1f, 6f),
                    limiter = LimiterConfig(enabled = true, thresholdDb = -1.2f),
                    loudnessMaxBoostDb = 6f,
                    subsonicCutoffHz = 65f,
                    virtualBassMix = 0.45f,
                )
        }
    }

    // Player delta curves (roadmap 15, table B). Neutral anchors (1 kHz, 12 kHz) keep a change in
    // one range from spreading over the whole band, as in the system-EQ deltas below.
    private fun playerAdjustment(useCase: GenreUseCase): List<TargetPoint> =
        when (useCase) {
            GenreUseCase.LOUD ->
                listOf(
                    TargetPoint(35f, -1.5f),
                    TargetPoint(55f, -0.5f),
                    TargetPoint(95f, 0.5f),
                    TargetPoint(260f, -1.0f),
                    TargetPoint(1000f, 0f),
                    TargetPoint(3000f, -1.2f),
                    TargetPoint(4500f, -1.5f),
                    TargetPoint(8000f, -0.5f),
                    TargetPoint(12000f, 0f),
                )
            GenreUseCase.QUIET ->
                listOf(
                    TargetPoint(45f, 0.8f),
                    TargetPoint(80f, 0.5f),
                    TargetPoint(150f, 0f),
                    TargetPoint(6000f, 0f),
                    TargetPoint(12000f, 1.0f),
                )
            GenreUseCase.HEADPHONES ->
                listOf(
                    TargetPoint(30f, 1.2f),
                    TargetPoint(55f, 0.8f),
                    TargetPoint(120f, 0f),
                    TargetPoint(300f, -0.5f),
                    TargetPoint(1000f, 0f),
                    TargetPoint(3000f, -0.8f),
                    TargetPoint(6500f, -1.8f),
                    TargetPoint(9000f, -0.5f),
                    TargetPoint(12000f, 0.8f),
                )
            // The system-EQ car delta; the sub below 60 Hz is capped afterwards (applyForPlayer).
            GenreUseCase.CAR -> adjustment(GenreUseCase.CAR)
            GenreUseCase.BLUETOOTH_SPEAKER ->
                listOf(
                    TargetPoint(100f, 1.2f),
                    TargetPoint(130f, 1.2f),
                    TargetPoint(200f, 0f),
                    TargetPoint(400f, -1.5f),
                    TargetPoint(800f, 0f),
                )
        }

    // No point below [limitHz] boosts more than [maxGainDb].
    private fun List<TargetPoint>.capBelow(
        limitHz: Float,
        maxGainDb: Float,
    ): List<TargetPoint> = map { if (it.frequencyHz < limitHz) it.copy(gainDb = minOf(it.gainDb, maxGainDb)) else it }

    // Delta curve per use case, added on top of the genre's own curve. End points are given
    // explicitly, because interpolation holds the last value flat beyond them.
    private fun adjustment(useCase: GenreUseCase): List<TargetPoint> =
        when (useCase) {
            GenreUseCase.CAR ->
                listOf(
                    TargetPoint(35f, -2.5f),
                    TargetPoint(60f, -1.5f),
                    TargetPoint(90f, 0f),
                    TargetPoint(120f, 0.5f),
                    TargetPoint(200f, -1.0f),
                    TargetPoint(400f, 0f),
                    TargetPoint(2500f, 1.0f),
                    TargetPoint(6000f, 0.5f),
                    TargetPoint(10000f, 1.0f),
                    TargetPoint(14000f, 1.0f),
                )
            GenreUseCase.BLUETOOTH_SPEAKER ->
                listOf(
                    TargetPoint(100f, 1.0f),
                    TargetPoint(130f, 1.5f),
                    TargetPoint(200f, 0f),
                    TargetPoint(400f, -1.5f),
                    TargetPoint(800f, 0f),
                    TargetPoint(2000f, 0f),
                    TargetPoint(3500f, 1.0f),
                    TargetPoint(8000f, 0.5f),
                    TargetPoint(14000f, 0f),
                )
            GenreUseCase.HEADPHONES ->
                listOf(
                    TargetPoint(30f, 1.0f),
                    TargetPoint(60f, 0.5f),
                    TargetPoint(120f, 0f),
                    TargetPoint(300f, -0.5f),
                    TargetPoint(1000f, 0f),
                    TargetPoint(3000f, -1.0f),
                    TargetPoint(6500f, -1.5f),
                    TargetPoint(9000f, -0.5f),
                    TargetPoint(12000f, 0.5f),
                )
            GenreUseCase.LOUD ->
                listOf(
                    TargetPoint(35f, -1.0f),
                    TargetPoint(60f, -0.5f),
                    TargetPoint(120f, 0f),
                    TargetPoint(1000f, 0f),
                    TargetPoint(3000f, -1.0f),
                    TargetPoint(4500f, -1.0f),
                    TargetPoint(8000f, -0.5f),
                    TargetPoint(12000f, 0f),
                )
            GenreUseCase.QUIET ->
                listOf(
                    TargetPoint(40f, 1.0f),
                    TargetPoint(80f, 0.5f),
                    TargetPoint(150f, 0f),
                    TargetPoint(6000f, 0f),
                    TargetPoint(12000f, 1.0f),
                )
        }

    // The genre's own points are kept exactly; the adjustment's points are added where the genre
    // has none nearby, so the result still has the genre's shape plus the use-case changes.
    private fun reshape(
        baseCurve: List<TargetPoint>,
        delta: List<TargetPoint>,
    ): List<TargetPoint> {
        val base = baseCurve.sortedBy { it.frequencyHz }
        val frequencies = base.map { it.frequencyHz }.toMutableList()
        delta.forEach { point ->
            val close = frequencies.any { maxOf(it, point.frequencyHz) / minOf(it, point.frequencyHz) < MERGE_RATIO }
            if (!close) frequencies += point.frequencyHz
        }
        return frequencies.sorted().map { hz ->
            val gain =
                EqualizerInterpolator.interpolateFrequency(hz, base) +
                    EqualizerInterpolator.interpolateFrequency(hz, delta)
            TargetPoint(hz, roundTenth(gain))
        }
    }

    private fun roundTenth(value: Float): Float {
        val rounded = (value * 10f).roundToInt() / 10f
        return if (abs(rounded) < 0.05f) 0f else rounded
    }
}
