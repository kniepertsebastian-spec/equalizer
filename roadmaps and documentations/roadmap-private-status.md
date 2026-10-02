# Status zur Roadmap „HardBass EQ – Roadmap“ (roadmap-private.md)

Stand: Oktober 2026. „Code“ = im Repo umgesetzt und per CI/Unit-Test abgesichert. „Gerät“ = braucht einen Test auf echten Geräten, den nur Menschen machen können.

| Punkt | Stand |
|---|---|
| 1 Pipeline-Tests (Start-Reihenfolgen, Bluetooth, Anruf, Prozess-Neustart …) | **Gerät** – Checkliste in `docs/TEST_MATRIX.md`; Code für Wiederanbindung/Retry vorhanden (`docs/STATE_MACHINE.md`) |
| 2 EQ-Status lügt nie | Code – `EqStatus`: aktiv nur bei bestätigtem Effekt (Active), sonst Warten/Verbinden/Verloren/Nicht verfügbar/Fehler/Aus |
| 3 YouTube eindeutig | Code – „Open YouTube“-Button mit Erklärung; Status folgt der erkannten Audio-Session |
| 4 Startseite vereinfacht | Code – Status, Sound-Ziel, Profil, Vergleich, Feinanpassung eingeklappt |
| 5 A/B-Vergleich | Code – Original/EQ-Chips, Lautstärke-Angleichung (Schätzung), **neu: Halten-zum-Vergleichen-Taste** |
| 6 Sound-Ziele | Code – fünf Ziele mit Beschreibung |
| 7 Player-Struktur | Code – Suche, Playlists, Queue, Mini-Player, Import |
| 8 Playlist-Import robust | Code – leere/fehlerhafte Playlists, Fortschritt, Abbrechen, Duplikate, nicht abspielbare Titel werden mit Hinweis übersprungen |
| 9 Audio-Testsystem | Code vorhanden (Sweep, Sinus, Spike-Bereich unter „Settings & diagnostics“); Auswertung auf echten Ausgängen = **Gerät** |
| 10 Headroom verständlich | Code – Klartext-Hinweise zur Lautstärkereserve |
| 11 Geräteprofile | Code – Profil je Ausgabegerät, automatisches Laden beim Wechsel |
| 12 Gerätenamen | Code – **neu:** „Rename device“ (eigener Name pro Gerät, nur Anzeige) |
| 13 Gerät/Ziel/Feintuning getrennt | Code – getrennte Bereiche |
| 14 Dienste, externe Apps | Code – ehrliche Hinweise bei fehlender Audio-Session |
| Fehlerzustände | Code – Meldungen je Zustand |
| Diagnose | Code – **neu:** hinter „Settings & diagnostics ▾“ eingeklappt; Bericht enthält jetzt EQ-Status, Audio-Session, Player, letzten Fehler (zusätzlich zu Version, Android, Gerät, Route, Capabilities, DSP-Stufen, Ereignisse); kopieren/teilen |
| Accessibility | Code – Slider mit Beschreibung samt Wert, Kontrast der Presets geprüft (≥ 4,5:1); **Gerät:** TalkBack, große Schrift, Querformat |
| Sprachen | **Abweichung:** Die App ist bewusst nur Englisch (Entscheidung vom Oktober 2026), „Deutsch vollständig“ entfällt |
| P2 (parametrisch, AutoEQ, Preset-Verwaltung, SoundCloud-Playlists) | Code vorhanden und erhalten |

Offen für Menschen: Gerätetests (Punkt 1, 9, Accessibility), Nutzertest mit Freunden (`docs/USABILITY_TEST.md`).
