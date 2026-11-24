# Roadmap 10 – High-Res Player-EQ, Virtual-Bass-Feinschliff & Stream-Qualität

**Priorität:** Mittel (Minor m1 + Abschluss offener Punkte aus Roadmap 03 / `P4-1`)  
**Ziel:** Den eigenen Player klanglich über den 34-Band-STFT-EQ von Spotify hinausheben (hochauflösende minimalphasige Biquads ohne STFT-Pre-Echo), den Virtual-Bass-Exciter auf den neuen Pegel aus RM 08 abstimmen und die echte AAC-HLS-Bitrate auf dem Gerät absichern.  
**Abhängigkeit:** **Roadmap 08** abgeschlossen (gleiche Datei `CurveEqualizer.kt`). Danach vollständig parallel zu RM 09, 11, 12 und 13.

---

## Ausgangslage / Befund

1. **Gröbere Bandauflösung als Spotify-Pre-EQ (Minor m1):**  
   `CurveEqualizer` (`CurveEqualizer.kt:292-295`) approximiert die gesamte Zielkurve (inkl. AutoEQ-Korrektur und freien parametrischen Bändern) über nur **14 feste Peaking-Biquads** (`Q = 1.4` bzw. `1.0`). Schmale Kopfhörer-Korrekturen oder präzise Bass-Kerben werden dadurch stärker verschliffen als nötig.
2. **Virtual-Bass-Kalibrierung nach Pegel-Fix:**  
   `BassExciter` (`BassExciter.kt:280-294`) arbeitet mit `OUTPUT_TRIM = 0.35f`, `DEFAULT_THIRD_HARMONIC_SHARE = 0.25f` und einem Gate bei `-66 / -54 dBFS`. Sobald RM 08 das Eingangssignal vor dem Exciter um 6–9 dB anhebt, verschiebt sich das Hörgefühl (insbesondere bei starkem Bass-Boost + aktivem Exciter).
3. **Offener Punkt aus Roadmap 03 (`P4-1`):**  
   `StreamSelection.kt` bevorzugt zwar AAC 256 > AAC 160 > MP3 128 und schließt Opus aus, aber auf einem echten Gerät wurde noch nicht geprüft, welche `preset`-Namen SoundClouds v2-API aktuell liefert und ob wirklich `aac_160k`/`aac_256k` statt `128k MP3` gestreamt wird.

---

## Zugewiesener Dateiumfang

- `core/src/main/kotlin/com/auracurve/app/dsp/CurveEqualizer.kt` (+ `CurveEqualizerTest.kt`)
- `core/src/main/kotlin/com/auracurve/app/dsp/BassExciter.kt` (+ `BassExciterTest.kt`)
- `core/src/main/kotlin/com/auracurve/app/link/StreamSelection.kt` (+ `StreamSelectionTest.kt`)
- *(Optional in separatem Schritt, nicht parallel zu RM 13)* `app/src/main/java/com/auracurve/app/eq/EqController.kt` & `SoundSheet.kt` (für direkte Übergabe von `ParametricBand` / `thirdHarmonicShare`-Regler)

---

## Arbeitspakete

### P10-1 – `CurveEqualizer` auf Terzabstand (24–28 Bänder) verfeinern (Minor m1)
- [x] `CENTERS_HZ` in `CurveEqualizer.kt` von 14 auf ein dichtes ISO-Terz-Raster (z. B. 28 Bänder von `25 Hz` bis `16 kHz`: `25, 31.5, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000 Hz`) mit passendem `Q` (ca. `2.2–2.8` für Terzbänder) ausbauen.
- [x] `solveGains()` (`SOLVE_ITERATIONS`, `SOLVE_DAMPING`) so abstimmen, dass die Abweichung zur Zielkurve auch bei steilen AutoEQ-Kurven `< 0,5 dB` bleibt und nicht oszilliert.
- [x] Performance-Budget pro `512`-Frame-Block messen (Ziel: problemlos echtzeitfähig auf Smartphone-Little-Cores bei gesperrtem Bildschirm; unbenutzte Bänder mit `|gain| < 0.05 dB` im `processBlock` überspringen!).

### P10-2 – Direkte Kaskadierung freier parametrischer Bänder
- [x] In `CurveEqualizer` eine optionale Liste direkter `ParametricFilter` (Peak, Low-Shelf, High-Shelf) zusätzlich zum Basis-Kurven-Solver vorsehen, damit vom Nutzer gesetzte `ParametricBand`-Filter ohne Raster-Approximation exakt gerechnet werden.
- [x] In `peakBoostDb()` die kombinierten Biquads (Basis-Peaks + direkte parametrische Filter) berücksichtigen, damit das Pre-Gain weiterhin exakt bleibt.

### P10-3 – `BassExciter` (Virtual Bass) nach RM 08 feinabstimmen
- [ ] Hör- und Messprüfung des `BassExciter` mit dem um 6–9 dB heißeren Signal aus RM 08 (kein ungewolltes Zerr-Mulmen bei `mix = 1.0`, sauberes Gate-Verhalten in leisen Intros).
- [x] Falls sinnvoll: `thirdHarmonicShare` (z. B. `0.15` „Tief/Warm“ vs. `0.45` „Hart/Punch“) in `PlayerDspSettings` konfigurierbar machen.
- [x] Prüfen, ob eine optionale High-Pass-Kombination (leichte Entlastung unterhalb 35–40 Hz nur bei kleinen BT-Speakern) den Virtual-Bass-Effekt noch deutlicher macht.

### P10-4 – SoundCloud-AAC-HLS-Verifikation am Gerät (Restpunkt `P4-1`)
- [ ] Mit realer `transcodings`-Antwort von SoundCloud prüfen, ob `StreamSelection.pick()` zuverlässig `aac_160k` / `aac_256k` (HLS) vor `mp3_1_0` (128k) wählt.
- [x] Neue oder geänderte `preset`-/`mime_type`-Bezeichnungen in `StreamSelection.kt` ergänzen und per Unit-Test absichern.
- [ ] Gapless-Übergang, Seek und URL-Refresh nach längerer Pause mit AAC-HLS am Gerät verifizieren.

---

## Abnahmekriterien & Verifikation

- [x] **Unit-Tests `:core`:**
  - `CurveEqualizerTest`: Abweichung zur Zielkurve `< 0,5 dB` über `25 Hz – 16 kHz` inkl. enger AutoEQ-Korrekturen; Bänder mit `0 dB` werden übersprungen (Fast-Path).
  - `BassExciterTest`: 4.–6. Harmonische bleiben `> 50 dB` unter der 2. Harmonischen; leistungserhaltendes `thirdHarmonicShare`.
  - `StreamSelectionTest`: alle real beobachteten SoundCloud-Transcoding-Varianten abgedeckt.
- [x] **Build & Lint:**
  ```bash
  ./gradlew :core:test :core:ktlintCheck
  ```
- [ ] **Gerätetest:** CPU-Last bei gesperrtem Bildschirm gemessen; Diagnostik/Log bestätigt AAC-HLS-Streamwahl auf SoundCloud.

---

## Umsetzung (Stand: 23. November 2026 – Code und Unit-Tests fertig, Gerätetests offen)

**Basis:** Roadmap 08 (PR #122, gemergt), weil RM 10 dieselbe Datei `CurveEqualizer.kt` ändert und auf
dem neuen Pre-Gain aufsetzt. Parallel zu RM 09 (PR #123, inzwischen gemergt) entstanden: Dessen Dateien
(`PlayerDspPcm16`, `PlayerDspChain`, `PlayerDspAudioProcessor`, `PlayerDspState`) sind hier bewusst
**nicht** angefasst. `setCurve(...)` bekommt nur einen neuen Parameter mit Standardwert.

### P10-1 – Terzband-EQ
- **29 Peaks** auf den ISO-Terzmitten 25 Hz … 16 kHz (die Liste der Roadmap hat 29, nicht 28 Einträge)
  **plus Low-Shelf bei 25 Hz und High-Shelf bei 16 kHz**: Ohne die Shelves bricht jede Kurve, die über
  den Rand hinaus weiterläuft (Bass-Shelf, Höhen-Tilt), am Rand um 2–5 dB ein.
- **Q 2,5** (gemessen 2,2 / 2,5 / 2,8 / 3,2: ab 2,8 Welligkeit zwischen den Bändern, 2,2 und 2,5 gleichauf).
  **Bandbreiten-Vorverzerrung:** Die bilineare Transformation macht Peaks nahe Nyquist schmaler (bei 16 kHz /
  48 kHz rund ein Drittel). Die Peaks nutzen deshalb die Bandbreiten-Form des RBJ-Cookbooks, jedes Band hat so
  dieselbe Breite in Oktaven. Das hat die Welligkeit 12,5–16 kHz von ±1,5 dB auf < 0,4 dB gebracht.
- **Solver neu:** statt gedämpfter Iteration (`SOLVE_DAMPING`, oszillierte bei steilen Kurven) gewichtete
  Kleinste-Quadrate über 20 Hz – 20 kHz (3 Punkte je Terz, außerhalb 25 Hz – 16 kHz Gewicht 0,25), Cholesky,
  kleiner Ridge, Gains über ±15 dB werden festgesetzt und der Rest neu gelöst. Weil eine Peak-Kurve nicht exakt
  proportional zum Gain ist, wird die Matrix mit den gefundenen Gains neu aufgebaut (max. 4 Runden, Abbruch bei
  < 0,01 dB Änderung). Frequenzgang in `double` ausgewertet (in `float` rauscht der Nenner bei 25 Hz).
- **Fast-Path:** Bänder mit `|gain| < 0,05 dB` werden in `process`/`processBlock` nicht gerechnet; ihr
  Filterzustand folgt dem Signal wie ein Durchlass-Filter (`BiquadFilterState.follow/followBlock`), damit ein
  Band, das wieder gebraucht wird, nicht klickt. Ehrlicher Befund: Bei den eingebauten Presets und AutoEQ-Kurven
  hat fast jedes Band etwas Gain (29–31 aktiv); der Fast-Path greift bei bereichsweise flachen Kurven
  (nur Bass-Boost: ≤ 14 aktiv, nur Subsonic: 2).

**Messwerte** (max. / RMS-Abweichung 25 Hz – 16 kHz, 48 kHz; alt = 14 Peaks):

| Kurve | neu max | neu RMS | alt max | alt RMS |
|---|---|---|---|---|
| Car | 0,24 dB | 0,05 dB | 0,72 dB | 0,17 dB |
| Bluetooth-Box | 0,44 dB | 0,06 dB | 0,81 dB | 0,17 dB |
| Soundbar | 0,26 dB | 0,05 dB | 0,72 dB | 0,20 dB |
| In-Ear | 0,12 dB | 0,03 dB | 1,30 dB | 0,36 dB |
| flach +12 dB | 0,32 dB | 0,11 dB | 3,97 dB | 0,90 dB |
| Bass-Shelf +15 dB | 0,31 dB | 0,08 dB | 5,01 dB | 0,55 dB |
| AutoEQ-Katalog (10 Modelle, Roh-GraphicEQ) | 0,20–1,74 dB | 0,08–0,34 dB | 1,75–5,27 dB | 0,50–1,24 dB |

**Abweichung vom Abnahmekriterium „< 0,5 dB inkl. enger AutoEQ-Korrekturen":** Für Presets und steile, aber
glatte Kurven (Shelf, Tilt, oktavbreite Präsenz-Senke) erfüllt (Tests). Die rohen AutoEQ-GraphicEQ-Daten
schwanken teils um 1–2 dB innerhalb einer Sechsteloktave (z. B. Sony WH-1000XM4 4,0–4,8 kHz); das kann kein
Terzfilter darstellen. Dort gilt im Test RMS < 0,35 dB und max < 1,8 dB. Für exakt gemeinte enge Filter gibt
es jetzt P10-2.

**Performance:** Ein Kurvenwechsel (Neuentwurf) kostet ~0,6 ms auf dem Desktop-JIT. 60 s Stereo mit voller
AutoEQ-Kurve (31 Filter) ~0,25–0,29 s, also ~45 µs je 512er-Block (= 10,7 ms Audio), ~0,45 % eines
Desktop-Kerns. Ein Little-Core (Cortex-A55) ist grob 10× langsamer: wenige Prozent eines Kerns. Gemessen
im Test `the chain is cheap enough for the slow cores`; die echte Messung am Gerät bei gesperrtem Bildschirm ist offen.

### P10-2 – Direkte parametrische Filter
- `setCurve(curve, cutoff, ratio, parametricFilters = emptyList())`: Peak/Shelves (und Hoch-/Tiefpass) werden
  exakt als Biquads nach den Terzbändern gerechnet; gebypasste und 0-dB-Filter übersprungen.
- `peakBoostDb()` rechnet über alle aktiven Biquads und prüft zusätzlich die Mittenfrequenzen der
  parametrischen Filter (ein Peak mit Q 8 liegt sonst zwischen den Rasterpunkten).
- Hinzufügen/Entfernen eines Filters während der Wiedergabe behält den Speicher der übrigen Filter; ein neuer
  Filter startet vom aktuellen Ausgangssignal statt von Stille (kein Klick, Test).
- **(Inzwischen erledigt, siehe Nachtrag 24.11.2026.)** Die Übergabe der `ParametricBand`s aus
  `EqController` an den Player. Dafür müssen `PlayerBridge`, `PlayerDspState`, `PlayerDspAudioProcessor` und
  `PlayerDspPcm16`/`PlayerDspChain` die Filter durchreichen und `pushPlayerCurve()` die parametrische Kurve
  dann *aus* der gesampelten Kurve nehmen. Das kollidiert mit RM 09 und kommt nach dessen Merge.

### P10-3 – Virtual Bass nach RM 08
- Messprüfung (Unit-Tests, keine Hörprobe): Harmonischen-Verhältnis bei +6/+9/+12 dB unverändert, 4.–6.
  Harmonische > 50 dB unter der 2. – kein pegelabhängiges „Mulmen", `OUTPUT_TRIM` bleibt.
- **Gate um +6 dB verschoben** (−60/−48 statt −66/−54 dBFS): Ein leises Intro mit +12 dB Bass-Boost wird
  am Kopfhörer exakt wie vor RM 08 behandelt. Details und Preis in `docs/DSP_VIRTUAL_BASS.md`, 3.4.1.
- `thirdHarmonicShare` 0,15 / 0,45 leistungserhaltend und sauber (Test). **Nicht** in `PlayerDspSettings`
  verdrahtet: die Klasse zieht mit RM 09 nach `PlayerDspChain` um.
- **High-Pass-Befund:** Box-Preset hat bereits 65 Hz / 24 dB/Okt. Der Hochpass sitzt aber *vor* dem Exciter
  und nimmt ihm 40-Hz-Kicks um ~20 dB weg. Deutlicher würde der Effekt mit Seitenkette (Exciter greift vor dem
  Hochpass ab). Umbau der Kettenreihenfolge nach RM 09 vorgemerkt.
- Offen: Hörprobe H1–H6 (`docs/DSP_VIRTUAL_BASS.md` §7) mit RM-08-Pegel.

### P10-4 – SoundCloud AAC-HLS
- Stand der API (soundcloud/api Issue 441, andere Clients; ohne eigene Live-Antwort, der Container hat keinen
  SoundCloud-Zugang): Progressive-MP3 seit August 2026 abgeschaltet, HLS-MP3 läuft aus, Opus fällt weg. Geliefert
  werden `aac_160k`, `aac_96k`, `abr_sq` (adaptiv), alle HLS; `aac_256k` nur für Go+ und bisher nicht über die
  öffentliche API.
- `StreamSelection`: **Fehler behoben** – ein MIME-Typ `audio/mpegurl` (HLS-Playlist) wurde wegen
  `startsWith("audio/mpeg")` als MP3 erkannt; jetzt wird der Basistyp exakt verglichen, Playlist-Typen sagen
  nichts über den Codec. `abr_*` ist nur letzte Wahl (Qualität nicht fest, bei anderen Clients teils defekt).
  Verschlüsselte HLS-Varianten bleiben ausgeschlossen (Test). Neue Tests für die aktuelle Liste.
- **Diagnose-Log:** `SoundCloudClient` schreibt die Wahl nach Logcat:
  `adb logcat -s AuraCurveStream` → z. B. `chose hls aac_160k (AAC ~160 kbit/s) of hls aac_160k (...), ...`.
- Offen am Gerät: reale `transcodings`-Antwort im Log ablesen, Gapless, Seek und URL-Refresh nach längerer
  Pause mit AAC-HLS.

### Tests, Build, Lint
- `./gradlew :core:test :core:ktlintCheck` grün.
- `:player` konnte im Container nicht kompiliert werden (kein Android-SDK); die Änderung dort ist eine
  Log-Zeile, CI baut das Modul.

### Offene Punkte
- [ ] Gerätetest: CPU-Last bei gesperrtem Bildschirm, AAC-HLS-Streamwahl im Log, Gapless/Seek/URL-Refresh.
- [ ] Hörprobe Virtual Bass mit RM-08-Pegel (H1–H6).
- [x] Nachtrag (24.11.2026, nach Merge von RM 09): `ParametricBand`-Übergabe an den Player (P10-2),
      `thirdHarmonicShare` als Einstellung, Exciter-Seitenkette vor dem Subsonic-Hochpass (P10-3) – siehe unten.
- [ ] Hörprobe der Seitenkette auf der BT-Box (Preset „Bluetooth-Box“, 65-Hz-Hochpass): Virtual Bass jetzt
      deutlich präsenter als vorher, aber ohne Dröhnen; ggf. `mix` des Presets nachstimmen.

### Nachtrag 24.11.2026 – Folgeschritte nach RM 09
- **P10-2 verdrahtet:** `EqController.pushPlayerCurve()` nimmt die parametrischen Bänder aus der gesampelten
  Kurve heraus und gibt sie als exakte `ParametricFilter` mit (`PlayerBridge.setPlayerCurve(..., parametricFilters)`
  → `PlayerCurve.parametricFilters` → `PlayerDspAudioProcessor` → `PlayerDspChain.updateCurve` →
  `CurveEqualizer`). Abgeschaltete Bänder bleiben weg. Der Systempfad (andere Apps) bekommt sie weiter als Teil der
  Kurve. Die E-008-Goldens zeigen genau das: nur die Schritte mit parametrischem Band ändern sich (Kurve ohne
  das Band, Band als `filters=[...]`).
- **Virtual-Bass-Charakter:** `PlayerDspSettings.virtualBassThirdHarmonicShare` (gespeichert in `PlayerDspState`),
  im Sound-Sheet des Players als Pills „Warm / Balanced / Punchy“ (0,15 / 0,25 / 0,45; gleiche Gesamtlautstärke).
- **Seitenkette:** Der Subsonic-Hochpass sitzt jetzt am Ende der `CurveEqualizer`-Kette; `processBlock(...,
  beforeHighPass)` gibt das Signal davor aus, `BassExciter.processBlock(..., source)` erzeugt daraus die Obertöne
  und mischt sie dem gefilterten Signal zu. Test: Bei 65 Hz Hochpass bleibt die Oktave eines 45-Hz-Tons innerhalb
  1,5 dB gleich (ohne Seitenkette ~20 dB weniger, Gegenprobe schlägt fehl), der Grundton selbst wird weiter
  gekappt. **Hörbare Folge:** Auf Presets mit Subsonic-Hochpass (v. a. BT-Box, 65 Hz) ist der Virtual Bass
  lauter als bisher – genau das war das Ziel, braucht aber eine Hörprobe.
