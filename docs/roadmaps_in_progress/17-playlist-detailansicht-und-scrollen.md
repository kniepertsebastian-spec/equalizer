# Roadmap 17 – Playlist-Detailansicht & flüssiges Scrollen im eigenen Player

**Priorität:** Hoch (Kern-UX im eigenen Player)  
**Ziel:** Playlisten im eigenen Player — sowohl aus der **SoundCloud-Bibliothek** (`Meine Playlisten`, `Gelikte Playlisten`, `Likes`) als auch **lokal gespeicherte / importierte Playlisten** — gezielt öffnen, vollständig betrachten und flüssig bis zum letzten Track durchscrollen können, ohne sofort die laufende Wiedergabe zu unterbrechen.  
**Abhängigkeit:** Keine (**Welle 1** – komplett unabhängig von RM 14, 15 und 16, sofort parallel im eigenen Worktree bearbeitbar).

---

## Ausgangslage / Befund (am Code verifiziert)

1. **SoundCloud-Bibliothek lässt sich gar nicht öffnen (`PlaylistsTab.kt:291-337`, `PlaylistRows.kt:55-93`):**
   - `library.own`, `library.liked` und „Likes“ verwenden `LibraryRow`.
   - Ein Tap auf die gesamte Zeile (oder den Play-Button) ruft direkt `viewModel.playLibraryPlaylist(playlist)` bzw. `viewModel.playLikedTracks()` auf (`PlayerViewModel.kt:173-175`). Es existiert keine Ansicht, um den Inhalt einer SoundCloud-Playlist vorher anzuschauen oder durchzuscrollen.
   - **Gute Nachricht:** `OnlineSources.kt` (`loadPlaylistTracks(playlistId)` und `loadLikedTracks()`) sowie `LibraryLoader.kt` in `:player` existieren bereits fertig — sie müssen in `PlayerViewModel` nur für eine reine Lese-/Detailansicht genutzt werden, ohne sofort `playQueue(...)` aufzurufen!
2. **Lokale / gespeicherte Playlisten nur als nicht-lazy Inline-Aufklapper (`PlaylistRows.kt:99-225`):**
   - `PlaylistRow` klappt per `expanded`-Boolean alle Tracks inline mit `hits.forEach { ... }` in einer normalen `Column` innerhalb eines einzigen `LazyColumn`-Items auf.
   - Bei größeren Playlisten ist das unübersichtlich, schlecht scrollbar, verliert bei Tab-/Konfigurationswechsel den Zustand und bietet keine vollwertige Detailansicht mit Header, Shuffle und sauberem Scroll-Verhalten bis unter den `MiniPlayer`.

---

## Zugewiesener Dateiumfang

- `app/src/main/java/com/auracurve/app/ui/player/library/PlaylistsTab.kt`
- `app/src/main/java/com/auracurve/app/ui/player/library/PlaylistRows.kt`
- Neu: `app/src/main/java/com/auracurve/app/ui/player/library/PlaylistDetailScreen.kt`
- `app/src/main/java/com/auracurve/app/ui/player/PlayerScreen.kt`
- `app/src/main/java/com/auracurve/app/ui/player/PlayerViewModel.kt`
- `app/src/main/res/values/strings.xml` *(nur neue Playlist-Detail-Strings, deutlich kommentierter Block)*
- Tests:
  - `app/src/test/java/com/auracurve/app/ui/player/LibraryUiTest.kt`
  - `app/src/testFull/java/com/auracurve/app/ui/player/PlayerViewModelTest.kt`

*Nicht anfassen:* `:core/preset`, `EqController.kt`, `MainViewModel.kt`, `ui/equalizer/*` (gehören zu RM 14/15/16) sowie `:player` (`LibraryLoader` / `OnlineSources` haben die nötigen Methoden bereits).

---

## Arbeitspakete

### P17-1 – Detail-State & Laden ohne Queue-Abbruch in `PlayerViewModel`
- [x] Sauberes UI-State-Modell einführen (z. B. `OpenedPlaylistUiState` als `StateFlow<OpenedPlaylistUiState?>`):
  - Unterstützt sowohl **lokale/gespeicherte Playlisten** (reaktiv aus `playlistRepository.playlists`, sodass Entfernen eines Tracks sofort sichtbar wird) als auch **SoundCloud-Playlisten** (`PlaylistItem`) und **SoundCloud-Likes**.
  - Enthält `title`, `subtitle` (Quelle + Track-Anzahl), `artworkUrl`, `tracks`, `isLoading`, `errorMessage`, `canRemoveTracks` (`sourceUrl == null` bei lokalen Listen) und `playlistId` / Typ.
- [x] Neue ViewModel-Methoden:
  - `openSavedPlaylist(playlistId: String)`
  - `openLibraryPlaylist(playlist: PlaylistItem)` *(ruft `controller.loadPlaylistTracks(playlist.id)` auf, ohne die aktive Wiedergabe/Queue zu verändern)*
  - `openLikedTracks()` *(ruft `controller.loadLikedTracks()` auf)*
  - `closeOpenedPlaylist()`
  - `playOpenedPlaylist(startIndex: Int = 0, shuffle: Boolean = false)`
  - `retryOpenedPlaylist()` *(bei Netzwerk-/Timeout-Fehler)*
- [x] Laufenden Ladevorgang bei schnellem Schließen oder Wechsel sauber abbrechen (`Job?.cancel()`), keine Race Conditions.

### P17-2 – `PlaylistDetailScreen.kt` mit eigener `LazyColumn`
- [x] Vollwertige, eigenständig scrollbare Detailansicht für geöffnete Playlisten erstellen:
  - **TopBar:** Zurück-Button (`48x48 dp`, `contentDescription`), Playlist-Titel (`maxLines = 1`, Ellipsis), optional „Auf SoundCloud senden“ / „Löschen“ bei lokalen Listen.
  - **Header-Item in der `LazyColumn`:** Cover (`CoverThumb`), Titel, Quellen-Hinweis + Track-Anzahl, Primär-Buttons **„Alle abspielen“** und **„Shuffle“**, sowie Such-/Filterfeld (`PlaylistSearch`, ab `SEARCH_MIN_TRACKS` oder immer sichtbar).
  - **Trackliste:** `LazyColumn` mit stabilen `key`s (`"${track.id}-$index"`), `TrackStateIcon` + `TrackHighlight` für laufenden/gespielten Track, `CoverThumb`, Titel & Artist, und bestehendes `TrackQueueMenu` (`Als Nächstes`, `Zur Warteschlange`, `Ab hier abspielen`, bei lokalen Listen `Aus Playlist entfernen`).
  - **Scroll- & Edge-to-Edge-Sauberkeit:** `contentPadding` berücksichtigt `WindowInsets.systemBars` **und** die Höhe des sichtbaren `MiniPlayer`, sodass der allerletzte Track immer vollständig erreichbar und antippbar ist.
  - **Zustände:** `Loading` (`CircularProgressIndicator`), `Empty` (klarer Leertext) und `Error` (Fehlermeldung + „Erneut versuchen“-Button bei Offline/Timeout).
- [x] **System-Back:** `BackHandler(enabled = openedPlaylist != null) { viewModel.closeOpenedPlaylist() }` einbinden.

### P17-3 – Einstieg in `PlaylistsTab.kt`, `PlaylistRows.kt` & `PlayerScreen.kt`
- [x] `LibraryRow` um optionales `onOpen: (() -> Unit)?` erweitern:
  - Tap auf die Zeile ruft `onOpen` auf (öffnet Detailansicht).
  - Tap auf den runden Play-Button (`48x48 dp`) spielt wie bisher direkt ab (`onPlay`).
- [x] `PlaylistRow` (lokale/gespeicherte Playlisten):
  - Tap auf die Zeile öffnet ebenfalls `PlaylistDetailScreen` (statt der schweren inline `hits.forEach`-Liste in der `Column`).
  - Direkte Aktionen (Play-Button, Löschen, optional SoundCloud-Upload) bleiben auf der Zeile erhalten.
- [x] In `PlayerScreen.kt`: Ist `openedPlaylist != null`, wird `PlaylistDetailScreen` angezeigt; beim Schließen bleibt die vorherige `LazyListState`-Scroll-Position des `PlaylistsTab` erhalten (`rememberLazyListState` oberhalb des Switches halten).
- [x] Store-Flavor (`onlineSources = false`) prüfen: lokale Playlisten öffnen und scrollen dort genauso sauber, ohne SoundCloud-Abhängigkeit.

---

## Abnahmekriterien & Verifikation

- [x] **Unit- & UI-Tests `:app`:**
  - `PlayerViewModelTest`:
    - `openLibraryPlaylist(...)` lädt Tracks in `openedPlaylist`, **ohne** die laufende Queue oder Wiedergabe zu verändern.
    - Fehler beim Laden liefert sauberen Error-State + funktionierendes `retryOpenedPlaylist()`.
    - `playOpenedPlaylist(startIndex = 12)` startet exakt ab Track 12.
    - Entfernen eines Tracks in einer offenen lokalen Playlist aktualisiert die angezeigte Liste sofort.
  - `LibraryUiTest`:
    - Tap auf lokale Playlist und auf SoundCloud-Playlist öffnet Detailansicht; Play-Button startet direkt.
    - Lange Playlist (z. B. 50 Tracks): Scrollen per `performScrollToIndex` bis zum letzten Track funktioniert und Klick darauf ruft `onPlayTrack(49)` auf.
    - Zurück-Navigation schließt Detailansicht und zeigt wieder die Playlist-Übersicht.
- [x] **Build & Lint (beide Flavors):**
  ```bash
  ./gradlew :app:testFullDebugUnitTest :app:testStoreDebugUnitTest :app:ktlintCheck
  ```
- [ ] **Gerätetest:** *(offen, Nutzer)*
  - Lokale Playlist und SoundCloud-Playlist mit 50+ Tracks öffnen, flüssig bis ganz nach unten scrollen (trotz sichtbarem MiniPlayer), Track aus der Mitte starten, suchen/filtern und per Back-Geste zur vorherigen Position zurückkehren.

---

## Umsetzungsstand

**Commit:** `adf82bb`

**Status:** umgesetzt, Gerätetest offen. Nur Dateien aus dem zugewiesenen Umfang geändert (keine Abweichungen;
`:core`, `:player`, `EqController`, `MainViewModel`, `ui/equalizer/*` unberührt).

### Geänderte Dateien
- `app/.../ui/player/PlayerViewModel.kt` (P17-1)
  - Neu: `OpenedPlaylistSource` (`Saved(playlistId)`, `Library(PlaylistItem)`, `Likes`) und
    `OpenedPlaylistUiState` (`title`, `subtitle` = Quelle + Track-Anzahl, `artworkUrl`, `tracks`, `isLoading`,
    `errorMessage`, `canRemoveTracks`, `savedPlaylist`).
  - `openedPlaylist: StateFlow<OpenedPlaylistUiState?>` und `openSavedPlaylist(id)`, `openLibraryPlaylist(item)`,
    `openLikedTracks()`, `closeOpenedPlaylist()`, `retryOpenedPlaylist()`, `playOpenedPlaylist(startIndex, shuffle)`.
  - Öffnen ruft nur `controller.loadPlaylistTracks()` / `loadLikedTracks()` – **kein** `playQueue`, keine Befehle an
    den Player. Erst „Alle abspielen“, „Shuffle“ oder ein Track-Tap ändern die Queue.
  - Gespeicherte Playlisten werden reaktiv aus `playlistRepository.playlists` beobachtet (Entfernen eines Tracks ist
    sofort sichtbar); wird die Liste gelöscht, schließt die Ansicht.
  - Ein `Job` pro geöffneter Liste: jedes neue Öffnen und `closeOpenedPlaylist()` bricht den vorigen Lade-/Beobachtungs-
    Job ab, eine späte Antwort kann die aktuelle Ansicht nicht überschreiben.
- Neu: `app/.../ui/player/library/PlaylistDetailScreen.kt` (P17-2)
  - Feste TopBar: Zurück (`48x48 dp`, `contentDescription` „Back to the playlists“), Titel (`maxLines = 1`, Ellipsis,
    `heading`), bei gespeicherten Listen „Send to SoundCloud“ (nur Flavor `full`) und „Delete playlist“.
  - Eine `LazyColumn`: Header-Item (Cover 96 dp, Titel, Quelle + Anzahl, „Play all“ / „Shuffle“ mit min. 48 dp Höhe,
    Suchfeld ab `SEARCH_MIN_TRACKS` = 6), danach die Tracks mit stabilen Keys `"${track.id}-$index"`,
    `TrackStateIcon`/`markWhenCurrent`, `CoverThumb`, Titel/Artist, `TrackQueueMenu` (Als Nächstes, Zur
    Warteschlange, Ab hier abspielen, bei lokalen Listen Aus Playlist entfernen). Zeilen min. 56 dp hoch,
    `onClickLabel` „Play from this track“.
  - Zustände: Loading (`CircularProgressIndicator` + Text), Error (Meldung + „Try again“), Empty (lokal: bisheriger
    Hinweis, SoundCloud: „This playlist has no playable tracks.“), Suche ohne Treffer.
  - Suche über `PlaylistSearch` (Index bleibt der Platz in der ganzen Liste, Tap startet dort).
- `app/.../ui/player/library/PlaylistRows.kt` (P17-3)
  - `LibraryRow(onOpen = …)`: Tap auf die Zeile öffnet (mit `onClickLabel` „Open …“), der runde Play-Button
    spielt sofort. Ohne `onOpen` (Suchtreffer, Bridge-Treffer, Radio/Audius) bleibt das alte Verhalten.
  - `PlaylistRow`: kein Inline-Aufklappen mehr (`hits.forEach` entfernt); Tap öffnet die Detailansicht, Play,
    Löschen und SoundCloud-Upload bleiben auf der Zeile.
- `app/.../ui/player/library/PlaylistsTab.kt` – Verdrahtung `onOpen` für gespeicherte Listen, Likes, „My playlists“
  und „Liked playlists“; ungenutzte Parameter `currentTrackId`/`playedIds` entfernt.
- `app/.../ui/player/PlayerScreen.kt` – `rememberLazyListState()` für die Übersicht **oberhalb** des Umschaltens;
  ist `openedPlaylist != null`, wird `PlaylistDetailScreen` gezeigt. `BackHandler(enabled = openedPlaylist != null)`.
  Ein geteilter Link (`requestedTab`) schließt eine offene Playlist, damit der Import sichtbar ist.
- `app/src/main/res/values/strings.xml` – kommentierter Block „Roadmap 17“ am Dateiende (nur Playlist-Detail-Strings).
- Tests: `PlayerViewModelTest` (+9), `LibraryUiTest` (+4, bestehender Test angepasst).

### Entscheidungen
- **Unterer Rand / MiniPlayer:** Die `NavHost` des Player-Tabs ist bereits um `shellPadding` des Scaffolds gepolstert
  (MiniPlayer + NavigationBar inkl. System-Navigationsleiste). Die Detail-`LazyColumn` endet deshalb über dem
  MiniPlayer; sie addiert nur `spacing.large` unten und oben die Statusleiste (`WindowInsets.systemBars`). Eine
  zusätzliche Addition von MiniPlayer-Höhe und Systemleiste würde den Abstand doppeln. Nachgewiesen im UI-Test mit
  einer 120-dp-Bottom-Bar als MiniPlayer-Ersatz: Unterkante des letzten Tracks ≤ Oberkante der Leiste.
- **Shuffle:** schaltet den Shuffle-Modus des Players ein (`setShuffle(true)`) und startet bei einem zufälligen Track
  der ganzen Liste. „Alle abspielen“ / Track-Tap lassen den Shuffle-Modus unverändert – wie die bestehenden
  Play-Buttons der Zeilen.
- **Zustand über Tab-/Konfigurationswechsel:** die offene Playlist liegt im ViewModel, nicht im Composable; die Suche
  per `rememberSaveable(state.source)` je Liste.
- Die bisherigen `playLibraryPlaylist` / `playLikedTracks` / `playPlaylist` bleiben für den Play-Button der Zeilen
  (direktes Abspielen wie bisher). `playNextFromPlaylist` / `addToQueueFromPlaylist` werden im UI nicht mehr
  genutzt (die Detailansicht arbeitet mit `TrackItem`), bleiben aber samt Test bestehen.

### Tests & Nachweise
- `PlayerViewModelTest` (neu):
  - Öffnen einer SoundCloud-Playlist lädt Tracks in `openedPlaylist`, **ohne** `playQueue` oder sonstige Befehle.
  - Likes öffnen ohne Abspielen.
  - Fehler → Error-State, `retryOpenedPlaylist()` lädt erneut und zeigt die Tracks.
  - `playOpenedPlaylist(startIndex = 12)` startet exakt bei Index 12 (Track 13 von 20).
  - Shuffle: `shuffle:true` + ganze Liste, Startindex im Bereich.
  - Während des Ladens wird nichts abgespielt.
  - Schließen / Wechsel während des Ladens verwirft die späte Antwort.
  - Entfernen eines Tracks in offener lokaler Playlist aktualisiert Liste und Untertitel sofort.
  - Importierte Liste: `canRemoveTracks = false`; Löschen schließt die Ansicht.
- `LibraryUiTest` (neu):
  - Tap auf lokale Playlist und SoundCloud-Playlist öffnet, Play-Button spielt direkt.
  - 50 Tracks: `performScrollToIndex(50)` bis zum letzten Track, liegt über der MiniPlayer-Leiste, Klick →
    `onPlayTrack(49)` (Screenshot `build/player-screenshots/playlist-detail-bottom.png`).
  - Suche „Artist 42“ → Tap startet mit Index 41 der ganzen Liste.
  - Loading / Error (+ Retry) / Empty.
  - Zurück-Pfeil und System-Back (`onBackPressedDispatcher`, `BackHandler` wie in `PlayerScreen`) zeigen wieder die
    Übersicht.
- `./gradlew :app:testFullDebugUnitTest :app:testStoreDebugUnitTest :app:ktlintCheck` – RESULT_LINE

### Offene Punkte
- **Gerätetest** (Nutzer): lokale und SoundCloud-Playlist mit 50+ Tracks öffnen, bis ganz unten scrollen (MiniPlayer
  sichtbar), Track aus der Mitte starten, suchen, per Back-Geste zurück – Scroll-Position der Übersicht prüfen.
- Rückmeldungen von „Send to SoundCloud“ aus der Detailansicht erscheinen (wie bisher) in der Meldungszeile der
  Übersicht, nicht in der Detailansicht.
- Der `BackHandler` von `PlayerScreen` selbst ist nur indirekt getestet (gleiches Muster im UI-Test); `PlayerScreen`
  braucht echte ViewModels und hat keinen eigenen UI-Test.
