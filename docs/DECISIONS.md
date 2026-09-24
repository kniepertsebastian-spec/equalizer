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
