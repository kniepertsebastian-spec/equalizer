# Roadmap: Bass-orientierte Equalizer-App für Android

> Arbeitsdokument für Claude Code. Diese Datei ist zugleich Produktspezifikation, Architekturentscheidung und Umsetzungsplan. Arbeite die Meilensteine der Reihe nach ab, halte den Build nach jedem Meilenstein grün und aktualisiere die Checklisten in dieser Datei.

## 1. Produktvision

Eine moderne Android-App, die Kopfhörer und Lautsprecher für basslastige elektronische Musik – insbesondere Uptempo Hardcore – kontrolliert abstimmt. Die App soll nicht einfach maximalen Bass erzeugen, sondern Tiefbass, Kick-Punch und Klarheit verbessern, Übersteuerung vermeiden und verständlich zeigen, welche Funktionen auf dem jeweiligen Gerät tatsächlich verfügbar sind.

Arbeitstitel: **HardBass EQ**. Der Name ist vor Veröffentlichung zu prüfen und leicht austauschbar zu halten.

### Leitprinzipien

1. **Sauberer Druck statt maximaler Lautstärke.** Keine irreführende „Volume Booster“-Funktion.
2. **Clipping-Schutz zuerst.** Positive Verstärkung muss mit Headroom, Eingangsabsenkung oder Limiting gekoppelt werden.
3. **Ehrliche Kompatibilität.** Die App behauptet niemals, auf jedem Android-Gerät und in jeder Streaming-App systemweit zu funktionieren.
4. **Progressive Komplexität.** Einsteiger erhalten sichere Presets; erfahrene Nutzer können Parameter detailliert bearbeiten.
5. **Offline und privat.** Presets und Geräteprofile bleiben standardmäßig lokal. Keine Analyse von Hörverhalten.
6. **Messbar und testbar.** Jede DSP- oder Effektfunktion benötigt definierte Grenzen, Bypass-Vergleiche und Tests.

## 2. Harte Android-Grenze

Androids öffentliche `AudioEffect`-APIs binden Effekte zuverlässig an eine konkrete Audio-Session. Das Anhängen von Insert-Effekten an den globalen Ausgabemix über Session-ID `0` ist offiziell veraltet. Manche Geräte und Player unterstützen globale oder fremde Sessions weiterhin, andere nicht oder nur teilweise.

Daraus folgen drei Betriebsarten:

| Modus | Zweck | Zuverlässigkeit | Release-Status |
|---|---|---:|---|
| Session-Modus | Effekt an eine vom Player veröffentlichte Audio-Session binden | Gut, wenn der Player kooperiert | MVP |
| Kompatibilitätsmodus | Herstellerabhängige globale Session bzw. verfügbare Systemeffekte verwenden | Best Effort | Experimentell, klar kennzeichnen |
| Eigene Wiedergabepipeline | Eigene Audiodateien über Media3 und eigenen DSP verarbeiten | Kontrollierbar | Spätere Phase, nicht MVP |

Wichtig: `MediaProjection`/Playback Capture ist kein zulässiger Ersatz für einen virtuellen Systemausgang. Es darf nicht als Weg geplant werden, fremde App-Audiosignale abzugreifen, zu bearbeiten und wieder systemweit auszugeben.

## 3. Umfang

### MVP – enthalten

- Kotlin-App mit Jetpack Compose und Material 3
- Android 9+ (`minSdk 28`), weil `DynamicsProcessing` ab API 28 verfügbar ist
- Laufzeit-Erkennung der verfügbaren Audioeffekte und ihrer echten Parametergrenzen
- Session-Erkennung über die vorgesehenen AudioEffect-Control-Intents, soweit vom Player unterstützt
- Grafischer EQ auf Basis der vom Gerät bereitgestellten Bänder
- Presets für Uptempo/Hard Dance sowie ein neutrales Preset
- Master-Schalter und vollständiger Bypass
- Automatischer Headroom, wenn die aktive Engine Eingangs-Gain unterstützt
- Limiter und Multiband-Dynamik nur, wenn `DynamicsProcessing` tatsächlich verfügbar ist
- Separate Profile für Bluetooth-, Kabel-, USB- und Gerätelautsprecher-Ausgänge
- Automatischer Profilwechsel anhand des Audio-Ausgabegeräts
- Import/Export eigener Presets als versioniertes JSON
- Diagnoseansicht mit aktiver Session, Route, Engine und nicht unterstützten Funktionen
- Lokale Speicherung; keine Anmeldung und kein Tracking-SDK
- Deutsche und englische UI-Texte

### Nicht im MVP

- Root/Magisk-Unterstützung
- Eigener Kernel- oder Audio-Treiber
- Garantierte Bearbeitung jeder Drittanbieter-App
- DRM-Umgehung oder Playback Capture zur Wieder-Ausgabe
- Cloud-Konto, Community-Presets oder Telemetrie
- Eigener Musikplayer
- AutoEQ-Datenbank
- Convolution/Impulse Responses
- Transient Shaper, Subharmonic Synthesizer oder harmonischer Exciter
- Werbung, Abonnement oder In-App-Käufe

## 4. Ziel-Nutzererlebnis

### Erster Start

1. Kurze Erklärung: Die App verbessert Klang kontrolliert, Verfügbarkeit hängt von Gerät und Player ab.
2. Berechtigungen nur kontextbezogen anfragen.
3. Angeschlossenen Audioausgang erkennen.
4. Kompatibilitätstest ausführen und Ergebnis verständlich anzeigen.
5. Preset „Uptempo – Clean Punch“ als Vorschlag anbieten, aber nicht ungefragt aktivieren.
6. A/B-Schalter für sofortigen Vergleich bereitstellen.

### Hauptansicht

- Master-Schalter
- Statuschip: `Aktiv`, `Wartet auf Audio-Session`, `Teilweise unterstützt` oder `Nicht unterstützt`
- Aktuelles Ausgabegerät und aktives Profil
- Preset-Auswahl
- EQ-Kurve und Bandregler
- Headroom-/Clipping-Anzeige
- Schnellzugriff auf Bass, Punch und Härte; diese Makros werden deterministisch auf EQ-Bänder abgebildet
- Link zur Diagnose, wenn eine Funktion nicht greift

### Expertenansicht

- Alle vom Gerät gemeldeten EQ-Bänder mit tatsächlichem Frequenzzentrum und Wertebereich
- Eingangs-Gain/Preamp, falls unterstützt
- Limiterparameter, falls unterstützt
- Multiband-Kompressor, falls unterstützt
- Links-/Rechts-Balance, sofern technisch sauber umsetzbar
- Preset duplizieren, umbenennen, zurücksetzen, importieren und exportieren

## 5. Klangkonzept und sichere Presets

Die Presets sind Zielkurven, keine Garantie für identische Ergebnisse auf allen Geräten. Sie müssen auf die tatsächlich verfügbaren Bänder interpoliert und anschließend begrenzt werden.

### Preset A: Uptempo – Clean Punch

| Zielbereich | Gain | Zweck |
|---|---:|---|
| 45–60 Hz | +3 dB | kontrollierter Tiefbass |
| 90–120 Hz | +2 dB | Kick-Punch |
| 200–350 Hz | −2 dB | weniger Matsch/Dröhnen |
| 2,5–4,5 kHz | +1 dB | Attack und Durchsetzung |
| 7–10 kHz | −1 dB | weniger Härte bei scharfen Masters |

Ziel-Headroom: mindestens `−4 dB` vor Dynamikverarbeitung. Wenn kein echtes Input-Gain verfügbar ist, positive Gains so skalieren, dass das geschätzte Clipping-Risiko begrenzt bleibt, und die Einschränkung in der UI anzeigen.

### Preset B: Uptempo – Deep Rumble

- 45–70 Hz: +4 dB
- 90–120 Hz: +1 dB
- 220–350 Hz: −2,5 dB
- 3–5 kHz: 0 dB
- Ziel-Headroom: −5 dB

### Preset C: Hard Dance – Balanced

- 50–80 Hz: +2 dB
- 100–140 Hz: +1 dB
- 250–400 Hz: −1,5 dB
- 3–5 kHz: +0,5 dB
- Ziel-Headroom: −3 dB

### Preset D: Flat/Safe

- Alle Bänder: 0 dB
- Dynamikmodule: aus
- Kein versteckter Gain

### Makroregler

- **Bass:** Low-Shelf-ähnliche Abbildung auf alle verfügbaren Bänder unter 120 Hz
- **Punch:** breite Anhebung um 90–140 Hz, gekoppelt mit optionaler leichter Absenkung um 250–350 Hz
- **Härte:** kontrolliert 2,5–6 kHz; positive Änderung maximal +2 dB
- Makros dürfen niemals außerhalb der Engine-Grenzen schreiben.
- Jede Makroänderung muss im Experten-EQ sichtbar und vollständig reversibel sein.

## 6. Vorgeschlagener Technologie-Stack

- Sprache: Kotlin
- UI: Jetpack Compose + Material 3
- Architektur: unidirektionaler Datenfluss mit ViewModels, Coroutines und `StateFlow`
- Dependency Injection: Hilt
- Navigation: Navigation Compose
- Einstellungen: DataStore
- Komplexere Profile/Presets: Room
- Serialisierung: Kotlin Serialization
- Audio MVP: `android.media.audiofx.AudioEffect`, `Equalizer`, `DynamicsProcessing`
- Routing: `AudioManager`, `AudioDeviceInfo`, passende Audio-Device-Callbacks
- Hintergrundarbeit: nur wenn nachweislich nötig; keine dauerhaft laufende Foreground-Service-Lösung als Voreinstellung
- Tests: JUnit, kotlinx-coroutines-test, Turbine oder gleichwertig, Compose UI Test, instrumentierte Android-Tests
- Qualität: ktlint oder Spotless, detekt, Android Lint
- CI: GitHub Actions mit Build, Unit Tests, Lint und Debug-APK-Artefakt

Keine Versionsnummern blind festschreiben. Claude Code soll beim Projektstart die aktuelle stabile Android-/Kotlin-/Compose-Kompatibilitätsmatrix prüfen und die gewählten Versionen in `docs/DEPENDENCIES.md` dokumentieren.

## 7. Architektur

### Module

```text
app/
core:model/
core:data/
core:audio-api/
core:audio-android/
core:designsystem/
feature:onboarding/
feature:equalizer/
feature:presets/
feature:profiles/
feature:diagnostics/
```

Für den ersten Commit darf ein einzelnes App-Modul verwendet werden. Vor Meilenstein 2 sollen die Audio-Schnittstellen jedoch klar vom Android-Backend getrennt sein. Modularisierung nur durchführen, wenn sie Build und Entwicklung nicht unnötig blockiert.

### Zentrale Schnittstellen

```kotlin
interface AudioEngine {
    val capabilities: StateFlow<AudioCapabilities>
    val state: StateFlow<AudioEngineState>

    suspend fun attach(session: AudioSession): AttachResult
    suspend fun detach()
    suspend fun setEnabled(enabled: Boolean)
    suspend fun apply(settings: ProcessingSettings): ApplyResult
    suspend fun readBack(): ProcessingSnapshot
}

interface AudioSessionRepository {
    val sessions: StateFlow<List<AudioSession>>
    val activeSession: StateFlow<AudioSession?>
}

interface AudioRouteRepository {
    val activeRoute: StateFlow<AudioRoute>
}
```

Weitere Kernmodelle:

- `AudioCapabilities`: verfügbare Effekte, Bandanzahl, Frequenzen, Gain-Grenzen, Kanalzahl, Input-Gain, Limiter, MBC
- `AudioEngineState`: detached, attaching, active, suspended, unsupported, lostControl, error
- `ProcessingSettings`: EQ, Input-Gain, Limiter, MBC, Balance und Bypass
- `DeviceProfile`: Route-Fingerprint, Anzeigename, Preset-ID, Overrides
- `Preset`: Schema-Version, Name, Zielkurve, Dynamikeinstellungen, Herkunft
- `CompatibilityReport`: Gerät, Android-Version, Effekt-Deskriptoren, getestete Funktionen und Fehlercodes – ohne personenbezogene Daten

### Datenfluss

```text
Audio route/session events
        -> repositories
        -> profile resolver
        -> validated ProcessingSettings
        -> AudioEngine
        -> read-back verification
        -> UI state + diagnostics
```

### Threading und Lebenszyklus

- Niemals AudioEffect-Aufrufe direkt aus Composables.
- Alle Effektobjekte besitzen genau einen Owner im Audio-Layer.
- Erstellen, Anwenden und Freigeben seriell über einen `Mutex` oder einen einzelnen Actor.
- Bei Session-Wechsel alte Effekte immer in `finally` freigeben.
- `DEAD_OBJECT`, Kontrollverlust und nicht unterstützte Parameter als Zustände modellieren, nicht als App-Crash.
- Slider-Eingaben drosseln, aber die letzte Änderung garantiert anwenden.

## 8. Signal- und Einstellungsreihenfolge

Logische Reihenfolge, soweit das jeweilige Android-Backend sie unterstützt:

```text
Input -> Input Gain/Headroom -> Pre-EQ -> Multiband Dynamics -> Post-EQ -> Limiter -> Output
```

Regeln:

- Limiter steht logisch immer am Ende.
- Bypass darf keinerlei versteckte Klangänderung behalten.
- Automatischer Headroom basiert konservativ auf dem maximalen positiven EQ-Gain; später kann eine präzisere Schätzung folgen.
- Parameteränderungen möglichst weich interpolieren, um Knackser zu verhindern.
- Wenn das System nur einen einfachen `Equalizer` bereitstellt, UI und Datenmodell auf dessen echte Fähigkeiten reduzieren.
- Nicht unterstützte Parameter nicht simulieren oder stillschweigend ignorieren.

## 9. Datenformat für Presets

Versioniertes, menschenlesbares JSON verwenden:

```json
{
  "schemaVersion": 1,
  "name": "Uptempo - Clean Punch",
  "targetCurve": [
    { "frequencyHz": 55.0, "gainDb": 3.0 },
    { "frequencyHz": 105.0, "gainDb": 2.0 },
    { "frequencyHz": 280.0, "gainDb": -2.0 },
    { "frequencyHz": 3500.0, "gainDb": 1.0 },
    { "frequencyHz": 8500.0, "gainDb": -1.0 }
  ],
  "requestedHeadroomDb": 4.0,
  "limiter": { "enabled": true, "thresholdDb": -1.0 },
  "metadata": { "genre": "uptempo-hardcore", "builtIn": true }
}
```

Importregeln:

- Schema-Version validieren.
- Größenlimit setzen.
- NaN, Infinity, negative Frequenzen und extreme Gains ablehnen.
- Unbekannte Felder für Vorwärtskompatibilität tolerieren.
- Import niemals direkt aktivieren; zuerst Vorschau und Bestätigung.
- Export enthält keine Gerätekennungen oder Nutzungsdaten.

## 10. Meilensteine

### M0 – Machbarkeits-Spike

Ziel: Vor UI-Feinarbeit beweisen, was auf realen Geräten funktioniert.

- [ ] Leeres Kotlin-/Compose-Projekt erstellen und reproduzierbaren Gradle-Build herstellen.
- [ ] `minSdk 28` setzen; aktuelle stabile `compileSdk`/`targetSdk` verwenden.
- [ ] Verfügbare Effekte über `AudioEffect.queryEffects()` erfassen.
- [ ] Einfachen `Equalizer` an eine kontrollierte Test-Audio-Session binden.
- [ ] Bänder, Frequenzen und Gain-Grenzen auslesen und protokollieren.
- [ ] `DynamicsProcessing` erkennen und Input-Gain, EQ, MBC sowie Limiter einzeln testen.
- [ ] Open/Close-AudioEffect-Control-Intents mit einem eigenen kleinen Testplayer validieren.
- [ ] Verhalten bei Session `0` ausschließlich als Experiment dokumentieren; nicht als Garantie verwenden.
- [ ] Geräte-Matrix mit mindestens Emulator plus einem physischen Gerät beginnen.
- [ ] Ergebnisse in `docs/FEASIBILITY.md` festhalten.

Abnahmekriterien:

- App kann auf dem Testgerät eine bekannte Session hörbar und reversibel verändern.
- Bypass stellt Flat-Zustand wieder her.
- Alle erzeugten Effektobjekte werden zuverlässig freigegeben.
- Eine Entscheidung für MVP-Backend und Fallbacks ist schriftlich dokumentiert.

Stop-Kriterium: Falls keine stabile Bearbeitung fremder Sessions möglich ist, bleibt die App als Session-kompatibler Effect Controller positioniert. Nicht zu einer behaupteten systemweiten Lösung umdeuten.

### M1 – Projektfundament

- [ ] Paketnamen und Arbeitstitel zentral konfigurierbar machen.
- [ ] Compose Design System, Navigation und Theme erstellen.
- [ ] Hilt, Coroutines, DataStore, Room und Serialization einrichten.
- [ ] CI mit `assembleDebug`, Unit Tests, Android Lint, Formatierung und detekt einrichten.
- [ ] Fehler- und Logstrategie definieren; Release-Logs dürfen keine Track- oder Gerätenamen enthalten.
- [ ] `docs/ARCHITECTURE.md`, `docs/DEPENDENCIES.md` und ADR-Verzeichnis anlegen.
- [ ] Debug-Menü für Engine-Simulation und Capability-Fakes hinzufügen.

Abnahmekriterien:

- Frischer Checkout baut mit einem dokumentierten Befehl.
- CI ist grün.
- Keine Geschäftslogik lebt in Composables.

### M2 – Audio-Engine und Session-Lebenszyklus

- [ ] `AudioEngine` und Fake-Implementierung erstellen.
- [ ] Android-Implementierung für `Equalizer` erstellen.
- [ ] Optionale Android-Implementierung für `DynamicsProcessing` erstellen.
- [ ] Capability Discovery mit Read-back implementieren.
- [ ] AudioEffect-Control-Session-Intents verarbeiten.
- [ ] Attach/Detach, Kontrollverlust, tote Session und Engine-Konflikt behandeln.
- [ ] Route-Erkennung für Speaker, kabelgebunden, Bluetooth und USB implementieren.
- [ ] Engine-State als `StateFlow` zur UI führen.
- [ ] Keine dauerhafte Hintergrundausführung einführen, bevor sie technisch und Play-Policy-seitig begründet ist.

Abnahmekriterien:

- Wiederholte Session-Wechsel verursachen keine Leaks oder Abstürze.
- Nicht unterstützte Effekte führen zu Capability-Fallbacks.
- UI kann zwischen `aktiv`, `wartend`, `teilweise unterstützt` und `Fehler` unterscheiden.

### M3 – Equalizer-MVP

- [ ] Dynamische Bandregler anhand der realen Engine-Bänder darstellen.
- [ ] Flat, Clean Punch, Deep Rumble und Balanced als Built-in-Presets ausliefern.
- [ ] Zielkurve logarithmisch auf Gerätebänder interpolieren.
- [ ] Gain-Werte auf gemeldete Min-/Max-Grenzen begrenzen.
- [ ] Master-Bypass und A/B-Vergleich implementieren.
- [ ] Bass-, Punch- und Härte-Makros implementieren.
- [ ] Headroom-Schätzung und Warnung bei Clipping-Risiko anzeigen.
- [ ] Bei verfügbarem Input-Gain automatische Absenkung anwenden.
- [ ] Alle Änderungen per Read-back verifizieren.

Abnahmekriterien:

- Presets liefern auf allen getesteten Bandkonfigurationen deterministische Werte.
- Flat setzt alle von der App kontrollierten Klangparameter neutral.
- Kein Regler kann außerhalb der gemeldeten Engine-Grenzen schreiben.
- Screenreader lesen Frequenz, Gain und Einheit verständlich vor.

### M4 – Profile und Persistenz

- [ ] Room-Schema für Presets und Geräteprofile implementieren.
- [ ] DataStore für globale UI-/App-Einstellungen nutzen.
- [ ] Stabilen, datensparsamen Route-Fingerprint definieren.
- [ ] Profilwechsel bei Route-Wechsel implementieren.
- [ ] Konfliktregeln definieren: manuelle Auswahl schlägt automatische Auswahl bis zum nächsten Route-Wechsel.
- [ ] JSON-Import mit Vorschau und Validierung implementieren.
- [ ] JSON-Export über Android Sharesheet/Storage Access Framework implementieren.
- [ ] Migrationstests für Datenbankschema und Preset-Schema ergänzen.

Abnahmekriterien:

- Bluetooth- und Kabelprofil überschreiben sich nicht.
- App-Neustart stellt den letzten konsistenten Zustand wieder her.
- Fehlerhafte Imports verändern den aktiven Zustand nicht.

### M5 – Dynamik und Schutz

- [ ] Limiter nur bei gemeldeter Unterstützung aktivieren.
- [ ] Sichere Standardwerte definieren; Threshold niemals über 0 dBFS anbieten.
- [ ] Optionalen Bass-Multiband-Kompressor für 30–150 Hz anbieten, sofern Engine unterstützt.
- [ ] Attack, Release, Ratio und Gain-Grenzen konservativ begrenzen.
- [ ] Gain-Reduction bzw. Aktivität anzeigen, wenn die Engine Messwerte bereitstellt; andernfalls keine Fake-Anzeige.
- [ ] Schutz vor Parameter-Sprüngen und Race Conditions implementieren.
- [ ] Hörwarnung und sachliche Erklärung hoher Lautstärke integrieren, ohne Nutzerdaten zu speichern.

Abnahmekriterien:

- Extreme gültige Einstellungen erzeugen keinen App-Crash.
- Limiter-Bypass und Gesamt-Bypass funktionieren vollständig.
- Auf Geräten ohne `DynamicsProcessing` bleibt der EQ nutzbar und die UI erklärt die Einschränkung.

### M6 – Diagnose, Onboarding und UX-Politur

- [ ] Kompatibilitätsprüfung beim Onboarding.
- [ ] Diagnoseansicht mit aktiver Route, Session-ID, Effekt-Engine, Bandanzahl und Fähigkeiten.
- [ ] Kopierbaren, datensparsamen Diagnosebericht erstellen.
- [ ] Problemlösungen für Akkuoptimierung und Player-Kompatibilität nur gerätespezifisch anzeigen, wenn relevant.
- [ ] Alle leeren, wartenden, nicht unterstützten und Fehlerzustände gestalten.
- [ ] Dark Mode, dynamische Farben und große Schrift testen.
- [ ] Deutsche und englische Lokalisierung vervollständigen.
- [ ] Keine manipulativen Lautstärkeversprechen oder audiophile Scheinmetriken verwenden.

Abnahmekriterien:

- Nutzer versteht innerhalb einer Ansicht, ob und worauf der Effekt wirkt.
- App ist mit TalkBack und 200 % Schriftgröße bedienbar.
- Diagnosebericht enthält keine Mediennamen, Bluetooth-MAC-Adressen oder andere eindeutige Identifikatoren.

### M7 – Qualität und Beta

- [ ] Unit Tests für Interpolation, Clamping, Headroom, Makros und Importvalidierung.
- [ ] Contract Tests, die Fake- und Android-Engine gegen dieselben Zustandsregeln prüfen.
- [ ] Instrumentierte Tests für Session-Wechsel, Route-Wechsel und Prozessneustart.
- [ ] Compose-Tests für Master-Schalter, Presetwechsel und Fehlerzustände.
- [ ] Leak- und StrictMode-Prüfung.
- [ ] Performance-/Akkutest über mindestens 60 Minuten Wiedergabe.
- [ ] Audio-Smoke-Tests mit Sinustönen, Sweep, Impuls und stark geclipptem Testsignal.
- [ ] Testmatrix mindestens Pixel/AOSP, Samsung und ein weiterer Hersteller, soweit Geräte verfügbar.
- [ ] Datenschutzerklärung und Play-Store-Angaben vorbereiten.
- [ ] Interne Beta veröffentlichen und Kompatibilitätsberichte sammeln – nur nach expliziter Einwilligung.

Release-Gates:

- Kein P0/P1-Bug offen.
- Keine bekannte Einstellung erzeugt unkontrolliertes positives Gain ohne Warnung/Schutz.
- Bypass ist auf jedem Testgerät verifiziert.
- Unsupported-Geräte erhalten eine ehrliche Meldung statt wirkungsloser Regler.
- Crash-freier manueller Langzeittest mit Bluetooth-Wechsel und eingehendem Anruf.

### M8 – Post-MVP

Reihenfolge nach Nutzerfeedback und technischer Machbarkeit:

1. AutoEQ-Import und später eine lizenzrechtlich geprüfte Profil-Datenbank
2. Parametrischer EQ in einer eigenen Media3-Wiedergabepipeline
3. Nativer DSP-Kern in C++ oder Rust mit 32-Bit-Float-Verarbeitung
4. Dynamischer Bass
5. Transient Shaper für Attack/Sustain
6. Convolution/Impulse Responses
7. Subharmonic-/harmonischer Bass-Enhancer für kleine Lautsprecher
8. Frequenzabhängige Stereo-Breite mit mono gehaltenem Tiefbass
9. Loudness-Normalisierung/ReplayGain in der eigenen Wiedergabepipeline

Diese Funktionen dürfen nicht als systemweit beworben werden, solange sie nur in der eigenen Wiedergabepipeline laufen.

## 11. Teststrategie im Detail

### Unit Tests

- Logarithmische Frequenzinterpolation
- Kurvenabbildung auf 5, 9, 10, 15 und 32 simulierte Bänder
- Min-/Max-Clamping
- Automatischer Headroom
- Makroregler-Komposition und Rückgängigmachen
- JSON-Schema, Größenlimit und feindliche Eingaben
- Profilauflösung bei konkurrierenden Routen
- Zustandsmaschine der Engine

### Instrumentierte Tests

- Effekt an eigene MediaPlayer-/AudioTrack-Session binden
- Wiederholt attach/detach
- Kontrollverlust simulieren
- App-Prozess beenden und Zustand rekonstruieren
- Bluetooth verbinden/trennen
- Telefonanruf bzw. Audio-Focus-Unterbrechung
- Gerät drehen, Dark Mode, große Schrift, TalkBack-Semantik

### Audio-Referenztests

Testdateien dürfen selbst erzeugte, lizenzfreie Signale sein:

- 40/60/100/250/1000/4000/8000-Hz-Sinustöne
- logarithmischer Sweep 20 Hz–20 kHz
- Dirac-Impuls
- rosa Rauschen
- Kick-ähnlicher Testimpuls mit langem Bass-Tail
- Signal knapp unter 0 dBFS zur Clipping-Prüfung

Wenn die Plattform keine numerischen Ausgangsdaten bereitstellt, werden Hör-/Loopback-Tests als manuelle Tests dokumentiert und nicht als automatisierte Präzisionsmessung ausgegeben.

## 12. Berechtigungen und Datenschutz

- Nur `MODIFY_AUDIO_SETTINGS` verwenden, wenn vom gewählten Backend benötigt.
- Benachrichtigungs- oder Foreground-Service-Berechtigungen erst hinzufügen, wenn ein implementierter Anwendungsfall sie wirklich erfordert.
- Keine Accessibility API zur Erkennung fremder Player missbrauchen.
- Keine Bluetooth-MAC-Adressen speichern.
- Keine Titel, Interpreten oder Hörhistorie erfassen.
- Keine Audioaufnahmen oder Mikrofonberechtigung.
- Diagnoseexport nur nach Nutzeraktion und mit Vorschau.
- Abhängigkeiten mit Telemetrie müssen begründet oder vermieden werden.

## 13. Risiken und Gegenmaßnahmen

| Risiko | Auswirkung | Gegenmaßnahme |
|---|---|---|
| Globaler EQ greift auf manchen Geräten nicht | Kernversprechen nicht erfüllt | Session-Modus priorisieren, Kompatibilitätsprüfung und ehrliche Statusanzeige |
| Hersteller implementiert Audioeffekte anders | Andere Bandzahl/Grenzen | Fähigkeiten immer abfragen, nie fest codieren |
| Player sendet keine Session-Intents | Keine Verbindung | Anleitung und Support-Matrix; später eigener Player, keine Umgehung behaupten |
| Zweite EQ-App übernimmt Engine-Kontrolle | Effekt stoppt | Kontrollverlust erkennen, erklären und Wiederanbindung anbieten |
| Bass-Boost clippt | Schlechter Klang/Hörbelastung | Headroom, Limiter, konservative Grenzen, Warnung |
| Hintergrundprozess wird beendet | Effekt verschwindet | Zustand sichtbar machen; Hintergrundlösung nur policy-konform und begründet |
| Preset klingt je Kopfhörer stark anders | Inkonsistente Qualität | Geräteprofile; später AutoEQ; keine universellen Qualitätsversprechen |
| Scope wächst zu früh | MVP verzögert | Post-MVP-Funktionen strikt hinter Release-Gate halten |

## 14. Definition of Done pro Ticket

Ein Ticket gilt erst als abgeschlossen, wenn:

- Implementierung und Fehlerzustände vollständig sind.
- Relevante Unit-/UI-/Instrumentierungstests existieren und grün sind.
- Accessibility Labels und deutsche/englische Texte vorhanden sind.
- Keine neue Berechtigung ohne Dokumentation eingeführt wurde.
- Architektur- oder Produktentscheidung bei Bedarf als ADR dokumentiert wurde.
- Lint, Formatierung und Build lokal sowie in CI grün sind.
- Die Checkliste in dieser Roadmap aktualisiert wurde.

## 15. Arbeitsanweisung für Claude Code

1. Lies zuerst diese gesamte Datei.
2. Prüfe das Repository und bestehende Anweisungsdateien, bevor du Änderungen machst.
3. Starte mit **M0**. Implementiere nicht mehrere Meilensteine gleichzeitig.
4. Lege vor größeren Architekturentscheidungen eine kurze ADR unter `docs/adr/` an.
5. Führe nach jedem sinnvollen Schritt die kleinste relevante Testmenge aus.
6. Nutze keine versteckten oder nicht öffentlichen Android-APIs.
7. Erfinde keine Systemweite-Kompatibilität. Jede Capability wird zur Laufzeit geprüft.
8. Füge keine Analytics-, Werbe- oder Account-Abhängigkeiten hinzu.
9. Implementiere sichere Standardwerte; Bypass muss immer erreichbar bleiben.
10. Aktualisiere am Ende jeder Session:
   - erledigte Checkboxen in dieser Datei,
   - `docs/DECISIONS.md` mit offenen Entscheidungen,
   - `docs/TEST_MATRIX.md` mit getesteten Geräten/Android-Versionen,
   - eine kurze Zusammenfassung der nächsten konkreten Aufgabe.

## 16. Erste konkrete Aufgaben

Claude Code soll für den ersten Arbeitsdurchlauf ausschließlich Folgendes erledigen:

1. Android-Projekt initialisieren.
2. Einen reproduzierbaren Debug-Build herstellen.
3. Compose-Startansicht mit App-Name und leerem Kompatibilitätsstatus anlegen.
4. `AudioEffect.queryEffects()` hinter einem Repository kapseln.
5. Eine Debug-Ansicht erstellen, die verfügbare Effekt-Deskriptoren anzeigt.
6. Unit Test für Mapping der Effekt-Deskriptoren auf `AudioCapabilities` schreiben.
7. CI-Grundworkflow einrichten.
8. `docs/FEASIBILITY.md` mit Testanleitung und noch offenen M0-Punkten anlegen.

Danach stoppen, Ergebnisse zusammenfassen und erst nach Review mit dem Session-Attach-Spike fortfahren.

## 17. Offene Entscheidungen

- [ ] Finaler App-Name und Package-ID
- [ ] Ausschließlich Session-Controller oder langfristig zusätzlich eigener Player
- [ ] Welche physischen Testgeräte stehen zur Verfügung?
- [ ] Soll die erste öffentliche Version AutoEQ-Import enthalten oder erst Post-MVP?
- [ ] Open-Source-Lizenz und Veröffentlichungsmodell
- [ ] Monetarisierung – bewusst erst nach technischem MVP entscheiden

## 18. Referenzen

- Android `AudioEffect`: https://developer.android.com/reference/android/media/audiofx/AudioEffect
- Android `Equalizer`: https://developer.android.com/reference/android/media/audiofx/Equalizer
- Android `DynamicsProcessing`: https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing
- Android Audio Routing: https://developer.android.com/reference/android/media/AudioManager
- Android Media3: https://developer.android.com/media/media3

Stand der Roadmap: 18. September 2026.

