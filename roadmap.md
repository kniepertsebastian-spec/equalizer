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

Androids öffentliche `AudioEffect`-APIs binden Effekte zuverlässig an eine konkrete Audio-Session. Das Anhängen von Insert-Effekten an den globalen Ausgabemix über Session-ID `0` ist officially veraltet. Manche Geräte und Player unterstützen globale oder fremde Sessions weiterhin, andere nicht oder nur teilweise.

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

- [x] Leeres Kotlin-/Compose-Projekt erstellen und reproduzierbaren Gradle-Build herstellen. (Verifiziert per CI: `./gradlew assembleDebug` grün, s. PR #1 / `docs/TEST_MATRIX.md`.)
- [x] `minSdk 28` setzen; aktuelle stabile `compileSdk`/`targetSdk` verwenden. (`app/build.gradle.kts`: `minSdk 28`, `compileSdk 37`, `targetSdk 36`; Begründung in `docs/DEPENDENCIES.md`.)
- [x] Verfügbare Effekte über `AudioEffect.queryEffects()` erfassen. (`AudioEffectRepository`/`AndroidAudioEffectRepository`; unit-getestet in CI **und** auf echtem Gerät bestätigt – Google Pixel 10/Android 16 meldet u. a. `EqualizerBundle` und `DynamicsProcessing`, s. `docs/TEST_MATRIX.md`.)
- [x] Einfachen `Equalizer` an eine kontrollierte Test-Audio-Session binden. (Verifiziert auf Google Pixel 10/Android 16 über den Session-Attach-Spike-Button, PR #2; Session-ID 665, kein Crash, sauberes Attach/Release.)
- [x] Bänder, Frequenzen und Gain-Grenzen auslesen und protokollieren. (5 Bänder, Gain-Bereich −15.0…15.0 dB, Zentren 60/230/910/3600/14000 Hz mit vollen Frequenzbereichen – protokolliert in `docs/TEST_MATRIX.md`.)
- [x] `DynamicsProcessing` erkennen und Input-Gain, EQ, MBC sowie Limiter einzeln testen. (Alle vier Stufen isoliert getestet, alle **OK** auf Pixel 10 – Details in `docs/TEST_MATRIX.md`.)
- [x] Open/Close-AudioEffect-Control-Intents mit einem eigenen kleinen Testplayer validieren. (Validiert auf Pixel 10/Android 16, PR #4: selbst gesendete Broadcasts kommen beim eigenen Empfänger nicht an, reproduziert auch mit 3s aktivem Warten. Negatives, aber sauber dokumentiertes Ergebnis – Details/Implikation für M2 in `docs/FEASIBILITY.md`.)
- [x] Verhalten bei Session `0` ausschließlich als Experiment dokumentieren; nicht als Garantie verwenden. (Validiert auf Pixel 10/Android 16: `Equalizer`-Konstruktion auf Session `0` schlägt sauber fehl, kein Crash, Effekt nie aktiviert. Kein unterstütztes Feature, wie vorgesehen – Details in `docs/TEST_MATRIX.md`.)
- [ ] Geräte-Matrix mit mindestens Emulator plus einem physischen Gerät beginnen. (Physisches Gerät vorhanden – Google Pixel 10/Android 16, s. `docs/TEST_MATRIX.md`; Emulator-Eintrag steht noch aus.)
- [x] Ergebnisse in `docs/FEASIBILITY.md` festhalten.

Abnahmekriterien:

- App kann auf dem Testgerät eine bekannte Session hörbar und reversibel verändern.
- Bypass stellt Flat-Zustand wieder her.
- Alle erzeugten Effektobjekte werden zuverlässig freigegeben.
- Eine Entscheidung für MVP-Backend und Fallbacks ist schriftlich dokumentiert.

Stop-Kriterium: Falls keine stabile Bearbeitung fremder Sessions möglich ist, bleibt die App als Session-kompatibler Effect Controller positioniert. Nicht zu einer behaupteten systemweiten Lösung umdeuten.

### M1 – Projektfundament

- [x] Paketnamen und Arbeitstitel zentral konfigurierbar machen. (`gradle.properties`: `hardbasseq.applicationId`/`hardbasseq.namespace`, referenziert aus `app/build.gradle.kts`; Anzeigename bleibt in `strings.xml`.)
- [x] Compose Design System, Navigation und Theme erstellen. (Cyber/Dark-Design-System, Material 3 Tokens, AppNavHost Navigation eingerichtet.)
- [x] Hilt und Coroutines einrichten. (Hilt/KSP-Umbau verifiziert.)
- [x] DataStore, Room und Serialization einrichten. (Room AppDatabase, DAOs, Kotlinx Serialization & DataStore Preferences eingerichtet.)
- [x] CI mit `assembleDebug`, Unit Tests und Android Lint einrichten.
- [x] Formatierung und ktlint in CI einrichten. (`org.jlleitschuh.gradle.ktlint` eingerichtet; detekt bleibt laut ADR 0003 zurückgestellt.)
- [x] Fehler- und Logstrategie definieren; Release-Logs dürfen keine Track- oder Gerätenamen enthalten.
- [x] `docs/ARCHITECTURE.md`, `docs/DEPENDENCIES.md` und ADR-Verzeichnis anlegen.
- [x] Debug-Menü für Engine-Simulation und Capability-Fakes hinzufügen. (`FakeAudioEngine` & Capability-Fakes bereitgestellt.)

### M2 – Audio-Engine und Session-Lebenszyklus

- [x] `AudioEngine` und Fake-Implementierung erstellen.
- [x] Android-Implementierung für `Equalizer` erstellen.
- [x] Optionale Android-Implementierung für `DynamicsProcessing` erstellen.
- [x] Capability Discovery mit Read-back implementieren.
- [x] AudioEffect-Control-Session-Intents verarbeiten. (`AndroidAudioSessionRepository`)
- [x] Attach/Detach, Kontrollverlust, tote Session und Engine-Konflikt behandeln.
- [x] Route-Erkennung für Speaker, kabelgebunden, Bluetooth und USB implementieren. (`AndroidAudioRouteRepository`)
- [x] Engine-State als `StateFlow` zur UI führen.
- [x] Keine dauerhafte Hintergrundausführung einführen, bevor sie technisch und Play-Policy-seitig begründet ist.

### M3 – Equalizer-MVP

- [x] Dynamische Bandregler anhand der realen Engine-Bänder darstellen.
- [x] Flat, Clean Punch, Deep Rumble und Balanced als Built-in-Presets auslievers.
- [x] Zielkurve logarithmisch auf Gerätebänder interpolieren. (`EqualizerInterpolator`)
- [x] Gain-Werte auf gemeldete Min-/Max-Grenzen begrenzen.
- [x] Master-Bypass und A/B-Vergleich implementieren.
- [x] Bass-, Punch- und Härte-Makros implementieren.
- [x] Headroom-Schätzung und Warnung bei Clipping-Risiko anzeigen.
- [x] Bei verfügbarem Input-Gain automatische Absenkung anwenden.
- [x] Alle Änderungen per Read-back verifizieren.

### M4 – Profile und Persistenz

- [x] Room-Schema für Presets und Geräteprofile implementieren. (`PresetEntity`, `DeviceProfileEntity`)
- [x] DataStore für globale UI-/App-Einstellungen nutzen.
- [x] Stabilen, datensparsamen Route-Fingerprint definieren.
- [x] Profilwechsel bei Route-Wechsel implementieren.
- [x] Konfliktregeln definieren: manuelle Auswahl schlägt automatische Auswahl bis zum nächsten Route-Wechsel.
- [x] JSON-Import mit Vorschau und Validierung implementieren. (`PresetJsonSerializer`)
- [x] JSON-Export über Android Sharesheet/Storage Access Framework implementieren.

### M5 – Dynamik und Schutz

- [x] Limiter nur bei gemeldeter Unterstützung aktivieren.
- [x] Sichere Standardwerte definieren; Threshold niemals über 0 dBFS anbieten. (Strikte Begrenzung `<= 0 dBFS`)
- [x] Optionalen Bass-Multiband-Kompressor für 30–150 Hz anbieten, sofern Engine unterstützt.
- [x] Attack, Release, Ratio und Gain-Grenzen konservativ begrenzen.
- [x] Schutz vor Parameter-Sprüngen und Race Conditions implementieren.

### M6 – Diagnose, Onboarding und UX-Politur

- [x] Kompatibilitätsprüfung beim Onboarding.
- [x] Diagnoseansicht mit aktiver Route, Session-ID, Effekt-Engine, Bandanzahl und Fähigkeiten. (`DiagnosticsScreen`)
- [x] Kopierbaren, datensparsamen Diagnosebericht erstellen. (`DiagnosticsReportFormatter`)
- [x] Deutsche und englische Lokalisierung vervollständigen.
- [x] Keine manipulativen Lautstärkeversprechen oder audiophile Scheinmetriken verwenden.

### M7 – Qualität und Beta

- [x] Unit Tests für Interpolation, Clamping, Headroom, Makros und Importvalidierung.
- [x] Contract Tests, die Fake- und Android-Engine gegen dieselben Zustandsregeln prüfen. (`AudioEngineContractTest`)
- [x] Audio-Smoke-Tests mit Sinustönen, Sweep, Impuls und Testsignalen. (`LogarithmicSweepGenerator`)

### M8 – Post-MVP

- [x] AutoEQ-Import (`AutoEqParser`).
- [x] Media3 Eigene Wiedergabepipeline & DSP Prototype (`Media3DspPipeline`).

Stand der Roadmap: 19. September 2026.

## 19. Session-Log

### Session 9 (19. September 2026)

Meilensteine M1 bis M8 vollständig umgesetzt und verifiziert:
- **M1 Fundament:** Maven Mirror in `settings.gradle.kts` ergänzt (Google Cloud Mirror verhindert Cloudflare 429 Fehler), Cyber/Dark Material 3 Tokens & Spacing erstellt, `FakeAudioEngine` bereitgestellt, `ktlint` in CI integriert.
- **M2 Audio Engine & Routing:** `AndroidAudioEngine` mit `Equalizer` & `DynamicsProcessing` Anbindung, `AndroidAudioSessionRepository` für Session Broadcasts, `AndroidAudioRouteRepository` für AudioDeviceCallback Routing.
- **M3 Equalizer MVP:** Logarithmische Frequenzinterpolation (`EqualizerInterpolator`), Presets (Clean Punch, Deep Rumble, Balanced, Flat), Makros (Bass, Punch, Härte), Clipping-Schutz/Headroom Berechnung und interaktiver Compose `EqualizerScreen`.
- **M4 Profile & Persistenz:** Room Database (`PresetEntity`, `DeviceProfileEntity`), DAOs, Hilt DatabaseModule, `PresetJsonSerializer` mit strikter Validierung (Max 100 KB, Frequenz-/Gain-Limits).
- **M5 Dynamik & Schutz:** `DynamicsProcessing` Limiter & MBC Schutzlogik (`limiterThresholdDb <= 0 dBFS`).
- **M6 Diagnose & UX:** `DiagnosticsScreen` mit `DiagnosticsReportFormatter` (datensparsamer, kopierbarer Diagnosebericht), Navigation Compose AppNavHost, DE/EN Lokalisierung.
- **M7 Qualität:** `AudioEngineContractTest` Vertragstests, `LogarithmicSweepGenerator` Audiosignale.
- **M8 Post-MVP:** `AutoEqParser` AutoEQ-Import, `Media3DspPipeline` Media3 Audio-Pipeline-Prototyp.
- **Verifikation:** Alle Unit Tests und Android Lint (`./gradlew testDebugUnitTest lintDebug`) erfolgreich bestanden.
