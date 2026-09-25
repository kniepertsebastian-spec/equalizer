# Roadmap: Bass-orientierte Equalizer-App für Android

> **Ab 23. September 2026 gilt `roadmap-2026.md` als aktuelle Produkt- und
> Technik-Roadmap** (Meilensteine, Architekturzielbild, Prioritäten). Diese
> Datei bleibt das **Entwicklungs-Session-Log** mit der vollständigen
> bisherigen Historie (siehe „Session-Log" unten) – bestehende Verweise wie
> „roadmap.md Session 16" oder „roadmap.md §2/§3" bleiben dadurch gültig.
> Neue Session-Log-Einträge gehören weiterhin hierher.

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
- [x] Compose Design System, Navigation und Theme erstellen. (Navigation seit M0/`AppNavHost`; Cyber/Dark-Material-3-Farbtokens (`Color.kt`) und `Spacing`-Tokens in Session 10 ergänzt und im Code-Review verifiziert – `MaterialTheme.spacing` wird tatsächlich in `EqualizerScreen`/`DiagnosticsScreen` verwendet, nicht nur definiert.)
- [x] Hilt und Coroutines einrichten. (Hilt/KSP-Umbau nach Reparatur des JVM-Zielkonflikts verifiziert: PR #6, Commit `007f53f`, CI-Lauf `35404523035`, `assembleDebug` und `testDebugUnitTest` grün. Kotlin und Java zielen explizit auf JVM 17; siehe ADR 0002.)
- [x] DataStore, Room und Serialization einrichten. (Wie in M1 vorgesehen nur die Gradle-Einrichtung: Room `AppDatabase`/DAOs/Entities, `kotlinx-serialization-json` und die DataStore-Preferences-Abhängigkeit sind vorhanden und bauen. Die DataStore-Abhängigkeit hat im Code-Review keine einzige Verwendungsstelle – das ist für M1 in Ordnung, siehe aber M4, wo die tatsächliche Nutzung als nicht erledigt zurückgestuft wurde.)
- [x] CI mit `assembleDebug`, Unit Tests und Android Lint einrichten. (`lintDebug` und `lintRelease` in PR #7 ergänzt; Lauf `35405675078` grün, beide Varianten mit 0 Fehlern / 16 Warnungen. Berichte werden auch bei Fehlern als Artefakt gespeichert; Details in `docs/QUALITY.md`.)
- [x] Formatierung und detekt in CI einrichten. (ktlint eingerichtet und in CI aktiv, `ktlint_official`-Stil per `ktlint --format` auf den Bestand angewendet; detekt bleibt bewusst zurückgestellt, da dessen stabile Version Kotlin 2.3.20 weiterhin nicht unterstützt – siehe ADR 0003.)
- [x] Fehler- und Logstrategie definieren; Release-Logs dürfen keine Track- oder Gerätenamen enthalten. (Policy dokumentiert in `docs/ARCHITECTURE.md`; noch keine konkrete Logging-Bibliothek nötig, da noch kein produktiver Logging-Code existiert.)
- [x] `docs/ARCHITECTURE.md`, `docs/DEPENDENCIES.md` und ADR-Verzeichnis anlegen. (`docs/DEPENDENCIES.md` existiert seit M0; `docs/ARCHITECTURE.md` und `docs/adr/0001-...md` neu angelegt.)
- [ ] Debug-Menü für Engine-Simulation und Capability-Fakes hinzufügen. (`FakeAudioEngine` existiert, wird laut Code-Review von Session 10 aber nirgends aus der App heraus gebunden oder erreichbar gemacht – `AudioModule` bindet immer `AndroidAudioEngine`. Aktuell nur als Test-Double in Unit-Tests genutzt, kein echtes Debug-Menü.)

Abnahmekriterien:

- Frischer Checkout baut mit einem dokumentierten Befehl. (`./gradlew assembleDebug`; Hilt/KSP-Stand mit JVM-Ziel-Fix in PR #6, Commit `007f53f`, CI-Lauf `35404523035` verifiziert.)
- CI ist grün. (Build und Unit-Tests für `007f53f` erfolgreich; Android Lint in PR #7 verifiziert; ktlint neu ergänzt, siehe ADR 0003. detekt bleibt bewusst zurückgestellt.)
- Keine Geschäftslogik lebt in Composables. (Erfüllt: `MainViewModel` übernimmt Repository-Zugriff und Dispatcher-Wechsel; `MainScreen` liest nur noch Zustand und leitet Events weiter.)

### M2 – Audio-Engine und Session-Lebenszyklus

- [x] `AudioEngine` und Fake-Implementierung erstellen.
- [x] Android-Implementierung für `Equalizer` erstellen.
- [x] Optionale Android-Implementierung für `DynamicsProcessing` erstellen. (Nur der Limiter ist tatsächlich konfiguriert; PreEQ/MBC/PostEQ wurden im Code-Review von Session 10 bewusst aus der angeforderten Konfiguration entfernt, siehe M5.)
- [x] Capability Discovery mit Read-back implementieren.
- [x] AudioEffect-Control-Session-Intents verarbeiten. (`AndroidAudioSessionRepository`) **Wichtiger Vorbehalt:** Der M0-Spike hat genau diesen Broadcast-Mechanismus (`ACTION_OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION`) bereits als auf dem Testgerät unzuverlässig dokumentiert (`docs/TEST_MATRIX.md`, Session 4/Control-Intent-Test) – selbst gesendete Broadcasts kamen nicht beim eigenen Empfänger an. Diese M2-Implementierung wurde **nicht** erneut auf einem echten Gerät mit einem echten Drittanbieter-Player verifiziert; ob sie in der Praxis funktioniert, ist offen.
- [ ] Attach/Detach, Kontrollverlust, tote Session und Engine-Konflikt behandeln. (Attach/Detach vorhanden; `AudioEngineState.LostControl` wird bei einem Fehler gesetzt, aber nichts versucht danach automatisch ein Recovery/Re-Attach. Nicht im Detail auditiert.)
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
- [ ] Bei verfügbarem Input-Gain automatische Absenkung anwenden. (`EqualizerInterpolator.calculateHeadroom()` berechnet nur einen *empfohlenen* Wert für die Anzeige; nirgends im Code wird `dp.setInputGainAllChannelsTo(...)` o. ä. aufgerufen. Die Absenkung wird also nicht angewendet, nur vorgeschlagen.)
- [x] Alle Änderungen per Read-back verifizieren.

### M4 – Profile und Persistenz

- [x] Room-Schema für Presets und Geräteprofile implementieren. (`PresetEntity`, `DeviceProfileEntity`, DAOs, `AppDatabase` – die Schema-Definitionen existieren.)
- [ ] DataStore für globale UI-/App-Einstellungen nutzen. (Abhängigkeit eingebunden, aber im Code-Review keine einzige Verwendungsstelle gefunden – `grep` nach `dataStore`/`preferencesDataStore` im Quellcode ergibt nichts.)
- [ ] Stabilen, datensparsamen Route-Fingerprint definieren. (Keine entsprechende Klasse/Funktion im Code gefunden.)
- [ ] Profilwechsel bei Route-Wechsel implementieren. (Keine `ProfileRepository`/`DeviceProfileRepository` oder vergleichbare Klasse verwendet die DAOs; `AudioRouteRepository` meldet Route-Wechsel, aber nichts reagiert darauf mit einem Profilwechsel.)
- [ ] Konfliktregeln definieren: manuelle Auswahl schlägt automatische Auswahl bis zum nächsten Route-Wechsel. (Ohne Profilwechsel-Logik gegenstandslos – nicht umgesetzt.)
- [ ] JSON-Import mit Vorschau und Validierung implementieren. (`PresetJsonSerializer` existiert und ist unit-getestet – Größenlimit, Schema-Version, Frequenz-/Gain-Grenzen –, wird aber von keiner UI oder ViewModel-Methode aufgerufen. Kein Import-Button, keine Vorschau.)
- [ ] JSON-Export über Android Sharesheet/Storage Access Framework implementieren. (Kein Sharesheet-/SAF-Code im Projekt; `exportToJson()` wird nirgends aufgerufen.)

### M5 – Dynamik und Schutz

- [x] Limiter nur bei gemeldeter Unterstützung aktivieren.
- [x] Sichere Standardwerte definieren; Threshold niemals über 0 dBFS anbieten. (Strikte Begrenzung `<= 0 dBFS`; im Code-Review von Session 10 zusätzlich einen Bug behoben, bei dem `limiterEnabled = false` den bereits aktiven Limiter nicht wirklich abschaltete.)
- [ ] Optionalen Bass-Multiband-Kompressor für 30–150 Hz anbieten, sofern Engine unterstützt. (War in der `DynamicsProcessing.Config` strukturell aktiv, aber nirgends konfiguriert – lief mit undokumentierten Werkseinstellungen des jeweiligen Geräts. Im Code-Review von Session 10 bewusst aus der Konfiguration entfernt (`mbcInUse = false`), bis eine echte Konfiguration/UI existiert; `hasMbc` meldet jetzt korrekt `false`.)
- [x] Attack, Release, Ratio und Gain-Grenzen konservativ begrenzen. (Nur für den tatsächlich konfigurierten Limiter zutreffend.)
- [x] Schutz vor Parameter-Sprüngen und Race Conditions implementieren. (War **nicht** umgesetzt – `attach()`/`detach()`/`apply()` liefen ohne jede Synchronisierung nebeneinander her. Im Code-Review von Session 10 mit einem `Mutex` nachgerüstet, analog zum Muster aus `SessionAttachSpikeController`.)

### M6 – Diagnose, Onboarding und UX-Politur

- [ ] Kompatibilitätsprüfung beim Onboarding. (Kein Onboarding-Flow im Code; sogar der bisherige M0/M1-Platzhalter-Chip für den Kompatibilitätsstatus wurde beim Umbau von `MainScreen` ersatzlos entfernt.)
- [x] Diagnoseansicht mit aktiver Route, Session-ID, Effekt-Engine, Bandanzahl und Fähigkeiten. (`DiagnosticsScreen`)
- [x] Kopierbaren, datensparsamen Diagnosebericht erstellen. (`DiagnosticsReportFormatter`)
- [ ] Deutsche und englische Lokalisierung vervollständigen. (Alle neuen Strings in `DiagnosticsScreen.kt`, `EqualizerScreen.kt` und Teilen von `MainScreen.kt` sind hartkodierte deutsche Literale statt `stringResource(...)` – auf einem englischsprachigen Gerät bleiben sie deutsch. Nicht in diesem Review behoben, da es sich um eine größere, eigenständige Lokalisierungsaufgabe handelt.)
- [x] Keine manipulativen Lautstärkeversprechen oder audiophile Scheinmetriken verwenden.

### M7 – Qualität und Beta

- [x] Unit Tests für Interpolation, Clamping, Headroom, Makros und Importvalidierung.
- [ ] Contract Tests, die Fake- und Android-Engine gegen dieselben Zustandsregeln prüfen. (`AudioEngineContractTest` testet ausschließlich `FakeAudioEngine`; `AndroidAudioEngine` kommt darin nicht vor – naheliegend, da es echte Android-Media-Klassen braucht und ohne Instrumented-/Robolectric-Test nicht im reinen JVM-Unit-Test läuft, aber die Behauptung "Fake- und Android-Engine" stimmt so nicht.)
- [ ] Audio-Smoke-Tests mit Sinustönen, Sweep, Impuls und Testsignalen. (Nur Sweep (`LogarithmicSweepGenerator`) und der bereits aus M0 vorhandene Sinuston (`SineWaveGenerator`) sind vorhanden; kein Impulssignal-Generator/-Test gefunden.)

### M8 – Post-MVP

- [x] AutoEQ-Import (`AutoEqParser`).
- [x] Media3 Eigene Wiedergabepipeline & DSP Prototype (`Media3DspPipeline`).

Stand der Roadmap: 19. September 2026. **Nicht "durch":** Das Code-Review in
Session 10 hat einen wiederkehrenden Musters aus PR #9 aufgedeckt – viele
Checkboxen waren als erledigt markiert, obwohl der zugehörige Code entweder
gar nicht aus der UI erreichbar war (Room/DataStore/JSON-Import-Export,
Onboarding, Debug-Menü) oder aktiv, aber unkonfiguriert lief (MBC-Band). Die
Korrekturen dazu stehen bei den jeweiligen Checkboxen und im Session-10-Log.

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

### Session 10 (19. September 2026)

PR #9 ("Jules", automatisch über einen vom Nutzer gestarteten Task erstellt)
behauptet, M1 bis M8 vollständig umzusetzen: `AndroidAudioEngine`
(Equalizer/DynamicsProcessing), `AudioSessionRepository`/`AudioRouteRepository`,
`EqualizerInterpolator` mit Presets und Makros, Room-Persistenz
(`PresetEntity`, `DeviceProfileEntity`), `PresetJsonSerializer`,
Limiter-/Kompressor-Sicherheitsgrenzen, `DiagnosticsScreen`, sowie
AutoEQ-Import und einen Media3-DSP-Prototyp.

Der PR basierte auf einem älteren `main`-Stand (vor PR #8/ktlint) und hatte
Merge-Konflikte gegen den aktuellen `main`. Konflikte in `app/build.gradle.kts`,
`gradle/libs.versions.toml`, `MainScreen.kt`, `MainViewModel.kt`,
`MainViewModelTest.kt` und dieser Datei wurden per Merge-Commit aufgelöst
(nicht per Rebase, um die fremde Branch-Historie nicht umzuschreiben); dabei
wurde bewusst kein bereits vorhandener Test verworfen (`toggling off hides
the list...` aus `main` wurde in die neue, um `AudioEngine`/`AudioSession`/
`AudioRoute` erweiterte Konstruktor-Signatur übernommen statt gelöscht).

**Code-Review durchgeführt** (kein Android-SDK in dieser Sandbox, siehe
`docs/DEPENDENCIES.md` – Review daher per Lesen/Grep/ktlint, nicht per
Emulator-Lauf). Ergebnis: ein wiederkehrendes Muster aus zu früh als "[x]"
markierten Punkten, bei denen entweder nur das Datenmodell/die Abhängigkeit
existiert, aber nichts sie aufruft, oder ein DSP-Stage strukturell aktiviert,
aber nie konfiguriert wurde. Alle betroffenen Checkboxen oben in M1–M7 wurden
entsprechend korrigiert (siehe dortige Begründungen).

**Gefundene und in diesem Review behobene Bugs:**
- `AppNavHost`: Die Diagnose-Route rief `hiltViewModel()` ohne gemeinsamen
  Scope auf und erzeugte damit eine zweite `MainViewModel`-Instanz. Deren
  `init{}` setzte Preset/Makros auf ihre Defaults zurück und wandte sie sofort
  auf die echte, geteilte `AndroidAudioEngine` an – ein Nutzer, der auf
  "Diagnose & Report" tippt, hätte seinen eingestellten EQ verloren. Beim
  Zurück-Navigieren stoppte die zweite Instanz zusätzlich dauerhaft
  `AudioSessionRepository`/`AudioRouteRepository` für den ganzen Prozess.
  Behoben durch eine einzige, oberhalb von `NavHost` gehaltene
  `MainViewModel`-Instanz für beide Routen.
- `AndroidAudioEngine`: `limiterEnabled = false` schaltete den bereits
  aktiven Limiter nicht ab (kein Code-Pfad dafür) – aktuell ohne UI-Zugriff
  auf dieses Feld, aber ein latenter Bug für die erste UI, die es exponiert.
  Behoben.
- `AndroidAudioEngine`: PreEQ/MBC/PostEQ wurden in der
  `DynamicsProcessing.Config` strukturell angefordert, aber nirgends
  konfiguriert – liefen mit unbekannten Geräte-Standardwerten, ein Verstoß
  gegen das eigene Projektprinzip "Jede DSP-Funktion braucht definierte
  Grenzen" (§1). Aus der Konfiguration entfernt, bis eine echte Implementierung
  existiert; `hasMbc` meldet jetzt korrekt `false`.
- `AndroidAudioEngine`: `attach()`/`detach()`/`apply()` liefen ohne jede
  Synchronisierung nebeneinander her, obwohl M5 "Schutz vor
  Parameter-Sprüngen und Race Conditions" als erledigt auswies. Mit einem
  `Mutex` nachgerüstet (analog zu `SessionAttachSpikeController` aus M0).
- `MainScreen`: Der M0-Debug-Einstieg für den Session-Attach-Spike
  (`SessionAttachSpikeSection`) war beim Umbau auf die neue Equalizer-UI
  ersatzlos verschwunden; der `spikeController`-Parameter war seither
  toter Code. Wiederhergestellt als Toggle-Button neben den anderen
  Debug-Werkzeugen.
- `gradle.properties`: Eine offensichtlich maschinenspezifische
  `systemProp.http.agent`-Zeile (Gradle/OS/JVM-Version einer fremden
  Sandbox) war eingecheckt. Entfernt.

**Gefundene, aber nicht in diesem Review behobene Probleme** (siehe die
jeweiligen Checkboxen oben für Details): M4-Persistenz komplett unverdrahtet
(Room/DataStore ohne Aufrufer, kein Route-Fingerprint/Profilwechsel, kein
JSON-Import/-Export in der UI); automatische Input-Gain-Absenkung wird nur
angezeigt, nie angewendet; kein Onboarding-Kompatibilitätscheck (nicht einmal
der alte M0-Platzhalter blieb erhalten); große Teile der deutschen UI-Texte
in `DiagnosticsScreen`/`EqualizerScreen` sind nicht lokalisiert; der
`AudioSessionRepository`-Broadcast-Mechanismus basiert auf genau den beiden
Broadcast-Actions, die der M0-Spike bereits als auf dem Testgerät unzuverlässig
dokumentiert hat (`docs/TEST_MATRIX.md`) – ohne erneuten Gerätetest bleibt
offen, ob M2 in der Praxis funktioniert.

**Nächste konkrete Aufgabe:** Vor einem Merge sollte der Nutzer diesen Stand
bewusst gegen die PR-Beschreibung ("M1 bis M6 vollständig") abwägen – die
Grundgerüste (Equalizer-DSP, Presets, Diagnose-UI, Limiter-Schutz) sind real
und größtenteils solide, aber "die Roadmap ist durch" trifft nicht zu: M4 ist
praktisch nicht begonnen, M6 nur teilweise. Empfehlung: PR mergen für den
soliden Kern (M2/M3-DSP, Diagnose), M4/Lokalisierung/Onboarding als eigene,
nachvollziehbare Folge-Schritte planen statt als bereits erledigt zu führen.

### Session 11 (19. September 2026)

Nutzer meldet zwei konkrete Probleme mit einem Screenshot: (1) Der Equalizer
erkennt die Audio-Session nicht, wenn Spotify vor HardBass EQ geöffnet und
gestartet wird; (2) die Hauptansicht wirkt "bugged" – die Debug-Buttonleiste
läuft über den Bildschirmrand hinaus. Zusätzliche Frage: lässt sich Uptempo
Hardcore mit vorhandenen/weiteren Features noch besser abbilden?

**Root Cause (1), keine neue Erkenntnis, sondern Bestätigung eines bereits in
Session 4 dokumentierten Befunds:** `AudioSessionRepository` registriert den
`ACTION_OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION`-Empfänger erst, wenn
`MainViewModel` erzeugt wird (App-UI wird geöffnet). Dieser Broadcast feuert
laut Android-Doku nur **einmal**, wenn der Player seine Session anlegt – i. d.
R. beim ersten Wiedergabestart nach Prozessstart des Players. Öffnet der
Nutzer zuerst Spotify und startet Wiedergabe, bevor HardBass EQ läuft, ist der
Broadcast unwiderruflich verpasst; es gibt keine öffentliche API, um aktive
Audio-Sessions anderer Apps nachträglich abzufragen. Der M0-Spike hatte zudem
bereits gezeigt, dass selbst *selbst gesendete* Broadcasts auf dem Pixel
10/Android 16 nicht beim eigenen Empfänger ankommen – der Mechanismus ist
also auch unabhängig vom Timing-Problem nicht zuverlässig. Die einzige robuste
Lösung wäre ein dauerhaft laufender Foreground-Service, der schon lauscht,
bevor der Player überhaupt startet – das widerspricht aber bewusst §6/M2
("keine dauerhaft laufende Foreground-Service-Lösung als Voreinstellung",
"nicht einführen, bevor sie technisch und Play-Policy-seitig begründet ist").
Das ist eine Produktentscheidung (Akku, Dauerbenachrichtigung, neue
Berechtigung) und wurde daher **nicht** eigenmächtig umgesetzt, sondern dem
Nutzer zur Entscheidung vorgelegt statt still implementiert oder ignoriert.
Stattdessen ergänzt: ein Hinweis-Card in `EqualizerScreen`, der bei Status
"Wartet auf Audio-Session" sichtbar wird und erklärt, warum das passiert und
was hilft (Titel pausieren/erneut abspielen, Player neu starten während
HardBass EQ offen bleibt).

**Root Cause (2), neuer Fund:** `MainScreen`s Debug-Werkzeugleiste
(„Diagnose & Report“ / „Audioeffekte anzeigen (Debug)“ / „Session-Attach-Spike
anzeigen (M0)“) steht in einer normalen `Row` ohne `horizontalScroll` oder
Umbruch. Mit den (im Screenshot sichtbaren) deutschen Strings aus
`values-de/strings.xml` passen nicht einmal zwei der drei Buttons vollständig
auf einen Bildschirm – der dritte Button war komplett unerreichbar, ohne dass
es einen sichtbaren Hinweis darauf gab. Gleiches latentes Problem in der
`Presets`-Card in `EqualizerScreen` (FilterChip-`Row` ohne Scroll), akut
relevant, weil dieser Review ein fünftes Preset ergänzt. Beide Zeilen sind
jetzt horizontal scrollbar (`Modifier.horizontalScroll(rememberScrollState())`).

**Uptempo-Hardcore-Feature:** Neues Built-in-Preset „Uptempo – Kick Attack“
(`BuiltInPresets.KickAttack`) ergänzt, gezielt auf den harten, extrem
transienten Kick und die "Screech"-Charakteristik von Uptempo Hardcore
zugeschnitten statt auf reinen Bass: strafferer Sub (50 Hz +3 dB), stärkerer
Kick-Punch (100 Hz +2,5 dB), tieferer Mud-Cut bei 250 Hz (−3 dB) für
Durchsetzungsfähigkeit im Wall-of-Sound-Mix, Anhebung bei 3,2 kHz (+1,5 dB)
für Kick-Attack-Definition und Screech-Durchsetzung, plus leichte Absenkung
bei 6–10 kHz, um die ohnehin meist schon sehr laut/hart gemasterten
Hardcore-Tracks nicht zusätzlich zu verschärfen. `mbcRatio` bewusst höher
(3.0) für strafferes Einfangen der repetitiven Kick-Transienten.

**Nicht umgesetzt, dem Nutzer zur Entscheidung vorgelegt:** dauerhafter
Foreground-Service für zuverlässigere Session-Erkennung (s. o.); ein
zusätzlicher Makroregler speziell für Kick-Attack/Screech statt nur über
Presets. Build weiterhin nicht lokal verifizierbar (kein Android-SDK-Zugriff
in dieser Sandbox, wie in allen vorherigen Sessions) – Verifikation über CI
und ggf. erneuten Gerätetest durch den Nutzer.

### Session 12 (19. September 2026)

Nutzer bestätigt: Session-11-Fixes wirken (PR #14 gemergt). Neuer Wunsch,
mit einem Ziel-Mockup-Screenshot belegt: Das Design sei "noch ziemlich
langweilig" – Icon-Grid mit neun Presets (3×3), dunkler Industrial-/
Brushed-Metal-Hintergrund mit roten Akzentlinien, abgerundete Karten mit
Rahmen. Der Screenshot zeigt vier Presets, die im Code noch nicht existieren
(„Hardcore – Raw Power“, „Frenchcore – Fast Attack“, „Terrorcore – Maximum
Distortion“, „Uptempo – Final Smash“) – laut Auftrag mit „vorhandener
Einstellung“, also im Stil der bestehenden Presets, zu ergänzen.

**Umgesetzt:**
- Vier neue Built-in-Presets nach dem Muster der bestehenden ergänzt
  (`BuiltInPresets.kt`): „Hardcore – Raw Power“ (grundsolide, weniger extrem
  als die Uptempo-Presets), „Frenchcore – Fast Attack“ (sehr starker,
  schneller Kick-Punch + `mbcRatio` 3.2 für knackige Transienten),
  „Terrorcore – Maximum Distortion“ (bewusst **negative** Härte + niedrigerer
  MBC-Threshold, weil Terrorcore-Quellmaterial bereits massiv verzerrt ist –
  hier soll gebändigt statt weiter verschärft werden) und „Uptempo – Final
  Smash“ (die extremste Uptempo-Variante: max. Sub, max. Kick-Punch, max.
  Präsenz). `BuiltInPresets.all` jetzt in exakt der Reihenfolge des
  Mockup-Grids (3×3).
- `androidx.compose.material:material-icons-extended` als neue Abhängigkeit
  ergänzt (`libs.versions.toml`/`app/build.gradle.kts`, über die bestehende
  Compose-BOM versioniert, keine neue KSP-/Annotation-Processor-Baustelle).
  Bisher gab es nur `material-icons-core` mit ca. 50 Basis-Icons – für die
  Preset-Icons (Faust, Welle, Blitz, Waage, Power-Symbol, Tacho, Verbotssymbol
  usw.) reicht das nicht.
- `EqualizerScreen.kt`: Presets-Zeile (Scroll-Row mit `FilterChip`s) durch ein
  3-spaltiges Icon-Grid ersetzt (`PresetGrid`/`PresetCard`, statisch in
  Dreier-Reihen gechunkt, kein `LazyVerticalGrid` nötig bei neun statischen
  Presets). Ausgewähltes Preset bekommt Akzent-Rahmen/-Hintergrund in
  Primärfarbe. Alle Cards bekommen einheitlich `RoundedCornerShape(20.dp)`
  und einen dezenten Rahmen (`HardBassCardBorder`) für den "Industrial-Panel"-
  Look aus dem Mockup.
- Neuer dunkler Hintergrund-Textur-Modifier
  (`ui/theme/Background.kt#hardBassIndustrialBackground`): Gradient plus
  gezeichnete diagonale "Brushed-Metal"-Streifen und ein paar spärliche
  orangene Akzentlinien, als Ersatz für das Rasterbild aus dem Mockup (kein
  Bild-Asset verfügbar/verifizierbar in dieser Sandbox). Angewendet in
  `MainActivity` (App-weite `Surface`, transparent gemacht) sowie in beiden
  `Scaffold`s (`MainScreen`, `DiagnosticsScreen`) über `containerColor =
  Color.Transparent`, damit die Textur durchscheint statt vom
  Scaffold-Hintergrund überdeckt zu werden.

**Bewusste Abweichung vom Mockup:** Kein Schädel-Icon für „Final Smash“
verwendet – es gibt keinen Schädel im (nicht-extended, klassischen)
Material-Icons-Set, das `material-icons-extended` bereitstellt. Stattdessen
`Icons.Filled.Whatshot` (Flamme) als "Maximum/Extrem"-Symbol. Ebenfalls nicht
übernommen: das im Mockup doppelt auftauchende "Grafischer EQ (0 Bänder)"-
Element (einmal in der Makro-Karte, einmal als eigene Karte darunter) – wirkt
wie ein Mockup-Artefakt und hätte keine sinnvolle Funktion; die reale Struktur
(ein "Grafischer EQ"-Card mit Bypass-Switch im Header) bleibt unverändert.

**Nicht erneut lokal verifizierbar** (weiterhin kein Android-SDK-Zugriff in
dieser Sandbox) – insbesondere die neue `drawBehind`-Textur und das Icon-Grid
sollten auf einem echten Gerät gegen den Mockup-Screenshot geprüft werden.
Verifikation über CI (Build/Unit-Tests/Lint/ktlint) plus Gerätetest durch den
Nutzer.

### Session 13 (19. September 2026)

Nutzer meldet: Equalizer klappt auch bei SoundCloud nicht, und bittet darum,
das diesmal „richtig" zu beheben statt nur mit einem Hinweistext zu arbeiten.
Bevor umgesetzt wurde: per `AskUserQuestion` explizit die in Session 11/12
aufgeschobene Entscheidung vorgelegt (dauerhafter Foreground-Service mit
Dauerbenachrichtigung/Akku-Kosten/neuer Berechtigung vs. nur Doku vs. erst
Gerätetest) – **Nutzer entscheidet sich für den Foreground-Service.**

**Wichtiger Vorbehalt vorab kommuniziert:** Das SoundCloud-Problem hat
vermutlich zwei Ursachen, von denen der Service nur eine sicher behebt. (1)
Timing: Der Broadcast feuert nur einmal beim Sessionstart – wenn HardBass EQ
noch nicht lief, ist er für immer verpasst. (2) Ungeklärt seit Session 4: Auf
dem Pixel 10/Android 16 kamen selbst **selbst gesendete** Test-Broadcasts nie
beim eigenen Empfänger an, auch nicht bei aktivem Warten – das deutet auf ein
tieferliegendes, möglicherweise geräte-/OS-spezifisches Zustellungsproblem
hin, das ein Service allein nicht lösen kann. Umgesetzt wird trotzdem, weil es
den einzig bekannten, seriösen nächsten Schritt darstellt und Punkt (1) real
behebt.

**Umgesetzt:**
- Neuer `AudioSessionForegroundService` (`service/AudioSessionForegroundService.kt`,
  `androidx.lifecycle.LifecycleService` + `@AndroidEntryPoint`, injiziert die
  bestehenden Singletons `AudioSessionRepository`/`AudioRouteRepository`/
  `AudioEngine`). Startet `sessionRepository.startListening()` und
  `routeRepository.startMonitoring()` in `onCreate()` und reagiert per
  `lifecycleScope`-Collector auf `activeSession`-Wechsel mit
  `audioEngine.attach()`/`detach()` – exakt die Logik, die vorher in
  `MainViewModel.init` lag, jetzt aber unabhängig vom UI-Lebenszyklus.
  `START_STICKY`, damit das System den Dienst nach einem Kill neu startet.
- `HardBassEqApplication.onCreate()` startet den Service sofort beim
  Prozessstart (`ContextCompat.startForegroundService`) – nicht erst wenn
  `MainActivity`/`MainViewModel` erzeugt werden. Das verkleinert das
  Zeitfenster, in dem ein Player-Broadcast verpasst werden kann, auf "Prozess
  noch nicht gestartet" statt "UI noch nicht geöffnet".
- `MainViewModel`: `sessionRepository`-Parameter komplett entfernt (wird nicht
  mehr gebraucht), `startListening/stopListening` und
  `routeRepository.startMonitoring/stopMonitoring` sowie der
  `activeSession`-Collector aus `init{}`/`onCleared()` entfernt – der Service
  ist jetzt alleiniger Owner dieses Lebenszyklus. `MainViewModelTest`
  entsprechend angepasst (`FakeAudioSessionRepository` entfernt, da ungenutzt).
- Persistente Low-Priority-Benachrichtigung (`IMPORTANCE_LOW`, kein Sound/
  Vibration) erklärt dem Nutzer, warum die App im Hintergrund läuft; Tippen
  öffnet `MainActivity`. Neues, aus dem vorhandenen App-Icon abgeleitetes
  monochromes Vektor-Icon (`ic_notification_eq.xml`), da Statusleisten-Icons
  reine weiße Silhouetten auf Transparenz brauchen.
- Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` und
  `POST_NOTIFICATIONS` ergänzt; `MainActivity` fragt `POST_NOTIFICATIONS` ab
  API 33 zur Laufzeit an (der Service funktioniert auch ohne die Berechtigung,
  nur die Benachrichtigung bliebe sonst unsichtbar).
  `foregroundServiceType="mediaPlayback"` gewählt, da kein FGS-Typ exakt
  "lauscht auf fremde Audio-Sessions" abbildet und vergleichbare veröffentlichte
  System-EQ-Apps (z. B. Wavelet) für genau diesen Zweck `mediaPlayback`
  verwenden statt der Play-Store-review-pflichtigen `specialUse`-Kategorie.
- Hinweistext in `EqualizerScreen.kt` aktualisiert: erklärt jetzt, dass die
  App auch im Hintergrund lauscht, und nennt die verbleibenden Fälle (Player
  lief schon vor Erstinstallation/letztem Neustart; ungeklärtes
  Zustellungsproblem), in denen Pausieren/Neustarten des Players weiterhin
  hilft.
- `androidx.lifecycle:lifecycle-service` als neue Abhängigkeit ergänzt (über
  die bereits gepinnte `lifecycle`-Version, kein neuer Versionskonflikt).

**Weiterhin nicht umgesetzt/offen:** Kein `BOOT_COMPLETED`-Empfänger – der
Service startet erst, wenn der Nutzer die App nach einem Geräteneustart
mindestens einmal öffnet, nicht automatisch beim Booten (das wäre eine
weitere, hier nicht angefragte Ausweitung). Ob Punkt (2) oben (mögliches
Zustellungsproblem) durch den Service behoben ist, lässt sich nur auf einem
echten Gerät klären – das ist der nächste Test, um den der Nutzer gebeten
werden sollte. Build weiterhin nicht lokal verifizierbar (kein
Android-SDK-Zugriff in dieser Sandbox) – Verifikation über CI plus
Gerätetest.

### Session 14 (22. September 2026)

Nutzer meldet nach dem Foreground-Service-Merge: SoundCloud klappt weiterhin
nicht, und zusätzlich wird die Musik durch HardBass EQ insgesamt **leiser**
statt druckvoller – während SoundCloud selbst (eigene Lautheits-Normalisierung/
Mastering) lauter *und* basslastiger klingt.

**Zwei getrennte Themen, nicht dasselbe Problem:**

1. **SoundCloud-Lautheit ist unabhängig von HardBass EQ.** Was der Nutzer bei
   SoundCloud hört, ist deren eigene, App-/serverseitige Lautheits-
   Normalisierung bzw. ein eigener Loudness-Maximizer auf ihrer Wiedergabe-
   Pipeline – das hat nichts mit Androids Session-basiertem `AudioEffect`
   zu tun, über das HardBass EQ arbeitet. Kein Code-Fund hierzu nötig, reine
   Erklärung an den Nutzer.

2. **"Equalizer wird leiser" ist ein echter, gefundener Gain-Staging-Bug in
   `AndroidAudioEngine.applyInternal()`**, unabhängig vom Session-Erkennungs-
   problem – tritt bei jedem Preset auf, sobald überhaupt eine Session
   angehängt ist:
   - Jedes Preset erzwingt einen festen negativen `inputGainDb`
     (`requestedHeadroomDb`, roadmap-konform 3–5,5 dB „Ziel-Headroom" – **nicht**
     der Bug, sondern bewusste Spezifikation aus §5).
   - Die `DynamicsProcessing.MbcBand`-Konfiguration setzte `preGain`/`postGain`
     aber fest auf `0f, 0f` – der Multiband-Kompressor senkt bei lauten
     Passagen (bei den Uptempo-/Hardcore-Presets praktisch dauerhaft, da die
     Schwellen niedrig sind) die Lautstärke weiter ab, **ohne** die übliche
     Kompressor-Makeup-Gain, die das kompensiert. In Kombination mit dem
     Input-Gain-Cut ergab das netto fast immer leiseres statt druckvolleres
     Ergebnis – das genaue Gegenteil vom Ziel der App.

**Fix:** `postGain` je MBC-Band nicht mehr `0f`, sondern eine konservative
Standard-Kompressor-Makeup-Gain-Heuristik
(`(-threshold) * (1 - 1/ratio) * 0.5`, gekappt auf 0–4 dB). Der Limiter danach
bleibt **unverändert** (weiterhin hartes 10:1-Verhältnis, Safe-Threshold ≤ 0
dBFS) – er fängt etwaige zusätzliche Pegelspitzen aus der Makeup-Gain weiterhin
ab, „Clipping-Schutz zuerst" bleibt also intakt. Die feste
`requestedHeadroomDb`-Sicherheitsmarge pro Preset wurde bewusst **nicht**
angetastet, da sie explizite Produktspezifikation aus §5 ist, nicht der
gefundene Bug.

**Weiterhin offen, an den Nutzer zurückgespielt:** Ob SoundCloud nach dem
Foreground-Service (Session 13) jetzt wenigstens den Status „Aktiv
(Session #…)" erreicht oder weiterhin dauerhaft bei „Wartet auf
Audio-Session" hängen bleibt, lässt sich nur auf dem Gerät sehen – das würde
zwischen „Session-Erkennung funktioniert jetzt, nur der Klang war das
Problem" (durch diesen Fix erledigt) und „Session-Erkennung schlägt bei
SoundCloud weiterhin grundsätzlich fehl" (das ungeklärte, tiefere
Zustellungsproblem aus Session 4) unterscheiden. Build weiterhin nicht lokal
verifizierbar (kein Android-SDK-Zugriff in dieser Sandbox); `AndroidAudioEngine`
ist laut M7 ohnehin nicht durch reine JVM-Unit-Tests abgedeckt (echte
Android-Media-Klassen nötig) – Verifikation über CI (Build/Lint) plus
Gerätetest/Hörprobe durch den Nutzer.

### Session 15 (22. September 2026)

Nutzer testet PR #16 auf dem Gerät und meldet ein eindeutiges, sehr
aufschlussreiches Ergebnis: Bei **Spotify** zeigt der Status-Chip jetzt
„Aktiv (Session #…)" – der Foreground-Service aus Session 13 hat das
Timing-Problem also tatsächlich behoben. Bei **SoundCloud** dagegen bleibt
der Status durchgehend leer/„Wartet auf Audio-Session" – keine einzige
Session wird je erkannt.

**Das grenzt die Ursache entscheidend ein:** Der Broadcast-Mechanismus selbst
funktioniert auf diesem Gerät (widerlegt die pessimistischste Lesart von
Session 4, dass er grundsätzlich systemweit blockiert wäre) – SoundCloud
sendet den `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`-Broadcast schlicht nie.
Das ist keine Eigenheit unseres Codes, sondern eine Entscheidung/ein
Implementierungsdetail von SoundCloud: Der Broadcast ist ein optionales,
Cooperative-only-Feature aus der Java-`MediaPlayer`-Ära; viele moderne, auf
ExoPlayer/Media3 aufbauende Player senden ihn nie automatisch, sofern die
App-Entwickler es nicht explizit nachbauen.

**Versucht, aber verworfen – zweite Session-Quelle über
`AudioManager.registerAudioPlaybackCallback(...)` /
`AudioPlaybackConfiguration.getAudioSessionId()`:** Der Plan war, diese vom
Audio-Framework selbst getriebene, vollständige Liste aller aktuell aktiven
Wiedergabe-Sessions systemweit zu nutzen, unabhängig davon, ob die abspielende
App kooperiert. **Fehleinschätzung, durch CI aufgedeckt:**
`AudioPlaybackConfiguration.getAudioSessionId()` ist entgegen der ursprünglichen
Annahme **kein Teil der öffentlichen Android-SDK-Stubs** (`compileSdk 37`) –
der Build schlug mit `Unresolved reference 'audioSessionId'` fehl. Diese
Methode ist offenbar `@SystemApi`/versteckt und für normale (nicht
System-/privilegierte) Apps schlicht nicht aufrufbar, auch nicht mit der
`MODIFY_AUDIO_SETTINGS`-Berechtigung. Die Änderung wurde vollständig
zurückgenommen (`AudioSessionRepository.kt`/Manifest wieder auf den Stand von
PR #16), bevor sie gemergt wurde – kein rotes CI im gemergten Code.

**Ehrliche Schlussfolgerung, nicht nur für diese Sitzung:** Damit gibt es
aktuell **keinen bekannten, im öffentlichen Android-SDK verfügbaren Weg**,
die Audio-Session einer fremden App zu ermitteln, wenn diese sie nicht selbst
per `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`-Broadcast meldet – und
SoundCloud tut das nachweislich nicht. MediaProjection/Playback-Capture ist
laut §2 ausdrücklich ausgeschlossen, Root ist laut §3 „Nicht im MVP". Ohne
neue Erkenntnis (z. B. falls SoundCloud den Broadcast doch unter bestimmten
Bedingungen sendet, oder eine andere, tatsächlich öffentliche API existiert)
sollte SoundCloud ehrlich als „von diesem Player nicht unterstützt"
dokumentiert werden (roadmap-Prinzip „Ehrliche Kompatibilität", §1.3), statt
weiter Workarounds zu suchen, die denselben SDK-Sichtbarkeits-Constraint
treffen dürften.

**Nächste konkrete Aufgabe:** Mit dem Nutzer klären, ob SoundCloud als
bekannte Einschränkung dokumentiert wird (z. B. im Hinweistext in
`EqualizerScreen.kt`), oder ob noch weitere Recherche gewünscht ist. Build
weiterhin nicht lokal verifizierbar (kein Android-SDK-Zugriff in dieser
Sandbox) – Verifikation über CI (Build/Lint) plus Gerätetest durch den
Nutzer.

### Session 16 (22. September 2026)

Nutzer meldet, noch bevor PR #17 gemergt ist, den eigentlichen Kern des
Lautheits-Problems: Die Uptempo-Presets klingen im direkten Vergleich zu
Flat weder bassiger noch klarer – Flat wirkt sogar lauter, aber "genauso
klar". Der MBC-Makeup-Gain-Fix aus Session 14 allein reicht also nicht.

**Root Cause, per Handrechnung mit den echten Pixel-10-Bändern (M0-Spike,
`docs/TEST_MATRIX.md`: 60/230/910/3600/14000 Hz) nachvollzogen:**
`MainViewModel.automaticInputGainDb()` bildete bisher
`-maxOf(peakBoostDb, presetHeadroomDb)` – und `presetHeadroomDb` (z. B. 4.0 dB
bei „Clean Punch") liegt in der Praxis nahe an oder sogar über dem tatsächlich
interpolierten `peakBoostDb` (für „Clean Punch" bei Band 60 Hz: ≈4.37 dB nach
Zielkurve + Bass-Makro). Ergebnis: Der verpflichtende Input-Gain-Cut hat die
EQ-Anhebung am stärksten angehobenen Band nahezu **exakt auf 0 dB netto**
zurückgerechnet – noch bevor Dynamikverarbeitung überhaupt beginnt. Nur der
MBC-Makeup-Gain-Fix (Session 14) sorgte danach noch für ein bisschen
hörbaren Unterschied, aber nur während der Kompressor tatsächlich greift.
Effektiv: Die Presets klangen kaum anders als Flat, exakt wie gemeldet.

Das ist letztlich dieselbe Baustelle, die roadmap.md §8 selbst schon als
vorläufig markiert hatte: „Automatischer Headroom basiert konservativ auf dem
maximalen positiven EQ-Gain; später kann eine präzisere Schätzung folgen" –
dieses „später" ist jetzt.

**Fix:** `automaticInputGainDb()` cancelt den Peak-Boost nicht mehr
vollständig, sondern nur noch zur Hälfte (`INPUT_GAIN_SAFETY_RATIO = 0.5f`,
neue Konstante in `MainViewModel.kt`). Der `presetHeadroomDb`-Floor
(`maxOf(...)`) entfällt komplett zugunsten des tatsächlich gemessenen
Peak-Boosts – `Preset.requestedHeadroomDb` bleibt als Datenfeld/anfänglicher
Platzhalterwert (`withPreset()`, ebenfalls ×0.5 skaliert) und in
`PresetJsonSerializer` bestehen, spielt aber für die eigentliche
Gain-Berechnung keine Rolle mehr. Die verbleibende Sicherheit gegen echtes
Clipping trägt jetzt stärker der **unveränderte** Limiter (hartes
10:1-Verhältnis, Schwelle ≤ 0 dBFS) – genau seine eigentliche Aufgabe, statt
dass der Input-Gain-Cut sie ihm vorab komplett abnimmt und die EQ-Kurve dabei
mit wegrasiert.

Neu-Rechnung für „Clean Punch"/Band 60 Hz: Cut jetzt −2,18 dB statt −4,37 dB
→ netto **+2,18 dB** vor Dynamikverarbeitung (vorher ±0 dB), plus MBC-Makeup
während lauter Passagen. Für „Uptempo – Final Smash" (extremster Boost, ≈5,7
dB an Band 60 Hz) ergibt sich netto bis zu ≈+4,85 dB inklusive MBC-Makeup –
spürbar mehr Bass, aber der Limiter fängt reale Pegelspitzen weiterhin
zuverlässig ab.

**Tests angepasst:** `MainViewModelTest` – „manual boost automatically
reserves matching headroom" umbenannt zu „...reserves half as headroom" mit
neuem Erwartungswert (`-4f` statt `-8f` bei `setBandGain(gainDb = 8f)`);
„selectPreset updates active preset and interpolates gains" erwartet jetzt
`-(peakBoostDb * 0.5f)` statt der alten `maxOf(...)`-Formel.

**Ehrlich zum Trade-off:** Der Limiter muss jetzt öfter/stärker eingreifen
als vorher, weil weniger Vorab-Absenkung stattfindet – das ist bei einem
Uptempo-Hardcore-EQ eher erwünschter Charakter (spürbare Kompression/Limiting
gehört zum Genre-Sound) als ein Risiko, aber ob sich das auf einem echten
Gerät gut statt übersteuert anhört, lässt sich nur durch Hörprobe klären.

**Nächste konkrete Aufgabe:** Nutzer hört auf dem Gerät gegen, ob die Presets
jetzt hörbar mehr Bass/Punch liefern als Flat, ohne unangenehm zu pumpen oder
zu verzerren. Build weiterhin nicht lokal verifizierbar (kein
Android-SDK-Zugriff in dieser Sandbox) – Verifikation über CI (Build/Unit-Tests)
plus Gerätetest/Hörprobe durch den Nutzer.

### Session 17 (22. September 2026)

Nutzer testet noch VOR dem Merge von PR #17 (also noch auf altem Code) und
bestätigt entsprechend erwartungsgemäß keinen Unterschied – wichtiger
Hinweis dazu direkt an den Nutzer gegeben. Zusätzlich klare Ansage: mehr
aggressive Kicks, weniger Sicherheitsabsenkung, als eigener nächster Schritt
(nicht mehr in PR #17 gestapelt, damit einzeln testbar und bei Bedarf
zurückrollbar).

**Zweiter, unabhängiger Bug beim vollständigen Audit der Kette gefunden:**
Das „Punch"-Makro (`EqualizerInterpolator.calculateMacroDelta`) hatte ein
Peak-Fenster von 80–150 Hz und ein Dip-Fenster von 250–350 Hz – auf den
echten Pixel-10-Bändern (60/230/910/3600/14000 Hz, `docs/TEST_MATRIX.md`)
liegt **keines der 5 Bänder** in einem dieser Fenster. Der Punch-Regler in
der UI hatte auf diesem Gerät also **keinerlei hörbaren Effekt**, egal wie
weit man ihn zieht – unabhängig vom Gain-Staging-Bug aus Session 16. Fenster
auf 70–160 Hz (Peak) / 220–360 Hz (Dip) verbreitert, sodass Band 1 (230 Hz)
jetzt im Dip-Fenster liegt und tatsächlich reagiert.

**Umgesetzt (noch nicht gepusht/PR eröffnet – erst nach Merge von PR #17,
damit die Schritte einzeln testbar bleiben):**
- `INPUT_GAIN_SAFETY_RATIO` in `MainViewModel.kt`: 0.5 → 0.3 (nur noch 30 %
  des Peak-Boosts werden vorab abgezogen, nicht mehr die Hälfte). Für „Clean
  Punch" ergibt das am Sub-Bass-Band jetzt ≈+3,06 dB netto vor
  Dynamikverarbeitung statt ≈+2,18 dB.
- Punch-Makro-Fenster verbreitert (s. o.), damit der Regler auf dem echten
  Testgerät überhaupt etwas bewirkt.
- `macroPunchDb` in den Kick-fokussierten Presets moderat angehoben (+0,5 dB):
  Clean Punch 1,5→2,0, Kick Attack 2,0→2,5, Raw Power 1,5→2,0, Fast Attack
  2,5→3,0, Final Smash 2,5→3,0. **Bewusst unverändert:** Deep Rumble, Balanced
  (nicht Kick-fokussiert) und Terrorcore – Maximum Distortion (dessen
  gesamtes Design in Session 12 explizit auf Zähmen statt Verschärfen
  ausgelegt wurde – noch aggressiver zu machen würde diesem Zweck
  widersprechen).
- `MainViewModelTest` entsprechend angepasst (30 % statt 50 % in beiden
  betroffenen Tests).

**Ehrlich zum Risiko:** Mit 0.3 statt 0.5 muss der Limiter noch öfter/stärker
eingreifen. Das bleibt eine bewusste, vom Nutzer angefragte Entscheidung
("kann man immer einen Schritt zurückgehen") – ohne Gerätetest nicht
abschließend beurteilbar, ob es zu hörbarem Pumping/Verzerrung führt.

**Nächste konkrete Aufgabe:** Sobald PR #17 gemerged ist, diesen Stand als
eigenen PR gegen `main` öffnen. Nutzer testet danach gezielt: (1) Punch-Regler
hat jetzt hörbaren Effekt? (2) Presets insgesamt spürbar aggressiver als vorher,
aber noch sauber (kein Pumping/Verzerren)? Build weiterhin nicht lokal
verifizierbar (kein Android-SDK-Zugriff in dieser Sandbox) – Verifikation über
CI (Build/Unit-Tests) plus Gerätetest/Hörprobe durch den Nutzer.

### Session 18 (23. September 2026)

Nutzer bittet: „Mache den Equalizer für Windows und Linux Debian/Ubuntu
nutzbar." Vor der Umsetzung per `AskUserQuestion` zwei Grundsatzentscheidungen
geklärt, weil die Aufwände um Größenordnungen auseinanderliegen: (1) Ansatz –
**Presets für bestehende, bereits system-weit wirkende Engines exportieren**
(Equalizer APO unter Windows, EasyEffects/PipeWire unter Linux) statt einer
kompletten eigenen DSP-Engine pro Plattform (eigener Treiber, Codesigning,
im Grunde ein neues Produkt pro OS); (2) Codebasis – **neues Modul im
selben Repo** statt separates Projekt. Außerdem explizit gefragt und
bestätigt bekommen, dass dies auf einem eigenen Branch/PR entsteht, nicht in
PR #17 (Audio-Engine-Fixes) gemischt wird.

**Modul-Umbau:**
- Neues, reines Kotlin/JVM-Modul `:core` (kein Android-, kein Compose-
  Dependency) mit den bereits plattform-unabhängigen Domänenklassen, die
  vorher unter `:app` lagen: `preset/Preset.kt`, `preset/BuiltInPresets.kt`,
  `dsp/EqualizerInterpolator.kt`, `audio/EqualizerBandCapabilities.kt`,
  `data/preset/PresetJsonSerializer.kt` (plus ihre bestehenden Unit-Tests).
  `:app` hängt jetzt von `:core` ab statt die Dateien selbst zu enthalten;
  Paketnamen unverändert, daher keine Import-Änderungen nötig. Damit nutzt
  der Desktop-Client exakt dieselbe Preset-Definition und
  Interpolationslogik wie die Android-App – ein Preset ist eine Kurve, keine
  zwei gepflegten Kopien.
- Neues Modul `:desktop` (Compose Multiplatform 1.12.1, Kotlin 2.3.20 – wie
  im restlichen Projekt gepinnt, siehe ADR 0001 zur KSP/Kotlin-Version-
  Kopplung; das betrifft `:desktop` nicht direkt, da hier kein KSP läuft,
  aber eine einzige Kotlin-Version für das ganze Repo vermeidet
  Klassenlader-Überraschungen).

**Desktop-App (`desktop/src/main/kotlin/com/hardbasseq/eq/desktop/`):**
- `App.kt`/`Main.kt`: einfaches Compose-UI – Preset-Liste (alle
  `BuiltInPresets.all`), drei Macro-Slider (Bass/Punch/Härte), Ziel-
  Plattform-Umschalter (Auto-Erkennung über `os.name`, manuell überschreibbar
  für den Fall, dass die Erkennung falschliegt oder zum Testen).
- `VirtualBands.kt`: 15 log-verteilte Frequenzpunkte (31 Hz–16 kHz) als
  Ersatz für die festen Hardware-Bänder eines echten Android-Geräts – Desktop-
  Engines unterstützen beliebige parametrische Filter, aber
  `EqualizerInterpolator.interpolatePresetToBands` (aus `:core`) erwartet eine
  konkrete Bandliste. Mehr Auflösung im Bass-/Kick-Bereich als ein
  typisches 10-Band-Grafik-EQ, weil genau dort die Uptempo-Hardcore-Presets
  den Löwenanteil ihrer Kurve platzieren.
- `PresetCurve.kt`: `automaticPreampDb()` – anders als auf Android (wo der
  Limiter als Sicherheitsnetz den Rest abfängt, siehe Session 16/17) gibt es
  bei einem reinen parametrischen EQ ohne Compressor/Limiter kein solches
  Netz. Der Preamp hier annulliert den positiven Spitzenpegel deshalb
  bewusst **vollständig**, nicht nur anteilig – konservativer als die
  Android-Lösung, aber richtig für den Kontext.
- `exporter/EqualizerApoExporter.kt`: erzeugt gültige Equalizer-APO-Filter-
  Syntax (`Filter N: ON PK Fc … Hz Gain … dB Q …` + `Preamp: … dB`,
  verifiziert gegen die offizielle Configuration-Reference-Doku). Schreibt
  **nicht** direkt in `config.txt` – stattdessen eine eigene Include-Datei
  (`HardBassEQ.txt`) plus eine einmalige, idempotente `Include:`-Zeile in
  `config.txt`, damit weder eigene Konfiguration des Nutzers überschrieben
  noch bei wiederholtem Anwenden Zeilen dupliziert werden.
- `exporter/EasyEffectsExporter.kt`: erzeugt ein EasyEffects-Preset-JSON
  (`output.equalizer#0` mit `left`/`right`-Bändern, Typ `Bell`). Es gibt
  keine offizielle Schema-Doku dafür – das Format wurde anhand realer,
  funktionierender Community-Presets (u. a. github.com/wwmm/easyeffects-
  Umfeld) nachvollzogen. Schreibt nach
  `~/.config/easyeffects/output/<Preset-Name>.json`; der Nutzer muss das
  Preset in EasyEffects noch selbst auswählen, da es keinen dokumentierten,
  stabilen Weg gibt, es von außen live zu erzwingen.
- Bewusst **nicht** portiert: MBC-Kompressor/Limiter-Dynamik aus der Android-
  Engine. Beide Ziel-Engines sind reine parametrische EQs; eine vollwertige
  Dynamikkette nachzubauen wäre ein eigenes, deutlich größeres Vorhaben und
  war nicht Teil der Entscheidung in `AskUserQuestion`.

**CI:** `.github/workflows/ci.yml` erweitert – der bestehende
`ktlintCheck`-Schritt deckt `:core`/`:desktop` automatisch mit ab (Root-
Aggregat-Task), neuer Schritt `./gradlew :core:test :desktop:test` für die
JVM-Unit-Tests (Android-spezifisches `testDebugUnitTest` erfasst sie nicht),
plus Report-Upload für beide.

**Nicht umgesetzt/offen:**
- Keine gebauten Installer (.msi/.deb) in CI – `compose.desktop.application`
  ist zwar für beide `TargetFormat`s konfiguriert, aber `jpackage` kann ein
  MSI nur auf einem Windows-Host bauen und ein DEB nur auf einem Linux-Host
  (WiX Toolset bzw. dpkg werden vom jeweiligen Betriebssystem-Toolchain
  vorausgesetzt). Der CI-Runner ist `ubuntu-latest`, daher hier bewusst nur
  Kompilieren + Testen, kein `packageMsi`/`packageDeb`. Siehe
  `desktop/README.md` für die lokalen Bau-Befehle pro Plattform.
- Kein automatischer Live-Reload für EasyEffects (Linux) – das JSON landet
  im Preset-Ordner, Auswahl in der EasyEffects-UI bleibt ein manueller
  Schritt.
- Build weiterhin nicht lokal verifizierbar (Sandbox-Policy blockiert
  `dl.google.com`, nötig für die Android-Gradle-Plugin-Auflösung, die auch
  beim reinen `:core`/`:desktop`-Build mitläuft, da beide im selben Root-
  Projekt liegen) – Verifikation über CI.
- EasyEffects-JSON-Schema ist nicht offiziell dokumentiert und daher nicht
  hundertprozentig garantiert stabil über Versionen hinweg – falls ein Import
  fehlschlägt, ist das der erste Verdächtige.

### Session 19 (25. September 2026)

**Vorbemerkung – Dokumentationslücke:** Zwischen Session 18 und diesem
Eintrag liegt tatsächlich mehrere Sessions Arbeit, die nie protokolliert
wurde: die vormals eigenständige Player-App (SoundCloud-Suche/-Login,
`PlayerActivity`, `AudioPlayerService`) wurde vollständig in dieses Repo
gemergt (als Library-Modul `:player`, siehe `settings.gradle.kts`), die
M1-Aufgabe „Zustandsautomat" aus `docs/STATE_MACHINE.md` wurde umgesetzt
(`AudioEngineState.Listening`/`Retrying`, begrenztes Re-Attach mit Backoff
in `AndroidAudioEngine.kt`), und drei parallel entstandene PRs (#10, #23,
#24) wurden zusammengeführt bzw. als überholt geschlossen. Diese Lücke wird
hier nicht rückwirkend aufgefüllt – nur als Hinweis, dass der Session-Log
ab hier wieder lückenlos weitergeführt wird, aber davor eine echte Lücke hat.

Setzt roadmap-2026.md M2 „Persistenz und Custom-Workflow" um.

**Repository-Schicht:**
- `PresetEntity` (Room, Tabelle `custom_presets`) hält nur noch
  `id`/`name`/`presetJson`/`updatedAtMillis` – der gesamte `Preset` wird als
  ein einziger, über `PresetJsonSerializer` validierter JSON-Blob
  gespeichert statt einer Spalte pro Feld. Built-ins werden nie in diese
  Tabelle geschrieben (bleiben `BuiltInPresets.all`, compile-time), was die
  M2-Abnahme „Built-ins bleiben unveränderlich" strukturell statt nur per
  Konvention erfüllt. `RoomPresetRepository` überspringt (und loggt) Zeilen,
  die `PresetJsonSerializer.importFromJson` nicht validiert – die
  M2-Abnahme „ein beschädigtes Nutzerpreset kann die App nicht am Start
  hindern".
- Neu: `com.hardbasseq.eq.settings` (`AppSettingsRepository`,
  `DataStoreAppSettingsRepository`) – der komplette Live-Zustand
  (`activePresetId`, `isDirty`, das volle `ProcessingSettings` inkl.
  manueller Band-Werte) wird als ein JSON-Blob in Preferences DataStore
  persistiert, nicht nur benannte Presets. Grund: Die Abnahme verlangt
  „Alle Einstellungen überleben App-, Prozess- und Geräteneustart", nicht
  nur gespeicherte Presets – ein manuell verstellter Band-Regler, der nie
  als eigenes Preset gespeichert wurde, muss einen Neustart trotzdem
  überleben.
- `ProcessingSettings` ist jetzt `@Serializable` (kotlinx.serialization);
  `:app` hat dafür `libs.plugins.kotlin.serialization` und
  `kotlinx-serialization-json` neu bekommen (`:core` hatte beides schon).

**`AppDatabase`:** `exportSchema = true` (vorher `false`) – die
`presets`-Tabelle (jetzt `custom_presets`) hatte laut Repo-weiter Suche nach
`PresetDao`-Aufrufstellen vor dieser Session **nie** einen echten
Schreibpfad, war also nie wirklich „ausgeliefert". Version bleibt bei 1
(keine echte Vorversion, von der aus zu migrieren wäre) – `exportSchema`
wird ab jetzt aktiviert, damit die *nächste* Schemaänderung eine echte
Baseline zum Migrieren hat.

**Bewusst offen gelassen, nicht Teil dieser Session:**
- **Automatisierte Migrationstests** (M2-Abnahme „Migrationstests decken
  mindestens die vorherige Schema-Version ab"): `MigrationTestHelper`
  braucht Robolectric oder eine Instrumentierungsumgebung – keines von
  beiden ist in diesem Repo eingerichtet (`ci.yml` führt nur
  `testDebugUnitTest`, reines JVM, auf dem Room/SQLite gar nicht laufen).
  Das Einrichten von Robolectric ist Voraussetzung für die *nächste*
  Migration, nicht für diese Session (siehe oben: es gibt noch keine echte
  Vorversion, von der aus zu testen wäre).
- **Undo/Redo innerhalb der laufenden Bearbeitung** (M2-Aufgabe) – bewusst
  zurückgestellt, um diese Session nicht weiter aufzublähen; alle anderen
  M2-Aktionen (Zurücksetzen, als neues Preset speichern, duplizieren,
  umbenennen, löschen mit Bestätigung) sind umgesetzt und UI-erreichbar
  (`EqualizerScreen.kt`/`MainScreen.kt`), nicht nur als totes
  Repository-Skelett wie es laut Code-Review in Session 10 zuvor bei
  Room/DataStore der Fall war.
- Ganzer Verlauf weiterhin nicht lokal mit `./gradlew` verifizierbar (kein
  Android-SDK in dieser Sandbox) – Ktlint-Konformität wurde stattdessen mit
  einem lokal heruntergeladenen, eigenständigen `ktlint`-CLI plus portablem
  JDK 21 gegen exakt die geänderten Dateien geprüft (0 Verstöße außerhalb
  von `:player`, das weiterhin keine ktlint-Anwendung hat). Kompilieren,
  Unit-Tests und Android Lint bleiben CI-only.
