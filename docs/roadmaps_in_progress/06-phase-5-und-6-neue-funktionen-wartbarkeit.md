# Roadmap 06 – Neue Funktionen und Wartbarkeit

**Modell-Empfehlung:** Sonnet 5.5 für die Umsetzung; Opus 5.5 für Architekturprüfung bei größeren Refactorings.  
**Ziel:** Die ausgewählten neuen Funktionen und die technische Absicherung umsetzen.  
**Abhängigkeit:** Nach den grundlegenden Player- und Audioarbeiten; Phase 3 erst nach Planfreigabe umsetzen.
**Status:** Umgesetzt im Code, Unit-Tests und Lint lokal grün. **Noch offen:** alle Gerätetests und Hörproben (siehe Abschnitt „Offen“ unten und `docs/TEST_MATRIX.md`, K29–K41). Deshalb noch nicht nach `roadmaps_finished` verschoben.

Legende: `[x]` im Code und per Test nachgewiesen · `[~]` teilweise, Rest steht dabei · `[ ]` offen

## Phase 5 – Neue Funktionen

| ID | Funktion | Aufwand |
|---|---|---|
| P5-1 | Schnelleinstellungs-Kachel und Widget: EQ an/aus, Preset wechseln | S |
| P5-2 | Mehr fremde Apps erkennen: optionale Freigabe per Shizuku/ADB (`DUMP`), wie es Wavelet anbietet, für Apps ohne Session-Broadcast | M |
| P5-3 | Parametrischer Modus: Frequenz, Güte und Verstärkung je Band frei wählbar | M |
| P5-4 | Presets teilen per Link oder QR-Code; Import von AutoEq- und Wavelet-Dateien | S–M |
| P5-5 | Profile je App (Spotify anders als YouTube), zusätzlich zu den Profilen je Gerät | M |
| P5-6 | Sleep-Timer und Überblenden zwischen Titeln | S–M |
| P5-7 | Lokale Dateien als zweite Quelle im eigenen Player: volle DSP-Kette, unabhängig von SoundCloud | M |
| P5-8 | Bluetooth-Codec und Abtastrate im Diagnosebericht anzeigen | S |

---

## Phase 6 – Wartbarkeit und Absicherung

- **P6-1 Große Dateien teilen (M):** `MainViewModel.kt` (1.448 Zeilen), `EqualizerScreen.kt` (1.451), `PlayerViewModel.kt` (834). Kurvenberechnung, Geräteprofile und Vergleichsmodus in eigene Klassen.
- **P6-2 `:player` angleichen (M):** Versionen stehen fest im Modul statt im Versionskatalog, Lint ist abgeschaltet (`abortOnError = false`), kein Hilt, Zustand in globalen Objekten (`PlayerDspState`, `NowPlayingState`, `PlayerCurveState`).
- **P6-3 Gerätetests (M):** Es gibt 72 Testdateien, aber keinen einzigen Test auf einem Gerät. Mindestens: Service-Start aus dem Hintergrund, Attach an eine Session, Room-Migration.
- **P6-4 Absturzgrund über Neustarts merken (S, alt: MED-04):** `ActivityManager.getHistoricalProcessExitReasons()` in den Diagnosebericht.
- **P6-5 Roadmaps zusammenführen (S):** Fünf weitere Roadmap-Dateien mit zusammen rund 4.200 Zeilen (`docs/roadmap*.md`, `roadmap-kontext-dsp.md`). Erledigtes ins Archiv, eine gültige Datei.
- **P6-6 R8 für Release (M):** Macht keinen Klangunterschied. Bringt ein kleineres APK und schnellere Schleifen im Audio-Thread; braucht Keep-Regeln und einen Gerätetest.
- **P6-7 Statusangaben prüfen (S):** Ein Punkt gilt erst als erledigt, wenn der Code auf `main` es zeigt (siehe P0-1).

---

## Abschlusskriterien
- Jede Funktion und jedes Refactoring ist separat getestet und dokumentiert.
- Große Dateien werden schrittweise zerlegt, ohne Verhalten unbeabsichtigt zu ändern.
- Modulkonfigurationen, Dependency-Versionen, Hilt-/State-Architektur und Lint-Einstellungen sind konsistent.
- Gerätetests decken mindestens Service-Start aus dem Hintergrund, Attach an eine Audio-Session und Room-Migration ab.
- Absturzdiagnose, Roadmap-Bereinigung und R8-Release-Konfiguration sind geprüft.
- Statusangaben entsprechen dem tatsächlich auf `main` vorhandenen Code.
- Build, Unit-Tests, Lint und erforderliche Gerätetests sind erfolgreich bzw. offene Fehler sind ausdrücklich dokumentiert.

---

## Umsetzungsstand (13. November 2026)

Geprüft gegen den Code auf diesem Branch (P6-7: ein Punkt zählt erst als erledigt, wenn der Code ihn zeigt). Lokal ausgeführt: `ktlintCheck`, `:core:test` (375 Tests), `:app:testDebugUnitTest` (333), `:player:testDebugUnitTest` (17), `lintDebug`/`lintRelease`, `:app:assembleDebug`, `:app:assembleRelease` (mit R8). Geräte- und Instrumentationstests wurden **nicht** ausgeführt (kein Gerät, kein Emulator in dieser Umgebung).

### Phase 5

- **P5-1 Kachel und Widget** `[x]` Zwei Kacheln (`EqToggleTileService`: EQ an/aus; `NextPresetTileService`: nächstes Preset) und ein Widget (`QuickWidgetProvider`) im Paket `quick/`. Befehle laufen über `QuickControls` (Singleton) in `MainViewModel`; läuft kein ViewModel, öffnet die Kachel die App mit dem Befehl (`QuickCommandIntents`). Tests: `QuickControlsTest` (4), `MainViewModelTest` (+2), `PresetCycleTest` (4). **Grenze:** Widget-Knöpfe sind Broadcasts, solange ein ViewModel lebt, sonst Activity-Intents; das Verhalten auf echten Launchern ist ungeprüft.
- **P5-2 Mehr Apps erkennen** `[~]` Optionaler Weg über `dumpsys audio` mit per ADB erteilter `DUMP`-Berechtigung (`adb shell pm grant com.hardbasseq.eq android.permission.DUMP`): `DumpsysAudioParser` (Core, 6 Tests), `DumpsysSessionScanner`, Merge in `AudioSessionRepository.updateScannedSessions` (4 Tests), Abfrage alle 4 s nur während Wiedergabe. Diagnosebericht zeigt, ob die Freigabe da ist. **Nicht umgesetzt: Shizuku** (zusätzliche Bibliothek und Binder-Anbindung; ADB reicht für denselben Zweck). **Offen:** Das Format von `dumpsys audio` ist nicht stabil; der Parser sucht Tokens (`u/pid:`, `state:`, `session:`) und wurde nur mit nachgebauten Zeilen getestet, nicht mit echter Ausgabe von Samsung/Pixel.
- **P5-3 Parametrischer Modus** `[x]` bis zu 8 freie Bänder (Frequenz, Güte, Verstärkung, an/aus) als `ParametricBand`; `ParametricCurve` wertet sie mit den vorhandenen Biquads zu einer Zielkurve aus, die wie das Korrekturprofil in die gemeinsame Kurve eingeht (`CurveInputs.parametricCurve`). Gespeichert in `LiveSettings.parametricBands`. Tests: `ParametricCurveTest` (7), `MainViewModelTest` (+2). **Grenzen:** Im eigenen Player folgt die Kurve genau; über den System-Equalizer nur so fein wie dessen Bänder (eine schmale Güte wird verschmiert). Die Bänder gehören nicht zu einem gespeicherten Preset, sondern zu den Live-Einstellungen.
- **P5-4 Presets teilen und importieren** `[x]` Teilen als Link `hardbasseq://preset?v=1&d=…` (`PresetShareCodec`, deflate + base64url, Größenlimit gegen Zip-Bomben, Validierung wie beim Datei-Import) und als QR-Code (zxing-core, nur Encoder; `QrCode`, `SharePresetDialog`). Import: Link einfügen oder QR mit der Kamera-App öffnen (Deep-Link in `MainActivity`). AutoEq: `GraphicEQ`-Zeile (damit auch Wavelet-Dateien) war da, neu ist `ParametricEQ.txt` (`ParametricEqTextParser`). Tests: `PresetShareCodecTest` (7), `AutoEqParserTest` (+3), `QrCodeTest` (2), `MainViewModelTest` (+2). **Nicht umgesetzt:** ein Scanner in der App (braucht Kamera-Berechtigung und eine Bibliothek); der Link-Import per Zwischenablage und die Kamera-App decken das ab.
- **P5-5 Profile je App** `[x]` Room-Tabelle `app_profiles` (Migration 2→3, Schema `3.json`), `AppProfileRepository`, Schalter „Use this sound for <App>“ in der Quellenliste. Ein App-Profil gilt, sobald die App aktive Session wird, und gewinnt nach einem Routenwechsel wieder gegen das Geräteprofil. Tests: `MainViewModelTest` (+3), `DatabaseMigrationTest` (Gerät, siehe P6-3).
- **P5-6 Sleep-Timer und Überblenden** `[~]` Sleep-Timer (15/30/45/60/90 min oder Ende des Titels, 15 s Ausblenden, Restzeit-Anzeige) und Ein-/Ausblenden an den Titelrändern (0–12 s) im eigenen Player (`PlaybackFadeController`, `PlaybackFade` im Core, Karte im Sound-Sheet). Tests: `PlaybackFadeTest` (7), `PlaybackFadeControllerTest` (7), `PlaybackTimersCardTest` (2). **Abweichung:** Das ist ein Ausblenden und Einblenden nacheinander, **kein Überlappen** der Titel. Echtes Crossfade braucht einen zweiten Decoder-/Ausgabepfad gleichzeitig; das leistet der einzelne ExoPlayer nicht. Die Oberfläche sagt das (`track_fade_desc`).
- **P5-7 Lokale Dateien** `[x]` „Play files from this device“ im Playlists-Tab (Systemdialog, mehrere Dateien), Metadaten über `MediaMetadataRetriever`. Lokale Titel haben negative Ids (`LocalTrackId`) und spielen von ihrer eigenen Adresse; sie laufen durch dieselbe Audio-Kette (Kurve, Virtual Bass, Mono-Bass, Limiter) wie SoundCloud, weil die Kette im Audio-Sink hängt. Tests: `LocalTrackIdTest` (5), `TrackMediaItemsTest` (2), `PlayerViewModelTest` (+4). **Grenzen:** Lokale Titel lassen sich nicht in gespeicherten Playlists ablegen (die Playlists speichern SoundCloud-Ids); die Queue wird nicht über einen Neustart des Dienstes gerettet.
- **P5-8 Codec und Abtastrate im Diagnosebericht** `[~]` Bericht zeigt Ausgabegerät, native Abtastrate, vom Gerät gemeldete Raten und Encodings (`OutputAudioInfo`, `SystemDiagnosticsSource`, 3 Tests). **Der ausgehandelte Bluetooth-Codec (SBC/AAC/LDAC …) ist über die öffentliche Android-API nicht lesbar** (`BluetoothA2dp.getCodecStatus` ist versteckt); der Bericht schreibt das ausdrücklich in eine Zeile, statt zu raten.

### Phase 6

- **P6-1 Große Dateien teilen** `[~]` Herausgelöst: `CurveCalculator` (Kurve, 34-Band-Pre-EQ, Vergleichspegel; 7 Tests) und `RouteContextResolver` (Kontextmodus und Geräteart-Frage je Route; 8 Tests) aus `MainViewModel` (−111 Zeilen); `EqualizerScreen.kt` 1.451 → 925 Zeilen (`PresetPickers.kt`, `EqualizerControls.kt`, mechanisch verschoben, kein Code geändert). Die bestehenden `MainViewModelTest`-Tests liefen unverändert grün. **Ziel verfehlt für `MainViewModel`:** 1.440 → 1.532 Zeilen, weil die neuen Funktionen dieses Pakets (App-Profile, parametrische Bänder, Schnellbefehle, Preset-Link; zusammen rund 200 Zeilen) dort hineinkamen. `PlayerViewModel.kt` (957 Zeilen, vorher 928) ist nicht geteilt. Die Geräteprofil-Persistenz (`applyDeviceProfileForRoute`, `saveDeviceProfileBinding`) steckt zu eng im ViewModel-Zustand, um sie ohne Verhaltensrisiko herauszulösen. Nächster Schritt: App-Profile, parametrische Bänder und Schnellbefehle in je eine eigene Klasse, dann die Geräteprofile.
- **P6-2 `:player` angleichen** `[~]` Versionen stehen im Versionskatalog (`gradle/libs.versions.toml`, Werte unverändert), `abortOnError = false` ist entfernt, `lintDebug`/`lintRelease` für `:player` laufen mit 0 Fehlern (vorher 4: 3 `UnstableApi`-Opt-ins, 1 `RestrictedApi`, jetzt behoben bzw. mit Begründung unterdrückt). **Offen:** Hilt und die globalen Objekte (`PlayerDspState`, `NowPlayingState`, `PlayerCurveState`) bleiben; Begründung und Etappenplan in `docs/adr/0004-player-state-and-hilt.md`.
- **P6-3 Gerätetests** `[~]` Geschrieben und kompiliert (`:app:compileDebugAndroidTestKotlin`): `DatabaseMigrationTest` (1→2, 2→3, 1→3), `SessionServiceBackgroundStartTest`, `EngineAttachToSessionTest` (echte `AudioTrack`-Session). **Nicht ausgeführt** – das braucht ein Gerät oder einen Emulator (`./gradlew :app:connectedDebugAndroidTest`). Bis dahin ist nichts davon als bestanden zu betrachten.
- **P6-4 Absturzgrund über Neustarts** `[x]` `ActivityManager.getHistoricalProcessExitReasons` im Diagnosebericht („Last Process Exit“ und Liste der letzten fünf), `ProcessExitRecord`, `SystemDiagnosticsSource`. Tests: `DiagnosticsReportFormatterTest` (+7). Echte Exit-Info-Einträge sind nur auf einem Gerät zu sehen.
- **P6-5 Roadmaps zusammenführen** `[x]` Die doppelten Root-Dateien `roadmap.md` und `roadmap-kontext-dsp.md` sind entfernt (Kopien lagen schon unter `docs/roadmaps_finished/`); `README.md` und `GEMINI.md` verweisen auf `docs/roadmaps_in_progress/00-INDEX.md` als einzige gültige Datei. Kommentare im Code, die „roadmap.md“ nennen, meinen das archivierte Session-Log und bleiben.
- **P6-6 R8 für Release** `[~]` `isMinifyEnabled` und `isShrinkResources` an, Regeln in `app/proguard-rules.pro` (Serialisierung, Room-Daten, lesbare Stacktraces). `:app:assembleRelease` läuft durch, das unsignierte APK hat rund 7,3 MB (Debug: 88 MB); Manifest-Komponenten sind im DEX vorhanden. **Offen:** ein Lauf der signierten Release-APK auf einem Gerät (Start, Session-Attach, Preset speichern/laden, Teilen/Import, Tile, Player), bevor ein Release damit gebaut wird (TEST_MATRIX K41).
- **P6-7 Statusangaben prüfen** `[x]` Auf diese Roadmap angewendet (dieser Abschnitt). Die Kästchen in 01–05 wurden nicht erneut gegen den Code geprüft.

### Offen (nicht abgeschlossen)

1. Gerätetests und Hörproben: `docs/TEST_MATRIX.md`, K29–K41.
2. Instrumentationstests ausführen (P6-3).
3. `PlayerViewModel` teilen; Hilt/State in `:player` (ADR 0004).
4. Shizuku (P5-2), echtes Überlappen (P5-6), Bluetooth-Codec (P5-8, nur mit DUMP-Freigabe überhaupt denkbar).

### Geänderte gemeinsam genutzte Dateien

`MainViewModel`, `EqualizerScreen`, `AudioSessionRepository`, `AudioSessionForegroundService`, `AppDatabase` (Version 2 → 3), `AndroidManifest.xml` (Kacheln, Widget, Deep-Link, `DUMP`), `AudioPlayerService`, `TrackMediaItems`, `gradle/libs.versions.toml`, `player/app/build.gradle.kts`, `app/build.gradle.kts`.
