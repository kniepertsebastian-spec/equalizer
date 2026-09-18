# M0 – Machbarkeits-Spike

Status: **in Arbeit, CI grün.** Dieser erste Arbeitsdurchlauf deckt ausschließlich die
in `roadmap.md` §16 ("Erste konkrete Aufgaben") aufgeführten Punkte ab. Der
eigentliche Session-Attach-Spike (Anhängen von `Equalizer` an eine echte
Audio-Session, Auslesen von Bändern/Grenzen, Test von
`DynamicsProcessing`-Teilkomponenten, Verhalten bei Session `0`) ist laut
Roadmap-Anweisung **erst nach Review dieses Durchlaufs** an der Reihe.

## Was in diesem Durchlauf umgesetzt wurde

1. Kotlin-/Compose-Projekt initialisiert (Single-Module `app/`, siehe
   `docs/DEPENDENCIES.md` für die Versionsauswahl).
2. Gradle-Wrapper (Ziel: Gradle 9.7.1) eingerichtet; lokaler Build in dieser
   Sandbox nicht verifizierbar (kein Android-SDK-Zugriff, siehe unten).
3. Compose-Startansicht (`MainScreen.kt`) mit App-Name und einem noch
   ungeprüften Kompatibilitätsstatus-Chip.
4. `AudioEffect.queryEffects()` hinter `AudioEffectRepository` /
   `AndroidAudioEffectRepository` gekapselt (`app/src/main/java/com/hardbasseq/eq/audio/`).
5. Debug-Ansicht in `MainScreen.kt` (Button "Show audio effects (debug)"),
   die die rohen Effekt-Deskriptoren (Name, Implementor, Connect-Mode,
   Type-UUID) auflistet.
6. Unit-Test `AudioCapabilitiesMapperTest` für das Mapping von
   `AudioEffectDescriptor` auf `AudioCapabilities` (reine JVM-Logik, keine
   Android-Abhängigkeit).
7. CI-Grundworkflow (`.github/workflows/ci.yml`): `assembleDebug` +
   `testDebugUnitTest` auf `ubuntu-latest`.
8. Diese Datei.

## Bekannte Einschränkung dieser Sandbox

`dl.google.com` ist über die Egress-Policy dieser Umgebung blockiert
(`CONNECT tunnel failed, response 403`). Damit sind weder ein lokales
Android-SDK-Setup noch ein lokaler `./gradlew assembleDebug`-Lauf in dieser
Session möglich. Die Build- und Testverifikation muss über die GitHub-Actions-CI
erfolgen, sobald der Branch gepusht bzw. eine PR erstellt ist.

**Update:** Der CI-Lauf ist grün (siehe `docs/TEST_MATRIX.md` und PR #1). Der
erste Versuch schlug fehl, weil AGP 9.0+ Kotlin fest eingebaut mitbringt und
das zusätzlich angewendete `org.jetbrains.kotlin.android`-Plugin den Build
fatal abbrach; behoben durch Entfernen dieses Plugins (Details in
`docs/DEPENDENCIES.md`). Damit sind Build und Unit-Tests aus §16 verifiziert.
PR #1 wurde gemerged.

## Session-Attach-Spike (zweiter Durchlauf)

Umgesetzt in `app/src/main/java/com/hardbasseq/eq/audio/spike/`:

- `SineWaveGenerator` (rein, unit-getestet): erzeugt einen leisen Sinuston als PCM16.
- `TestTonePlayer`: spielt den Ton über einen eigenen `AudioTrack` (eigene,
  selbst erzeugte `audioSessionId` – **keine** fremde Session, kein Session `0`).
- `EqualizerSpike`: hängt einen `Equalizer` an diese Session, liest
  Bandanzahl, Zentrumsfrequenzen, Frequenzbereiche und Gain-Grenzen aus und
  gibt den Effekt sofort wieder frei.
- `DynamicsProcessingSpike`: testet Input-Gain, Pre-EQ, MBC und Limiter
  jeweils einzeln isoliert (eigene `DynamicsProcessing.Config` pro Stufe,
  alle anderen Stufen deaktiviert), fängt jede Exception pro Stufe ab statt
  abzustürzen und protokolliert Erfolg/Fehlschlag samt Detailtext.
- `SessionAttachSpikeController`: serialisiert Start/Stop/Probe über einen
  `Mutex` (Roadmap §7 Threading-Regeln), gibt Effekte immer in `finally` frei.
- UI: neuer Button „Show session-attach spike (M0)" auf dem Startbildschirm
  zeigt Session-ID, Equalizer-Bänder und DynamicsProcessing-Stufenergebnisse an.

**Noch nicht abgedeckt (bewusst verschoben, siehe unten):** Open/Close-
AudioEffect-Control-Intents mit einem Testplayer, Verhalten bei Session `0`
als Experiment, Geräte-Matrix mit Emulator. Diese drei bleiben als
eigenständiger, klar abgegrenzter nächster Schritt offen, um diesen
Durchlauf nicht zu überladen.

**Update: Auf echtem Gerät verifiziert.** Der Nutzer hat den Button „Show
session-attach spike (M0)" auf seinem Google Pixel 10 (Android 16) getestet:
Equalizer-Attach erfolgreich (5 Bänder, Gain-Bereich −15…15 dB), alle vier
DynamicsProcessing-Stufen (Input-Gain, Pre-EQ, MBC, Limiter) isoliert
getestet und **alle OK**, kein Crash. Details in `docs/TEST_MATRIX.md`. Die
entsprechenden M0-Checkboxen in `roadmap.md` sind jetzt abgehakt.

Zwischenzeitlich war die CI fünf Commits lang komplett rot, weil ein
`secrets`-Verweis in einer `if:`-Bedingung die ganze Workflow-Datei ungültig
gemacht hat (0 Jobs, sofort rot, keine Logs) – behoben in Commit `8bf1a99`,
Details in `docs/DRIVE_UPLOAD.md`.

## Noch offene M0-Checklistenpunkte (erfordern echtes Gerät/Emulator)

Diese Punkte aus `roadmap.md` §10 (M0) sind laut §16 ausdrücklich **nicht**
Teil dieses ersten Durchlaufs und folgen erst nach Review:

- [ ] Einfachen `Equalizer` an eine kontrollierte Test-Audio-Session binden.
- [ ] Bänder, Frequenzen und Gain-Grenzen auslesen und protokollieren.
- [ ] `DynamicsProcessing` erkennen und Input-Gain, EQ, MBC sowie Limiter
      einzeln testen.
- [ ] Open/Close-AudioEffect-Control-Intents mit einem eigenen kleinen
      Testplayer validieren.
- [ ] Verhalten bei Session `0` ausschließlich als Experiment dokumentieren.
- [ ] Geräte-Matrix mit mindestens Emulator plus einem physischen Gerät
      beginnen (siehe `docs/TEST_MATRIX.md`).

## Wie man diesen Stand testet

Sobald ein Android SDK verfügbar ist (lokal mit Android Studio oder über CI):

```bash
./gradlew assembleDebug        # Debug-APK bauen
./gradlew testDebugUnitTest    # Unit-Tests, u.a. AudioCapabilitiesMapperTest
```

Die App zeigt beim Start den App-Namen, einen Platzhalter-Statuschip
("Compatibility not checked yet") und einen Button, der die vom Gerät
gemeldeten Audioeffekt-Deskriptoren aus `AudioEffect.queryEffects()` auflistet.
Dieser Aufruf ist session-unabhängig und bindet keinen Effekt an eine
Wiedergabe; er beantwortet nur, welche Effekt-Engines auf dem jeweiligen
Gerät/Build überhaupt registriert sind.
