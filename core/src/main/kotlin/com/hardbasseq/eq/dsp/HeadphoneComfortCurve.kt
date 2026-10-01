package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint

// Chat-Nachtrag zu Punkt 1 ("Kopfhörer-Modus" sollte wirklich etwas bewirken,
// nicht nur ein Datenbank-Flag sein): Ein echtes, hörbares Crossfeed/Bass-Mono-
// Summing (Punkte 2/5) ist über den aktuell laufenden Wiedergabepfad
// (AndroidAudioEngine: Androids System-Equalizer/DynamicsProcessing-Effekte,
// an fremde Sessions wie Spotify/SoundCloud angehängt) technisch nicht
// möglich - diese Systemeffekte kennen nur Band-EQ und Kompressor/Limiter/MBC,
// keine eigenen Algorithmen. Diese Kurve ist der pragmatische Ersatz: eine
// feste, einfache EQ-Anpassung, die genau über diesen bereits funktionierenden
// Pfad läuft (MainViewModel.combinedCurve() -> EqualizerInterpolator ->
// AndroidAudioEngine.applyInternal() -> Equalizer.setBandLevel()).
//
// Begründung der Kurve: Kopfhörer sitzen näher am Ohr als Lautsprecher -
// Präsenz/Zischlaute (~3-6 kHz) wirken dort tendenziell ermüdender (keine
// Raumreflexionen/Distanz, die das glätten), und kleine Kopfhörer-Treiber
// profitieren oft von etwas mehr Bass, weil ihnen die "Raumgewinn" genannte
// Verstärkung fehlt, die Lautsprecher durch Wand-/Bodennähe bekommen. Keine
// wissenschaftlich hergeleitete Zielkurve, nur ein sinnvoller fester
// Ausgangston - ähnlich wie viele Kopfhörer-Apps einen "Kopfhörer-Modus"
// als spürbaren Bass-/Präsenz-Tilt statt als 1:1-Nachbau von Crossfeed
// umsetzen.
object HeadphoneComfortCurve {
    const val DEFAULT_BASS_DB = 2.5f

    // Bassanhebung wählbar (Kopfhörer-Power); die Präsenzsenke bleibt fest.
    fun curve(bassDb: Float): List<TargetPoint> =
        listOf(
            TargetPoint(20f, bassDb),
            TargetPoint(60f, bassDb),
            TargetPoint(150f, bassDb * 0.4f),
            TargetPoint(300f, 0f),
            TargetPoint(3000f, 0f),
            TargetPoint(4500f, -1.5f),
            TargetPoint(8000f, -1f),
            TargetPoint(16000f, 0f),
        )

    val curve: List<TargetPoint> = curve(DEFAULT_BASS_DB)
}
