# Roadmap 09 – 32-Bit-Float-Pipeline, Soft-Clipper & Limiter-Abstimmung (Punch & „Schallern“)

**Priorität:** Hoch (Blocker B2, Major M1, Minor m4, Restpunkt `P4-7`)  
**Ziel:** Stillen DSP-Komplettausfall bei Float-/24-Bit-Audio beheben, 32-Bit-Float-Verarbeitung bis `DefaultAudioSink` durchreichen, doppeltes Brickwall-Limiting auflösen und dem eigenen Player druckvollen, analogen Kick-Punch geben.  
**Abhängigkeit:** **Roadmap 08** abgeschlossen (und idealerweise **Roadmap 11** wegen `AudioPlayerService.kt`).

---

## Ausgangslage / Befund

1. **Stiller DSP-Komplettausfall bei Nicht-PCM16 (Blocker B2 / `P4-7`):**  
   `PlayerDspAudioProcessor.onConfigure()` (`PlayerDspAudioProcessor.kt:30-40`) liefert für alles außer `C.ENCODING_PCM_16BIT` `AudioFormat.NOT_SET` zurück. ExoPlayer überspringt den Prozessor lautlos, während `EqController` den System-EQ auf `0 dB` nullt — Ergebnis: komplett unbearbeitetes Audio bei Float-Output oder 24-Bit-FLAC. Media3 1.11.1 unterstützt in `SonicAudioProcessor` bereits Float-Audio (siehe Kommentar in `PlayerDspPcm16.kt:18-21`).
2. **Doppeltes Brickwall-Limiting (Major M1):**  
   Im eigenen Player laufen zwei Limiter in Serie: erst `LookaheadLimiter` (`-1.0 dB`, `5 ms` Lookahead, ∞:1) in `PlayerDspPcm16.kt:125`, danach `DynamicsProcessing.Limiter` (`2 ms` Attack, `10:1`) in `AndroidAudioEngine.kt:417-429`. Der `LookaheadLimiter` ignoriert `settings.limiterThresholdDb` und rundet Transienten bei heißer Aussteuerung bereits 5 ms vor dem Kick ab.
3. **Pro-Buffer-Allokationen (Minor m4):**  
   `inputBuffer.asShortBuffer()` und `output.asShortBuffer()` erzeugen pro Audio-Buffer Heap-Wrapper auf dem Echtzeit-Audio-Thread (`PlayerDspAudioProcessor.kt:54, 63`).

---

## Zugewiesener Dateiumfang

- `core/src/main/kotlin/com/auracurve/app/dsp/SoftClipper.kt` *(neu)* + `SoftClipperTest.kt` *(neu)*
- `core/src/main/kotlin/com/auracurve/app/dsp/LookaheadLimiter.kt` (+ Tests)
- `core/src/main/kotlin/com/auracurve/app/dsp/PlayerDspPcm16.kt` *(Refaktorierung auf gemeinsamen Float/Double-Kern + PCM16-Wrapper)*
- `player/app/src/main/java/com/auracurve/app/player/playback/PlayerDspAudioProcessor.kt` (+ Media3-Unit-Test)
- `player/app/src/main/java/com/auracurve/app/player/playback/PlayerDspState.kt` *(optional für Output-Drive / Soft-Clipper-Switch)*
- `player/app/src/main/java/com/auracurve/app/player/service/AudioPlayerService.kt` *(nur `buildRenderersFactory()`)*
- *(Optional – nur wenn nicht parallel zu RM 13)* `app/src/main/java/com/auracurve/app/audio/AndroidAudioEngine.kt` *(Limiter-Sync für eigene Session)*

---

## Arbeitspakete

### P9-1 – 32-Bit-Float-Audio-Pipeline (`P4-7`, Blocker B2, Minor m4)
- [x] ~~`.setEnableFloatOutput(true)` explizit aktivieren~~ → **bewusst abweichend: explizit `false`.** Verifikation am Bytecode von Media3 1.11.1: Sonic nimmt Float (`SonicAudioProcessor.configure()` akzeptiert PCM16 und Float), aber `DefaultAudioSink.configure()` lässt bei `enableFloatOutput && isEncodingHighResolutionPcm` die komplette `AudioProcessorChain` weg (nur `ToFloatPcmAudioProcessor`). Mit `true` liefen Klangkette und Sonic bei 24-Bit-/Float-Quellen gar nicht – genau der Ausfall aus B2. Float bis zur Senke kommt stattdessen aus dem Prozessor selbst (PCM16 rein → `ENCODING_PCM_FLOAT` raus) über die neue `PlayerAudioProcessorChain`.
- [x] `PlayerDspPcm16` aufgeteilt: `PlayerDspChain` (`processFloat`, `processPcm16ToFloat`, `beginBuffer`/`processBlock`) und PCM16-Adapter `PlayerDspPcm16` mit `TpdfDither` nur für den 16-Bit-Ausgang.
- [x] `PlayerDspAudioProcessor.onConfigure()` für `C.ENCODING_PCM_FLOAT` **und** `C.ENCODING_PCM_16BIT` freigeben (zusätzlich 24/32 Bit Integer):
  - Bei `ENCODING_PCM_FLOAT`: direkt auf 32-Bit-Floats arbeiten (kein Dither, kein 16-Bit-Clipping vor `DynamicsProcessing`, voller Headroom).
  - Bei `ENCODING_PCM_16BIT`: bestehender PCM16-Pfad als Fallback für Geräte ohne Float-Output.
  - Bei 24-Bit/32-Bit-Int-Quellen prüfen, ob ExoPlayer automatisch zu `PCM_FLOAT` oder `PCM_16BIT` konvertiert oder `ToInt16PcmAudioProcessor` / `ENCODING_PCM_24BIT`-Konvertierung nötig ist, sodass der Prozessor nie still inaktiv bleibt.
    → Geprüft: Mit `enableFloatOutput = false` steht `ToInt16PcmAudioProcessor` immer vor der Kette, der Prozessor bekommt PCM16. Er liest trotzdem selbst PCM 16/24/32 Bit und Float; nur andere Formate (kommen hinter dieser Senke nicht vor) führen zu `NOT_SET` mit Log-Warnung statt Wiedergabeabbruch.
- [x] `asShortBuffer()` / `asFloatBuffer()` pro Buffer entfernt: absolute `getShort/getFloat/putFloat` auf den Media3-Puffern (native Byte-Reihenfolge), Arbeits-Array wird wiederverwendet.

### P9-2 – Doppeltes Limiting auflösen & Limiter sauber abstimmen (Major M1)
- [x] Klare Zuständigkeit zwischen Player-Limiter und System-Limiter festlegen: **Weg A** (System-`DynamicsProcessing` macht die Lautheit, `AndroidAudioEngine.kt` unverändert; Player-Limiter = schaltbares Ceiling `PlayerDspSettings.SAFETY_CEILING`, −0,2 dBFS / 2 ms):
  - **Empfohlener Weg A (mit 32-Bit-Float + aktivem System-`DynamicsProcessing`):**  
    Im Float-Pfad das Signal mit sauberem Headroom (oder sanftem Soft-Clipper) an `DynamicsProcessing` (MBC + 2-ms-Attack-Limiter) übergeben; `LookaheadLimiter` nur noch als schaltbares Sicherheits-Ceiling (`-0.2 dBFS`, `1.5–2.0 ms` Lookahead statt `-1.0 dB` / `5 ms`) oder wenn System-`DynamicsProcessing` nicht verfügbar ist.
  - **Alternativer Weg B (Player-Limiter als Master):**  
    `limiterEnabled` und `limiterThresholdDb` aus Preset/Context synchron an `PlayerDspChain` übergeben und den System-Limiter auf der eigenen Player-Session deaktivieren *(Änderung an `AndroidAudioEngine.kt` dann mit RM 13 abstimmen)*.
- [x] Lookahead-Fenster für den Standardbetrieb von `5 ms` auf `2.0 ms` verkürzt (im Player; die generischen Defaults von `LookaheadLimiterSettings` bleiben unverändert, der Wert war schon konfigurierbar).

### P9-3 – Sanfter Soft-Clipper & optionaler Output-Drive (`0 bis +4 dB`)
- [x] Allokationsfreie `SoftClipper`-Klasse in `:core` implementieren:
  - Unterhalb eines Kniepunkts (z. B. `-2,5 dBFS` ≈ `0.75`): 100 % linear (unverfärbte Dynamik).
  - Oberhalb des Kniepunkts: stetig differenzierbare `tanh`-Sättigung hin zu `±0.98` (erzeugt warmen, dichten Kick-Druck ohne harte Clipping-Kanten).
- [x] In der Player-DSP-Kette **vor** dem finalen Safety-Ceiling platzieren (Standard an, schaltbar über `PlayerDspSettings.softClipEnabled`).
- [x] Output-Drive `0.0 bis +4.0 dB` vorgesehen (`PlayerDspSettings.outputDriveDb`, Standard 0 dB, geklemmt, in `PlayerDspState` gespeichert) – **ohne Regler in der Oberfläche** (Sound-Sheet liegt außerhalb des Dateiumfangs, siehe offene Punkte).

---

## Abnahmekriterien & Verifikation

- [x] **Unit-Tests `:core`:**
  - `SoftClipperTest`: unterhalb des Kniepunkts Fehler `< 0.01 dB`, oberhalb monoton steigend, asymptotisch begrenzt `< 1.0f`, keine NaN/Inf.
  - `PlayerDspChainTest`: Float-Pfad und PCM16-Pfad liefern im linearen Bereich identische RMS-Pegel (±0,1 dB).
- [x] **Unit-Tests `:player`:** (geschrieben; Nachweis über CI, lokal kein Android-SDK)
  - `PlayerDspAudioProcessorTest`: `onConfigure(ENCODING_PCM_FLOAT)` gibt `ENCODING_PCM_FLOAT` zurück (`isActive == true`); Verarbeitung verändert Testsignal entsprechend Kurve/Exciter.
- [x] **Build & Lint:** `:core:test` + `:core:ktlintCheck` lokal grün; Android-Module über CI
  ```bash
  ./gradlew :core:test :player:testFullDebugUnitTest :app:testFullDebugUnitTest :core:ktlintCheck :app:ktlintCheck
  ```
- [ ] **Gerätetest / Hörprobe:** offen (kein Gerät)
  - 10 Minuten Wiedergabe bei **gesperrtem Bildschirm** (Little-Cores) ohne Aussetzer/Stottern.
  - A/B-Hörtest (Spotify vs. eigener Player, Preset „Knall“): eigener Player erreicht mindestens gleiche Lautheit bei saubererem, druckvollerem Kick.

---

## Abschlussbericht (Stand: Umsetzung, Gerätetest offen)

**Abhängigkeit:** Roadmap 08 und 11 liegen in diesem Repository nicht vor (weder unter
`roadmaps_in_progress` noch `roadmaps_finished`); bearbeitet auf dem aktuellen `main`.
`AudioPlayerService.kt` nur in `buildRenderersFactory()` geändert, `AndroidAudioEngine.kt` nicht
angefasst (Weg A, keine Überschneidung mit RM 13).

### Kette im eigenen Player (neu)

`ToInt16PcmAudioProcessor` (Media3) → `SilenceSkippingAudioProcessor` (nur aktiv, wenn
eingeschaltet) → **`PlayerDspAudioProcessor`**: PCM16 → Float → Kurve/Subsonic → Virtual Bass →
Mono-Bass → Drive (0…+4 dB) → Soft-Clipper → Ceiling (−0,2 dBFS, 2 ms) → `ENCODING_PCM_FLOAT` →
`SonicAudioProcessor` → Float-`AudioTrack` → System-`DynamicsProcessing` (MBC + Limiter).

### Geänderte / neue Dateien
- `core/.../dsp/SoftClipper.kt` *(neu)*, `PlayerDspChain.kt` *(neu, inkl. `PlayerDspSettings`)*,
  `PlayerDspPcm16.kt` *(jetzt PCM16-Adapter + `TpdfDither`)*
- `player/.../playback/PlayerDspAudioProcessor.kt`, `PlayerAudioProcessorChain.kt` *(neu)*,
  `PlayerDspState.kt`, `player/.../service/AudioPlayerService.kt` (`buildRenderersFactory()`)
- Tests: `SoftClipperTest` *(neu)*, `PlayerDspChainTest` *(neu)*, `PlayerDspAudioProcessorTest`
  *(neu, Robolectric)*, `LookaheadLimiterTest` (+1), angepasst: `PlayerDspPcm16Test`,
  `CurveEqualizerTest`, `BlockProcessingTest` (Referenzkette mit Soft-Clipper und neuem Ceiling)
- Doku: `docs/DECISIONS.md` (neuer Abschnitt), `docs/DSP_VIRTUAL_BASS.md` (Verweise)

### Entscheidungen
- **`setEnableFloatOutput` bleibt aus** (siehe P9-1): in Media3 1.11.1 würde der Schalter die
  Prozessorkette für hochauflösende Quellen abhängen. Float entsteht im Prozessor.
- **Eigene `AudioProcessorChain`**, weil `SilenceSkippingAudioProcessor` in
  `DefaultAudioProcessorChain` *hinter* den eigenen Prozessoren sitzt und Float mit einer
  Exception ablehnt – jede Wiedergabe wäre gescheitert.
- **Soft-Clipper:** `|y| = k + (L−k)·tanh((|x|−k)/(L−k))`, k = 0,75, L = 0,98; Wert und Steigung
  am Knie stetig, NaN → 0.
- **Ceiling −0,2 dBFS / 2 ms** statt −1 dB / 5 ms; `limiterThresholdDb` des Presets wirkt weiter
  nur im System-Limiter (Weg A).

### Tests
- Lokal: `./gradlew :core:test :core:ktlintCheck` grün (Baseline vorher ebenfalls grün).
  Neu u. a.: Soft-Clipper linear unter dem Knie (< 0,01 dB), monoton, < 1,0, keine NaN/Inf;
  Float- und PCM16-Pfad gleicher RMS (±0,1 dB); Float-Pfad behält Headroom > 0 dBFS ohne
  Ceiling; Drive +3 dB = +3 dB; Ceiling hält −0,2 dBFS und verzögert um 2 ms.
- `:player`/`:app` (`testFullDebugUnitTest`, Lint): lokal nicht baubar (kein Android-SDK);
  Nachweis über die CI des Pull Requests.

### Offene Punkte
1. **Gerätetest:** Wiedergabe mit Float-`AudioTrack` (inkl. Geschwindigkeit ≠ 1, Seek,
   Titelwechsel), 10 Minuten bei gesperrtem Bildschirm ohne Aussetzer.
2. **A/B-Hörtest** gegen Spotify mit Preset „Knall“; ggf. Knie/Ceiling des Soft-Clippers nachstimmen.
3. **Drive-Regler** im Player-Sound-Sheet (oder Kopplung an „Knall“/`HeadphonePower`) – eigene
   UI-Entscheidung; bis dahin bleibt der Drive bei 0 dB.
4. **24-Bit-Auflösung:** Quellen > 16 Bit werden vor der Kette auf 16 Bit gekürzt (Media3 1.11.1
   kann Float-Eingang und Prozessorkette nicht kombinieren). Erst mit einer eigenen Senke oder
   einer späteren Media3-Version lösbar.
