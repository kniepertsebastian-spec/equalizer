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
