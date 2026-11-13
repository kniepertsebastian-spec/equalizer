# Player-Redesign „Sonic Control" – Umsetzungsplan (Roadmap 05)

Stand: 13. November 2026, Code-Stand `main` 5d5b190 (nach Roadmap 02 und P4-1).
**Status: Plan zur Freigabe.** Bis zur ausdrücklichen Freigabe wird keine UI-Datei geändert.

Grundlage: `docs/roadmaps_in_progress/05-phase-3-player-redesign-plan-und-freigabe.md`, `CLAUDE.md`
(„Sonic Control"), `docs/DECISIONS.md`. Die App ist komplett Englisch (`DECISIONS.md`, Oktober 2026).
Alle UI-Texte unten sind deshalb englisch, die Erklärungen deutsch.

---

## 1. Ist-Zustand (am Code geprüft)

| Bereich | Befund |
|---|---|
| Player-Tab | `PlayerScreen.kt` hat 1.063 Zeilen und ist **eine** `LazyColumn`: Titel, EQ-Statuszeile, Cover, Seekbar, Transport, „Zur Playlist", EQ-Chip, dann Tabs *Search / Playlists / Queue*. Darunter folgen Kontext-Chips und die DSP-Karte, unabhängig vom Tab. Scrollt man die Suchergebnisse, sind Cover und Transport weg. |
| Mini-Leiste | `NowPlayingBar` ist `private` in `MainScreen.kt` und steht nur im Equalizer-Tab. Der Knopf „To player" führt zurück. |
| Navigation | `AppNavHost`: `Scaffold` mit `NavigationBar` (Player, Equalizer). Routen `player` (Start), `home` (EQ), `services`, `diagnostics`. Die Mini-Leiste gehört nicht zur Shell. |
| Zustandsquelle | **Eine einzige** Quelle: Der Dienst schreibt `NowPlayingState`/`PlaybackQueueState` (`:player`). `PlayerController.nowPlaying/queue` reicht beides an `PlayerViewModel` **und** `MainViewModel` weiter. Befehle laufen über Intents/`QueueRequests`. Das bleibt so. |
| Playlists-Tab | Import-Karte (Link-Feld, Spotify-Fortsetzung, Neu/Zusammenführen/Hochladen), lokale Playlists (aufklappbar, `PlaylistRow`), Brücken-Treffer, SoundCloud-Bibliothek (Likes, eigene und gelikte Playlists). |
| Search-Tab | Suchfeld und Ergebnisse (`searchItems`), darunter Discovery: „neue Uploads" beobachteter Künstler (`discoveryItems`). |
| Queue-Tab | Liste mit Statussymbol, Cover, Dauer und Menü (hoch/runter/entfernen/zur Playlist). Kein Ziehen, kein Wegwischen. |
| Theme | `Color.kt` (Graphite, Amber, Signal-Grün, Caution, Clip), `Spacing`, Barlow-Schriften, `SonicStyle`. Wiederverwendbar: `PlayerSoundChip`/`MiniCurve`, `EqStatusLine` (Lampe mit Form je Zustand), `CoverImage`/`CoverThumb` (Coil), `ShuffleButton`, `RepeatButton`, `TrackStateIcon`, `markWhenCurrent`. |
| Daten, die fehlen | **Like** (kein Schreib-Endpunkt in `SoundCloudClient`), **Wellenform** (`waveform_url` wird nicht gelesen), **„Zuletzt gehört"** (`PlayedTracksState` hält nur IDs im Speicher, nichts bleibt über einen Neustart), **Spektrum/Limiter-Pegel** (der Prozessor veröffentlicht keine Messwerte, `LookaheadLimiter.currentGain` ist privat), **Qualitäts-Etikett** (`NowPlaying` kennt das gespielte Format nicht; P4-1 wählt jetzt AAC/MP3, gezeigt wird es nirgends). |
| Tests | Unit-Tests für ViewModels/Logik. Keine Compose-UI-Tests, kein `androidTest`. Robolectric gibt es nur in `:player`. |

---

## 2. Informationsarchitektur und Navigationsfluss

```
App-Shell (AppNavHost-Scaffold)
├── Tab „Player"  = Bibliothek (Route player)
│     ├── Suchmodus (gleiche Route, Zustand im ViewModel)
│     └── Playlist-Detail (neue Route player/playlist/{source}/{id})
├── Tab „Equalizer" (Route home, unverändert)
├── Mini-Player  ── in der Shell, über der NavigationBar, in beiden Tabs
│     └── zieht auf ▶ „Now Playing" (Vollbild-Ebene der Shell, keine Route)
│            ├── Queue-Sheet (ModalBottomSheet)
│            └── Sound-Sheet hinter dem EQ-Chip (ModalBottomSheet)
│                   └── „Open equalizer" → Tab Equalizer (Now Playing klappt ein)
├── Linked services (Route services, unverändert)
└── Diagnostics (Route diagnostics, unverändert)
Dialoge: Import from link, New playlist / Add to playlist, Merge playlists
```

**Entscheidung: „Now Playing" ist eine Ebene der Shell, keine Navigationsroute.** Mini-Player und
Vollbild sind *ein* Element in zwei Größen. Der Finger zieht es stufenlos auf und zu. Als Route würde
es beim Wechsel neu aufgebaut, die Geste wäre unterbrochen, und unter dem Vollbild wäre der Tab nicht
mehr derselbe. Der Ausklappzustand ist reiner UI-Zustand (`PlayerSheetState`, Abschnitt 4) und
überlebt Rotation über `rememberSaveable`.

**Einstiege nach „Now Playing":**

| Auslöser | Verhalten |
|---|---|
| Tippen auf Mini-Player / Wischen nach oben | klappt auf |
| Titel in Bibliothek/Suche/Playlist antippen | spielt, Mini-Player erscheint, **kein** Aufklappen (wie Spotify/YouTube Music; man bleibt in der Liste) |
| Benachrichtigung, Sperrbildschirm (`PlayerLaunch.ACTION_OPEN_PLAYER`) | Tab Player, Now Playing aufgeklappt |
| Geteilter Link (`showPlayerRequest`) | Tab Player. Ein Playlist-Import öffnet den Import-Dialog mit Fortschritt (ersetzt `requestedTab = PLAYLISTS`). |

**Android-Back (in dieser Reihenfolge):**
1. Offenes Sheet (Queue, Sound) oder offener Dialog schließt.
2. Now Playing klappt auf den Mini-Player zu. Mit *Predictive Back* folgt das Zuklappen der
   Rückgeste (`PredictiveBackHandler`, activity-compose 1.10.1 vorhanden).
3. Suchmodus: Suchtext leeren und Fokus lösen, zurück zu den Bibliotheks-Abschnitten.
4. Playlist-Detail → Bibliothek.
5. Tab Equalizer → Tab Player (wie heute über `popUpTo(ROUTE_PLAYER)`).
6. Bibliothek → App verlassen (System).

---

## 3. UI-Spezifikation

Maße in dp. Touch-Ziele mindestens 48. Abstände aus `MaterialTheme.spacing` (4/8/16/24/32).
Farben nur aus `Color.kt`. **Amber** markiert Bedienbares (Play-Taste, gewählte Zustände, Fortschritt).
**Grün** (`SonicSignal`) erscheint nur, wenn der EQ das Signal nachweislich verarbeitet
(`EqStatusKind.ACTIVE`). **Rot** (`SonicClip`) steht für Fehler. Kein Zustand wird nur über Farbe
vermittelt: Es gibt immer Symbol, Form oder Text dazu.

### 3.1 Bibliothek (Startseite des Player-Tabs)

```
┌──────────────────────────────────────────┐
│ Player                    🔗  SoundCloud ▾│  Titelzeile (wie heute)
│ ● EQ active · SoundCloud              ›  │  EqStatusLine (wie heute)
│ ┌──────────────────────────────────────┐ │
│ │ 🔍 Search SoundCloud                  │ │  Suchfeld, fest oben (sticky)
│ └──────────────────────────────────────┘ │
│ Recently played                    All › │  Abschnitt, horizontale Reihe
│ [cov][cov][cov][cov] →                   │  96er-Cover, Titel 2 Zeilen
│ Likes                                    │
│ [♥ ] Liked tracks · 312 tracks       ▶   │  Zeile mit Cover
│ Playlists                     +  ⇪  ⋯    │  Neu / Import / Mehr (Merge, To SoundCloud)
│ [cov] Hardstyle Car · 48 tracks       ›  │  lokale + SoundCloud-Playlists,
│ [cov] Uptempo · 120 tracks · SC       ›  │  Quelle als Text-Etikett
│ New uploads                 ⟳  Edit  ▶All│  Discovery (Kopf eingeklappt)
│ [cov] Title · Artist · 2 d          ✕   │  Wegwischen wie heute
│ …                                        │
├──────────────────────────────────────────┤
│ [cov] Title — Artist        ● ▶  ⏭      │  Mini-Player (Shell)
├──────────────────────────────────────────┤
│   ♫ Player            ≋ Equalizer        │  NavigationBar
└──────────────────────────────────────────┘
```

- **Suchmodus:** Fokus im Suchfeld oder Text vorhanden blendet die Abschnitte aus und zeigt Treffer
  (`searchItems` unverändert in der Logik). Das Menü je Treffer bleibt (Play next / Add to queue /
  Add to playlist). Die Mini-Leiste bleibt sichtbar, außer die Tastatur ist offen.
- **Playlist-Detail** (neue Route): Kopf mit großem Cover, Name, Anzahl und Quelle, Knöpfe *Play* und
  *Shuffle*, Menü (Rename, Send to SoundCloud, Delete). Darunter Titelzeilen mit Menü. Ersetzt das
  Aufklappen von `PlaylistRow` in der Liste.
- **Import from link** wird ein eigener Dialog (aus dem Playlist-Kopf ⇪ und beim geteilten Link):
  Link-Feld, Fortschritt, Fehlermeldung, Fortsetzen/Verwerfen eines unterbrochenen Spotify-Imports.
  Brücken-Treffer (YouTube/Spotify-Link → SoundCloud) erscheinen im selben Dialog als Ergebnisliste.
- **Discovery-Kopf** (Künstler/Genres bearbeiten) öffnet sich über *Edit* als Sheet und steht nicht
  mehr dauerhaft in der Liste.
- **Abschnitte ohne Inhalt** werden ausgeblendet. Ausnahme: *Playlists* mit Leerhinweis.

| Zustand | Darstellung |
|---|---|
| Leer (nicht angemeldet, nichts gespielt, keine Playlists) | Suchfeld; Karte „Sign in to SoundCloud to see your likes and playlists" mit Knopf → Linked services; *Playlists* mit Leerhinweis und „New playlist"; *New uploads* mit „Add an artist" |
| Lädt | Abschnitt zeigt bis zu drei Platzhalterzeilen (graue Balken, ohne Animation, wenn Animationen aus sind). Kein Vollbild-Spinner. |
| Fehler (Bibliothek/Suche) | Zeile im Abschnitt: ⚠ + Text in `SonicClip` + „Retry". Andere Abschnitte bleiben nutzbar. |
| Keine Verbindung | Banner unter der Statuszeile: „Offline – showing saved playlists" (Symbol ☁̸, Steel). Suche deaktiviert mit Hinweis. Lokale Playlists und Zuletzt gehört bleiben spielbar (soweit der Stream erreichbar ist). |
| Spielt / pausiert | laufender Titel in Listen über `markWhenCurrent` + `TrackStateIcon` (wie heute) |
| Vorschau | Treffer, die als Vorschau bekannt sind, tragen das Etikett `PREVIEW` (umrandet, Text, nicht nur Farbe) |

### 3.2 Mini-Player (Shell, beide Tabs)

```
├─────────────────────────────── 2 dp Fortschritt (Amber auf Rule) ─┤
│ [48 cov] Title (1 Zeile, SemiBold)             ●   ▶/❚❚   ⏭      │  64 dp hoch
│          Artist · oder Hinweis                lamp                │
```

- Steht in der Shell direkt über der `NavigationBar`, in **beiden** Tabs. Ersetzt `NowPlayingBar`
  aus `MainScreen.kt` und den Knopf „To player".
- Nur sichtbar, wenn `nowPlaying != null`. Erscheinen und Verschwinden ist ein Schieben von unten
  (200 ms). Bei offener Tastatur ausgeblendet.
- **EQ-Statuspunkt:** die Lampe aus `EqStatusLine`, gleiche Form-Sprache (gefüllt = aktiv/grün,
  Ring = aus, Quadrat = Problem). Tippen auf die Lampe öffnet das Sound-Sheet.
- Gesten: Tippen oder Wischen nach oben → Now Playing. Wischen nach unten bei pausiertem Titel →
  Wiedergabe beenden? **Nein**, bewusst nicht: zu leicht versehentlich.

| Zustand | Darstellung |
|---|---|
| Lädt | Play-Taste zeigt Spinner (20 dp), Titel des gewählten Tracks steht schon da |
| Spielt / pausiert | ❚❚ / ▶; `stateDescription` „Playing"/„Paused" |
| Vorschau | hinter dem Titel `PREVIEW` als Etikett |
| Hinweis (Netz/Fehler) | zweite Zeile zeigt `noticeText` statt Interpret, mit Symbol (☁̸ wartet, ⚠ Fehler). Fehler in `SonicClip`, Warten in Steel (wie heute). |
| Kein Titel | ausgeblendet |

### 3.3 „Now Playing" (Vollbild)

```
┌──────────────────────────────────────────┐
│ ⌄                Now playing         ⋯   │  Zuklappen / Menü (Add to playlist, Go to artist)
│                                          │
│        ┌────────────────────────┐        │
│        │                        │        │  Cover: min(Breite−48, 42 % Höhe), Ecke 8
│        │         Cover          │        │
│        └────────────────────────┘        │
│ Title (2 Zeilen)                    ♡    │  Like (nur nach Roadmap 04, s. 6.)
│ Artist                    AAC 160 · 44.1 │  Qualitäts-Etikett (Steel, Mono-Ziffern)
│ PREVIEW · Sign in for the full track     │  nur bei Vorschau
│ ▁▃▅▇▅▃▂▃▆▇▆▄▂▁▂▄▆▇▅▃ ← Wellenform/Seekbar │  Fallback: Seekbar wie heute
│ 1:12                               4:05  │
│    ⤨      ⏮      ( ▶ )      ⏭      ↻     │  Transport, Play-Taste Amber 72 dp
│                                          │
│ ⇋ Bluetooth · JBL Flip 6   [~~ Hardstyle ●]  ☰ │  Ausgabe · EQ-Chip · Queue
│ ┌─ Signal ─────────────────────────────┐ │
│ │ Spektrum hinter der EQ-Kurve          │ │  P3-3, 72 dp hoch, aus/an im Sound-Sheet
│ │ Limiter −1.8 dB ▮▮▮▯▯▯                │ │
│ └──────────────────────────────────────┘ │
└──────────────────────────────────────────┘
```

- **Ausgabegerät:** `MainViewModel.displayRoute` (vorhanden) als Text mit Symbol je Routentyp. Nur
  Anzeige; ein Tippen öffnet den System-Ausgabewähler (`MediaRouter`-Dialog). Fällt der weg, bleibt es
  reine Anzeige.
- **EQ-Chip:** `PlayerSoundChip` (Mini-Kurve, Presetname, Kontext) plus Statuslampe. Tippen öffnet das
  **Sound-Sheet**, nicht mehr direkt den EQ-Tab.
- **Queue-Knopf** mit Anzahl als Badge-Text → Queue-Sheet.
- **Fehler-Banner:** Bei `NetworkGaveUp`/`TooManyUnplayable` steht unter dem Interpreten eine Zeile mit
  ⚠, Text und „Try again" (= Play). Bei `WaitingForNetwork` ☁̸ „Waiting for network…" ohne Knopf.
- **Querformat:** zwei Spalten (Cover links, alles andere rechts, scrollbar).

### 3.4 Queue-Sheet

`ModalBottomSheet`, halbe Höhe, aufziehbar auf volle Höhe. Kopf: „Queue · 23 tracks", bei Shuffle
der Hinweis aus `queue_shuffle_order_hint`, Knopf „Clear upcoming" (bestätigt per Snackbar mit
„Undo"). Darunter folgen „Now playing" (gesperrt) und „Next up".

- **Umsortieren:** Ziehgriff ≡ rechts (Long-Press auf den Griff, Drag). Die Reihenfolge wird erst beim
  Loslassen an `moveQueueItem(from, to)` geschickt. Unterwegs bleibt sie nur lokal, damit kein Intent
  pro Pixel an den Dienst geht.
- **Wegwischen:** nach links/rechts → `removeQueueItem`, Snackbar „Removed · Undo" (Undo = `playNext`
  an alter Stelle bzw. Wiedereinfügen über `QueueRequest`). Die Logik dafür ist in Schritt S4 zu
  klären: Ein Einfügen an beliebiger Position gibt es heute nicht.
- **Barrierefreiheit:** Jede Zeile hat Custom Actions „Move up", „Move down", „Remove", „Add to
  playlist". Das bisherige `QueueEntryMenu` bleibt als ⋯-Menü.
- Leer: Leerhinweis „Nothing queued" + „Search" (schließt Sheet und Now Playing, fokussiert die Suche).

### 3.5 Sound-Sheet (hinter dem EQ-Chip)

Inhalt, der heute unten auf der Player-Seite steht: Kontext-Chips (`ContextModeChips`) und die DSP-Karte
(`PlayerDspCard`: Mono-Bass, Cutoff, Limiter). Neu sind dort nur der Schalter „Show signal meter"
(Spektrum/Limiter-Anzeige, Standard an) und unten „Open equalizer" → Tab Equalizer. Kopf: Presetname
mit Mini-Kurve und `EqStatusLine`.

### 3.6 Zustände je Fläche (Übersicht)

| | Bibliothek | Mini-Player | Now Playing | Queue-Sheet |
|---|---|---|---|---|
| **leer** | Abschnitts-Leerhinweise, Sign-in-Karte | ausgeblendet | nicht erreichbar | „Nothing queued" |
| **lädt** | Platzhalterzeilen je Abschnitt | Spinner in Play-Taste | Spinner in Play-Taste, Seekbar gesperrt | Liste da, laufender Titel mit Spinner-Symbol |
| **Fehler** | Zeile ⚠ + Retry je Abschnitt | Hinweis statt Interpret (⚠, rot) | Banner + „Try again" | unverändert |
| **spielt** | aktueller Titel markiert | ❚❚, Fortschritt läuft | ❚❚, Spektrum läuft | „Now playing"-Zeile mit Pegelsymbol |
| **pausiert** | Markierung bleibt | ▶ | ▶, Spektrum eingefroren und abgeblendet | wie spielt, Symbol statisch |
| **Vorschau** | `PREVIEW`-Etikett | `PREVIEW` | `PREVIEW` + Hinweiszeile | `PREVIEW` in der Zeile |
| **keine Verbindung** | Offline-Banner, Suche aus | ☁̸ „Waiting for network…" | ☁̸-Zeile, Transport bleibt bedienbar | unverändert |

---

## 4. Zustand und Datenfluss

```
AudioPlayerService (:player)  ── schreibt ──▶ NowPlayingState / PlaybackQueueState / PlayerMeterState*
          ▲ Intents, QueueRequests                     │
          │                                            ▼
     PlayerController (:app, Singleton)  ── StateFlows ──▶ PlayerViewModel ─▶ Bibliothek, Mini, Now Playing, Queue
                                                     └──▶ MainViewModel  ─▶ EQ-Tab (Status, Kurve, Route)
UI-Zustand (kein Player-Zustand): PlayerSheetState (Ausklappgrad, Sheets), Suchtext, Scrollposition
* neu, nur Messwerte (Abschnitt 4.3)
```

**Regel:** Es gibt keine zweite Quelle für Wiedergabezustand. Mini-Player, Now Playing und Queue
lesen alle `PlayerViewModel.nowPlaying/queue`. Das ist dieselbe Instanz, die `AppNavHost` heute schon
an die Player-Route reicht. Sie wird dafür in der Shell gesammelt und an Mini-Player und Now Playing
gegeben. `MainViewModel.nowPlaying` wird für die Mini-Leiste nicht mehr gebraucht.
`toggleNowPlayingPlayback` entfällt dort.

### 4.1 Neue, reine UI-Zustände
- `PlayerSheetState` (in der Shell, `rememberSaveable`): `expandedFraction` (0..1, aus
  `AnchoredDraggableState` mit Ankern *collapsed*/*expanded*), `queueOpen`, `soundOpen`.
- `LibraryUiState` im `PlayerViewModel`: wird aus vorhandenen Flows (`playlists`, `libraryState`,
  `signedIn`, Discovery, `recent`) per `combine` zusammengesetzt. Er ersetzt die Tab-Logik
  (`PlayerTab`, `requestedTab`).
- `NowPlayingUi` als reine Abbildungsfunktion `NowPlaying? → NowPlayingUi`
  (Anzeige-Strings, Etiketten, Freigaben der Knöpfe). Testbar ohne Compose.

### 4.2 Neue Daten
| Was | Woher | Wo gespeichert | Hinweis |
|---|---|---|---|
| Zuletzt gehört | `PlayerViewModel`-unabhängiger `RecentTracksRepository` (Singleton in `:app`) sammelt `controller.nowPlaying`, distinct nach Track-ID, sobald ein Titel ≥ 10 s spielte | Room-Tabelle `recent_tracks` (id, title, artist, artworkUrl, durationMs, playedAtMs), max. 50 → **Schema v3 mit Migration** | Entscheidung E1 |
| Qualitäts-Etikett | Dienst liest beim Format-Wechsel `ExoPlayer.audioFormat` (`sampleMimeType`, `sampleRate`, `bitrate`/`averageBitrate`) und das von `StreamSelection` gewählte Preset | neues Feld `NowPlaying.audioFormat: AudioFormatInfo?` | Teil der vorhandenen Quelle, keine neue |
| Ausgabegerät | `MainViewModel.displayRoute` (vorhanden) | – | wird an Now Playing durchgereicht |
| Wellenform | `waveform_url` aus der Track-Antwort (Liste von Pegelwerten) | Speicher-Cache je Track-ID | **abhängig von Roadmap 04** (E2) |
| Like | Schreib-Endpunkt fehlt | – | **abhängig von Roadmap 04** (E2) |

### 4.3 Spektrum und Limiter-Anzeige (P3-3)
- `PlayerDspAudioProcessor` schreibt, **nur solange jemand zusieht** (`PlayerMeterState.listeners > 0`),
  die Mono-Summe *nach* der DSP-Kette in einen Ringpuffer (4.096 Float, ohne Allokation, ohne Sperre).
  Dazu kommt einmal pro Puffer die kleinste Limiter-Verstärkung (neuer Getter
  `LookaheadLimiter.gainReductionDb`, durchgereicht über `PlayerDspPcm16`).
- Die UI holt mit 30 fps (`withFrameNanos`, nur bei sichtbarem Now Playing) 2.048 Samples, rechnet
  FFT (Hann-Fenster) → 1/6-Oktav-Bänder 20 Hz–20 kHz → dB, mit Rückfall 20 dB/s, und zeichnet sie
  als gefüllte Fläche in `SonicRule`/Steel **hinter** der Amber-EQ-Kurve. Das Spektrum ist
  Hintergrund und konkurriert nicht mit der Kurve (CLAUDE.md §4).
- Limiter-Anzeige: Balken mit 6 Segmenten bis −6 dB, Zahl in dB. Unter 1 dB Steel, 1–3 dB
  `SonicCaution`, darüber `SonicClip`. Die Zahl steht immer daneben, Farbe ist nie allein.
- Grün: nur die Statuslampe, wenn `EqStatusKind.ACTIVE`. Spektrum und Limiter sind **nicht** grün.
- Kosten: FFT 2.048 bei 30 fps auf dem UI-Thread ist gering. Im Audio-Thread nur eine Kopie. Ohne
  Zuschauer passiert nichts. Messung am Gerät im Testplan (T-P5).

---

## 5. Komponenten- und Dateiaufteilung

Neu unter `app/src/main/java/com/hardbasseq/eq/ui/player/`:

| Datei | Inhalt | Herkunft |
|---|---|---|
| `shell/PlayerShell.kt` | Mini-Player + Now-Playing-Ebene + Sheets, `PlayerSheetState` | neu; Einbau in `AppNavHost` |
| `mini/MiniPlayer.kt` | Mini-Player | ersetzt `NowPlayingBar` (`MainScreen.kt`) |
| `nowplaying/NowPlayingScreen.kt` | Vollbild | Cover/Transport-Block aus `PlayerScreen.kt` (Z. 214–332) |
| `nowplaying/SeekBar.kt`, `WaveformSeekBar.kt` | Seekbar, später Wellenform | `SeekBar` aus `PlayerScreen.kt` |
| `nowplaying/TransportRow.kt` | Shuffle/Prev/Play/Next/Repeat | aus `PlayerScreen.kt` + `PlayerControls.kt` (`PlayKey`) |
| `nowplaying/SignalMeter.kt` | Spektrum + Limiter | neu |
| `nowplaying/NowPlayingUi.kt` | Abbildung `NowPlaying → NowPlayingUi` | neu, unit-getestet |
| `queue/QueueSheet.kt` | Queue mit Ziehen/Wegwischen | Queue-Tab aus `PlayerScreen.kt`, `QueueEntryMenu` |
| `sound/SoundSheet.kt` | Kontext-Chips, DSP-Karte | Kartenblock aus `PlayerScreen.kt`, `PlayerDspCard.kt` |
| `library/LibraryScreen.kt` | Startseite mit Abschnitten und Suchmodus | Rest von `PlayerScreen.kt` |
| `library/LibrarySections.kt` | Recently, Likes, Playlists, New uploads | `LibraryRow`, `discoveryItems` |
| `library/PlaylistDetailScreen.kt` | Detailseite | `PlaylistRow` |
| `library/ImportDialog.kt` | Link-Import, Spotify-Fortsetzung, Brücke | Import-Karte und Brückenkarte aus `PlayerScreen.kt` |
| `library/PlaylistDialogs.kt` | Neu/Hinzufügen/Zusammenführen | `PlaylistDialog`, `MergePlaylistsDialog` |
| `common/TrackRow.kt`, `common/QualityTag.kt`, `common/PreviewTag.kt` | gemeinsame Zeilen und Etiketten | `PlayerQueueUi.kt`, `TrackHighlight.kt` |

Bleibt: `PlayerViewModel` (wächst um `LibraryUiState`, `recent`). Die Aufteilung von `PlayerViewModel`
selbst gehört zu P6-1, nicht hierher. `PlayerSections.kt` verliert `PlayerTabs`. `SourceSelector` und
`searchItems` bleiben. `PlayerScreen.kt` entfällt am Ende.

Außerhalb `ui/player`: `AppNavHost.kt` (Shell, neue Route), `MainScreen.kt` (Mini-Leiste raus),
`MainActivity.kt` (Intent → Now Playing aufgeklappt), `:player` `NowPlayingState.kt`
(`audioFormat`), `AudioPlayerService.kt` (Format, Meter), `PlayerDspAudioProcessor.kt` (Ringpuffer),
neu `PlayerMeterState.kt`, `:core` `LookaheadLimiter.kt`/`PlayerDspPcm16.kt` (Getter),
`strings.xml`; bei E1 Room v3 (`AppDatabase`, `Migrations.kt`, Schema-JSON).

---

## 6. Migrationsreihenfolge

Jeder Schritt ist ein eigener Commit (oder PR) mit grünem Build, Tests und Lint. Nach jedem Schritt
ist die App benutzbar.

| Schritt | Inhalt | Roadmap-Punkt | Risiko |
|---|---|---|---|
| **S1** | `PlayerScreen.kt` ohne sichtbare Änderung in Dateien je Bereich zerlegen (reiner Umzug) | P3-4 | gering |
| **S2** | Mini-Player in die Shell, beide Tabs; `NowPlayingBar` raus | P3-1.2 | gering |
| **S3** | Now Playing als ausziehbare Ebene; Cover/Transport aus der Liste dorthin; Back/Predictive Back; Benachrichtigung öffnet aufgeklappt | P3-1.3, P3-2 | mittel (Gesten, Insets) |
| **S4** | Queue-Sheet mit Ziehen und Wegwischen; Queue-Tab entfällt | P3-2 | mittel (Drag in LazyColumn) |
| **S5** | Sound-Sheet hinter dem EQ-Chip; Kontext-Chips/DSP-Karte aus der Liste | P3-4 | gering |
| **S6** | Bibliothek als Startseite: Suchfeld oben, Abschnitte, Playlist-Detail-Route, Import-Dialog; Tabs entfallen | P3-1.1, P3-4 | mittel (viel Umzug) |
| **S7** | Zuletzt gehört (nach E1: Room v3) | P3-1.1 | gering–mittel (Migration) |
| **S8** | Qualitäts-Etikett, `PREVIEW`-Etikett überall | P3-3 | gering |
| **S9** | Signal-Anzeige: Ringpuffer, Limiter-Getter, Spektrum, Schalter im Sound-Sheet | P3-3 | mittel (Audio-Thread) |
| **S10** | Wellenform-Seekbar und Like | P3-2 | **blockiert durch Roadmap 04** |

---

## 7. Querschnitt

**Barrierefreiheit**
- Mini-Player ist *ein* TalkBack-Knoten („Now playing: Title by Artist, Playing") mit Custom Actions
  Play/Pause, Next, Open player. Die Statuslampe ist ein eigener Knoten („EQ active").
- Now Playing hat einen sichtbaren Zuklapp-Knopf ⌄. Die Wischgeste ist nie der einzige Weg.
- Seekbar: `Slider` mit `stateDescription` „1:12 of 4:05", Schritte von 5 s über die Lautstärketasten
  des Screenreaders.
- Queue: Custom Actions statt Drag (siehe 3.4). Wegwischen geht auch über das Menü.
- Etiketten (`PREVIEW`, Qualität, Limiter-dB) sind Text, keine Farbe allein. Kontrast ≥ 4,5:1
  (Steel auf Graphite rund 5,7:1, Amber auf Graphite rund 9,2:1).
- Schriftgröße bis 200 %: Titel umbrechen auf 2 Zeilen, der Mini-Player wächst in der Höhe (min 64),
  die Transportzeile behält 48er-Ziele.

**Bildschirmgrößen**
- Kompakt (360 × 640) als Referenz; das Cover schrumpft auf 42 % der Höhe.
- Querformat: Now Playing zweispaltig, die Bibliothek bleibt eine Spalte.
- Breite ≥ 600 (Tablet/Foldable aufgeklappt): Inhalte zentriert, max. 640 breit. Eine Navigation-Rail
  ist nicht Teil dieses Plans.

**Insets**
- Die Shell kümmert sich um Navigationsleiste und Tastatur (`imePadding` in der Bibliothek, Mini-Player
  bei offener Tastatur aus).
- Now Playing zeichnet randlos über Status- und Navigationsleiste, der Inhalt mit
  `WindowInsets.systemBars`. Sheets nutzen die Standard-Insets von `ModalBottomSheet`.
- Display-Cutout im Querformat: `safeDrawing`.

**Animationen**
- Aufklappen folgt dem Finger; Loslassen schnappt mit Spring (Dämpfung 1, mittlere Steifigkeit).
- Das Cover wandert per Interpolation der Grenzen vom Mini-Thumbnail ins große Cover
  (`SharedTransitionLayout`, wenn stabil im BOM 2026.08, sonst manuell über `expandedFraction`).
- Animator-Dauer 0 (System „Animationen entfernen") → alles springt, das Spektrum steht still.
- Keine Glows oder Verläufe. Das Cover bekommt keinen farbigen Hintergrund-Blur (CLAUDE.md:
  Gradienten sparsam).

---

## 8. Abhängigkeiten

- **Roadmap 02:** Im Code abgeschlossen (Queue in ExoPlayer, Shuffle/Repeat, Coil, `PlayerNotice`).
  Die Gerätetests stehen noch aus. Das Redesign baut darauf auf, blockiert aber nicht. Fehler, die bei
  diesen Tests auftauchen, sind vor S3/S4 zu beheben (Queue-Bedienung).
- **Roadmap 04 (offizielle SoundCloud-API):** Like und Wellenform brauchen Endpunkte. Ohne schriftliche
  Freigabe durch SoundCloud werden **keine** neuen inoffiziellen Schreibzugriffe gebaut. Bis dahin
  bleiben die Knöpfe weg; es gibt keine toten Bedienelemente. Ändert 04 Login oder Likes-Laden,
  müssen die Bibliotheks-Abschnitte nur ihre Datenquelle tauschen.
- **P4-1 (auf `main`):** Liefert die Stream-Wahl. Das Etikett zeigt, was ExoPlayer tatsächlich
  dekodiert, und ist damit unabhängig von Annahmen über Preset-Namen.
- **P6-2:** `PlayerMeterState` folgt bewusst dem vorhandenen Muster (Singleton im selben Prozess wie
  `NowPlayingState`). Wird P6-2 umgesetzt, zieht er mit um.

---

## 9. Akzeptanzkriterien

1. Im Player-Tab sind Cover und Transport nie Teil der Scroll-Liste. Wiedergabe ist aus beiden Tabs
   jederzeit über den Mini-Player steuerbar.
2. Now Playing öffnet per Tippen, Wischen, Benachrichtigung und Sperrbildschirm. Es schließt per
   Wischen, ⌄ und Back (auch Predictive Back). Rotation behält den Zustand.
3. Queue: Umsortieren per Ziehen und per TalkBack-Aktion, Entfernen per Wischen mit Undo. Die
   Reihenfolge stimmt danach mit ExoPlayer überein (gleiche Quelle).
4. Kontext-Chips und DSP-Karte sind nur noch im Sound-Sheet. Die Bibliothek hat keine Tabs mehr, der
   Import ist ein Dialog.
5. Alle Zustände aus 3.6 sind erreichbar und sehen aus wie spezifiziert.
6. Grün erscheint nur bei `EqStatusKind.ACTIVE`. Jeder Zustand ist auch ohne Farbe erkennbar.
7. Kein Wiedergabezustand außerhalb von `NowPlayingState`/`PlaybackQueueState`; `MainViewModel`
   liefert keinen Player-Zustand mehr an die UI.
8. Spektrum/Limiter-Anzeige kosten bei geschlossenem Now Playing nichts (keine Kopie im Audio-Thread).
   Offen kein hörbarer Aussetzer und < 2 % zusätzliche CPU.
9. `PlayerScreen.kt` existiert nicht mehr. Keine neue Datei über 400 Zeilen.
10. Build, `testDebugUnitTest`, `:core:test`, ktlint und Lint grün.

## 10. Testplan

**Automatisiert (JVM)**
- Unit: `NowPlayingUi`-Abbildung (alle Zustände aus 3.6), Back-Reihenfolge als reine Funktion
  (`BackTarget.resolve(sheetState, searchActive, route)`), `LibraryUiState`-Zusammenbau
  (Abschnitte ein-/ausblenden), Spektrum-Rechnung (Sinus → richtiges Band, Rückfallzeit), Limiter-Getter
  in `LookaheadLimiterTest`, `RecentTracksRepository` (distinct, 10-s-Regel, Obergrenze 50),
  Room-Migration 2→3 (bei E1).
- ViewModel: `PlayerViewModelTest` erweitert (Library-Zustand, Queue-Undo).
- **Compose-UI-Tests mit Robolectric in `:app`** (neu: `robolectric`, `compose-ui-test-junit4` ist
  im Katalog). Abgedeckt: Mini-Player sichtbar/unsichtbar, Tippen klappt auf, Back klappt zu,
  Queue-Custom-Actions verschieben, Sound-Sheet öffnet über den EQ-Chip, Semantik-Knoten (Texte,
  `stateDescription`).

**Am Gerät** (Einträge in `docs/TEST_MATRIX.md`, P-Reihe):

| # | Prüfung |
|---|---|
| T-P1 | Fluss: Suche → Titel → Mini → Now Playing → Queue umsortieren → zu → EQ-Tab → Mini steuert |
| T-P2 | Gesten und Predictive Back (Android 14+), Rotation in Now Playing und im offenen Sheet |
| T-P3 | TalkBack-Durchgang aller Flächen, Schrift 200 %, Anzeigegröße groß |
| T-P4 | Alle Zustände: Flugmodus, Vorschau-Titel, nicht abspielbarer Titel, abgemeldet, leere Bibliothek |
| T-P5 | CPU/Akku 30 min mit offenem Now Playing (Spektrum) gegen geschlossen; kein Knacken |
| T-P6 | Benachrichtigung/Sperrbildschirm öffnen Now Playing; Android Auto unverändert |
| T-P7 | Kleines Telefon (≤ 360 dp), Querformat, Tablet/Foldable |

---

## 11. Entscheidungen für die Freigabe

| # | Frage | Empfehlung |
|---|---|---|
| E1 | „Zuletzt gehört" dauerhaft speichern (Room v3, Migration) oder nur für die laufende Sitzung? | dauerhaft. Sonst ist der Abschnitt nach jedem Neustart leer. |
| E2 | Like und Wellenform: auf Roadmap 04 warten oder schon über die heutige (inoffizielle) v2-API bauen? | warten (S10 zurückstellen). Neue inoffizielle Schreibzugriffe widersprechen der Abbruchregel von 04. |
| E3 | Signal-Anzeige (Spektrum/Limiter) standardmäßig an? | an, abschaltbar im Sound-Sheet |
| E4 | Titel antippen: nur spielen (Mini erscheint) oder sofort Now Playing öffnen? | nur spielen |
| E5 | Umfang der Freigabe: alles (S1–S9) oder zuerst S1–S5 (Gerüst ohne neue Daten)? | S1–S5 zuerst, nach Gerätetest S6–S9 |

Nach Freigabe beginnt die Umsetzung mit S1. Ohne Freigabe bleibt es bei diesem Dokument.
