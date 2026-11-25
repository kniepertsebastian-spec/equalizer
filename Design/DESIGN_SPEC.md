# Cyber-Audio „Neon Dark“ – Design-Spezifikation und Umsetzungsplan

Stand: 24. November 2026, Branch `feat/cyber-ui-design` (Basis `11a7528`).
**Status:** Freigegeben. Die Entscheidungen E1–E5 stehen in §9.

Diese Datei ist die verbindliche Referenz für das Redesign. Vor jeder Screen-Gruppe werden diese Datei und die passenden Mockups erneut gelesen.
UI-Texte bleiben englisch und unverändert (`docs/DECISIONS.md`). Die Erklärungen hier sind deutsch.

---

## 0. Leitplanken (nicht verhandelbar)

1. **Nur Optik.** Unverändert bleiben:
   - App-Logik, Audio-/DSP-Engine (`:core`, `audio/`, `dsp/`) und ViewModels;
   - State-Bindings, Callbacks, Click-Handler, Navigation (Routen, Back-Verhalten);
   - Texte (`strings.xml`). Keine Funktion fällt weg.
2. **Keine neuen Permissions.** Die Manifeste werden nicht angefasst. `RECORD_AUDIO` und `Visualizer`/`SpectrumHub` werden für die UI weder angefordert noch genutzt.
3. **Signaturen bleiben stabil.** Öffentliche und interne Composables (`EqualizerScreen`, `PlayerScreen`, `NowPlayingScreen`, `Section`, `ChoiceChip` …) behalten ihre Parameter. Zusätzliche Parameter haben Default-Werte.
4. **Testbarkeit bleibt.** Erhalten bleiben alle Semantik-Hooks, die die Robolectric-UI-Tests nutzen:
   - Texte, `contentDescription`, `stateDescription`;
   - `Role.Tab`, `Role.Switch`, `Role.Button`;
   - Progress-Semantik (`SetProgress`), `toggleable`/`selectable`.
5. **Zugänglichkeit.**
   - Touch-Ziele mindestens 48 dp.
   - Text-Kontrast mindestens 4,5:1 (große Zahlen und Grafik mindestens 3:1).
   - Zustände nie nur über Farbe (Form, Text oder Icon zusätzlich).
6. **Performance.**
   - Zeichnen nur in `drawWithCache`/`drawBehind`; Brushes, Paths und Paints werden im Cache-Block angelegt.
   - Pro Frame keine Allokation und keine Recomposition: Animationswerte werden erst in der Draw-Phase gelesen.
   - Animationen laufen nur, wenn der Screen sichtbar ist **und** Wiedergabe oder Verarbeitung aktiv ist (§6).

---

## 1. Zuordnung der Mockups

Die Dateinamen bleiben unverändert (bereits in `11a7528` eingecheckt). Die Zuordnung ist hier verbindlich festgelegt.

Die PNGs (1080 × 2424) sind Screenshots eines Screenshots. Es zählt nur der innere Telefonrahmen (etwa x 36–1044 px, y 310–2110 px). Darin entsprechen ≈ 2,6 px einem dp. Inhalte wie „Car Uptempo – Kick Snap Max“, „NOSAM“ oder „+6,5 dB“ sind Beispieldaten; die App zeigt ihre echten Daten.

| Datei | Screen | Code-Einstieg |
|---|---|---|
| `Design/10881.png` | Equalizer → Tab **Sound** | `ui/equalizer/EqualizerScreen.kt` (`Head`, `GenreSection`), `EqualizerLayout.kt`, `GenrePresetList.kt`, `PresetPickers.kt` |
| `Design/10882.png` | Equalizer → Tab **Fine-tune** | `EqualizerScreen.kt` (`FineTuneSection`), `EqualizerControls.kt`, `ParametricBandsSection.kt`, `HighResolutionEqRow.kt` |
| `Design/10883.png` | Equalizer → Tab **Device** | `EqualizerScreen.kt` (`ProfilesSection`, `CorrectionSection`, `ContextSection`), `ContextModeChips.kt`, `AudioSourceList.kt` |
| `Design/10884.png` | **Now Playing** (Vollbild-Layer) | `ui/player/nowplaying/NowPlayingScreen.kt`, `SeekBar.kt`, `ui/player/PlayerControls.kt`, `PlayerQueueUi.kt` |
| `Design/10885.png` | Player → Tab **Radio** | `ui/player/PlayerScreen.kt`, `PlayerSections.kt`, `full/…/ui/player/catalog/RememberCatalogTabs.kt` |
| `Design/10886.png` | Player → Tab **Playlists** | `ui/player/library/PlaylistsTab.kt`, `PlaylistRows.kt`, `full/…/ui/player/SoundCloudLinkImport.kt` |
| `Design/10887.png` | Player → Tab **Search** | `PlayerSections.kt` (`searchItems`, `PillTextField`), `DiscoveryItems.kt` |
| alle außer 10884 | **Bottom-Bar** | `ui/navigation/AppNavHost.kt` (`NavigationBar`), `ui/player/mini/MiniPlayer.kt` |

---

## 2. Ist-Architektur (am Code geprüft)

- **UI:** 100 % Jetpack Compose (Material3, Navigation Compose, Hilt), keine XML-Layouts.
  - Module: `:app` (Flavors `store`/`full`), `:core` (JVM: DSP, Presets), `:player`.
- **Theme:** `ui/theme/`
  - `Color.kt` (Sonic-Tokens, `SonicAccentBrush`), `Theme.kt` (`AuraCurveTheme`, `darkColorScheme`, Shapes 6/12/16/20/28 dp, `PillShape`), `Type.kt` (Barlow, `SonicNumerals`), `Spacing.kt` (4/8/16/24/32 dp).
  - Canvas-Code liest das Token-Bündel `EqualizerDesignStyle`/`SonicStyle` (`ui/equalizer/EqualizerDesignStyle.kt`). Das ist der zentrale Hebel für das Retargeting.
- **Navigation:** `AppNavHost`
  - Routen `player` (Start), `home` (EQ), `services`, `diagnostics`.
  - Material3-`NavigationBar` mit zwei Items, darüber der `ShellMiniPlayer`.
  - Der `NowPlayingLayer` wird nur komponiert, solange er sichtbar ist (`sheet.isVisible`).
- **EQ:** `MainScreen` sammelt die Flows von `MainViewModel` und reicht rund 70 Parameter an `EqualizerScreen` weiter.
  - Der Kopf enthält Route, Titel, Quelle, `PowerKey`, `EqStatusLine`, `HeadroomBadge`, `ResponseGraph` (klappt beim Scrollen ein) und `BandReadout`.
  - Tabs: `EqualizerTab` SOUND / FINE_TUNE / DEVICE. Ab 600 dp zweispaltig.
- **Datenlage für die neuen Visuals:**
  - **Limiter:** `HeadroomWarningLevel` (SUFFICIENT / OCCASIONAL_LIMITING / HEAVY_LIMITING) aus `HeadroomWarningLevelCalculator`, schon heute im `HeadroomBadge`. Wird an die LED-Kette gebunden. `currentLevelDb` ist aus der Systemlautstärke abgeleitet und **keine** Messung.
  - **Spektrum:** In der App gibt es keinen Audio-Puffer. `SpectrumHub` (nur `full`) braucht `Visualizer`/`RECORD_AUDIO` und ist daher tabu. Ein echtes Spektrum aus dem eigenen Player-DSP ist geplant (`docs/PLAYER_REDESIGN.md` §4.3, P3-3), aber nicht umgesetzt.
  - **Wellenform:** keine Daten vorhanden.
  - **Wiedergabe:** `NowPlayingUi.isPlaying`, `EqStatusKind.ACTIVE` (die Engine hat das Anwenden des Effekts bestätigt).
- **Tests:**
  - `EqualizerScreenUiTest` speichert Screenshots nach `app/build/equalizer-screenshots/`.
  - Weitere: `LibraryUiTest`, `PlayerShellUiTest`, `NowPlayingUiTest`.
  - CI: `ktlintCheck`, `assembleFullDebug`, `testStoreDebugUnitTest`, `testFullDebugUnitTest`, `scripts/check_store_apk.py`.

---

## 3. Design-Tokens (verbindlich)

„Brief“ heißt: Wert aus dem Auftrag. „Mockup“ heißt: per Pixelmessung aus den PNGs bestätigt oder abgeleitet.

### 3.1 Farben

| Token | Hex / Alpha | Quelle | Verwendung |
|---|---|---|---|
| `CyberVoid` | `#070510` | Brief | App-Hintergrund (Basis) |
| `CyberVoidRaised` | `#0C071B` | Brief | obere Hintergrundzone, Sheets, Dialoge |
| `CyberSurface` | `#120D24`, α 0,82 (Spanne 0,78–0,88) | Brief, Mockup `#0F0F27` | Glas-Karten, Listenzeilen |
| `CyberSurfaceHigh` | `#1A1433`, α 0,90 | Mockup | inaktive Pills, Felder, Knopf-Mulden |
| `CyberBar` | `#05040C`, α 0,94 | Mockup `#03030D` | Bottom-Bar |
| `CyberOutline` | `#4F4C7B` | Mockup | Rand inaktiver Pills, Chips und Felder |
| `CyberGridLine` | `#8A7CFF`, α 0,05 | Brief (4–6 %) | Tech-Raster im Hintergrund |
| `CyberPurple` | `#8A2BE2` | Brief | Primärfarbe, Ambient-Glow, Spektrum-Balken |
| `CyberViolet` | `#9D4EDD` | Brief | Primärfarbe hell, Verlaufsmitte, Harshness-Ring (Anfang) |
| `SonicViolet` (bleibt) | `#A390FF` | bestehend | violetter Text, Icons, Outline-Buttons |
| `CyberCyan` | `#00F5FF` | Brief, Mockup `#44F2FB` | Akzent, aktive Ränder, Bass-Ring, Bar-Indikator |
| `CyberCyanText` | `#5FD9E6` | Mockup `#58D1D7` | kleiner cyanfarbener Text (Route, Artist, Links) |
| `CyberMagenta` | `#FF00AA` | Brief | Verlaufsanfang Rand und Pills, Tag-Chips |
| `CyberOrchid` | `#F64BF0` | Mockup `#FA4BEC` | Punch-Ring, Bass-Ende der Kurve, magentafarbener Text (Zeiten, „+ Add band“) |
| `CyberOrange` | `#FF5500` | Brief | Limiter-LEDs, Harshness-Ring (Ende) |
| `CyberRed` | `#FF3B30` | Brief | Limiter-LED-Spitze (nur HEAVY) |
| `CyberAmber` | `#FFA35F` | Mockup | Warn-Icon und Rand des Status-Banners |
| `CyberYellow` | `#FFD04A` | Mockup | gelegentliches Limiting, HUD-Statusquadrat |
| `SonicSignal` (bleibt) | `#4FD08A` | bestehend | echte Signalzustände (ACTIVE, Live-Status) |
| `SonicClip` (bleibt) | `#FF6B62` | bestehend, Mockup `#E58785` | Fehler (z. B. „HTTP 401“). Ist Korallrot, also klar getrennt vom Limiter-Orange. |
| `SonicPaper` (bleibt) | `#ECEAF6` | bestehend | Primärtext |
| `SonicSteel` (bleibt) | `#8E8CA8` | bestehend, Mockup `#86869E` | Sekundärtext |

### 3.2 Verläufe (Brushes)

Alle Verläufe liegen in `CyberTheme.brushes`. Sie werden nie pro Frame neu erzeugt.

| Brush | Definition | Verwendung |
|---|---|---|
| `borderBrush` | linear 135°: `#FF00AA` → `#8A2BE2` → `#00F5FF`, je α 0,70 | 1-dp-Rand von Karten und HUD-Rahmen |
| `pillBrush` | horizontal: `#A52CF2` → `#5A5FE0` → `#1E86C8` | Füllung aktiver Pills, Segmente und Chips. Weißer Text liegt über der Mitte; Kontrast ≥ 4,5:1 (Mockup-Ton `#6F83F4` nachgedunkelt, das Ende gegenüber dem Mockup-`#149FD0` ebenfalls, damit auch ein Label bis zum Rand lesbar bleibt) |
| `pillBorderBrush` | horizontal: `#FF00AA` → `#00F5FF` | 1,5-dp-Rand aktiver Pills. Dazu Glow in `CyberCyan` |
| `curveBrush` | nach Frequenz: `#F64BF0` (20 Hz) → `#A98BFF` (≈ 1 kHz) → `#3DF0FF` (20 kHz) | Antwortkurve, Mini-Kurven, Wellenform |
| `curveFill` | vertikal: Kurvenfarbe α 0,28 → α 0 | Fläche unter der Kurve |
| `spectrumBrush` | vertikal: `#8A2BE2` α 0,25 (unten) → `#B9A6FF` α 0,75 (oben) | Spektrum-Balken |
| `metalBrush` | Sweep: `#2A2B40`, `#8A88B0`, `#3B3C58`, `#C9C6E8`, `#2A2B40` sowie ein radialer Glanzpunkt oben links (`#FFFFFF` α 0,25 → 0) | Drehknöpfe, Power, Play |
| `ambient` | radial: `#8A2BE2` α 0,22 (oben links, r = 0,9 · Breite), `#00F5FF` α 0,08 (unten rechts, r = 0,8 · Breite), `#FF00AA` α 0,06 (unten links) | nur in der Hintergrund-Ebene, einmal gecacht (siehe E1) |

### 3.3 Formen und Radien

Wiederverwendet werden die Theme-Shapes; es gibt keine neuen Radien.

| Element | Form |
|---|---|
| Karten (`CyberCard`, `Section`) | `shapes.medium` (16 dp) |
| Listenzeilen (Genre, Sender, Playlist, Track) | `shapes.small` (12 dp) |
| Pills, Chips, Tabs, Segmente, dB-Pills, Text- und Suchfelder | `PillShape` |
| HUD-Rahmen | `shapes.small` plus Tech-Ecken (L-Winkel 10 dp lang, 2 dp Strich, in `borderBrush`) |
| Runde Buttons (Transport, Kopf, Play) | `CircleShape` |
| Bottom-Bar | eckig, nur eine Oberkante |

### 3.4 Linien und Ränder

| Element | Breite | Farbe |
|---|---|---|
| Karte | 1 dp | `borderBrush` |
| Inaktive Pill, Chip, Feld | 1 dp | `CyberOutline` |
| Aktive Pill, Chip, Segment | 1,5 dp | `pillBorderBrush` plus Glow |
| Feld fokussiert / unfokussiert | 1,5 dp / 1 dp | `CyberCyan` / `CyberCyan` α 0,55 |
| Graph-Raster | 1 px | `SonicRule` α 0,6 |
| 0-dB-Linie | 1 px | `SonicSteel` α 0,9 |
| Hintergrund-Raster | 1 px, Raster 24 dp | `CyberGridLine` |
| Bottom-Bar-Oberkante | 1 dp | Verlauf `CyberPurple` → `CyberCyan` → `CyberPurple`, α 0,8 |

### 3.5 Glow-Regeln

- **Technik:** `Modifier.cyberGlow(color, radius, alpha, shape)` zeichnet hinter dem Element eine Form mit `Paint` + `BlurMaskFilter`.
  - Das ist ab API 28 hardwarebeschleunigt, und minSdk ist 28.
  - Das Paint entsteht einmal in `drawWithCache`.
  - `RenderEffect`-Blur (erst ab API 31, teuer) wird nicht benutzt.
- **Erlaubt** (Radius / Alpha):
  - Power-Key **an**: Halo 18 dp, Violett und Cyan, pulsiert α 0,35 ↔ 0,6 in 2,4 s. Aus: kein Glow.
  - Play/Pause-Key: Halo 20 dp in `CyberPurple`, α 0,5. Bei Wiedergabe langsamer Puls.
  - Slider-Daumen, Knopf-Marker, Kurven-Knoten, Seek-Orb: 8 dp, α 0,6.
  - Aktive Pill, Segment oder Chip: 10 dp in `CyberCyan`, α 0,45.
  - Indikator in der Bottom-Bar: 12 dp in `CyberCyan`, α 0,6.
  - Karten: Rand-Glow 6 dp, α 0,12 (kaum sichtbar).
  - Radial-Visualizer, Wellenform, Spektrumspitzen: additiv (`BlendMode.Plus`) im eigenen Canvas.
- **Verboten:**
  - Glow auf Fließtext, Hintergrundflächen, inaktiven oder deaktivierten Elementen.
  - Echte Hintergrund-Unschärfe (Glassmorphism-Blur). „Glas“ ist hier: halbtransparente Füllung über dem statischen Hintergrund, ein Verlaufsrand und eine 1-px-Glanzkante oben (`#FFFFFF` α 0,06).
- **Text-Glow:** nur für die Überschrift „NOW PLAYING“ (Shadow, Blur 12, `CyberCyan` α 0,6).

### 3.6 Abstände

Die Werte aus `Spacing.kt` bleiben:

| Abstand | Wert |
|---|---|
| Screen-Rand horizontal | 16 dp (`medium`) |
| Kartenpadding | 16 dp |
| zwischen Karten | 12 dp (`small` + `extraSmall`) |
| Zeilen in Karten | 8 dp |
| Pill-Innenabstand horizontal | 16 dp |
| Mindesthöhe von Pills | 48 dp; sichtbare Höhe 40–44 dp, Rest als Touch-Fläche |

### 3.7 Typografie

Die Schriften bleiben Barlow und Barlow Semi Condensed (`Type.kt`). Es gibt keine neue Schrift.

| Element | Stil |
|---|---|
| Preset-Titel im EQ-Kopf | `headlineSmall` (24 sp SemiBold) |
| Kartentitel | `titleMedium` SemiBold |
| Route-Zeile | `labelMedium`, `CyberCyanText` |
| „NOW PLAYING“ | `labelLarge`, Letter-Spacing 0,18 em, `CyberCyan`, Text-Glow |
| dB-Pill unter dem Knopf | `SonicNumerals.value` (tabellarische Ziffern) |
| Achsen | `SonicNumerals.axis` |
| Zeiten im Seekbar | `SonicNumerals.axis`, `CyberOrchid` |
| „Live“-Label | `labelSmall` SemiBold, Letter-Spacing 0,08 em. Der Text „Live“ kommt aus `radio_live` (bestehend), Großschreibung nur per Stil. |

### 3.8 Zustände (nie nur über Farbe)

| Zustand | Optik | Zusätzliches Merkmal |
|---|---|---|
| aktiv / ausgewählt | `pillBrush`, weißer SemiBold-Text, Cyan-Glow | gefüllte Form, Semantik `selected` |
| inaktiv | `CyberSurfaceHigh`, Rand `CyberOutline`, Text `SonicSteel` | Outline statt Füllung |
| Bypass / EQ aus | Kurve gestrichelt, α 0,5. Spektrum aus, Power-Halo aus | Text „Off“ / „Bypass“ (bestehend) |
| nicht verfügbar | α 0,38, kein Glow | erklärender Text (bestehend, z. B. „Not available on this device…“) |
| Fehler | `SonicClip`, Icon | Fehlertext (bestehend) |
| Limiter | LED-Kette, siehe §5 A | Text „Heavy limiting“ / „Limiting sometimes“ / „OK“ (bestehend) |

---

## 4. Komponenten (Meilenstein 2)

Paket `com.auracurve.app.ui.components.cyber`. Alle Komponenten sind zustandslos: Werte rein, Callbacks raus.

| Komponente | API (Kurzform) | Ersetzt / genutzt in | Semantik |
|---|---|---|---|
| `Modifier.cyberBackdrop()` | – | Wurzel in `AppNavHost` und `NowPlayingLayer` | keine (dekorativ) |
| `CyberCard` | `(modifier, hud: Boolean = false, contentPadding, content)` | innen in `Section`, Genre- und Senderzeilen | keine eigene |
| `CyberPill` / `CyberSegmented` | `(text, selected, onClick, leadingIcon?)` | innen in `ChoiceChip`, `EqualizerTabs`, `PlayerTabs`, `PresetSourceTabs`, `ContextModeChips` | `selectable`, `Role.Tab` bzw. Button wie bisher |
| `CyberToggle` | `(checked, onCheckedChange?, enabled)` 52 × 30 dp | innen in `SwitchRow`, Bypass, `HighResolutionEqRow`, `GradientSwitch` | `toggleable(Role.Switch)`. Der Daumen zeigt ✓ oder –, also nicht nur Farbe. |
| `RotaryKnob` | `(value, valueRange, onValueChange, ring: Brush, label, valueText, step = 0.5f)` | Bass, Punch und Harshness in `FineTuneSection` | `contentDescription = label`, `stateDescription = valueText`, `progressBarRangeInfo` + `setProgress` (wie bisher der Slider) |
| `SpectrumEqGraph` | Parameter von `ResponseGraph` plus `spectrum: SpectrumMode` | Kopf aller EQ-Tabs | Band-Semantik von `ResponseGraph` 1:1 |
| `LimiterLedMeter` | `(level: HeadroomWarningLevel?)` | innen in `HeadroomBadge` | Text bleibt im Badge |
| `CyberPowerKey` | wie `PowerKey` | `PowerKey` | `toggleable(Role.Switch)` wie bisher |
| `CyberPlayKey` | `(playing, enabled, onClick, size)` | `PlayKey` (Now Playing, Mini-Player, Radio, Likes) | wie `PlayKey` |
| `CyberRoundButton` | `(icon, contentDescription, onClick, selected = false)` 48–56 dp | `RoundControlButton`, Shuffle/Repeat, Kopf-Buttons | wie bisher |
| `RadialVisualizer` | `(playing: Boolean, seed: Long, modifier)` | um das Cover in Now Playing | dekorativ, `clearAndSetSemantics {}` |
| `WaveformSeekbar` | Parameter von `SeekBar` plus `seed: Long` | innen in `SeekBar` | `seek_description`, Progress, Seek beim Loslassen (wie bisher) |
| `CyberBottomBar` | `(items: List<CyberNavItem>, selected, onSelect)` | `NavigationBar` in `AppNavHost` | `selectable(Role.Tab)`, Label sichtbar |
| `CyberTextField` | Parameter von `PillTextField` | innen in `PillTextField`, URL-Feld, Discovery-Felder | wie `TextField` |
| `CyberOutlineButton` | `(text, onClick, icon?, techCorners = true)` | Import- und Add-Buttons (Playlists, Device) | `Role.Button` |
| `GlowIconBadge` | `(icon, tint)` 40 dp | Genre-Zeilen, Listening-Device-Chips | dekorativ |
| `Modifier.hudCorners()` / `Modifier.cyberGlow()` / `Modifier.cyberBorder()` | – | überall | – |

**Drehknopf im Detail:**
- 270° Weg (−135° … +135°), 0 dB oben.
- 24 LED-Segmente, von der Mitte (0 dB) zum Wert hin leuchtend.
- Metallkappe mit Positionsmarker.
- Bedienung: vertikal ziehen, 1 dp = Bereich / 220. Optional radial (Winkel zum Mittelpunkt), wenn der Finger weiter als 0,6 · r vom Zentrum entfernt aufsetzt.
- Doppeltipp setzt auf 0,0 dB zurück.
- Haptik: `SegmentTick` an jeder 0,5-dB-Raste, `GestureThresholdActivate` beim Überqueren von 0 dB. Gleiches Verhalten wie heute im Graphen.
- Ringfarben: Bass `CyberCyan`, Punch `CyberOrchid`, Harshness Verlauf `CyberViolet` → `CyberOrange`.
- Darunter: dB-Pill (Rand in Ringfarbe), dann die bestehende Beschreibung (`macro_*_desc`).
- Wertebereiche wie bisher: Bass ±6, Punch ±6, Harshness ±2 dB (E5).

---

## 5. Screens

### A) Gemeinsamer EQ-Kopf (10881–10883)

- **Kopfzeile:** Route-Zeile in `CyberCyanText`, Preset-Titel. Der Button für die Quellenwahl (LibraryMusic) wird `CyberRoundButton`.
- **Power:** `CyberPowerKey`.
  - Metallkappe (`metalBrush`), Power-Icon.
  - An: animierter Halo Violett/Cyan (§3.5). Aus: dunkel, Icon `SonicSteel`, Ring `CyberOutline`.
  - `processing = masterEnabled && !bypass` bleibt die Quelle.
- **Status-Banner:** `EqStatusLine` als Glaskarte mit Rand in `CyberAmber`. Warn-Icon bei WAITING, CONNECTING und LOST; Rand in `SonicClip` bei ERROR; dünner Rand in `SonicSignal` bei ACTIVE. Chevron und Logik bleiben.
- **Limiter:** `HeadroomBadge` behält Text, Klick und Beschreibung und bekommt einen `LimiterLedMeter`. 6 Segmente in Treppenform:

  | `HeadroomWarningLevel` | Leuchtende Segmente | Farbe |
  |---|---|---|
  | `null` (EQ aus) | 0 | – |
  | SUFFICIENT | 2 | `SonicSignal` |
  | OCCASIONAL_LIMITING | 4 | `CyberYellow` → `CyberOrange` |
  | HEAVY_LIMITING | 6 | `CyberOrange` → `CyberRed`; das oberste Segment blinkt sanft nur bei aktiver Verarbeitung |

- **Graph:** `SpectrumEqGraph` (Weiterentwicklung von `ResponseGraph`).
  - Gesten, Haptik, TalkBack-Boxen, Einklappen und Achsen bleiben.
  - Neu ist die Optik: `curveBrush` mit 2,5 dp, `curveFill`, Knoten als Ringe mit Glow, der ausgewählte Knoten größer und gefüllt.
  - Spektrum-Balken (48 logarithmisch verteilte Balken) unter der Kurve (E2):
    - Höhe = Kurvenpegel an der Balkenfrequenz, normiert auf den Plot, plus eine geglättete, deterministische Bewegung.
    - Bewegung nur bei `EqStatusKind.ACTIVE` **und** laufender Wiedergabe im eigenen Player. Sonst stehen die Balken still auf Kurvenform bei α 0,35. Bei Bypass sind sie aus.
  - Im Tab DEVICE ist die Zielkurve stärker (3 dp, mehr Glow, keine Balken) und der Graph 16 dp höher (nicht im Compact-Layout).
- **Tabs:** `EqualizerTabs` als drei einzelne Pills.
  - Aktiv: `pillBrush` + `pillBorderBrush` + Glow.
  - Inaktiv: Outline. `Role.Tab` bleibt.

### B) Sound (10881)

- „More intensity“: `SwitchRow` mit `CyberToggle`.
- „Own player / Spotify & other apps“ (`PresetSourceTabs`): Pill-Switch mit zwei Segmenten in `CyberSegmented`.
- Genre-Zeilen (`GenrePresetRow`, `ExpandableGroupHeader`):
  - `CyberCard`-Zeile mit `GlowIconBadge`. Das Icon kommt aus dem bestehenden `genreIcon()`.
  - Badge-Farben: Terror `CyberOrange`, Uptempo `CyberCyan`, Gabber `CyberYellow`, Early `CyberViolet`, Frenchcore `#4A9BFF`, Dance `CyberOrchid`, Pop `#4A9BFF`, Rap `CyberCyan`, Flat `SonicSteel`.
  - Die aktive Zeile bekommt Rand `pillBorderBrush` und Häkchen (bestehend).
- Abschnittskopf „Genre“ in `CyberOrchid` (Mockup); „Paste link“ als Text-Button in `CyberCyanText`.
- Eigene Presets (`PresetGrid`, `PresetCard`): Glaskacheln. Die aktive Kachel bekommt die Pill-Rand-Optik.
- Module, `SoundGoalCard`, Compare: `CyberCard`. `HoldToCompareButton` als `CyberOutlineButton` ohne Tech-Ecken.

### C) Fine-tune (10882)

- Karte „Macro controls“: drei `RotaryKnob` nebeneinander. Unter 360 dp Breite in einer Reihe mit kleineren Knöpfen (min. 88 dp); im Zwei-Spalten-Layout ebenfalls drei nebeneinander.
- Zeile „Graphic EQ (n bands)“ + Bypass (`CyberToggle`).
  - Die Mini-Fader zeigen die **echten** Gains der Bänder (`graphBands`, nur Anzeige).
  - Bei 0 Bändern: gedimmte Platzhalter (E4).
  - Die `BandSliderRow`s darunter bleiben; `SonicSlider` bekommt Neon-Track und Daumen-Glow.
- „Parametric bands“: `CyberCard`, „+ Add band“ in `CyberOrchid`. Die LED-Deko aus dem Mockup entfällt, außer E4 entscheidet anders.
- „High-resolution EQ“: `CyberToggle`; der Hinweis „Not available…“ in `SonicClip` (bestehend).
- Der Hinweis `graph_note_interpolated` bleibt.

### D) Device (10883)

- `AudioSourceList` und `eq_playback_only_note` bleiben inhaltlich und werden als `CyberCard` dargestellt.
- „Sound profiles“ (`ProfilesSection`): Glaskarte, `TextButton`s in `CyberCyanText`.
- „My headphones“ (`CorrectionSection`):
  - Glaskarte; `CorrectionProfileRow` als zwei Glas-Kacheln.
  - Import als `CyberOutlineButton` in `SonicViolet`.
  - „Headphone mode“ als `CyberToggle`.
- „Listening device“ (`ContextModeChips`):
  - Chips mit Icon (Car `DirectionsCar`, Bluetooth speaker `Speaker`, Soundbar `Tv`, In-ear `Headphones`) in `CyberPill`.
  - Aktiv: `pillBrush`, Glow, ✓-Icon.
  - Die Logik `onContextModeChanged(if (selected) null else context)` bleibt.
- `HeadphonePowerSection`, `deviceTabFooter` (Remote-Server, Update-Hinweis): `CyberCard`.

### E) Now Playing (10884)

- **Hintergrund:** eigener `cyberBackdrop` mit stärkerem violettem Ambient-Licht oben.
- **Kopf:** Collapse und More als `CyberRoundButton` (Glas, Metallring). „NOW PLAYING“ in `CyberCyan` mit Tech-Linien links und rechts (Canvas, dekorativ).
- **Cover:**
  - Rundes `CoverImage` (Clip `CircleShape`).
  - Der Fortschrittsring (`ProgressRingCover`) bleibt als dünner Ring in `curveBrush`.
  - Außen `RadialVisualizer`: 96 Strahlen und Partikel, Farbe nach Winkel (`CyberOrchid` ↔ `CyberCyan`).
  - Spielt die Wiedergabe: synthetische Bewegung, deterministisch aus Track-Seed und Zeit.
  - Pause: eingefroren, α 0,35.
  - Laden: langsames Rotieren.
- **Titel und Artist:** Titel weiß SemiBold, Artist `CyberCyanText`. „Add to playlist“ als `CyberRoundButton`.
- **Seekbar:** `WaveformSeekbar`.
  - Stilisierte Wellenform, deterministisch aus der Track-ID (E3).
  - Gespielter Teil in `curveBrush` mit Glow, Rest gedimmt.
  - Energie-Orb am Positionspunkt (weißer Kern, violetter Halo).
  - Zeiten in `CyberOrchid`. Semantik und Seek-on-release bleiben.
- **Transport:** Shuffle, Prev, Next, Repeat als `CyberRoundButton` (52 dp; aktiver Shuffle/Repeat mit Cyan-Ring und bestehendem Icon-Wechsel). Mitte `CyberPlayKey` 80 dp (Glas/Metall, Halo).
- **Output-Zeile:** bleibt (Icon + Text in `SonicSteel`).
- **Preset-Karte:**
  - HUD-Rahmen (`hudCorners`, `borderBrush`).
  - Links `MiniCurve` (`soundCurve`) in `curveBrush`, dann Name und Kontext.
  - `EqStatusLamp` als kleines Quadrat (Form bleibt Zustandsträger).
  - Rechts, abgetrennt, der Queue-Button mit Anzahl. Die Klicks gehen weiter auf `onOpenSound` und `onOpenQueue`.
- **Weitere Elemente:** `PreviewTag`, `NoticeRow`, Retry und die Landscape-Variante bleiben in Funktion und bekommen dieselbe Optik.
- **Mini-Player:** Glasleiste mit Neon-Oberkante. Das Cover bekommt einen Fortschrittsring in `curveBrush`; Play/Pause wird zum kleinen `CyberPlayKey`.

### F) Player: Radio, Playlists, Search (10885–10887)

- **Kopf:**
  - „Player“ (`headlineSmall`).
  - `SourceSelector` als Pill mit `CyberCyan`-Glow-Rand.
  - Link-Button als `CyberRoundButton`.
- **Banner:** `EqStatusLine` wie in A.
- **Sub-Tabs:** `PlayerTabs` als Neon-Pills (wie die EQ-Tabs), horizontal scrollbar.
- **Radio:**
  - `GenreChips` als `CyberPill`.
  - Senderzeilen (`LibraryRow`, Variante `stationRow`) als `CyberCard`.
  - Die Untertitel-Zeile wird geteilt: Der erste Teil (`radio_live`) erscheint als Label in Großbuchstaben, in `SonicSignal` mit Punkt. Land und Format folgen in `SonicSteel`. Der Text bleibt gleich, nur die Darstellung ändert sich.
  - Rechts ein runder `CyberPlayKey` (44 dp sichtbar, 48 dp Touch). Den Pulsring bekommt nur der gerade spielende Sender; die anderen haben einen statischen Ring.
  - Menü ⋮ bleibt.
  - Mockup-Varianten der Live-Farbe (Cyan, Magenta, Orange) werden **nicht** übernommen: Grün ist hier ein echter Status.
- **Playlists:**
  - Import-Buttons als `CyberOutlineButton` mit Tech-Ecken und Cyan-Glow.
  - URL-Feld als `CyberTextField` mit Glow-Rand; „Add“ als violetter Outline-Button.
  - Fehlertext in `SonicClip` (bestehend).
  - „Likes“ mit glühendem `CyberPlayKey`.
  - Playlist-Zeilen als Glaszeilen.
- **Search:**
  - `CyberTextField` mit Cyan-Glow; der Suchknopf als runder `pillBrush`-Kreis mit Pfeil.
  - Karte „Interesting new uploads“ (`DiscoveryItems`) im HUD-Rahmen.
  - Tag-Chips (z. B. „unproven ×“) in `CyberMagenta`-Outline.
  - Genre-Pills als Neon-Pills; „Like my music“-Aktion mit `pillBrush`.
  - Leere Zustände und Fehler unverändert.
- **Playlist-Detail, `LinkedServicesScreen`, Diagnostics:** Karten und Buttons übernehmen die Komponenten; es gibt keine eigenen Mockups.

### G) Bottom-Bar

- `CyberBottomBar`: `CyberBar`-Glas, Neon-Oberkante (§3.4).
- Aktiver Tab:
  - Leuchtender Trapez-Indikator in `CyberCyan` an der Oberkante (Glow 12 dp).
  - Icon in `CyberCyan`, Label `SonicPaper` SemiBold.
- Inaktive Tabs: `SonicSteel`.
- Items, Labels (`nav_player`, `nav_equalizer`), Icons und `navigateTo(...)` bleiben.
- Navigationsleisten-Inset wie bei `NavigationBar` (`windowInsetsPadding(NavigationBarDefaults.windowInsets)`).

---

## 6. Animation und Sichtbarkeit

| Animation | Läuft nur, wenn | Technik |
|---|---|---|
| Power-Halo | EQ-Screen komponiert **und** `processing` | `rememberInfiniteTransition`, Wert erst in `drawBehind` gelesen |
| Spektrum-Balken | EQ-Screen komponiert **und** `EqStatusKind.ACTIVE` **und** Player spielt **und** Lifecycle ≥ RESUMED | `withFrameNanos`-Schleife in `LaunchedEffect(active)`, Werte in einem vorab angelegten `FloatArray(48)`, Invalidierung über einen `mutableLongStateOf`-Tick nur in der Draw-Phase, max. 30 fps |
| Radial-Visualizer | `NowPlayingLayer` sichtbar **und** `isPlaying` **und** RESUMED | wie oben, 96 Strahlen, kein Path-Neubau (Linien per `drawLine`) |
| Seek-Orb-Puls | `isPlaying` | `infiniteTransition` |
| Pulsring Radio | Sender spielt gerade | `infiniteTransition` |
| LED-Spitze HEAVY | `HEAVY_LIMITING` **und** `processing` | `infiniteTransition` (1,2 s) |

- Die EQ-Route wird von `NavHost` beim Tabwechsel entsorgt, Now Playing nur bei `sheet.isVisible` komponiert. Zusätzlich prüft ein Helfer `rememberIsResumed()` den Lifecycle. Im Hintergrund oder bei ausgeschaltetem Display läuft nichts.
- **Reduzierte Bewegung:** Bei `Settings.Global.ANIMATOR_DURATION_SCALE == 0` zeichnen alle Visuals statisch.
- **Tests:** Animationen sind deterministisch (Seed und Frame-Zeit). Robolectric-Screenshots zeigen den statischen Zustand.

---

## 7. Datenbindung und Ehrlichkeit (`CLAUDE.md` §4)

| Visual | Datenquelle | Echt? |
|---|---|---|
| Antwortkurve, Knoten, Achsen | `combinedCurve`, `graphBands` (bestehend) | **ja** (unverändert berechnet) |
| Limiter-LEDs | `HeadroomWarningLevel` (bestehend) | **ja**: Vorhersage aus der Kurve, keine Pegelmessung (wie heute) |
| Spektrum-Balken | Kurvenform + Wiedergabestatus | **nein**: Visualisierung, keine Messung. Kein Text behauptet eine Messung. TalkBack bekommt keine eigene Beschreibung. |
| Radial-Visualizer | `isPlaying`, Track-Seed | **nein**: dekorativ, `clearAndSetSemantics` |
| Wellenform im Seekbar | Track-ID-Seed, Position und Dauer echt | Position und Dauer **ja**, Wellenform **nein** |
| Mini-Fader (Graphic EQ) | `graphBands` | **ja** |
| Mini-Kurve in der Preset-Karte | `soundCurve` | **ja** |

**Zukunft:** Sobald P3-3 (Spektrum aus dem eigenen Player-DSP, `docs/PLAYER_REDESIGN.md` §4.3) umgesetzt ist, bekommen `SpectrumEqGraph` und `RadialVisualizer` echte Werte über denselben `FloatArray`-Eingang, ohne neue Permission.

---

## 8. Umsetzungsplan (Meilensteine)

Jeder Meilenstein läuft so ab:

1. Spec und Mockups erneut lesen.
2. Umsetzen.
3. Prüfen, Fehler beheben:
   - `./gradlew ktlintCheck`
   - `./gradlew assembleDebug` (beide Flavors)
   - `./gradlew testStoreDebugUnitTest testFullDebugUnitTest`
4. `MockupScreensTest` laufen lassen und `app/build/mockup-screenshots/` mit den Mockups vergleichen (§10).
5. Commit und Push nach `feat/cyber-ui-design`.

| # | Commit | Dateien |
|---|---|---|
| 1 | **Spec + CyberTheme + Basis-Modifier/Brushes** | `Design/DESIGN_SPEC.md`; neu `ui/theme/CyberColor.kt` (Tokens §3.1), `ui/theme/CyberTheme.kt` (`CyberTheme.colors/brushes/glow/dims`), `ui/theme/CyberModifiers.kt` (`cyberBackdrop`, `cyberGlow`, `cyberBorder`, `glassSurface`, `hudCorners`); `Theme.kt` (`darkColorScheme` auf Cyber-Tokens, sodass Dialoge, Menüs und Sheets mitziehen); `EqualizerDesignStyle.kt` (`SonicStyle` auf Cyber-Werte); `AppNavHost.kt` (Backdrop an der Wurzel, Container transparent) |
| 2 | **Komponenten** | neu unter `ui/components/cyber/`: `CyberCard`, `CyberPill`, `CyberToggle`, `RotaryKnob`, `SpectrumEqGraph` (+ reine Logik `SyntheticSpectrum`), `LimiterLedMeter`, `CyberButtons` (Power, Play, Round, Outline), `RadialVisualizer`, `WaveformSeekbar` (+ reine Logik `WaveformShape`), `CyberBottomBar`, `CyberTextField`, `GlowIconBadge`; JVM-Unit-Tests für `SyntheticSpectrum`, `WaveformShape` und die Knopf-Mathematik (Winkel ↔ Wert, Rasten) |
| 3 | **EQ-Screens** (Sound, Fine-tune, Device) + Bottom-Bar | `EqualizerScreen.kt`, `EqualizerLayout.kt`, `ControlComponents.kt`, `EqualizerControls.kt`, `ResponseGraph.kt` (Delegation an `SpectrumEqGraph`), `GenrePresetList.kt`, `PresetPickers.kt`, `ParametricBandsSection.kt`, `HighResolutionEqRow.kt`, `ContextModeChips.kt`, `SoundGoalCard.kt`, `AudioSourceList.kt`, `DeviceKindCard.kt`, `ui/eq/EqStatus.kt`, `MainScreen.kt` (nur ein zusätzlicher Parameter `playerPlaying` mit Default), `AppNavHost.kt` (`CyberBottomBar`, Wert `playerPlaying`) |
| 4 | **Now Playing** (+ Mini-Player) | `nowplaying/NowPlayingScreen.kt`, `SeekBar.kt`, `PlayerControls.kt`, `PlayerQueueUi.kt`, `mini/MiniPlayer.kt`, `shell/PlayerShell.kt` (nur Backdrop) |
| 5 | **Player-Screens** (Radio, Playlists, Search) + Feinschliff | `PlayerScreen.kt`, `PlayerSections.kt`, `DiscoveryItems.kt`, `library/*`, `full/…/RememberCatalogTabs.kt`, `full/…/SoundCloudLinkImport.kt`, `LinkedServicesScreen.kt`, `queue/QueueSheet.kt`, `sound/SoundSheet.kt`, `SignalChainSheet`, Diagnostics-Karten; abschließender Screenshot-Abgleich aller sieben Mockups |

**Nicht angefasst:**
- `:core`, `:player`, `audio/`, `dsp/`, `remote/` (außer Karten-Optik), ViewModels, Repositories, Manifeste;
- `strings.xml` (keine neuen Texte nötig);
- Gradle-Abhängigkeiten. `material-icons-extended` ist bereits eingebunden, eine neue Bibliothek ist nicht geplant.

---

## 9. Entscheidungen (vom Nutzer bestätigt)

| # | Frage | Entscheidung |
|---|---|---|
| **E1** | `CLAUDE.md` §2–3 („Sonic Control“) verbietet Glassmorphism, Neon-Ränder und Hintergrundverläufe und erlaubt Glow nur auf Power-Key und Slider-Daumen. Das neue Design braucht genau das. | **Ausnahme für dieses Redesign.** Die Abweichungen von `CLAUDE.md` §2–3 sind dieses Mal erlaubt; danach wird das Ergebnis gemeinsam bewertet. `CLAUDE.md` wird **nicht** umgeschrieben. Die Grundsätze gelten weiter: Kurve ist Mittelpunkt, Grün nur für Signal, Zustände nicht nur über Farbe. |
| **E2** | Die Spektrum-Balken sind ohne `RECORD_AUDIO` nicht messbar. | Synthetisch nach Kurvenform; Bewegung nur bei bestätigter Verarbeitung (`EqStatusKind.ACTIVE`) + Wiedergabe; nie als Messung bezeichnet. |
| **E3** | Für die Wellenform im Seekbar gibt es keine Daten. | Stilisierte, pro Track feste Form (Seed aus der Track-ID). |
| **E4** | Mini-Fader und LEDs neben „Graphic EQ“ und „Parametric bands“ sind im Mockup Deko. | Graphic EQ: Mini-Fader mit echten Band-Gains. Parametric: keine Fake-LEDs. |
| **E5** | Wertebereiche der Drehknöpfe | Bestehende Bereiche bleiben (Bass ±6, Punch ±6, Harshness ±2 dB). Das „+6,5 dB“ im Mockup ist Beispieltext. |
| **H1** | Der Equalizer soll nach Hardcore aussehen: „Dunkel, dreckig, Untergrund, ‚asozial‘“, weniger knallig, keine langweiligen Buttons. Das widerspricht `CLAUDE.md` §3 (Violett-Blau-Akzent, keine Texturen, Glow nur auf Power-Key und Daumen). | **Ausnahme für den Equalizer-Bereich** (Kopf, Kurve, Tabs Sound / Fine-tune / Device, Bottom-Bar auf der EQ-Route), nach dem bestätigten Entwurf. Player, Now Playing und Mini-Player bleiben im Cyber-Look. Akzent ist Blutrot statt Violett-Blau. Erlaubt sind Beton- und Kratz-Texturen, Gefahrenstreifen für Warnungen und kleine LED-Glows an Lampen und Anzeigen. Grün (`toxic`) bleibt Signal-Zustand. Zustände werden weiter auch über Form und Text gezeigt (Taste gedrückt, Lampe an/aus, Schieber-Position, Wörter). `CLAUDE.md` wird **nicht** umgeschrieben. Umsetzung: `ui/theme/HardcoreTheme.kt`, `HardcoreTextures.kt` und `ui/components/hardcore/`. Der Schalter ist `LocalHardcoreLook`, den `EqualizerScreen(hardcoreLook = true)` setzt. Die gemeinsamen Bausteine verzweigen darauf; Logik, Texte und Semantik bleiben gleich. |

## 10. Vergleichs-Screenshots

`app/src/testFull/java/com/auracurve/app/ui/MockupScreensTest.kt` zeichnet die sieben Screens mit Beispieldaten nahe an den Mockups (393 × 873 dp, Robolectric) nach `app/build/mockup-screenshots/`:

```
./gradlew :app:testFullDebugUnitTest --tests '*MockupScreensTest*'
```

Die Bilder werden nicht verglichen und nicht eingecheckt; sie dienen dem Vorher/Nachher nach jedem Meilenstein.
