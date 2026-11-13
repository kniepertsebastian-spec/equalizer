# Roadmap 02 – Player-Fundament

**Modell-Empfehlung:** Opus 5.5 für P2-1; Sonnet 5.5 für P2-2 bis P2-4  
**Ziel:** Queue, Wiedergabeintegration und Player-Service stabilisieren.  
**Abhängigkeit:** Roadmap 01 abgeschlossen. Vor Roadmap 05 abschließen.

## Phase 2 – Player-Fundament (Technik unter der Oberfläche)

### P2-1 Queue an ExoPlayer übergeben (L)
- **Befund:** ExoPlayer hält immer nur einen Titel. Die Queue liegt im Service, jeder Wechsel bedeutet Pause, Netzabruf, `setMediaItem`, `prepare`. Dadurch gibt es Lücken zwischen Titeln und kein Vorladen.
- **Lösung:** Ganze Queue als Media-Items setzen, Stream-Adresse erst beim Laden auflösen (`ResolvingDataSource`). Der Service wird ein `MediaLibraryService`: Benachrichtigung, Sperrbildschirm, Bluetooth, Android Auto und Wear kommen dann aus einer Quelle; der getrennte `AutoBrowserService` und der `ForwardingPlayer`-Umbau entfallen.
- **Fertig, wenn:** Der nächste Titel vorgeladen ist und der Wechsel ohne hörbare Lücke läuft.
  - [x] Ganze Queue als Media-Items in ExoPlayer (`TrackMediaItems`, `QueueMediaId`); Stream-Adresse wird beim Laden aufgelöst (`ResolvingTrackMediaSource`, Abweichung von `ResolvingDataSource` siehe Migrationsplan)
  - [x] `AudioPlayerService` ist ein `MediaLibraryService`; `AutoBrowserService`, `AutoSessionState`, Queue-`ForwardingPlayer` und `androidx.media:media` entfernt
  - [x] Vorladen und Wechsel ohne Puffern mit echtem ExoPlayer nachgewiesen (Robolectric, `ResolvingTrackMediaSourceTest`, 7 Tests: nur der laufende und ein kurzer Vorlauf werden aufgelöst, Wechsel ohne `STATE_BUFFERING`, HLS-Erkennung, Fehler beim Vorladen erst beim Übergang, neues `prepare()` löst neu auf)
  - [ ] **Gerätetest:** Titelwechsel ohne hörbare Lücke, Vorladen mit echten SoundCloud-Streams (progressiv und HLS)

### P2-2 Shuffle, Wiederholen, „Als Nächstes spielen", Queue umsortieren (M)
- Fehlt komplett; folgt nach P2-1 fast von selbst aus den ExoPlayer-Funktionen.
  - [x] Shuffle (an/aus) und Wiederholen (aus / ganze Queue / dieser Titel) im Player, Zustand kommt aus ExoPlayer (`NowPlaying.shuffle/repeat`)
  - [x] „Als Nächstes spielen“ und „Zur Queue“ für Suchtreffer und Playlist-Titel; im Shuffle landet „Als Nächstes“ wirklich direkt danach (`QueueOrder`, 5 Tests)
  - [x] Queue umsortieren (nach oben/unten) und Einträge entfernen (`QueueEntryMenu`)
  - [x] ViewModel-Tests (`PlayerViewModelTest`, +5)
  - [ ] **Gerätetest:** Bedienung auf dem Telefon, Shuffle/Wiederholen auch über Sperrbildschirm/Auto

### P2-3 Bildlader mit Cache (S)
- **Befund:** `ArtworkImage` lädt per `URL.openConnection()` ohne Cache und nur das eine große Cover. Listen (Suche, Queue, Playlists) haben keine Cover.
- **Lösung:** Coil einbinden, Cover in allen Listenzeilen.
  - [x] Coil 3.4.0 (letzte Version für Kotlin 2.3) mit Speicher- und Plattencache (`HardBassEqApplication.newImageLoader`, 64 MB in `cacheDir/covers`)
  - [x] `ArtworkImage` mit `URL.openConnection()` entfernt; großes Cover über `CoverImage`
  - [x] Cover in Suche, Queue, gespeicherten Playlists, Bibliothek, Brücken-Treffern, Discovery und der Mini-Leiste
  - [ ] **Gerätetest:** Darstellung und Scrollen der Listen

### P2-4 Kleinere Service-Punkte (S)
- `startForeground` in `AudioPlayerService` ohne `ServiceCompat`, Typ und `try/catch` (derselbe Fehler, der in der EQ-Service-Klasse als CRIT-01 behoben wurde).
- Fortschritts-Ticker läuft auch im Pausenzustand zweimal pro Sekunde weiter.
- Toasts aus dem Service durch Zustände in der Oberfläche ersetzen.
- Benachrichtigungs-Icon ist `android.R.drawable.ic_media_play`: eigenes Icon.
- `PlayerDspAudioProcessor.queueInput` legt pro Puffer ein neues `ShortArray` an: wiederverwenden.
  - [x] `startForeground`: kein eigener Aufruf mehr. Media3 startet den Vordergrund selbst, mit Typ `mediaPlayback` und abgefangener `ForegroundServiceStartNotAllowedException` (in den Quellen von 1.11.1 geprüft: `MediaNotificationManager`)
  - [x] Fortschritts-Ticker läuft nur noch während der Wiedergabe
  - [x] Toasts ersetzt durch `PlayerNotice` (Player-Bildschirm und Mini-Leiste); Vorschau-Hinweis als Zeile unter dem Titel
  - [x] Eigenes Benachrichtigungs-Symbol `ic_notification_player`
  - [x] `ShortArray` wird wiederverwendet (wächst nur)
  - [ ] **Gerätetest:** Benachrichtigung und Symbol, Hinweise bei Flugmodus

---

## Abschlusskriterien
- [x] Queue wird als Media-Items durch ExoPlayer verwaltet; Stream-Adressen werden beim Laden aufgelöst.
- [ ] Vorladen und möglichst lückenloser Titelwechsel sind auf einem realen Gerät geprüft. *(Im Robolectric-Test mit echtem ExoPlayer nachgewiesen; Gerät offen.)*
- [ ] Shuffle, Wiederholen, „Als Nächstes spielen“ und Umsortieren funktionieren. *(Umgesetzt; Logik und ViewModel getestet, Bedienung am Gerät offen.)*
- [ ] Cover werden mit Cache in den relevanten Listen angezeigt. *(Umgesetzt und gebaut; auf dem Bildschirm nicht gesehen, Gerätetest offen.)*
- [x] Foreground-Service, Fortschritts-Ticker, UI-Zustände, Benachrichtigungs-Icon und PCM-Pufferverarbeitung sind überprüft.
- [ ] Benachrichtigung, Sperrbildschirm, Bluetooth und Android Auto verwenden den vorgesehenen Player-Einstieg. *(Im Code: eine Session, `setSessionActivity` → `PlayerLaunch`; Gerätetest offen.)*
- [x] Build, Unit-Tests, Lint und erforderliche Gerätetests sind dokumentiert.

## Integrationshinweis
P2-1 ist ein Architekturumbau. Vor dem Coden erst Ist-Zustand, Media3-Version, Service- und Android-Auto-Integration prüfen und einen Migrationsplan erstellen. P2-2 bis P2-4 dürfen nicht parallel dieselben Player-Dateien verändern, solange P2-1 läuft.

## Migrationsplan P2-1 (vor dem Umbau erstellt)

**Ist-Zustand (Stand `main` 4f47a55):**
- Media3 1.11.1 (`media3-exoplayer`, `-hls`, `-session`, `-ui`), dazu `androidx.media:media` 1.7.0 nur für das `MediaSessionCompat.Token` des Auto-Browsers.
- `AudioPlayerService` (`:player`) ist ein einfacher `Service` mit ExoPlayer, `MediaSession` und selbst gebauter Benachrichtigung (`startForeground` direkt, Symbol `android.R.drawable.ic_media_play`).
- ExoPlayer hält immer nur einen Titel. Die Queue liegt in `PlaybackQueueState` (von der Oberfläche geschrieben); vor jedem Titel: Pause, `SoundCloudClient.getTrack`, `setMediaItem`, `prepare`. Kein Vorladen.
- Ein `ForwardingPlayer` biegt Weiter/Zurück/Play/Pause/`setMediaItems` (Auto) auf die Service-Queue um.
- Android Auto: eigener `AutoBrowserService` (`MediaBrowserServiceCompat`, `:app`) liest `AppAutoCatalog` und bekommt das Session-Token über `AutoSessionState`.
- Oberfläche → Service: Intents (`ACTION_PLAY_INDEX`, `ACTION_NEXT` …), Zustand zurück über `NowPlayingState`. Hinweise kommen als Toasts aus dem Service.
- Fehlerbehandlung aus P0-2 (`PlaybackFailurePolicy`) hängt an dieser Ein-Titel-Logik.

**Zielbild:**
1. `AudioPlayerService` wird ein `MediaLibraryService` mit `MediaLibrarySession`. Benachrichtigung (Media3 `DefaultMediaNotificationProvider`, eigenes Symbol), Sperrbildschirm, Bluetooth, Android Auto und Wear hängen an dieser einen Session. `ForwardingPlayer`, `AutoBrowserService`, `AutoSessionState` und `androidx.media:media` entfallen.
2. Die ganze Queue liegt als `MediaItem`-Liste in ExoPlayer. Jeder Eintrag trägt nur die Titel-ID (Platzhalter-URI `hardbasseq-sc://track/<id>`) und die Metadaten.
3. Stream-Adresse erst beim Laden auflösen: **Abweichung von `ResolvingDataSource`, bewusst.** SoundCloud liefert je Titel progressive MP3 oder HLS. Welche `MediaSource` (progressiv oder HLS) ExoPlayer braucht, steht erst nach der Auflösung fest. Eine `ResolvingDataSource` kann aber nur die Adresse tauschen, nicht den Quelltyp. Deshalb löst eine eigene `ResolvingTrackMediaSource` die Adresse auf und baut erst dann die passende progressive oder HLS-Quelle. ExoPlayer bereitet Playlist-Einträge mit `useLazyPreparation` (Standard) erst vor, wenn es sie wirklich lädt. Das ist der laufende Titel und beim Vorladen der nächste. Es wird also nicht die ganze Queue auf einmal aufgelöst. Ein abgelaufener Link wird beim nächsten `prepare()` neu aufgelöst.
4. Fehlerbehandlung aus P0-2 bleibt in `PlaybackFailurePolicy`. Netzfehler: pausieren und später denselben Titel neu vorbereiten. Nicht abspielbar: `seekToNextMediaItem()`, höchstens dreimal in Folge. Ein Fehler beim Vorladen meldet ExoPlayer erst beim Übergang zu diesem Titel.
5. Android Auto: Browsen und „Dies abspielen“ / Sprachsuche laufen über `MediaLibrarySession.Callback` (`onGetLibraryRoot`, `onGetChildren`, `onSetMediaItems`) auf dem bestehenden `AppAutoCatalog`. Die Freigabe nur für System, Android Auto und Assistant bleibt.
6. Oberfläche → Service weiter per Intent, gleicher Prozess. Eine neue Queue, „Als Nächstes“ und „Zur Queue“ gehen über `QueueRequests`. Zustand zurück über `NowPlayingState`/`PlaybackQueueState`, die der Service jetzt aus dem Player ableitet.

**Reihenfolge:** P2-1 (Service, Quelle, Auto, Fehlerpfad) → P2-4 (fällt größtenteils mit dem Umbau zusammen) → P2-2 (Player-Funktionen und Oberfläche) → P2-3 (Coil, Cover in Listen). Nach jedem Schritt ktlint, Build, Unit-Tests und Lint.

**Risiken / nur am Gerät prüfbar:** hörbare Lücke beim Titelwechsel, Vorladen, Android Auto (Browsen, Sprachsuche), Benachrichtigung und Sperrbildschirm, Bluetooth-Tasten, abgelaufene Stream-Links nach langer Pause.

---

## Abschlussbericht

**Branch / PR:** `claude/gifted-mayer-pszodc` (neu von `main` 4f47a55 nach dem Merge von PR #63), Draft-PR folgt.

### Commits
| Punkt | Commit |
|---|---|
| P2-1, P2-2, P2-3, P2-4 | `026d714` |
| Migrationsplan, Häkchen, Abschlussbericht | dieser Doku-Commit |

Ein gemeinsamer Commit, weil alle vier Punkte dieselben Dateien ändern (`AudioPlayerService.kt` für P2-1/P2-2/P2-4, `PlayerScreen.kt` für P2-2/P2-3/P2-4). Gearbeitet wurde nacheinander in der Reihenfolge des Migrationsplans.

### Geänderte Dateien
- **`:core`:** neu `playlist/QueueMediaId.kt`, `playlist/QueueOrder.kt` (+ Tests); `playlist/QueueNavigation.kt` auf die noch genutzte Konstante reduziert (das Weiterschalten macht ExoPlayer), `QueueNavigationTest` gelöscht
- **`:player`:** `service/AudioPlayerService.kt` (neu als `MediaLibraryService`), neu `playback/ResolvingTrackMediaSource.kt`, `playback/StreamResolver.kt`, `playback/TrackMediaItems.kt`, `res/drawable/ic_notification_player.xml`; geändert `playback/NowPlayingState.kt` (`PlayerNotice`, `RepeatSetting`, `QueueRequests`), `playback/AutoCatalog.kt` (`AutoSessionState` entfernt), `playback/PlayerDspAudioProcessor.kt`, `model/Models.kt` (`isHlsStream`), `soundcloud/SoundCloudClient.kt` (Protokoll merken), `AndroidManifest.xml`, `res/values/strings.xml` (Toast- und Benachrichtigungs-Texte entfernt), `build.gradle.kts` (`androidx.media` raus, Robolectric/Media3-Test-Utils rein); neu Test `ResolvingTrackMediaSourceTest`
- **`:app`:** gelöscht `auto/AutoBrowserService.kt`; neu `ui/player/PlayerQueueUi.kt`; geändert `integration/PlayerController.kt`, `ui/player/PlayerViewModel.kt`, `ui/player/PlayerScreen.kt`, `ui/player/PlayerSections.kt`, `ui/player/DiscoveryItems.kt`, `ui/MainScreen.kt`, `HardBassEqApplication.kt` (Coil-ImageLoader), `AndroidManifest.xml` (Auto-Filter am Player-Dienst), `res/values/strings.xml`, `res/xml/automotive_app_desc.xml` (Kommentar), `build.gradle.kts`; Tests `PlayerViewModelTest`, `DiscoveryViewModelTest`
- **Repo:** `gradle/libs.versions.toml` (Coil 3.4.0 rein, `androidx.media` raus), `docs/DECISIONS.md` (Eintrag „Player-Fundament“, alter Android-Auto-Eintrag als abgelöst markiert)

### Entscheidungen
- **Keine `ResolvingDataSource`:** Begründung im Migrationsplan. Die eigene `ResolvingTrackMediaSource` verlässt sich auf `useLazyPreparation`; der Dienst setzt es ausdrücklich. Der Test hat gezeigt, warum: Ohne `useLazyPreparation` wurden alle 20 Titel einer Queue sofort aufgelöst.
- **Wann der nächste Titel aufgelöst wird:** sobald der laufende vollständig gepuffert ist. Bei einem echten Stream ist das, wenn die Datei geladen ist; der Standardpuffer hält etwa 50 s Vorlauf.
- **Rest-`ForwardingPlayer`:** Er leitet keine Queue mehr um. Er setzt bei Play/Weiter/Zurück von außen die Fehlerzähler aus P0-2 zurück und bereitet nach einem Fehler neu vor. Ohne ihn hätte „Weiter“ auf dem Sperrbildschirm nach einem Fehler nichts abgespielt.
- **Abgelaufene Links:** Ein HTTP-Fehler, nachdem der Titel schon lief, führt einmal zu einem neuen `prepare()` (neue Auflösung, gleiche Position), bevor der Titel als nicht abspielbar gilt.
- **Oberfläche → Dienst** bleibt bei Intents (gleicher Prozess). Titellisten liegen in `QueueRequests`, weil `TrackItem` nicht in ein Intent passt.
- **„Als Nächstes“ im Shuffle:** ExoPlayer verteilt neue Einträge zufällig in der Shuffle-Reihenfolge. `QueueOrder.playNext` setzt sie direkt hinter den laufenden Titel.
- **Queue-Anzeige im Shuffle:** Die Liste behält die Queue-Reihenfolge, ein Hinweis sagt, dass die Wiedergabe zufällig springt.
- **Coil 3.4.0** statt der neuesten Version: 3.5 und neuer brauchen die Kotlin-2.4-Standardbibliothek, das Projekt ist laut ADR 0001 auf Kotlin 2.3.20 festgelegt.
- **Manifest:** Die Android-Auto-Filter (`MediaBrowserService`, `MEDIA_PLAY_FROM_SEARCH`) stehen im `:app`-Manifest neben der Auto-Deklaration, sonst schlägt Lint fehl (`MissingMediaBrowserServiceIntentFilter`, `MissingIntentFilterForMediaSearch`). Sie werden in die Dienst-Deklaration aus `:player` gemischt (geprüft im zusammengeführten Manifest).

### Tests
- **Lokal** (Android-SDK 37 im Container, Lizenzen mit Freigabe von Sebastian akzeptiert): `ktlintCheck`, `assembleDebug`, `testDebugUnitTest`, `:core:test`, `lintDebug`, `lintRelease`: alle grün.
  - `:core` 313 Tests (neu `QueueOrderTest` 5, `QueueMediaIdTest` 4; `QueueNavigationTest` 3 entfallen), `:app` 262 Tests (`PlayerViewModelTest` +5), `:player` 8 Tests (neu `ResolvingTrackMediaSourceTest` 7, Robolectric mit echtem ExoPlayer).
  - Lint: keine Fehler in `:app`. In `:player` meldet Lint wie bisher den `RestrictedApi`-Fehler in `SoundCloudLoginActivity` (bricht dort nicht ab) und neu die Warnung `ExportedService` für den Player-Dienst. Android Auto braucht ihn exportiert, wie vorher `AutoBrowserService`.
- **CI:** siehe PR.

### Offene Punkte (Gerätetests)
1. **Titelwechsel ohne hörbare Lücke** mit echten SoundCloud-Streams, progressiv und HLS (Go+), mit eingeschaltetem EQ und Virtual Bass.
2. **Vorladen:** den nächsten Titel kurz vor Ende im Flugmodus: Der laufende muss zu Ende spielen, danach gilt die Regel aus P0-2 (warten, nicht durchspringen).
3. **Benachrichtigung / Sperrbildschirm / Bluetooth:** Media3-Benachrichtigung mit eigenem Symbol, Tasten, Cover; Tippen öffnet den Compose-Player (zusammen mit dem offenen Gerätetest P0-3 aus Roadmap 01).
4. **Android Auto:** Ordner browsen, „Dies abspielen“, Sprachsuche, Queue-Ansicht im Auto.
5. **Lange Pause** (> 1 h) und dann weiterspielen: abgelaufener Link wird neu aufgelöst.
6. **Oberfläche:** Shuffle, Wiederholen, „Als Nächstes“, „Zur Queue“, Verschieben/Entfernen, Cover in allen Listen, Hinweiszeile im Player und in der Mini-Leiste.
7. Nach bestandenen Gerätetests diese Roadmap nach `docs/roadmaps_finished/` verschieben.
