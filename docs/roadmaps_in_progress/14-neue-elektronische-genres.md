# Roadmap 14 – Fünf neue elektronische Genres (Hardtekk, Techno, Hard Techno, Industrial Techno, Schranz) (umgesetzt)

**Priorität:** Hoch (Genrevollständigkeit im Preset-Katalog)  
**Ziel:** Die fünf elektronischen Genres **Hardtekk**, **Techno**, **Hard Techno**, **Industrial Techno** und **Schranz** als vollwertige `GenrePreset`-Gruppen in `:core` ergänzen — jeweils mit Basis-Kurve („Moderate“ für Spotify/System-EQ), zwei handabgestimmten Character-Varianten, automatischen Use-Case-Varianten, Beschreibungen und visueller Design-Zuordnung.  
**Abhängigkeit:** Keine (**Welle 1** – sofort startbar, 100 % parallel zu **Roadmap 17**). Muss vor **Roadmap 15** abgeschlossen sein.

---

## Ausgangslage / Befund

1. `BuiltInGenrePresets.kt` deckt derzeit Terror, Uptempo, Gabber, Early, Frenchcore, Dance, Pop, Rap und Flat ab. Die fünf zentralen Techno-/Tekk-Subgenres fehlen vollständig.
2. `BuiltInGenreVariants.kt` liefert für jedes bestehende Genre genau zwei handabgestimmte Character-Varianten plus die fünf automatisch abgeleiteten `GenreUseCase`-Varianten. Die neuen Genres sollen exakt demselben Qualitätsstandard folgen (kein Gesamt-Boost ≥ 9 dB inkl. Macros, Headroom passend zum stärksten Boost, Höhen bei stark verzerrten Genres gezähmt statt weiter angehoben).
3. `PresetDesign.kt` ordnet bisher nur `terror`, `uptempo`, `hard-dance`, `frenchcore`, `hardcore` und `gabber` zu; neue Techno-/Tekk-Genres würden sonst auf `FLAT` zurückfallen.

---

## Zugewiesener Dateiumfang

- `core/src/main/kotlin/com/auracurve/app/preset/BuiltInGenrePresets.kt`
- `core/src/main/kotlin/com/auracurve/app/preset/BuiltInGenreVariants.kt`
- `core/src/main/kotlin/com/auracurve/app/preset/GenrePresetGroups.kt` *(nur `baseDescriptions` für die 5 neuen Genres)*
- `core/src/main/kotlin/com/auracurve/app/preset/PresetDesign.kt`
- Zugehörige Unit-Tests in `core/src/test/kotlin/com/auracurve/app/preset/`

*Nicht anfassen:* `Preset.kt`, `GenreUseCase.kt`, `EqController.kt`, UI-Dateien in `:app` (gehören zu RM 15 / RM 16 / RM 17).

---

## Tuning-Vorgaben (Spotify / Standard-Basis „Moderate“)

| Genre | ID | Zielkurve (`Hz: dB`) | Bass / Punch / Härte | Headroom | MBC Thresh / Ratio | Limiter | Subsonic |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Hardtekk** | `genre_hardtekk` | `50: +2.0`, `95: +3.5`, `150: +1.5`, `300: -3.0`, `2500: +1.5`, `5000: -0.5`, `10000: -0.5` | `0.8` / `2.5` / `0.8` | `4.5 dB` | `-7.0 dB` / `3.0:1` | `-0.3 dB` | `30 Hz` |
| **Techno** | `genre_techno` | `45: +3.0`, `90: +2.0`, `250: -1.5`, `1200: 0.0`, `3500: +0.5`, `8000: +1.0`, `13000: +0.5` | `1.2` / `1.2` / `0.2` | `3.5 dB` | `-8.0 dB` / `2.2:1` | `-0.3 dB` | `25 Hz` |
| **Hard Techno** | `genre_hard_techno` | `48: +3.5`, `100: +2.5`, `280: -2.5`, `1800: +0.5`, `3500: +1.0`, `6500: -1.0`, `11000: +0.5` | `1.5` / `2.0` / `0.5` | `4.5 dB` | `-7.5 dB` / `2.8:1` | `-0.3 dB` | `28 Hz` |
| **Industrial Techno** | `genre_industrial_techno` | `42: +3.8`, `90: +2.0`, `280: -3.0`, `1500: +0.5`, `3500: -1.0`, `6000: -1.8`, `11000: +1.0` | `1.5` / `1.5` / `-0.5` | `5.0 dB` | `-9.0 dB` / `3.2:1` | `-0.5 dB` | `28 Hz` |
| **Schranz** | `genre_schranz` | `50: +2.0`, `105: +3.2`, `160: +1.0`, `300: -3.5`, `2000: +0.5`, `4000: -1.5`, `7000: -1.5`, `12000: -0.5` | `0.8` / `2.5` / `-0.5` | `4.5 dB` | `-9.5 dB` / `3.4:1` | `-0.5 dB` | `32 Hz` |

### Vorgesehene Character-Varianten (`BuiltInGenreVariants.kt`)

- **Hardtekk:**
  - `hollow` (*Hollow Kick*): Kick-Körper bei 100–140 Hz (`+3.8 dB`) + trockener 2,8-kHz-Klick, tiefer 300-Hz-Scoop.
  - `dark` (*Dark Floor*): Wärmerer 60-Hz-Körper (`+3.0 dB`), entschärfte obere Mitten für lange Sets.
- **Techno:**
  - `rumble` (*Hypnotic Rumble*): Tiefer 45–65-Hz-Sub-Rumble (`+3.8 dB`), weichere Höhen für Clubs/Kopfhörer.
  - `peak` (*Peak Time*): Straffer 100-Hz-Kick (`+2.8 dB`) und klare 8-kHz-Hi-Hat-Präsenz.
- **Hard Techno:**
  - `warehouse` (*Rave Warehouse*): Breiter 45–60-Hz-Sub-Schub (`+3.8 dB`), 4-kHz-Stabs sanft gezähmt (`-1.0 dB`).
  - `dry` (*Dry Punch*): Kurzer, trockener 95-Hz-Kick (`+3.2 dB`), wenig Sub-Nachhall für schnelle 150+ BPM.
- **Industrial Techno:**
  - `heavy` (*Heavy Warehouse*): Maximaler dunkler Sub-Fokus bei 40–55 Hz (`+4.0 dB`), 300 Hz tief ausgeschnitten.
  - `tamed` (*Tamed Metal*): Zusätzliche Entschärfung bei 2,5–7 kHz (`-2.0` bis `-2.8 dB`) gegen harsche metallische Distortion.
- **Schranz:**
  - `grinder` (*Loop Grinder*): Trockener 110-Hz-Punch (`+3.8 dB`) und harter 300-Hz-Scoop (`-3.8 dB`), damit dichte Loops nicht vermatschen.
  - `session` (*Long Session*): Entspannte Höhen (3–9 kHz abgesenkt) und straffer MBC (`-10.0 dB`, `3.5:1`) für dauerhaftes Hören.

---

## Arbeitspakete

### P14-1 – Genre-Basen in `BuiltInGenrePresets.kt`
- [x] `Hardtekk`, `Techno`, `HardTechno`, `IndustrialTechno` und `Schranz` als `GenrePreset` gemäß Tabelle anlegen (mit `metadata = PresetMetadata(genre = ..., builtIn = true)`).
- [x] In `BuiltInGenrePresets.all` an sinnvoller Stelle (elektronische Genres zusammenhängend, `Flat` bleibt am Ende) einreihen.
- [x] Prüfen, dass `BuiltInGenrePresets.allResolvedPresets` und `BuiltInPresets.all` alle neuen Kombinationen automatisch enthalten.

### P14-2 – Character-Varianten in `BuiltInGenreVariants.kt`
- [x] Je 2 handabgestimmte `GenreVariant`-Einträge für alle 5 neuen Genres in `styles` ergänzen, jeweils mit klarer „Wann ist das die beste Wahl?“-Beschreibung im Stil der bestehenden Einträge.
- [x] Sicherstellen, dass kein Preset inkl. Macros ≥ 9 dB Gesamt-Boost an einem Punkt der Kurve erreicht (bestehende Invariante in `:core`-Tests prüfen).

### P14-3 – Gruppenbeschreibungen & visuelles Design
- [x] `GenrePresetGroups.baseDescriptions` um alle 5 neuen Genre-IDs ergänzen.
- [x] `PresetDesign.forPreset()` erweitern, sodass `hardtekk`, `schranz`, `industrial`, `hard-techno` / `hard techno` und `techno` auf passende Designs gemappt werden statt auf `FLAT`.

---

## Abnahmekriterien & Verifikation

- [x] **Unit-Tests `:core`:**
  - Alle 5 neuen Genres sind über `BuiltInGenrePresets.all`, `BuiltInPresets.all` und `GenrePresetGroups.all` auffindbar.
  - Keine doppelten Preset-IDs über `BuiltInPresets.all`.
  - Bestehende Headroom-/Max-Boost-Tests in `:core` bestehen für alle neuen Basen, Character-Varianten und automatisch abgeleiteten `GenreUseCase`-Varianten.
  - `PresetDesign.forPreset()` ordnet keines der 5 neuen Genres fälschlich `FLAT` zu.
- [x] **Build & Lint:**
  ```bash
  ./gradlew :core:test :core:ktlintCheck
  ```

---

## Umsetzungsstand

**Status:** umgesetzt, Hörprobe am Gerät offen.

### Geänderte Dateien
- `core/.../preset/BuiltInGenrePresets.kt` – `Hardtekk`, `Techno`, `HardTechno`, `IndustrialTechno`,
  `Schranz` exakt nach Tuning-Tabelle (Kurve, Macros, Headroom, MBC, Limiter, Subsonic). Reihenfolge
  in `all`: Terror, Uptempo, Gabber, Early, Frenchcore, Dance, **Hardtekk, Techno, Hard Techno,
  Industrial Techno, Schranz**, Pop, Rap, Flat.
- `core/.../preset/BuiltInGenreVariants.kt` – je zwei Character-Varianten (`hollow`/`dark`,
  `rumble`/`peak`, `warehouse`/`dry`, `heavy`/`tamed`, `grinder`/`session`) mit „Wann ist das die
  beste Wahl?“-Text. Der Helfer `preset()` nimmt optional eine eigene Subsonic-Ecke an (Standard:
  die des Genres).
- `core/.../preset/GenrePresetGroups.kt` – `baseDescriptions` für die fünf Genres.
- `core/.../preset/PresetDesign.kt` – Zuordnung der neuen Genres.
- Tests: `ElectronicGenrePresetsTest.kt` (neu), `BuiltInGenrePresetsTest.kt` (14 Genres, 13 mit
  Intensitätsachse), `PresetDesignTest.kt` (Erwartungen für die fünf Genre-IDs).

### Entscheidungen
- **Design ohne neue Enum-Werte:** `PresetDesign` wird derzeit in `:app` nicht referenziert; neue
  Werte wären reine Vorratsarbeit. Die Genres nutzen die vorhandenen Looks:
  Industrial Techno & Schranz → `TERRORCORE` (dunkel, verzerrt), Hardtekk → `GABBER` (roher
  Kick-Körper), Hard Techno → `UPTEMPO_HARDCORE` (schnell, harter Kick), Techno → `HARD_DANCE`
  (Club, Four-on-the-floor). Die spezifischeren Schlüssel stehen vor `"techno"`.
- **Metadaten-Genres:** `hardtekk`, `techno`, `hard-techno`, `industrial-techno`, `schranz` – keiner
  enthält `hardcore`/`terror`/`gabber` und fällt dadurch versehentlich in ein altes Design.
- **Subsonic & Limiter** stehen in der Basis; die automatischen Use-Case-Varianten überschreiben sie
  wie bei allen Genres (`GenreUseCase` gehört zu RM 15 und wurde nicht angefasst). `Hard Techno – Dry
  Punch` und `Schranz – Loop Grinder` setzen 35 Hz für weniger Sub-Nachhall.

### Nachweise
- Stärkster Boost inkl. Macros (Basis Moderate, Character- und Use-Case-Varianten) < 9 dB – geprüft
  in `ElectronicGenrePresetsTest` und dem bestehenden `GenrePresetGroupsTest`.
- Angeforderter Headroom liegt für Basis und Character-Varianten zwischen 50 % des stärksten Boosts
  und Boost + 1 dB (gleiche Spanne wie bei den bestehenden Genres).
- Industrial Techno & Schranz: 4 und 6 kHz in Basis und Varianten abgesenkt, Härte-Macro ≤ 0.
- `:core:test` (485 Tests, 0 Fehler) und `:core:ktlintCheck` grün.
  Hinweis: Im Cloud-Container ist `dl.google.com` gesperrt, das Root-Projekt lädt deshalb das
  Android-Gradle-Plugin nicht. `:core` wurde über ein temporäres Settings-Projekt gebaut, das nur
  `:core` mit demselben Version-Catalog einbindet; volle Bestätigung über die CI.

### Offen
- Hörprobe der zehn Character-Varianten am Gerät (Kopfhörer, BT-Box, Auto).
- RM 15 baut darauf auf (Player-Presets / Use-Case-Äquivalente).

**Commit:** `557947b`
