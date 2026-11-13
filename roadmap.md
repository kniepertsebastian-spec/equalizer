# HardBass EQ – Roadmap

Stand: 13. November 2026. Grundlage: Code-Review des gesamten Repos (`:app`, `:core`, `:player`).
Die Review war rein statisch: kein Build, kein Gerätetest, keine Hörprobe.
Diese Datei ersetzt die Roadmap vom Oktober 2026; deren offene Punkte sind unten übernommen (Abschnitt 8).

**Gesamtnote der Review: 6,5 / 10.** Der DSP-Kern und die Effekt-Engine sind stark, der Player und die SoundCloud-Anbindung ziehen die Note nach unten.

| Bereich | Note |
|---|---|
| DSP-Kern (`:core/dsp`) | 8 |
| Effekt-Engine und Service-Lifecycle | 7,5 |
| Equalizer-Oberfläche (nur Code gelesen) | 7 |
| Tests, CI, Release-Pipeline | 7 |
| Codequalität und Wartbarkeit | 6 |
| Player-Technik (Wiedergabe, Queue, Fehlerbehandlung) | 5 |
| Player-Oberfläche | 4,5 |
| SoundCloud-Zugang (Login, API) | 3 |

Aufwand: **S** = wenige Stunden, **M** = ein bis drei Tage, **L** = mehr als drei Tage.

---

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

## Phase 1 – SoundCloud-Zugang auf solide Füße stellen

Ausgangslage: Login per WebView mit gefälschtem Desktop-User-Agent, Abgreifen des `oauth_token`-Cookies, `client_id` aus dem JavaScript der Webseite, alle Abrufe über die inoffizielle `api-v2`. Google blockiert die Anmeldung in eingebetteten WebViews absichtlich; jede weitere Tarnung ist ein Wettlauf, der ohne Vorwarnung wieder bricht.

### P1-1 Sofort-Lösung ohne Code: Anmeldung per E-Mail und Passwort (S)
- Für das SoundCloud-Konto über „Passwort vergessen" ein Passwort setzen und im vorhandenen WebView mit E-Mail anmelden. Google ist dann nicht beteiligt.
- **Vorher prüfen:** Der Kommentar in `SoundCloudLoginActivity.kt` behauptet, es gebe kein E-Mail-Formular mehr. Das am Gerät gegenprüfen.

### P1-2 Zielbild: offizielle SoundCloud-API mit OAuth 2.1 und PKCE (L)
- Anmeldung in einem Chrome Custom Tab, Rücksprung über `hardbasseq://soundcloud-callback`. Das ist dasselbe Muster wie `SpotifyLoginActivity`. Im echten Browser funktioniert die Google-Anmeldung, weil SoundCloud dort der Vertragspartner von Google ist.
- Voraussetzung: eigene App bei SoundCloud registrieren (`client_id`, `client_secret`). Eingabe der eigenen Zugangsdaten in „Verknüpfte Dienste", wie heute bei Spotify.
- `SoundCloudClient` auf `api.soundcloud.com` umstellen: `/me`, Likes, Playlists, Suche, `/tracks/{id}/streams`. Access-Token läuft nach etwa einer Stunde ab, Refresh-Token ist einmalig verwendbar: Erneuerung mit Sperre gegen parallele Anfragen bauen.
- **Bekannte Einschränkung:** Die offizielle API liefert für Go+-Katalogtitel voraussichtlich nur Vorschau oder gar keinen Stream. Vor der Umstellung mit dem eigenen Konto testen, welche der regelmäßig gehörten Titel betroffen sind.
- **Hinweis:** Die Entwickler-Doku war aus der Review-Sitzung nicht erreichbar. Endpunkte und Limits beim Umsetzen gegen `developers.soundcloud.com` prüfen.

### P1-3 Rückfallweg behalten und bequemer machen (S)
- Manuelle Token-Eingabe bleibt. Ergänzen: Einfügen aus der Zwischenablage mit einem Tipp und Prüfung des Tokens gegen `/me`, bevor gespeichert wird.

### P1-4 Sackgassen (nicht weiterverfolgen)
- Cookies aus einem Custom Tab lesen: technisch nicht möglich, die Cookie-Speicher sind getrennt.
- Natives Google Sign-In oder Credential Manager: das Token gilt für die eigene App, SoundCloud akzeptiert es nicht.
- Weitere User-Agent- oder Header-Tricks, eigene Browser-Engine (GeckoView): verstößt gegen Googles Vorgabe für eingebettete Browser und kann jederzeit abgeschaltet werden.

---

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

## Phase 3 – Player-Redesign „Sonic Control"

Laut `CLAUDE.md` gilt: erst Plan, dann Freigabe, dann Code. **Dieser Abschnitt ist der Plan; umgesetzt wird erst nach ausdrücklicher Freigabe.**

**Warum der Player heute „am Rand" wirkt:** Der ganze Player-Tab ist eine einzige Scroll-Liste (`PlayerScreen.kt`, 1.054 Zeilen). Cover und Transport stehen oben, darunter Tabs mit Suche, Playlists und Queue, danach Kontext-Chips und die DSP-Karte. Wer in den Suchergebnissen scrollt, schiebt die Steuerung aus dem Bild. Eine Mini-Leiste gibt es nur im Equalizer-Tab.

### P3-1 Drei Ebenen wie bei Spotify, SoundCloud und YouTube Music (L)
1. **Bibliothek** als Startseite des Player-Tabs: Suchfeld oben, darunter Abschnitte (Zuletzt gehört, Likes, Playlists, Neue Uploads), alle Zeilen mit Cover.
2. **Mini-Player** fest über der Navigationsleiste in *beiden* Tabs: Cover, Titel, Interpret, Play/Pause, Weiter, Fortschrittslinie, EQ-Statuspunkt.
3. **„Läuft gerade"** als Vollbild, das aus dem Mini-Player nach oben aufzieht und sich nach unten wegwischen lässt.

### P3-2 Aufbau von „Läuft gerade" (M)
- Großes Cover, Titel, Interpret, Like, „Zur Playlist".
- Suchleiste als **Wellenform** des Titels: SoundClouds Erkennungsmerkmal und passend zur technischen Linie.
- Transportzeile: Shuffle, Zurück, Play, Weiter, Wiederholen.
- Untere Zeile: Ausgabegerät (z. B. „Bluetooth · Name"), EQ-Chip mit Mini-Kurve (vorhandener `PlayerSoundChip`), Queue-Knopf.
- Queue als Bottom-Sheet mit Umsortieren und Wegwischen.

### P3-3 Eigene Handschrift statt Kopie (M)
- **Live-Spektrum hinter der EQ-Kurve** und eine **Limiter-Anzeige** (Pegelreduktion in dB). Der eigene Player hat Zugriff auf die PCM-Daten; das kann keiner der großen Player zeigen. Grün nur für „Signal wird verarbeitet", Amber für Bedienelemente.
- Qualitäts-Etikett am Titel (z. B. „AAC 160 · 44,1 kHz") und klar markierte Vorschau-Titel.

### P3-4 Aufräumen der Seite (S)
- Kontext-Chips und DSP-Karte (Mono-Bass, Limiter) aus dem Player in ein „Klang"-Sheet hinter dem EQ-Chip verschieben.
- Playlist-Import aus Link in einen eigenen Dialog; die Seite wird dadurch zur Bibliothek.
- `PlayerScreen.kt` in einzelne Dateien je Bereich teilen.

---

## Phase 4 – Klangqualität

### P4-1 Bessere Quelle wählen: AAC statt MP3 128 (S–M) — größter Einzelhebel
- **Befund:** `StreamSelection` bevorzugt „progressive" vor HLS und wertet weder Codec noch Qualitätsstufe aus. Progressive ist bei SoundCloud MP3 mit 128 kbit/s; über HLS gibt es AAC 160 und mit Go+ AAC 256. Kein Equalizer holt zurück, was der Codec entfernt hat, und ein Bass-Boost macht MP3-Artefakte im Hochton eher hörbar.
- **Zusätzliches Risiko:** SoundCloud hat angekündigt, die MP3-Progressive-Streams zugunsten von AAC-HLS abzuschalten. Aktuellen Stand prüfen.
- **Fertig, wenn:** Die Auswahl nach Qualität sortiert (AAC 256, AAC 160, MP3 128) und HLS am Gerät geprüft ist.

### P4-2 Lautheit pro Titel messen und angleichen (M)
- **Befund:** Der eigene Player senkt um den vollen stärksten Boost ab (`CurveEqualizer.preGain`). Bei +12 dB Bass wird alles 12 dB leiser, auch leise gemasterte Titel, die den Platz gar nicht bräuchten.
- **Lösung:** Lautheit nach ITU-R BS.1770 (LUFS) beim Abspielen messen, je Titel-ID merken, Vorverstärkung daran ausrichten. Der Limiter bleibt als Sicherung.
- **Nutzen:** gleichmäßige Lautstärke zwischen Titeln und spürbar mehr Pegel bei leisen Titeln.

### P4-3 Virtual Bass neu aufbauen (M)
- Nach P0-5: Kennlinie mit einstellbarem Verhältnis von zweiter und dritter Harmonischer statt reiner Gleichrichtung, Bandpass für die Obertöne, Hüllkurvenfolger, damit der Effekt bei leisen Stellen nicht überproportional wird.

### P4-4 Crossfeed im eigenen Player anschließen (S)
- `Crossfeed.kt` existiert mit Tests. Für Kopfhörer im eigenen Player einschalten; verringert die Ermüdung bei hart links/rechts gemischten Titeln. Für fremde Apps bleibt es technisch unmöglich.

### P4-5 Dynamischer Bass im eigenen Player (M)
- Der Systempfad hat einen 4-Band-Kompressor, der eigene Player keinen. Ein pegelabhängiges Bass-Shelf (voller Boost bei leisen Stellen, weniger bei lauten) bringt mehr gefühlten Bass, ohne dass der Limiter bei jedem Kick eingreift.

### P4-6 Limiter verfeinern (M)
- True-Peak-Erkennung mit 4-fach-Polyphasenfilter statt Catmull-Rom-Schätzung.
- Zweistufiges Release (schnell für einzelne Spitzen, langsam für Dauerlast) statt fester 80 ms.

### P4-7 Float-Verarbeitung bis zum Ausgang prüfen (M)
- Die Kette rechnet intern in Float, gibt aber PCM16 mit Dither aus. Mit Media3 1.11.1 prüfen, ob Float-Ausgabe zusammen mit eigenen Prozessoren möglich ist. Nutzen bei 128–256-kbit/s-Quellen klein; nach P4-1 und P4-2 einordnen.

### P4-8 Systempfad (Spotify und andere fremde Apps) (M)
- **Kurvenstapel entschärfen (alt: HIGH-03, weiter offen):** Bis zu acht Kurven werden addiert. Bei jedem Bluetooth-Gerät kommen ungefragt −1,5 dB bei 4,5 kHz und bis zu +9 dB Loudness-Bass dazu (`HeadphoneComfortCurve.kt`, `HeadphonePower.DEFAULT_LOUDNESS_DB = 9f`). „Flat" ist damit nicht flat. Standard auf 0 setzen, Präsenzsenke optional machen.
- **Kurvenlogik doppelt:** Dieselbe Kombination steht in `MainViewModel.combinedCurve()` und noch einmal in `EqualizerScreen.kt`. Eine Quelle daraus machen, sonst zeigt die Grafik irgendwann etwas anderes als die Engine.
- **Geräte-Korrektur in den Post-EQ:** `DynamicsProcessing` hat eine ungenutzte Post-EQ-Stufe. AutoEq-Korrektur dorthin legen, Geschmackskurve im Pre-EQ lassen; so teilen sich beide nicht dieselben Bänder.
- **Getrennte Kanäle:** Pro-Ohr-Abgleich (Balance, einseitige Hörschwäche) ist über die Kanal-APIs möglich.

### P4-9 Persönliche Abstimmung (L)
- Hörtest mit Sinustönen pro Ohr, daraus eine Ausgleichskurve.
- Vollständige AutoEq-Datenbank online durchsuchbar statt nur der eingebauten Auswahl; im eigenen Player als echte parametrische Filter statt Annäherung über 14 feste Bänder.

---

## Phase 5 – Neue Funktionen

| ID | Funktion | Aufwand |
|---|---|---|
| P5-1 | Schnelleinstellungs-Kachel und Widget: EQ an/aus, Preset wechseln | S |
| P5-2 | Mehr fremde Apps erkennen: optionale Freigabe per Shizuku/ADB (`DUMP`), wie es Wavelet anbietet, für Apps ohne Session-Broadcast | M |
| P5-3 | Parametrischer Modus: Frequenz, Güte und Verstärkung je Band frei wählbar | M |
| P5-4 | Presets teilen per Link oder QR-Code; Import von AutoEq- und Wavelet-Dateien | S–M |
| P5-5 | Profile je App (Spotify anders als YouTube), zusätzlich zu den Profilen je Gerät | M |
| P5-6 | Sleep-Timer und Überblenden zwischen Titeln | S–M |
| P5-7 | Lokale Dateien als zweite Quelle im eigenen Player: volle DSP-Kette, unabhängig von SoundCloud | M |
| P5-8 | Bluetooth-Codec und Abtastrate im Diagnosebericht anzeigen | S |

---

## Phase 6 – Wartbarkeit und Absicherung

- **P6-1 Große Dateien teilen (M):** `MainViewModel.kt` (1.448 Zeilen), `EqualizerScreen.kt` (1.451), `PlayerViewModel.kt` (834). Kurvenberechnung, Geräteprofile und Vergleichsmodus in eigene Klassen.
- **P6-2 `:player` angleichen (M):** Versionen stehen fest im Modul statt im Versionskatalog, Lint ist abgeschaltet (`abortOnError = false`), kein Hilt, Zustand in globalen Objekten (`PlayerDspState`, `NowPlayingState`, `PlayerCurveState`).
- **P6-3 Gerätetests (M):** Es gibt 72 Testdateien, aber keinen einzigen Test auf einem Gerät. Mindestens: Service-Start aus dem Hintergrund, Attach an eine Session, Room-Migration.
- **P6-4 Absturzgrund über Neustarts merken (S, alt: MED-04):** `ActivityManager.getHistoricalProcessExitReasons()` in den Diagnosebericht.
- **P6-5 Roadmaps zusammenführen (S):** Fünf weitere Roadmap-Dateien mit zusammen rund 4.200 Zeilen (`docs/roadmap*.md`, `roadmap-kontext-dsp.md`). Erledigtes ins Archiv, eine gültige Datei.
- **P6-6 R8 für Release (M):** Macht keinen Klangunterschied. Bringt ein kleineres APK und schnellere Schleifen im Audio-Thread; braucht Keep-Regeln und einen Gerätetest.
- **P6-7 Statusangaben prüfen (S):** Ein Punkt gilt erst als erledigt, wenn der Code auf `main` es zeigt (siehe P0-1).

---

## 7. Empfohlene Reihenfolge

1. Phase 0 komplett (ein bis zwei Tage, behebt echte Fehler).
2. P1-1 testen, P4-1 umsetzen (bessere Quelle, sofort hörbar).
3. P2-1 bis P2-3 (Fundament), danach Freigabe für Phase 3 einholen und das Redesign bauen.
4. P4-2, P4-3, P4-8 (Klang).
5. P1-2 (offizielle API), sobald die App-Registrierung bei SoundCloud steht.
6. Phase 5 und 6 nach Bedarf.

---

## 8. Übernommen aus der Roadmap vom Oktober 2026

| Alt-ID | Thema | Stand laut Code | Jetzt |
|---|---|---|---|
| CRIT-01 | Foreground-Service-Absturz im EQ-Service | erledigt (`ServiceCompat`, `START_NOT_STICKY`) | gleicher Fehler im Player-Service: P2-4 |
| CRIT-02 | Race in `markDetached()` | erledigt | – |
| HIGH-01 | Makeup-Gain treibt den Limiter | erledigt | – |
| HIGH-02 | 34-Band-Raster | **zurückgerollt** | P0-1 |
| HIGH-03 | Kurvenstapel bei Bluetooth | offen | P4-8 |
| HIGH-04 | Capabilities vor dem ersten Attach | erledigt | – |
| HIGH-05 | Lautstärke-Monitoring ohne Session | laut alter Roadmap erledigt, nicht nachgeprüft | – |
| MED-01 | Umschalten 34-Band zur Laufzeit | erledigt | – |
| MED-02 | Diagnose: konfiguriert gegen aktiv | laut alter Roadmap erledigt, nicht nachgeprüft | – |
| MED-03 | Grafik zeigt im 34-Band-Modus nur die Gerätebänder | offen (`EqualizerScreen.kt`, `bandPoints`) | mit P4-8 erledigen |
| MED-04 | Letzter Absturzgrund | offen | P6-4 |
| LOW-01 | Limiter kanalgekoppelt | erledigt (`LookaheadLimiter(channelCount)`) | – |
| TEST-02 | Test auf Galaxy S21 Ultra mit Android Auto | offen, braucht das Gerät | bleibt |
| DEBT-01 | `core/src/src` | erledigt | – |
| DEBT-02 | `Media3DspPipeline` | offen | P0-6 |
