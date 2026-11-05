# HardBass EQ

In der App und auf dem Startbildschirm heißt sie „Baschtls Gedröhne“. Der Projektname, die Paket-ID (`com.hardbasseq.eq`), die Datei- und Modulnamen und die Spotify-Weiterleitung (`hardbasseq://spotify-callback`) bleiben HardBass EQ, damit bestehende Installationen, Updates und Spotify-Einstellungen weiter passen.

Ein Equalizer für Android, gebaut für satten, kontrollierten Bass: Eine Frequenzkurve zum Anfassen, Presets für Auto, Box und Kopfhörer und ein eingebauter SoundCloud-Player mit eigener Klangverarbeitung.

Die App wird privat gebaut und über GitHub-Releases verteilt. Der Play Store ist nicht geplant.

## Was drin ist

- **Equalizer:** Der Frequenzgang ist die Hauptansicht. Punkte lassen sich ziehen, Presets, Klangziele und Geräteprofile (inklusive AutoEQ-Korrekturen) setzen die Kurve.
- **Systemweiter EQ für andere Apps:** Spotify und andere Player melden ihre Audio-Session. HardBass EQ hängt Equalizer und DynamicsProcessing daran. Welche Apps das tun, hängt von der App und vom Telefon ab.
- **Eigener Player:** Spielt SoundCloud (inoffizieller Zugang) mit Kurven-EQ, Subsonic-Filter, Virtual Bass und Limiter direkt im Audiopfad. Läuft auch im Auto über Android Auto.
- **Kontext-Modi:** Auto, Box und Kopfhörer werden pro Ausgang erkannt.
- **Dienst im Hintergrund:** Der Dienst, der auf neue Audio-Sessions wartet, beendet sich nach 10 Minuten ohne Session von selbst. Öffnet man die App, startet er wieder.

## Module

| Modul | Inhalt |
|---|---|
| `app` | Android-App (Kotlin, Jetpack Compose, Hilt, Room): Oberfläche, Audio-Engine, Dienste |
| `core` | Reine Kotlin-Logik ohne Android: Presets, Kurvenmathe, DSP-Bausteine, Link-Auflösung |
| `player` | Eingebauter Player (media3/ExoPlayer, SoundCloud, Android Auto) als Bibliothek in der App |

## Bauen und testen

Benötigt JDK 21 und ein Android SDK (Pfad in `local.properties`).

```bash
./gradlew :app:assembleDebug     # Debug-APK nach app/build/outputs/apk/debug/
./gradlew testDebugUnitTest :core:test
./gradlew ktlintCheck lintDebug
```

Ein signierter Release entsteht in GitHub Actions, wenn ein Release mit einem Tag wie `v0.2.1` veröffentlicht wird (`.github/workflows/release.yml`).

## Dokumentation

Alles liegt in `docs/`:

| Datei | Inhalt |
|---|---|
| `docs/roadmap-ux.md` | Aktueller Arbeitsplan für Oberfläche und Produkt. Der nächste Release wird gebaut, wenn alle Punkte erledigt sind. |
| `docs/roadmap-2026.md` | Produkt- und Technik-Roadmap 2026 |
| `docs/DECISIONS.md` | Entscheidungen mit Begründung, zuletzt die Antworten auf die offene Fragenliste |
| `docs/ARCHITECTURE.md`, `docs/STATE_MACHINE.md` | Aufbau und Zustände der Audio-Engine und des Dienstes |
| `docs/TEST_MATRIX.md` | Geräte- und Testprotokoll, inklusive offener Gerätetests |
| `docs/DEPENDENCIES.md`, `docs/adr/` | Bibliotheksversionen und Architekturentscheidungen |
| `docs/roadmap.md` | Entwicklungs-Session-Log (Verlauf) |
| `roadmap.md`, `roadmap-kontext-dsp.md` | Technisches Review und die Roadmap für Kontext-Modi und DSP |
| `docs/roadmap-private.md`, `docs/roadmap-private-status.md` | Roadmap für die kleine private Nutzergruppe und ihr Stand |
