# HardBass EQ – Arbeits-Roadmaps

**Arbeitsregeln**
- Jede Roadmap in einem eigenen Kontextfenster und möglichst eigenem Git-Worktree bearbeiten.
- Vor Änderungen den aktuellen Codezustand prüfen; die ursprüngliche Roadmap beruht auf einer statischen Review ohne Build, Gerätetest oder Hörprobe.
- Nur den zugewiesenen Umfang bearbeiten. Abweichungen und Änderungen an gemeinsam genutzten Dateien vorher dokumentieren.
- Nach jedem abgeschlossenen Arbeitspunkt passende Tests, Build und Lint ausführen.
- Nichts als erledigt markieren, solange die jeweiligen Abnahmekriterien nicht nachgewiesen sind.
- Am Ende geänderte Dateien, Entscheidungen, Tests, offene Punkte und Commit-ID dokumentieren.

**Wichtige Abhängigkeiten**
- Roadmap 01 sollte vor den anderen Implementierungsarbeiten abgeschlossen werden.
- Roadmap 02 (Player-Fundament) sollte vor der tatsächlichen Umsetzung von Roadmap 05 (Player-Redesign) abgeschlossen sein.
- Roadmap 03 baut für Virtual Bass auf der Korrektur P0-5 aus Roadmap 01 auf.
- Roadmap 04 darf erst nach schriftlicher Klärung mit SoundCloud begonnen werden. Nutzer muss dafür das GO geben
- Roadmap 05 ist zunächst ausschließlich eine Planungsaufgabe. Implementierung erst nach ausdrücklicher Freigabe.
- Nicht in diesen sechs Paketen enthaltene Punkte aus der ursprünglichen Phase 4 sowie P1-1/P1-3 sind am Ende in `00-INDEX.md` als zurückgestellter Restumfang aufgeführt.

---
1. [01-phase-0-sofort-fehler-und-altlasten.md](01-phase-0-sofort-fehler-und-altlasten.md)
2. [02-phase-2-player-fundament.md](02-phase-2-player-fundament.md)
3. [03-phase-4-audioqualitaet-ausgewaehlte-punkte.md](03-phase-4-audioqualitaet-ausgewaehlte-punkte.md)
4. [04-phase-1-2-offizielle-soundcloud-api.md](04-phase-1-2-offizielle-soundcloud-api.md)
5. [05-phase-3-player-redesign-plan-und-freigabe.md](05-phase-3-player-redesign-plan-und-freigabe.md)
6. [06-phase-5-und-6-neue-funktionen-wartbarkeit.md](06-phase-5-und-6-neue-funktionen-wartbarkeit.md)
7. [07-laermabhaengiger-eq-messluecken.md](07-laermabhaengiger-eq-messluecken.md) – Lärm-EQ (PoC), gehört zu EQ-remote Modul 11 F12
8. [08-player-lautheit-und-eq-paritaet.md](08-player-lautheit-und-eq-paritaet.md) – Player-Lautheit, manuelle Slider und A/B-Vergleich im eigenen Player
9. [09-player-float-pipeline-und-punch.md](09-player-float-pipeline-und-punch.md) – 32-Bit-Float-Kette, Soft-Clipper, Limiter-Abstimmung (umgesetzt, Gerätetest offen)
10. [10-player-highres-eq-und-exciter-feinschliff.md](10-player-highres-eq-und-exciter-feinschliff.md) – Terzband-EQ im Player, exakte parametrische Filter, Virtual-Bass-Gate, SoundCloud-AAC-HLS (umgesetzt, Gerätetest offen)
11. [11-player-stabilitaet-und-lifecycle.md](11-player-stabilitaet-und-lifecycle.md) – Android-12+-sichere Player-Steuerung, begrenzter Stream-Lookup, kein Disk-I/O im Service-Start, `PlayerDspStore`
12. [12-release-r8-und-sicherheits-audit.md](12-release-r8-und-sicherheits-audit.md) – R8/Resource-Shrinking, Network-Security-Config, Gradle-/Git-Hygiene, Sicherheits-/a11y-Nach-Audit (umgesetzt, Gerätetest offen)
13. [13-system-eq-bassqualitaet-mbc-posteq.md](13-system-eq-bassqualitaet-mbc-posteq.md) – 5-Band-MBC, PreEQ/PostEQ-Split, Pre-Ringing-Schutz (umgesetzt, Gerätetest/Hörprobe offen)
14. [14-neue-elektronische-genres.md](14-neue-elektronische-genres.md) – Fünf neue elektronische Genres (Hardtekk, Techno, Hard Techno, Industrial Techno, Schranz) inkl. Character-Varianten & Design-Zuordnung (umgesetzt, Hörprobe offen)
15. [15-player-presets-und-use-cases.md](15-player-presets-und-use-cases.md) – `PresetSourceScope`, boxensichere Player-Presets für alle Genres sowie Player-Use-Case-Äquivalente (Loud, Quiet, In-Ear, Car, BT-Speaker) (umgesetzt, Hörprobe offen)
16. [16-quellenerkennung-und-preset-ui.md](16-quellenerkennung-und-preset-ui.md) – `ActivePlaybackSource` in `EqController`, Session-Fix, automatischer Quellen-Switch und Ausgrauen nicht passender Presets in der UI (umgesetzt, Gerätetest offen)
17. [17-playlist-detailansicht-und-scrollen.md](17-playlist-detailansicht-und-scrollen.md) – Playlist-Detailansicht (lokal, SoundCloud-Playlisten, Likes) mit eigener `LazyColumn`, Öffnen ohne Queue-Abbruch, System-Back (umgesetzt, Gerätetest offen)
18. [18-player-knall-entschaerfen.md](18-player-knall-entschaerfen.md) – Weniger „Knall“ im eigenen Player: Kick/Punch der Player-Presets moderat gesenkt, „Mehr Intensität“ im Player halbiert, Laut-Extra-Kick abschaltbar (Standard an); milde Lautheitsangleichung zur Freigabe (umgesetzt bis auf P18-4, Hörprobe offen)

Gerätetests der Roadmaps 08–13 gesammelt in einer Sitzung: [geraetetest-sitzung-rm08-13.md](geraetetest-sitzung-rm08-13.md).

Zusätzlich: Roadmap-Reihe **AuraCurve** (Rebranding, SoundCloud-Anfrage, Store-Release) in [`auracurve/`](auracurve/00-uebersicht-und-leitplanken.md).

## Zurückgestellte Punkte aus der ursprünglichen Gesamt-Roadmap

Diese Punkte wurden nicht stillschweigend gelöscht, sind aber nicht Teil der sechs gewünschten Arbeitspakete:

- **P1-1:** Sofort-Lösung ohne Code: Anmeldung per E-Mail und Passwort; vorher am Gerät prüfen, ob das E-Mail-Formular vorhanden ist.
- **P1-3:** Manuelle Token-Eingabe als Rückfallweg beibehalten; Einfügen aus Zwischenablage und Prüfung gegen `/me`.
- **P1-4:** Sackgassen nicht weiterverfolgen: Custom-Tab-Cookies auslesen, natives Google Sign-In als SoundCloud-Token-Ersatz sowie weitere User-Agent-/Header-Tricks oder eigene Browser-Engine. 
- **P4-2:** Lautheit pro Titel messen und angleichen (ITU-R BS.1770 / LUFS).
- **P4-7:** Float-Verarbeitung bis zum Ausgang prüfen. → in Roadmap 09 (P9-1) umgesetzt, Gerätetest offen.
- **P4-9:** Persönliche Abstimmung mit Hörtest und vollständiger AutoEQ-Datenbank.

Diese Restpunkte sollten nach Abschluss der sechs Pakete neu priorisiert und bei Bedarf in weitere kleine Roadmaps überführt werden.

