# Offene und getroffene Entscheidungen

Wird am Ende jeder Arbeitssession aktualisiert (siehe `roadmap.md` §15,
Punkt 10).

## In dieser Session getroffen (vorläufig, leicht änderbar)

- **Package-ID / Namespace:** `com.hardbasseq.eq`, abgeleitet vom Arbeitstitel
  „HardBass EQ“. Noch nicht final (siehe Roadmap §17, „Finaler App-Name und
  Package-ID“ bleibt offen). Zentrale Konfigurierbarkeit des Paketnamens ist
  laut Roadmap explizit **M1**-Aufgabe, nicht M0.
- **Modulstruktur:** Einzelnes `app`-Modul, wie in Roadmap §7 für den ersten
  Commit ausdrücklich erlaubt. Trennung Audio-API/Android-Backend erfolgt vor
  M2.
- **Versionswahl (AGP, Kotlin, Compose BOM, SDK-Level):** siehe
  `docs/DEPENDENCIES.md`.
- **Kein Hilt/Room/DataStore/Navigation in diesem Durchlauf:** Diese gehören
  laut Roadmap zu M1 und wurden bewusst nicht vorgezogen, um M0 nicht zu
  überladen.
- **Keine `MODIFY_AUDIO_SETTINGS`-Berechtigung im Manifest:** `AudioEffect.queryEffects()`
  ist eine statische, berechtigungsfreie Abfrage. Berechtigungen werden erst
  ergänzt, wenn ein konkreter, implementierter Anwendungsfall (Session-Attach
  in M2) sie tatsächlich benötigt (Roadmap §12).

## Weiterhin offen (aus Roadmap §17, unverändert)

- [ ] Finaler App-Name und Package-ID
- [ ] Ausschließlich Session-Controller oder langfristig zusätzlich eigener Player
- [ ] Welche physischen Testgeräte stehen zur Verfügung?
- [ ] Soll die erste öffentliche Version AutoEQ-Import enthalten oder erst Post-MVP?
- [ ] Open-Source-Lizenz und Veröffentlichungsmodell
- [ ] Monetarisierung – bewusst erst nach technischem MVP entscheiden

## Neu aufgeworfene offene Frage

- [ ] CI-Verifikation dieses ersten Durchlaufs steht noch aus (siehe
      `docs/FEASIBILITY.md`), da diese Sandbox keinen Zugriff auf
      `dl.google.com` hat und daher kein lokaler Android-Build möglich war.
