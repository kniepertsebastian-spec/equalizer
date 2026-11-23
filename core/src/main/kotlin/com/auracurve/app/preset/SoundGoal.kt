package com.auracurve.app.preset

// Klangziele: Auswahl nach gewünschtem Ergebnis statt nach Genre. Ein Ziel ist eine kleine, additive
// Korrekturkurve über dem gewählten Genre-Preset (wie die Kopfhörer-Kurve), also unabhängig vom Genre.
// "Gesang vorne" ist bewusst subtil: etwas mehr Präsenz (2-4 kHz) und weniger Maskierung durch den
// Bereich um 300 Hz - es isoliert keinen Gesang aus dem Mix und repariert keine schlechte Aufnahme.
// Die sichtbaren Namen und Beschreibungen stehen in den App-Ressourcen (Deutsch/Englisch).
enum class SoundGoal(
    val id: String,
    val curve: List<TargetPoint>,
) {
    BALANCED("goal_balanced", emptyList()),
    VOCALS_FORWARD(
        "goal_vocals_forward",
        listOf(
            TargetPoint(100f, 0f),
            TargetPoint(250f, -0.8f),
            TargetPoint(400f, -1.5f),
            TargetPoint(700f, -0.5f),
            TargetPoint(1200f, 0.5f),
            TargetPoint(2500f, 1.8f),
            TargetPoint(3500f, 2.0f),
            TargetPoint(5000f, 0.8f),
            TargetPoint(8000f, 0f),
        ),
    ),
    MORE_POWER(
        "goal_more_power",
        listOf(
            TargetPoint(40f, 2.5f),
            TargetPoint(70f, 3.0f),
            TargetPoint(120f, 2.0f),
            TargetPoint(220f, 0.5f),
            TargetPoint(400f, 0f),
        ),
    ),
    MORE_PUNCH(
        "goal_more_punch",
        listOf(
            TargetPoint(60f, 0.5f),
            TargetPoint(100f, 2.5f),
            TargetPoint(160f, 1.0f),
            TargetPoint(300f, 0f),
            TargetPoint(2500f, 0f),
            TargetPoint(4000f, 1.5f),
            TargetPoint(7000f, 0f),
        ),
    ),
    LESS_HARSH(
        "goal_less_harsh",
        listOf(
            TargetPoint(1500f, 0f),
            TargetPoint(3000f, -1.5f),
            TargetPoint(5000f, -2.5f),
            TargetPoint(8000f, -2.0f),
            TargetPoint(12000f, -1.0f),
        ),
    ),

    // Roadmap 13, P13-4: for very strong bass (+8 to +15 dB) - the vocals stay free. A mud dip at
    // 200-300 Hz, a little less sibilance around 4.5 kHz and a gentle air lift from 10 kHz up, which
    // the post-EQ (PostEqSplit) keeps behind the compressor so it is not flattened again.
    CLEAR_BASS(
        "goal_clear_bass",
        listOf(
            TargetPoint(130f, 0f),
            TargetPoint(200f, -1.5f),
            TargetPoint(260f, -3.0f),
            TargetPoint(330f, -1.5f),
            TargetPoint(450f, 0f),
            TargetPoint(3200f, 0f),
            TargetPoint(4500f, -1.0f),
            TargetPoint(6500f, 0f),
            TargetPoint(10000f, 1.0f),
            TargetPoint(13000f, 2.0f),
            TargetPoint(16000f, 2.0f),
        ),
    ),

    LESS_BASS(
        "goal_less_bass",
        listOf(
            TargetPoint(40f, -3.5f),
            TargetPoint(80f, -3.0f),
            TargetPoint(150f, -2.0f),
            TargetPoint(300f, -0.5f),
            TargetPoint(600f, 0f),
        ),
    ),
    ;

    companion object {
        // Unknown or missing ids (older saved settings) mean "no goal".
        fun fromId(id: String?): SoundGoal = entries.firstOrNull { it.id == id } ?: BALANCED
    }
}
