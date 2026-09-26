package com.hardbasseq.eq.autoeq

// Feature-Idee aus dem Nothing/Dirac-Opteo-Chat ("Mein Kopfhörer", Punkt 8):
// gleicht einen erkannten Bluetooth-/USB-Gerätenamen (siehe AudioRoute.name in
// :app - AndroidAudioRouteRepository liest dort bereits device.productName aus)
// gegen BuiltInAutoEqCatalog ab, um ein passendes Korrekturprofil vorzuschlagen.
// Bewusst nur ein *Vorschlag*, kein automatischer Import: Kabelgebundene
// Kopfhörer liefern über Android so gut wie nie einen Produktnamen (nur den
// generischen Typ), und selbst bei Bluetooth-Geräten mit echtem Namen ist
// Fuzzy-Matching nie sicher genug für einen stillen Auto-Import - der
// Aufrufer muss die Bestätigung selbst einholen.
object AutoEqCatalogMatcher {
    // Unterhalb dieser Konfidenz wird kein Vorschlag gemacht - ein falscher
    // Vorschlag ist schlimmer als gar keiner.
    private const val MIN_CONFIDENCE = 0.6

    fun findBestMatch(
        deviceName: String,
        catalog: List<AutoEqCatalogEntry> = BuiltInAutoEqCatalog.entries,
    ): AutoEqCatalogEntry? {
        if (deviceName.isBlank()) return null
        val normalizedDeviceName = normalize(deviceName)
        if (normalizedDeviceName.isBlank()) return null

        return catalog
            .map { entry -> entry to bestScoreFor(normalizedDeviceName, entry) }
            .filter { (_, score) -> score >= MIN_CONFIDENCE }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun bestScoreFor(
        normalizedDeviceName: String,
        entry: AutoEqCatalogEntry,
    ): Double {
        val candidates = listOf(entry.displayName) + entry.aliases
        return candidates.maxOf { candidate -> similarity(normalizedDeviceName, normalize(candidate)) }
    }

    private fun normalize(name: String): String = name.lowercase().replace(Regex("[^a-z0-9]"), "")

    // Enthaltensein zuerst - deckt sowohl "das Modell steckt als Teilstring im
    // (oft länger/anders formatierten) Bluetooth-Namen" als auch den
    // umgekehrten Fall ab. Fällt sonst auf Levenshtein-Ähnlichkeit zurück, für
    // knapp daneben liegende Schreibweisen (OEM-Firmware mangelt Namen gerne
    // auf eigene Art ab).
    private fun similarity(
        a: String,
        b: String,
    ): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (a.contains(b) || b.contains(a)) return 0.9

        val distance = levenshtein(a, b)
        val maxLength = maxOf(a.length, b.length)
        return 1.0 - (distance.toDouble() / maxLength)
    }

    private fun levenshtein(
        a: String,
        b: String,
    ): Int {
        val distances = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) distances[i][0] = i
        for (j in 0..b.length) distances[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                distances[i][j] =
                    if (a[i - 1] == b[j - 1]) {
                        distances[i - 1][j - 1]
                    } else {
                        1 + minOf(distances[i - 1][j], distances[i][j - 1], distances[i - 1][j - 1])
                    }
            }
        }
        return distances[a.length][b.length]
    }
}
