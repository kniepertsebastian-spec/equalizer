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

## Weiterhin offen (aus Roadmap §17, unverändert)

- [ ] Finaler App-Name und Package-ID
- [ ] Ausschließlich Session-Controller oder langfristig zusätzlich eigener Player
- [x] Physisches Testgerät: Google Pixel 10 / Android 16 (M0-Spikes getestet).
- [ ] Weitere Hersteller und Emulator für die Testmatrix verfügbar machen.
- [ ] Soll die erste öffentliche Version AutoEQ-Import enthalten oder erst Post-MVP?
- [ ] Open-Source-Lizenz und Veröffentlichungsmodell
- [ ] Monetarisierung – bewusst erst nach technischem MVP entscheiden

## Nächste konkrete Aufgabe

Android Lint für Debug und Release ist in PR #7 erfolgreich eingerichtet;
als Nächstes Formatierung und detekt mit geprüften, kompatiblen Versionen
ergänzen. Design-System-Tokens und Capability-Fakes bleiben ebenfalls offen.

Die M0-Restpunkte (Emulator, hörbare reversible Änderung/Bypass und
schriftliche MVP-Backend-/Fallback-Entscheidung) bleiben ausdrücklich offen.
Ein erfolgreicher JVM-Build ersetzt keine Audio-Laufzeitprüfung am Gerät.
