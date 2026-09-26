package com.hardbasseq.eq.dsp

import com.hardbasseq.eq.preset.TargetPoint

// Feature-Idee aus dem Nothing/Dirac-Opteo-Vergleich (Chat, nicht roadmap-2026.md
// verortet): eine ISO-226/Fletcher-Munson-*inspirierte* Lautstärkekompensation.
// Das Ohr wird bei niedrigem Pegel relativ unempfindlicher für Bässe und Höhen
// (die Equal-Loudness-Konturen flachen dort ab) - eine bei einer bestimmten
// Hörlautstärke abgestimmte EQ-Kurve klingt deshalb bei leiser Wiedergabe dünn
// und bei lauter Wiedergabe basslastig/schrill. Das hier ist keine echte
// ISO-226-Lookup-Tabelle (die bräuchte SPL-Kalibrierung pro Gerät/Kopfhörer,
// die diese App nicht hat), sondern approximiert nur die *Form* dieses
// Effekts: ein Shelf-artiger Bass- und Höhen-Boost, der mit dem Abstand zu
// einer Referenz-Hörlautstärke linear zunimmt und ab REFERENCE_DROP_DB
// gekappt wird.
//
// Ergebnis ist eine ganz normale TargetPoint-Liste - komponiert über
// CurveComposer.combine() genau wie CorrectionProfile- und VoicingPreset-
// Kurven, keine Sonderbehandlung im restlichen DSP-Pfad nötig.
object LoudnessCompensationCurve {
    // Equal-Loudness-Konturen flachen im Bass deutlich stärker ab als in den
    // Höhen - der Bass bekommt deshalb den vollen Boost, die Höhen nur einen
    // Bruchteil davon.
    private const val TREBLE_TO_BASS_RATIO = 0.4f

    // Pegelabstand zur Referenzlautstärke, ab dem die Kompensation ihr
    // Maximum (maxBoostDb) erreicht - darüber hinaus wird geklemmt, nicht
    // weiter extrapoliert.
    private const val REFERENCE_DROP_DB = 40f

    private const val MIN_MAX_BOOST_DB = 0f
    private const val MAX_MAX_BOOST_DB = 15f

    // currentLevelDb/referenceLevelDb sind relative dB-Werte auf derselben
    // Skala wie die Lautstärkequelle des Aufrufers (z. B. System-Media-Volume
    // in dB oder gemessenes Signal-RMS in dBFS) - diese Funktion braucht nur
    // deren *Differenz*, keine absolute SPL-Kalibrierung.
    fun forLevel(
        currentLevelDb: Float,
        referenceLevelDb: Float = 0f,
        maxBoostDb: Float = 9f,
    ): List<TargetPoint> {
        val clampedMaxBoost = maxBoostDb.coerceIn(MIN_MAX_BOOST_DB, MAX_MAX_BOOST_DB)
        val levelDropDb = (referenceLevelDb - currentLevelDb).coerceIn(0f, REFERENCE_DROP_DB)
        if (levelDropDb <= 0f || clampedMaxBoost <= 0f) return emptyList()

        val compensationFraction = levelDropDb / REFERENCE_DROP_DB
        val bassBoostDb = clampedMaxBoost * compensationFraction
        val trebleBoostDb = clampedMaxBoost * TREBLE_TO_BASS_RATIO * compensationFraction

        return listOf(
            TargetPoint(frequencyHz = 20f, gainDb = bassBoostDb),
            TargetPoint(frequencyHz = 60f, gainDb = bassBoostDb),
            TargetPoint(frequencyHz = 200f, gainDb = bassBoostDb * 0.3f),
            TargetPoint(frequencyHz = 1000f, gainDb = 0f),
            TargetPoint(frequencyHz = 8000f, gainDb = trebleBoostDb * 0.5f),
            TargetPoint(frequencyHz = 16000f, gainDb = trebleBoostDb),
            TargetPoint(frequencyHz = 20000f, gainDb = trebleBoostDb),
        )
    }
}
