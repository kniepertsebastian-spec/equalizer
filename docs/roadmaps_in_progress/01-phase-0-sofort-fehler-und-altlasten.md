# Roadmap 01 – Sofort: Fehler und Altlasten

**Modell-Empfehlung:** Sonnet 5.5  
**Ziel:** Die sechs Punkte aus Phase 0 der Gesamt-Roadmap isoliert beheben.  
**Abhängigkeit:** Keine. Diese Roadmap zuerst ausführen.

## Phase 0 – Sofort: Fehler und Altlasten (klein, hoher Nutzen)

### P0-1 Zurückgerollten 34-Band-Fix wiederherstellen (S)
- **Befund:** Die alte Roadmap führt HIGH-02 (logarithmisches Raster ohne 5-Hz-Abstände) als erledigt. Der Commit `9409917` („ci red fix", 28.10.2026) hat ihn wieder entfernt. `PreEqLayout.CUTOFFS_HZ` enthält erneut `31, 36, 40, 45, 50, 55 Hz` und `LOWEST_HZ = 15f` (`core/.../dsp/PreEqMapper.kt`).
- **Folge:** Die Bassbänder liegen enger als die FFT-Auflösung von `DynamicsProcessing` (bei 20 ms Frame rund 23 Hz). Mehrere Bänder teilen sich ein Bin, die Kurve im Bass ist dadurch unschärfer, als die Oberfläche zeigt.
- **Fertig, wenn:** Nachbarbänder mindestens rund 18 % auseinanderliegen, `PreEqMapperTest` das prüft und CI grün ist.

### P0-2 Queue läuft bei Netzausfall komplett durch (S)
- **Befund:** `onPlayerError` ruft ohne Prüfung der Fehlerart `onTrackUnplayable()` auf, das sofort den nächsten Titel startet (`AudioPlayerService.kt`). Ohne Netz schlägt jeder Titel fehl, die ganze Queue wird mit Toasts übersprungen.
- **Fertig, wenn:** Netzfehler pausieren und mit Wartezeit erneut versuchen; nur echte „Titel nicht abspielbar"-Fälle springen weiter, höchstens dreimal hintereinander.

### P0-3 Benachrichtigung öffnet den alten XML-Player (S)
- **Befund:** `setSessionActivity` und der Content-Intent der Benachrichtigung zeigen auf `PlayerActivity` (altes View-Layout aus dem früheren Audioplayer-Repo), nicht auf den Compose-Player in `MainActivity`.
- **Fertig, wenn:** Benachrichtigung, Sperrbildschirm und Android Auto den Compose-Player öffnen und `PlayerActivity`, `activity_player.xml` und `TrackAdapter` gelöscht sind.

### P0-4 Build-Ausgabe aus dem Repo entfernen (S)
- **Befund:** 6.494 Dateien unter `app/build`, `core/build`, `player/app/build` und `build/` sind eingecheckt, ebenso `local.properties` mit einem lokalen Pfad. `.gitignore` listet beides, die Dateien wurden aber vorher hinzugefügt.
- **Fertig, wenn:** `git rm -r --cached` für diese Pfade gelaufen ist und `git ls-files | grep /build/` leer bleibt.

### P0-5 Virtual Bass: wirkungslosen Drive-Regler und Filterlage korrigieren (S–M)
- **Befund 1:** `abs(x · drive) / drive` ist identisch mit `abs(x)`. Der Parameter `drive` in `BassExciter.kt` ändert rechnerisch nichts.
- **Befund 2:** Der Hochpass hinter dem Gleichrichter sitzt auf derselben Frequenz wie der Tiefpass davor (150 Hz). Die Oktave über einem 40-Hz-Grundton (80 Hz) wird damit um rund 11 dB gedämpft, während Obertöne von 100–150-Hz-Tönen fast ungedämpft in den unteren Mitten landen. Der Effekt wirkt also dort am schwächsten, wo kleine Treiber ihn brauchen.
- **Fertig, wenn:** Der Hochpass tiefer liegt (etwa 60–70 Hz), ein Tiefpass die Obertöne nach oben begrenzt (etwa 300 Hz) und ein Test den Pegel der zweiten Harmonischen für 40, 60 und 100 Hz festschreibt. Siehe auch P4-3.

### P0-6 Ungenutzten Code entfernen (S)
- `app/.../audio/media3/Media3DspPipeline.kt` samt Test (alt: DEBT-02).
- `Crossfeed.kt` ist fertig und getestet, aber nirgends angeschlossen: entweder in Phase 4 nutzen (P4-4) oder löschen.
- Veralteter Kommentar „Media3 1.3.1" in `PlayerDspPcm16.kt`; eingebunden ist 1.11.1.

---

## Abschlusskriterien
- Alle sechs Punkte einzeln geprüft und umgesetzt oder mit konkreter Begründung als nicht umsetzbar dokumentiert.
- Nach jedem Punkt passende Tests ausgeführt.
- `:app:assembleDebug`, relevante Unit-Tests und `ktlintCheck`/`lintDebug` erfolgreich.
- Git-Status enthält keine unbeabsichtigten Build-Ausgaben oder lokalen Konfigurationsdateien.
- Abschlussbericht und eigener Commit vorhanden.

## Besondere Abhängigkeit
P0-5 korrigiert zunächst die bestehende Virtual-Bass-Implementierung. Der grundlegende DSP-Neuaufbau erfolgt später in Roadmap 03 (P4-3). P0-6 darf `Crossfeed.kt` nicht voreilig löschen, solange die Entscheidung über P4-4 noch offen ist; die Entscheidung ist als offen zu dokumentieren.
