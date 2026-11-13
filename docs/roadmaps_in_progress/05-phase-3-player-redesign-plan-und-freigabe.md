# Roadmap 05 – Player-Redesign „Sonic Control“

**Modell-Empfehlung:** Opus 5.5 für Konzept und Architektur; Umsetzung erst nach ausdrücklicher Freigabe.  
**Ziel:** Zunächst einen vollständigen, überprüfbaren Umsetzungsplan erstellen.  
**Abhängigkeit:** Roadmap 02 (Player-Fundament) sollte abgeschlossen sein, bevor die Umsetzung beginnt.

## Verbindliche Arbeitsweise

Laut Projektvorgabe gilt: erst Plan, dann Freigabe, dann Code. **Diese Roadmap ist zunächst ausschließlich eine Planungsaufgabe. Keine Implementierung ohne ausdrückliche Freigabe.**

## Ausgangslage

Der Player-Tab ist eine einzige Scroll-Liste (`PlayerScreen.kt`, laut Review 1.054 Zeilen). Cover und Transport stehen oben, darunter Tabs mit Suche, Playlists und Queue, danach Kontext-Chips und DSP-Karte. Beim Scrollen der Suchergebnisse verschwindet die Steuerung aus dem Bild. Eine Mini-Leiste gibt es nur im Equalizer-Tab.

## P3-1 Drei Ebenen wie bei Spotify, SoundCloud und YouTube Music (L)

1. **Bibliothek** als Startseite des Player-Tabs: Suchfeld oben, darunter Abschnitte (Zuletzt gehört, Likes, Playlists, Neue Uploads), alle Zeilen mit Cover.
2. **Mini-Player** fest über der Navigationsleiste in beiden Tabs: Cover, Titel, Interpret, Play/Pause, Weiter, Fortschrittslinie, EQ-Statuspunkt.
3. **„Läuft gerade“** als Vollbild, das aus dem Mini-Player nach oben aufzieht und sich nach unten wegwischen lässt.

## P3-2 Aufbau von „Läuft gerade“ (M)

- Großes Cover, Titel, Interpret, Like, „Zur Playlist“.
- Suchleiste als Wellenform des Titels.
- Transportzeile: Shuffle, Zurück, Play, Weiter, Wiederholen.
- Untere Zeile: Ausgabegerät (z. B. „Bluetooth · Name“), EQ-Chip mit Mini-Kurve (vorhandener `PlayerSoundChip`), Queue-Knopf.
- Queue als Bottom-Sheet mit Umsortieren und Wegwischen.

## P3-3 Eigene Handschrift statt Kopie (M)

- Live-Spektrum hinter der EQ-Kurve und eine Limiter-Anzeige (Pegelreduktion in dB). Der eigene Player hat Zugriff auf die PCM-Daten.
- Grün nur für „Signal wird verarbeitet“, Amber für Bedienelemente.
- Qualitäts-Etikett am Titel (z. B. „AAC 160 · 44,1 kHz“) und klar markierte Vorschau-Titel.

## P3-4 Aufräumen der Seite (S)

- Kontext-Chips und DSP-Karte (Mono-Bass, Limiter) aus dem Player in ein „Klang“-Sheet hinter dem EQ-Chip verschieben.
- Playlist-Import aus Link in einen eigenen Dialog; die Seite wird dadurch zur Bibliothek.
- `PlayerScreen.kt` in einzelne Dateien je Bereich teilen.

## Was der Plan liefern muss
- Informationsarchitektur und Navigationsfluss für Bibliothek, Mini-Player, „Läuft gerade“ und Queue.
- Wireframes oder präzise textuelle UI-Spezifikation für alle Zustände (leer, lädt, Fehler, spielt, pausiert, Vorschau, keine Verbindung).
- Zustands- und Datenflussbeschreibung; keine zweite, konkurrierende Player-State-Quelle einführen.
- Komponenten- und Dateiaufteilung inklusive Migrationsreihenfolge.
- Accessibility, Bildschirmgrößen, Insets, Animationen und Android-Back-Verhalten berücksichtigen.
- Abhängigkeiten zu Roadmap 02 und zur SoundCloud-Freigabe benennen.
- Akzeptanzkriterien und Testplan ergänzen.

## Freigabepunkt
Nach Vorlage des Plans anhalten. Keine UI-Dateien ändern, keine Komponenten implementieren und keine bestehenden Screens umbauen, bis der Nutzer den Plan ausdrücklich freigegeben hat.

---

## Stand (13. November 2026)

- [x] Umsetzungsplan erstellt: [`docs/PLAYER_REDESIGN.md`](../PLAYER_REDESIGN.md)
  - [x] Ist-Zustand am Code geprüft (Abschnitt 1)
  - [x] Informationsarchitektur, Navigationsfluss, Einstiege und Back-Verhalten (Abschnitt 2)
  - [x] Textuelle UI-Spezifikation mit Wireframes für Bibliothek, Mini-Player, „Now Playing", Queue- und Sound-Sheet; Zustandsmatrix leer/lädt/Fehler/spielt/pausiert/Vorschau/keine Verbindung (Abschnitt 3)
  - [x] Zustands- und Datenfluss ohne zweite Player-Zustandsquelle; neue Daten (Zuletzt gehört, Qualitäts-Etikett, Signal-Anzeige) (Abschnitt 4)
  - [x] Komponenten- und Dateiaufteilung, Migrationsreihenfolge S1–S10 (Abschnitte 5, 6)
  - [x] Barrierefreiheit, Bildschirmgrößen, Insets, Animationen (Abschnitt 7)
  - [x] Abhängigkeiten zu Roadmap 02, 04, P4-1 und P6-2 (Abschnitt 8)
  - [x] Akzeptanzkriterien und Testplan (Abschnitte 9, 10)
- [x] **Freigabe durch den Nutzer** (13. November 2026, „klingt gut, setz das so um“): E1–E5 wie empfohlen. E1 dauerhaft speichern, E2 Like/Wellenform erst nach Roadmap 04, E3 Signal-Anzeige standardmäßig an, E4 Antippen spielt nur, E5 zuerst S1–S5.

## Umsetzung S1–S5 (13. November 2026)

- [x] **S1** `PlayerScreen.kt` zerlegt (Playlists-Tab, Zeilen, Dialoge, Seekbar in `library/`, `nowplaying/`); 1.063 → 212 Zeilen, keine neue Datei über 400 Zeilen
- [x] **S2** Mini-Player in der Shell, beide Tabs (`mini/MiniPlayer.kt`, `shell/PlayerShell.kt`); `NowPlayingBar` und `MainViewModel.nowPlaying`/`toggleNowPlayingPlayback` entfernt
- [x] **S3** „Now playing“ als ausziehbare Ebene (`nowplaying/NowPlayingScreen.kt`, `shell/PlayerSheetState.kt`): Ziehen, ⌄, Predictive Back, Benachrichtigung/Sperrbildschirm öffnen aufgeklappt (`requestNowPlaying`), Querformat zweispaltig
- [x] **S4** Queue-Sheet (`queue/QueueSheet.kt`, `QueueReorder.kt`): Ziehgriff, Wegwischen mit Undo, „Clear upcoming“ mit Undo, TalkBack-Aktionen; Queue-Tab entfernt
- [x] **S5** Sound-Sheet hinter EQ-Chip und Mini-Player-Lampe (`sound/SoundSheet.kt`): Kontext-Chips, DSP-Karte, „Open equalizer“
- [x] Unit-Tests: `NowPlayingUiTest` (7), `SheetSettleTest` (2), `QueueReorderTest` (4), `PlayerViewModelTest` (+6)
- [x] Compose-UI-Tests mit Robolectric (`PlayerShellUiTest`, 6 Tests, schreibt Screenshots nach `app/build/player-screenshots/`)
- [x] Lokal: `assembleDebug`, `testDebugUnitTest`, `:core:test`, `ktlintCheck`, `lintDebug` grün (keine neuen Lint-Warnungen)
- [ ] **Gerätetest** T-P1–T-P4, T-P6, T-P7 (`docs/TEST_MATRIX.md`), danach S6–S9 (E5)

**Abweichungen vom Plan (bewusst, klein):**
- Das Cover wandert nicht vom Mini-Thumbnail ins große Cover. Die Ebene schiebt sich als Ganzes von unten hoch. Der Morph ist Feinschliff nach dem Gerätetest.
- Ausgabegerät nur als Anzeige, kein System-Ausgabewähler (im Plan als Rückfall vorgesehen).
- Queue-Sheet zeigt die ganze Queue (gespielte Titel mit Haken, laufender Titel umrandet) statt getrennter Abschnitte „Now playing“/„Next up“. Der Ziehgriff braucht keinen Long-Press, weil er nur ein Griff ist.
- Undo beim Entfernen: anhängen und an die alte Stelle verschieben (der Dienst kennt kein „einfügen an“). Beide Befehle laufen in Reihenfolge durch den Dienst.
