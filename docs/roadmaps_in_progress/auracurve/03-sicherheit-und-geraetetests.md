# Roadmap 03 · Sicherheit & Gerätetests

**Enthält:** Phase 2 (Sicherheit) & Phase 3 (Gerätetests)  
**Ziel:** Die Anmeldedaten der Privatvariante sind sicher abgelegt, die bekannten Absturz- und Missbrauchsrisiken sind behoben, und beide Varianten laufen nachweislich auf mehr als einem Gerät.  
**Wann:** Direkt nach dem Absenden der SoundCloud-Anfrage (Roadmap 02, 1.6), also während der Wartezeit auf SoundCloud. 3.1 und 3.2 können sofort beginnen.  
**Abgleich mit dem Repository:** 15.11.2026 (Stand nach PR #77). Befunde mit Datei und Zeile stammen aus [`eq-review.md`](eq-review.md), Abschnitt 3 und 4, und sind gegen den aktuellen Code geprüft. Übersicht: [`00-uebersicht-und-leitplanken.md`](00-uebersicht-und-leitplanken.md)

---

## Teil A · Phase 2: Sicherheit

*Betrifft fast nur die Privatvariante (`full`): Die Store-Variante hat keine Anmeldung, keine Tokens und kein Selbst-Update. Die Arbeit ist trotzdem nötig, weil die offizielle SoundCloud-Anbindung (alte Roadmap 04) auf derselben Token-Ablage aufbaut.*

| # | Aufgabe | Befund (geprüft 15.11.2026) | Aufwand | Wer | Status |
|---|---|---|---|---|---|
| 2.1 | **Tokens verschlüsselt ablegen:** SoundCloud- und Spotify-Token (inkl. Erneuerungs-Token) mit einem Schlüssel aus dem Android-Keystore verschlüsseln. Beim ersten Start die vorhandenen Klartext-Werte übernehmen und löschen. | `sc_auth_prefs` und `spotify_auth` liegen im Klartext (`SoundCloudLoginActivity`, `SpotifyApiClient`, beide jetzt in `player/app/src/full`). | M | Umsetzung | [ ] |
| 2.2 | **Tokens aus dem Backup nehmen:** `sc_auth_prefs.xml` und `spotify_auth.xml` in `backup_rules.xml` und `data_extraction_rules.xml` ausschließen. Nach einer Wiederherstellung meldet man sich neu an. | Beide Regeldateien schließen nichts aus; die Tokens wandern bewusst mit (Kommentar in `data_extraction_rules.xml`). | S | Umsetzung | [ ] |
| 2.3 | **Abmelden löscht alles:** Bei SoundCloud auch die Cookies der Browseransicht löschen, bei Spotify den Erneuerungs-Token. | Review 3: „Abmelden“ löscht die Cookies der WebView nicht. | S | Umsetzung | [ ] |
| 2.4 | **Absturz beim Start des Players aus dem Hintergrund abfangen:** `AndroidPlayerController.send` ruft `context.startService` ungeschützt auf; aus dem Hintergrund lehnt Android das ab (`IllegalStateException`). Absichern und den Befehl verwerfen oder nachholen, mit Test. | `app/src/main/.../integration/PlayerController.kt:183` | S | Umsetzung | [ ] |
| 2.5 | **Exportierte Komponenten prüfen:** Jede nach außen offene Komponente darauf prüfen, ob fremde Apps sie missbrauchen können (falsche Intents, fremde Befehle an den Player, Spotify-Rücksprung). | `exported="true"`: `MainActivity`, zwei Schnellkacheln, Widget, `AudioPlayerService`, `SpotifyLoginActivity` (nur `full`). | M | Umsetzung | [ ] |
| 2.6 | **Release-Schlüssel sichern:** Keystore-Datei und Passwort außerhalb von GitHub an zwei Orten ablegen. Für den Play Store einen eigenen Upload-Schlüssel anlegen (Play App Signing), nicht den GitHub-Release-Schlüssel wiederverwenden. | `docs/UPDATES.md`; Secrets `HARDBASS_RELEASE_*` in GitHub | S | du | [ ] |

**Definition of Done (Phase 2):** Kein Token liegt im Klartext oder im Backup, Abmelden hinterlässt nichts, kein bekannter Absturz beim Start aus dem Hintergrund, die exportierten Komponenten sind geprüft und dokumentiert, der Schlüssel ist doppelt gesichert.

---

## Teil B · Phase 3: Gerätetests

| # | Aufgabe | Bezug | Aufwand | Wer | Status |
|---|---|---|---|---|---|
| 3.1 | **Beide Varianten am Telefon:** Store-Variante (nur „Your music“, kein Update-Hinweis, kein „Teilen mit“ für Links, About-Block) und Privatvariante (alles wie bisher, Link-Teilen, Selbst-Update aus einem Release). | Roadmap 02, 1.3; Roadmap 01, M.2/M.6 | S | du | [ ] |
| 3.2 | **Player-Gerätetests T-P1 bis T-P8** aus `docs/TEST_MATRIX.md` (Mini-Player, „Now playing“, Queue, TalkBack, Flugmodus, Benachrichtigung, Querformat, Sound-Sheet). Gibt die Schritte S6 bis S9 des Player-Redesigns frei. | Alte Roadmap 05, Bedingung E5 | M | du | [ ] |
| 3.3 | **Hardware-Matrix:** mindestens ein Samsung, ein Xiaomi und ein Gerät mit Android 9 bis 11 zusätzlich zum Pixel. Je Gerät: Equalizer an einer fremden Musik-App, Diagnose-Seite (`Equalizer`/`DynamicsProcessing` vorhanden?), Bluetooth- und Kabel-Ausgang, Profilwechsel, eigener Player mit Bildschirm aus. | Review 6 „Gerätetests“; `docs/REQUIREMENTS.md` | M–L | du | [ ] |
| 3.4 | **Messen statt schätzen:** Prozessorlast und Speicher auf einem günstigen und einem mittleren Gerät messen und die geschätzten Werte in `docs/REQUIREMENTS.md` ersetzen. | Review 8 | S | du, Umsetzung | [ ] |
| 3.5 | **Queue-Reste:** Beim Ziehen an den Rand automatisch weiterscrollen; entscheiden, ob die Queue bei Zufallswiedergabe die gespielte oder die gespeicherte Reihenfolge zeigt. | Review 4 „Was offen bleibt“ | S–M | du (Entscheidung), Umsetzung | [ ] |

**Definition of Done (Phase 3):** Beide Varianten sind auf mindestens drei Geräten unterschiedlicher Hersteller geprüft, die Ergebnisse stehen in `docs/TEST_MATRIX.md`, und T-P1 bis T-P8 sind abgehakt.

---

## Abhängigkeiten

- 3.1 kommt vor dem SoundCloud-Video (Roadmap 02, 1.5), damit die gezeigte Store-Variante geprüft ist.
- 3.2 ist Voraussetzung für den Player-Teil von Roadmap 04 (S6–S9), nicht für den Equalizer-Umbau (Roadmap 04, Teil A).
- 2.1 bis 2.3 sind Voraussetzung für die offizielle SoundCloud-Anbindung (alte Roadmap 04), sobald SoundCloud zustimmt.
