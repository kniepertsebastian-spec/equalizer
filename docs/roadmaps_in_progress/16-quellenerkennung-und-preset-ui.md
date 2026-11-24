# Roadmap 16 – Quellenerkennung, Auto-Switch & Preset-UI mit Ausgrauen

**Priorität:** Hoch (Verhindert versehentliches Abspielen starker Spotify-Presets im eigenen Player + klare UX)  
**Ziel:**  
1. In `EqController` eine eindeutige, zuverlässige Quellenerkennung (`ActivePlaybackSource`: `OWN_PLAYER`, `EXTERNAL_APP`, `NONE`) einführen und die bestehende Session-Lücke (`isOwnPlayerSession` vs. `pcmEqualizerOwnsTheCurve`) schließen.  
2. Beim Wechsel zwischen eigenem Player und Spotify automatisch das passende Quellen-Preset (oder das Gegenstück aus RM 15) aktivieren.  
3. In der Preset-UI (**Equalizer** & **SoundSheet**) Presets klar in **„Eigener Player“** und **„Spotify & externe Apps“** trennen und nicht zur erkannten Quelle passende Presets **visuell ausgrauen**.

**Abhängigkeit:** **Roadmap 15** abgeschlossen (**Welle 3** – parallel zu **Roadmap 17** möglich, lediglich getrennte Blöcke in `strings.xml` abstimmen).

---

## Ausgangslage / Befund

1. **Inkonsistente Session-Prüfung in `EqController.kt`:**
   - `isOwnPlayerSession` (`EqController.kt:160-163`) liefert bei `activeSession == null` bereits `true`.
   - `pcmEqualizerOwnsTheCurve()` (`EqController.kt:1317-1318`) prüft dagegen strikt `activeSession?.packageName == BuildConfig.APPLICATION_ID`.
   - **Folge:** Beim App-Start oder Session-Wechsel ohne gemeldete Session-ID kann der System-EQ aktiv bleiben, während `pushPlayerCurve()` gleichzeitig den Player-PCM-EQ speist (Doppel-EQ-Risiko), und die UI zeigt bereits fälschlich „Eigener Player“ an.
2. **Kein Schutz beim Quellenwechsel:** Wer auf Spotify ein starkes Bass-Preset nutzt und danach in den eigenen Player wechselt, schiebt dasselbe Spotify-Preset ungebremst durch die Player-DSP-Kette auf die Boxen.
3. **Keine Trennung / kein Ausgrauen in der UI:** `GenrePresetList.kt`, `PresetPickers.kt` und `SoundSheet.kt` mischen bisher alle Presets ohne Quellenkontext.

---

## Zugewiesener Dateiumfang

- `app/src/main/java/com/auracurve/app/eq/EqController.kt`
- `app/src/main/java/com/auracurve/app/ui/main/MainViewModel.kt`
- `app/src/main/java/com/auracurve/app/ui/equalizer/GenrePresetList.kt`
- `app/src/main/java/com/auracurve/app/ui/equalizer/PresetPickers.kt`
- `app/src/main/java/com/auracurve/app/ui/equalizer/EqualizerScreen.kt`
- `app/src/main/java/com/auracurve/app/ui/player/sound/SoundSheet.kt`
- `app/src/main/res/values/strings.xml` *(nur neue Preset-/Quellen-Strings, deutlich kommentierter Block)*
- Tests: `EqControllerTest.kt`, `MainViewModelTest.kt`, `EqualizerScreenUiTest.kt` *(Golden-Files prüfen/aktualisieren, falls betroffen)*

*Nicht anfassen:* `:core/preset` (abgeschlossen in RM 14/15) und `ui/player/library/*` (gehört zu RM 17).

---

## Arbeitspakete

### P16-1 – `ActivePlaybackSource` & Doppel-EQ-Fix in `EqController`
- [x] Explizites Modell einführen:
  ```kotlin
  sealed interface ActivePlaybackSource {
      data object None : ActivePlaybackSource
      data object OwnPlayer : ActivePlaybackSource
      data class ExternalApp(val packageName: String) : ActivePlaybackSource
  }
  ```
- [x] `activePlaybackSource: StateFlow<ActivePlaybackSource>` aus `sessionRepository.activeSession` (und optional aktivem Player-Playback-Status) ableiten.
- [x] `isOwnPlayerSession` und `pcmEqualizerOwnsTheCurve()` auf dieselbe Wahrheitsquelle (`activePlaybackSource`) stützen, sodass niemals gleichzeitig System-EQ (`preEq`/`postEq`/`bandGainsDb`) und `PlayerDspChain` die Zielkurve anwenden.

### P16-2 – Automatischer Quellen-Preset-Wechsel (Boxenschutz)
- [x] Letztes gewähltes Preset pro Quelle (`OWN_PLAYER` / `EXTERNAL_APPS`) persistieren (z. B. über `LiveSettings` / `AppSettingsRepository` oder bestehendes `AppProfileRepository`).
- [x] Bei Wechsel der `ActivePlaybackSource`:
  - Wechselt die Quelle auf `OwnPlayer` und das aktive Preset hat `sourceScope == EXTERNAL_APPS`: automatisch auf das zuletzt genutzte `OWN_PLAYER`-Preset oder das deterministische Gegenstück (`GenrePresetGroups.counterpartPresetId(...)` aus RM 15) umschalten.
  - Analog beim Wechsel auf `ExternalApp` (Spotify etc.), falls aktuell ein `OWN_PLAYER`-Preset aktiv ist.
  - `PresetSourceScope.ALL` (z. B. `Flat` oder universelle Custom-Presets) bleibt unverändert aktiv.

### P16-3 – UI-Trennung & Ausgrauen nicht passender Presets
- [x] In `GenrePresetList.kt`, `PresetPickers.kt` und `SoundSheet.kt` die Presets klar nach **„Eigener Player“** und **„Spotify & externe Apps“** strukturieren (z. B. Segment-Switch / klar beschriftete Sektionen mit Status-Badge der aktuell erkannten Quelle).
- [x] **Ausgrau-Logik:**
  - `ActivePlaybackSource.OwnPlayer` → alle `EXTERNAL_APPS`-Presets sind sichtbar ausgegraut (`alpha = 0.38f`), als inaktiv markiert und gegen versehentlichen Tap geschützt (Hinweis-Badge/Toast: „Nur aktiv bei Spotify / externen Apps“).
  - `ActivePlaybackSource.ExternalApp` → alle `OWN_PLAYER`-Presets analog ausgegraut („Nur aktiv im eigenen Player“).
  - `ActivePlaybackSource.None` → beide Gruppen bleiben voll sichtbar und wählbar (Vorauswahl möglich), jeweils mit klarem Quellen-Tag.
  - `PresetSourceScope.ALL` (z. B. `Flat`) ist nie ausgegraut.
- [x] **Custom-Presets speichern:** Im „Als neues Preset speichern“-Dialog (`EqualizerScreen.kt` / `PresetPickers.kt`) eine einfache Auswahl für `PresetSourceScope` (`Eigener Player` / `Spotify & Extern` / `Beide`, Vorauswahl = aktuell erkannte Quelle) anbieten.
- [x] **Barrierefreiheit (a11y):** Alle neuen Texte in `strings.xml`, `stateDescription` / `contentDescription` für ausgegraute Presets und Quellen-Badges, Touch-Targets mind. `48x48 dp`.

---

## Abnahmekriterien & Verifikation

- [x] **Unit-Tests `:app`:**
  - `EqControllerTest`:
    - `activeSession == null` führt zu `ActivePlaybackSource.None` (kein stilles Doppel-EQ-Fenster).
    - Wechsel von `ExternalApp("com.spotify.music")` zu `OwnPlayer` schaltet ein `EXTERNAL_APPS`-Preset automatisch auf das passende `OWN_PLAYER`-Preset um.
    - `Flat` (`ALL`) bleibt beim Quellenwechsel stabil.
  - `EqualizerScreenUiTest`:
    - Bei `ActivePlaybackSource.OwnPlayer` sind Spotify-/External-Presets als ausgegraut/deaktiviert dargestellt und lösen keine ungewollte Aktivierung aus.
    - Bei `ActivePlaybackSource.ExternalApp` sind Player-Presets entsprechend ausgegraut.
    - Bei `ActivePlaybackSource.None` sind beide Gruppen bedienbar.
- [x] **Build & Lint:**
  ```bash
  ./gradlew :app:testFullDebugUnitTest :app:testStoreDebugUnitTest :app:ktlintCheck
  ```
- [ ] **Gerätetest:**
  - Spotify abspielen → Player-Presets sind ausgegraut, Spotify-Presets aktiv.
  - Wechsel zum eigenen Player → automatischer Wechsel auf boxensicheres Player-Preset, Spotify-Presets sind sofort ausgegraut.

---

## Umsetzungsstand

**Commit:** wird nach dem Commit eingetragen (siehe Folge-Commit „Roadmap 16: record commit id“).

**Status:** umgesetzt, Gerätetest offen. Baut auf RM 14/15 in `main` auf.

### Geänderte Dateien
- `app/.../eq/EqController.kt`
  - Neues Modell `ActivePlaybackSource` (`None`, `OwnPlayer`, `ExternalApp(packageName)`) mit `scope`
    (`ALL` / `OWN_PLAYER` / `EXTERNAL_APPS`) und `ActivePlaybackSource.of(session, ownPlayerPlaying)`.
  - `activePlaybackSource: StateFlow` aus `sessionRepository.activeSession` **und** dem Wiedergabestatus
    des eigenen Players. Der spielende (oder ladende) eigene Player gewinnt – auch wenn noch die (pausierte)
    Spotify-Session angehängt ist (`AudioSessionRepository` behält die erste offene Session als aktive).
  - `isOwnPlayerSession`, `pcmEqualizerOwnsTheCurve()`, `CurveInputs.ownPlayerSession`, Player-Kurve und
    Virtual Bass lesen alle `activePlaybackSource`:
    - `OwnPlayer` → System-EQ flach (`bandGainsDb`/`preEq`/`postEq` = 0), Kurve nur im Player-PCM-EQ.
    - `ExternalApp` → Kurve nur im System-EQ; der Player bekommt **keine Kurve und keinen Virtual Bass**.
    - `None` → kein System-EQ angehängt (Engine `detach()`), der Player ist vorbereitet, damit ein Start
      aus dem Player ab dem ersten Puffer mit Kurve läuft.
  - P16-2 `fitPresetToSource()`: nach jedem Quellenwechsel und nach jeder automatischen Preset-Anwendung
    (Restore, Routen-Profil, App-Profil). Passt das aktive Preset nicht zur Quelle, wird es ersetzt durch
    1. das deterministische Gegenstück (`GenrePresetGroups.counterpartPresetId`), sonst
    2. das zuletzt aus dieser Quelle gespielte Preset, sonst
    3. `Flat`.
    `ALL`-Presets (Flat, Custom „Beide“) bleiben, bei `None` passiert nichts. Der Wechsel wird nicht in
    Routen-/App-Bindung gespeichert (Entscheidung der Quelle, nicht des Nutzers) und als
    `sourcePresetSwitches` gemeldet.
  - Letztes Preset je Quelle wird in `persistLiveSettings()` gemerkt und in `LiveSettings` gespeichert.
  - Schnellkachel „Nächstes Preset“ blättert nur durch Presets der aktuellen Quelle.
  - `saveAsNewPreset(name, sourceScope)` + `defaultScopeForNewPreset()` (erkannte Quelle, sonst Scope des
    aktiven Presets).
- `app/.../ui/main/MainViewModel.kt` – `activePlaybackSource`, `defaultScopeForNewPreset()`,
  `saveAsNewPreset(name, scope)`, Feedback-Meldung beim automatischen Wechsel.
- `app/.../ui/equalizer/GenrePresetList.kt` – `PresetSourceTabs` (Status der erkannten Quelle in Worten +
  Punkt, Segment „Own player“ / „Spotify & other apps“, Hinweis-Banner bei der nicht aktiven Liste),
  `initialPresetSourceTab`, `inactiveReason`; Zeilen nicht passender Presets: `alpha = 0.38f`,
  `enabled = false` (kein Tap), sichtbares Hinweis-Badge, `stateDescription`; Menü (Info/Duplizieren/
  Teilen) bleibt bedienbar und ist im SoundSheet ohne Bearbeiten-Aktionen.
- `app/.../ui/equalizer/PresetPickers.kt` – Custom-Presets ebenfalls ausgegraut; neuer `SavePresetDialog`
  mit Auswahl `Own player` / `Spotify & other apps` / `Both` (Vorauswahl = erkannte Quelle).
- `app/.../ui/equalizer/EqualizerScreen.kt` – Parameter `playbackSource`; Genre-Liste über
  `GenrePresetGroups.forSource(tab)` (statt `forGenres`), Custom-Presets nach Quelle gefiltert. Der Tab
  folgt der erkannten Quelle, bei `None` dem Scope des aktiven Presets.
- `app/.../ui/player/sound/SoundSheet.kt` – neue Karte „Preset“ mit derselben Quellen-Umschaltung und
  Liste; dafür `SoundPresetViewModel` (Hilt, direkt auf `EqController`).
- `app/src/main/res/values/strings.xml` – kommentierter Block „Roadmap 16“ am Dateiende (nur Preset-/
  Quellen-Strings).
- Tests: `EqControllerTest` (13 neue Tests), `EqTestFakes` (`EqFakePlayerBridge.playing`),
  `MainViewModelTest` (1), `EqualizerScreenUiTest` (5), Golden `e008-golden/player-and-compare.txt`.

### Abweichungen vom Dateiumfang (nötig, klein)
- `integration/PlayerBridge.kt` / `AndroidPlayerBridge.kt`: `ownPlayerPlaying: Flow<Boolean>` (Default
  `false`; Android-Implementierung aus `NowPlayingState`, `isPlaying` ist dort schon beim Laden wahr).
- `settings/LiveSettings.kt`: `lastOwnPlayerPresetId`, `lastExternalAppsPresetId` (beide mit Default,
  alte Daten bleiben lesbar) – in der Roadmap als Option genannt.
- `ui/MainScreen.kt`: reine Verdrahtung (`playbackSource`, `SavePresetDialog` statt `PresetNameDialog`
  beim Speichern).
- `test/resources/e008-golden/player-and-compare.txt`: bewusst neu aufgenommen. Unterschiede nur dort,
  wo RM 16 sie verlangt: bei fremder Session ist die Player-Kurve leer; beim Wechsel auf den eigenen
  Player wird „Uptempo – Deep Rumble“ (System-EQ) durch „Uptempo – Player“ ersetzt (Limiter −1,0 dB,
  Subsonic 38 Hz, MBC 2,6:1). Die anderen drei Golden-Szenarien sind unverändert.

### Entscheidungen
- **Gegenstück vor Erinnerung:** Die Roadmap nennt „zuletzt genutztes Preset oder Gegenstück“. Gewählt
  ist zuerst das Gegenstück – der Klang folgt dem Genre/Use Case, das gerade gewählt wurde (Spotify
  „Uptempo – Loud“ → Player „Uptempo – Loud“ und zurück). Die Erinnerung je Quelle greift, wo es kein
  Gegenstück gibt (player-only Genres aus RM 14, Custom-Presets einer Quelle), danach Flat.
- **`isOwnPlayerSession` bleibt bei `None` wahr** (keine Virtual-Bass-Näherung, kein „nur im Player“-Hinweis),
  denn ohne Session ist kein System-EQ angehängt. Die Quelle selbst ist jedoch klar `None`; die UI zeigt
  „No playback detected“ statt „Eigener Player“. Das Doppel-EQ-Fenster ist geschlossen, weil System-
  und Player-Seite jetzt dieselbe Quelle lesen und der Player bei fremder Quelle gar keine Kurve bekommt.
- **Manuelle Auswahl wird nicht umgebogen:** Ausgegraute Presets sind in der UI nicht antippbar; die
  Remote-API (`EqRemoteApi`, nicht im Umfang) kann weiterhin jedes Preset setzen. Beim nächsten
  Quellenwechsel greift der Boxenschutz.
- Hinweis statt Toast: der Grund steht dauerhaft am ausgegrauten Preset und als Banner über der Liste;
  ein automatischer Wechsel meldet sich über das bestehende Feedback (Snackbar).

### Nachweise
- `./gradlew :app:testFullDebugUnitTest :app:testStoreDebugUnitTest :app:ktlintCheck :core:test
  lintFullDebug lintStoreDebug` lokal grün (Android-SDK 37 im Cloud-Container installiert).
- Screenshots (Robolectric, nicht eingecheckt): `app/build/equalizer-screenshots/12-own-player-spotify-presets-greyed.png`,
  `13-own-player-presets.png`.

### Offen
- **Gerätetest:** Spotify abspielen → Player-Presets ausgegraut; Wechsel zum eigenen Player →
  automatischer Wechsel auf das Player-Preset, Spotify-Presets sofort ausgegraut; zurück zu Spotify.
- `EqRemoteApi` meldet/akzeptiert Presets ohne Quellenprüfung (siehe RM 15 „Offen“).

