# ADR 0004: `:player` behält vorerst seine globalen Zustandsobjekte und kein Hilt

Status: Akzeptiert (zurückgestellt, nicht verworfen)
Datum: 13. November 2026
Kontext: Roadmap 06, P6-2 („`:player` angleichen“)

## Ausgangslage

`:app` nutzt Hilt und injizierte Repositories. `:player` (früher eigenes Repository) hält seinen Zustand
in Singleton-Objekten: `PlayerDspState`, `PlayerCurveState`, `VirtualBassState`, `NowPlayingState`,
`PlaybackQueueState`, `QueueRequests`, und neu `SleepTimerState` und `PlaybackFadeState`.
`AudioPlayerService` ist kein Hilt-Dienst.

## Was umgesetzt ist

- Versionen im Versionskatalog, Lint nicht mehr abgeschaltet (siehe Roadmap 06, P6-2).

## Was zurückgestellt wird, und warum

Hilt in `:player` und das Auflösen der Objekte wäre ein eigenes Refactoring mit Verhaltensrisiko:

1. Die Objekte werden vom **Audio-Thread** gelesen (`PlayerDspAudioProcessor` liest `PlayerCurveState`,
   `PlayerDspState`, `VirtualBassState` pro Puffer). Eine Injektion dorthin braucht eine Übergabe beim Bau
   des Prozessors statt eines globalen Zugriffs, und jede Änderung an dieser Stelle ist nur mit einer
   Hörprobe (Roadmap 03, K26–K28) abzusichern, die hier nicht möglich ist.
2. `AudioPlayerService` ist ein `MediaLibraryService`; mit `@AndroidEntryPoint` ändert sich seine
   Erzeugung. Der Auto-Katalog (`AutoCatalogHolder`) ist ebenfalls ein globaler Halter.
3. Die Tests von `:player` (Robolectric mit echtem ExoPlayer) greifen auf die Objekte direkt zu.

## Etappenplan (wenn aufgenommen)

1. Die schreibenden Stellen hinter schmale Schnittstellen legen (`NowPlayingSink`, `PlayerDspSettingsSource`),
   die Objekte bleiben als Standardimplementierung. Kein Verhaltenswechsel.
2. `PlayerDspAudioProcessor` bekommt seine Quellen per Konstruktor.
3. Hilt-Plugin in `:player`, `AudioPlayerService` als `@AndroidEntryPoint`, die Objekte werden `@Singleton`-Klassen.
4. Jede Etappe einzeln mit Gerätetest und Hörprobe.

## Folgen

Neue Zustände im Player (Sleep-Timer, Fade) folgen bis dahin dem bestehenden Muster (Singleton-Objekt,
nur der Dienst schreibt), damit der spätere Umbau mechanisch bleibt.
