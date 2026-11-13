# Roadmap 03 – Audioqualität: Stream-Auswahl und Virtual Bass

**Modell-Empfehlung:** Sonnet 5.5 für P4-1; Opus 5.5 für P4-3  
**Ziel:** Die beiden ausdrücklich ausgewählten Audioqualitäts-Punkte bearbeiten.  
**Abhängigkeit:** Roadmap 01 abgeschlossen, insbesondere P0-5.

## P4-1 Bessere Quelle wählen: AAC statt MP3 128 (S–M) — größter Einzelhebel

### P4-1 Bessere Quelle wählen: AAC statt MP3 128 (S–M) — größter Einzelhebel
- **Befund:** `StreamSelection` bevorzugt „progressive" vor HLS und wertet weder Codec noch Qualitätsstufe aus. Progressive ist bei SoundCloud MP3 mit 128 kbit/s; über HLS gibt es AAC 160 und mit Go+ AAC 256. Kein Equalizer holt zurück, was der Codec entfernt hat, und ein Bass-Boost macht MP3-Artefakte im Hochton eher hörbar.
- **Zusätzliches Risiko:** SoundCloud hat angekündigt, die MP3-Progressive-Streams zugunsten von AAC-HLS abzuschalten. Aktuellen Stand prüfen.
- **Fertig, wenn:** Die Auswahl nach Qualität sortiert (AAC 256, AAC 160, MP3 128) und HLS am Gerät geprüft ist.

**Stand P4-1 (Umsetzung, Gerätetest offen):**
- [x] Auswahl nach Qualität: `StreamSelection.pick` sortiert Vollversion vor Vorschau, dann Codec/Bitrate (AAC 256 > AAC 160 > MP3 128 > niedrige AAC-Stufe), bei Gleichstand progressive vor HLS. Codec und Bitrate kommen aus `preset` (`aac_256k`, `aac_160k`, `mp3_1_0`, …) und `format.mime_type` der Transcodings; fehlen beide, gilt „progressive = MP3" wie bisher.
- [x] Opus wird nie gewählt (kommt als Ogg-Segmente in HLS, die ExoPlayers HLS-Quelle nicht abspielt).
- [x] `SoundCloudClient.resolveStream` reicht `preset` und `mime_type` an die Auswahl weiter; die HLS-Weiche aus P2-1 (`isHls` → M3U8-Quelle) bleibt unverändert und gilt damit auch für AAC-HLS.
- [x] Unit-Tests: `StreamSelectionTest` (11 Fälle: Rangfolge, Reihenfolge der Liste egal, AAC 96 vs. MP3, Opus, Mime-Erkennung ohne Preset, Rückfall ohne Codec-Angaben, Vorschau-Regeln).
- [ ] **Offen: HLS/AAC am Gerät.** Hier nicht prüfbar (kein Gerät, kein SoundCloud-Zugriff). Zu prüfen: (1) Titel spielt über AAC-HLS ohne Fehler und lückenlos in die nächste Queue-Position; (2) Log/Debug zeigt, dass wirklich `aac_160k` bzw. mit Go+ `aac_256k` gewählt wurde; (3) Titel, die nur MP3 anbieten, spielen weiter; (4) Seek und Wiederaufnahme nach langer Pause (abgelaufener Link) mit HLS.
- [ ] **Offen: tatsächlich verfügbare Stream-Varianten.** Preset-Namen und Mime-Typen sind aus dem Verhalten der v2-API abgeleitet, nicht gegen eine aktuelle Antwort geprüft. Die offizielle Ankündigung nennt für die öffentliche API AAC-HLS (`hls_aac_160_url`, Rückfall `hls_aac_96_url`) und die Abschaltung von Progressive-MP3, HLS-MP3 und HLS-Opus zum Jahreswechsel 2025 (Quellen nennen auch den 15.11.2025). Der heutige Stand der internen v2-API (die diese App nutzt) ist nicht belegt. Mit einer echten `transcodings`-Antwort gegenprüfen und die Namen in `StreamSelection` bei Abweichung anpassen.

## P4-3 Virtual Bass neu aufbauen (M)

Nach P0-5: Kennlinie mit einstellbarem Verhältnis von zweiter und dritter Harmonischer statt reiner Gleichrichtung, Bandpass für die Obertöne, Hüllkurvenfolger, damit der Effekt bei leisen Stellen nicht überproportional wird.

**DSP-Konzept:** [`docs/DSP_VIRTUAL_BASS.md`](../DSP_VIRTUAL_BASS.md)

- [x] Kennlinie mit einstellbarem Verhältnis 2./3. Harmonische: Tschebyschow-Polynome T2/T3 auf der Phase des analytischen Signals (`HilbertAllpassPair`), `thirdHarmonicShare` 0..1, leistungserhaltend (`BassExciterTest.shareZero_*`, `shareOne_*`, `halfShare_*`, `defaultShare_*`, `share_keepsTheTotalHarmonicPower`)
- [x] Keine höheren Harmonischen: 4.–6. mehr als 50 dB unter der 2. (vorher 4. nur 13 dB darunter) (`noHarmonicsAboveTheThird`)
- [x] Bandpass für die Obertöne: 65 Hz bis 3 · Cutoff; Isolation jetzt 24 dB/Oktave (`tonesAboveTheCutoff_generateLittle`, `outOfBandTones_passUnchanged`)
- [x] Hüllkurvenfolger: Oberton-Verhältnis zwischen 0 und −40 dBFS konstant (±0,3 dB; vorher +8,5 dB bei leisen Stellen), Gate unter −54 … −66 dBFS (`harmonicRatio_isIndependentOfTheInputLevel`, `gate_fadesHarmonicsOutBelowTheThreshold`, `harmonics_followAToneBurst`)
- [x] Mathematische Tests für Frequenzgang (`secondHarmonicLevel_isPinnedAcrossTheBassRange`, `harmonicLevel_isFlatFrom40To120Hz`, `differentSampleRates_*`), Pegel (`totalHarmonicLevel_at100HzMatchesTheP05Reference`), Differenzton (`twoTones_produceNoDifferenceTone`) und Hilbert-Paar (`HilbertAllpassPairTest`)
- [ ] Hörprobe mit Testsignalen H1–H5 und realem Material H6 (`docs/DSP_VIRTUAL_BASS.md` Abschnitt 7, `docs/TEST_MATRIX.md` K26/K27)
- [ ] CPU/Akku am Gerät (K28)

## Verbindliche Prüfungen
- Für P4-1 Codec- und Qualitätsauswahl anhand tatsächlich verfügbarer Stream-Varianten prüfen; HLS am Gerät testen. Keine Annahme über die aktuelle SoundCloud-Streamverfügbarkeit ungeprüft übernehmen.
- Für P4-3 mathematische Tests für Frequenzgang, Pegel, zweite und dritte Harmonische sowie Verhalten bei unterschiedlichen Eingangspegeln ergänzen.
- Hörprobe mit definierten Testsignalen und realem Audiomaterial durchführen und dokumentieren. Unit-Tests allein belegen keine Klangqualität.
- Build, relevante Tests und Lint ausführen.

## Umfangsgrenze
Diese Roadmap enthält nur P4-1 und P4-3. Die übrigen Punkte der ursprünglichen Phase 4 (P4-2 und P4-4 bis P4-9) sind bewusst zurückgestellt und in `00-INDEX.md` aufgeführt.

---

## Abschlussbericht P4-3 (P4-1 noch offen)

**Branch:** `claude/beautiful-euler-t0res0`

### Geänderte Dateien
- **`:core`:** `dsp/BassExciter.kt` (neu aufgebaut), neu `dsp/HilbertAllpassPair.kt`; Tests `BassExciterTest` (neu geschrieben, 22 Tests), neu `HilbertAllpassPairTest` (5 Tests)
- **Doku:** neu `docs/DSP_VIRTUAL_BASS.md`, `docs/DECISIONS.md`, `docs/TEST_MATRIX.md` (K26–K28), diese Roadmap
- `PlayerDspPcm16`, `:player` und `:app` sind unverändert: Die Schnittstelle (`BassExciterSettings(enabled, mix)`) bleibt gleich.

### Entscheidungen
- Obertöne über das analytische Signal (Hilbert-Allpass-Paar) statt Spitzenwert-Hüllkurve: exakte Normierung ohne Restwelligkeit, keine Differenztöne. Begründung und verworfene Alternativen: `docs/DSP_VIRTUAL_BASS.md` Abschnitt 3.2.
- Der Hüllkurvenfolger steuert nur ein Gate (−66 … −54 dBFS); das Oberton-Verhältnis ist darüber pegelunabhängig.
- `drive` entfällt, neu `thirdHarmonicShare` (Standard 0,25). Keine UI-Änderung.
- Lautheit auf die P0-5-Version kalibriert (`OUTPUT_TRIM = 0,35`), damit Presets nicht lauter werden.

### Tests
- **Lokal:** `:core:test` 329 Tests, 0 Fehler; `:core:ktlintCheck` grün. Gebaut mit einer nur-`:core`-Settings-Datei, weil das Android-Gradle-Plugin in dieser Umgebung nicht geladen werden kann (`dl.google.com` gesperrt). `:app`/`:player` sind nicht berührt; Nachweis für Build und Lint der Android-Module ist die CI.
- Die Werte der alten Version im Konzept stammen aus einem Python-Nachbau, der die in P0-5 festgeschriebenen Pegel (−9,9 / −8,9 / −9,7 dB) exakt reproduziert.

### Offene Punkte
1. Hörprobe H1–H6 am Gerät (K26, K27). Stellschrauben danach: `thirdHarmonicShare`, Gate-Schwellen, `OUTPUT_TRIM`.
2. CPU/Akku am Gerät (K28); auf der JVM rund 62 ns pro Sample und Kanal.
3. Ob `thirdHarmonicShare`/`cutoffHz` in der Oberfläche einstellbar werden sollen, ist eine eigene UI-Entscheidung.
