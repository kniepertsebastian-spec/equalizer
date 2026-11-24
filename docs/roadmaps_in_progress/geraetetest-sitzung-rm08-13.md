# Gerätetest-Sitzung Roadmaps 08–13

Alles, was in den Roadmaps 08 bis 13 nur am Gerät nachweisbar ist, in einer Reihenfolge, die möglichst
wenig Umbauten braucht. Ergebnisse direkt hier abhaken und Auffälligkeiten mit Zeitpunkt/Track notieren;
danach in die jeweilige Roadmap übertragen.

**Vorbereitung**
- Zwei Builds: `fullDebug` (Abschnitte A–E) und signierte R8-Release-APKs `storeRelease` + `fullRelease` (Abschnitt F).
- Kopfhörer, kleine Bluetooth-Box, wenn möglich Auto (Bluetooth oder Android Auto).
- Spotify installiert, angemeldet; SoundCloud-Konto (Go+ nur für den `aac_256k`-Punkt).
- `adb logcat -s AuraCurveStream PlayerSessionCommands PlayerController` in einem Terminal mitlaufen lassen.
- Testmaterial: ein basslastiger Track (Hardstyle/DnB/Uptempo), ein 808-Hip-Hop-Track, ein akustischer Track
  mit Kontrabass, ein Track mit leisem Intro.

---

## A. Eigener Player, Kopfhörer

| # | Prüfung | Roadmap | Erwartung | ✓ |
|---|---|---|---|---|
| A1 | Gleicher Track in Spotify und im eigenen Player, Preset „Knall“ | 08, 09 | etwa gleich laut, eigener Player nicht leiser | ☐ |
| A2 | Manuellen EQ-Slider im eigenen Player bewegen | 08 | sofort hörbar | ☐ |
| A3 | A/B-Vergleich (EQ ↔ Original) im eigenen Player | 08 | beide Seiten etwa gleich laut | ☐ |
| A4 | Geschwindigkeit ≠ 1, Seek, Titelwechsel | 09 | kein Knacken, kein Aussetzer | ☐ |
| A5 | Parametrisches Band anlegen (z. B. 3 kHz, +6 dB, Q 6) | 10 | im eigenen Player schmal und exakt hörbar, nicht breit verschmiert | ☐ |
| A6 | Virtual Bass an, Charakter „Warm / Balanced / Punchy“ umschalten | 10 | hörbarer Charakterwechsel, gleiche Lautheit, kein Klick beim Umschalten | ☐ |
| A7 | Virtual-Bass-Hörtests H1–H6 (`docs/DSP_VIRTUAL_BASS.md` §7) | 10 | keine Verzerrung in Stimmen, sauberes Gate im leisen Intro | ☐ |
| A8 | Hörprobe Bass-Track bei +10 bis +12 dB Bass | 13 | Vocals pumpen nicht, Hi-Hats sauber, kein Pre-Echo vor Kicks | ☐ |

## B. Eigener Player, kleine BT-Box (Preset „Bluetooth-Box“)

| # | Prüfung | Roadmap | Erwartung | ✓ |
|---|---|---|---|---|
| B1 | Virtual Bass an, Track mit 40–50-Hz-Kicks | 10 (Seitenkette) | Bass deutlich präsenter als vor RM 10, aber kein Dröhnen; ggf. `mix` nachstimmen | ☐ |
| B2 | Laut aufdrehen | 09, 13 | kein hörbares Pumpen des End-Limiters | ☐ |

## C. Bildschirm gesperrt, Akku/CPU

| # | Prüfung | Roadmap | Erwartung | ✓ |
|---|---|---|---|---|
| C1 | 10 Minuten Wiedergabe mit gesperrtem Bildschirm, volle AutoEQ-Korrektur + Virtual Bass | 09, 10 | keine Aussetzer | ☐ |
| C2 | CPU-Last dabei (`adb shell top -H -p $(adb shell pidof com.auracurve.app)` oder Android-Studio-Profiler) | 10 | Audio-Thread wenige Prozent eines Kerns | ☐ |

## D. SoundCloud

| # | Prüfung | Roadmap | Erwartung | ✓ |
|---|---|---|---|---|
| D1 | Track starten, Log `AuraCurveStream` lesen | 10 | `chose hls aac_160k (AAC ~160 kbit/s)` bzw. mit Go+ `aac_256k`; Liste der angebotenen Varianten notieren | ☐ |
| D2 | Gapless-Übergang zwischen zwei Tracks | 10 | ohne Lücke | ☐ |
| D3 | Seek in einem AAC-HLS-Track | 10 | springt sauber | ☐ |
| D4 | Pause > 30 Minuten, dann weiter | 10 | spielt weiter (URL wird erneuert) | ☐ |

## E. Steuerung bei geschlossener App

App vollständig schließen (aus den Letzten Apps wischen), Musik lief vorher im eigenen Player.

| # | Prüfung | Roadmap | Erwartung | ✓ |
|---|---|---|---|---|
| E1 | Quick-Tile: Master aus | 11 | Player stoppt, kein Absturz | ☐ |
| E2 | Widget: Play/Pause | 11 | funktioniert | ☐ |
| E3 | Bluetooth-Taste am Kopfhörer: Play/Pause | 11 | funktioniert | ☐ |
| E4 | Fernbedienung (Web-Remote): Play/Pause, Weiter, Zurück | 11 (Nachtrag) | funktioniert, kein „Player command … dropped“ im Log | ☐ |
| E5 | StrictMode-Lauf beim Start des Dienstes | 11 | keine Disk-Reads auf dem Main Thread | ☐ |

## F. Release-APKs (signiert, R8)

| # | Prüfung | Roadmap | Erwartung | ✓ |
|---|---|---|---|---|
| F1 | `storeRelease` + `fullRelease`: Start, Presets speichern, JSON-Import/-Export | 12 | kein Absturz | ☐ |
| F2 | Player + Spotify-Attach | 12 | funktioniert | ☐ |
| F3 | `fullRelease`: Remote-Server starten, SoundCloud-Login | 12 | funktioniert | ☐ |
| F4 | TalkBack + 200 % Schrift auf EQ- und Player-Screen | 12 | nichts abgeschnitten, alles vorlesbar | ☐ |

## G. Diagnose (System-EQ mit Spotify)

| # | Prüfung | Roadmap | Erwartung | ✓ |
|---|---|---|---|---|
| G1 | Diagnose-Bericht öffnen | 13 | `MBC Bands: 5`, falls unterstützt `Post-EQ: 34-band` | ☐ |
