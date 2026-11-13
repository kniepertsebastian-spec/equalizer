# HardBass EQ – Roadmap

## Ziel

HardBass EQ soll für eine kleine private Nutzergruppe (Entwickler + Freunde) zuverlässig,
verständlich und angenehm funktionieren.

Der Fokus liegt deshalb nicht auf öffentlicher Produktisierung, Accounts, Community oder
Cloud-Funktionen, sondern auf:

1. zuverlässiger Audio-/EQ-Funktionalität
2. transparenter Anzeige des tatsächlichen EQ-Status
3. einfacher Bedienung
4. stabiler Player-, Playlist- und Profilfunktionalität
5. komfortabler Nutzung ohne unnötige Einschränkungen

Bestehende sinnvolle Funktionen bleiben grundsätzlich erhalten. Neue Funktionen werden erst
nach ausreichender Stabilität priorisiert.

---

# P0 – Funktionalität zuverlässig machen

## 1. Audio- und EQ-Pipeline vollständig testen

- [ ] HardBass starten → Musik-App starten
- [ ] Musik-App starten → HardBass starten
- [ ] Pause → Trackwechsel → Weiter
- [ ] Bluetooth verbinden/trennen
- [ ] Kopfhörer wechseln
- [ ] Bluetooth-Gerät wechseln
- [ ] Musik-App schließen und neu starten
- [ ] HardBass im Hintergrund laufen lassen
- [ ] HardBass-Prozess beenden → Wiederherstellung
- [ ] Display sperren/entsperren
- [ ] Lautstärke ändern
- [ ] Telefonanruf/Audio-Fokus → Musik fortsetzen
- [ ] Bluetooth-Verbindung während laufender Wiedergabe wechseln
- [ ] EQ während Wiedergabe an/aus schalten

**Akzeptanzkriterium:** Die App zeigt nach diesen Aktionen immer korrekt an, ob der EQ
tatsächlich aktiv ist.

---

## 2. EQ-Status darf niemals lügen

Die wichtigste Information der App muss der reale Zustand sein.

### Aktiver Zustand

> 🟢 EQ aktiv  
> SoundCloud · Bluetooth-Kopfhörer · Gesang vorne

### Wartender Zustand

> 🟡 Warte auf Audiowiedergabe  
> Starte Musik in deiner Musik-App.

### Nicht verfügbar

> 🔴 EQ nicht verfügbar  
> Diese App stellt keine kompatible Audio-Session bereit.

Der Status darf nicht einfach „aktiv“ anzeigen, wenn der Audioeffekt tatsächlich nicht
auf dem Audiopfad angewendet wird.

---

## 3. YouTube-Verhalten eindeutig machen

YouTube darf nicht so wirken, als würde HardBass YouTube intern abspielen, wenn tatsächlich
die externe YouTube-App bzw. ein externer Player geöffnet wird.

- [ ] Button eindeutig „YouTube öffnen“ nennen
- [ ] Verhalten kurz erklären
- [ ] Nach dem Start erkennen, ob eine kompatible Audio-Session existiert
- [ ] EQ-Status entsprechend aktualisieren
- [ ] Bei fehlender Unterstützung verständlich informieren

Das gleiche Prinzip gilt für andere externe Musik-Apps.

---

# P0 – Benutzerfreundlichkeit

## 4. EQ-Startseite vereinfachen

Die wichtigsten Informationen sollen sofort sichtbar sein.

### Empfohlene Struktur

**HardBass EQ**

> 🟢 EQ aktiv  
> Bluetooth · Meine Kopfhörer

### Sound-Ziel

- 🎵 Ausgewogen
- 🎤 Gesang vorne
- 🔊 Mehr Druck
- 🥁 Mehr Bass
- ✨ Weniger scharf

### Aktuelles Profil

> Meine Kopfhörer – Gesang vorne

### Vergleich

> **Original | EQ**

### Erweiterte Einstellungen

> **Feinanpassung ▾**

Technische EQ-Parameter bleiben vollständig erhalten, sind aber zunächst eingeklappt.

---

## 5. A/B-Vergleich prominent machen

Der Original/EQ-Vergleich ist eine der wichtigsten Funktionen des EQs.

- [ ] Original und EQ direkt erreichbar
- [ ] Lautstärke möglichst angleichen
- [ ] Zustand eindeutig anzeigen
- [ ] Keine anderen Einstellungen beim Vergleich verändern
- [ ] Optional Press-and-hold für schnellen Vergleich

Ziel: Ein Freund soll innerhalb weniger Sekunden hören können, was HardBass verändert.

---

## 6. Sound-Ziele als Hauptbedienung

Sound-Ziele sollen verständlich beschrieben werden.

### Beispiel

**Gesang vorne**  
Stimmen werden klarer und stärker hervorgehoben.

**Mehr Druck**  
Mehr Punch bei Kick und Bass, ohne einfach nur den Bass stark anzuheben.

**Weniger scharf**  
Reduziert unangenehme Härte in Stimmen und Höhen.

Technische Parameter bleiben unter der Feinanpassung verfügbar.

---

# P0 – Player und Playlists

## 7. Player-Struktur

Playlist Import bleibt ausdrücklich erhalten.

- [ ] Suche
- [ ] Playlists
- [ ] Queue
- [ ] Mini-Player
- [ ] aktueller Titel
- [ ] Play/Pause
- [ ] nächster/vorheriger Titel
- [ ] Playlist importieren
- [ ] importierte Playlists anzeigen
- [ ] Queue bearbeiten

Ziel:

> Musik starten → EQ läuft → fertig.

---

## 8. Playlist Import robust machen

- [ ] Leere Playlist korrekt behandeln
- [ ] Verständliche Fehlermeldungen
- [ ] Doppelte Titel sinnvoll behandeln
- [ ] Ungültige/gelöschte Tracks erkennen
- [ ] Importfortschritt anzeigen
- [ ] Import abbrechen können
- [ ] Leere Zustände verständlich darstellen

---

# P0 – Technische Qualität

## 9. Audio-Testsystem verwenden

Die vorhandenen Testmöglichkeiten sollen aktiv zur Validierung genutzt werden.

Testfälle:

- Sweep
- Sinustöne
- Musikreferenzen
- EQ aus/an
- verschiedene Presets
- verschiedene Audioausgänge

Prüfen:

- [ ] Clipping
- [ ] korrekte Filter
- [ ] korrekte Kanalzuordnung
- [ ] EQ wird tatsächlich angewendet
- [ ] Einstellungen verändern das Audiosignal wie erwartet
- [ ] keine unerwarteten Pegelsprünge

---

## 10. Headroom verständlich darstellen

Technische Funktion behalten, aber verständlich erklären.

Beispiel:

> **Automatische Lautstärkereserve**  
> HardBass reduziert den Pegel um 4 dB, damit die zusätzliche Verstärkung nicht
> zu Übersteuerung führt.

Technische Details können aufklappbar sein.

---

# P1 – Profile und Geräte

## 11. Geräteprofile

Beibehalten und stabilisieren.

Beispiele:

- Meine Kopfhörer → Gesang vorne
- Auto → Mehr Druck
- Bluetooth-Lautsprecher → Ausgewogen

Beim Wechsel des Geräts soll möglichst automatisch das passende Profil geladen werden.

---

## 12. Gerätenamen ermöglichen

Optional:

> Sony WH-1000XM5

kann umbenannt werden zu:

> Meine Kopfhörer

Das erleichtert die Nutzung bei mehreren ähnlichen Geräten.

---

## 13. Gerät, Sound-Ziel und Feintuning klar trennen

Die drei Ebenen müssen eindeutig unterscheidbar sein:

**Gerät**  
> Meine Kopfhörer

**Sound-Ziel**  
> Gesang vorne

**Feintuning**  
> +2 dB / -1 dB / …

Dadurch wird verhindert, dass Nutzer Profil, Gerät und EQ-Einstellungen miteinander
verwechseln.

---

# P1 – Musikdienste

## 14. Spotify, SoundCloud, YouTube und andere Player

Die bestehenden Dienste bleiben erhalten.

Die App muss jedoch klar zwischen zwei Situationen unterscheiden:

### HardBass spielt selbst

HardBass kontrolliert den Player bzw. die Wiedergabe direkt.

### Externe Musik-App

HardBass wartet auf eine kompatible Audio-Session.

Beispiel:

> Starte Musik in Spotify, YouTube Music oder einer anderen App.  
> HardBass versucht anschließend, den Audiostream zu bearbeiten.

Wenn die App keinen kompatiblen Audiostream bereitstellt:

> Diese Musik-App stellt aktuell keine kompatible Audio-Session bereit.

Keine scheinbare Funktionalität vortäuschen.

---

# P1 – Fehlerzustände

Für wichtige Zustände verständliche Meldungen bereitstellen.

### Keine Musik

> 🎵 Keine Wiedergabe erkannt  
> Starte einen Titel in deiner Musik-App.

### EQ nicht verfügbar

> ⚠️ EQ konnte nicht mit dem Audiostream verbunden werden.

### Bluetooth gewechselt

> 🎧 Neues Audiogerät erkannt  
> Profil wird geladen …

### Nicht kompatible App

> Diese Musik-App stellt aktuell keine kompatible Audio-Session bereit.

---

# P1 – Diagnose

Eine Diagnosefunktion bleibt erhalten, aber nicht prominent auf der Startseite.

Pfad beispielsweise:

> Einstellungen → Diagnose

Anzeigen bzw. exportieren:

- App-Version
- Android-Version
- Gerät
- Audiogerät
- aktuelle Audio-Session
- verwendeter Player
- EQ-Status
- Audio-Capabilities
- letzter Fehler
- Audio Route
- Effektstatus

### Diagnose kopieren

Ein kompletter Diagnosebericht soll kopiert/geteilt werden können.

Ziel:

> Wenn bei einem Freund etwas nicht funktioniert, kann er den Diagnosebericht schicken,
> ohne technische Informationen manuell zusammensuchen zu müssen.

---

# P1 – Accessibility und Komfort

Auch für eine kleine private Nutzergruppe sinnvoll:

- [ ] ausreichend große Touch-Flächen
- [ ] große Schrift unterstützen
- [ ] ausreichender Kontrast
- [ ] TalkBack testen
- [ ] Querformat prüfen
- [ ] Deutsch vollständig
- [ ] Englisch vollständig
- [ ] Informationen nicht ausschließlich über Farbe vermitteln
- [ ] Slider mit verständlichen Werten versehen

---

# P2 – Erweiterte EQ-Funktionen

Diese Funktionen bleiben erhalten, werden aber erst nach Stabilisierung priorisiert.

- [ ] Parametrische EQ-Bänder
- [ ] Grafische EQ-Kurve
- [ ] Q-Faktor
- [ ] Frequenz
- [ ] Gain
- [ ] Limiter
- [ ] Multiband-Kompressor
- [ ] Subsonic
- [ ] Virtual Bass
- [ ] Headphone Power
- [ ] weitere DSP-Optionen

Standardmäßig:

> **Feinanpassung ▾**

---

# P2 – AutoEQ

Nach stabiler Audio-Pipeline:

- [ ] Kopfhörermodell auswählen
- [ ] AutoEQ-Kurve anzeigen
- [ ] Vorschau
- [ ] Übernehmen
- [ ] anschließend manuell verändern

---

# P2 – Preset- und Profilverwaltung

- [ ] Profile duplizieren
- [ ] Profile umbenennen
- [ ] Profile zurücksetzen
- [ ] Profile exportieren/importieren
- [ ] Default-Profil
- [ ] gerätespezifische Profile

---

# P2 – SoundCloud-Playlist-Funktionen

Bestehende SoundCloud-Playlist-Funktionen behalten und weiter stabilisieren.

- [ ] Playlist-Übernahme
- [ ] Queue-Verhalten
- [ ] Fehler bei nicht mehr verfügbaren Titeln
- [ ] Import-/Synchronisationsstatus

---

# Bewusst keine Priorität

Diese Funktionen werden nicht benötigt, um HardBass für die private Nutzergruppe fertig
zu bekommen:

- Cloud-Synchronisation
- Community
- Social Features
- öffentliche Preset-Plattform
- Benutzerkonten
- KI-Presets
- öffentliche Sharing-Funktionen
- weitere Produkt-/Store-Funktionen

Sie müssen nicht zwingend aus dem Code entfernt werden, sollen aber keine Entwicklungszeit
blockieren.

---

# Definition of Done

HardBass EQ gilt für die private Nutzung als funktional fertig, wenn diese Fragen mit
„Ja“ beantwortet werden können:

1. Starte ich Musik und weiß sofort, ob HardBass sie tatsächlich bearbeitet?
2. Kann ich zuverlässig zwischen Original und EQ vergleichen?
3. Bleibt der EQ nach Pause, Trackwechsel und Bluetooth-Wechsel korrekt?
4. Funktionieren Sound-Ziel, Profil und manuelle Einstellungen tatsächlich im Audiopfad?
5. Erklärt die App verständlich, wenn eine externe Musik-App nicht unterstützt wird?
6. Kann ein Freund bei einem Fehler einen Diagnosebericht erstellen und weitergeben?

---

# Entwicklungspriorität

## Phase 1 – Funktion beweisen
**Ca. 50 % der Arbeit**

Audio-Session, EQ-Aktivierung, Bluetooth, Player, YouTube, Spotify, Routing,
Prozess-Neustart, Clipping, Presets und reale Geräte testen.

## Phase 2 – Bedienung vereinfachen
**Ca. 30 % der Arbeit**

Status, Sound-Ziele, A/B, Player, Profile und Fehlerzustände.

## Phase 3 – Komfort
**Ca. 20 % der Arbeit**

AutoEQ, erweiterte Profile und weitere Komfortfunktionen.

---

# Grundsatz

> Nicht möglichst viele neue Funktionen hinzufügen.
>
> Zuerst beweisen, dass die vorhandenen Funktionen zuverlässig funktionieren,
> und anschließend die Bedienung so einfach machen, dass man den EQ benutzen kann,
> ohne die technische Funktionsweise verstehen zu müssen.
