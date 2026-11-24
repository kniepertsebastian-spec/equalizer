# Roadmap 13 – System-EQ-Klangqualität bei sehr kräftigem Bass (5-Band-MBC, PreEQ/PostEQ-Split & Pre-Ringing-Schutz)

**Priorität:** Hoch (Klangqualität für Spotify, YouTube & alle externen Apps sowie finaler Dynamics-Pfad)  
**Ziel:** Bei sehr kräftigem Bass (+8 bis +15 dB) deutlich mehr Klarheit, Druck und Transienten-Treue herausholen — ohne Breitband-Pumpen, ohne Mulm (180–350 Hz) und ohne verschmierte Kicks.  
**Abhängigkeit:** **Roadmap 08** abgeschlossen (und idealerweise **Roadmap 09**, sofern RM 09 `AndroidAudioEngine.kt` anpasst; im Turbo-Plan erhält RM 13 die alleinige Hoheit über `AndroidAudioEngine.kt`).

---

## Ausgangslage / Befund

In `AndroidAudioEngine.kt` (`303-431`) liegen vier konkrete Qualitätsbremsen bei sehr starkem Bass:

1. **4 MBC-Bänder koppeln oberen Bass/Mulm mit Vocals:**  
   Aktuelle Trennfrequenzen: `45 Hz` | `130 Hz` | `1500 Hz` | `20000 Hz`. Dadurch liegen `130–350 Hz` (oberer Bass/Mulm) und `350–1500 Hz` (Grundton der Stimmen) im selben Band. Wenn der obere Bass komprimiert wird, werden Vocals mit heruntergezogen.
2. **Identische harte MBC-Ratio für alle Frequenzbereiche:**  
   Alle 4 Bänder erhalten dieselbe `mbcRatio` (bis `6:1`) und fast denselben `mbcThresholdDb` (nur Sub hat `+3 dB` Offset). Harte Bass-Kompression quetscht dadurch unnötig Mitten und Höhen.
3. **`PostEQ` komplett ungenutzt (`postEqEnabled = false`):**  
   Korrekturkurve (AutoEQ, Subsonic) **und** der komplette Bass-/Höhen-Boost laufen gemeinsam vor dem MBC im `PreEQ`. Ein +12-dB-Bass-Boost fährt den MBC sofort stark in die Sättigung, und ein Höhen-„Air“-Boost wird vom MBC unnötig abgedämpft.
4. **STFT-Pre-Ringing & Breitband-Limiter-Pumpen:**  
   `DynamicsProcessing` arbeitet frequenzbasiert (`20 ms` Frame-Länge). Sehr steile Sprünge im 34-Band-Pre-EQ (insbesondere im Modus `PRECISE`) erzeugen hörbares Pre-Echo vor Kicks; zudem drückt der breitbandige End-Limiter bei zu heißer Aussteuerung das gesamte Spektrum (Vocals + Hi-Hats) im Takt des Basses weg.

---

## Zugewiesener Dateiumfang

- `app/src/main/java/com/auracurve/app/audio/AndroidAudioEngine.kt` (+ `AndroidAudioEngine` / `DynamicsProtectionTest`)
- `core/src/main/kotlin/com/auracurve/app/dsp/PreEqMapper.kt` *(Steilheitsbegrenzung / Split-Helper)*
- `core/src/main/kotlin/com/auracurve/app/dsp/SubsonicFilterCurve.kt`
- `app/src/main/java/com/auracurve/app/ui/main/CurveCalculator.kt`
- `app/src/main/java/com/auracurve/app/eq/EqController.kt`
- `app/src/main/java/com/auracurve/app/audio/ProcessingSettings.kt` *(falls `postEqGainsDb` ergänzt wird)*

---

## Arbeitspakete

### P13-1 – Upgrade auf 5-Band-MBC mit frequenzspezifischen Ratios & Transienten-Attack
- [x] `MBC_BAND_COUNT` von `4` auf `5` erhöhen (mit automatischem Fallback auf `4` Bänder in der `attempts`-Schleife von `AndroidAudioEngine.attachLocked()`, falls ein OEM-Gerät 5 Bänder ablehnt):
  - **Band 0 (`<= 50 Hz`, Sub-Bass):** langsame Attack (`25–30 ms`), Release `160–180 ms`, Threshold-Offset `+3 dB`, Ratio `min(mbcRatio, 3.5f)`, weiches Knie `10 dB`.
  - **Band 1 (`50–135 Hz`, Kick-Punch):** Attack `18–25 ms` (lässt Kick-Transienten durch!), Release `90–110 ms`, Ratio `min(mbcRatio, 4.0f)`, Knie `8 dB`.
  - **Band 2 (`135–350 Hz`, Low-Mid / Mulm-Trennung):** Attack `12–15 ms`, Release `100 ms`, moderate Ratio (`1f + (mbcRatio - 1f) * 0.55f`), Knie `8 dB`, reduzierter Makeup-Gain (`* 0.35f`) — entkoppelt Bass-Mulm sauber von Vocals!
  - **Band 3 (`350–3000 Hz`, Vocals / Mitten):** sanfte Ratio (`1f + (mbcRatio - 1f) * 0.25f`, max. `1.8:1`), Threshold-Offset `+4 dB`, minimaler Makeup-Gain (`* 0.25f`) — Stimmen bleiben stabil und klar.
  - **Band 4 (`3000–20000 Hz`, Präsenz & Höhen/Air):** sanfte Ratio (`1f + (mbcRatio - 1f) * 0.2f`, max. `1.6:1`), Threshold-Offset `+4 dB`, minimaler Makeup-Gain (`* 0.2f`) — keine zischeligen Hi-Hats.
- [x] Sicherstellen, dass Sub- und Kick-Peaks primär im MBC abgefangen werden, damit der breitbandige End-Limiter (`-1.0` bis `-1.5 dB` Threshold) nur noch sanft Spitzen abfängt und nicht mehr hörbar pumpt.

### P13-2 – `PreEQ → MBC → PostEQ` Mastering-Split aktivieren (mit Fallback)
- [x] In `AndroidAudioEngine.createDynamicsConfig()` eine Variante mit `postEqEnabled = true` (z. B. 10–16 Bänder für glattes Voicing) vor die bisherigen Versuche in `attempts` setzen — bei Geräten, die `PreEQ + MBC + PostEQ` ablehnen, greift automatisch der bisherige `PreEQ + MBC`-Pfad.
- [x] Signalaufteilung in `CurveCalculator` / `EqController`:
  - **`PreEQ` (vor MBC):** Aufräumen & Korrigieren — AutoEQ-Kopfhörerkorrektur, Subsonic-High-Pass (`SubsonicFilterCurve`), Mud-Dip (`180–320 Hz`), schmale parametrische Korrekturen.
  - **`PostEQ` (hinter MBC):** Klangfarbe & Glanz — optionaler Anteil des Bass-Shelves / Sound-Goals sowie Hochton-„Air“ (`10–16 kHz`), damit der MBC diese Anhebungen nicht wieder flachbügelt.
- [x] Headroom-Berechnung so absichern, dass `PreEQ + PostEQ` den End-Limiter nicht überfahren.

### P13-3 – STFT-Pre-Ringing-Schutz im 34-Band-Pre-EQ
- [x] In `PreEqMapper.kt` prüfen, wie groß maximale Pegelsprünge zwischen direkt benachbarten Bass-Bändern (`<= 250 Hz`) im Modus `PRECISE` werden können.
- [x] Optionale maximale Band-zu-Band-Steilheit (Slope-Limiter, z. B. max. `4–5 dB` Sprung pro Terzband unterhalb `250 Hz`) als Schutz gegen hörbares STFT-Pre-Echo vor Kicks evaluieren und per Unit-Test absichern.

### P13-4 – Bass-Klarheits-Kontur (Subsonic, Mud-Dip & Air) & Quellen-/Hardware-Checkliste
- [x] In den bassstarken Presets / Sound-Goals prüfen und ggf. nachschärfen:
  - **Subsonic-Cut:** Standardmäßig aktivierbar bzw. sauber abgestimmt (`25–30 Hz` Kopfhörer, `35–45 Hz` Auto/BT-Speaker) gegen Treiber-Hub & IMD.
  - **Mud-Dip (`200–300 Hz`, `-2` bis `-3,5 dB`):** Optionales Sound-Goal oder Bestandteil der „Knall“-/Bass-Presets für freie Vocals trotz massivem Tiefbass.
  - **Hochton-Gegenkontur:** Sanfter Air-Boost (`+1,5` bis `+2,5 dB` ab `10–14 kHz`) bei gleichzeitiger Zügelung der Zischlaute um `4,5 kHz` (`HeadphoneComfortCurve`).
- [x] Kurzen Hinweis / Diagnose-Check in der App (oder Doku) ergänzen für externe Qualitätskiller:
  - Spotify-Normalisierung auf **Aus** (nicht „Laut“), Audioqualität **Sehr hoch (320 kbps)**, Spotify-EQ **Aus**
  - Kopfhörer-Hersteller-App (Sony/Samsung/Nothing/JBL/Bose): EQ **Flat**, **Clear Bass / Dolby Atmos Aus**
  - Bluetooth-Codec prüfen (**LDAC / aptX Adaptive** statt **SBC**)

---

## Abnahmekriterien & Verifikation

- [x] **Unit-Tests:**
  - `DynamicsProtectionTest` / `AndroidAudioEngine`-Tests: 5-Band-MBC-Fallback-Reihenfolge und `PostEQ`-Fallback funktionieren deterministisch; Mitten-/Höhen-Ratios bleiben gedämpft gegenüber Sub/Kick.
  - `PreEqMapperTest`: Slope-Check verhindert extreme Nachbarband-Sprünge im Bass.
- [x] **Build & Lint:**
  ```bash
  ./gradlew :core:test :app:testFullDebugUnitTest :core:ktlintCheck :app:ktlintCheck
  ```
- [ ] **Gerätetest & Hörprobe (Spotify + Kopfhörer + Auto/BT-Box):**
  - Diagnostik-Screen bestätigt `hasMbc = true`, 5 MBC-Bänder und (sofern vom Gerät unterstützt) aktives `PostEQ`.
  - Basslastiger Track (Hardstyle / DnB / Uptempo) bei `+10 bis +12 dB` Bass: Vocals pumpen bei Kicks nicht mehr weg, Hi-Hats bleiben sauber, kein hohles Pre-Echo vor Kicks.

---

## Umsetzungsstand (23.11.2026)

### P13-1 – 5-Band-MBC
- `MBC_BAND_COUNT = 5`, Rückfall auf 4. `MbcTuning` (in `AndroidAudioEngine.kt`, testbar ohne Gerät):
  50 / 135 / 350 / 3000 / 20000 Hz mit den Werten der Roadmap (Sub 28/170 ms, `min(r, 3,5)`, +3 dB,
  Knie 10 · Kick 20/100 ms, `min(r, 4)`, Knie 8 · Mud 13/100 ms, `1 + (r−1)·0,55`, Makeup ·0,35 ·
  Vocals `≤ 1,8:1`, +4 dB, Makeup ·0,25 · Air `≤ 1,6:1`, +4 dB, Makeup ·0,2). Nicht vorgegebene Werte:
  Vocals-Attack 10 ms / Release 110 ms, Knie 8 dB für Vocals und Air.
- Der 4-Band-Rückfall behält seine Trennfrequenzen (45/130/1500/20000), bekommt aber dieselbe
  Ratio-Staffelung (Mitten-Band zwischen Mud- und Vocal-Ratio).
- End-Limiter unverändert (2 ms, Ratio 10, 120 ms Release, Schwelle aus dem Preset).

### P13-2 – PreEQ → MBC → PostEQ
- `DynamicsAttempts.order()` (getestet): je Pre-EQ-Raster zuerst `PreEQ + 5-Band-MBC + PostEQ`, dann
  5 Bänder, 4 Bänder, ohne MBC; danach dieselben MBC-Stufen ohne Pre-EQ.
- **Entscheidung:** Der PostEQ nutzt das Raster des PreEQ (34 bzw. 16 Bänder) statt 10–16 eigener
  Bänder. Nur so ist `pre + post` pro Band exakt die bisherige Kurve – Frequenzgang und der
  Headroom, den der Eingangsgain dafür reserviert, bleiben gleich (P13-2, Punkt 3).
- `PostEqSplit` (in `PreEqMapper.kt`): hinter den MBC wandern ein gedeckelter Anteil (35 %, max.
  4 dB) von Bass-Anhebungen bis 150 Hz (bis 250 Hz ausgeblendet) und die Air-Anhebung ab 6–10 kHz
  (max. 6 dB). Absenkungen (Subsonic, Mud-Dip, Korrektur-Dips) und Mitten bleiben vor dem MBC.
- Die Aufteilung arbeitet auf den fertigen Bandwerten, nicht getrennt nach Kurvenquelle: eine
  positive AutoEQ-Korrektur im Air-Bereich läuft also ebenfalls hinter dem MBC.
- `preEqUnavailableReason` meldet nur noch Pre-EQ-Raster, die gar nicht liefen; ein abgelehnter
  PostEQ oder 5-Band-MBC beim laufenden Raster sperrt den Hochauflösungs-EQ nicht mehr.

### P13-3 – Pre-Ringing-Schutz
- `PreEqSlopeLimiter`: höchstens 5 dB Sprung zwischen Nachbarbändern unterhalb 250 Hz, symmetrisch
  (beide Seiten gehen aufeinander zu), in allen Modi. Ein 12-dB/Oktave-Subsonic passiert
  unverändert; ein +12-dB-Spike in `PRECISE` wird begrenzt. Die bisherigen E-008-Goldens änderten
  sich dadurch nicht.

### P13-4 – Kontur & Checkliste
- Neues Klangziel **„Clear bass“** (Mud-Dip −3 dB bei 260 Hz, −1 dB bei 4,5 kHz, +2 dB ab 13 kHz).
  Die bassstarken Presets hatten Mud-Dips und Subsonic (über Kontext-Modi) bereits.
- `docs/BASS_QUALITY_CHECKLIST.md`: Spotify, Hersteller-Apps, Bluetooth-Codec, Diagnose-Zeilen.
- Diagnose-Bericht: `MBC Bands: n`, `Post-EQ: 34-band / not in use`.

### Tests
- Neu: `DynamicsProtectionTest` (Reihenfolge, Rückfall, Ratios/Attacks/Thresholds, Reason),
  `PreEqSlopeAndSplitTest`, `SoundGoalTest`, `DiagnosticsReportFormatterTest`.
- Lokal: `ktlintCheck`, `:core:test`, `testStoreDebugUnitTest`, `testFullDebugUnitTest` grün bis auf
  Robolectric-Klassen, die hier ihr `android-all`-Artefakt nicht laden können (Netz) – Nachweis
  über die CI.

### Offen (nur am Gerät)
1. Diagnose bestätigt `MBC Bands: 5` und (falls unterstützt) `Post-EQ: 34-band`.
2. Hörprobe Hardstyle/DnB/Uptempo mit +10 bis +12 dB Bass: kein Vocal-Pumpen, saubere Hi-Hats,
   kein Pre-Echo. Ggf. Bass-Anteil des PostEQ (35 %, 4 dB) nachstimmen.
