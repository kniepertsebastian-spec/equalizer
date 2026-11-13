# Roadmap 03 – Audioqualität: Stream-Auswahl und Virtual Bass

**Modell-Empfehlung:** Sonnet 5.5 für P4-1; Opus 5.5 für P4-3  
**Ziel:** Die beiden ausdrücklich ausgewählten Audioqualitäts-Punkte bearbeiten.  
**Abhängigkeit:** Roadmap 01 abgeschlossen, insbesondere P0-5.

## P4-1 Bessere Quelle wählen: AAC statt MP3 128 (S–M) — größter Einzelhebel

### P4-1 Bessere Quelle wählen: AAC statt MP3 128 (S–M) — größter Einzelhebel
- **Befund:** `StreamSelection` bevorzugt „progressive" vor HLS und wertet weder Codec noch Qualitätsstufe aus. Progressive ist bei SoundCloud MP3 mit 128 kbit/s; über HLS gibt es AAC 160 und mit Go+ AAC 256. Kein Equalizer holt zurück, was der Codec entfernt hat, und ein Bass-Boost macht MP3-Artefakte im Hochton eher hörbar.
- **Zusätzliches Risiko:** SoundCloud hat angekündigt, die MP3-Progressive-Streams zugunsten von AAC-HLS abzuschalten. Aktuellen Stand prüfen.
- **Fertig, wenn:** Die Auswahl nach Qualität sortiert (AAC 256, AAC 160, MP3 128) und HLS am Gerät geprüft ist.

## P4-3 Virtual Bass neu aufbauen (M)

Nach P0-5: Kennlinie mit einstellbarem Verhältnis von zweiter und dritter Harmonischer statt reiner Gleichrichtung, Bandpass für die Obertöne, Hüllkurvenfolger, damit der Effekt bei leisen Stellen nicht überproportional wird.

## Verbindliche Prüfungen
- Für P4-1 Codec- und Qualitätsauswahl anhand tatsächlich verfügbarer Stream-Varianten prüfen; HLS am Gerät testen. Keine Annahme über die aktuelle SoundCloud-Streamverfügbarkeit ungeprüft übernehmen.
- Für P4-3 mathematische Tests für Frequenzgang, Pegel, zweite und dritte Harmonische sowie Verhalten bei unterschiedlichen Eingangspegeln ergänzen.
- Hörprobe mit definierten Testsignalen und realem Audiomaterial durchführen und dokumentieren. Unit-Tests allein belegen keine Klangqualität.
- Build, relevante Tests und Lint ausführen.

## Umfangsgrenze
Diese Roadmap enthält nur P4-1 und P4-3. Die übrigen Punkte der ursprünglichen Phase 4 (P4-2 und P4-4 bis P4-9) sind bewusst zurückgestellt und in `00-INDEX.md` aufgeführt.
