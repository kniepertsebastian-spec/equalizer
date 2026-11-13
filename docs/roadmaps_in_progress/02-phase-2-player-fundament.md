# Roadmap 02 – Player-Fundament

**Modell-Empfehlung:** Opus 5.5 für P2-1; Sonnet 5.5 für P2-2 bis P2-4  
**Ziel:** Queue, Wiedergabeintegration und Player-Service stabilisieren.  
**Abhängigkeit:** Roadmap 01 abgeschlossen. Vor Roadmap 05 abschließen.

## Phase 2 – Player-Fundament (Technik unter der Oberfläche)

### P2-1 Queue an ExoPlayer übergeben (L)
- **Befund:** ExoPlayer hält immer nur einen Titel. Die Queue liegt im Service, jeder Wechsel bedeutet Pause, Netzabruf, `setMediaItem`, `prepare`. Dadurch gibt es Lücken zwischen Titeln und kein Vorladen.
- **Lösung:** Ganze Queue als Media-Items setzen, Stream-Adresse erst beim Laden auflösen (`ResolvingDataSource`). Der Service wird ein `MediaLibraryService`: Benachrichtigung, Sperrbildschirm, Bluetooth, Android Auto und Wear kommen dann aus einer Quelle; der getrennte `AutoBrowserService` und der `ForwardingPlayer`-Umbau entfallen.
- **Fertig, wenn:** Der nächste Titel vorgeladen ist und der Wechsel ohne hörbare Lücke läuft.

### P2-2 Shuffle, Wiederholen, „Als Nächstes spielen", Queue umsortieren (M)
- Fehlt komplett; folgt nach P2-1 fast von selbst aus den ExoPlayer-Funktionen.

### P2-3 Bildlader mit Cache (S)
- **Befund:** `ArtworkImage` lädt per `URL.openConnection()` ohne Cache und nur das eine große Cover. Listen (Suche, Queue, Playlists) haben keine Cover.
- **Lösung:** Coil einbinden, Cover in allen Listenzeilen.

### P2-4 Kleinere Service-Punkte (S)
- `startForeground` in `AudioPlayerService` ohne `ServiceCompat`, Typ und `try/catch` (derselbe Fehler, der in der EQ-Service-Klasse als CRIT-01 behoben wurde).
- Fortschritts-Ticker läuft auch im Pausenzustand zweimal pro Sekunde weiter.
- Toasts aus dem Service durch Zustände in der Oberfläche ersetzen.
- Benachrichtigungs-Icon ist `android.R.drawable.ic_media_play`: eigenes Icon.
- `PlayerDspAudioProcessor.queueInput` legt pro Puffer ein neues `ShortArray` an: wiederverwenden.

---

## Abschlusskriterien
- Queue wird als Media-Items durch ExoPlayer verwaltet; Stream-Adressen werden beim Laden aufgelöst.
- Vorladen und möglichst lückenloser Titelwechsel sind auf einem realen Gerät geprüft.
- Shuffle, Wiederholen, „Als Nächstes spielen“ und Umsortieren funktionieren.
- Cover werden mit Cache in den relevanten Listen angezeigt.
- Foreground-Service, Fortschritts-Ticker, UI-Zustände, Benachrichtigungs-Icon und PCM-Pufferverarbeitung sind überprüft.
- Benachrichtigung, Sperrbildschirm, Bluetooth und Android Auto verwenden den vorgesehenen Player-Einstieg.
- Build, Unit-Tests, Lint und erforderliche Gerätetests sind dokumentiert.

## Integrationshinweis
P2-1 ist ein Architekturumbau. Vor dem Coden erst Ist-Zustand, Media3-Version, Service- und Android-Auto-Integration prüfen und einen Migrationsplan erstellen. P2-2 bis P2-4 dürfen nicht parallel dieselben Player-Dateien verändern, solange P2-1 läuft.
