# Roadmap 15 – Quellengetrennte Presets (`PresetSourceScope`), Player-Presets & Use-Case-Äquivalente

**Priorität:** Kritisch (Boxenschutz im eigenen Player, klangliche Parität der Use Cases zwischen Player & Spotify)  
**Ziel:**  
1. Das Preset-Datenmodell abwärtskompatibel um `PresetSourceScope` (`ALL`, `OWN_PLAYER`, `EXTERNAL_APPS`) erweitern.  
2. Für alle 13 Genres (8 bestehende + 5 neue aus Roadmap 14; `Flat` bleibt `ALL`) boxensichere **Eigener-Player-Presets** bereitstellen (reduzierter Tiefbass, verpflichtender Subsonic-Hochpass, `virtualBassMix = 0f`, defensiver Limiter `-1.0 dB`).  
3. Vollständige **Player-Use-Case-Äquivalente** in `GenreUseCaseTuning` bereitstellen — insbesondere für:
   - **Kräftiger Bass bei lauter Musik** (`LOUD`)
   - **Moderat bei leiser Musik** (`QUIET`)
   - **Besser bei In-Ears** (`HEADPHONES`)
   - sowie `CAR` und `BLUETOOTH_SPEAKER`.

**Abhängigkeit:** **Roadmap 14** abgeschlossen (**Welle 2** – parallel zu **Roadmap 17**). Voraussetzung für **Roadmap 16**.

---

## Ausgangslage / Befund

1. Im eigenen Player laufen `CurveEqualizer` (29 Terz-Biquads, `headroomSafetyRatio = 0.5f`), `BassMonoSummer` (120 Hz), `SoftClipper` (-2,5 dBFS), optional `BassExciter` und danach zusätzlich Androids 5-Band-MBC + Limiter (`AndroidAudioEngine`). Zudem greift keine Spotify-Lautheitsnormalisierung.
2. Presets aus `BuiltInGenrePresets.kt`, die für Spotify optimal klingen, erzeugen im eigenen Player zu viel Tiefton-Hub (ohne Subsonic-Cut: `subsonicCutoffHz = 0f`, Limiter `-0.3 dB`) und gefährden Lautsprecherboxen.
3. Aktuell gibt es im Datenmodell (`Preset.kt`) keine Unterscheidung, ob ein Preset für den eigenen Player oder für Spotify / externe Apps abgestimmt ist.

---

## Zugewiesener Dateiumfang

- `core/src/main/kotlin/com/auracurve/app/preset/Preset.kt`
- Neu: `core/src/main/kotlin/com/auracurve/app/preset/BuiltInPlayerPresets.kt`
- `core/src/main/kotlin/com/auracurve/app/preset/GenreUseCase.kt`
- `core/src/main/kotlin/com/auracurve/app/preset/BuiltInPresets.kt`
- `core/src/main/kotlin/com/auracurve/app/preset/GenrePresetGroups.kt` *(Quellen-Filter / Gruppen nach `PresetSourceScope`)*
- `core/src/main/kotlin/com/auracurve/app/data/preset/PresetJsonSerializer.kt` & `PresetShareCodec.kt`
- Unit-Tests in `core/src/test/kotlin/com/auracurve/app/preset/` und `.../data/preset/`

*Nicht anfassen:* `EqController.kt`, ViewModels oder Compose-Screens in `:app` (gehören zu RM 16 / RM 17).

---

## Tuning-Vorgaben

### A. Boxensichere Player-Basis-Presets (`OWN_PLAYER`, alle `virtualBassMix = 0.0f`)

> **Roadmap 18:** Kick-Punkte (80–170 Hz) −25 % und Punch −35 % gegenüber dieser Tabelle, „Mehr Intensität“ für Player-Presets halbiert, Laut-Extra-Kick abschaltbar – siehe [18-player-knall-entschaerfen.md](18-player-knall-entschaerfen.md).

| Genre (Player) | Zielkurve (`Hz: dB`) | Bass / Punch / Härte | Headroom | MBC Thresh / Ratio | Limiter | Subsonic |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Uptempo** | `55: +1.2`, `105: +1.5`, `280: -2.0`, `3500: +0.8`, `8500: -1.0` | `0.3` / `1.0` / `0.3` | `4.0 dB` | `-9.0 dB` / `2.6:1` | `-1.0 dB` | `38 Hz` |
| **Gabber** | `50: +1.0`, `120: +2.0`, `300: -2.5`, `1200: +0.8`, `5000: -0.5` | `0.2` / `1.2` / `0.3` | `4.0 dB` | `-9.0 dB` / `2.8:1` | `-1.0 dB` | `40 Hz` |
| **Frenchcore** | `50: +1.0`, `100: +1.8`, `260: -2.5`, `2000: +0.8`, `4500: +1.0`, `9000: -1.0` | `0.2` / `1.5` / `0.8` | `4.5 dB` | `-8.5 dB` / `3.0:1` | `-1.0 dB` | `40 Hz` |
| **Early** | `45: +1.5`, `90: +1.2`, `300: -2.0`, `1500: +0.8`, `5000: +0.3` | `0.4` / `1.0` / `0.5` | `4.0 dB` | `-9.0 dB` / `2.6:1` | `-1.0 dB` | `36 Hz` |
| **Terror** | `50: +1.5`, `90: +1.0`, `300: -3.5`, `2500: -1.2`, `6000: -2.0`, `12000: -1.0` | `0.3` / `0.5` / `-1.0` | `5.0 dB` | `-11.0 dB` / `3.2:1` | `-1.2 dB` | `42 Hz` |
| **Dance** | `65: +1.0`, `120: +0.8`, `300: -1.5`, `4000: +0.5` | `0.0` / `0.4` / `0.2` | `3.0 dB` | `-8.0 dB` / `2.0:1` | `-1.0 dB` | `35 Hz` |
| **Rap** | `50: +1.5`, `90: +1.0`, `250: -1.0`, `2500: +0.8`, `5000: +0.4`, `9000: -0.5` | `0.3` / `0.3` / `0.0` | `3.5 dB` | `-9.5 dB` / `2.2:1` | `-1.0 dB` | `35 Hz` |
| **Pop** | `60: +0.8`, `150: +0.3`, `1000: 0.0`, `3000: +0.2`, `10000: +0.8` | `0.0` / `0.2` / `0.1` | `2.0 dB` | `-9.0 dB` / `1.8:1` | `-1.0 dB` | `30 Hz` |
| **Hardtekk** | `50: +0.8`, `95: +2.0`, `150: +1.0`, `300: -3.0`, `2500: +1.0`, `5000: -0.8`, `10000: -0.5` | `0.2` / `1.2` / `0.4` | `4.0 dB` | `-9.0 dB` / `3.0:1` | `-1.0 dB` | `40 Hz` |
| **Techno** | `45: +1.2`, `90: +1.2`, `250: -1.5`, `1200: 0.0`, `3500: +0.4`, `8000: +0.8`, `13000: +0.5` | `0.3` / `0.6` / `0.1` | `3.5 dB` | `-9.5 dB` / `2.2:1` | `-1.0 dB` | `35 Hz` |
| **Hard Techno** | `48: +1.5`, `100: +1.6`, `280: -2.5`, `1800: +0.4`, `3500: +0.6`, `6500: -1.2`, `11000: +0.4` | `0.4` / `1.0` / `0.2` | `4.5 dB` | `-9.5 dB` / `2.8:1` | `-1.0 dB` | `38 Hz` |
| **Industrial Techno** | `42: +1.6`, `90: +1.2`, `280: -3.0`, `1500: +0.4`, `3500: -1.2`, `6000: -2.0`, `11000: +0.8` | `0.4` / `0.8` / `-0.5` | `4.5 dB` | `-10.5 dB` / `3.2:1` | `-1.0 dB` | `38 Hz` |
| **Schranz** | `50: +0.8`, `105: +1.8`, `160: +0.8`, `300: -3.5`, `2000: +0.4`, `4000: -1.8`, `7000: -1.5`, `12000: -0.5` | `0.2` / `1.2` / `-0.5` | `4.5 dB` | `-11.0 dB` / `3.4:1` | `-1.2 dB` | `42 Hz` |

### B. Player-Use-Case-Äquivalente (Deltas auf der jeweiligen Player-Basis)

| Use Case | Zielkurven-Delta (`Hz: dB`) | Macro-Delta | MBC | Limiter | Subsonic | Loudness / Virtual Bass |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **LOUD** *(Kräftiger Bass bei lauter Musik)* | `35: -1.5`, `55: -0.5`, `95: +0.5`, `260: -1.0`, `3000: -1.2`, `4500: -1.5`, `8000: -0.5` | `Bass -0.3`, `Punch +0.2`, `Härte -0.6` | `-2.0 dB` / `+0.5` | `-1.2 dB` | `max(base, 42 Hz)` | `0 dB` / `0.0` |
| **QUIET** *(Moderat bei leiser Musik)* | `45: +0.8`, `80: +0.5`, `150: 0.0`, `6000: 0.0`, `12000: +1.0` | unverändert | `+1.0 dB` / `-0.2` | `-0.8 dB` | `30 Hz` | `7.5 dB` / `0.0` |
| **HEADPHONES** *(Besser bei In-Ears)* | `30: +1.2`, `55: +0.8`, `120: 0.0`, `300: -0.5`, `3000: -0.8`, `6500: -1.8`, `9000: -0.5`, `12000: +0.8` | `Bass +0.3`, `Punch 0.0`, `Härte ≤ 0.0` | `+1.5 dB` / `-0.3` | `-0.6 dB` | `22 Hz` | `0 dB` / `0.0` |
| **CAR** *(Player im Auto)* | Analog `CAR`, aber Sub < 60 Hz gedeckelt, damit Kabinen-Gain + Player-EQ nicht dröhnen | `Bass -0.3` | `-1.0 dB` / `+0.3` | `-1.0 dB` | `36 Hz` | `6.0 dB` / `0.0` |
| **BLUETOOTH_SPEAKER** *(Player auf kleiner BT-Box)* | `< 75 Hz` auf `<= 0 dB` gekappt, Kick 100–130 Hz `+1.2 dB`, 400 Hz `-1.5 dB` | `Bass * 0.25` | `-2.0 dB` / `+0.5` | `-1.2 dB` | `65 Hz` | `6.0 dB` / `0.45` |

---

## Arbeitspakete

### P15-1 – `PresetSourceScope` im Datenmodell & Serialisierung
- [x] In `Preset.kt`:
  ```kotlin
  @Serializable
  enum class PresetSourceScope { ALL, OWN_PLAYER, EXTERNAL_APPS }
  ```
- [x] `PresetMetadata` um `val sourceScope: PresetSourceScope = PresetSourceScope.ALL` erweitern (Default sichert 100 % Abwärtskompatibilität für alte Room-Daten, JSON-Exporte und Share-Links).
- [x] Bestehende Spotify-/System-Genre-Presets (`BuiltInGenrePresets`, `BuiltInGenreVariants`, Legacy-Presets in `BuiltInPresets`) auf `sourceScope = PresetSourceScope.EXTERNAL_APPS` setzen; `Flat` bleibt `PresetSourceScope.ALL`.
- [x] `PresetJsonSerializer` und `PresetShareCodec` prüfen: `sourceScope` wird sauber mitserialisiert und beim Fehlen in alten Links/JSONs als `ALL` dekodiert.

### P15-2 – `BuiltInPlayerPresets.kt` & Player-Use-Cases in `GenreUseCase.kt`
- [x] `BuiltInPlayerPresets` mit allen 13 boxensicheren Player-Basen (ID-Schema z. B. `player_genre_<name>`, klar getrennt von `genre_<name>`) anlegen, alle mit `sourceScope = PresetSourceScope.OWN_PLAYER`.
- [x] In `GenreUseCaseTuning` eine quellenbewusste Methode (z. B. `applyForPlayer(genre, useCase)` oder über `genre.base.metadata.sourceScope` gesteuert) ergänzen, sodass jedes Player-Genre die abgestimmten Äquivalente für **Loud**, **Quiet**, **Headphones/In-Ear**, **Car** und **Bluetooth Speaker** erhält.
- [x] Alle Player-Presets (Basen + Use Cases) in `BuiltInPresets.all` aufnehmen, damit `findPresetById(...)`, Geräte- und App-Profile sie nahtlos auflösen können.

### P15-3 – Gruppen-Helper & Mapping zwischen Spotify- und Player-Gegenstück
- [x] In `GenrePresetGroups` klare APIs bereitstellen:
  - Gruppen gefiltert nach `PresetSourceScope` (`forSource(PresetSourceScope.OWN_PLAYER)` vs. `forSource(PresetSourceScope.EXTERNAL_APPS)`).
  - Deterministisches Mapping-Helper `counterpartPresetId(presetId, targetScope)` (z. B. `genre_uptempo__moderate` ↔ `player_genre_uptempo`, `genre_uptempo_use_loud` ↔ `player_genre_uptempo_use_loud`), damit Roadmap 16 beim Quellenwechsel das passende Gegenstück direkt umschalten kann.

---

## Abnahmekriterien & Verifikation

- [x] **Unit-Tests `:core`:**
  - `PresetJsonSerializerTest` / `PresetShareCodecTest`: alte JSONs/Share-Codes ohne `sourceScope` dekodieren als `PresetSourceScope.ALL`; Roundtrip für `OWN_PLAYER` und `EXTERNAL_APPS` verlustfrei.
  - `BuiltInPlayerPresetsTest`:
    - Keine doppelten IDs in `BuiltInPresets.all`.
    - Alle 13 Genres existieren als `OWN_PLAYER`-Basis und mit allen 5 Use Cases (insb. `LOUD`, `QUIET`, `HEADPHONES`).
    - Alle Boxen-Presets (`OWN_PLAYER` ohne `HEADPHONES`/`QUIET`) erfüllen `subsonicCutoffHz >= 30f` (Standard-Boxen `>= 35f`, `LOUD >= 42f`), `limiter.thresholdDb <= -1.0f` und `virtualBassMix == 0f` (außer `BLUETOOTH_SPEAKER`).
    - Kein Preset überschreitet inkl. Macros die 9-dB-Gesamtboost-Schranke.
  - `counterpartPresetId(...)` findet für jedes Genre- und Use-Case-Preset das exakte Gegenstück in der jeweils anderen Quelle.
- [x] **Build & Lint:**
  ```bash
  ./gradlew :core:test :core:ktlintCheck
  ```

---

## Umsetzungsstand

**Commit:** `7f513d0`

**Status:** umgesetzt, Hörprobe am Gerät offen. Baut auf dem Stand von Roadmap 14
(Branch `ccr-c4753f2c-j0bzgb`, noch nicht in `main`) auf, der dafür in diesen Branch gemergt wurde.

### Geänderte Dateien
- `core/.../preset/Preset.kt` – `enum class PresetSourceScope { ALL, OWN_PLAYER, EXTERNAL_APPS }` mit
  `fits(source)` (ALL passt auf jeder Seite immer); `PresetMetadata.sourceScope` mit Default `ALL`.
- `core/.../preset/BuiltInPlayerPresets.kt` *(neu)* – 13 Player-Basen exakt nach Tabelle A
  (IDs `player_genre_<name>`, Name „<Genre> – Player“, `virtualBassMix = 0f`, MBC an, gleicher
  Metadaten-Genre-Schlüssel wie das System-EQ-Gegenstück → gleiches `PresetDesign`), `genres`,
  `forGenre(id)`, `useCases(genre)` und `allPresets` (13 Basen + 65 Use Cases).
- `core/.../preset/GenreUseCase.kt` – `GenreUseCaseTuning.applyForPlayer(genre, useCase)` nach
  Tabelle B; `apply()` wählt das Tuning über `genre.base.metadata.sourceScope` (OWN_PLAYER → Player).
  Neuer Enum-Text `playerDescription` je Use Case für die Player-Liste. Kleiner Helfer `capBelow()`.
- `core/.../preset/BuiltInPresets.kt` – Legacy-Presets `EXTERNAL_APPS`, `Flat` explizit `ALL`;
  `all` enthält zusätzlich `BuiltInPlayerPresets.allPresets`.
- `core/.../preset/BuiltInGenrePresets.kt` – die acht bisherigen Genres `EXTERNAL_APPS`, Flat `ALL`,
  die fünf Genres aus RM 14 `OWN_PLAYER` (siehe Entscheidungen). Scope vererbt sich über
  `copy()` automatisch auf Intensitäten, Character- und Use-Case-Varianten.
- `core/.../preset/GenrePresetGroups.kt` – `playerGroups`, `forSource(scope)`,
  `counterpartPresetId(presetId, target, scope = …)`; `genreOf()` findet auch Player-Presets.
  `all` und `forGenres()` bleiben unverändert (die App nutzt sie bis RM 16).
- `core/.../data/preset/PresetJsonSerializer.kt` – `coerceInputValues = true`: ein unbekannter
  Scope-Wert einer späteren Version fällt auf `ALL` zurück, statt den Import scheitern zu lassen.
- `core/.../data/preset/PresetShareCodec.kt` – nur Kommentare: Scope wird (außer `ALL`, wegen
  `encodeDefaults = false`) mitgeteilt; Links mit `ALL` sind byte-gleich zu vorher.
- Tests: `BuiltInPlayerPresetsTest.kt` *(neu)*, `PresetJsonSerializerTest.kt`,
  `PresetShareCodecTest.kt`, `ElectronicGenrePresetsTest.kt` (Limiter/Subsonic der RM-14-Genres).

### Entscheidungen
- **RM-14-Genres sind player-only** (Vorgabe aus RM 14 „Offen“): `genre_hardtekk`, `genre_techno`,
  `genre_hard_techno`, `genre_industrial_techno`, `genre_schranz` samt Intensitäten, Character- und
  Use-Case-Varianten tragen `OWN_PLAYER`. Damit sie die Boxen-Regeln erfüllen, wurden **nur
  Limiter und Subsonic** ihrer Basen auf die Player-Werte aus Tabelle A gesetzt (Hardtekk 40 Hz /
  -1.0 dB, Techno 35 / -1.0, Hard Techno 38 / -1.0, Industrial Techno 38 / -1.0, Schranz 42 / -1.2);
  Kurven und Macros aus RM 14 sind unverändert. Ihre Use Cases laufen über das Player-Tuning.
- **Player-Liste je Genre** (`forSource(OWN_PLAYER)`): Player-Basis → Character-Varianten, die selbst
  `OWN_PLAYER` sind (nur die RM-14-Genres, je 2) → 5 Player-Use-Cases; am Ende Flat (`ALL`). Die
  System-EQ-Character-Varianten und Legacy-Presets bleiben in der System-EQ-Liste.
  `forSource(EXTERNAL_APPS)` = bisherige Gruppen ohne die fünf player-only Genres.
- **`counterpartPresetId`**: passt ein Preset schon zur Zielquelle (inkl. Flat, Custom, Kontext),
  bleibt es. Sonst Basis/Intensität ↔ Basis (`genre_uptempo__moderate` ↔ `player_genre_uptempo`),
  Use Case ↔ gleicher Use Case, Character-Variante/Legacy-Preset → Player-Basis des Genres.
  `null`, wenn es kein Gegenstück gibt (player-only Genres haben keine System-EQ-Version) – RM 16
  muss dann z. B. auf Flat zurückfallen. Für Custom-Presets den eigenen Scope über `scope =` übergeben.
- **Neutrale Anker in den Delta-Kurven:** LOUD und HEADPHONES bekommen zusätzlich `1000 Hz: 0` (LOUD
  auch `12000 Hz: 0`), wie in den bestehenden System-EQ-Deltas. Ohne sie würde die Interpolation
  z. B. bei LOUD die ganzen Mitten zwischen 260 Hz und 3 kHz um ~1 dB absenken. BT-Speaker endet mit
  `800 Hz: 0`. Headroom bleibt in allen Player-Use-Cases auf dem Basiswert (Tabelle B nennt keinen).
- **CAR „Sub < 60 Hz gedeckelt“** = kein Kurvenpunkt unter 60 Hz über 0 dB (die Kabine liefert den
  Sub). **Pop** hat laut Tabelle A Subsonic 30 Hz; das liegt unter der „Standard-Boxen ≥ 35 Hz“-Regel,
  erfüllt aber die Mindestgrenze ≥ 30 Hz – im Test als Ausnahme benannt.
- **9-dB-Schranke:** gilt für alle angebotenen `OWN_PLAYER`-Presets. Die stillgelegten
  Intensitätsstufen (`__aggressive`, `__very_aggressive`) der RM-14-Genres liegen darüber (bis
  10,8 dB) – sie werden nur noch für alte Profile aufgelöst und von `PresetIntensityBoost.legacy`
  auf Basis + Schalter umgeleitet; das galt schon vorher für die bestehenden Genres.

### Nachweise
- `BuiltInPlayerPresetsTest`: keine doppelten IDs in `BuiltInPresets.all`; 13 Player-Genres mit
  Basis und allen 5 Use Cases; Tabelle A (Kurve, Macros, Headroom, MBC, Limiter, Subsonic); Boxen-
  Regeln (Subsonic ≥ 30/35/42 Hz, Limiter ≤ -1.0 dB, `virtualBassMix == 0` außer BT); < 9 dB inkl.
  Macros; Tabelle B je Use Case und Genre; weniger Tiefbass (40–60 Hz) als das System-EQ-Gegenstück;
  `forSource`, `genreOf`, `counterpartPresetId` (exakt für alle 8 gemeinsamen Genres × Basis + 5
  Use Cases, Fallbacks, player-only → `null`).
- `PresetJsonSerializerTest` / `PresetShareCodecTest`: alte JSONs/Links ohne `sourceScope` → `ALL`;
  Roundtrip `OWN_PLAYER`/`EXTERNAL_APPS` verlustfrei; unbekannter Scope → `ALL`; `ALL`-Links
  enthalten kein `sourceScope`.
- `:core:test` (508 Tests, 0 Fehler) und `:core:ktlintCheck` grün – wie in RM 14 über ein temporäres
  Settings-Projekt nur mit `:core` (im Cloud-Container ist `dl.google.com` gesperrt, das Root-Projekt
  lädt das Android-Gradle-Plugin nicht). `:app` wurde nicht gebaut; geprüft per Suche: keine
  `:app`-Stelle hängt von geänderten Signaturen ab, `GenrePresetGroups.all` ist unverändert.

### Offen / Übergabe an RM 16
- Hörprobe der Player-Presets am Gerät (Boxen, In-Ears, Auto, BT-Box), v. a. LOUD bei hoher Lautstärke.
- RM 16: `forSource(...)` statt `forGenres(...)` in der Preset-UI, `counterpartPresetId(...)` beim
  Quellenwechsel; beim Speichern eines Custom-Presets (`EqController`, `PresetMetadata(...)`) den
  Scope des Ausgangs-Presets übernehmen. `EqRemoteApi` meldet für Player-Presets die Genre-ID
  `player_genre_…` – bei Bedarf dort auf das System-EQ-Genre abbilden.
