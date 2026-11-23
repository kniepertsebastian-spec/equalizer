# Roadmap 08 – Player-Lautheit & EQ-Parität (Größter Sofort-Hebel)

**Priorität:** Kritisch (Blocker B1, Major M2, Major M3)  
**Ziel:** Den Lautstärkeverlust von 6–12+ dB im eigenen Player beseitigen, den nachgeschalteten System-MBC wieder normal aussteuern und volle Funktionsparität bei manuellen EQ-Slidern sowie beim A/B-Vergleich herstellen.  
**Abhängigkeit:** Keine (Welle 1 – sofort startbar, parallel zu Roadmap 11 und 12).

---

## Ausgangslage / Befund

1. **100-%-Headroom-Abzug statt 50 % (Blocker B1):**  
   Für Spotify senkt `EqController` den Input-Gain bewusst nur um `50 %` des Peak-Boosts (`INPUT_GAIN_SAFETY_RATIO = 0.5f` in `EqController.kt:92`, `0.8f` für Speaker/Soundbar). Im eigenen Player zieht `CurveEqualizer.design()` (`CurveEqualizer.kt:235-236`) dagegen stur **100 %** (`10f.pow(-maxBoostDb / 20f)`) ab. Bei +12 bis +15 dB Bass-Boost verlässt das Signal den Player 6–9+ dB leiser als bei Spotify. Da `EqController.pushToEngine()` (`EqController.kt:1218-1225`) gleichzeitig `inputGainDb = 0f` an `AndroidAudioEngine` schickt, verhungert der System-MBC komplett und die Mitten/Vocals sacken ab.
2. **Manuelle Band-Slider im Player ignoriert (Major M2):**  
   `CurveCalculator.preEqGains()` (`CurveCalculator.kt:139-143`) übernimmt manuelle Band-Abweichungen (`deltas`) auf den 34-Band-Pre-EQ. `EqController.pushPlayerCurve()` (`EqController.kt:1247-1270`) sampelt jedoch nur `combinedCurve(includeSubsonic = false)` + Macros — manuelle EQ-Band-Änderungen haben im eigenen Player keine Wirkung.
3. **A/B-Lautheitsabgleich defekt (Major M3):**  
   In `EqController.pushToEngine()` (`EqController.kt:1196-1241`) berechnet `LoudnessMatch.plan(...)` zwar `plan.eqExtraGainDb`, doch im `pcmEqualizerOwnsTheCurve(settings)`-Zweig wird `inputGainDb` pauschal auf `0f` überschrieben.

---

## Zugewiesener Dateiumfang

- `core/src/main/kotlin/com/auracurve/app/dsp/CurveEqualizer.kt` (+ Unit-Tests in `:core`)
- `player/app/src/main/java/com/auracurve/app/player/playback/PlayerDspState.kt` *(nur `PlayerCurve`)*
- `player/app/src/main/java/com/auracurve/app/player/playback/PlayerDspAudioProcessor.kt` *(nur `updateCurve`-Aufruf)*
- `core/src/main/kotlin/com/auracurve/app/dsp/PlayerDspPcm16.kt` *(nur `updateCurve`-Signatur)*
- `app/src/main/java/com/auracurve/app/integration/PlayerBridge.kt` & `AndroidPlayerBridge.kt` *(nur `setPlayerCurve`-Signatur; `stopPlayer`/`togglePlayback` gehören zu RM 11)*
- `app/src/main/java/com/auracurve/app/ui/main/CurveCalculator.kt`
- `app/src/main/java/com/auracurve/app/eq/EqController.kt` (+ `EqControllerTest`, `EqCharacterizationTest`, Golden-Files falls betroffen)

---

## Arbeitspakete

### P8-1 – Dynamische `headroomSafetyRatio` im `CurveEqualizer` (Blocker B1)
- [x] `PlayerCurve` in `PlayerDspState.kt` um `val headroomSafetyRatio: Float = 0.5f` erweitern.
- [x] `PlayerBridge.setPlayerCurve(...)`, `AndroidPlayerBridge.setPlayerCurve(...)`, `PlayerDspAudioProcessor.queueInput(...)` und `PlayerDspPcm16.updateCurve(...)` um `headroomSafetyRatio` erweitern (mit Default `0.5f` für Abwärtskompatibilität in Tests).
- [x] `CurveEqualizer.setCurve(...)` und `CurveEqualizer.design(...)` anpassen:
  ```kotlin
  val maxBoostDb = peakBoostDb(peaks, sampleRateHz)
  val preGainDb = -(maxBoostDb * request.headroomSafetyRatio.coerceIn(0f, 1f))
  return Design(request, sampleRateHz, highPass, peaks, 10f.pow(preGainDb / 20f))
  ```
- [x] In `EqController.pushPlayerCurve()` den bereits vorhandenen Wert `inputGainSafetyRatio()` (`0.5f` Standard, `0.8f` bei `BLUETOOTH_SPEAKER` / `SOUNDBAR`) übergeben.
- [x] Prüfen, dass sich die Gain-Rampe (`GAIN_RAMP_MS = 30f`) beim Wechsel der Ratio (z. B. Kopfhörer → Bluetooth-Box) klickfrei verhält.

### P8-2 – Manuelle EQ-Band-Deltas in `pushPlayerCurve()` übernehmen (Major M2)
- [x] Die Delta-Berechnung (Abweichung `classicBandGainsDb` von `EqualizerInterpolator.interpolateCurveToBands(...)` + Auswertung per `PreEqMapper`-Logik / `PreEqResponseMode`) aus `CurveCalculator.preEqGains()` in eine wiederverwendbare reine Funktion in `CurveCalculator` auslagern.
- [x] Diese manuellen Deltas in `EqController.pushPlayerCurve()` für jedes gesampelte Frequenzband (`PLAYER_CURVE_MIN_HZ..PLAYER_CURVE_MAX_HZ`) aufaddieren.
- [x] Sicherstellen, dass `recalculateBandGains()` und manuelle Band-Slider-Updates `pushPlayerCurve()` zuverlässig aktualisieren.

### P8-3 – A/B-Vergleich (`LoudnessMatch`) für eigenen Player reparieren (Major M3)
- [x] In `EqController.pushToEngine()` unterscheiden zwischen regulärem Betrieb (`inputGainDb = 0f` für das System, weil der Player bereits Pre-Gain anwendet) und aktivem Vergleich (`compare != null`).
- [x] Bei `CompareSide.EQ`: `plan.eqExtraGainDb` nicht mit `0f` überschreiben, sondern gezielt wirksam halten (entweder als `inputGainDb = plan.eqExtraGainDb` im System-`DynamicsProcessing` oder als expliziter Zusatz-Gain im Player-DSP).
- [x] Bei `CompareSide.ORIGINAL`: prüfen, dass `plan.originalGainDb` sauber angewendet wird und Player-Curve + Virtual Bass gebypasst bleiben.

---

## Abnahmekriterien & Verifikation

- [x] **Unit-Tests `:core`:** `CurveEqualizerTest` prüft `preGain` bei `+12 dB` Peak-Boost:
  - `ratio = 1.0f` → `-12 dB`
  - `ratio = 0.5f` → `-6 dB`
  - `ratio = 0.8f` → `-9,6 dB`
  - `ratio = 0.0f` → `0 dB` (keine Vorabsenkung)
- [x] **Unit-Tests `:app`:** `EqControllerTest` weist nach:
  - Standard-Route übergibt `headroomSafetyRatio = 0.5f`, Speaker/Soundbar `0.8f`.
  - Manuelles Verschieben eines EQ-Bands verändert die an `PlayerBridge.setPlayerCurve()` übergebene Kurve.
  - Aktiver A/B-Compare (`CompareSide.EQ`) verwirft `plan.eqExtraGainDb` nicht mehr.
- [x] **Build & Lint:**
  ```bash
  ./gradlew :core:test :player:testFullDebugUnitTest :app:testFullDebugUnitTest :core:ktlintCheck :app:ktlintCheck
  ```
- [ ] **Gerätetest / Hörprobe:** Gleicher Track auf Spotify und im eigenen Player mit identischem Bass-Preset (z. B. „Knall“) ist subjektiv annähernd gleich laut; manuelle Slider sind im eigenen Player sofort hörbar.

---

## Umsetzung (Stand: erledigt bis auf Gerätetest)

**Status:** P8-1, P8-2 und P8-3 umgesetzt, Unit-Tests, Build und Lint grün. Offen: Gerätetest/Hörprobe.

**Entscheidungen / Abweichungen vom Dateiumfang**
- `CurveEqualizer.preGainDb(maxBoostDb, ratio)` als reine Funktion im Companion; die Ratio steckt im `Request`, ein Wechsel (z. B. Kopfhörer → Box) erzeugt ein neues Design, der Pre-Gain gleitet über `GAIN_RAMP_MS` (Test `a new safety ratio glides instead of jumping`).
- **Schutz ohne Limiter:** `PlayerDspPcm16` nimmt bei ausgeschaltetem Player-Limiter automatisch Ratio `1.0` (voller Headroom), weil sonst nichts den Rest-Boost vor dem PCM16-Ausgang abfängt. Umschalten des Limiters setzt die Kurve neu (klickfrei über die Rampe). Dafür wurde `PlayerDspPcm16` über die reine `updateCurve`-Signatur hinaus angepasst.
- **P8-2:** `PreEqMapper.deltaAt` (`:core`) ist jetzt öffentlich, damit der Player die manuellen Deltas exakt so verteilt wie der Pre-EQ (inkl. `PreEqResponseMode`). In `CurveCalculator` neu: `manualDeltas(...)` und `manualDeltaDbAt(...)`; `preEqGains()` nutzt sie. Die Deltas werden gegen die kombinierte Kurve *mit* Subsonic gemessen (daraus wurden die Klassik-Bänder berechnet), aufaddiert wird auf die Player-Kurve *ohne* Subsonic.
- **P8-3:** Im Eigener-Player-Zweig trägt `inputGainDb` des System-`DynamicsProcessing` nur noch den Lautheitsabgleich: `0` ohne Vergleich, `plan.eqExtraGainDb` auf der EQ-Seite, `plan.originalGainDb` auf der Original-Seite (Player-Kurve + Virtual Bass gebypasst). Auch die Original-Seite war vorher betroffen.
- `PlayerBridge.DEFAULT_HEADROOM_SAFETY_RATIO` / `CurveEqualizer.DEFAULT_HEADROOM_SAFETY_RATIO` = `0.5f` als Default.

**Tests**
- `:core` `CurveEqualizerTest`: Ratio 1.0/0.5/0.8/0.0 bei +12 dB (−12/−6/−9,6/0 dB), designte Kette folgt der Ratio, Rampe bei Ratio-Wechsel, kein Clipping bei ausgeschaltetem Limiter.
- `:app` `EqControllerTest`: Ratio 0.5 (Standard/Auto) bzw. 0.8 (Box/Soundbar) an `setPlayerCurve`; manueller Band-Slider verändert die Player-Kurve lokal; A/B-Vergleich (EQ- und Original-Seite) erreicht das System mit eigenem Player.
- Golden-Files `e008-golden/*` neu aufgenommen: zusätzlich `headroom=` pro Schritt; inhaltlich geändert nur „band gain“ (Player-Kurve um 910 Hz) und „own player compare ORIGINAL“ (`inputGainDb` −3,83 statt 0).

