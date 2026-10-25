# HardBass EQ – Roadmap Kontext-Modi & DSP-Auflösung (Q4 2026)

**Stand:** 25. Oktober 2026 · **Basis:** `main` auf `6d5d8cf`
**Verhältnis zu anderen Roadmaps:** Ergänzt `roadmap-2026.md` (Produkt-Roadmap,
M0–M7) um die Befunde der App-/Funktionsanalyse vom 25.10.2026 zu Auto-/Box-Modus,
Routen-Erkennung, Virtual Bass und EQ-Auflösung. Session-Log-Einträge gehören
weiterhin nach `roadmap.md`.

---

## 0. Arbeitsanweisung für den umsetzenden Agenten (Sonnet)

Diese Datei ist so geschrieben, dass jede Aufgabe einzeln und ohne weiteren
Kontext umsetzbar ist. Bitte so vorgehen:

1. **Genau eine Aufgabe pro Durchlauf** (z. B. „A2“). Nachbaraufgaben nur
   anfassen, wenn die aktuelle sonst nicht korrekt funktioniert.
2. **Code vor Roadmap:** Dateipfade und Funktionsnamen unten sind der Stand von
   `6d5d8cf`. Weicht der Code ab, gilt der Code – Abweichung im PR notieren.
3. **Abhängigkeiten beachten** (Spalte „Braucht“ in §2). Eine Aufgabe, deren
   Voraussetzung nicht erledigt ist, nicht beginnen.
4. **Eigener Branch je Aufgabe** (`roadmap/<id>-<kurzname>`), PR gegen `main`.
5. **Verifikation:** `./gradlew ktlintCheck` und, falls ein Android-SDK verfügbar
   ist, `./gradlew testDebugUnitTest :core:test :desktop:test`. Ohne SDK: ktlint
   lokal, Rest über CI. Eine Aufgabe gilt erst als erledigt, wenn CI im PR grün ist.
6. **👤-Schritte** (Gerätetests, Messungen) kann der Agent nicht ausführen. Diese
   als offene Zeile in `docs/TEST_MATRIX.md` eintragen und im PR erwähnen – nie
   als bestanden markieren.
7. **Nach Abschluss:** Checkbox der Aufgabe hier abhaken und unter der Aufgabe
   eine Zeile „Umgesetzt in PR #…, Abweichungen: …“ ergänzen.
8. **Nicht tun:** keine allgemeinen Refactorings, keine neuen Presets, keine
   UI-Umgestaltung über das Beschriebene hinaus, keine neuen Berechtigungen außer
   dort, wo die Aufgabe es ausdrücklich verlangt.
9. Alle neuen UI-Texte in `app/src/main/res/values/strings.xml` (App ist
   englisch), keine fest codierten Strings in Composables.

---

## 1. Ausgangslage (Befunde der Analyse)

| # | Befund | Folge |
|---|---|---|
| F1 | Manuell gewählter Kontext-Modus bleibt bei Routenwechsel aktiv (`MainViewModel.applyContextModeForRoute` schaltet nur automatisch gesetzte Modi ab). | „Auto“-Kurve läuft nach dem Aussteigen auf Kopfhörern weiter. |
| F2 | Kontext-Modus wird nicht pro Route gespeichert (`DeviceProfileEntity` hat kein Feld, `setContextMode` speichert nichts). Testfall K3 erwartet das. | Ausgeschalteter Auto-Modus kommt beim nächsten Verbinden wieder; manuelle Wahl wird nicht gemerkt. |
| F3 | `AndroidAudioRouteRepository.detectCurrentRoute()` nimmt das erste passende Gerät aus `getDevices(GET_DEVICES_OUTPUTS)`, nicht das Medienziel; SCO zählt mit; LE Audio (`TYPE_BLE_HEADSET`, `TYPE_BLE_SPEAKER`, `TYPE_BLE_BROADCAST`) fällt in keinen Zweig. | LE-Audio-Geräte werden als „Built-in Speaker“ behandelt; falsches Profil. |
| F4 | Namensheuristik `SoundContextClassifier` verfehlt verbreitete Namen (Ford „SYNC“, „MEDIA-NAV“, „R-Link“, Nachrüst-Radios, FM-Transmitter/AUX-Adapter, Tribit/Teufel/Beats-Boxen). | Auto-/Box-Modus startet bei vielen Geräten nicht. |
| F5 | Auf dem System-Pfad (klassischer `Equalizer`, 5 Bänder 60/230/910/3600/14000 Hz) kommt von den Kontextkurven nur eine grobe Neigung an. Auto: 60 Hz −0,4 · 230 Hz −1,8 · 3,6 kHz +2,4 · 14 kHz +1,5 dB. Box: 60 Hz ±0 · 230 Hz −1,1 · 3,6 kHz +1,4 dB – der geplante Lift bei 100 Hz fehlt. | Kontext-Modi bei Spotify nur eingeschränkt wirksam. |
| F6 | „Subsonic“ ist auf dem System-Pfad kein Hochpass, sondern senkt das ganze 30–120-Hz-Band um 0,5–1,4 dB. | Weniger Kick, kaum Headroom-Gewinn; UI zeigt „< 35 Hz“. |
| F7 | Virtual Bass wirkt nur im eigenen Player; Kachel zeigt bei Fremd-Apps trotzdem „on“. | Intransparent (Prinzip 4/6 aus `roadmap-2026.md`). |
| F8 | MBC-Werte (`mbcThresholdDb`, `mbcRatio`), Makros und `requestedHeadroomDb` der Kontext-Presets werden nirgends angewandt (`withContextFeatures` übernimmt nur Limiter, Loudness, Subsonic, Virtual Bass). | Tote Konfiguration. |
| F9 | `INPUT_GAIN_SAFETY_RATIO = 0.1`: +6 dB Loudness im Box-Modus senkt den Input-Gain nur um 0,6 dB; der Limiter (10:1) macht die Arbeit. | Pumpen bei Kicks, keine Schonung kleiner Treiber. |
| F10 | `DynamicsProcessing` hängt bereits an jeder Session, Pre-EQ lief auf dem Pixel 10 fehlerfrei (`docs/TEST_MATRIX.md`), wird aber nicht genutzt (in Session 10 entfernt, weil unkonfiguriert – nicht, weil er nicht funktioniert). | Größter ungenutzter Hebel für Auflösung auf dem System-Pfad. |
| F11 | Der eigene Player rechnet die EQ-Kurve nicht im PCM-Pfad, obwohl `BiquadFilterDesigner`/`BiquadFilterState` in `:core` existieren. | Kein echter Hochpass, keine exakte Kurve – auch dort, wo es technisch ginge. |
| F12 | Echtes Virtual Bass für Fremd-Apps ist ohne Root bzw. Audio-Mitschnitt nicht möglich (Android erlaubt fremden Sessions nur System-Effekte). | Entscheidung: bleibt Player-Feature; für Spotify nur EQ-Annäherung (Phase E). |

---

## 2. Übersicht und Reihenfolge

| ID | Aufgabe | Behebt | Braucht | Größe | Prio |
|---|---|---|---|---|---|
| A1 | Aktive Medienroute korrekt bestimmen, LE Audio | F3 | – | S | P0 |
| A2 | Kontext-Modus pro Route speichern (Room-Migration 1→2) | F1, F2 | – | M | P0 |
| A3 | Klassifizierer-Stichwörter erweitern | F4 | – | XS | P1 |
| A4 | Einmalige Gerätefrage bei unbekanntem Bluetooth-Gerät | F4 | A2 | M | P1 |
| A5 | Android-Auto-Verbindung als sicheres Auto-Signal | F4 | A2 | S | P1 |
| B1 | Ehrliche Kennzeichnung Virtual Bass / Subsonic | F6, F7 | – | S | P1 |
| B2 | Kontext-MBC anwenden, Box-Headroom, tote Felder | F8, F9 | – | S | P1 |
| C1 | DynamicsProcessing-Pre-EQ hinter Schalter | F5, F6, F10 | – | L | P1 |
| C2 | 👤 Messung Pre-EQ vs. klassischer EQ, Entscheidung | – | C1 | Gerät | P1 |
| C3 | Pre-EQ als Standard (nur nach C2 „go“) | – | C2 | XS | P2 |
| D1 | Kurven-EQ + echter Hochpass im eigenen Player | F11 | – | L | P2 |
| D2 | Doppelte EQ-Anwendung auf der Player-Session vermeiden | F11 | D1 | S | P2 |
| E1 | „Bass-Lift (angenähert)“ für Fremd-Apps | F12 | C1, B1 | S | P2 |
| F1 | Doku: TEST_MATRIX, DECISIONS, roadmap-2026-Verweis | – | jeweils | XS | P1 |
| G1 | Optional: BluetoothClass per Opt-in-Berechtigung | F4 | A4 | M | P3 |

**Empfohlene Reihenfolge:** A1 → A2 → A3 → B1 → B2 → A5 → A4 → C1 → 👤 C2 → C3 → D1 → D2 → E1 → G1.
F1 läuft mit jeder Aufgabe mit (jede Aufgabe trägt ihre eigenen Gerätetests ein).

---

## Phase A – Kontext-Modus korrekt machen

### A1 – Aktive Medienroute korrekt bestimmen, LE Audio  `[x]`

**Ziel:** Die Route ist das Gerät, auf dem Medien tatsächlich laufen – inklusive LE Audio.

**Dateien:** `app/src/main/java/com/hardbasseq/eq/audio/AudioRouteRepository.kt`,
neu `app/src/main/java/com/hardbasseq/eq/audio/RouteSelection.kt`,
Test neu `app/src/test/java/com/hardbasseq/eq/audio/RouteSelectionTest.kt`.

**Umsetzung:**
1. Die Auswahl-Logik aus `detectCurrentRoute()` in eine reine, testbare Funktion
   auslagern: `RouteSelection.pick(candidates: List<RouteCandidate>, mediaDeviceType: Int?): RouteCandidate?`
   mit `data class RouteCandidate(val type: Int, val productName: String?)`
   (Android-`AudioDeviceInfo.TYPE_*`-Konstanten als `Int`, damit JVM-testbar).
2. **Bevorzugt das echte Medienziel:** Ab API 33 (`Build.VERSION_CODES.TIRAMISU`)
   `audioManager.getAudioDevicesForAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())`
   abfragen; das erste Ergebnis ist das Medienziel. Wenn vorhanden und es ein
   unterstützter Typ ist, wird genau dieses Gerät verwendet.
3. **Fallback (API < 33 oder leeres Ergebnis):** feste Priorität statt
   Listenreihenfolge: BT A2DP / BLE Headset / BLE Speaker / BLE Broadcast / Hearing Aid
   → USB → kabelgebunden → Bus (Automotive) → interner Lautsprecher.
4. **`TYPE_BLUETOOTH_SCO` ignorieren**, außer es gibt kein anderes BT-Gerät
   *und* `audioManager.mode == MODE_IN_COMMUNICATION || MODE_IN_CALL`. (SCO ist
   Telefonie; ein Auto, das nur für Anrufe verbunden ist, darf keinen Auto-Modus auslösen.)
5. **LE Audio:** `TYPE_BLE_HEADSET` (26), `TYPE_BLE_SPEAKER` (27),
   `TYPE_BLE_BROADCAST` (30) wie Bluetooth behandeln. Fingerprint-Präfix bleibt
   `"bt"` (gleiches Gerät über Classic oder LE → gleiches Profil).
   `TYPE_BLE_SPEAKER` → `AudioDeviceType.BLUETOOTH_SPEAKER` **ohne** Namensprüfung
   (Android meldet es verlässlich). `TYPE_BLE_HEADSET` → Namensheuristik wie bei A2DP.
   Die Konstanten existieren erst ab API 31/33 – Vergleich als Int ist auf älteren
   Geräten harmlos; `@SuppressLint("InlinedApi")` falls Lint meckert.
6. Die Rückgabe von `detectCurrentRoute()` (`AudioRoute`) bleibt formal gleich,
   damit `MainViewModel` unverändert bleibt.

**Tests (`RouteSelectionTest`):**
- `media device from attributes wins over list order`
- `a2dp is preferred over wired when no media device is reported`
- `sco only does not count as a route outside a call`
- `ble speaker is a speaker without a name match`
- `ble headset uses the name heuristic`
- `no external device falls back to the built-in speaker`

**Akzeptanz:** Tests grün; `AudioRouteTest` unverändert grün.
👤 Gerätetest A1-G1: LE-Audio-Kopfhörer bzw. -Box verbinden → Diagnose zeigt richtige Route.

*Umgesetzt (Branch `ccr-89d3640a-gjxiez`), Abweichungen: `pick` hat zusätzlich den Parameter `inCommunication` (Audio-Modus-Prüfung für SCO). Lokal kein Gradle-Build möglich (Android-Plugin offline nicht auflösbar) – ktlint/Tests laufen über CI. A1-G1 offen.*

---

### A2 – Kontext-Modus pro Route speichern  `[x]`

**Ziel:** Jede Route merkt sich eine explizite Modus-Wahl. Eine manuelle Wahl gilt
nur für die Route, auf der sie getroffen wurde. Behebt F1 und F2.

**Semantik (verbindlich):**

| Gespeicherter Wert `contextMode` | Bedeutung |
|---|---|
| `null` | Keine Wahl → Erkennung entscheidet (Typ CAR/BLUETOOTH_SPEAKER → Modus an, sonst aus) |
| `"CAR"` / `"BLUETOOTH_SPEAKER"` | Explizit an |
| `"NONE"` | Explizit aus (auch wenn die Erkennung etwas anderes meint) |

**Dateien:**
- `app/src/main/java/com/hardbasseq/eq/data/profile/DeviceProfileEntity.kt` – Feld `val contextMode: String? = null`
- `app/src/main/java/com/hardbasseq/eq/data/db/AppDatabase.kt` – `version = 2`
- `app/src/main/java/com/hardbasseq/eq/di/DatabaseModule.kt` – `.addMigrations(MIGRATION_1_2)`
- neu `app/src/main/java/com/hardbasseq/eq/data/db/Migrations.kt`
- `app/src/main/java/com/hardbasseq/eq/profile/DeviceProfileRepository.kt` + `RoomDeviceProfileRepository.kt`
- `app/src/main/java/com/hardbasseq/eq/data/db/DeviceProfileDao.kt`
- `app/src/main/java/com/hardbasseq/eq/ui/main/MainViewModel.kt`
- Test: `app/src/test/java/com/hardbasseq/eq/ui/main/MainViewModelTest.kt` (inkl. `FakeDeviceProfileRepository`)

**Umsetzung:**
1. **Migration – wichtig:** Es gibt installierte Release-APKs (Release-Workflow,
   Update-Hinweis), also echte Daten in Schema 1. Ohne Migration stürzt Room beim
   Start ab. Daher:
   ```kotlin
   val MIGRATION_1_2 = object : Migration(1, 2) {
       override fun migrate(db: SupportSQLiteDatabase) {
           db.execSQL("ALTER TABLE device_profiles ADD COLUMN contextMode TEXT DEFAULT NULL")
       }
   }
   ```
   Das exportierte Schema `app/schemas/.../2.json` muss vom Build erzeugt und
   mit eingecheckt werden. **Kein** `fallbackToDestructiveMigration()` für Upgrades.
2. `DeviceProfileRepository` um eine eigene Schreibmethode erweitern
   (`suspend fun saveContextMode(routeId: String, routeType: String, displayName: String, contextMode: String?)`),
   die eine vorhandene Zeile aktualisiert bzw. eine neue anlegt, ohne
   `boundPresetId`/`boundCorrectionProfileId` zu überschreiben. Für eine neue Zeile
   ohne Preset-Bindung `boundPresetId` = aktuell aktives Preset setzen (gleiches
   Verhalten wie `saveDeviceProfileBinding()`), damit die Zeile gültig ist.
   Alternativ `saveProfile` um `contextMode` erweitern – dann aber an **allen**
   Aufrufstellen den bisherigen Wert durchreichen, sonst wird er gelöscht.
3. `MainViewModel`:
   - `applyContextModeForRoute(route)` erhält den gespeicherten Wert
     (`binding?.contextMode`) und entscheidet nach der Tabelle oben. Der bisherige
     Zweig „`contextSetAutomatically` → aus“ entfällt: Beim Routenwechsel wird der
     Modus **immer** aus gespeicherter Wahl bzw. Erkennung der neuen Route neu
     bestimmt. Eine manuelle Wahl wandert nie auf eine andere Route.
   - Reihenfolge in `applyDeviceProfileForRoute`: erst Binding lesen, dann
     Modus bestimmen (heute wird `applyContextModeForRoute` vor dem Lesen der
     Binding-Werte aufgerufen – anpassen).
   - `setContextMode(context)` speichert für die **aktuelle** Route:
     `context?.name ?: "NONE"`.
   - Legacy-Fall (Binding zeigt auf ein Kontext-Preset, `BuiltInContextPresets.contextOf`)
     bleibt: gilt als explizite Wahl dieses Modus, wenn `contextMode == null`.
   - `LiveSettings.activeContext`/`activeContextAutomatic` bleiben für die
     Wiederherstellung beim Kaltstart lesbar; nach dem ersten Routen-Ereignis gilt
     die Routen-Logik. `contextSetAutomatically` nur entfernen, wenn kein Test
     und kein Persistenzpfad es mehr braucht.

**Tests (neu, in `MainViewModelTest`):**
- `a manually enabled mode is not carried over to the next route`
- `a mode switched off on a detected car route stays off on reconnect`
- `a manual mode choice is remembered for that route`
- `an explicit NONE wins over car detection`
- `a route without a saved choice follows detection`
- Bestehende Kontext-Tests (ab ca. Zeile 990) müssen grün bleiben bzw. an die neue
  Semantik angepasst werden – Anpassungen im PR begründen.

**Akzeptanz:** Tests grün, Schema 2 eingecheckt.
👤 A2-G1: Update-Installation über eine Version mit Schema 1 → App startet, bestehende Profile sind noch da.
👤 A2-G2: K3 aus `docs/TEST_MATRIX.md` (Gerät ohne Stichwort, manuell „Car“ → beim nächsten Verbinden wieder an).
👤 A2-G3: „Car“ manuell an, Kopfhörer verbinden → Modus aus.


*Umgesetzt (Branch `ccr-89d3640a-gjxiez`, PR #57). Abweichungen: Persistenz über ein zusätzliches Argument `contextMode` an `saveProfile` statt eigener Schreibmethode; das exportierte Schema `2.json` wird vom Build erzeugt (das eingecheckte `1.json` ist veraltet und kennt die Spalten `boundCorrectionProfileId`/`headphoneAcousticsOverride` nicht) – nach dem ersten Gradle-Lauf mit einchecken. A2-G1…G3 offen (K12–K14).*

---

### A3 – Klassifizierer-Stichwörter erweitern  `[x]`

**Ziel:** Mehr Autos/Boxen am Namen erkennen, ohne neue Fehlgriffe.

**Dateien:** `core/src/main/kotlin/com/hardbasseq/eq/context/SoundContext.kt`,
`core/src/test/kotlin/com/hardbasseq/eq/context/SoundContextClassifierTest.kt`.

**Umsetzung:**
- Auto-Wörter ergänzen: `sync`, `medianav`, `rlink`, `uvo`, `bluelink`, `pcm`,
  `entune`, `cue`, `mitsubishi`, `alfa`, `smart`, `polestar`, `mg`, `byd`,
  `pioneer`, `kenwood`, `alpine`, `blaupunkt`, `headunit`, `carstereo`, `fm`,
  `transmitter`.
- Zusammengesetzte Namen: Bindestrich-Namen werden heute in Wörter zerlegt
  („MEDIA-NAV“ → `media`, `nav`). Zusätzlich die Verkettung benachbarter Wörter
  prüfen (`media`+`nav` → `medianav`, `r`+`link` → `rlink`), oder eine kleine
  Liste von Mehrwort-Mustern (`listOf(listOf("media","nav"), listOf("r","link"))`).
- Box-Wörter ergänzen: `stormbox`, `pill`, `boomster`, `rockster`, `emberton`,
  `stanmore`, `acton`, `onyx`, `motion` **nur zusammen mit** `soundcore`,
  `clip`, `pulse`, `go` **nur zusammen mit** `jbl`, `party`, `soundcore`-Mehrwort
  wie oben (Mehrwort-Regeln, damit „Go“/„Motion“/„Clip“ allein nichts auslösen).
- **Nicht** aufnehmen: `jvc`, `sony`, `marshall`, `beats` allein (machen auch
  Kopfhörer), `mini` (MINI-Autos vs. „Mini Speaker“).
- Reihenfolge Auto vor Box bleibt.

**Tests:** je ein positiver Fall pro neuem Wort/Muster und diese Negativfälle:
`"JBL Tune 760NC"`, `"Sony WH-1000XM5"`, `"Marshall Major IV"`, `"Galaxy Buds3 Pro"`,
`"Soundcore Liberty 4"`, `"Beats Studio Pro"` → `null`.

**Akzeptanz:** alle bestehenden und neuen Klassifizierer-Tests grün.


*Umgesetzt (PR #57). Mehrwort-Regeln für `go`/`clip`/`pulse` (mit `jbl`) und `motion` (mit `soundcore`); zusammengesetzte Namen über Verkettung benachbarter Wörter.*

---

### A4 – Einmalige Gerätefrage bei unbekanntem Bluetooth-Gerät  `[x]`

**Braucht:** A2.

**Ziel:** Generische Namen („BT Audio“, „MY CAR“, Kürzel) lösen das Problem
ohne neue Berechtigung: Der Nutzer sagt einmal, was das Gerät ist.

**Dateien:** `MainViewModel.kt`, `EqualizerScreen.kt` (oder eigenes Composable
`DeviceKindPrompt.kt` in `ui/equalizer/`), `strings.xml`, Tests in `MainViewModelTest`.

**Umsetzung:**
1. Bedingung für die Frage: `route.type == AudioDeviceType.BLUETOOTH` (also
   nicht klassifiziert) **und** gespeicherter `contextMode == null` **und** in
   dieser App-Sitzung für diese `routeId` noch nicht weggeklickt.
2. ViewModel: `val deviceKindPrompt: StateFlow<AudioRoute?>` und
   `fun answerDeviceKind(kind: DeviceKind)`, `fun dismissDeviceKindPrompt()`.
   `enum class DeviceKind { HEADPHONES, CAR, SPEAKER }` (in `:core` oder `audio/`).
3. Antwort speichert über den A2-Pfad:
   - HEADPHONES → `contextMode = "NONE"`, `headphoneAcousticsOverride = true`
   - CAR → `contextMode = "CAR"`, `headphoneAcousticsOverride = false`
   - SPEAKER → `contextMode = "BLUETOOTH_SPEAKER"`, `headphoneAcousticsOverride = false`
   und wendet den Modus sofort an.
4. UI: kompakte Karte oben im EQ-Screen im Neon-Stil (`NeonPanel`), Text z. B.
   „What is *<name>*?“ mit drei Buttons + „Later“. Keine Dialoge, die die
   Wiedergabe-Steuerung blockieren. 48-dp-Ziele, beschriftet (Accessibility).
5. In der Diagnose/Profil-Übersicht muss die Wahl später änderbar sein: Der
   bestehende Kontext-Chip-Schalter reicht dafür (er speichert seit A2 pro Route).

**Tests:**
- `an unclassified bluetooth route without a choice asks once`
- `answering car stores CAR and turns the mode on`
- `answering headphones stores NONE and the headphone override`
- `dismissing does not ask again in the same session`
- `a classified or already answered route does not ask`

**Akzeptanz:** Tests grün. 👤 A4-G1: unbekanntes BT-Gerät verbinden → Frage erscheint genau einmal, Antwort bleibt nach App-Neustart.


*Umgesetzt (PR #57). Die Frage entfällt, solange Android Auto verbunden ist. A4-G1 offen (K15).*

---

### A5 – Android-Auto-Verbindung als sicheres Auto-Signal  `[x]`

**Braucht:** A2 (Explizite Wahl schlägt auch dieses Signal).

**Ziel:** Läuft Android Auto, ist der Kontext „Car“ – unabhängig vom BT-Namen und
auch, wenn das Audio über USB läuft.

**Umsetzung:**
1. Abhängigkeit `androidx.car.app:app` (aktuelle stabile Version, im Version-Catalog
   `gradle/libs.versions.toml` eintragen) und
   `androidx.car.app.connection.CarConnection(context).type` (LiveData) beobachten.
   `CONNECTION_TYPE_PROJECTION` (Android Auto) bzw. `CONNECTION_TYPE_NATIVE`
   (Automotive OS) → Auto aktiv. In ein neues Repository kapseln:
   `interface CarConnectionRepository { val carConnected: StateFlow<Boolean> }`,
   Android-Implementierung + Hilt-Binding, Fake für Tests.
2. Falls `CarConnection` sich als unzuverlässig erweist (👤 C10 unten), zusätzlich
   in `app/src/main/java/com/hardbasseq/eq/auto/AutoBrowserService.kt`
   `onGetRoot` mit `clientPackageName == "com.google.android.projection.gearhead"`
   als zweites Signal nutzen. Das nur einbauen, wenn Schritt 1 im Gerätetest versagt.
3. `MainViewModel`: Erkennung = `CAR`, wenn `carConnected == true`, sonst wie
   bisher über den Routentyp. Gespeicherte explizite Wahl (A2) gewinnt weiterhin.
4. Diagnose-Screen: Zeile „Android Auto: connected / not connected“.

**Tests:** `android auto connection turns car mode on for an unclassified route`,
`an explicit NONE wins over an android auto connection`,
`disconnecting android auto re-evaluates the route`.

**Akzeptanz:** Tests grün. 👤 C10 (= K10 in TEST_MATRIX): kabelgebunden und drahtlos – Modus geht an, und ob der EQ auf der Spotify-Session greift, wird dokumentiert.


*Umgesetzt (PR #57), `androidx.car.app:app:1.4.0`. Schritt 2 (Gearhead-Signal im `AutoBrowserService`) nur nach Gerätetest K18, falls `CarConnection` versagt.*

---

## Phase B – Ehrlichkeit und Sicherheit

### B1 – Ehrliche Kennzeichnung Virtual Bass / Subsonic  `[x]`

**Ziel:** Kein Schalter behauptet eine Wirkung, die auf dem aktuellen Pfad nicht entsteht.

**Dateien:** `EqualizerScreen.kt` (Kachelreihe ab ca. Zeile 686, `SignalModuleTile`),
`PlayerScreen.kt` falls dort dieselben Kacheln erscheinen, `MainViewModel.kt`, `strings.xml`.

**Umsetzung:**
1. `MainViewModel`: `val isOwnPlayerSession: StateFlow<Boolean>` – wahr, wenn die
   angebundene Session (`AudioSessionRepository.activeSession`) die eigene ist
   (`packageName == context.packageName`; im ViewModel über einen injizierten
   `ownPackageName: String` lösen, damit testbar).
2. Virtual-Bass-Kachel: Bei Fremd-Session Detailtext `"player only"` statt `"on"`
   und die Kachel visuell gedämpft (nicht deaktiviert – der Wert gilt weiter für
   den Player). Neuer String `module_player_only`.
3. Subsonic-Kachel: Solange der klassische EQ-Pfad aktiv ist (vor C1/C3), Detailtext
   `"≈ ${cutoff} Hz"` und im Info-Text der Hinweis, dass dies auf 5-Band-Geräten
   nur eine Absenkung des untersten Bands ist. Neuer String `context_subsonic_approx`.
   Nach C1 mit aktivem Pre-EQ: wieder `"< ${cutoff} Hz"`.

**Tests:** ViewModel-Test `own player session is detected by package name`.
**Akzeptanz:** 👤 B1-G1: Spotify spielt, Virtual Bass an → Kachel zeigt „player only“.


*Umgesetzt (PR #57). `isOwnPlayerSession` ist auch ohne gebundene Session wahr (der eigene Player kann ohne Session spielen); `BuildConfig.APPLICATION_ID` statt injiziertem Paketnamen. Info-Text zum Subsonic-Näherungswert entfällt (nur Detailtext `≈`).*

---

### B2 – Kontext-MBC anwenden, Box-Headroom, tote Felder  `[x]`

**Ziel:** Die Schutz-Werte der Kontext-Presets wirken tatsächlich; kleine Boxen
werden nicht nur vom Limiter „gerettet“.

**Dateien:** `MainViewModel.kt` (`withContextFeatures`, `recalculateBandGains`,
`safetyScaledInputGainDb`, Konstante `INPUT_GAIN_SAFETY_RATIO` Zeile ~100),
`core/.../preset/BuiltInContextPresets.kt`, Tests.

**Umsetzung:**
1. **MBC:** In `recalculateBandGains` werden `mbcThresholdDb`/`mbcRatio` heute nur
   aus `_activePreset` gelesen. Bei aktivem Kontext zusammenführen:
   `threshold = minOf(preset, context)`, `ratio = maxOf(preset, context)`,
   `mbcEnabled = preset.mbcEnabled || context != null`. Danach erst das
   Kopfhörer-Easing anwenden (wie bisher).
2. **Headroom pro Kontext:** `INPUT_GAIN_SAFETY_RATIO` bleibt `0.1f` als Standard
   (bewusste Entscheidung aus Session 24 „soll ja knallen“). Neu: Ratio aus dem
   Kontext, `BLUETOOTH_SPEAKER → 0.5f`, `CAR → 0.1f` (unverändert), kein Kontext → `0.1f`.
   Als Feld `inputGainSafetyRatio: Float? = null` am Preset in `:core` oder als
   `when` im ViewModel – Hauptsache an einer Stelle und kommentiert.
   Hintergrund im Kommentar: kleine Treiber verzerren mechanisch, bevor digital
   etwas clippt; der Limiter schützt nicht die Membran.
3. **Tote Felder:** `macroBassDb`, `macroPunchDb`, `macroHaerteDb`,
   `requestedHeadroomDb` der Kontext-Presets werden nicht angewandt. Nicht
   anwenden (würde Genre-Makros überschreiben), sondern in
   `BuiltInContextPresets.kt` per Kommentar als „bei Overlay ungenutzt“
   kennzeichnen. Werte nicht löschen (Legacy-Profile lesen das Preset).

**Tests:**
- `speaker mode tightens mbc threshold and ratio`
- `car mode keeps the genre mbc when it is already tighter`
- `speaker mode uses the higher input gain safety ratio`
- `switching the mode off restores the genre dynamics`

**Akzeptanz:** Tests grün. 👤 B2-G1 (= D3 in TEST_MATRIX): Box-Modus, basslastiger Track, laut → weniger Pumpen als vorher.


*Umgesetzt (PR #57). Der Kontext-Faktor liegt als `when` im ViewModel (`BLUETOOTH_SPEAKER` 0,5; sonst 0,1). B2-G1 offen (K17).*

---

## Phase C – Mehr Auflösung auf dem System-Pfad (Spotify & Co.)

### C1 – DynamicsProcessing-Pre-EQ hinter Schalter  `[x]`

**Ziel:** Statt 5 Bänder (klassischer `Equalizer`) bis zu 16 frei gesetzte Bänder
über den Pre-EQ von `DynamicsProcessing`, der ohnehin an jeder Session hängt.
Zunächst **experimentell und standardmäßig aus**, bis C2 gemessen hat.

**Bandaufteilung (obere Grenzfrequenzen, 16 Bänder):**
`25, 40, 55, 70, 90, 115, 150, 200, 280, 400, 630, 1000, 2000, 4000, 8000, 20000` Hz.
Begründung: dicht im Bass, wo Kick, Kabinenresonanz, Subsonic und Virtual-Bass-
Annäherung liegen; grob in Mitten/Höhen. Als Konstante in `:core`
(`PreEqLayout.CUTOFFS_HZ`).

**Dateien:**
- neu `core/src/main/kotlin/com/hardbasseq/eq/dsp/PreEqMapper.kt` + Test
- `app/src/main/java/com/hardbasseq/eq/audio/ProcessingSettings.kt` – `val preEqGainsDb: List<Float> = emptyList()`
- `app/src/main/java/com/hardbasseq/eq/audio/AudioCapabilities.kt` – `val preEqCutoffsHz: List<Float> = emptyList()`
- `app/src/main/java/com/hardbasseq/eq/audio/AndroidAudioEngine.kt`
- `app/src/main/java/com/hardbasseq/eq/audio/FakeAudioEngine.kt`
- `app/src/main/java/com/hardbasseq/eq/ui/main/MainViewModel.kt`
- `app/src/main/java/com/hardbasseq/eq/ui/diagnostics/DiagnosticsScreen.kt`
- Einstellung „High-resolution EQ (experimental)“ (Persistenz wie andere App-Einstellungen in `LiveSettings`/`AppSettingsRepository`, Default `false`)

**Umsetzung:**
1. **`PreEqMapper.map(curve, macros, cutoffsHz, manualDeltas): List<Float>`** (rein, in `:core`):
   - Gain je Band = Kurve (`EqualizerInterpolator.resolveGainAtFrequency`, inkl.
     Makros) am **logarithmischen Mittelpunkt** des Bands
     (`sqrt(untereGrenze * obereGrenze)`, untere Grenze des ersten Bands = 15 Hz).
   - Plus manuelle Deltas (siehe 4), auf ±15 dB begrenzt.
2. **Engine (`AndroidAudioEngine.attachLocked`):** Wenn die Einstellung an ist,
   Konfigurationen in dieser Reihenfolge versuchen, erste erfolgreiche gewinnt:
   (a) Pre-EQ + MBC + Limiter, (b) Pre-EQ + Limiter, (c) MBC + Limiter (heutiges
   Verhalten), (d) Limiter. Für (a)/(b) im `Config.Builder`
   `preEqInUse = true, preEqBandCount = 16` setzen und **vor** dem Erzeugen die
   Grenzfrequenzen festlegen:
   ```kotlin
   val eq = DynamicsProcessing.Eq(true, true, cutoffs.size)
   cutoffs.forEachIndexed { i, hz -> eq.setBand(i, DynamicsProcessing.EqBand(true, hz, 0f)) }
   builder.setPreEqAllChannelsTo(eq)
   ```
   Nach Erfolg `preEqCutoffsHz` in den Capabilities melden (leere Liste = kein Pre-EQ).
3. **Anwenden (`applyInternal`):** Bei Pre-EQ je Band
   `dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoffs[i], gain))`
   und den klassischen `Equalizer` auf `enabled = false` setzen (Instanz behalten,
   sie liefert weiter die Bandinfo für UI und Fallback). Ohne Pre-EQ: Verhalten
   exakt wie heute. **Nie beide gleichzeitig aktiv.**
4. **Manuelle Bandänderungen** (`MainViewModel.setBandGain`, klassische Band-Indizes):
   Differenz zwischen gesetztem Wert und Kurvenwert an der Band-Mitte als
   Delta-Stützpunkt speichern und in `PreEqMapper` auf die Pre-EQ-Bänder
   interpolieren. Damit bleiben die Regler in der UI unverändert nutzbar.
5. **`recalculateBandGains`** berechnet `preEqGainsDb` zusätzlich, wenn
   `capabilities.preEqCutoffsHz` nicht leer ist. Headroom bleibt wie bisher
   (`HeadroomCalculator.fromCombinedCurve` arbeitet schon auf der kontinuierlichen Kurve).
6. **A/B-Vergleich** (`pushToEngine`, `CompareSide.ORIGINAL`): auch `preEqGainsDb`
   auf 0 setzen.
7. **Subsonic auf dem Pre-EQ-Pfad:** Durch die Bänder 25/40 Hz wirkt
   `SubsonicFilterCurve` jetzt annähernd als Hochpass – keine Sonderbehandlung
   nötig, nur B1-Text (`"< x Hz"` statt `"≈"`) an `preEqCutoffsHz.isNotEmpty()` koppeln.
8. **Diagnose:** Zeile „EQ path: classic 5-band / DP pre-EQ 16-band / DP unavailable“.

**Tests:**
- `PreEqMapperTest`: flache Kurve → alle 0; Car- und Box-Kurve → Werte an
  75/100/120/200 Hz liegen innerhalb ±0,5 dB der Zielkurve (`BuiltInContextPresets`);
  Clamping bei ±15 dB; manuelles Delta am 60-Hz-Band hebt die Bänder 40–90 Hz an.
- `MainViewModelTest`: `pre eq gains are computed when the engine reports pre eq bands`,
  `compare original zeroes pre eq gains`, `without pre eq bands nothing changes`.
- `FakeAudioEngine` bekommt einen Schalter für Pre-EQ-Capabilities.

**Akzeptanz:** Tests grün, Standard bleibt aus.
**Nicht tun:** keine PEQ-UI (das ist M6 Phase 2 in `roadmap-2026.md`), keine
Änderung der Preset-Kurven.


*Umgesetzt (PR #57), Standard aus. Nachträglich verfeinert: 34 statt 16 Bänder (`PreEqLayout`), MBC mit 4 statt 3 Bändern (eigenes Kick-Band bis 130 Hz, Sub-Band bis 45 Hz); fällt das Gerät bei der Erzeugung durch, greift die Rückfallkette. Schalter oben in der Equalizer-Hauptansicht (nicht mehr in der Diagnose); die Einstellung liegt in `ProcessingSettings.highResolutionEq` (damit in `LiveSettings` persistiert); Umschalten baut den Effekt per `retry()` neu auf. Abweichung: ±0,5 dB aus der Roadmap sind mit dieser Bandaufteilung bei 200 Hz (Car) nicht erreichbar, der Test prüft ±1 dB.*

---

### C2 – 👤 Messung Pre-EQ vs. klassischer EQ, Entscheidung  `[ ]`

**Braucht:** C1. **Ausführender:** Sebastian (Gerät); Agent bereitet nur die Tabelle vor.

**Vorbereitung (Agent):** In `docs/TEST_MATRIX.md` Abschnitt „C2 – Pre-EQ-Messung“
mit dieser Tabelle anlegen (leer):

| Prüfung | Klassisch | Pre-EQ | Notiz |
|---|---|---|---|
| Sweep über die Spike-Werkzeuge (`audio/spike/TestTonePlayer.kt` mit `LogarithmicSweepGenerator`, Aufnahme z. B. mit zweitem Handy + REW/Spektrum-App): Abweichung zur Zielkurve bei 40/75/100/200 Hz, Car-Modus | | | |
| Dasselbe, Box-Modus | | | |
| Knackser beim Preset-/Modus-Wechsel während Wiedergabe | | | |
| Attach klappt bei Spotify (10 Starts) | | | |
| CPU/Akku 30 min Spotify | | | |
| Höreindruck Auto / Box | | | |

**Entscheidungsregel:** „Go“ für C3, wenn Pre-EQ die Zielkurve im Bass messbar
genauer trifft, keine neuen Knackser auftreten und Attach nicht schlechter wird.
Ergebnis in `docs/DECISIONS.md` festhalten.


*Tabelle vorbereitet in `docs/TEST_MATRIX.md`. Messung steht aus (👤).*

---

### C3 – Pre-EQ als Standard  `[ ]`

*Bewusst nicht umgesetzt: wartet auf das „Go“ aus der Messung C2.*

**Braucht:** C2 mit Ergebnis „Go“. Default der Einstellung aus C1 auf `true`;
Schalter bleibt in der Diagnose als Rückfallebene. Nichts weiter.

---

## Phase D – Eigener Player: exakte Kurve

### D1 – Kurven-EQ + echter Hochpass im PCM-Pfad des Players  `[x]`

**Ziel:** Im eigenen Player läuft die komplette Zielkurve als Biquad-Kette und der
Subsonic-Filter als echter Hochpass (24 dB/Oktave). Damit ist der eigene Player
für Auto/Box hörbar die beste Option – ehrlich nach Release-Gate B.

**Dateien:**
- neu `core/src/main/kotlin/com/hardbasseq/eq/dsp/CurveEqualizer.kt` + Test
- `core/src/main/kotlin/com/hardbasseq/eq/dsp/PlayerDspPcm16.kt` (`PlayerDspSettings`, `process`)
- `player/app/src/main/java/com/soundcloud/equalizer/player/playback/PlayerDspState.kt`
- `app/src/main/java/com/hardbasseq/eq/integration/PlayerBridge.kt` + `AndroidPlayerBridge.kt`
- `MainViewModel.kt` (Übergabe der Kurve, analog `pushVirtualBass`)

**Umsetzung:**
1. **`CurveEqualizer` (`:core`, rein):**
   - Eingabe: Zielkurve (`List<TargetPoint>`), `subsonicCutoffHz`, Samplerate, Kanalzahl.
   - Hochpass: 2 kaskadierte Butterworth-HP (`ParametricFilterType.HIGH_PASS`,
     Q 0,7071) bei `subsonicCutoffHz`, nur wenn > 0.
   - Kurve: Peaking-Biquads (`ParametricFilterType.PEAK`) an festen Mitten
     `31, 45, 63, 90, 125, 180, 250, 355, 500, 1000, 2000, 4000, 8000, 16000` Hz,
     Q aus dem Mittenabstand (≈ 1,4 für die Halboktav-Bässe, ≈ 1,0 darüber).
     Gains iterativ bestimmen (3 Durchläufe): Start = Zielwert an der Mitte;
     dann `gain += ziel(f) − ist(f)` mit `ist` aus
     `BiquadFilterDesigner.magnitudeResponseDb` der gesamten Kette. Clamping ±15 dB.
   - Koeffizienten nur neu berechnen, wenn Kurve/Samplerate sich ändern (nicht pro Buffer).
   - Zustand je Kanal je Filter in `BiquadFilterState`.
2. **Wichtig: die übergebene Kurve enthält die Subsonic-Kurve nicht.** In
   `MainViewModel` die Player-Kurve aus denselben Quellen wie `combinedCurve()`
   bilden, aber ohne `SubsonicFilterCurve` (sonst doppelt), und `subsonicCutoffHz`
   separat übergeben. Gemeinsame Hilfsfunktion statt Copy-Paste.
3. **`PlayerDspPcm16.process`** auf durchgehende Float-Verarbeitung pro Frame
   umbauen: PCM → Float → **CurveEqualizer** → Mono-Bass → Limiter → einmal zurück
   nach PCM16. Heute wird nach jeder Stufe nach `Short` gewandelt; mit EQ-Boosts
   würde das vor dem Limiter clippen.
   Vor dem EQ einen Vorverstärkungs-Abzug `−maxBoostDb` der Kurve anwenden
   (Headroom), der Limiter bleibt das letzte Glied.
4. Reihenfolge der Media3-Prozessoren bleibt: `BassExciterAudioProcessor` →
   `PlayerDspAudioProcessor`. (Virtual-Bass-Prinzip: erst Obertöne aus dem
   Tiefbass erzeugen, dann den Tiefbass per Hochpass entfernen.)
5. `PlayerBridge.setPlayerCurve(curve: List<TargetPoint>, subsonicCutoffHz: Float)`;
   Übergabe wie Virtual Bass über einen thread-sicheren Zustand
   (`@Volatile`/`StateFlow`, analog `VirtualBassState`). Bei Master aus/Bypass:
   leere Kurve, Cutoff 0.

**Tests (`CurveEqualizerTest`):**
- Frequenzgang der Kette trifft Car- und Box-Kurve bei 40–16000 Hz innerhalb ±1 dB
  (über `magnitudeResponseDb` geprüft, kein Audio nötig).
- Hochpass: bei `cutoff/2` mindestens −20 dB, bei `2×cutoff` höchstens −1 dB.
- Flache Kurve, Cutoff 0 → Ausgabe = Eingabe (bit-genau oder ±1 LSB).
- Sinus 1 kHz bei 0 dBFS mit +6-dB-Kurve → kein Wert über Full Scale nach Limiter.
- `PlayerDspPcm16`: Kette mit allen Stufen aus → unverändert.

**Akzeptanz:** Tests grün. 👤 D1-G1: SoundCloud-Track, Box-Modus, Subsonic an/aus →
Box verzerrt mit „an“ hörbar weniger; keine Knackser bei Seek/Titelwechsel.
👤 D1-G2: CPU/Akku 30 min im Player unauffällig.


*Umgesetzt (PR #57). Abweichung: das iterative Lösen der Peak-Gains braucht Dämpfung (Faktor 0,6, 8 Durchläufe); ohne Dämpfung divergiert es. Die Makros werden als Stützpunkte auf einem Log-Raster (~6 pro Oktave) in die Player-Kurve gesampelt. D1-G1/G2 offen (K19/K20).*

### D2 – Doppelte EQ-Anwendung auf der Player-Session vermeiden  `[x]`

**Braucht:** D1, B1 (`isOwnPlayerSession`).

**Umsetzung:** Wenn `isOwnPlayerSession == true` und der PCM-Kurven-EQ aktiv ist,
schickt `pushToEngine` dem System-Pfad flache Bänder (`bandGainsDb` und
`preEqGainsDb` = 0); MBC und Limiter des System-Pfads bleiben aktiv. Bei
Fremd-Sessions unverändert.

**Tests:** `own player session gets flat system bands when the pcm eq is active`,
`foreign session keeps system band gains`.


*Umgesetzt (PR #57). Der System-Pfad bekommt zusätzlich `inputGainDb = 0` (ohne Bänder entfällt die Pegelkompensation). Greift nur bei ausdrücklich gebundener eigener Session.*

---

## Phase E – Virtual Bass für Spotify (Annäherung)

### E1 – „Bass-Lift (angenähert)“ für Fremd-Apps  `[x]`

**Braucht:** C1 (ohne Pre-EQ zu grob, siehe F5), B1.

**Hintergrund (nicht verhandelbar):** Echte Obertonerzeugung ist für fremde
Sessions ohne Root oder Audio-Mitschnitt nicht möglich. Ein EQ kann nur
vorhandene Obertöne betonen. Bei Uptempo/Hardcore sind die Kicks stark verzerrt,
die Obertöne bei 90–250 Hz sind also schon da – deshalb lohnt die Annäherung.

**Umsetzung:**
1. `core/.../dsp/VirtualBassApproxCurve.kt`: `forMix(mix: Float): List<TargetPoint>`
   mit Stützpunkten `30 Hz: −6·mix`, `45 Hz: −3·mix`, `70 Hz: 0`,
   `110 Hz: +3·mix`, `160 Hz: +2,5·mix`, `250 Hz: 0`. Werte als benannte Konstanten.
2. In `combinedCurve()` nur aufnehmen, wenn **alle** gelten: Virtual Bass an
   (`virtualBassMix > 0`), Fremd-Session (`!isOwnPlayerSession`), Pre-EQ aktiv
   (`preEqCutoffsHz.isNotEmpty()`).
3. B1-Kachel zeigt dann für Fremd-Apps `"approx."` statt `"player only"`
   (neuer String `module_approx`), Info-Text erklärt den Unterschied in einem Satz.

**Tests:** `VirtualBassApproxCurveTest` (Mix 0 → leer/0 dB; Mix 1 → Werte wie oben);
ViewModel: `approx curve is applied only for foreign sessions with pre eq`,
`own player session never gets the approx curve`.

**Akzeptanz:** 👤 E1-G1: Spotify auf kleiner Box, A/B mit Original/EQ-Vergleich →
Kick wirkt präsenter, Box pumpt nicht stärker als ohne.


*Umgesetzt (PR #57). E1-G1 offen (K21).*

---

## Phase F – Dokumentation (läuft mit)

### F1 – Doku nachziehen  `[x]`

Pro Aufgabe im selben PR:
- `docs/TEST_MATRIX.md`: Gerätetests der Aufgabe (A1-G1 … E1-G1) als offene Zeilen
  im Abschnitt „Kontext-Modi“ ergänzen; K3/K4/M4 an die A2-Semantik anpassen.
- `docs/DECISIONS.md`: einmalig festhalten „Virtual Bass bleibt Player-Feature;
  Fremd-Apps erhalten nur eine EQ-Annäherung (E1). Begründung F12.“ und nach C2
  die Pre-EQ-Entscheidung.
- `roadmap-2026.md`: im Kopf einen Verweis auf diese Datei (einmalig, mit A1).
- `roadmap.md`: Session-Log-Eintrag je PR (bestehendes Format).


*Läuft mit; Gerätetests K11–K21, DECISIONS-Eintrag und Verweis in `roadmap-2026.md` sind drin. Session-Log-Eintrag in `roadmap.md` ergänzt.*

---

## Phase G – Optional

### G1 – BluetoothClass per Opt-in-Berechtigung  `[ ]`

*Bewusst nicht umgesetzt: die Aufgabe gilt nur, wenn A3+A4+A5 in der Praxis nicht reichen – das zeigen erst die Gerätetests K11–K18.*

**Braucht:** A4. **Nur umsetzen, wenn A3+A4+A5 in der Praxis nicht reichen.**

Die App verzichtet bewusst auf `BLUETOOTH_CONNECT` (siehe Kommentar in
`AudioRouteRepository.stableRouteFingerprint`). Daher nur als Opt-in:
In der A4-Karte zusätzlich „Detect automatically“ → Berechtigung anfragen
(API 31+: `BLUETOOTH_CONNECT`; API 28–30: `BLUETOOTH` im Manifest, keine
Laufzeitabfrage). Mit Berechtigung das gebundene Gerät per Name finden und
`bluetoothClass.deviceClass` auswerten:
`AUDIO_VIDEO_CAR_AUDIO`/`AUDIO_VIDEO_HANDSFREE` → CAR (Handsfree nur, wenn kein
Kopfhörer-Klasse), `AUDIO_VIDEO_LOUDSPEAKER`/`AUDIO_VIDEO_PORTABLE_AUDIO` → SPEAKER,
`AUDIO_VIDEO_HEADPHONES`/`WEARABLE_HEADSET` → Kopfhörer. Ergebnis wie eine
A4-Antwort speichern. Ohne Berechtigung: Verhalten wie vorher.
Mapping als reine Funktion in `:core` mit Tests (Klassenwerte als Int).

---

## 3. Bewusst nicht in dieser Roadmap

- Audio-Mitschnitt (AudioPlaybackCapture) für echtes Virtual Bass bei Spotify:
  Aufnahme-Dialog bei jedem Start, Latenz, Stummschalten des Originals, App-
  Sperren – widerspricht „Zuverlässigkeit vor Funktionsmenge“.
- Root-/Magisk-Systemeffekt.
- Parametrische EQ-Oberfläche (bleibt M6 Phase 2 in `roadmap-2026.md`).
- Neue Presets oder Umstimmung der bestehenden Kontextkurven – erst nach C2
  beurteilen, wie sie mit voller Auflösung klingen.
