# Roadmap 01 – Sofort: Fehler und Altlasten

**Modell-Empfehlung:** Sonnet 5.5  
**Ziel:** Die sechs Punkte aus Phase 0 der Gesamt-Roadmap isoliert beheben.  
**Abhängigkeit:** Keine. Diese Roadmap zuerst ausführen.  
**Status:** Umgesetzt, CI grün (PR #63). **Noch offen:** Gerätetest und Hörprobe, siehe Abschlussbericht. Deshalb noch nicht nach `roadmaps_finished` verschoben.

Legende: `[x]` nachgewiesen · `[ ]` offen

## Phase 0 – Sofort: Fehler und Altlasten (klein, hoher Nutzen)

### P0-1 Zurückgerollten 34-Band-Fix wiederherstellen (S)
- **Befund:** Die alte Roadmap führt HIGH-02 (logarithmisches Raster ohne 5-Hz-Abstände) als erledigt. Der Commit `9409917` („ci red fix", 28.10.2026) hat ihn wieder entfernt. `PreEqLayout.CUTOFFS_HZ` enthält erneut `31, 36, 40, 45, 50, 55 Hz` und `LOWEST_HZ = 15f` (`core/.../dsp/PreEqMapper.kt`).
- **Folge:** Die Bassbänder liegen enger als die FFT-Auflösung von `DynamicsProcessing` (bei 20 ms Frame rund 23 Hz). Mehrere Bänder teilen sich ein Bin, die Kurve im Bass ist dadurch unschärfer, als die Oberfläche zeigt.
- **Fertig, wenn:**
  - [x] Nachbarbänder mindestens rund 18 % auseinanderliegen (`PreEqLayout.MIN_EDGE_RATIO = 1.18`)
  - [x] `PreEqMapperTest` das prüft (jede Bandkante, zusätzlich: keine 5-Hz-Schritte im Bass)
  - [x] CI grün (Run 243)

### P0-2 Queue läuft bei Netzausfall komplett durch (S)
- **Befund:** `onPlayerError` ruft ohne Prüfung der Fehlerart `onTrackUnplayable()` auf, das sofort den nächsten Titel startet (`AudioPlayerService.kt`). Ohne Netz schlägt jeder Titel fehl, die ganze Queue wird mit Toasts übersprungen.
- **Fertig, wenn:**
  - [x] Netzfehler pausieren und mit Wartezeit erneut versuchen (Logik: `PlaybackFailurePolicyTest`, 13 Tests)
  - [x] nur echte „Titel nicht abspielbar"-Fälle springen weiter, höchstens dreimal hintereinander (Logik: `PlaybackFailurePolicyTest`)
  - [x] Service-Verdrahtung kompiliert, CI grün
  - [ ] **Gerätetest:** Flugmodus während der Wiedergabe einer Queue schalten – es darf kein Titel übersprungen werden, nach dem Ausschalten läuft derselbe Titel weiter

### P0-3 Benachrichtigung öffnet den alten XML-Player (S)
- **Befund:** `setSessionActivity` und der Content-Intent der Benachrichtigung zeigen auf `PlayerActivity` (altes View-Layout aus dem früheren Audioplayer-Repo), nicht auf den Compose-Player in `MainActivity`.
- **Fertig, wenn:**
  - [x] Benachrichtigung (Content-Intent) und Media-Session (`setSessionActivity`, Sperrbildschirm/Android Auto) zeigen im Code auf `PlayerLaunch.ACTION_OPEN_PLAYER`, den `MainActivity` beantwortet
  - [x] `PlayerActivity`, `activity_player.xml` und `TrackAdapter` gelöscht (zusätzlich `item_track_playlist.xml`, Manifest-Eintrag, 5 ungenutzte Strings, `viewBinding`)
  - [x] Build, Unit-Tests und Lint grün (CI)
  - [ ] **Gerätetest:** Tippen auf Benachrichtigung, Sperrbildschirm-Karte und Android Auto öffnet den Compose-Player (App im Hintergrund, App beendet und App im Vordergrund)

### P0-4 Build-Ausgabe aus dem Repo entfernen (S)
- **Befund:** 6.494 Dateien unter `app/build`, `core/build`, `player/app/build` und `build/` sind eingecheckt, ebenso `local.properties` mit einem lokalen Pfad. `.gitignore` listet beides, die Dateien wurden aber vorher hinzugefügt.
- **Fertig, wenn:**
  - [x] `git rm -r --cached` für diese Pfade gelaufen ist
  - [x] `git ls-files | grep -E '(^|/)build/'` leer bleibt (zusätzlich: `git ls-files -ci --exclude-standard` ist leer)

### P0-5 Virtual Bass: wirkungslosen Drive-Regler und Filterlage korrigieren (S–M)
- **Befund 1:** `abs(x · drive) / drive` ist identisch mit `abs(x)`. Der Parameter `drive` in `BassExciter.kt` ändert rechnerisch nichts.
- **Befund 2:** Der Hochpass hinter dem Gleichrichter sitzt auf derselben Frequenz wie der Tiefpass davor (150 Hz). Die Oktave über einem 40-Hz-Grundton (80 Hz) wird damit um rund 11 dB gedämpft, während Obertöne von 100–150-Hz-Tönen fast ungedämpft in den unteren Mitten landen. Der Effekt wirkt also dort am schwächsten, wo kleine Treiber ihn brauchen.
- **Fertig, wenn:**
  - [x] Der Hochpass tiefer liegt (65 Hz)
  - [x] ein Tiefpass die Obertöne nach oben begrenzt (2 × Cutoff = 300 Hz)
  - [x] ein Test den Pegel der zweiten Harmonischen für 40, 60 und 100 Hz festschreibt (`BassExciterTest.secondHarmonicLevel_*`)
  - [x] `drive` wirkt (`BassExciterTest.drive_changesTheHarmonicLevel`; gegen die alte Implementierung rot)
  - [ ] **Hörprobe** am Gerät (kein Hörtest in der Entwicklungsumgebung möglich)
- Siehe auch P4-3.

### P0-6 Ungenutzten Code entfernen (S)
- `app/.../audio/media3/Media3DspPipeline.kt` samt Test (alt: DEBT-02).
- `Crossfeed.kt` ist fertig und getestet, aber nirgends angeschlossen: entweder in Phase 4 nutzen (P4-4) oder löschen.
- Veralteter Kommentar „Media3 1.3.1" in `PlayerDspPcm16.kt`; eingebunden ist 1.11.1.
- **Fertig, wenn:**
  - [x] `Media3DspPipeline` und `Media3DspPipelineTest` entfernt, keine Verweise mehr (grep, CI-Build grün)
  - [x] `Crossfeed.kt` nicht gelöscht; die offene Entscheidung (P4-4) steht im Dateikopf
  - [x] Kommentar in `PlayerDspPcm16.kt` korrigiert (inhaltlich, nicht nur die Versionsnummer)

---

## Abschlusskriterien
- [x] Alle sechs Punkte einzeln geprüft und umgesetzt oder mit konkreter Begründung als nicht umsetzbar dokumentiert.
- [x] Nach jedem Punkt passende Tests ausgeführt (P0-1/4/5/6 und die Fehlerlogik aus P0-2 lokal in `:core`, alles zusammen in der CI).
- [x] `:app:assembleDebug`, relevante Unit-Tests und `ktlintCheck`/`lintDebug` erfolgreich (CI-Run 243: ktlint, assembleDebug, testDebugUnitTest, `:core:test`, lintDebug/lintRelease).
- [x] Git-Status enthält keine unbeabsichtigten Build-Ausgaben oder lokalen Konfigurationsdateien.
- [x] Abschlussbericht und eigener Commit vorhanden.
- [ ] Gerätetest und Hörprobe (siehe oben) – erst danach nach `docs/roadmaps_finished/` verschieben.

## Besondere Abhängigkeit
P0-5 korrigiert zunächst die bestehende Virtual-Bass-Implementierung. Der grundlegende DSP-Neuaufbau erfolgt später in Roadmap 03 (P4-3). P0-6 darf `Crossfeed.kt` nicht voreilig löschen, solange die Entscheidung über P4-4 noch offen ist; die Entscheidung ist als offen zu dokumentieren.

---

## Abschlussbericht

**Branch / PR:** `claude/gifted-mayer-pszodc`, PR #63 (Draft)

### Commits
| Punkt | Commit |
|---|---|
| P0-4 | `531ac67` |
| P0-1 | `df4cc87` |
| P0-5 | `07a5730` |
| P0-6 | `cfc1c5a` |
| P0-2 + P0-3 | `38b78a8` (beide ändern `AudioPlayerService.kt`) |

Die Reihenfolge weicht von der Roadmap ab: P0-4 kam zuerst, weil jeder Gradle-Lauf die eingecheckten Build-Dateien verändert und den Diff verrauscht hat.

### Geänderte Dateien (ohne Build-Ausgabe)
- **`:core`:** `dsp/PreEqMapper.kt`, `dsp/BassExciter.kt`, `dsp/Crossfeed.kt` (nur Kommentar), `dsp/PlayerDspPcm16.kt` (nur Kommentar), `dsp/SubsonicFilterCurve.kt` (nur Kommentar), neu `playlist/PlaybackFailurePolicy.kt`; Tests `PreEqMapperTest`, `BassExciterTest`, neu `PlaybackFailurePolicyTest`
- **`:player`:** `service/AudioPlayerService.kt`, `AndroidManifest.xml`, `res/values/strings.xml`, `build.gradle.kts`, neu `PlayerLaunch.kt`; gelöscht `PlayerActivity.kt`, `ui/TrackAdapter.kt`, `res/layout/activity_player.xml`, `res/layout/item_track_playlist.xml`
- **`:app`:** `AndroidManifest.xml`, `MainActivity.kt`, `integration/AndroidPlayerBridge.kt`, `ui/player/PlayerViewModel.kt`, `ui/MainScreen.kt` (Kommentar); Tests `MainViewModelTest`, `PlayerViewModelTest`; gelöscht `audio/media3/Media3DspPipeline.kt` und Test
- **Repo:** 6.494 Build-Dateien und `local.properties` aus dem Index entfernt

Geteilte Dateien: `AudioPlayerService.kt` (P0-2 und P0-3), `strings.xml` (P0-2 ergänzt, P0-3 entfernt). Beide Punkte wurden zusammen committet.

### Entscheidungen
- **P0-1:** Das ursprüngliche Raster (24 … 20000 Hz) wurde wiederhergestellt, nicht neu entworfen. `MainViewModelTest` suchte das 110-Hz-Band über die entfernte 115-Hz-Kante und sucht es jetzt per Lookup.
- **P0-2:** Die Regeln liegen als reine Kotlin-Klasse in `:core`, damit sie ohne Android testbar sind. Netzfehler = Gerät offline, Media3-Netzfehlercodes oder `java.net`-Ursache in der Cause-Kette. Alles andere bei bestehender Verbindung gilt als „nicht abspielbar". Wartezeiten 2, 4, 8, 16, 30 s (gedeckelt), höchstens 8 Versuche, dann bleibt der Player pausiert; Play lädt den Titel neu. Ein Wiederholungsversuch setzt an der Abbruchposition fort und lädt die Stream-URL neu (die laufen ab). Play/Pause von Sperrbildschirm und Auto laufen über den Service, sonst würde ein gescheiterter Titel dort nicht wieder starten.
- **P0-2, Nebenbefund:** Ein veralteter Stream-Lookup konnte nach einem Titelwechsel noch einen Titel starten (`ensureActive` nach dem Lookup ergänzt).
- **P0-3:** Eigene Action statt Launcher-Intent, weil ein Launcher-Start bei laufender App nicht zuverlässig `onNewIntent` auslöst. `AndroidPlayerBridge.launchPlayer` bringt jetzt ebenfalls den Compose-Player nach vorn (vorher: die alte Activity).
- **P0-5:** `drive` ist eine `tanh`-Sättigung (Verstärkung 1–3) vor dem Gleichrichter. `OUTPUT_TRIM = 0,5` hält den 100-Hz-Referenzpegel auf dem alten Wert, damit bestehende Presets nicht insgesamt lauter werden. Gemessen (zweite Harmonische re Eingang, Amplitude 0,3, mix 1, Standard-Drive):

  | Ton | vorher | nachher |
  |---|---|---|
  | 40 Hz | −18,7 dB | −9,9 dB |
  | 60 Hz | −12,9 dB | −8,9 dB |
  | 100 Hz | −9,4 dB | −9,7 dB |

  Bekannte Grenze: bei sehr lautem Bass flacht die Sättigung Spitzen ab, die zweite Harmonische wächst dann nicht mehr mit `drive`. Das ersetzt der Neuaufbau in Roadmap 03 (P4-3).
- **P0-6:** Der Kommentar „Sonic akzeptiert nur 16 Bit" stimmt für Media3 1.11.1 nicht mehr (`SonicAudioProcessor.configure()` akzeptiert PCM16 und Float, am Bytecode von `media3-common` 1.11.1 geprüft). Das Ergebnis geht in P4-7 (Float-Verarbeitung) ein.

### Tests
- **Lokal:** `:core:test` 307 Tests, 0 Fehler; `ktlintCheck` für `:core` und `:app` grün. Neue Tests: `PreEqMapperTest` (+1, 1 erweitert), `BassExciterTest` (+5), `PlaybackFailurePolicyTest` (13), `PlayerViewModelTest` (+1). Vier der fünf neuen `BassExciterTest`-Tests sind gegen die alte Implementierung rot (nachgewiesen).
- **CI (PR #63, Run 243, Commit `38b78a8`):** ktlint, `assembleDebug`, `testDebugUnitTest`, `:core:test`, `lintDebug`/`lintRelease` – alle Schritte grün.
- Die Android-Module wurden lokal nicht gebaut (kein Android SDK, Lizenzannahme nicht freigegeben); der Nachweis dafür ist die CI.

### Offene Punkte
1. **Gerätetest P0-3:** Benachrichtigung, Sperrbildschirm und Android Auto öffnen den Compose-Player.
2. **Gerätetest P0-2:** Flugmodus während der Wiedergabe; Wiederaufnahme nach Rückkehr des Netzes (aktuell nur über die Wartezeiten, nicht über einen Netz-Callback).
3. **Hörprobe P0-5:** Im Bass klingt der Effekt jetzt stärker (40 Hz um rund 9 dB, 60 Hz um rund 4 dB); Presets mit hohem `virtualBassMix` bei Bedarf nachstellen.
4. **Entscheidung P4-4:** `Crossfeed.kt` nutzen oder löschen (bleibt vorerst unverändert; Vermerk im Dateikopf).
5. **P4-7:** Float-Ausgabe im Player-Pfad ist technisch möglich (siehe oben), noch nicht bewertet.
6. Das Raster-Verhalten von P0-1 (Bass-Bänder tatsächlich getrennt hörbar/messbar) ist auf dem Gerät nicht gemessen.
