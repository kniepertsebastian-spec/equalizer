# M0 – Machbarkeits-Spike

Status: **in Arbeit**. Dieser erste Arbeitsdurchlauf deckt ausschließlich die
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

**Nächster Schritt nach dieser Session:** CI-Lauf auf dem gepushten Branch
prüfen und Ergebnis hier ergänzen, bevor mit dem Session-Attach-Spike
begonnen wird.

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
