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

## Control-Intents und Session-0-Experiment (dritter Durchlauf)

Ergänzt in `app/src/main/java/com/hardbasseq/eq/audio/spike/`:

- `ControlSessionIntentSpike`: registriert einen `BroadcastReceiver` für
  `AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` und
  `ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION`, sendet dieselben Broadcasts
  testweise selbst (wie ein kooperativer Player es täte) und prüft, ob sie
  korrekt zurückkommen (Session-ID, Package-Name als Extras). Das validiert
  **nur unsere eigene Empfänger-Logik** – nicht, ob ein echter Drittanbieter-
  Player diese Broadcasts von sich aus sendet. Empfang von echten fremden
  Broadcasts bräuchte `RECEIVER_EXPORTED` und eine eigene Sicherheitsprüfung
  – bewusst nicht Teil dieses Spikes.
- `SessionZeroExperiment`: rein informativer, read-only Test, ob sich ein
  `Equalizer` überhaupt auf Session `0` (globaler Mix) konstruieren lässt.
  Der Effekt wird **nie aktiviert** – nur angehängt und sofort wieder
  freigegeben –, kann also niemals hörbar in die Wiedergabe eingreifen. Ein
  Erfolg hier ist ausdrücklich **keine unterstützte Funktion** (Roadmap §2).
- UI: zwei neue Unterabschnitte unter „Show session-attach spike (M0)":
  „Control-session intents (M0)" und „Session 0 experiment (M0,
  informational only)".

Wie beim Session-Attach-Spike zuvor: nur über CI kompiliert, **nicht auf
einem Gerät ausgeführt** – Verifikation folgt über den Nutzer auf dem
Pixel 10.

**Erster Testlauf auf dem Pixel 10:**

- Session-0-Experiment: `[FAIL] Cannot initialize effect engine for type: …`
  – erwartetes, sauberes Fehlschlagen ohne Crash. Bestätigt genau das, was
  Roadmap §2 vorhersagt ("Manche Geräte ... unterstützen globale ... Sessions
  ... nicht"). Kein Bugfix nötig, das ist das korrekte Verhalten des Codes.
- Control-Intent-Test: „No broadcasts received back." – **auch nach dem
  Fix** (aktives Warten bis zu 3 s statt festem 300-ms-Delay, zweiter
  Testlauf) weiterhin kein einziger Broadcast beim eigenen Empfänger
  angekommen. Damit ist ein reines Timing-Problem ausgeschlossen.
  **Untersucht und ausgeschlossen:** Die beiden Actions stehen **nicht**
  auf AOSPs `protected-broadcast`-Liste (direkt im AOSP-Quellcode von
  `frameworks/base/core/res/AndroidManifest.xml` verifiziert – keine
  Übereinstimmung für `AUDIO_EFFECT_CONTROL_SESSION`); Registrierung/Senden
  folgt dem dokumentierten, für API 33+ korrekten Muster
  (`RECEIVER_NOT_EXPORTED`, Broadcast ohne explizites Package). Da AOSP nur
  die öffentlich einsehbare Basis ist, kann eine gerätespezifische
  Einschränkung (z. B. eine zusätzliche `protected-broadcast`-Deklaration in
  Googles proprietärer Pixel-Firmware, die nicht im öffentlichen AOSP-Mirror
  steht) nicht ausgeschlossen werden – das ließe sich nur mit `adb logcat`
  am angeschlossenen Gerät weiter eingrenzen, was in dieser Sandbox nicht
  möglich ist.
  **Schlussfolgerung für die Roadmap:** Der historische Open/Close-
  Broadcast-Mechanismus, mit dem sich Dritt-Player traditionell bei
  EQ-Apps anmelden, funktioniert – zumindest für selbst gesendete
  Broadcasts auf diesem Pixel 10/Android 16 – nicht zuverlässig. Für M2
  bedeutet das: **nicht** ungeprüft auf dieses Mechanismus als
  Session-Erkennungsweg setzen; roadmap-konform (§2, §13) ohnehin nur als
  „Best Effort", nie als Garantie behandeln. Dieser Punkt ist damit
  **validiert** (negatives, aber sauber dokumentiertes Ergebnis) – nicht
  „kaputt", sondern eine echte Erkenntnis aus dem Spike.

## Noch offene M0-Checklistenpunkte (erfordern echtes Gerät/Emulator)

- [x] Open/Close-AudioEffect-Control-Intents: validiert (negatives
      Ergebnis, siehe oben und `docs/TEST_MATRIX.md`).
- [x] Session-`0`-Experiment: Ergebnis liegt vor (`[FAIL]`, sauber ohne
      Crash) – siehe oben und `docs/TEST_MATRIX.md`.
- [ ] Geräte-Matrix mit mindestens Emulator plus einem physischen Gerät
      beginnen (physisches Gerät vorhanden, Emulator weiterhin offen –
      diese Sandbox hat keinen Android-SDK-Zugriff, siehe oben).

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
