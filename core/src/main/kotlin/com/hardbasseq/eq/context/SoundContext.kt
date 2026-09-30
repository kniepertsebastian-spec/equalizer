package com.hardbasseq.eq.context

// Where the sound is being played, as far as it can be told from the audio
// route. Only contexts that have their own built-in preset are modeled; every
// other route (headphones, phone speaker, unknown) is simply "no context".
enum class SoundContext {
    CAR,
    BLUETOOTH_SPEAKER,
}

// Android does not say what kind of Bluetooth device an output is: reading the
// real Bluetooth class needs the BLUETOOTH_CONNECT permission this app does not
// otherwise use. The product name is all that is available without it, so this
// is a name heuristic - a *default suggestion* for a route the user has not
// configured, never a guarantee. The user's own preset choice for the route
// always wins (it is stored as that route's device profile).
//
// Matching is on whole words (lowercased, split on anything that is not a
// letter or digit) rather than substrings, so short brand names such as "VW",
// "Seat" or "Kia" cannot match inside an unrelated word.
object SoundContextClassifier {
    private val carWords =
        (
            "car carplay auto carkit uconnect mylink handsfree " +
                "bmw audi mercedes benz vw volkswagen skoda seat cupra opel ford toyota lexus honda " +
                "hyundai kia mazda nissan volvo tesla peugeot citroen renault dacia fiat porsche " +
                "suzuki subaru jeep mmi idrive comand mbux"
        ).split(' ').toSet()

    // "freisprech..." (German hands-free) is matched as a prefix since it is
    // compounded in practice ("Freisprecheinrichtung").
    private val carPrefixes = listOf("freisprech")

    private val speakerWords =
        (
            "speaker lautsprecher box soundbox boombox boom megaboom " +
                "wonderboom partybox soundlink flip xtreme charge soundbar"
        ).split(' ').toSet()

    fun classifyBluetoothName(name: String): SoundContext? {
        val words = name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        return when {
            words.any { it in carWords || carPrefixes.any { prefix -> it.startsWith(prefix) } } -> SoundContext.CAR
            words.any { it in speakerWords } -> SoundContext.BLUETOOTH_SPEAKER
            else -> null
        }
    }
}
