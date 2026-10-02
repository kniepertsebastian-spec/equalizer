# UX- und Produkt-Roadmap – HardBass EQ

**Stand:** 2. Oktober 2026  
**Zweck:** Die besprochenen Verbesserungen an EQ, Player und Orientierung zu einem priorisierten, überprüfbaren Arbeitsplan bündeln. Diese Roadmap ergänzt die bestehende technische Roadmap.

## Fortschritt (wird bei jedem Schritt gepflegt)

> **Release-Regel:** Der nächste Release wird erst gebaut, wenn alle Punkte erledigt sind.

| # | Schritt | Status |
|---|---|---|
| 1 | P0 Player-Navigation und Quellenwahl | umgesetzt (Gerätetest offen) |
| 2 | P0 Playlist-Import, Playlists, Warteschlange auffindbar | umgesetzt (Gerätetest offen) |
| 3 | P0 Echter EQ-Status, lautheitsangepasster A/B-Vergleich | umgesetzt (Gerätetest offen) |
| 4 | P1 Klangziele („Gesang vorne“ …) und Beschreibungen | umgesetzt (Gerätetest offen) |
| 5 | P1 Verknüpfte Dienste getrennt von Klangprofilen | umgesetzt (Gerätetest offen) |
| 6 | P1 Vollständige englische Lokalisierung | offen |
| 7 | P2 Headroom/AutoEQ-Vorschau, Geräteprofile | offen |
| 8 | P2 Mikrofon-Entscheidung, Zugänglichkeit | offen |

## Zielbild

Neue Nutzer sollen ohne Vorwissen erkennen können:

- wo sie Musik suchen und starten;
- wie sie SoundCloud, YouTube oder einen externen Player auswählen;
- wo importierte Playlists und die Warteschlange liegen;
- ob und auf welchem Audiopfad der EQ tatsächlich aktiv ist;
- wie sie gezielt einen Klang wie „Gesang vorne“ statt „Mehr Power“ auswählen;
- wo verknüpfte Dienste und Klangprofile verwaltet werden.

Der Player und der EQ sind zwei klar erkennbare Hauptbereiche. Sie zeigen den aktuellen Titel und den EQ-Status so an, dass sie als zusammengehöriger Ablauf verständlich bleiben.

## P0 – Player und Navigation erkennbar machen

### Aufgaben

- Hauptnavigation mit **Player** und **Equalizer** einführen.
- Im Player dauerhaft erreichbare Bereiche anbieten:
  - **Suchen**;
  - **Playlists**;
  - **Warteschlange**.
- Einen sichtbaren Quellenwähler im Player platzieren, zum Beispiel **SoundCloud ▾**. Ein Wechsel zu SoundCloud darf nicht in einem allgemeinen Menü versteckt sein.
- Die unterstützten Quellen korrekt und konsistent darstellen. YouTube funktioniert laut aktuellem Projektstand ebenfalls; veraltete Dokumentation, die es als nicht funktionsfähig beschreibt, aktualisieren.
- Playlist-Import als klar beschriftete Aktion auf der Playlists-Seite anbieten. Importierte Listen müssen nach dem Import leicht wiederzufinden sein.
- Die Warteschlange mit Anzahl, aktuellem Titel und verständlichem Leerzustand darstellen.
- Einen kompakten Player mit Titel und Wiedergabesteuerung im EQ-Bereich sichtbar halten. Von dort führt **Zum Player** zurück zum Player.
- Im Player den EQ-Status und den betroffenen Pfad nennen, beispielsweise **EQ aktiv für SoundCloud**.
- Lade-, Fehler-, Offline- und leere Zustände jeweils mit verständlicher Erklärung und passender nächster Aktion versehen.

### Abnahmekriterien

- Neue Nutzer finden die SoundCloud-Suche, den Playlist-Import und die Warteschlange direkt über die Player-Navigation.
- Nutzer können zwischen unterstützten Quellen wechseln, ohne die Wiedergabe- oder EQ-Ansicht durchsuchen zu müssen.
- Aus Player und EQ ist jeweils klar erkennbar, was gerade abgespielt wird und ob der EQ auf diesem Pfad aktiv ist.

## P0 – Verlässlichen EQ-Status und fairen Klangvergleich schaffen

### Aufgaben

- Status in Alltagssprache anzeigen: **Aktiv**, **Wartet auf Wiedergabe**, **Verbindung verloren**, **Nicht unterstützt** oder **Fehler**.
- Bei jedem nicht aktiven Zustand erklären, was fehlt und was Nutzer als Nächstes tun können.
- Die Oberfläche nur dann als aktiv kennzeichnen, wenn der Audioeffekt tatsächlich angewandt wurde.
- Einen gut erreichbaren **Original / EQ**-Vergleich ergänzen.
- Beim A/B-Vergleich die Lautheit angleichen, damit ein bloßer Lautstärkeunterschied nicht wie eine Klangverbesserung wirkt.
- Bypass, Zurücksetzen und den aktuellen Preset-Namen gut sichtbar machen.

### Abnahmekriterien

- Kein Zustand vermittelt, der EQ wirke, wenn der aktive Audiopfad das nicht bestätigt.
- Der A/B-Vergleich ist mit einem Tipp bedienbar und wird nicht durch deutlich unterschiedliche Lautheit verzerrt.

## P1 – EQ nach Klangziel verständlich machen

### Aufgaben

- Presets nicht nur nach Genre, sondern auch nach gewünschtem Ergebnis anbieten.
- Für Pop und Rap beispielsweise Auswahlmöglichkeiten wie:
  - **Ausgewogen**;
  - **Gesang vorne**;
  - **Mehr Power**.
- Weitere klare Klangziele prüfen, etwa **Mehr Druck**, **Weniger scharf** und **Bass zurücknehmen**.
- Einsteiger zuerst über Klangziele und wenige verständliche Makros führen; Frequenzbänder als aufklappbare **Feinanpassung** anbieten.
- Erklären, was eine Anpassung hörbar verändert. Die EQ-Kurve kann Orientierung geben, soll aber nicht die einzige Erklärung sein.
- „Gesang vorne“ subtil abstimmen: Stimme präsenter machen und störende Maskierung verringern, ohne zu versprechen, dass der EQ Gesang aus dem Mix isoliert oder eine schlechte Aufnahme repariert.
- A/B- und Rücksetzfunktionen in den Preset-Workflow integrieren.

### Abnahmekriterien

- Nutzer können zwischen einem Klangziel für mehr Stimmpräsenz und einem für mehr Druck wählen.
- Preset-Namen und Kurzbeschreibungen erklären das erwartete Ergebnis in verständlicher Sprache.
- Ein überarbeitetes Preset erhält eine faire, lautheitsangepasste Vergleichsmöglichkeit.

## P1 – Dienste und Klangprofile sauber trennen

### Aufgaben

- Einen Bereich **Verknüpfte Dienste** für verbundene Musikdienste und Konten bereitstellen.
- Dort Verbindungsstatus und verfügbare Aktionen wie **Erneut verbinden** oder **Trennen** sichtbar machen.
- Klangprofile separat verwalten. Sie beschreiben EQ-Einstellungen für Kopfhörer oder Ausgänge und sind keine Konten.
- Klangkorrektur für ein Gerät und persönlichen Klangstil getrennt auswählbar machen.
- Profile für Bluetooth-Kopfhörer, kabelgebundene Kopfhörer und Lautsprecher speichern und beim Gerätewechsel passend anwenden, soweit der Audiopfad dies unterstützt.
- Manuelle Anpassungen als eigenes Profil oder **Benutzerdefiniert** erkennbar machen; Speichern, Zurücksetzen und Duplizieren anbieten.

### Abnahmekriterien

- **Verknüpfte Dienste** und **Klangprofile** sind an getrennten, eindeutigen Orten erreichbar.
- Beim Wechsel des Audioausgangs wird das passende Klangprofil angezeigt und, sofern unterstützt, angewandt.
- Nutzer können eine manuelle Anpassung speichern und später wiederfinden.

## P1 – Vollständige englische Lokalisierung

### Aufgaben

- **Alles Sichtbare muss ins Englische übersetzt werden.** Dazu gehören sämtliche sichtbaren Oberflächentexte, nicht nur Navigation und Hauptschaltflächen:
  - Titel, Menüs, Schaltflächen und Reglerbeschriftungen;
  - Presets, Kurzbeschreibungen und Hilfetexte;
  - Dialoge, Bestätigungen und Berechtigungs-Erklärungen;
  - Such-, Lade-, Leer-, Fehler- und Offline-Zustände;
  - Benachrichtigungen, Statusmeldungen und Hinweise;
  - Onboarding, Import- und Verbindungsabläufe;
  - Barrierefreiheitsbeschriftungen für Screenreader.
- Deutsche und englische Texte zentral verwalten, statt sichtbare Texte fest im Code zu verteilen.
- Englische Begriffe natürlich und konsistent formulieren; keine gemischten oder abgeschnittenen Texte in der Oberfläche belassen.
- Layouts mit längeren englischen Beschriftungen, größeren Schriftgrößen und schmalen Bildschirmen prüfen.
- Alle künftig hinzugefügten sichtbaren Texte gleichzeitig auf Deutsch und Englisch pflegen.

### Abnahmekriterien

- Der Wechsel auf Englisch lässt keine sichtbaren deutschen Texte, unübersetzten Systemhinweise der App oder fehlenden Accessibility-Labels zurück.
- Beide Sprachversionen decken dieselben Ansichten und Zustände ab.

## P2 – Mehr Korrektur und Sicherheit aus dem EQ holen

### Aufgaben

- Automatischen Headroom aus der kombinierten EQ-Kurve berechnen und nötige Absenkung verständlich anzeigen.
- Klar erklären, wann starker Boost die Lautstärke reduziert oder den Limiter stärker arbeiten lässt.
- AutoEQ-Profile mit Vorschau der resultierenden Kurve und benötigtem Headroom importieren.
- Herkunft und zugehöriges Ausgabegerät eines Korrekturprofils anzeigen.
- Band- und Kurvenbearbeitung als Fortgeschrittenenansicht weiterentwickeln, sobald die unterstützten Audiopfade dafür verifiziert sind.
- Preset-Aktionen wie als neues Profil speichern, duplizieren, umbenennen und löschen mit nachvollziehbarer Rückmeldung anbieten.

### Abnahmekriterien

- Kein starkes Preset führt unbemerkt zu Clipping oder deutlicher Begrenzung.
- Vor Anwendung eines importierten Korrekturprofils sind Kurve und Headroom-Bedarf sichtbar.
- Die UI unterscheidet nachweisbare Aktivität von einer bloßen Schätzung.

## P2 – Mikrofonverbesserung als getrennte Produktentscheidung behandeln

### Aufgaben

- Den Musik-EQ klar vom Mikrofoneingang abgrenzen: Änderungen am Wiedergabe-EQ bearbeiten nicht automatisch die Mikrofonstimme.
- Entscheiden, ob die App eigene Sprachaufnahme oder Mikrofon-Monitoring anbieten soll.
- Nur bei einem klaren eigenen Aufnahme-/Monitoring-Szenario einen getrennten Bereich **Mikrofon / Stimme** planen.
- Bei einer Umsetzung EQ mit den dafür geeigneten Werkzeugen kombinieren, etwa Rauschminderung oder De-Esser; nicht versprechen, dass EQ allein Hall oder Aufnahmefehler entfernt.
- Unterstützung für Mikrofonverarbeitung in fremden Apps separat prüfen. Androids Eingabeverwaltung erlaubt nicht, dass eine gewöhnliche App einfach den Mikrofoneingang anderer Apps bearbeitet.

### Abnahmekriterien

- Musik-EQ und Mikrofonverarbeitung werden in Produkttexten und Oberfläche nicht verwechselt.
- Mikrofonfunktionen werden nur für einen tatsächlich unterstützten Audiopfad angeboten.

## P2 – Zugänglichkeit und Verständlichkeit prüfen

### Aufgaben

- Touch-Ziele, Kontrast und größere Schrift berücksichtigen.
- Zustände, EQ-Regler, Presets und Wiedergabesteuerung für TalkBack sinnvoll beschriften.
- Kernabläufe mit Nutzern ohne EQ-Vorkenntnisse prüfen: Quelle wechseln, Titel suchen, Playlist importieren, Warteschlange öffnen, EQ aktivieren, „Gesang vorne“ wählen und A/B vergleichen.
- Erheben, ob Nutzer Player, Dienste, Playlists, Warteschlange und Klangprofile ohne Anleitung finden.

### Abnahmekriterien

- Die Kernabläufe sind bei größerer Schrift und mit Screenreader sinnvoll bedienbar.
- Beobachtete Verständnisprobleme fließen in Beschriftungen und Navigation zurück.

## Empfohlene Reihenfolge

1. **Player-Navigation und Quellenwahl** sichtbar machen.
2. **Playlist-Import, Playlists und Warteschlange** als Player-Bereiche auffindbar machen.
3. **Echten EQ-Status und lautheitsangepassten A/B-Vergleich** anbieten.
4. **Klangziele wie „Gesang vorne“** und verständliche Preset-Beschreibungen ergänzen.
5. **Verknüpfte Dienste** von **Klangprofilen** trennen.
6. Alle sichtbaren App-Texte vollständig **ins Englische lokalisieren**.
7. Geräteprofile, AutoEQ, Barrierefreiheit und die Entscheidung zur Mikrofonverarbeitung ausbauen.

## Abgrenzung

Mikrofonverarbeitung ist zunächst eine Produktentscheidung, keine Erweiterung des normalen Musik-EQ. Präzise EQ-Bearbeitung, Headroom, Limiter und Geräteprofile dürfen nur für Audiopfade als aktiv dargestellt werden, auf denen ihre Wirkung tatsächlich verfügbar ist.

