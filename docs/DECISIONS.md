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
