# Roadmap 01 · Marke umsetzen & Pull Request #74 abschließen

**Enthält:** Phase M (Marke) & Phase 0 (PR #74)  
**Priorität:** Sofort starten (Basis für alle weiteren Schritte)  
**Abgleich mit dem Repository:** 15.11.2026 (Stand `c76dc8b`). Übersicht und Leitplanken: [`00-uebersicht-und-leitplanken.md`](00-uebersicht-und-leitplanken.md)

---

## Teil A · Phase 0: Pull Request #74 abschließen

*Muss vor dem Rebranding abgeschlossen sein, damit Umbenennungen nicht mit dem offenen PR kollidieren.*

| # | Aufgabe | Aufwand | Wer | Status |
|---|---|---|---|---|
| 0.1 | Queue am Telefon prüfen: Titel wegwischen (der nächste bleibt an seinem Platz), zweimal schnell hintereinander entfernen, Titel über mehrere Plätze ziehen (auch den obersten sichtbaren), „Kommende leeren“ bei langer Queue. | S | du | [x] |
| 0.2 | Pull Request zusammenführen, wenn 0.1 passt. Sonst die Abweichung beschreiben; Behebung erfolgt im selben PR. | S | du | [~] |

> **Abgleich:** PR #74 ist laut Git-Historie bereits gemerged (`c76dc8b`, 15.11.2026), bevor 0.1 am Gerät bestätigt wurde. 0.2 ist damit formal erledigt, **0.1 bleibt offen**. Zeigt der Gerätetest eine Abweichung, wird sie in einem eigenen Folge-PR behoben, nicht mehr im selben PR.

**Definition of Done (Phase 0):** Die Queue läuft am Gerät fehlerfrei (0.1) und PR #74 ist gemerged (0.2, erfüllt). Das Rebranding (M.2 bis M.4) startet erst, wenn 0.1 bestätigt ist.

---

## Teil B · Phase M: Marke „AuraCurve“ umsetzen

*Reihenfolge: M.1 sofort. M.2 bis M.4 direkt nach Abschluss von Phase 0.*

| # | Aufgabe | Aufwand | Wer | Status |
|---|---|---|---|---|
| M.1 | **Namensprüfung (vor jeder Änderung!):** Gleichnamige Apps im Play Store, Marken beim DPMA und EUIPO (Klassen 9 und 42), freie Domain sowie freie Handles in sozialen Netzwerken prüfen. | S | du | [x] |
| M.2 | Namen in der App setzen (`app_name` steht in `app/src/main/res/values/strings.xml:3`, derzeit „Baschtls Gedröhne“; `rootProject.name` in `settings.gradle.kts` lautet `HardBassEQ`): `app_name`, Benachrichtigungen, Info-Bildschirm mit Slogan (*„Feel the bass. Rule the sound.“*) und Fußnote (*„Music player, bass enhancement, advanced EQ.“*). README, `docs/` und `CLAUDE.md` anpassen. | S | Umsetzung | [ ] |
| M.3 | **Kennungen umstellen:** Anwendungs-ID `com.hardbasseq.eq` und Paketnamen aller Module auf einen eigenen Namen ändern (z. B. `com.auracurve.app`). *Wird zusammen mit Aufgabe 1.2 erledigt.* **Achtung:** Für Android entsteht dadurch eine neue App (kein Update für Bestandsinstallationen, Presets/Playlists wandern nicht automatisch mit). Nach dem ersten Play-Store-Upload nicht mehr änderbar! | M | Umsetzung | [x] |

> **Codebefund zu M.3:** `applicationId` und `namespace` stehen zentral in `gradle.properties` (`hardbasseq.applicationId`, `hardbasseq.namespace`, beide `com.hardbasseq.eq`). Das Verzeichnis `app/src/**/java/com/hardbasseq/eq/` (inkl. aller Tests) und die Modul-Pakete in `core/` müssen mit umziehen. Das Modul `player/app` nutzt davon abweichend `com.soundcloud.equalizer.player`. `.github/workflows/ci.yml` und `release.yml` enthalten ebenfalls die alten Namen.

> **Stand 15.11.2026:** 0.1 und M.1 laut deiner Rückmeldung erledigt. M.3 ist zusammen mit Roadmap 02, Aufgabe 1.2, umgesetzt: Anwendungs-ID und alle Pakete heißen `com.auracurve.app`, das Player-Modul `com.auracurve.app.player`. Einzelheiten und bewusst ausgelassene Kennungen (URI-Schema `hardbasseq://`, Gradle-Schlüssel) in [`02-soundcloud-vorbereitung.md`](02-soundcloud-vorbereitung.md), Abschnitt 1.2. Die CI hat den Bau und die Tests von `:app` und `:player` bestätigt (PR #76, `3e553d2`).
| M.4 | Repository umbenennen und die Adresse prüfen, unter der das Selbst-Update nach neuen Versionen sucht. | S | du, Umsetzung | [ ] |

> **Codebefund zu M.4:** Der Repo-Name ist fest einkodiert in `ReleaseUpdateClient.kt:21` (`RELEASES_URL`) und `ReleaseCatalog.kt:62` (Prüfung `startsWith(".../equalizer/releases/download/")`). GitHub leitet die API weiter, aber die Download-Prüfung in `ReleaseCatalog` akzeptiert nur das alte Präfix. Beide Stellen und `ReleaseCatalogTest` zusammen mit der Umbenennung anpassen, sonst findet das Selbst-Update nach der Umbenennung keine gültigen Downloads mehr.
| M.5 | **Banner exportieren:** Bild 06 als Play-Store-Titelgrafik im Format **1024 × 500 px ohne Rahmen** exportieren. Überschrift: *„Bass you can see and steer.“*, darunter Slogan und Fußnote. Der Slogan *„Feel the bass. Rule the sound.“* steht überall sonst (Info-Screen, Store-Texte, SoundCloud-Anschreiben). | S | Umsetzung | [ ] |
| M.6 | Store-Texte aus der Marke ableiten: Titel max. 30 Zeichen (z. B. *„AuraCurve: Bass EQ & Player“* = 27 Zeichen), Kurzbeschreibung max. 80 Zeichen (die Fußnote hat 44 Zeichen und passt unverändert). | S | du, Umsetzung | [ ] |

**Reihenfolge-Hinweis:** M.1 ist die einzige Aufgabe, die vor jeder Codeänderung entschieden sein muss und nur von dir erledigt werden kann. Solange M.1 offen ist, bleibt M.2 bis M.6 unangetastet. `CLAUDE.md` nennt das Projekt noch „HardBass EQ“; die Anpassung gehört zu M.2.

**Definition of Done (Phase M):** Der Name ist rechtlich/verfügbarkeitsseitig geprüft und in App, Repository und Dokumenten steht durchgängig nur noch „AuraCurve“.
