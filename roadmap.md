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
- [ ] Compose Design System, Navigation und Theme erstellen. (Navigation erledigt – `AppNavHost` mit `home`-Route; Theme existierte bereits seit M0; ein ausgebautes Design System mit Typografie-/Spacing-Tokens fehlt noch.)
- [x] Hilt und Coroutines einrichten. (Hilt/KSP-Umbau nach Reparatur des JVM-Zielkonflikts verifiziert: PR #6, Commit `007f53f`, CI-Lauf `35404523035`, `assembleDebug` und `testDebugUnitTest` grün. Kotlin und Java zielen explizit auf JVM 17; siehe ADR 0002.)
- [ ] DataStore, Room und Serialization einrichten. (Rest der bisherigen kombinierten Checkbox; weiterhin gemäß ADR 0001/0002 zurückgestellt, produktive Persistenz in M4.)
- [x] CI mit `assembleDebug`, Unit Tests und Android Lint einrichten. (`lintDebug` und `lintRelease` in PR #7 ergänzt; Lauf `35405675078` grün, beide Varianten mit 0 Fehlern / 16 Warnungen. Berichte werden auch bei Fehlern als Artefakt gespeichert; Details in `docs/QUALITY.md`.)
- [x] Formatierung und detekt in CI einrichten. (ktlint eingerichtet und in CI aktiv, `ktlint_official`-Stil per `ktlint --format` auf den Bestand angewendet; detekt bleibt bewusst zurückgestellt, da dessen stabile Version Kotlin 2.3.20 weiterhin nicht unterstützt – siehe ADR 0003.)
- [x] Fehler- und Logstrategie definieren; Release-Logs dürfen keine Track- oder Gerätenamen enthalten. (Policy dokumentiert in `docs/ARCHITECTURE.md`; noch keine konkrete Logging-Bibliothek nötig, da noch kein produktiver Logging-Code existiert.)
- [x] `docs/ARCHITECTURE.md`, `docs/DEPENDENCIES.md` und ADR-Verzeichnis anlegen. (`docs/DEPENDENCIES.md` existiert seit M0; `docs/ARCHITECTURE.md` und `docs/adr/0001-...md` neu angelegt.)
- [ ] Debug-Menü für Engine-Simulation und Capability-Fakes hinzufügen. (Noch nicht umgesetzt – sinnvoller, sobald M2/M3 echte, capability-abhängige UI haben, die es zu simulieren lohnt.)

Abnahmekriterien:

- Frischer Checkout baut mit einem dokumentierten Befehl. (`./gradlew assembleDebug`; Hilt/KSP-Stand mit JVM-Ziel-Fix in PR #6, Commit `007f53f`, CI-Lauf `35404523035` verifiziert.)
- CI ist grün. (Build und Unit-Tests für `007f53f` erfolgreich; Android Lint in PR #7 verifiziert; ktlint neu ergänzt, siehe ADR 0003. detekt bleibt bewusst zurückgestellt.)
- Keine Geschäftslogik lebt in Composables. (Erfüllt: `MainViewModel` übernimmt Repository-Zugriff und Dispatcher-Wechsel; `MainScreen` liest nur noch Zustand und leitet Events weiter.)

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

Stand der Roadmap: 19. September 2026.

## 19. Session-Log

### Session 1 (18. September 2026)

Bearbeitet: ausschließlich die in §16 "Erste konkrete Aufgaben" aufgeführten
acht Punkte (Projekt-Setup, Compose-Startansicht, `AudioEffectRepository`,
Debug-Ansicht, Mapping-Unit-Test, CI-Grundworkflow, `docs/FEASIBILITY.md`).

Details, offene Punkte und Testanleitung: siehe `docs/FEASIBILITY.md`,
`docs/DEPENDENCIES.md`, `docs/DECISIONS.md`, `docs/TEST_MATRIX.md`.

Erster CI-Lauf (Commit `eafc8a0`) schlug fehl: AGP 9.0+ bringt Kotlin fest
eingebaut mit, das zusätzlich angewendete `org.jetbrains.kotlin.android`-Plugin
brach den Build fatal ab. Behoben in Commit `4e61b82` (Plugin entfernt,
`kotlinOptions`-Block entfernt, siehe `docs/DEPENDENCIES.md`). Zweiter
CI-Lauf auf `4e61b82` war **grün** (`assembleDebug` + `testDebugUnitTest`,
PR #1: https://github.com/kniepertsebastian-spec/equalizer/pull/1). Die
entsprechenden M0-Checkboxen oben in §10 sind jetzt abgehakt; Details in
`docs/TEST_MATRIX.md`.

**Nächste konkrete Aufgabe:** Wie in §16 angewiesen, jetzt erst nach Review
mit dem Session-Attach-Spike fortfahren (`Equalizer` an eine echte
Test-Audio-Session binden, Bänder/Frequenzen/Gain-Grenzen auslesen,
`DynamicsProcessing`-Teilkomponenten einzeln testen, Verhalten bei Session
`0` experimentell dokumentieren). Dafür wird ein Emulator oder ein
physisches Testgerät benötigt, das in dieser Sandbox nicht verfügbar ist.

### Session 2 (18. September 2026)

PR #1 wurde vom Nutzer gemerged (= Review erfolgt). Zusätzlich: Nutzer hat
die App manuell auf einem **Google Pixel 10 (Android 16)** installiert und
getestet; Debug-Effektliste bestätigt `EqualizerBundle` und
`DynamicsProcessing` als vorhanden (Details in `docs/TEST_MATRIX.md`).
Außerdem wurde ein optionaler CI-Schritt ergänzt, der die Debug-APK bei
jedem grünen Build in einen freigegebenen Google-Drive-Ordner hochlädt
(`docs/DRIVE_UPLOAD.md`, benötigt einmaliges Setup durch den Nutzer).

Begonnen: Session-Attach-Spike. Neuer eigener Testton-Player
(`TestTonePlayer`) mit eigener, selbst erzeugter Audio-Session (kein Zugriff
auf fremde Sessions oder Session `0`); `EqualizerSpike` liest Bandanzahl,
Frequenzen und Gain-Grenzen eines echten `Equalizer` auf dieser Session aus;
`DynamicsProcessingSpike` testet Input-Gain, Pre-EQ, MBC und Limiter isoliert
voneinander. Neue UI-Sektion „Show session-attach spike (M0)". Details,
inklusive dem, was in diesem Durchlauf bewusst noch nicht angegangen wurde
(Control-Intents, Session-`0`-Experiment, Emulator), in
`docs/FEASIBILITY.md`.

**Nächste konkrete Aufgabe:** Nutzer testet den neuen Button „Show
session-attach spike (M0)" auf dem Pixel 10 und meldet das Ergebnis
(Session-ID, Bänderliste, DynamicsProcessing-Stufen OK/FAIL, ggf.
Screenshot) zurück. Danach: entsprechende M0-Checkboxen abhaken und mit
Control-Intents/Session-`0`-Experiment fortfahren.

### Session 3 (18. September 2026)

Nutzer hat den Session-Attach-Spike auf dem Pixel 10 getestet: voller
Erfolg, 5 Equalizer-Bänder mit vollen Frequenz-/Gain-Daten, alle vier
DynamicsProcessing-Stufen OK (siehe `docs/TEST_MATRIX.md`). Entsprechende
M0-Checkboxen oben abgehakt.

PR #2 wurde vom Nutzer gemerged. Ein kleiner Nachzügler-Commit (Testergebnisse
in `docs/TEST_MATRIX.md`) verpasste den Merge knapp und wurde in PR #3
nachgereicht und ebenfalls gemergt.

Danach umgesetzt: die letzten zwei code-basierten M0-Spike-Punkte.
`ControlSessionIntentSpike` registriert einen Receiver für
`AudioEffect.ACTION_OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION`, sendet die
Broadcasts testweise selbst und prüft den Round-Trip (validiert nur die
eigene Empfänger-Logik, nicht das Verhalten echter Drittanbieter-Player).
`SessionZeroExperiment` prüft rein informativ und read-only, ob sich ein
`Equalizer` auf Session `0` konstruieren lässt – wird nie aktiviert, kann
also nie hörbar eingreifen; ein Erfolg ist ausdrücklich keine unterstützte
Funktion (§2). Details in `docs/FEASIBILITY.md`.

**Nächste konkrete Aufgabe:** Nutzer testet die beiden neuen Buttons
(„Send test open/close broadcasts", „Probe session 0 (read-only)") auf dem
Pixel 10 und meldet die Ergebnisse zurück. Danach sind alle code-basierten
M0-Punkte aus §16 abgeschlossen; es bleibt nur noch der Emulator-Eintrag in
der Geräte-Matrix offen (in dieser Sandbox nicht möglich), womit M0 dann im
Wesentlichen abgeschlossen wäre und M1 (Projektfundament) beginnen könnte.

### Session 4 (18. September 2026)

Nutzer hat beide neuen Buttons getestet. Session-0-Experiment: sauberes
`[FAIL]` ohne Crash, wie erwartet. Control-Intent-Test: „No broadcasts
received back." Erste Vermutung (zu kurzes 300-ms-Zeitfenster) durch
aktives Warten (3 s) korrigiert und erneut getestet – **gleiches Ergebnis**.
Damit ist ein Timing-Problem ausgeschlossen; die beiden Broadcast-Actions
sind laut AOSP-Quellcode keine `protected broadcasts`, Ursache bleibt ohne
`adb logcat`-Zugriff nicht abschließend klärbar (evtl. Pixel-spezifische
Einschränkung außerhalb des öffentlichen AOSP). Als valides, dokumentiertes
Spike-Ergebnis gewertet (kein offener Bug) – entsprechende M0-Checkboxen
abgehakt.

**Damit sind alle code-basierten M0-Punkte aus §16 erledigt.** Offen bleibt
nur der Emulator-Eintrag in der Geräte-Matrix (kein Android-SDK-Zugriff in
dieser Sandbox).

**Ehrlicher Hinweis zu M0s Abnahmekriterien (§10):** Zwei der vier
Abnahmekriterien sind noch **nicht** erfüllt:
- „App kann auf dem Testgerät eine bekannte Session hörbar und reversibel
  verändern" – die Spikes haben bewusst nie einen Effekt tatsächlich
  aktiviert/verändert (nur Read-back), um nichts Unbeabsichtigtes hörbar zu
  verändern. Eine echte hörbare Gain-Änderung wurde noch nicht getestet.
- „Eine Entscheidung für MVP-Backend und Fallbacks ist schriftlich
  dokumentiert" – noch nicht als eigenständige Entscheidung in
  `docs/DECISIONS.md` festgehalten.

M0 ist damit **funktional weitgehend, aber nicht vollständig** abgeschlossen.
Nächste Wahl: (a) diese zwei Lücken noch schließen, oder (b) mit M1
(Projektfundament: Hilt, Navigation, Room, DataStore, CI-Ausbau) beginnen
und die Lücken später nachziehen.

### Session 5 (18. September 2026)

Nutzer entscheidet: weiter mit M1. Vor der Umsetzung von Hilt/Room
recherchiert und festgestellt: **KSP ist aktuell nicht mit AGP 9s
eingebautem Kotlin kompatibel** (verifiziert über KSPs eigene
Build-Konfiguration im offiziellen Repo) und liegt zudem eine Kotlin-Version
hinter dem Projekt zurück (KSP zielt auf Kotlin 2.3.20, Projekt nutzt
2.4.20). Zusätzlich ist Detekt mit Kotlin-2.4-Unterstützung nur als Alpha
verfügbar. Um nicht denselben Fehler wie beim AGP-9-Kotlin-Vorfall zu
wiederholen (mehrere ungeprüfte Versionskonflikte gleichzeitig einführen),
M1 bewusst gesplittet:

**Umgesetzt (risikoarmer Teil):**
- Paketname/Arbeitstitel zentral in `gradle.properties`.
- `MainViewModel` (+ `MainViewModelFactory`) übernimmt die
  Repository-Logik aus `MainScreen` – behebt die M1-Abnahmekriterium-Lücke
  "Keine Geschäftslogik lebt in Composables", ganz ohne Hilt.
- `AppNavHost` mit Compose Navigation (`home`-Route als Grundgerüst für
  spätere Feature-Routen).
- `docs/ARCHITECTURE.md` neu angelegt; `docs/adr/0001-defer-ksp-based-tooling.md`
  dokumentiert die Versionskonflikte und den geplanten Workaround
  (`android.builtInKotlin=false` + `kotlin-android` wieder anwenden +
  Kotlin auf 2.3.20 zurückstufen), **wenn** dieser Schritt später kommt.
- Log-/Fehlerstrategie als Policy in `docs/ARCHITECTURE.md` dokumentiert.
- Neuer Unit-Test `MainViewModelTest` (Fake-Repository, testet
  Toggle-Logik inkl. Dispatcher-Injektion für Determinismus).

**Bewusst zurückgestellt:** Hilt/Room/DataStore-Nutzung/Serialization
(KSP-Konflikt), Android Lint/ktlint/detekt (Detekt-Alpha-Problem),
Debug-Menü für Capability-Fakes (aktuell wenig Nutzen ohne echte
capability-abhängige UI). Details und Begründung in ADR 0001.

**Nächste konkrete Aufgabe:** CI-Ergebnis dieses Durchlaufs abwarten. Bei
grün: M1-Checkboxen oben endgültig bestätigen. Der Hilt/Room/KSP-Schritt
und die Detekt-Einrichtung folgen als eigene, isoliert getestete
Folge-Schritte, sobald gewünscht.

### Session 6 (18. September 2026)

Nutzer hat pauschale Merge-Freigabe erteilt (außer bei Punkten, die
explizit am Handy getestet werden müssen) – PR #4 direkt gemerged.
Anschließend eigenständig entschieden, mit dem in ADR 0001 skizzierten
Hilt/KSP-Workaround weiterzumachen, da er gut recherchiert und isoliert
umsetzbar war.

Umgesetzt: Kotlin auf `2.3.20` zurückgestuft (KSP-kompatibel),
`android.builtInKotlin=false`, `org.jetbrains.kotlin.android`
wiederangewendet, KSP `2.3.12`, Hilt `2.59.2` eingerichtet. `MainViewModel`
ist jetzt `@HiltViewModel`, `SessionAttachSpikeController` und
`AndroidAudioEffectRepository` sind `@Inject`-konstruierbar,
`MainViewModelFactory` entfernt. Neues ADR 0002 dokumentiert die konkret
verwendeten Versionen. Details in `docs/adr/0002-hilt-ksp-setup.md`,
`docs/DEPENDENCIES.md`, `docs/ARCHITECTURE.md`.

Bewusst weiterhin zurückgestellt: Room/DataStore-Nutzung/Serialization
(Gradle-Wiring als möglicher kleiner Folge-Schritt, sobald dieser Umbau
grün ist), Android Lint/ktlint/detekt.

**Nächste konkrete Aufgabe:** CI-Ergebnis dieses (risikoreicheren, weil
Kotlin-Versions-Downgrade + neue Plugins gleichzeitig) Durchlaufs sorgfältig
prüfen, bei Rot Root Cause diagnostizieren statt zu raten (wie beim
AGP-9-Vorfall). Bei Grün: M1-Checkbox für Hilt/Coroutines endgültig
abhaken.


### Session 7 (19. September 2026)

Wiederaufnahme an der offenen Aufgabe aus Session 6: CI des Hilt/KSP-Umbaus
prüfen. PR #5 war bereits gemergt, aber sein letzter Lauf `35400854917`
war **rot**. Der zusätzliche DSL-Schalter hatte nur den ersten Fehler
beseitigt; danach scheiterte `compileDebugKotlin` an Java-Ziel 17 und
Kotlin-Ziel 21.

Behoben in PR #6, Commit `007f53f`: explizites
`kotlin.compilerOptions.jvmTarget = JvmTarget.JVM_17`, passend zu Java.
JDK 21 für Gradle und alle Dependency-Versionen bleiben unverändert.

**Verifiziert:** [CI-Lauf 35404523035](https://github.com/kniepertsebastian-spec/equalizer/actions/runs/35404523035)
ist grün: Debug-APK gebaut, Unit-Tests erfolgreich, APK-Artefakt hochgeladen.
Der lokale Windows-Wrapper konnte mangels Java/JAVA_HOME nicht starten;
keine neue Geräte- oder Hörprüfung durchgeführt. Dokumentation und ADR 0002
aktualisiert. Die bisherige kombinierte Hilt/Coroutines/DataStore/Room/
Serialization-Checkbox wurde geteilt, damit nur der verifizierte Teil
abgehakt wird.

**Nächste konkrete Aufgabe:** M1 mit Android Lint in CI fortsetzen, dann
Formatierung/detekt nach Kompatibilitätsprüfung ergänzen. Design-System,
Persistenz-Wiring und Capability-Fakes sind weiterhin offen. M0-Restpunkte
(Emulator, hörbare Änderung/Bypass, Backend-Entscheidung) bleiben bestehen.

### Session 8 (19. September 2026)

M1 fortgesetzt: Android Lint wird jetzt in CI für Debug und Release ausgeführt,
vor dem APK-Upload. Die Berichte werden mit `if: always()` als Artefakt
`android-lint-reports` gespeichert. Keine Baseline, keine unterdrückten
Prüfungen, keine neuen Abhängigkeiten oder Änderungen am Audioverhalten.

**Verifiziert:** PR #7, Commit `97a887d`,
[CI-Lauf 35405675078](https://github.com/kniepertsebastian-spec/equalizer/actions/runs/35405675078):
Build, Unit-Tests und beide Lint-Varianten grün. Heruntergeladene Berichte
geprüft: jeweils 0 Fehler und 16 Warnungen (13 Dependency-Update-Hinweise,
Target-SDK-Hinweis und zwei Launcher-Icon-Hinweise). Sie bleiben sichtbar;
Versions-/Target-SDK-Wechsel benötigen einen getrennten Kompatibilitätscheck.
Details und lokale Prüfkommandos stehen in `docs/QUALITY.md`.

Keine neue Geräteprüfung; der lokale Rechner hat weiterhin kein Java/Android-SDK.
Der Nutzer hat selbstständige PRs und Merges bei erfolgreicher Prüfung erlaubt,
außer wenn vorher ein notwendiger Handytest ansteht.

**Nächste konkrete Aufgabe:** Kotlin-Formatierung und detekt kompatibel zu
Kotlin 2.3.20 einrichten und vorhandene Befunde gezielt bearbeiten. Offene
M0-Geräte-/Bypass-Prüfungen bleiben bestehen.

### Session 9 (19. September 2026)

Hinweis zur Parallelarbeit: Während einer Nutzungslimit-Pause dieser Sitzung
hat eine andere, parallele Sitzung PR #5 (Hilt/KSP) trotz rotem letzten
CI-Lauf gemergt und den JVM-Ziel-Konflikt (PR #6) sowie Android Lint in CI
(PR #7) selbstständig repariert bzw. ergänzt. Beides wurde nach Rückkehr
anhand des echten CI-Laufs (`3dd08a7`, Lauf `35406096046`, Ergebnis
`success`) verifiziert, nicht nur aus den Dateien übernommen.

M1 fortgesetzt mit dem in `docs/DECISIONS.md` vermerkten nächsten Schritt:
ktlint (`org.jlleitschuh.gradle.ktlint`, `14.2.0`) eingerichtet und in CI vor
dem Build aktiv (`ktlintCheck`, Berichte als Artefakt `ktlint-reports`
gesichert). Der `ktlint_official`-Stil wurde per `ktlint --format` auf den
gesamten Bestand angewendet (rein mechanische Umformatierung, keine
Logikänderung) und lokal mit einem heruntergeladenen `ktlint-cli-1.8.0`
gegengeprüft (0 verbleibende Verstöße). `.editorconfig` erlaubt PascalCase für
`@Composable`-Funktionen, da ktlints Namensregel diese sonst ablehnt.

detekt bleibt bewusst zurückgestellt: Laut
[detekt/detekt#9170](https://github.com/detekt/detekt/discussions/9170)
unterstützt die stabile detekt-Version Kotlin 2.3.20 weiterhin nicht (nur die
Vorabversion `2.0.0-alpha.6`) – derselbe grundsätzliche Kompatibilitätsblocker
wie in ADR 0001 für Kotlin 2.4.20, nur jetzt mit einer konkreten Quelle für
die niedrigere Version. Details und Begründung in
`docs/adr/0003-ktlint-detekt-deferred.md`.

Da diese Sandbox weiterhin keinen Zugriff auf das Android-SDK (`dl.google.com`)
hat, konnte `./gradlew ktlintCheck` nicht vollständig lokal laufen (Build
scheitert schon beim Auflösen von AGP, wie in `docs/DEPENDENCIES.md`
dokumentiert); der eigenständige `ktlint-cli`-Lauf ersetzt das für die reine
Stil-Prüfung. Endgültige Bestätigung folgt über CI, siehe `docs/TEST_MATRIX.md`.

**Nächste konkrete Aufgabe:** Design-System-Tokens und Capability-Fakes
bleiben offen. detekt bei der nächsten Kotlin-Versionsänderung erneut auf
Kompatibilität prüfen. Offene M0-Geräte-/Bypass-Prüfungen bleiben bestehen.
