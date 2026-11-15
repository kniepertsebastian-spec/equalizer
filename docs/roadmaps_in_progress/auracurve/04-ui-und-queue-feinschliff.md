# Roadmap 04 · Optik, Lokalisierung & Feinschliff

**Enthält:** Phase 4 (Optik) & Phase 5 (Lokalisierung, Feinschliff)  
**Ziel:** Die freigegebene Optik (Entwürfe `screenshots/01` bis `04`, Review Abschnitt 5) ist umgesetzt, die App spricht Deutsch, und leere oder fehlerhafte Zustände sagen, was zu tun ist.  
**Abgleich mit dem Repository:** 15.11.2026 (Stand nach PR #77). Übersicht: [`00-uebersicht-und-leitplanken.md`](00-uebersicht-und-leitplanken.md)

> **Änderung der Reihenfolge (15.11.2026, mit deiner Zustimmung):** Teil A, der Equalizer-Umbau, wird **vorgezogen** und vor dem SoundCloud-Video (Roadmap 02, 1.5) umgesetzt. Er hängt nicht von der Antwort von SoundCloud ab, und das Video soll die Kurve als Herzstück zeigen. Teil B (Player-Bibliothek und Suche) bleibt hinter der SoundCloud-Antwort und dem Gerätetest, weil die Suche dort SoundCloud einbindet.

> **Fehlende Entwürfe:** Die Bilder `screenshots/01` bis `05` aus dem Review liegen nicht im Repository. Bis sie da sind, gilt die Textbeschreibung im Review (Abschnitt 5). Bitte die Dateien nach `docs/roadmaps_in_progress/auracurve/screenshots/` legen.

---

## Teil A · Equalizer-Umbau (vorgezogen)

| # | Aufgabe | Bezug | Aufwand | Wer | Status |
|---|---|---|---|---|---|
| 4.A1 | **Umsetzungsplan** mit Ist-Stand, Aufbau, Reitern, Zuständen, Schritten und Tests. | [`docs/EQUALIZER_REDESIGN.md`](../../EQUALIZER_REDESIGN.md); Review 5.1 | S | Umsetzung | [x] Entwurf |
| 4.A2 | **Freigabe des Plans** (Entscheidungen E1 bis E4 im Plan). | `CLAUDE.md`: erst Plan, dann Freigabe, dann Code | S | du | [ ] |
| 4.A3 | **Umsetzung in Schritten Q1 bis Q6** aus dem Plan, jeder Schritt mit Build, Tests, Lint und Bildschirmfotos aus der CI. | Plan, Abschnitt 6 | M–L | Umsetzung | [ ] |
| 4.A4 | **Gerätetest** des neuen Equalizers (kleines Telefon, Querformat, Schrift 200 %, TalkBack). | Plan, Abschnitt 8 | S | du | [ ] |

**Definition of Done (Teil A):** Kurve, Status und gewähltes Band bleiben beim Scrollen sichtbar, die übrigen Einstellungen liegen in vier Reitern, nichts ist weggefallen, und der Gerätetest ist bestanden. Danach wird das SoundCloud-Video aufgenommen.

---

## Teil B · Player: Bibliothek, Playlist-Seite, eine Suche

*Erst nach dem Gerätetest T-P1 bis T-P8 (Roadmap 03, 3.2) und nach der Antwort von SoundCloud.*

| # | Aufgabe | Bezug | Aufwand | Wer | Status |
|---|---|---|---|---|---|
| 4.B1 | **Schritte S6 bis S9** des Player-Redesigns: Bibliothek als Startseite mit fester Suche und Abschnitten, Playlist als eigene Seite. | `docs/PLAYER_REDESIGN.md`, alte Roadmap 05 (freigegeben am 13.11.2026, Bedingung E5) | L | Umsetzung | [ ] |
| 4.B2 | **Verfeinerungen aus dem Review:** Karten mit Cover-Mosaik statt Textzeilen, eine Hauptaktion je Karte (Löschen ins Menü), Import-Wege hinter einem „+“, Filter mit Anzahl. | Review 5.2 | M | Umsetzung | [ ] |
| 4.B3 | **Eine Suche für alles:** eigene Titel, Playlists, danach SoundCloud (nur in der Variante, die SoundCloud hat). | Review 5.3; Antwort von SoundCloud | M | Umsetzung | [ ] |
| 4.B4 | **Store-Variante ohne SoundCloud:** Die Bibliothek muss auch nur mit eigenen Dateien gut aussehen (leere Zustände, Import nur von Dateien). | Roadmap 02, 1.3 | S | Umsetzung | [ ] |

---

## Teil C · Lokalisierung & Feinschliff

| # | Aufgabe | Bezug | Aufwand | Wer | Status |
|---|---|---|---|---|---|
| 4.C1 | **Deutsche Übersetzung** (`res/values-de`) für `:app` und `:player`. Dazu `localeFilters` in `app/build.gradle.kts` um `de` erweitern; heute steht dort nur `en`. Slogan und Fußnote bleiben Englisch. | Review 5.5 | M | Umsetzung, du (Gegenlesen) | [ ] |
| 4.C2 | **Leere Zustände** mit den möglichen nächsten Schritten als Knöpfe statt nur einem Satz (Bibliothek, Playlists, keine Audio-Sitzung). | Review 5.5 | S | Umsetzung | [ ] |
| 4.C3 | **Titelgrafik und Bildschirmfotos** für Store und README (M.5): Bild 06 als 1024 × 500 px; Bildschirmfotos aus der Store-Variante nach Teil A. | Roadmap 01, M.5; `docs/STORE_LISTING.md` | S | Umsetzung, du | [ ] |
| 4.C4 | **Lizenzhinweise in der App** (Schrift Barlow, Bibliotheken) auf „About & diagnostics“. | Review 6 „Lizenz“; `docs/licenses/` | S | Umsetzung | [ ] |

---

**Definition of Done (Roadmap 04):** Teil A ist am Gerät abgenommen, Teil B ist nach der SoundCloud-Antwort umgesetzt und getestet, die App ist auf Deutsch und Englisch vollständig, und die Store-Grafiken stammen aus der fertigen Optik.
