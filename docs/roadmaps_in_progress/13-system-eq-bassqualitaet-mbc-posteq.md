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
- [ ] `MBC_BAND_COUNT` von `4` auf `5` erhöhen (mit automatischem Fallback auf `4` Bänder in der `attempts`-Schleife von `AndroidAudioEngine.attachLocked()`, falls ein OEM-Gerät 5 Bänder ablehnt):
  - **Band 0 (`<= 50 Hz`, Sub-Bass):** langsame Attack (`25–30 ms`), Release `160–180 ms`, Threshold-Offset `+3 dB`, Ratio `min(mbcRatio, 3.5f)`, weiches Knie `10 dB`.
  - **Band 1 (`50–135 Hz`, Kick-Punch):** Attack `18–25 ms` (lässt Kick-Transienten durch!), Release `90–110 ms`, Ratio `min(mbcRatio, 4.0f)`, Knie `8 dB`.
  - **Band 2 (`135–350 Hz`, Low-Mid / Mulm-Trennung):** Attack `12–15 ms`, Release `100 ms`, moderate Ratio (`1f + (mbcRatio - 1f) * 0.55f`), Knie `8 dB`, reduzierter Makeup-Gain (`* 0.35f`) — entkoppelt Bass-Mulm sauber von Vocals!
  - **Band 3 (`350–3000 Hz`, Vocals / Mitten):** sanfte Ratio (`1f + (mbcRatio - 1f) * 0.25f`, max. `1.8:1`), Threshold-Offset `+4 dB`, minimaler Makeup-Gain (`* 0.25f`) — Stimmen bleiben stabil und klar.
  - **Band 4 (`3000–20000 Hz`, Präsenz & Höhen/Air):** sanfte Ratio (`1f + (mbcRatio - 1f) * 0.2f`, max. `1.6:1`), Threshold-Offset `+4 dB`, minimaler Makeup-Gain (`* 0.2f`) — keine zischeligen Hi-Hats.
- [ ] Sicherstellen, dass Sub- und Kick-Peaks primär im MBC abgefangen werden, damit der breitbandige End-Limiter (`-1.0` bis `-1.5 dB` Threshold) nur noch sanft Spitzen abfängt und nicht mehr hörbar pumpt.

### P13-2 – `PreEQ → MBC → PostEQ` Mastering-Split aktivieren (mit Fallback)
- [ ] In `AndroidAudioEngine.createDynamicsConfig()` eine Variante mit `postEqEnabled = true` (z. B. 10–16 Bänder für glattes Voicing) vor die bisherigen Versuche in `attempts` setzen — bei Geräten, die `PreEQ + MBC + PostEQ` ablehnen, greift automatisch der bisherige `PreEQ + MBC`-Pfad.
- [ ] Signalaufteilung in `CurveCalculator` / `EqController`:
  - **`PreEQ` (vor MBC):** Aufräumen & Korrigieren — AutoEQ-Kopfhörerkorrektur, Subsonic-High-Pass (`SubsonicFilterCurve`), Mud-Dip (`180–320 Hz`), schmale parametrische Korrekturen.
  - **`PostEQ` (hinter MBC):** Klangfarbe & Glanz — optionaler Anteil des Bass-Shelves / Sound-Goals sowie Hochton-„Air“ (`10–16 kHz`), damit der MBC diese Anhebungen nicht wieder flachbügelt.
- [ ] Headroom-Berechnung so absichern, dass `PreEQ + PostEQ` den End-Limiter nicht überfahren.

### P13-3 – STFT-Pre-Ringing-Schutz im 34-Band-Pre-EQ
- [ ] In `PreEqMapper.kt` prüfen, wie groß maximale Pegelsprünge zwischen direkt benachbarten Bass-Bändern (`<= 250 Hz`) im Modus `PRECISE` werden können.
- [ ] Optionale maximale Band-zu-Band-Steilheit (Slope-Limiter, z. B. max. `4–5 dB` Sprung pro Terzband unterhalb `250 Hz`) als Schutz gegen hörbares STFT-Pre-Echo vor Kicks evaluieren und per Unit-Test absichern.

### P13-4 – Bass-Klarheits-Kontur (Subsonic, Mud-Dip & Air) & Quellen-/Hardware-Checkliste
- [ ] In den bassstarken Presets / Sound-Goals prüfen und ggf. nachschärfen:
  - **Subsonic-Cut:** Standardmäßig aktivierbar bzw. sauber abgestimmt (`25–30 Hz` Kopfhörer, `35–45 Hz` Auto/BT-Speaker) gegen Treiber-Hub & IMD.
  - **Mud-Dip (`200–300 Hz`, `-2` bis `-3,5 dB`):** Optionales Sound-Goal oder Bestandteil der „Knall“-/Bass-Presets für freie Vocals trotz massivem Tiefbass.
  - **Hochton-Gegenkontur:** Sanfter Air-Boost (`+1,5` bis `+2,5 dB` ab `10–14 kHz`) bei gleichzeitiger Zügelung der Zischlaute um `4,5 kHz` (`HeadphoneComfortCurve`).
- [ ] Kurzen Hinweis / Diagnose-Check in der App (oder Doku) ergänzen für externe Qualitätskiller:
  - Spotify-Normalisierung auf **Aus** (nicht „Laut“), Audioqualität **Sehr hoch (320 kbps)**, Spotify-EQ **Aus**
  - Kopfhörer-Hersteller-App (Sony/Samsung/Nothing/JBL/Bose): EQ **Flat**, **Clear Bass / Dolby Atmos Aus**
  - Bluetooth-Codec prüfen (**LDAC / aptX Adaptive** statt **SBC**)

---

## Abnahmekriterien & Verifikation

- [ ] **Unit-Tests:**
  - `DynamicsProtectionTest` / `AndroidAudioEngine`-Tests: 5-Band-MBC-Fallback-Reihenfolge und `PostEQ`-Fallback funktionieren deterministisch; Mitten-/Höhen-Ratios bleiben gedämpft gegenüber Sub/Kick.
  - `PreEqMapperTest`: Slope-Check verhindert extreme Nachbarband-Sprünge im Bass.
- [ ] **Build & Lint:**
  ```bash
  ./gradlew :core:test :app:testFullDebugUnitTest :core:ktlintCheck :app:ktlintCheck
  ```
- [ ] **Gerätetest & Hörprobe (Spotify + Kopfhörer + Auto/BT-Box):**
  - Diagnostik-Screen bestätigt `hasMbc = true`, 5 MBC-Bänder und (sofern vom Gerät unterstützt) aktives `PostEQ`.
  - Basslastiger Track (Hardstyle / DnB / Uptempo) bei `+10 bis +12 dB` Bass: Vocals pumpen bei Kicks nicht mehr weg, Hi-Hats bleiben sauber, kein hohles Pre-Echo vor Kicks.
