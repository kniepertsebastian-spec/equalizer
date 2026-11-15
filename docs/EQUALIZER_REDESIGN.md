# Equalizer-Umbau · Umsetzungsplan zur Freigabe

**Stand:** 15.11.2026 · **Status:** **Freigegeben am 15.11.2026** mit E1 = drei Reiter + Signalketten-Sheet, E2–E4 wie empfohlen; Bedingung: „solange der optimale Klang nicht leiden muss“ (AuraCurve-Roadmap 04, Teil A).  
**Grundlage:** Review [`eq-review.md`](roadmaps_in_progress/auracurve/eq-review.md), Abschnitt 5.1 (Entwürfe `screenshots/01` und `02`, die **nicht im Repository liegen**; dieser Plan stützt sich auf die Textbeschreibung), `CLAUDE.md` (Kurve als Herzstück, Sonic-Control-Richtlinien).  
**Regel:** Kein Produktionscode, bevor der Plan freigegeben ist (`CLAUDE.md`, Abschnitt 1).

---

## 1 · Ist-Stand (am Code geprüft)

`ui/equalizer/EqualizerScreen.kt` (925 Zeilen) ist **eine** senkrecht scrollende Spalte (`verticalScroll`). `MainScreen.kt` setzt darüber den Update-Hinweis (nur `full`) und darunter fest „More tools“.

| # | Abschnitt heute | Inhalt |
|---|---|---|
| 1 | Kopf | Ausgang und Preset, Quellenwahl, Ein/Aus (`PowerKey`), Statuszeile (`EqStatusLine`), „Erneut versuchen“, Hinweis „keine Audio-Sitzung“, Liste der Audio-Sitzungen (`AudioSourceList`), Hochauflösung (`HighResolutionEqRow` mit Pre-EQ-Modus), Frage nach der Geräteart (`DeviceKindCard`) |
| 2 | Kurve | `ResponseGraph` (248 dp), darunter `BandReadout` (Frequenz und Gain des gewählten Bands groß, mit − und +) |
| 3 | Vergleich | Original/EQ, Halten zum Vergleichen |
| 4 | Genre × Intensität | Genre-Auswahl mit Mini-Kurven, Intensitätsstufen, Preset-Raster, Import per Link |
| 5 | Klangziel | `SoundGoalCard` |
| 6 | Auto & Bluetooth-Lautsprecher | Kontextmodus, Module Loudness, Subsonic, Virtual Bass |
| 7 | Parametrische Bänder | `ParametricBandsSection` |
| 8 | Signalkette | Pegel Boost/Input/Limit (`LedMeter`), Limiter, Mehrband-Kompressor, Headroom-Warnung |
| 9 | Klangprofile | welches Preset und welche Korrektur zum Ausgang gehören |
| 10 | Meine Kopfhörer | Korrekturkurve, Import/Export, AutoEQ-Vorschlag |
| 11 | Kopfhörermodus | „Headphone power“ |
| 12 | Feinabstimmung | Makro-Regler (Bass, Punch, Härte) und die festen Bänder, aufklappbar |

**Abweichung vom Review:** Die Kurve steht schon an zweiter Stelle, und das gewählte Band lässt sich schon mit −/+ einstellen. Das eigentliche Problem bleibt: **Alles scrollt mit.** Wer unten ein Band, ein Modul oder ein Profil ändert, sieht die Kurve nicht mehr. Zwölf Abschnitte untereinander verstecken außerdem den Pegel (Abschnitt 8) weit unten.

Es gibt **keinen** Oberflächentest für diesen Bildschirm.

## 2 · Ziel und Grenzen

**Ziel:** Kurve, Status und gewähltes Band bleiben immer sichtbar; alles andere liegt in drei Reitern darunter, sortiert nach Häufigkeit. Die Signalkette öffnet sich als Sheet hinter der Headroom-Anzeige.

**Klang:** Der Umbau ändert nur die Anordnung. Kein Wert, keine Voreinstellung und keine Verarbeitung ändert sich; `MainViewModel`, `:core` und `:player` bleiben unberührt, und alle DSP- und ViewModel-Tests laufen nach jedem Schritt unverändert mit (Bedingung der Freigabe).

**Grenzen:**
- **Es fällt nichts weg.** Jede Einstellung von heute ist danach in genau einem Reiter oder im Kopf.
- **Keine neue Gestaltung der Bausteine.** `ResponseGraph`, `BandReadout`, `LedMeter`, `SignalModuleTile`, Chips und Farben (`SonicStyle`, `Color.kt`) bleiben, wie sie sind. Neu ist die Anordnung.
- **Keine Änderung an Logik oder Daten.** `MainViewModel`, Klangverarbeitung und Speicherung bleiben unberührt; `EqualizerScreen` bekommt dieselben Parameter.
- Player, Deutsch-Übersetzung und Titelgrafik sind nicht Teil dieses Umbaus (Roadmap 04, Teil B und C).

## 3 · Aufbau

```text
┌───────────────────────────────────────────┐  ─┐
│ Bluetooth: Sony WH-1000XM5        [⇄] [⏻] │   │
│ Hardstyle · Medium (edited)               │   │
│ ● EQ ACTIVE · Spotify       Headroom  OK ▮▮▯│   │  fester Kopf
│ ┌───────────────────────────────────────┐ │   │  (scrollt nicht)
│ │            Frequenzgang-Kurve          │ │   │
│ └───────────────────────────────────────┘ │   │
│ 63 Hz   +4.5 dB                    [−] [+] │   │
├───────────────────────────────────────────┤  ─┘
│ ! Keine Audio-Sitzung … [Erneut versuchen] │  Hinweis (nur wenn nötig)
├───────────────────────────────────────────┤
│  Sound │ Fine-tune │ Device               │  Reiter
├───────────────────────────────────────────┤
│  … Inhalt des Reiters, scrollt …           │
└───────────────────────────────────────────┘
   More tools                                    (bleibt, aus MainScreen)
```

### 3.1 Fester Kopf

1. **Zeile 1:** Ausgang, darunter Preset (mit „edited“), rechts Quellenwahl und Ein/Aus. Wie heute, nur kompakter.
2. **Statuszeile:** links `EqStatusLine` wie heute (Zustand als Text, nicht nur als Farbe; grün nur für „aktiv“). **Neu rechts:** die Headroom-Aussage aus Abschnitt 8 in Kurzform: „Headroom OK“, „Limiter sometimes“ oder „Limiter heavy“, dazu eine dreistufige Mini-Anzeige in den bestehenden Farben (gedämpft, Vorsicht, heiß). Antippen öffnet das Sheet „Signal chain“ (3.3).
3. **Kurve:** `ResponseGraph` unverändert, 248 dp; kompakt siehe 3.5.
4. **Gewähltes Band:** `BandReadout` unverändert.

### 3.2 Hinweis-Platz

Unter dem Kopf steht **höchstens ein** Hinweis, nach Dringlichkeit: Fehler mit „Erneut versuchen“ → keine Audio-Sitzung (Kurzfassung, „Mehr“ klappt den heutigen Text auf) → Frage nach der Geräteart → AutoEQ-Vorschlag. Ohne Anlass ist der Platz leer und nimmt keinen Raum ein.

### 3.3 Reiter

| Reiter | Inhalt (heutige Abschnitte) |
|---|---|
| **Sound** | Genre × Intensität und Presets (4), Module Virtual Bass, Loudness, Subsonic (6), Klangziel (5), Vergleich mit dem Original (3) |
| **Fine-tune** | Makro-Regler und feste Bänder (12, ohne Aufklappen), parametrische Bänder (7), Hochauflösung und Pre-EQ-Modus (1, `HighResolutionEqRow`) |
| **Device** | Audio-Sitzungen und Klang je App (1, `AudioSourceList`), Klangprofile (9), Meine Kopfhörer (10), Kopfhörermodus (11), Auto & Lautsprecher (6, Kontextmodus) |
| **Sheet „Signal chain“** | Pegel Boost/Input/Limit, Limiter, Mehrband-Kompressor, Headroom-Warnung (8); öffnet sich beim Antippen der Headroom-Anzeige |

Sortiert nach Häufigkeit: Was man ständig ändert, liegt im ersten Reiter; „Device“ wird meist einmal je Gerät eingerichtet und steht hinten. Der gewählte Reiter wird gemerkt, solange die App läuft (E3).

Die Reiter sind `TabRow` aus Material 3 in den vorhandenen Farben: gewählter Reiter in Amber mit Unterstrich, die anderen in Grau.

### 3.4 Zustände

| Zustand | Kopf | Reiter |
|---|---|---|
| Aktiv | Status grün „EQ ACTIVE · App“, Kurve normal | bedienbar |
| Aus / Bypass | Status grau mit Text, Kurve abgeschwächt (wie heute) | bedienbar, Änderungen wirken nach dem Einschalten |
| Warten auf Sitzung | Status „waiting“, Hinweis-Platz erklärt | bedienbar |
| Nicht unterstützt / Fehler | Status mit Text, Hinweis mit Handlung | bedienbar, soweit sinnvoll (wie heute) |
| Limiter greift stark | Headroom „Limiter heavy“ in der Warnfarbe, zusätzlich Text | – |

### 3.5 Kleine Telefone, Querformat, große Schrift

- **Geringe Höhe** (unter etwa 640 dp verfügbare Höhe): Kurve 168 dp statt 248 dp, Zeile 1 einzeilig. Der Kopf darf höchstens die Hälfte der Höhe belegen.
- **Querformat:** zwei Spalten, links der feste Kopf, rechts Reiter und Inhalt.
- **Schrift 200 %:** Statuszeile und Headroom brechen um, nichts wird abgeschnitten; Reiter scrollen waagrecht, wenn sie nicht passen.
- **TalkBack:** Kopf, Hinweis, Reiter und Inhalt sind in dieser Reihenfolge erreichbar; die Reiter melden „Reiter 2 von 4, ausgewählt“.

## 4 · Dateien

| Datei | Änderung |
|---|---|
| `ui/equalizer/EqualizerScreen.kt` | wird zum Gerüst: Kopf, Hinweis, Reiter (Ziel unter 300 Zeilen) |
| `ui/equalizer/EqualizerHeader.kt` | neu: Zeile 1, Statuszeile mit Headroom, Kurve, `BandReadout` |
| `ui/equalizer/EqualizerNotice.kt` | neu: Hinweis-Platz mit Vorrangregel |
| `ui/equalizer/tabs/SoundTab.kt`, `FineTuneTab.kt`, `DeviceTab.kt` | neu: die heutigen Abschnitte, unverändert herausgelöst |
| `ui/equalizer/SignalChainSheet.kt` | neu: die heutige Signalkette (8) als Bottom-Sheet |
| `ui/equalizer/HeadroomBadge.kt` | neu: Kurzform der Headroom-Aussage (nutzt die vorhandene Berechnung `HeadroomWarningLevel`) |
| `MainScreen.kt` | nur, falls die Parameter des Gerüsts sich verschieben; Verhalten gleich |
| `res/values/strings.xml` | Reiternamen, Kurztexte für Headroom und Hinweise |

## 5 · Entscheidungen (15.11.2026)

- **E1 – Aufteilung:** drei Reiter **Sound | Fine-tune | Device**, Signalkette als Sheet hinter der Headroom-Anzeige (3.3). Ursprünglich vorgeschlagen waren vier Reiter mit „Dynamics“; geändert, weil Virtual Bass in einer Bass-App zur Klangwahl gehört und die Signalkette nur zum Nachsehen dient.
- **E2 – Pegel:** in der Statuszeile im Kopf.
- **E3 – Gewählter Reiter:** gemerkt, solange die App läuft.
- **E4 – Vergleich mit dem Original:** im Reiter „Sound“.

## 6 · Schritte

Jeder Schritt ist ein eigener Commit mit Build, Unit-Tests, ktlint, Lint und Bildschirmfotos aus dem neuen Oberflächentest.

| Schritt | Inhalt | Sichtbare Änderung |
|---|---|---|
| **Q1** | Oberflächentest `EqualizerScreenUiTest` (Robolectric, wie `PlayerShellUiTest`) schreibt Bildschirmfotos des **heutigen** Zustands als Vergleichsbasis. Danach die zwölf Abschnitte in eigene Dateien zerlegen, ohne Anordnung oder Aussehen zu ändern. | keine |
| **Q2** | Fester Kopf: Zeile 1, Statuszeile, Kurve, `BandReadout` außerhalb des Scrollbereichs. | Kurve bleibt stehen |
| **Q3** | Reiter mit der Zuordnung aus 3.3, Signalkette als Sheet. | drei Reiter, Sheet |
| **Q4** | Headroom in der Statuszeile, Hinweis-Platz mit Vorrangregel. | Pegel immer sichtbar |
| **Q5** | Kompakter Kopf, Querformat zweispaltig, große Schrift, TalkBack-Reihenfolge. | auf kleinen Geräten |
| **Q6** | Bildschirmfotos für Store und Video (Store-Variante), Doku (`ARCHITECTURE.md`, Roadmap 04). | – |

## 7 · Tests

- **Neu `EqualizerScreenUiTest`:** jeder Reiter und das Sheet einmal gerendert; nach dem Scrollen im Reiter ist die Kurve noch sichtbar; Reiterwechsel ändert die Kurve nicht; Hinweis-Platz zeigt nur den dringendsten Hinweis; Headroom-Text je Warnstufe; Bildschirmfotos nach `app/build/equalizer-screenshots/`, in der CI als Artefakt hochgeladen.
- **Bestehende Tests** (`MainViewModelTest`, Kurven- und DSP-Tests) bleiben unverändert grün; die Logik wird nicht angefasst.
- **APK-Prüfung** der Store-Variante läuft weiter mit.

## 8 · Gerätetest (du, danach)

1. Kleines Telefon und Querformat: Kurve und gewähltes Band immer sichtbar, nichts abgeschnitten.
2. In jedem Reiter etwas ändern und die Kurve dabei beobachten.
3. Schrift 200 % und TalkBack: alles erreichbar und vorgelesen.
4. EQ aus, Bypass, keine Audio-Sitzung, Limiter stark: Status und Hinweis stimmen.
5. Vergleich mit dem Original funktioniert wie vorher.

## 9 · Aufwand

M–L: Q1 bis Q3 je etwa ein halber bis ganzer Tag, Q4 und Q5 je ein halber Tag. Risiko gering, weil nur die Anordnung wechselt; die Bildschirmfotos aus Q1 zeigen jede ungewollte Änderung.

## 10 · Umsetzung (15.11.2026)

Umgesetzt im Branch zu PR #78: fester Kopf (Zeile 1, Statuszeile mit `HeadroomBadge`, Kurve, gewähltes Band), Hinweis-Platz (Fehler → keine Sitzung mit „More info“ → Geräteart → AutoEQ-Vorschlag), drei Reiter (`EqualizerTabs`), Signalkette als Sheet (`SignalChainSheet`), kompakter Kopf unter 640 dp Höhe, Querformat zweispaltig. Neue Bausteine in `ui/equalizer/EqualizerLayout.kt`; `ResponseGraph` hat einen Höhen-Parameter bekommen (Zeichnung und TalkBack-Ziele rechnen mit der echten Höhe).

**Abweichungen vom Plan:**
- Die Abschnitte stehen weiter in `EqualizerScreen.kt`, als lokale Composables (`GenreSection`, `ModulesSection`, `CompareSection`, `FineTuneSection`, `ProfilesSection`, `CorrectionSection`, `ContextSection`, `SignalChainSection`) statt in eigenen Dateien. Grund: Der Bildschirm hat rund 60 Parameter; eigene Dateien hätten vor allem Parameter durchgereicht, ohne etwas am Aussehen zu ändern. Die Datei bleibt dadurch bei etwa 990 Zeilen.
- Kompakter Kopf: Kurve 132 dp statt 168 dp, und die Hilfezeile unter der Kurve erscheint dort erst, wenn ein Band gewählt ist. Mit 168 dp belegte der Kopf auf einem 600-dp-Telefon rund zwei Drittel der Höhe.
- Die Erklärung „Points are the band settings …“ steht jetzt im Reiter „Fine-tune“ unter den Bändern, der Hinweis „Changes the sound of playback …“ im Reiter „Device“ bei den Audio-Sitzungen. Die Feinabstimmung ist nicht mehr zugeklappt.
- Die drei Module stehen unter der neuen Überschrift „Bass & loudness“ im Reiter „Sound“; „Car & Bluetooth speaker“ im Reiter „Device“ enthält nur noch den Kontextmodus.

**Klang:** keine Änderung an `MainViewModel`, `:core` oder `:player`; dieselben Rückrufe mit denselben Werten. Alle bestehenden Tests unverändert grün (Store 271, Full 343 App-Tests, `:core`), dazu `EqualizerScreenUiTest` mit 6 Fällen: jeder Reiter enthält seine Abschnitte und die Kurve bleibt im Kopf, Scrollen im Reiter lässt die Kurve stehen, das Headroom-Feld öffnet die Signalkette, kurzer Bildschirm, Querformat, „keine Audio-Sitzung“. Bildschirmfotos als CI-Artefakt `equalizer-screenshots`.

**Offen:** Q6 (Bildschirmfotos der Store-Variante für Store und Video) und der Gerätetest (Abschnitt 8).

