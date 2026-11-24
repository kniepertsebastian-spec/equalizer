# Roadmap 18 – Weniger „Knall“ im eigenen Player

**Priorität:** Hoch (Hörbeschwerde: der eigene Player ist im Bass zu kräftig, die Presets haben zu viel Knall)  
**Ziel:** Die Player-Presets aus Roadmap 15 moderat entschärfen, ohne den Player leiser oder dumpfer zu machen.

**Abhängigkeit:** Roadmap 15 und 16 (Player-Presets, Quellenerkennung).

---

## Ausgangslage / Befund

Rechnerisch nachvollzogen (Formeln aus `EqualizerInterpolator`, `MbcTuning`, `PresetIntensityBoost`;
nicht am Gerät gemessen):

1. **Der Kick-Bereich trug den ganzen Bass.** Der Tiefbass unter 50 Hz wird vom Subsonic-Filter
   (35–42 Hz, 24 dB/Oktave) stark weggenommen, bei 80–125 Hz blieben aber +2 bis +3 dB stehen
   (Kurvenpunkt + Punch-Makro, das 70–160 Hz gleichmäßig anhebt). Zusammen mit der Senke bei
   250–300 Hz lag der Kick 5–7 dB über den tiefen Mitten und bis zu 4,2 dB über 1 kHz.
2. **„Mehr Intensität“ galt voll auch für Player-Presets** (+2,5 dB Bass, +1,5 dB Punch). Mit dem
   Schalter lag der Kick sogar über dem Spotify-Preset (Uptempo: +7,2 dB statt +6,1 dB über 1 kHz).
3. **„Laut“ im Player gab zusätzlich Kick:** +0,5 dB bei 95 Hz und Punch +0,2.
4. **MBC-Make-up:** Die tieferen MBC-Schwellen der Player-Presets (−9 bis −11 dB statt −7 dB)
   geben den Bassbändern mehr Make-up (+2,5 bis +3 dB statt +1,9 dB).
5. **Keine Lautheitsangleichung im Player:** laute Masters (≈ −5 LUFS) kommen rund 9 dB heißer an als
   Spotify (−14 LUFS); Kompressor, Soft-Clipper und Limiter arbeiten bei fast jedem Kick.

## Vorgaben des Nutzers

1. MBC-Make-up **bleibt wie es ist** – die Lautstärke lässt den Player weniger dumpf wirken.
2. Kurven entschärfen, aber **nicht so stark** wie zunächst vorgeschlagen (Kick auf ≈ +1 dB,
   Punch 0,3–0,6, Senke halbieren).
3. „Mehr Intensität“ im eigenen Player **halbieren**.
4. Extra-Kick bei „Laut“ **abschaltbar**, Standard **an** („so hat man Macht darüber“).
5. Lautheitsangleichung, wenn überhaupt, nur **mild** – der Player war früher zu leise.

---

## Umsetzung

### P18-1 – Player-Basen: Kick −25 %, Punch −35 %
`BuiltInPlayerPresets.kt`: In jeder der 13 Player-Basen sinken die Kurvenpunkte im Kick-Bereich
(80–170 Hz, positiv) um 25 % und das Punch-Makro um 35 % (auf 0,1 dB gerundet). Die Senke bei
250–300 Hz bleibt bewusst stehen (sie nimmt den Mulm, ohne sie würde es dumpfer); sie wird nur über
das kleinere Punch-Makro (−Punch/2 bei 220–360 Hz) minimal flacher. Etwa der halbe Weg zum
ursprünglichen Vorschlag.

| Player-Genre | Kick-Punkt(e) alt → neu | Punch alt → neu | Kick über 1 kHz alt → neu |
| :--- | :--- | :--- | :--- |
| Uptempo | 105 Hz: 1,5 → 1,1 | 1,0 → 0,7 | 3,4 → 2,7 dB |
| Gabber | 120 Hz: 2,0 → 1,5 | 1,2 → 0,8 | 3,0 → 2,1 dB |
| Frenchcore | 100 Hz: 1,8 → 1,4 | 1,5 → 1,0 | 3,8 → 2,9 dB |
| Early | 90 Hz: 1,2 → 0,9 | 1,0 → 0,7 | 2,6 → 2,0 dB |
| Terror | 90 Hz: 1,0 → 0,8 | 0,5 → 0,3 | 4,1 → 3,7 dB |
| Dance | 120 Hz: 0,8 → 0,6 | 0,4 → 0,3 | 1,9 → 1,7 dB |
| Rap | 90 Hz: 1,0 → 0,8 | 0,3 → 0,2 | 1,6 → 1,4 dB |
| Pop | 150 Hz: 0,3 → 0,2 | 0,2 → 0,1 | 0,8 → 0,7 dB |
| Hardtekk | 95 Hz: 2,0 → 1,5, 150 Hz: 1,0 → 0,8 | 1,2 → 0,8 | 4,1 → 3,2 dB |
| Techno | 90 Hz: 1,2 → 0,9 | 0,6 → 0,4 | 2,3 → 1,8 dB |
| Hard Techno | 100 Hz: 1,6 → 1,2 | 1,0 → 0,7 | 3,5 → 2,9 dB |
| Industrial Techno | 90 Hz: 1,2 → 0,9 | 0,8 → 0,5 | 2,9 → 2,3 dB |
| Schranz | 105 Hz: 1,8 → 1,4, 160 Hz: 0,8 → 0,6 | 1,2 → 0,8 | 4,2 → 3,4 dB |

„Kick über 1 kHz“ = stärkster Wert von Kurve + Makros zwischen 80 und 170 Hz minus dem Wert bei
1 kHz, ohne Subsonic-Filter und ohne MBC. Terror liegt hoch, weil seine Mitten bei 1 kHz selbst
abgesenkt sind. Die Use Cases (Loud, Quiet, In-Ear, Auto, BT-Box) erben die neuen Werte, weil sie
Deltas auf der Basis sind.

Nebeneffekt, gewollt: Die Vorab-Absenkung im Player (die Hälfte des stärksten Boosts) wird kleiner,
weil der stärkste Boost sinkt – bei Player-Uptempo −1,00 statt −1,34 dB. Alles außer dem Kick spielt
dadurch etwa 0,3 dB lauter; der Player wird nicht leiser.

Ein unbearbeitetes, gespeichertes Player-Preset übernimmt die neuen Makros beim nächsten App-Start
(`EqController`-Restore); manuelle Änderungen (dirty) bleiben wie gespeichert.

### P18-2 – „Mehr Intensität“ halb für den eigenen Player
`PresetIntensityBoost`: `shareFor(preset)` = 0,5 für `OWN_PLAYER`-Presets, 1 für alle anderen,
0 für neutrale. `apply()` skaliert alle vier Deltas (Bass +1,25, Punch +0,75, MBC-Schwelle −0,75 dB,
Ratio +0,2). Neu `macroBassDeltaDb(preset)` / `macroPunchDeltaDb(preset)`; `EqController` zieht beim
Speichern eines eigenen Presets genau den Anteil ab, den das aktive Preset bekommen hat.

### P18-3 – Schalter „Extra kick at Loud“ (Standard an)
- `GenreUseCaseTuning.applyForPlayer(genre, useCase, loudExtraKick = true)`; ohne Extra-Kick ist der
  95-Hz-Punkt des Laut-Deltas 0 dB und Punch +0 statt +0,2 (`PLAYER_LOUD_EXTRA_KICK_DB`,
  `PLAYER_LOUD_EXTRA_PUNCH`). Alles andere am Laut-Preset (Sub-, 260-Hz- und Höhenabsenkung, MBC,
  Limiter, Subsonic) bleibt.
- `BuiltInPlayerPresets.withoutLoudExtraKick(preset)` / `hasLoudExtraKick(preset)`: für die 13
  Player-Laut-Varianten und die der player-only Genres aus RM 14 (`genre_<x>_use_loud`).
- `EqController`: `loudExtraKick`-State, `setLoudExtraKick()`, angewandt auf Kurve und Makros des
  aktiven Presets; persistiert in `LiveSettings.loudExtraKick` (Default `true`, alte Daten lesbar).
- UI: Schalter unter „More intensity“ im Sound-Tab, nur sichtbar, wenn eine Player-Laut-Variante
  aktiv ist.

### P18-4 – Milde Lautheitsangleichung
Noch nicht umgesetzt; Vorschlag zur Freigabe siehe „Offen“.

---

## Nachweise
- `:core`: `BuiltInPlayerPresetsTest` (Tabelle mit neuen Werten, Kick −25 %/Punch −35 % gegenüber
  RM 15 für alle 13 Genres, Kick über 1 kHz < 3,8 dB, Laut-Schalter nur für Player-Laut-Varianten),
  `PresetIntensityBoostTest` (halbe Stufe für alle Player-Presets, volle für andere, keine für Flat).
- `:app`: `EqControllerTest` (Laut-Schalter ändert Punch und Player-Kurve bei 95 Hz, nicht den Sub,
  wird gespeichert; ändert nichts bei anderen Presets; halbe Intensität und Speichern ohne Stufe;
  Restore mit neuen Makros, dirty bleibt), `EqualizerScreenUiTest` (Schalter nur bei Laut),
  Golden `player-and-compare` neu aufgenommen (nur Punch 1,0 → 0,7 und daraus folgende Gains).

- Lokal (Cloud-Container, diesmal mit Android-SDK, also auch `:app`): `:core:test` 511/511,
  `:app:testFullDebugUnitTest` 581/581, `:app:testStoreDebugUnitTest` 352/352, `:core:ktlintCheck`
  und `:app:ktlintCheck` grün. Ein Robolectric-Test (`ScannedSessionsTest`) schlug im ersten Lauf
  nur beim Herunterladen des Android-JARs fehl und lief bei Wiederholung grün.

## Offen
- **Hörprobe am Gerät**, v. a. Uptempo/Frenchcore/Hardtekk/Schranz auf Boxen, mit und ohne
  „Mehr Intensität“ und mit „Laut“ an/aus.
- **Character-Varianten der RM-14-Genres** (je 2 für Hardtekk, Techno, Hard Techno, Industrial
  Techno, Schranz) stehen ebenfalls in der Player-Liste und sind deutlich bassiger als die
  Player-Basen (z. B. Hard Techno „Dry Punch“: 95 Hz +3,2 dB, Punch 2,5). Nicht angefasst, da nicht
  Teil der Vorgabe – bei Bedarf gleiche Regel anwenden.
- **P18-4 milde Lautheitsangleichung (Vorschlag):** nur sehr heiße Titel leicht absenken, nie
  anheben – z. B. oberhalb −8 LUFS die Hälfte des Überschusses, höchstens −3 dB, langsam
  eingeblendet. Normale Titel (−9 bis −14 LUFS) bleiben unverändert laut. Braucht eine
  Lautheitsmessung im Player (BS.1770, laufend ab Titelstart), weil es keine Vorab-Analyse für
  Streams gibt; Nebenwirkung: bei heißen Titeln sinkt der Pegel in den ersten Sekunden um bis zu 3 dB.

## Umsetzungsstand
**Commit:** `651972d`

**Status:** P18-1 bis P18-3 umgesetzt, P18-4 wartet auf Freigabe, Hörprobe offen.
