package com.auracurve.app.preset

import com.auracurve.app.context.SoundContext

// Listening-context tunings: made for where the sound is played rather than for
// a genre (car, small Bluetooth speaker, soundbar, in-ears). Used as an overlay on
// whatever genre preset is active (see MainViewModel's context mode) - and still
// listed as presets so a device profile saved before that can be read. They are
// tuned for punch, but never by wasting headroom on frequencies the hardware
// cannot reproduce: the boosts sit where each device actually plays, and the
// context features (subsonic high-pass, loudness compensation, virtual bass)
// carry the rest.
// As an overlay only the target curve, limiter, mbcThresholdDb/mbcRatio and the
// context features are applied; macroBassDb, macroPunchDb, macroHaerteDb and
// requestedHeadroomDb are unused then (they would override the genre's macros) and
// only matter to profiles that still bind a context preset directly.
// Like the genre presets they are folded into BuiltInPresets.all, so a device
// profile can bind to them by id.
object BuiltInContextPresets {
    // Car cabin: road/wind noise masks 100-500 Hz and the dampened interior eats
    // treble, so presence (2-4 kHz) and air (8 kHz+) get a lift; the typical
    // cabin resonance (~70 Hz) and door-panel boom (~200 Hz) get a small cut.
    val Car =
        Preset(
            id = "context_car",
            name = "Car",
            targetCurve =
                listOf(
                    TargetPoint(50f, 1.0f),
                    TargetPoint(75f, -1.0f),
                    TargetPoint(120f, 1.5f),
                    TargetPoint(200f, -2.0f),
                    TargetPoint(350f, -1.0f),
                    TargetPoint(1000f, 0.0f),
                    TargetPoint(3000f, 2.5f),
                    TargetPoint(8000f, 2.0f),
                    TargetPoint(14000f, 1.5f),
                ),
            macroBassDb = 0.5f,
            macroPunchDb = 1.0f,
            macroHaerteDb = 0.0f,
            requestedHeadroomDb = 3.5f,
            mbcThresholdDb = -8.0f,
            mbcRatio = 3.0f,
            metadata = PresetMetadata(genre = "car", builtIn = true),
            loudnessMaxBoostDb = 9f,
            subsonicCutoffHz = 35f,
            virtualBassMix = 0.4f,
        )

    // Small Bluetooth speaker, tuned for as much punch as the driver can take. It
    // cannot move air below ~70 Hz, so a steep subsonic corner (65 Hz) stops the
    // limiter from pumping on bass it could never play, and the energy goes where
    // the driver still works: a strong 100-150 Hz kick lift, carried further down
    // by heavy synthesized harmonics. The "boxy" 400 Hz region of small enclosures
    // is cut, and the 3-5 kHz kick click is lifted - attack reads as punch.
    val BluetoothSpeaker =
        Preset(
            id = "context_bluetooth_speaker",
            name = "Bluetooth speaker",
            targetCurve =
                listOf(
                    TargetPoint(80f, 2.0f),
                    TargetPoint(110f, 5.0f),
                    TargetPoint(150f, 3.5f),
                    TargetPoint(250f, -1.5f),
                    TargetPoint(400f, -2.5f),
                    TargetPoint(1000f, 0.0f),
                    TargetPoint(2500f, 1.5f),
                    TargetPoint(4000f, 2.0f),
                    TargetPoint(8000f, 1.5f),
                    TargetPoint(12000f, 1.0f),
                ),
            macroBassDb = 0.5f,
            macroPunchDb = 1.5f,
            macroHaerteDb = 0.5f,
            requestedHeadroomDb = 4.0f,
            mbcThresholdDb = -12.0f,
            mbcRatio = 4.0f,
            limiter = LimiterConfig(enabled = true, thresholdDb = -1.0f),
            metadata = PresetMetadata(genre = "bluetooth-speaker", builtIn = true),
            loudnessMaxBoostDb = 9f,
            subsonicCutoffHz = 65f,
            virtualBassMix = 0.85f,
        )

    // Larger soundbar (with or without a sub): enough driver area for a real low
    // end, so this one pushes the 40-80 Hz fundament and kick body. The 250 Hz mud
    // of living rooms is cut so the bass stays tight, presence and air get a lift
    // to keep the kick snapping through. The subsonic corner only removes rumble
    // no soundbar plays; moderate virtual bass helps bars without a subwoofer.
    val Soundbar =
        Preset(
            id = "context_soundbar",
            name = "Soundbar",
            targetCurve =
                listOf(
                    TargetPoint(35f, 2.0f),
                    TargetPoint(50f, 4.0f),
                    TargetPoint(80f, 3.0f),
                    TargetPoint(120f, 1.0f),
                    TargetPoint(250f, -2.0f),
                    TargetPoint(500f, -1.0f),
                    TargetPoint(1000f, 0.0f),
                    TargetPoint(2500f, 1.5f),
                    TargetPoint(5000f, 1.0f),
                    TargetPoint(10000f, 2.0f),
                    TargetPoint(14000f, 1.5f),
                ),
            macroBassDb = 1.0f,
            macroPunchDb = 1.5f,
            macroHaerteDb = 0.5f,
            requestedHeadroomDb = 4.5f,
            mbcThresholdDb = -9.0f,
            mbcRatio = 3.0f,
            limiter = LimiterConfig(enabled = true, thresholdDb = -0.5f),
            metadata = PresetMetadata(genre = "soundbar", builtIn = true),
            loudnessMaxBoostDb = 8f,
            subsonicCutoffHz = 28f,
            virtualBassMix = 0.35f,
        )

    // In-ears: a sealed ear canal reproduces real sub-bass cheaply, so the low end
    // is pushed hard below 60 Hz (Harman-in-ear style) instead of faking it with
    // harmonics. Sibilance around 6.5 kHz - tiring this close to the eardrum - is
    // cut, a touch of air at 12 kHz keeps it open. Stacks with headphone mode's
    // comfort curve (which adds its own bass and presence dip), so the curve stays
    // narrower than the speaker ones.
    val InEar =
        Preset(
            id = "context_in_ear",
            name = "In-ear",
            targetCurve =
                listOf(
                    TargetPoint(25f, 4.0f),
                    TargetPoint(45f, 4.0f),
                    TargetPoint(90f, 2.0f),
                    TargetPoint(180f, 0.0f),
                    TargetPoint(300f, -1.0f),
                    TargetPoint(1000f, 0.0f),
                    TargetPoint(3000f, 0.5f),
                    TargetPoint(6500f, -2.0f),
                    TargetPoint(9000f, -0.5f),
                    TargetPoint(12000f, 1.5f),
                ),
            macroBassDb = 1.5f,
            macroPunchDb = 1.0f,
            macroHaerteDb = -0.5f,
            requestedHeadroomDb = 5.0f,
            mbcThresholdDb = -8.0f,
            mbcRatio = 2.5f,
            limiter = LimiterConfig(enabled = true, thresholdDb = -0.5f),
            metadata = PresetMetadata(genre = "in-ear", builtIn = true),
            loudnessMaxBoostDb = 6f,
            subsonicCutoffHz = 20f,
            virtualBassMix = 0.15f,
        )

    val all = listOf(Car, BluetoothSpeaker, Soundbar, InEar)

    // The tuning a listening-context mode layers on top of the active sound.
    fun defaultFor(context: SoundContext): Preset =
        when (context) {
            SoundContext.CAR -> Car
            SoundContext.BLUETOOTH_SPEAKER -> BluetoothSpeaker
            SoundContext.SOUNDBAR -> Soundbar
            SoundContext.IN_EAR -> InEar
        }

    // Reverse of defaultFor: which context mode a context preset (e.g. one saved as
    // a device profile's preset before context modes became an overlay) stands for.
    fun contextOf(preset: Preset): SoundContext? = SoundContext.entries.firstOrNull { defaultFor(it).id == preset.id }
}
