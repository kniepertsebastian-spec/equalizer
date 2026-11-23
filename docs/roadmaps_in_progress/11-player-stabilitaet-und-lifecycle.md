# Roadmap 11 – Player-Stabilität, Threading & Android-12+-Lifecycle

**Priorität:** Hoch (Blocker B3, Major M4, Major M5, Major M6)  
**Ziel:** Hintergrund-Crashes auf Android 12+ (`BackgroundServiceStartNotAllowedException`), unbegrenzte Thread-Erzeugung beim Stream-Lookup und Main-Thread-Disk-I/O beim Start von `AudioPlayerService` beseitigen sowie die globalen Singletons schrittweise entkoppeln.  
**Abhängigkeit:** Keine (Welle 1 – parallel zu Roadmap 08 und 12 startbar; in `AndroidPlayerBridge.kt` nur `stopPlayer`/`togglePlayback` anfassen, nicht `setPlayerCurve`).

---

## Ausgangslage / Befund

1. **Android-12+-Crashrisiko durch `startService()` (Blocker B3):**  
   `AndroidPlayerBridge.stopPlayer()` und `togglePlayback()` (`AndroidPlayerBridge.kt:25-39`) rufen `context.startService(intent)` auf. Wird das aus dem Hintergrund (Quick-Tile, Widget, Remote-Server) ausgelöst, wirft Android 12+ (`targetSdk = 36`) eine `BackgroundServiceStartNotAllowedException`.
2. **`runBlocking` in unbegrenztem `CachedThreadPool` (Major M4):**  
   `ResolvingTrackMediaSourceFactory` (`ResolvingTrackMediaSource.kt:46`) verwendet `Executors.newCachedThreadPool()`, und `SoundCloudStreamResolver.resolve()` (`SoundCloudStreamResolver.kt:17`) blockiert darin per `runBlocking`. Schnelles Skippen oder hängende Netzverbindungen können viele blockierte Threads anhäufen.
3. **Synchrones `SharedPreferences`-Lesen auf dem Main Thread (Major M5):**  
   `AudioPlayerService.onCreate()` (`AudioPlayerService.kt:228-229`) ruft synchron `PlayerDspState.load(this)` und `PlaybackFadeState.load(this)` auf (StrictMode-`DiskReadViolation`, ANR-Risiko beim Kaltstart).
4. **Prozessweite Mutable-Singletons statt DI/Session-IPC (Major M6):**  
   `PlayerCurveState`, `VirtualBassState`, `PlayerDspState`, `NowPlayingState`, `PlaybackQueueState` und `QueueRequests` teilen veränderlichen Zustand über globale `object`-Singletons.

---

## Zugewiesener Dateiumfang

- `app/src/main/java/com/auracurve/app/integration/AndroidPlayerBridge.kt` *(nur `stopPlayer`, `togglePlayback`, `launchPlayer`)*
- `app/src/test/java/com/auracurve/app/integration/PlayerServiceStartTest.kt`
- `player/app/src/main/java/com/auracurve/app/player/playback/ResolvingTrackMediaSource.kt` (+ Tests)
- `player/app/src/full/java/com/auracurve/app/player/playback/SoundCloudStreamResolver.kt`
- `player/app/src/main/java/com/auracurve/app/player/playback/StreamResolver.kt`
- `player/app/src/main/java/com/auracurve/app/player/playback/PlayerDspState.kt` *(nur Persistenz-/Load-Pfad; `PlayerCurve` gehört zu RM 08)*
- `player/app/src/main/java/com/auracurve/app/player/playback/PlaybackTimerState.kt`
- `player/app/src/main/java/com/auracurve/app/player/service/AudioPlayerService.kt` *(nur `onCreate()`-Initialisierung; `buildRenderersFactory()` gehört zu RM 09)*

---

## Arbeitspakete

### P11-1 – Android-12+-sichere Player-Steuerung (Blocker B3)
- [x] `AndroidPlayerBridge.stopPlayer()` und `togglePlayback()` von rohem `context.startService()` lösen:
  - **Bevorzugt:** Media3 `MediaController.Builder(context, SessionToken(context, ComponentName(context, AudioPlayerService::class.java))).buildAsync()` nutzen, um `pause()` / `play()` / `stop()` sauber über die `MediaLibrarySession` auszulösen (funktioniert auch aus Hintergrund-Triggern wie Quick-Tiles, Widgets und Remote-API).
  - **Für `stopPlayer()` (wenn kein aktiver Service läuft):** Kein `startService()` absetzen, nur um sofort wieder zu stoppen (`stopService()` oder nur senden, wenn `NowPlayingState` aktiv ist).
- [x] `PlayerServiceStartTest` um Szenarien für Hintergrund-Trigger (API 31+) erweitern.

### P11-2 – Stream-Lookup-Threading begrenzen & `runBlocking` ablösen (Major M4)
- [x] `StreamResolver.resolve(trackId: Long)` auf `suspend fun` umstellen (oder falls synchrone Signatur für Tests bleiben muss: mindestens `Executors.newFixedThreadPool(2)` mit benannter Daemon-`ThreadFactory` statt `newCachedThreadPool()`).
- [x] Bei Umstellung auf `suspend`: in `ResolvingTrackMediaSourceFactory` einen eigenen `CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(2))` verwenden und laufende Lookups in `releaseSourceInternal()` / `release()` sauber per `Job.cancel()` abbrechen.
- [x] `runBlocking` aus `SoundCloudStreamResolver.kt:17` entfernen und direkt `client.getTrack(trackId)` suspenden.

### P11-3 – Main-Thread-I/O aus `AudioPlayerService.onCreate()` entfernen (Major M5)
- [x] `PlayerDspState.load(context)` und `PlaybackFadeState.load(context)` asynchron über `libraryScope` auf `Dispatchers.IO` ausführen (oder langfristig auf DataStore umstellen), ohne den Main Thread in `onCreate()` zu blockieren.
- [x] Sicherstellen, dass `PlayerDspAudioProcessor` bis zum Abschluss des asynchronen Ladens mit sicheren Defaults (`monoBassEnabled = true`, `limiterEnabled = true`) startet und danach automatisch den geladenen Zustand aus dem `StateFlow` übernimmt.

### P11-4 – Schrittweise Entkopplung der globalen Singletons (Major M6)
- [x] Ein sauberes Interface (`PlayerDspStore`) für DSP-Einstellungen, Kurve und Virtual Bass definieren und die bestehenden `object`-Singletons dahinter kapseln, sodass `:app` und `:player`-Tests ohne globalen Restzustand laufen können.
- [x] Dokumentieren (kurzer ADR-Nachtrag zu `docs/adr/0004-player-state-and-hilt.md`), welche Singletons als Nächstes in Media3-`SessionCommand` / Hilt-`@Singleton` überführt werden.

---

## Abnahmekriterien & Verifikation

- [x] **Unit-Tests:**
  - `PlayerServiceStartTest` bestätigt: `stopPlayer()`/`togglePlayback()` verursachen keine illegale `startService()`-Ausführung im Hintergrund.
  - `ResolvingTrackMediaSourceTest` bestätigt: Schnelles Weiterskippen über 10 Tracks bricht veraltete Lookups ab; aktive Lookup-Concurrency bleibt `<= 2`.
- [x] **Build & Lint:**
  ```bash
  ./gradlew :player:testFullDebugUnitTest :app:testFullDebugUnitTest :app:ktlintCheck
  ```
- [ ] **Gerätetest:**
  - App vollständig schließen, Wiedergabe per Quick-Tile, Homescreen-Widget und Bluetooth-Taste umschalten — kein Crash im `Logcat`.
  - Mit aktiviertem `StrictMode.ThreadPolicy.Builder().detectDiskReads().penaltyLog()` keine `DiskReadViolation` beim Start von `AudioPlayerService`.

---

## Umsetzung (Stand: erledigt bis auf Gerätetest)

**Status:** P11-1 bis P11-4 umgesetzt, Unit-Tests, Build und ktlint grün. Offen: Gerätetest (Quick-Tile,
Widget, Bluetooth-Taste bei geschlossener App; StrictMode-Lauf beim Service-Start).

### P11-1 – Player-Steuerung ohne `startService()`
- Neu: `player/.../service/PlayerSessionCommands.kt` mit `PlayerCommandSender`. Ein kurzlebiger Media3-`MediaController`
  (Main-Looper, auch aus Threads ohne Looper wie dem Remote-Server) bindet an `AudioPlayerService` und schickt
  `ACTION_STOP` / `ACTION_TOGGLE_PLAYBACK` als **`SessionCommand`**; danach wird er wieder freigegeben.
- Warum `SessionCommand` statt `controller.stop()/play()`: der Dienst behandelt beide Befehle exakt wie die
  bisherigen Intents (`handleCommand`): Stopp leert die Queue, beendet den Sleep-Timer und schließt die
  Audio-Session für den EQ; Play/Pause nutzt `isPlayingOrLoading`/`resumePlayer`. Kein Verhaltenswechsel.
- `AudioPlayerService.LibraryCallback`: `onConnect` gewährt die zwei Befehle **nur Controllern mit der eigenen
  UID** (der Dienst ist für Auto/System exportiert), `onCustomCommand` prüft UID und Aktion erneut.
- `AndroidPlayerBridge`: `stopPlayer()`/`togglePlayback()` senden nur, wenn ein Track geladen ist
  (`NowPlayingState`); sonst passiert nichts – kein Service-Start, nur um sofort wieder zu stoppen.
  `setPlayerCurve`/`setVirtualBassMix` unverändert (RM 08).
- Tests (`PlayerServiceStartTest`): Hintergrund-Context, der jeden `startService`/`startForegroundService` wie
  Android 12+ ablehnt – Stopp und Play/Pause laufen über die Session, null Service-Starts; ohne geladenen Track
  wird gar nichts gesendet.

### P11-2 – Stream-Lookup begrenzt und abbrechbar
- `StreamResolver.resolve` ist `suspend`; `SoundCloudStreamResolver` ohne `runBlocking`.
- `ResolvingTrackMediaSourceFactory`: eigener `CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(2))`
  statt `newCachedThreadPool()`, dazu ein `Semaphore(2)`: `SoundCloudClient` wechselt intern auf
  `Dispatchers.IO` und entkäme sonst dem `limitedParallelism`-View. Höchstens 2 Lookups laufen gleichzeitig.
- Abbruch: `releaseSourceInternal()` und `release()` canceln laufende Lookups; zusätzlich `disableInternal()` –
  ExoPlayer **deaktiviert** (nicht released) Einträge, an denen der Nutzer vorbeigeskippt ist. Ein dort noch
  laufender Lookup wird abgebrochen, `enableInternal()` startet ihn neu, falls der Eintrag wieder dran ist.
  Eine Generationsnummer verwirft Antworten abgebrochener Lookups.
- Grenze: ein blockierender OkHttp-Aufruf selbst wird durch das Canceln nicht unterbrochen; er hält seinen Slot
  bis zum OkHttp-Timeout, sein Ergebnis wird verworfen. Wartende Lookups erreichen das Netz dann gar nicht.
- Tests (`ResolvingTrackMediaSourceTest`, echter ExoPlayer): 10× schnell weiterskippen bricht die Lookups von
  Track 1–10 ab, max. 2 gleichzeitig, Track 11 spielt; hängende, nicht abbrechbare Lookups halten das Limit 2
  (Track 3–10 erreichen das Netz nie); Player-Release cancelt einen laufenden Lookup.

### P11-3 – Kein Disk-I/O in `AudioPlayerService.onCreate()`
- `PlayerDspState.load` und `PlaybackFadeState.load` laufen in `libraryScope` auf `Dispatchers.IO`. Bis dahin
  gelten die sicheren Defaults (Mono-Bass und Limiter an, kein Fade); `PlayerDspAudioProcessor` liest den
  `StateFlow` pro Puffer und übernimmt die geladenen Werte von selbst, der Fade-Ticker wird danach neu gestartet.
- Zusätzlich lädt `PlayerDspViewModel` jetzt ebenfalls asynchron (vorher synchron im `init` auf dem Main Thread).

### P11-4 – Entkopplung der Singletons (Etappe 1–2 aus ADR 0004)
- Neu: `PlayerDspSource` (lesen, Audio-Thread), `PlayerDspStore` (lesen/schreiben/`load()`),
  `SharedPlayerDspStore` (auf den bisherigen Objekten), `InMemoryPlayerDspStore` (Tests, kein globaler Zustand).
- `PlayerDspAudioProcessor(source = PlayerDspSource.ProcessWide)`; `buildRenderersFactory()` (RM 09) unverändert.
- `:app`: `PlayerDspStore` per Hilt (`IntegrationModule`), `PlayerDspViewModel` nutzt nur noch den Store.
- ADR-Nachtrag in `docs/adr/0004-player-state-and-hilt.md` mit der Reihenfolge der nächsten Etappen.
- Tests: `PlayerDspStoreTest` (Isolation, Defaults, Clamping, Prozessor liest die injizierte Quelle),
  `PlayerDspViewModelTest`.

### Abweichungen vom zugewiesenen Dateiumfang
- Neue Dateien `PlayerSessionCommands.kt`, `PlayerDspStore.kt` (+ Tests); `AudioPlayerService.kt` zusätzlich zu
  `onCreate()` um `onConnect`/`onCustomCommand` im `LibraryCallback` ergänzt (nötig für P11-1, berührt
  `buildRenderersFactory()` nicht).
- `PlayerDspAudioProcessor.kt` (Konstruktor-Parameter), `PlayerDspViewModel.kt`, `IntegrationModule.kt` für P11-4.
- `PlaybackTimerState.kt` blieb unverändert: `PlaybackFadeState.load` ist bereits idempotent, nur der Aufrufer
  im Dienst wurde verlegt.

### Offene Punkte
- Gerätetest gemäß Abnahmekriterien (Quick-Tile, Widget, Bluetooth bei geschlossener App; StrictMode).
- Nicht im Umfang, aber gleiches Muster: `AndroidPlayerController` (Player-Screen, Remote-Server
  `playPause`) startet den Dienst weiterhin per `startService()` (abgesichert durch `PlayerServiceStart`, Befehl
  wird im Hintergrund verworfen statt zu crashen) und `AndroidPlaybackTimers` lädt `PlaybackFadeState`
  synchron. Siehe ADR-0004-Nachtrag, Etappe 3.

