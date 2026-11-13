# Roadmap 06 – Neue Funktionen und Wartbarkeit

**Modell-Empfehlung:** Sonnet 5.5 für die Umsetzung; Opus 5.5 für Architekturprüfung bei größeren Refactorings.  
**Ziel:** Die ausgewählten neuen Funktionen und die technische Absicherung umsetzen.  
**Abhängigkeit:** Nach den grundlegenden Player- und Audioarbeiten; Phase 3 erst nach Planfreigabe umsetzen.

## Phase 5 – Neue Funktionen

| ID | Funktion | Aufwand |
|---|---|---|
| P5-1 | Schnelleinstellungs-Kachel und Widget: EQ an/aus, Preset wechseln | S |
| P5-2 | Mehr fremde Apps erkennen: optionale Freigabe per Shizuku/ADB (`DUMP`), wie es Wavelet anbietet, für Apps ohne Session-Broadcast | M |
| P5-3 | Parametrischer Modus: Frequenz, Güte und Verstärkung je Band frei wählbar | M |
| P5-4 | Presets teilen per Link oder QR-Code; Import von AutoEq- und Wavelet-Dateien | S–M |
| P5-5 | Profile je App (Spotify anders als YouTube), zusätzlich zu den Profilen je Gerät | M |
| P5-6 | Sleep-Timer und Überblenden zwischen Titeln | S–M |
| P5-7 | Lokale Dateien als zweite Quelle im eigenen Player: volle DSP-Kette, unabhängig von SoundCloud | M |
| P5-8 | Bluetooth-Codec und Abtastrate im Diagnosebericht anzeigen | S |

---

## Phase 6 – Wartbarkeit und Absicherung

- **P6-1 Große Dateien teilen (M):** `MainViewModel.kt` (1.448 Zeilen), `EqualizerScreen.kt` (1.451), `PlayerViewModel.kt` (834). Kurvenberechnung, Geräteprofile und Vergleichsmodus in eigene Klassen.
- **P6-2 `:player` angleichen (M):** Versionen stehen fest im Modul statt im Versionskatalog, Lint ist abgeschaltet (`abortOnError = false`), kein Hilt, Zustand in globalen Objekten (`PlayerDspState`, `NowPlayingState`, `PlayerCurveState`).
- **P6-3 Gerätetests (M):** Es gibt 72 Testdateien, aber keinen einzigen Test auf einem Gerät. Mindestens: Service-Start aus dem Hintergrund, Attach an eine Session, Room-Migration.
- **P6-4 Absturzgrund über Neustarts merken (S, alt: MED-04):** `ActivityManager.getHistoricalProcessExitReasons()` in den Diagnosebericht.
- **P6-5 Roadmaps zusammenführen (S):** Fünf weitere Roadmap-Dateien mit zusammen rund 4.200 Zeilen (`docs/roadmap*.md`, `roadmap-kontext-dsp.md`). Erledigtes ins Archiv, eine gültige Datei.
- **P6-6 R8 für Release (M):** Macht keinen Klangunterschied. Bringt ein kleineres APK und schnellere Schleifen im Audio-Thread; braucht Keep-Regeln und einen Gerätetest.
- **P6-7 Statusangaben prüfen (S):** Ein Punkt gilt erst als erledigt, wenn der Code auf `main` es zeigt (siehe P0-1).

---

## Abschlusskriterien
- Jede Funktion und jedes Refactoring ist separat getestet und dokumentiert.
- Große Dateien werden schrittweise zerlegt, ohne Verhalten unbeabsichtigt zu ändern.
- Modulkonfigurationen, Dependency-Versionen, Hilt-/State-Architektur und Lint-Einstellungen sind konsistent.
- Gerätetests decken mindestens Service-Start aus dem Hintergrund, Attach an eine Audio-Session und Room-Migration ab.
- Absturzdiagnose, Roadmap-Bereinigung und R8-Release-Konfiguration sind geprüft.
- Statusangaben entsprechen dem tatsächlich auf `main` vorhandenen Code.
- Build, Unit-Tests, Lint und erforderliche Gerätetests sind erfolgreich bzw. offene Fehler sind ausdrücklich dokumentiert.
