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

## Zurückgestellte Punkte aus der ursprünglichen Gesamt-Roadmap

Diese Punkte wurden nicht stillschweigend gelöscht, sind aber nicht Teil der sechs gewünschten Arbeitspakete:

- **P1-1:** Sofort-Lösung ohne Code: Anmeldung per E-Mail und Passwort; vorher am Gerät prüfen, ob das E-Mail-Formular vorhanden ist.
- **P1-3:** Manuelle Token-Eingabe als Rückfallweg beibehalten; Einfügen aus Zwischenablage und Prüfung gegen `/me`.
- **P1-4:** Sackgassen nicht weiterverfolgen: Custom-Tab-Cookies auslesen, natives Google Sign-In als SoundCloud-Token-Ersatz sowie weitere User-Agent-/Header-Tricks oder eigene Browser-Engine. 
- **P4-2:** Lautheit pro Titel messen und angleichen (ITU-R BS.1770 / LUFS).
- **P4-7:** Float-Verarbeitung bis zum Ausgang prüfen.
- **P4-9:** Persönliche Abstimmung mit Hörtest und vollständiger AutoEQ-Datenbank.

Diese Restpunkte sollten nach Abschluss der sechs Pakete neu priorisiert und bei Bedarf in weitere kleine Roadmaps überführt werden.

