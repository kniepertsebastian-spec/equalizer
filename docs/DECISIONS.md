# Offene und getroffene Entscheidungen

Wird am Ende jeder Arbeitssession aktualisiert (siehe `roadmap.md` §15,
Punkt 10).

## Aktueller Stand (19. September 2026)

- **Package-ID / Namespace:** `com.hardbasseq.eq`, abgeleitet vom Arbeitstitel
  „HardBass EQ“. Noch nicht final (siehe Roadmap §17, „Finaler App-Name und
  Package-ID“ bleibt offen). In M1 sind Package-ID und Namespace bereits
  zentral über `gradle.properties` konfiguriert.
- **Modulstruktur:** Einzelnes `app`-Modul, wie in Roadmap §7 für den ersten
  Commit ausdrücklich erlaubt. Trennung Audio-API/Android-Backend erfolgt vor
  M2.
- **Versionswahl (AGP, Kotlin, Compose BOM, SDK-Level):** siehe
  `docs/DEPENDENCIES.md`.
- **M1-Fundament:** Navigation, ViewModel, Hilt/KSP und Coroutines sind
  eingerichtet. Room, DataStore und Serialization bleiben gemäß ADR 0001/0002
  zurückgestellt; die Roadmap führt diesen Rest jetzt als eigene offene Checkbox.
- **Build-Reparatur vor Erweiterungen:** PR #5 wurde trotz rotem CI-Lauf
  gemergt. Die Ursache Java-17-/Kotlin-21-Zielkonflikt wird durch explizites
  Kotlin-JVM-Ziel 17 behoben (ADR 0002). Versionen und Audiofunktionen werden
  in diesem Schritt nicht geändert. Nachweis: `docs/TEST_MATRIX.md`.
- **Keine `MODIFY_AUDIO_SETTINGS`-Berechtigung im Manifest:** `AudioEffect.queryEffects()`
  ist eine statische, berechtigungsfreie Abfrage. Berechtigungen werden erst
  ergänzt, wenn ein konkreter, implementierter Anwendungsfall (Session-Attach
  in M2) sie tatsächlich benötigt (Roadmap §12).

## Entscheidungsvorlage: Session-EQ vs. eigener Player (Release-Gate A)

Aus `roadmap-2026.md` M1 "Release-Gate A" (23. September 2026): Wird auf
wichtigen Playern (Spotify, SoundCloud) keine verlässliche
Fremd-Session-Anbindung erreicht, muss **vor** M3 entschieden werden, ob ein
eigener Player Teil des Produkts wird. Diese Vorlage strukturiert die
Entscheidung, sobald `docs/TEST_MATRIX.md` (M0-Sprint-0-Vorlage oben)
ausgefüllt ist – trifft die Entscheidung nicht selbst.

**Optionen** (roadmap-2026.md M1):
- **A – Session-basierter System-EQ bleibt Kernprodukt.** Voraussetzung:
  ≥95 % erfolgreiche Attach-Vorgänge über 50 definierte Starts je
  Ziel-Player/Testgerät (Abnahmekriterium M1).
- **B – Eigener Media3-Player für garantiertes DSP wird ergänzt.**
  Auslöser: Attach-Rate liegt strukturell unter dem Zielwert, oder ein
  wichtiger Player (z. B. SoundCloud) ist auf absehbare Zeit technisch
  nicht erreichbar (siehe Session-Log-Eintrag zu SoundCloud/Broadcast-
  Zuverlässigkeit).
- **C – Hybrid.** Session-EQ bleibt Standard, eigener Player als
  Fallback/Zusatzoption für nicht unterstützte Player.

**Eingabedaten für die Entscheidung** (aus der ausgefüllten Testmatrix):
1. Attach-Erfolgsquote pro Player, aggregiert über alle getesteten
   Geräte/Routen.
2. Ob der Kontrollverlust (`LostControl`) reproduzierbar durch Re-Attach
   behebbar ist oder strukturell auftritt (z. B. bei jedem Trackwechsel).
3. Ob das Zustellungsproblem bei SoundCloud (Broadcast kommt nie an, siehe
   `docs/TEST_MATRIX.md` "Control-Intents und Session-0-Experiment")
   geräte- oder plattformweit ist.

**Status:** Noch nicht entschieden – wartet auf die M0-Sprint-0-Testmatrix.
Erste reale Rückmeldung (24. September 2026, siehe `docs/TEST_MATRIX.md`
"Reale Rückmeldung"): Spotify wird erkannt, SoundCloud und YouTube nicht –
bestätigt Punkt 3 oben als reales, nicht nur theoretisches Risiko. Ein
Versuch, das über `AudioManager.AudioPlaybackCallback` +
`AudioPlaybackConfiguration.getAudioSessionId()` broadcast-unabhängig zu
lösen, scheiterte am CI-Build: Diese Member sind entgegen der Annahme kein
Teil der öffentlichen Android-API (`@SystemApi`/verborgen, nicht im
`compileSdk`-Stub) und wurden zurückgerollt – Details in
`docs/TEST_MATRIX.md` "Reale Rückmeldung". Es gibt damit aktuell **keinen
bekannten Weg**, Option A (reiner Session-EQ) für SoundCloud/YouTube zum
Laufen zu bringen; der einzige öffentlich unterstützte Weg dafür wäre eine
Audio-Capture-Pipeline (`AudioPlaybackCaptureConfiguration` + `MediaProjection`,
Option B/C) – ein eigener Architekturentscheid, keine kleine Ergänzung.
Die Entscheidung bleibt offen, rückt aber näher an B/C. **Bewusst auf
später verschoben** (24. September 2026) – blockiert Sprint 0 nicht mehr,
siehe „Für später notiert" unten für die vollständige Einordnung.

**Nachtrag (25. September 2026):** Faktisch bereits Richtung **Option C
(Hybrid)** unterwegs, unabhängig von diesem Dokument entstanden: Ein eigener
Player (`:player`-Modul, ursprünglich eigenständiges Repo, siehe
`settings.gradle.kts` und `roadmap.md` Session 19) spielt SoundCloud über die
SoundCloud-API selbst ab, wodurch HardBass EQ an die eigene, garantiert
vorhandene Session anhängt (`PlayerBridge`/`AndroidPlayerBridge`) – SoundClouds
fehlender `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`-Broadcast spielt damit
keine Rolle mehr. Spotify bleibt beim klassischen Session-Attach (Option A),
da es den Broadcast sendet. YouTube ist im Player als Quelle vorgesehen, aber
laut Session-19-Log noch nicht funktionsfähig. Diese Zeilen halten das nur
nachträglich fest – **die Entscheidung selbst wurde nicht hier, sondern
direkt im Code getroffen**; ob das rückwirkend so gewollt war/bleibt, ist
noch nicht ausdrücklich bestätigt.

### Für später notiert: Root-Modus und Cross-Platform (Android/iOS/Web)

Nicht Teil von Sprint 0 oder M1, aber als Kontext für die spätere B/C-
Entscheidung festgehalten:

- **Root-Modus als vierte technische Option (D):** Auf gerooteten Geräten
  lässt sich Audio systemweit auf HAL-Ebene abfangen (`audio_effects.xml`
  als systemweiter Post-Processing-Effekt, oder Library-Injection in
  `audioserver`/AudioFlinger) – genau der Mechanismus hinter Viper4Android
  und dem Root-Modus von RootlessJamesDSP/Wavelet. Das funktioniert für
  jede App inkl. Spotify/SoundCloud/YouTube, unabhängig von Kooperation,
  da es am gemischten Endsignal ansetzt statt an einzelnen Sessions.
  Voraussetzung ist ein entsperrter Bootloader/Root – auf vielen aktuellen
  Geräten (u. a. vielen Samsung-Modellen) praktisch nicht gegeben, und
  Root-only-Vertrieb erreicht nur eine kleine, technikaffine Zielgruppe.
  Rein Android-spezifisch, kein Beitrag zum Cross-Platform-Ziel unten.
- **Cross-Platform-Ziel (Android/iOS, „vermutlich per Web"):** Weder
  Session-Attach (A) noch Audio-Capture (B/C) noch Root (D) existieren
  auf iOS oder im Web – das sind alles Android-spezifische OS-Mechanismen.
  Ein „eigener Player" mit eigener Decode-/DSP-Pipeline (Media3/
  AVAudioEngine/Web Audio API) wäre die einzige Architektur, die auf allen
  drei Plattformen technisch gleich funktioniert – **aber**: Spotify,
  SoundCloud und YouTube lassen sich aus Lizenz-/ToS-Gründen nicht über
  einen eigenen Drittanbieter-Decoder abspielen (Spotifys App-Remote-SDK
  steuert nur die Spotify-App selbst, YouTubes offizielle APIs liefern
  keinen extrahierbaren Stream, SoundClouds API-Zugang ist stark
  eingeschränkt). Ein eigener Player würde also nur für lokale Dateien
  oder Dienste mit expliziter Stream-Freigabe funktionieren, nicht für die
  drei aktuell getesteten Ziel-Player. Diese Spannung (Cross-Platform vs.
  „wirkt auf die Streaming-Dienste, die der Nutzer eh hat") ist ungelöst
  und Teil jeder künftigen B/C/D-Entscheidung, nicht nur eine Fußnote.

## Weiterhin offen (aus Roadmap §17, unverändert)

- [ ] Finaler App-Name und Package-ID
- [ ] Ausschließlich Session-Controller oder langfristig zusätzlich eigener Player (siehe Entscheidungsvorlage oben)
- [x] Physisches Testgerät: Google Pixel 10 / Android 16 (M0-Spikes getestet).
- [ ] Weitere Hersteller und Emulator für die Testmatrix verfügbar machen.
- [ ] Soll die erste öffentliche Version AutoEQ-Import enthalten oder erst Post-MVP?
- [ ] Open-Source-Lizenz und Veröffentlichungsmodell
- [ ] Monetarisierung – bewusst erst nach technischem MVP entscheiden

## Nächste konkrete Aufgabe

Android Lint für Debug und Release ist in PR #7 erfolgreich eingerichtet.
ktlint ist jetzt ebenfalls eingerichtet und in CI aktiv (`ktlint_official`-Stil,
bestehender Code per `ktlint --format` automatisch angepasst). detekt bleibt
bewusst zurückgestellt: seine stabile Version unterstützt Kotlin 2.3.20 laut
[detekt/detekt#9170](https://github.com/detekt/detekt/discussions/9170)
weiterhin nicht (nur `2.0.0-alpha.6`) – siehe ADR 0003.

PR #9 ("Jules") hat M2/M3-DSP-Grundlagen, Presets, Diagnose-UI und
Design-System-Tokens ergänzt und beansprucht, M1–M8 komplett abzuschließen.
Das Code-Review in `roadmap.md` §19 Session 10 widerlegt das für mehrere
Punkte: M4 (Room/DataStore/Import-Export) ist praktisch nicht verdrahtet,
M6-Onboarding und -Lokalisierung fehlen größtenteils, und mehrere konkrete
Bugs (doppelte `MainViewModel`-Instanz über Navigation, nicht abschaltbarer
Limiter, unkonfigurierte DynamicsProcessing-Stages, fehlende Synchronisierung
zwischen `attach`/`detach`/`apply`) wurden im Review gefunden und behoben.
Details, inklusive was noch offen bleibt, in `roadmap.md` Session 10 und im
PR-Review zu PR #9. Als Nächstes: M4-Persistenz tatsächlich verdrahten (Room
+ Route-Fingerprint + Profilwechsel + Import/Export-UI), Onboarding-Check und
Lokalisierung der neuen Bildschirme nachholen.

Die M0-Restpunkte (Emulator, hörbare reversible Änderung/Bypass und
schriftliche MVP-Backend-/Fallback-Entscheidung) bleiben ausdrücklich offen.
Ein erfolgreicher JVM-Build ersetzt keine Audio-Laufzeitprüfung am Gerät –
das gilt jetzt auch explizit für PR #9: der M0-Spike hat den von
`AudioSessionRepository` genutzten Broadcast-Mechanismus bereits als auf dem
Testgerät unzuverlässig dokumentiert, ohne dass PR #9 das erneut getestet hat.

## Kontext-Modi: Auto und Bluetooth-Box (30. September 2026)

Ein Profi-Equalizer hebt den Klang im Auto und an kleinen Bluetooth-Boxen nicht
nur über die EQ-Kurve, sondern über vier weitere Bausteine. Alle vier sind jetzt
umgesetzt, mit unterschiedlicher Reichweite:

| Baustein | Umsetzung | Reichweite |
|---|---|---|
| Kontext-Presets „Auto“ und „Bluetooth-Box“ | `BuiltInContextPresets` (Kurve, Makros, MBC, Limiter), in `BuiltInPresets.all` | alle Apps (Systemeffekt-Pfad) |
| Automatische Umschaltung | `AudioDeviceType.CAR`/`BLUETOOTH_SPEAKER`, Erkennung über Produktnamen (`SoundContextClassifier`) bzw. `TYPE_BUS`; Default nur für Routen ohne gespeichertes Profil | alle Apps |
| Lautstärke-Loudness | `VolumeLevelMapper` + vorhandene `LoudnessCompensationCurve`, folgt der System-Medienlautstärke (`SystemVolumeRepository`) | alle Apps |
| Subsonic-Filter | `SubsonicFilterCurve` (Butterworth-Highpass als `TargetPoint`-Kurve) | alle Apps, aber nur so steil wie das unterste Band des Systemequalizers es zulässt |
| Virtual Bass | vorhandener `BassExciter` über `BassExciterPcm16` in einem Media3-`AudioProcessor` | **nur eingebauter Player** |

Entscheidungen und Grenzen:

- **Virtual Bass läuft nicht bei Spotify & Co.** Der Systemeffekt-Pfad
  (Equalizer/DynamicsProcessing auf der Session der fremden App) kennt keine
  Hooks für eigene Sample-Verarbeitung. Echte Harmonische gibt es nur dort, wo
  die App den Audiopfad selbst besitzt: im eingebauten ExoPlayer. Die UI sagt
  das beim Schalter ausdrücklich.
- **Erkennung ist eine Heuristik.** Android nennt ohne die Berechtigung
  `BLUETOOTH_CONNECT` keine Geräteklasse, nur den Produktnamen. Der Treffer ist
  deshalb nur ein Startwert für Routen ohne gespeichertes Profil; eine manuelle
  Presetwahl wird wie gewohnt als Geräteprofil gespeichert und gewinnt immer.
  Der Startwert selbst wird bewusst nicht gespeichert. Beim Verlassen einer
  automatisch umgeschalteten Route ohne Profil wird das vorherige Preset
  wiederhergestellt (nur im Speicher, nicht über einen Prozessneustart hinweg).
- **Kein Schema-Bump.** `DeviceProfileEntity.routeType` ist rein informativ
  (Schlüssel ist `routeId`), die neuen Preset-/`ProcessingSettings`-Felder haben
  Defaults „aus“, altes JSON dekodiert unverändert.
- **Android Auto / CarPlay:** dabei läuft die Wiedergabe typischerweise über den
  Projektionskanal des Head Units, nicht über eine vom Telefon ansteuerbare
  Audio-Session. Ob dort überhaupt ein Effekt greift, ist ungeprüft und hängt
  vom Fahrzeug ab. Der Modus zielt auf klassisches Bluetooth-Audio (A2DP) im Auto.
- Wie bei den übrigen DSP-Bausteinen gilt: Filter und Kurven sind gegen
  synthetische Signale offline getestet, **nicht** am Gerät gehört. Siehe
  `docs/TEST_MATRIX.md`, Abschnitt „Kontext-Modi“.

## Großer Player und SoundCloud-Playlists (30. September 2026)

Die Mini-Leiste unten öffnet jetzt einen vollwertigen Player (Route `player`):
Cover, Fortschrittsbalken mit Spulen, Vor/Zurück, Warteschlange, gespeicherte
Playlists und ein Feld zum Einfügen von Links. Die Leiste selbst zeigt einen
Fortschrittsstreifen; zusätzlich gibt es den Button „Player & Playlists“.

**Was direkt abgespielt wird – und was bewusst nicht:**

| Quelle | Verhalten |
|---|---|
| SoundCloud-Titel-/Playlist-Link (auch `on.soundcloud.com`, Mobil-Links, mit Tracking-Parametern) | wird aufgelöst, Titel spielt sofort, Playlist wird gespeichert |
| Teilen aus der SoundCloud-App an diese App | `ACTION_SEND text/plain` auf `MainActivity`, öffnet den Player und importiert |
| Spotify-/YouTube-(Music-)Links | werden erkannt und mit Erklärung abgelehnt |
| Spotify/YouTube Music direkt über den eigenen Player | **nicht umgesetzt und nicht geplant**: die Streams sind DRM-geschützt, die Nutzungsbedingungen verbieten das Herauslösen des Audios; Stream-Extraktoren für YouTube verstoßen gegen deren Bedingungen und Play-Store-Richtlinien |

**Technik:**

- `ShareLink`/`SavedPlaylist`/`QueueNavigation`/`PlayerFormat` liegen in `:core`
  (plattformfrei, dort getestet). Link-Auflösung (`SoundCloudClient.resolveLink`),
  Queue und Fortschritt im `AudioPlayerService`; die App spricht ihn über
  `PlayerController` an (Intents + geteilte Zustands-Singletons, wie bei
  `NowPlayingState`).
- Gespeicherte Tracks enthalten **keine Stream-URL**: SoundCloud-URLs laufen ab,
  deshalb löst der Service beim Abspielen per Track-ID eine frische auf. Ein
  nicht abspielbarer Titel wird übersprungen.
- Playlists liegen in einem eigenen DataStore (JSON), nicht in Room – kein
  Schema-Bump, keine Migration. Identität einer Playlist ist ihr Link; erneutes
  Importieren aktualisiert sie.
- `MainActivity` ist jetzt `singleTask`, damit ein geteilter Link die laufende
  Instanz (und deren ViewModels) erreicht statt eine zweite zu starten.
- Virtual Bass, Limiter-Einstellungen usw. greifen bei allem, was hier läuft,
  weil der eigene Audiopfad genutzt wird.

**Bekannte Grenzen:** Der SoundCloud-Client holt seine `client_id` per
Webseiten-Scraping (inoffiziell, kann jederzeit brechen). Cover werden ohne
Bildbibliothek geladen (ein Bild, kein Cache). Keine Sperrbildschirm-/
Benachrichtigungssteuerung (Media3-Session) und kein Zufall/Wiederholen.
Spotify-/YouTube-Playlists per Titelabgleich auf SoundCloud zu importieren ist
ein möglicher Folgeschritt (Spotify-Web-API-Zugang vorher klären).

### SoundCloud Go im eigenen Player (30. September 2026)

Der Player nutzt das gespeicherte Konto-Token des Nutzers (Login im Player-Screen,
WebView oder manuelles Token) für jede Anfrage. Mit einem aktiven Go-Abo liefert
SoundCloud die vollen Streams; Werbung blendet nur der offizielle Client ein, der
Player spielt die Audiodatei des Titels direkt.

- `StreamSelection`: volle Länge vor 30-Sekunden-Vorschau (`snipped`), bei
  Gleichstand Progressive vor HLS. Wird nur eine Vorschau angeboten, erscheint
  „Nur 30-Sekunden-Vorschau“ im Player und ein Hinweis (mit dem Zusatz, sich
  anzumelden, falls kein Token da ist).
- `media3-exoplayer-hls` ergänzt: einige volle Streams gibt es nur als HLS.
- **Nicht verifiziert:** dass ein Go-Token in dieser inoffiziellen Nutzung
  tatsächlich volle Längen freischaltet (Netzwerk/Konto, nicht testbar ohne
  Gerät), und ob SoundCloud den Zugriff über die Webseiten-`client_id` anders
  behandelt. Ein inoffizieller Client kann jederzeit brechen; die Nutzungs-
  bedingungen von SoundCloud sind dafür nicht eindeutig. Hohe Qualität (Go+
  AAC 256 über HLS) wird bewusst noch nicht bevorzugt, bis HLS am Gerät
  nachweislich läuft.

### Kontext-Modus ist eine Ebene, kein Preset (30. September 2026, Nachtrag)

Erste Fassung: „Auto“/„Bluetooth-Box“ waren Presets. Wer danach ein Genre-Preset
(z. B. Uptempo) wählte, ersetzte sie damit – Subsonic, Loudness, Virtual Bass
und Limiter gingen aus. Jetzt ist der Modus ein eigener, gespeicherter Zustand
(`MainViewModel.activeContext`, `LiveSettings.activeContext`), der über **jedem**
Preset liegt:

- Zusätzlich zur Preset-Kurve wird die Kurve des Modus addiert; Subsonic,
  Loudness und Virtual Bass gelten jeweils mit dem stärkeren Wert aus Preset und
  Modus; der Limiter ist mindestens so eng wie vom Modus verlangt (an).
  Makros, Kompressor und Headroom des Genres bleiben unberührt.
- Der Modus bleibt beim Genre-Wechsel an und ist auch im Player-Screen
  schaltbar (Karte „Klangmodus“). Er wirkt auf alles, was über den Equalizer
  läuft, weil er Teil der Effekt-Einstellungen ist.
- Routen-Erkennung schaltet ihn ein; beim Verlassen schaltet sie ihn nur aus,
  wenn sie ihn eingeschaltet hat. Manuell gesetzt bleibt er, bis man ihn
  ausschaltet. Das Genre-Preset wird nie angefasst.
- Ein vor dieser Änderung gespeichertes Geräteprofil mit Kontext-Preset-ID
  bedeutet jetzt „Modus an“ und lässt das Genre-Preset unverändert.
- Kein Schema-Bump: neue `LiveSettings`-Felder haben Defaults.

Player-Screen: helle Schrift (Titel helles Lila, Interpret Rosa, sonst helles
Neutral) und Abstand zu Status-/Navigationsleiste – außerhalb einer Karte war
die Standard-Textfarbe fast schwarz auf dunklem Grund.

### SoundCloud-Bibliothek im Player (30. September 2026)

Angemeldet zeigt der Player-Screen die Bibliothek des Kontos: „Likes“, eigene
Playlists und gelikte Playlists. Antippen lädt die Titel und spielt sie als Queue
(Stream-URLs werden wie immer erst beim Abspielen aufgelöst).

- Endpunkte (inoffiziell, `api-v2`, Token im Header): `/me`,
  `/users/{id}/playlists_without_albums`, `/users/{id}/playlist_likes`,
  `/users/{id}/track_likes`, `/playlists/{id}`; Seiten über `next_href`, begrenzt
  (6 Seiten Playlists, 10 Seiten Likes). `ApiUrl.withClientId` ersetzt dabei
  immer die `client_id`.
- **Nicht verifiziert:** Antwortformate und Verhalten der Endpunkte habe ich nur
  nach Kenntnis der Web-API defensiv umgesetzt, ohne Konto/Netzwerk testen zu
  können. Fehler erscheinen als Meldung in der Karte.
- **Go-Downloads (Offline) werden nicht genutzt und nicht nachgebaut.** Die
  Offline-Dateien der SoundCloud-App sind geschützt und nur dort abspielbar; ein
  eigener Offline-Cache aus den Streams würde die Go-Bedingungen verletzen. Der
  eigene Player braucht Netz. Denkbar (nicht umgesetzt): Download von Titeln, bei
  denen der Künstler den Download ausdrücklich freigegeben hat.

### YouTube-/Spotify-Link → Titel auf SoundCloud (30. September 2026)

Ein einzelner YouTube-, YouTube-Music- oder Spotify-Titel-Link wird erkannt; der
Titel selbst wird **nicht** von dort abgespielt. Stattdessen:

1. Titel und Kanal über die öffentliche oEmbed-Vorschau des Anbieters lesen
   (dieselben Daten wie eine Link-Vorschau im Messenger). Spotify liefert keinen
   Künstler, YouTube den Kanalnamen.
2. `TrackQueryBuilder` bereinigt den Titel („(Official Video)“, „[HD]“ …;
   Remix-/Edit-Hinweise bleiben) und trennt Künstler/Titel.
3. SoundCloud-Suche (ohne Stream-Auflösung); ohne Treffer zweiter Versuch nur mit
   dem Titel. `TrackMatcher` bewertet jeden Treffer nach Wortüberdeckung
   (Titel 65 %, Künstler 35 %, Abzug für fremde Zusatzwörter; der Künstler darf
   auch im Uploader-Namen stecken).
4. Bester Treffer ≥ 0,8 **mit bekanntem Künstler** startet von selbst; sonst
   (immer bei Spotify) zeigt die Karte „Auf SoundCloud gefunden“ die besten
   Treffer mit Trefferquote zur Auswahl.

Ausdrücklich nicht unterstützt: Playlists, Alben, Kanäle, DJ-Sets/Mixe (keine
einzelne Nummer), Spotify-Kurzlinks. Nicht einbettbare oder private Videos liefern
keine Angaben → verständliche Meldung. Die Trefferqualität hängt davon ab, ob der
Titel auf SoundCloud liegt; bei Mainstream oft nicht.

### „Interesting new uploads“ – wöchentliche Entdecker-Playlist (30. September 2026)

Im Player-Screen lassen sich Künstlernamen merken. Die App sucht dazu auf SoundCloud
nach **neuen Uploads, die den Namen nennen – auch von anderen Accounts** (Re-Uploads
sind der Sinn), und baut daraus eine eigene Playlist mit 15–20 Titeln, die sich
jeden Montag erneuert.

- **Was qualifiziert** (`DiscoveryRotation.isEligible`): der Name steht im Titel oder
  Uploader-Namen, Einzeltitel bis 10 Minuten (keine Sets/Mixe), Upload höchstens
  35 Tage alt, Datum bekannt. Suche: `search/tracks` mit `filter.created_at=last_month`
  (inoffiziell; die Daten werden zusätzlich selbst gegen das Upload-Datum geprüft).
- **Auswahl:** ungesehene Titel zuerst, Künstler reihum (einer füllt nicht die ganze
  Liste), bei zu wenig Neuem mit früheren Vorschlägen auf mindestens 15 aufgefüllt.
- **Wegwischen:** Titel seitlich aus der Liste wischen (oder ✕) – er wird nie wieder
  vorgeschlagen; unter 15 Titeln füllt die Liste aus dem Reservepool nach.
- **Montag:** `WeekKey` (Wochenwechsel um lokale Mitternacht, reine Arithmetik). Beim
  Öffnen des Screens und durch einen WorkManager-Job (alle 6 h, nur mit Netz) wird bei
  neuer Woche neu gebaut. Android legt die genaue Uhrzeit fest; die Liste ist also
  „Montag“, nicht „Montag 00:00“. Schlägt die Suche fehl, bleibt die alte Liste und der
  Job versucht es erneut.
- Zustand: eigener DataStore (JSON), kein Schema-Bump. Neue Künstler erzwingen sofort
  einen Neuaufbau.
- **Nicht verifiziert:** Datumsfilter und Antwortfelder (`created_at`) der inoffiziellen
  Suche; Trefferqualität bei häufigen Namen (zu allgemeine Namen liefern Fremdes).

**Genre-Filter (Nachtrag):** Damit „MBK“ nicht den Schlager-MBK liefert, müssen Uploads
zum Genre passen. Modi: *Wie meine Musik* (Standard), *Eigene* (Stichwörter), *Aus*.
- `GenreMatcher` vergleicht SoundCloud-`genre` und `tag_list` mit den gewünschten Begriffen
  (ganze Wörter; „uptempo“, „hardcore“, „gabber“ … gelten als eine Szene-Familie).
  Bei Szene-Wünschen zählen Gegen-Tags (Punk, Hip-Hop, Rap, Metal …) nicht, und
  „hardcore“ allein reicht dann nicht. Ergebnis: passt / unbekannt / passt nicht.
- *Passt nicht* wird aussortiert (Anzahl steht in der Meldung), *unbekannt* (kein Genre
  gesetzt) kommt, aber hinter den bestätigten Titeln.
- *Wie meine Musik*: `GenreProfile` leitet aus den Likes die häufigsten Genres ab (mind.
  5 Titel, mind. 3 Treffer bzw. 5 %, max. 6, ohne Allgemeinwörter), einmal pro Woche oder
  bei „Jetzt aktualisieren“. Schlägt das Lesen fehl, bleiben die alten Genres.
- **Nicht verifiziert:** Qualität der Genre-Tags auf echten Uploads; Likes-Endpunkt.

### Mono-Bass und Limiter im eigenen Player (1. Oktober 2026)

`BassMonoSummer` und `LookaheadLimiter` (vorher nur in `:core`, ungenutzt) hängen jetzt als
letzte Stufe in der Audiokette des eigenen Players (`PlayerDspAudioProcessor`, hinter
Virtual Bass, damit der Limiter dessen Obertöne mitfängt).

- **Beide standardmäßig an, beide abschaltbar** (Karte „Klang-Feinschliff“ im Player-Screen);
  Mono-Bass mit Übergangsfrequenz-Regler 40–200 Hz (Standard 120 Hz). Gespeichert in
  SharedPreferences (`PlayerDspState`), wirkt sofort ohne Player-Neustart.
- Mono-Bass nur bei Stereo; der Limiter läuft pro Kanal (nicht gelinkt) und verzögert das
  Signal um ca. 5 ms. Beim Wiedereinschalten wird er zurückgesetzt (kurze Stille möglich).
- Nur eigener Player, nicht Spotify & Co. (kein Hook im Systempfad).
- **Nicht verifiziert:** Klang auf echter Hardware, CPU-Last des Limiters (Fenster-Minimum
  pro Sample) – bei Aussetzern den Limiter abschalten und melden.

### Eigene Playlists: neu anlegen und Titel hinzufügen (1. Oktober 2026)

Im Player-Screen: „Neue Playlist“ (Karte Playlists) und ein Playlist-Symbol am aktuellen Titel
und an jedem Eintrag der Warteschlange („Zur Playlist hinzufügen“, auch mit „Neu anlegen und
hinzufügen“). Tippen auf den Namen einer Playlist klappt ihre Titel auf; bei eigenen Playlists
lassen sie sich einzeln entfernen.
- Lokal auf dem Gerät (`PlaylistEditing`, gleiche DataStore-JSON-Ablage, `sourceUrl == null`);
  ein Titel steht höchstens einmal in einer Playlist. Importierte Link-Playlists nehmen keine
  Titel auf (der Link ist ihre Quelle).
- **Nicht gebaut:** Schreiben in SoundCloud-Playlists des Kontos (inoffizielle API, Schreibzugriff
  ungeprüft) und Hinzufügen direkt aus „Interesting new uploads“.

### Bildschirm sperren: Kratzen/Aussetzer, gespielt-Markierung (1. Oktober 2026)

**Kratzen und Hängen bei gesperrtem Handy.** Zwei wahrscheinliche Ursachen, beide behoben:
- Der Player hielt weder CPU noch WLAN wach (`WAKE_LOCK` / `setWakeMode(WAKE_MODE_NETWORK)`
  fehlte); bei gesperrtem Bildschirm schläft das Handy ein und der Stream stockt.
- Der Limiter war zu rechenintensiv (Boxing, Minimum über das ganze Fenster, `pow`/`exp` pro
  Sample, ca. 20 Mio. Vergleiche/s). Bei gesperrtem Bildschirm taktet die CPU herunter, dann
  reicht die Rechenzeit nicht mehr → Aussetzer. Jetzt Ringpuffer ohne Allokation, Minimum in
  O(1), Konstanten gecacht; Verhalten gegen eine einfache Referenzimplementierung getestet.
- **Nicht verifiziert** auf dem Gerät. Hilft es nicht, hat der Hersteller-Akkusparmodus die App
  im Griff: Einstellungen → Apps → HardBass EQ → Akku → „Nicht optimieren“/„Unbegrenzt“.

**Gespielt-Markierung.** Der laufende Titel leuchtet (pulsierender fliederfarbener Rahmen,
Equalizer-Symbol), bereits gespielte Titel sind gedimmt mit Haken – in Warteschlange,
aufgeklappten Playlists und „Interesting new uploads“. `PlayedTracksState` merkt sich die
Titel nur, solange der App-Prozess läuft.

### Spotify-Playlist → Playlist aus SoundCloud-Titeln (1. Oktober 2026)

Ein öffentlicher Spotify-Playlist-Link (Teilen-Link oder eingefügt) legt auf dem Gerät eine
Playlist „<Name> (von Spotify)“ an: jeder Titel wird auf SoundCloud gesucht, nur sichere Treffer
(`TrackMatcher.isConfident`, Künstler bekannt) kommen hinein, der Rest steht in der Meldung
(„n von m gefunden. Nicht gefunden: …“). Es wird nichts von Spotify abgespielt oder geladen.
- Quelle der Titelliste: die öffentliche Embed-Seite der Playlist (`open.spotify.com/embed/playlist/<id>`),
  deren JSON (`__NEXT_DATA__`, Array `trackList`) `SpotifyPlaylistPage` namensbasiert liest.
  Inoffiziell: Spotify kann das jederzeit ändern; dann kommt „Titel konnten nicht gelesen werden“.
  Meist nur die ersten ~100 Titel; maximal 150 werden gesucht (eine Suche pro Titel, nacheinander).
- Nur öffentliche Playlists. Spotify-Kurzlinks (`spotify.link`), Alben und YouTube-Playlists sind
  weiter nicht möglich (YouTube hat keine öffentliche Titelliste ohne API-Schlüssel).
- **Nicht verifiziert** (kein Netz zu Spotify in der Entwicklungsumgebung): Aufbau der Embed-Seite,
  Trefferquote bei echten Playlists. Einzelne Spotify-Titel liefern über oEmbed nur den Titel,
  keinen Künstler – deshalb fragt die App dort weiter nach, statt automatisch zu starten.

**Nachtrag: Künstler bei einzelnen Spotify-Links.** Die oEmbed-Vorschau nennt nur den Titel.
Deshalb liest die App zuerst die öffentliche Track-Seite (`open.spotify.com/embed/track/<id>`,
`SpotifyTrackPage`) und sucht mit „Künstler Titel“; ist der Künstler bekannt und der Treffer
sicher, startet der Titel von allein. Lässt sich die Seite nicht lesen, bleibt es beim bisherigen
Weg (nur Titel, Trefferliste zur Auswahl). Außerdem gilt für alle Brücken-Suchen (YouTube,
Spotify, Playlist-Import): ist der erste Treffer nicht sicher, wird zusätzlich nur nach dem
Titel gesucht und beide Ergebnislisten werden gemeinsam nach Titel und Künstler bewertet.
Nicht verifiziert: Aufbau der Track-Embed-Seite.

**Nachtrag: lange Spotify-Playlists und Zusammenführen.** Spotifys öffentliche Seite listet nur die
ersten ca. 100 Titel einer Playlist – mehr kommt über diesen Weg nicht an (das ist die Grenze beim
*Lesen*, nicht beim Anlegen). Eine längere Playlist lässt sich deshalb in Spotify in Teile zu je
höchstens 100 Titeln aufteilen; werden die Links der Teile **zusammen** geteilt/eingefügt (mehrere
Links in einem Text), liest die App alle Teile und legt **eine** gemeinsame Playlist an
(„<Name> + n weitere (von Spotify)“), gleiche Titel nur einmal. Maximal 500 Titel pro Import
(eine Suche pro Titel, nacheinander – bei sehr vielen Titeln kann SoundCloud bremsen).
Zusätzlich: „Zusammenführen“ in der Playlists-Karte fasst beliebige eigene/importierte Playlists
zu einer neuen zusammen (Reihenfolge erhalten, Dopplungen entfernt, Originale bleiben).
Ein Spotify-API-Schlüssel (der echte Weg für Playlists >100) ist nicht eingebaut.

### Auto / Bluetooth: Titelanzeige und Tasten (1. Oktober 2026)

Der Player hatte keine Media-Session – Auto, Bluetooth-Geräte und Sperrbildschirm sahen deshalb
nichts (Anzeige „Inhalt nicht gefunden“). `AudioPlayerService` meldet jetzt eine Media3-
`MediaSession`: Titel, Interpret und Cover des laufenden Titels, Wiedergabestatus und die Tasten
Play/Pause/Weiter/Zurück (Weiter/Zurück gehen an die Warteschlange des Dienstes, ExoPlayer kennt
nur den einen laufenden Titel). Die Benachrichtigung ist ein Medien-Stil mit denselben Tasten und
wird bei Titel- oder Statuswechsel aktualisiert.
- Das ist der Weg für Bluetooth (AVRCP): das Auto zeigt „Läuft gerade“ wie bei Spotify.
- **Nicht gebaut:** Android Auto (USB/kabellos) und die Durchsuch-Ansicht im Auto – das braucht
  einen `MediaLibraryService` mit Inhaltsbaum. Die Auto-Mediaauswahl bleibt für HardBass EQ leer.
- Läuft die App nicht mehr (Dienst beendet), kann eine Auto-Taste sie nicht starten – erst in der
  App einen Titel starten.
- **Nicht verifiziert** auf einem echten Auto (Cover-Übertragung hängt vom Autoradio ab).

### Android Auto (1. Oktober 2026)

HardBass EQ meldet sich bei Android Auto als Medien-App: `AutoBrowserService`
(`MediaBrowserServiceCompat`, im Manifest mit `automotive_app_desc.xml`) zeigt dem Auto drei
Ordner – „Meine Playlists“ (mit ihren Titeln), „Interesting new uploads“, „Likes“ (nur mit
SoundCloud-Login) – bis 100 Einträge je Liste (`AppAutoCatalog`, Ids in `AutoMediaId`).
Wählt man einen Titel, wird die Liste, in der er steht, die Warteschlange und ab diesem Titel
gespielt. Abspielen und Tasten laufen über dieselbe Media-Session wie bei Bluetooth.
- Der Wiedergabe-Dienst selbst ist kaum verändert: die Session bekommt einen Callback, und der
  `ForwardingPlayer` fängt „spiele diese Id“ aus dem Auto ab (`startFromCar`); alles andere läuft
  wie bisher. Der Browser-Dienst bindet den Player-Dienst nur, wenn ein Auto/Assistent verbindet.
- Zugriff nur für System, Android Auto, Assistent und Bluetooth (`isTrustedClient`), nicht für
  beliebige Apps.
- **Voraussetzung:** Android Auto zeigt Apps, die nicht aus dem Play Store kommen, nur mit
  aktivierten Entwickleroptionen: Android-Auto-Einstellungen → mehrfach auf „Version“ tippen →
  Entwicklereinstellungen → „Unbekannte Quellen“ aktivieren.
- **Nicht verifiziert** (kein Android/Auto in der Entwicklungsumgebung): ob Android Auto die App
  anzeigt, Cover-Laden über https-Adressen, Sprachbefehle („Spiele Playlist …“).

**Nachtrag Android Auto: Sprachsuche.** Android-Lint verlangt für Auto-Medien-Apps einen
`MEDIA_PLAY_FROM_SEARCH`-Eintrag (`MissingIntentFilterForMediaSearch`) – ohne ihn war die CI auf
`main` nach dem Merge von PR #47 rot. Der Eintrag steht jetzt am `AutoBrowserService`, und die
Sprachsuche ist wirklich umgesetzt (`AutoCatalog.queueForSearch`): ein gesprochener Name, der in
einer Playlist vorkommt, startet diese Playlist; sonst wird auf SoundCloud gesucht; „Spiel Musik“
ohne Namen startet die neueste Playlist, sonst die Likes. Nicht verifiziert im echten Auto.

### Spotify-Import in Blöcken zu 100, mit gespeichertem Stand (1. Oktober 2026)

Der Playlist-Import sucht die Titel in **Blöcken zu je 100** auf SoundCloud (`SpotifyImportPlan`).
Jeder Block wird eine eigene Playlist („Mix – Teil 2 von 4 (von Spotify)“), und **nach jedem Block
wird der Stand gespeichert** (`SpotifyImportRepository`, eigener DataStore): Position, Titelliste,
Treffer bisher. Wird die App geschlossen oder bricht die Verbindung ab, macht der Import dort
weiter, statt von vorn zu beginnen:
- Dieselben Links nochmal einfügen/teilen **oder** in der Playlists-Karte „Fortsetzen“ tippen;
  „Verwerfen“ löscht den Stand (bereits angelegte Playlists bleiben).
- Fällt die Suche in einem Block überwiegend wegen der Verbindung aus (mehr als die Hälfte der
  Anfragen), hält der Import an, **ohne den Block zu überspringen** – „Fortsetzen“ versucht ihn neu.
- Die Teile lassen sich danach mit „Zusammenführen“ zu einer Playlist vereinen.
- Obergrenze 1.000 Titel je Import.
- **Grenze beim Lesen bleibt:** Spotifys öffentliche Seite liefert meist nur die ersten ~100 Titel
  einer Playlist. Hat eine gelesene Playlist genau 100 Titel, weist die Meldung darauf hin. Mehr
  als 100 Titel kommen nur an, wenn die Seite mehr liefert oder mehrere Teil-Links zusammen
  geteilt werden; der gespeicherte Stand hilft beim Suchen/Anlegen, nicht beim Lesen.

**Nachtrag: nichts doppelt importieren.** Spotifys Seite liefert nur die ersten ~100 Titel. Wer eine
größere Playlist ein zweites Mal teilte, bekam deshalb dieselben 100 nochmal als zweite Playlist.
Jetzt merkt sich die App je Quelle (`SpotifyImportLedger`), welche Spotify-Titel schon importiert
wurden und welcher SoundCloud-Titel dazu gefunden wurde:
- Dieselbe Playlist nochmal → „Nichts Neues“, keine zweite Playlist. Kommen neue Titel dazu (ergänzt
  in Spotify oder liefert die Seite mehr), werden nur diese als „… (Nachtrag)“ angelegt.
- Ein Titel zählt nur als erledigt, solange sein SoundCloud-Titel noch in einer deiner Playlists
  liegt – löschst du die Playlist, lässt sich dieselbe Quelle erneut importieren. Nicht gefundene
  Titel werden beim nächsten Mal nochmal versucht.
- **Weiter nicht möglich:** Titel 101 ff. aus *einer* Spotify-Playlist zu lesen. Dafür bräuchte es die
  Spotify-Web-API mit eigenem Entwickler-Schlüssel (nicht eingebaut) oder die Playlist in Spotify in
  Teile zu je ≤100 aufzuteilen und die Links zusammen zu teilen.

### Spotify-Anmeldung für lange eigene Playlists (1. Oktober 2026)

Wer in der Playlists-Ansicht die Client-ID seiner eigenen Spotify-Entwickler-App einträgt und sich
anmeldet, bekommt **alle** Titel seiner eigenen Playlists (auch über 100) über die Spotify-Web-API.
- **Anmeldung:** OAuth „Authorization Code mit PKCE“, kein Client-Secret (`SpotifyAuth`, core,
  getestet inkl. RFC-7636-Testvektor). Redirect-URI **`hardbasseq://spotify-callback`** (in der
  Spotify-App einzutragen), Rechte nur `playlist-read-private` und `playlist-read-collaborative`.
  `SpotifyLoginActivity` nimmt den Redirect an, tauscht den Code gegen Tokens (`SpotifyApiClient`,
  :player; Tokens in privaten SharedPreferences) und holt die App nach vorn. Das `state` schützt vor
  untergeschobenen Codes; Token werden automatisch erneuert.
- **Lesen:** `GET /playlists/{id}/items` in Seiten zu 50 bis max. 1.000 Titel (`SpotifyApiJson`,
  getestet für neues `item`- und älteres `track`-Feld, Episoden/leere Einträge übersprungen); 429 wird
  abgewartet, 401 einmal mit erneuertem Token wiederholt.
- **Einschränkung von Spotify (seit März 2026):** Entwickler-Apps bekommen die Titel nur für
  Playlists, die dem Nutzer **gehören oder bei denen er mitarbeitet**; der Besitzer der App braucht
  Premium. Bei fremden Playlists antwortet die API mit 403 – die App fällt dann auf die öffentliche
  Seite zurück (max. ~100 Titel) und nennt den Grund in der Meldung.
- Der Import selbst (Blöcke zu 100, Stand, Nachtrag) bleibt unverändert.
- **Nicht verifiziert** (kein Netz zu Spotify hier): der ganze Anmelde-Ablauf auf dem Gerät, das
  genaue Antwortformat von `/items`, ob Spotify für diese Entwickler-App 50 je Seite erlaubt.
