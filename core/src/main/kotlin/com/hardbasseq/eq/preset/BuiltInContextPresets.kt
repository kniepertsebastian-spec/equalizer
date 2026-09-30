package com.hardbasseq.eq.preset

import com.hardbasseq.eq.context.SoundContext

// Listening-context tunings: made for where the sound is played rather than for
// a genre. Used as an overlay on whatever genre preset is active (see
// MainViewModel's context mode) - and still listed as presets so a device profile
// saved before that can be read. They are deliberately moderate - the car and small-speaker problems
// they address are mostly about *not* wasting headroom on frequencies the
// hardware cannot reproduce, so they lean on the context features (subsonic
// high-pass, loudness compensation, virtual bass) more than on big EQ boosts.
// Like the genre presets they are folded into BuiltInPresets.all, so a device
// profile can bind to them by id.
object BuiltInContextPresets {
    // Car cabin: road/wind noise masks 100-500 Hz and the dampened interior eats
    // treble, so presence (2-4 kHz) and air (8 kHz+) get a lift; the typical
    // cabin resonance (~70 Hz) and door-panel boom (~200 Hz) get a small cut.
    val Car =
        Preset(
            id = "context_car",
            name = "Auto",
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

    // Small Bluetooth speaker: little driver excursion, so a big bass boost just
    // distorts. A gentle upper-bass lift, a tight limiter and a higher subsonic
    // corner protect the driver; synthesized harmonics carry the perceived bass.
    val BluetoothSpeaker =
        Preset(
            id = "context_bluetooth_speaker",
            name = "Bluetooth-Box",
            targetCurve =
                listOf(
                    TargetPoint(60f, 1.5f),
                    TargetPoint(100f, 2.5f),
                    TargetPoint(200f, -1.0f),
                    TargetPoint(350f, -1.5f),
                    TargetPoint(1000f, 0.0f),
                    TargetPoint(3000f, 1.5f),
                    TargetPoint(7000f, 1.0f),
                    TargetPoint(12000f, 0.5f),
                ),
            macroBassDb = 0.5f,
            macroPunchDb = 0.5f,
            macroHaerteDb = 0.0f,
            requestedHeadroomDb = 4.0f,
            mbcThresholdDb = -10.0f,
            mbcRatio = 3.5f,
            limiter = LimiterConfig(enabled = true, thresholdDb = -1.0f),
            metadata = PresetMetadata(genre = "bluetooth-speaker", builtIn = true),
            loudnessMaxBoostDb = 6f,
            subsonicCutoffHz = 45f,
            virtualBassMix = 0.6f,
        )

    val all = listOf(Car, BluetoothSpeaker)

    // The tuning a listening-context mode layers on top of the active sound.
    fun defaultFor(context: SoundContext): Preset =
        when (context) {
            SoundContext.CAR -> Car
            SoundContext.BLUETOOTH_SPEAKER -> BluetoothSpeaker
        }

    // Reverse of defaultFor: which context mode a context preset (e.g. one saved as
    // a device profile's preset before context modes became an overlay) stands for.
    fun contextOf(preset: Preset): SoundContext? = SoundContext.entries.firstOrNull { defaultFor(it).id == preset.id }
}
