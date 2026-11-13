# DSP-Konzept: Virtual Bass (Roadmap 03, P4-3)

Stand: 13. November 2026. Gilt für `core/.../dsp/BassExciter.kt` und `HilbertAllpassPair.kt`.
Virtual Bass läuft nur im eigenen Player (`PlayerDspPcm16`, siehe `docs/DECISIONS.md`, „Virtual Bass
bleibt Player-Feature"). Für fremde Apps gibt es weiterhin nur die EQ-Annäherung
`VirtualBassApproxCurve`; sie ist von diesem Umbau nicht betroffen.

## 1. Wozu Virtual Bass

Ein Telefonlautsprecher oder eine kleine Box strahlt unterhalb von etwa 100–200 Hz kaum noch etwas
ab. Ein EQ-Boost dort bringt keinen Bass, nur Hub, Verzerrung und Limiter-Arbeit. Hört das Ohr aber
die Obertöne eines Tons (2f, 3f, …), ergänzt es den Grundton f selbst („missing fundamental").
Virtual Bass erzeugt deshalb aus dem Bass, den der Treiber nicht kann, Obertöne in einem Bereich,
den er kann, und mischt sie dem Original zu.

Damit das als *tieferer Bass* und nicht als *Verzerrung* gehört wird, müssen die Obertöne

1. eine klare harmonische Reihe bilden (Oktave, Duodezime) statt eines Gemischs höherer Ordnungen,
2. im Pegel dem Grundton folgen, also bei jeder Lautstärke im gleichen Verhältnis stehen,
3. keine neuen Töne *unterhalb* des Basses erzeugen (Differenztöne, Gleichanteil),
4. auf das Band begrenzt bleiben, in dem der Treiber arbeitet, und nicht in die Mitten laufen.

## 2. Ausgangslage nach P0-5

Bisheriger Signalfluss: Tiefpass 150 Hz (2. Ordnung) → `tanh`-Sättigung (drive) → Vollweg-
Gleichrichtung `abs()` → Hochpass 65 Hz → Tiefpass 300 Hz → zumischen.

Gemessen (Python-Nachbau, der die in P0-5 festgeschriebenen Pegel exakt reproduziert; Ton mit
Amplitude 0,3, mix 1, Werte in dB relativ zum Eingang):

| Schwäche | Messung alt |
|---|---|
| Gleichrichter erzeugt nur gerade Harmonische, die 3. fehlt ganz; das Verhältnis ist nicht einstellbar | 3. Harmonische < −280 dB |
| Gleichrichter verteilt Energie auf 4., 6., … Harmonische (rauer Klang) | 60 Hz: 2. −8,9 dB, 4. −22,1 dB (nur 13 dB Abstand) |
| Oberton-Anteil hängt vom Pegel ab: `tanh` normiert auf Vollausschlag, leise Stellen bekommen relativ mehr | 2. Harmonische bei 0 dBFS −16,2 dB, bei −20 dBFS −7,8 dB, bei −40 dBFS −7,7 dB (+8,5 dB bei leisen Stellen) |
| `abs()` auf einem Tongemisch erzeugt Differenztöne unterhalb des Basses | 50 + 70 Hz: 20-Hz-Differenzton −27 dB |
| Isolations-Tiefpass nur 12 dB/Oktave: Stimmen/Gitarren erreichen den Generator | 300-Hz-Ton: 2. Harmonische −32 dB |

## 3. Neuer Signalfluss

```
x ──┬──────────────────────────────────────────────────────────────────────(+)── y
    │                                                                        ▲
    └─ HP 20 Hz ─ LP4 fc ─┬─ Allpasskette I ─┐                                │
       (2. Ord.) (24 dB/Okt)│                 ├─ A=√(I²+Q²), c=I/A, s=Q/A      │
                          └─ Allpasskette Q ─┘   │                            │
                                                 ▼                            │
                              h = A·(w2·cos2θ + w3·cos3θ) · Gate(Ā) ─ HP 65 Hz ─ LP 3·fc ─ ·mix·trim
                                                                      (2. Ord.)  (2. Ord.)
                              Ā = Hüllkurvenfolger(A), Attack 10 ms, Release 150 ms
```

### 3.1 Bass isolieren
Hochpass 20 Hz (Butterworth, 2. Ordnung) und Tiefpass bei `cutoffHz` als Butterworth 4. Ordnung
(zwei Biquads, Q 0,5412 und 1,3066). Die steilere Flanke (24 statt 12 dB/Oktave) hält
Mittenmaterial aus dem Generator: Ein 300-Hz-Ton erzeugt jetzt −40 dB statt −32 dB, ein 600-Hz-Ton
−83 dB. Der Hochpass hält Rumpeln und Gleichanteil fern, die sonst die Hüllkurve verfälschen.

### 3.2 Analytisches Signal (Hüllkurve und Phase)
`HilbertAllpassPair`: zwei Ketten aus je vier Allpässen erster Ordnung, deren Ausgänge I und Q im
Bereich 16–800 Hz um 90° verschoben sind. Für einen Ton A·cos θ gilt dann I = A·cos θ′,
Q = A·sin θ′. Daraus folgen ohne Glättung:

- Hüllkurve A = √(I² + Q²), sofort und ohne Restwelligkeit,
- Kosinus und Sinus der momentanen Phase: c = I/A, s = Q/A.

Polfrequenzen (Hz) — offline per Minimax auf die 90°-Abweichung optimiert, über die bilineare
Transformation mit Vorverzerrung für jede Abtastrate umgerechnet:

| Kette I | 18,67 | 80,72 | 316,7 | 2351,6 |
|---|---|---|---|---|
| **Kette Q** | **5,405** | **40,45** | **158,7** | **685,6** |

Abweichung von 90° zwischen 16 und 800 Hz: < 0,2° bei 44,1/48 kHz, < 0,4° bei 96/192 kHz
(`HilbertAllpassPairTest`). Beide Ketten sind reine Allpässe, ändern also keinen Pegel.

**Verworfene Alternative:** Spitzenwert-Hüllkurvenfolger plus normierte Kennlinie direkt auf x.
Ein Folger mit kurzer Release-Zeit hat bei 40 Hz starke Restwelligkeit (die Normierung x/Ā wird
selbst zur Verzerrung), einer mit langer Release-Zeit normiert Ausklänge falsch. Beides erzeugt
zusätzliche Obertöne und bei Pegelsprüngen einen Gleichanteil-Sprung (Klick). Das analytische
Signal hat keines dieser Probleme und kostet nur acht Allpässe erster Ordnung.

### 3.3 Kennlinie: einstellbares Verhältnis 2. zu 3. Harmonischer
Tschebyschow-Polynome auf der normierten Phase, skaliert mit der Hüllkurve:

```
h = A · ( w2 · cos 2θ + w3 · cos 3θ )
cos 2θ = T2(c) = c² − s²
cos 3θ = T3(c) = c · (c² − 3s²)
w2 = √(1 − share),  w3 = √share,  share = thirdHarmonicShare ∈ [0, 1]
```

- Ein Ton ergibt genau eine 2. und eine 3. Harmonische, keine höheren und keinen Gleichanteil.
- `share` verteilt die Oberton-*Leistung*; die Summe bleibt gleich (w2² + w3² = 1), Verschieben
  ändert also nicht die Lautheit des Effekts (`BassExciterTest.share_keepsTheTotalHarmonicPower`).
- Standard `share = 0,25`: Die Oktave dominiert (klanglich am unauffälligsten, gleiche Tonklasse),
  die Duodezime liegt 4,8 dB darunter und gibt dem Kick Kontur. 0 = nur Oktave (weich),
  1 = nur Duodezime (hart).
- Weil h linear mit A skaliert, ist das Oberton-Verhältnis pegelunabhängig — das alte Problem
  „leise Stellen überproportional" ist damit strukturell gelöst, nicht nur abgemildert.
- Zwei Töne gleichzeitig ergeben, wie bei jeder Nichtlinearität, Summentöne (z. B. 50 + 70 → 120 Hz).
  Das analytische Signal multipliziert aber nur „nach oben": Differenztöne unterhalb des Basses
  bleiben aus (20 Hz bei 50 + 70 Hz: < −50 dB statt −27 dB).

### 3.4 Hüllkurvenfolger und Gate
Ein Hüllkurvenfolger (einpolig, Attack 10 ms, Release 150 ms) glättet A zu Ā und steuert ein
weiches Gate (Smoothstep): unter −66 dBFS Basspegel keine Obertöne, ab −54 dBFS volle Stärke.

Aufgabe: Rauschen, Hallfahnen und ausklingende Töne sollen keinen eigenen Oberton-Teppich bekommen.
Der Folger entscheidet nur über das Gate, nicht über die Normierung (die macht das analytische
Signal exakt). Darum darf er träge sein, ohne Obertöne zu verfälschen. Musik mit hörbarem Bass liegt
im Bassband typischerweise weit über −54 dBFS, dort greift das Gate nicht.

Gemessen (2. Harmonische re Eingang, 60 Hz): 0 bis −54 dBFS konstant −10,8 dB, −57 dBFS −14,5 dB,
−60 dBFS −22,7 dB, −63 dBFS −36,8 dB, ab −66 dBFS aus.

### 3.5 Bandpass für die Obertöne
Hochpass 65 Hz (2. Ordnung): entfernt alles unterhalb des Nutzbands (Reste der Normierung bei
Tongemischen). 80 Hz, die Oktave über einem 40-Hz-Kick, kommt mit −1,6 dB durch.
Tiefpass bei 3 · `cutoffHz` (2. Ordnung, Standard 450 Hz): lässt die 3. Harmonische des höchsten
isolierten Grundtons mit −3 dB durch, aber nichts weiter in die Mitten. Bisher lag er bei
2 · `cutoffHz`; ohne dritte Harmonische war das ausreichend.

### 3.6 Zumischen und Pegel
`y = x + h · mix · OUTPUT_TRIM`, `OUTPUT_TRIM = 0,35`. Kalibriert so, dass die gesamte
Oberton-Leistung (2. + 3.) eines 100-Hz-Tons bei mix 1 der 2. Harmonischen der P0-5-Version
entspricht (−9,7 dB re Eingang). Bestehende Presets (`virtualBassMix` 0,4 bzw. 0,6) werden also
nicht lauter. Das Original bleibt unverändert, auch der Grundton; Spitzen fängt der Lookahead-
Limiter hinter der Kette (`PlayerDspPcm16`).

## 4. Frequenzgang des Effekts

Oberton-Pegel re Eingang bei mix 1, `share` 0,25, Amplitude 0,3 (`BassExciterTest`):

| Grundton | 2. Harm. | 3. Harm. | 4./5. Harm. | Summe 2.+3. |
|---|---|---|---|---|
| 30 Hz | −14,9 | −17,0 | < −74 | −12,8 |
| 40 Hz | −12,2 | −15,8 | < −79 | −10,6 |
| 60 Hz | −10,8 | −15,4 | < −75 | −9,5 |
| 80 Hz | −10,6 | −15,5 | < −114 | −9,4 |
| 100 Hz | −10,8 | −16,1 | < −78 | −9,6 |
| 120 Hz | −11,4 | −17,3 | < −79 | −10,4 |
| 150 Hz | −14,2 | −21,2 | < −95 | −13,4 |
| 300 Hz | −40,7 | −51,6 | | −40,3 |
| 600 Hz | −83,2 | −95,0 | | −82,9 |

Zwischen 40 und 120 Hz schwankt die Summe um 1,2 dB. Bei 44,1 und 96 kHz weicht die 2. Harmonische
um weniger als 0,02 dB von 48 kHz ab. Töne ab 1 kHz laufen unverändert durch (RMS-Abweichung im
eingeschwungenen Zustand < 1·10⁻⁶).

## 5. Latenz und Rechenaufwand

- Der trockene Pfad hat keine Latenz. Die Obertöne kommen durch Isolations-Tiefpass und Allpässe
  einige Millisekunden nach dem Grundton (bei 60 Hz unter 20 ms bis zur halben Endstärke,
  `harmonics_followAToneBurst`). Das liegt innerhalb der Zeit, in der das Gehör Grundton und
  Obertöne zu einem Ereignis verschmilzt; die Hörprobe muss das bestätigen (Kick-Signal, Abschnitt 7).
- Pro Sample und Kanal: 5 Biquads, 8 Allpässe erster Ordnung, eine Wurzel, eine Division.
  Gemessen auf der JVM im Container: rund 62 ns pro Sample und Kanal, bei Stereo 48 kHz also etwa
  0,6 % eines Kerns. Auf dem Telefon ist mit einem Mehrfachen zu rechnen; Messung offen
  (`docs/TEST_MATRIX.md` K9).

## 6. Schnittstelle

- `BassExciterSettings`: `enabled`, `cutoffHz`, `mix`, neu `thirdHarmonicShare`. Der Parameter
  `drive` entfällt: Er steuerte die `tanh`-Sättigung, die es nicht mehr gibt. Er wurde außerhalb von
  `BassExciter` nirgends gesetzt (`PlayerDspPcm16.updateVirtualBass` übergibt nur `mix`).
- In der App bleibt es beim Virtual-Bass-Schalter mit `virtualBassMix` aus dem Preset. `share` und
  `cutoffHz` laufen mit Standardwerten. Ob sie in der Oberfläche einstellbar werden sollen (z. B.
  „Charakter: weich – hart"), ist eine eigene UI-Entscheidung und gehört nicht zu P4-3.

## 7. Hörprobe (offen, braucht Gerät und Ohren)

Unit-Tests belegen keine Klangqualität. Auf Telefonlautsprecher, kleiner Bluetooth-Box und
Kopfhörer, jeweils Virtual Bass aus/an (A/B), bei leiser und lauter Wiedergabe:

| # | Signal | Worauf achten |
|---|---|---|
| H1 | Sinus-Sweep 30 → 150 Hz, 10 s, −12 dBFS | Tonhöhe wird durchgehend als *ein* tiefer Ton gehört, nicht als zwei Töne; kein Pegelloch |
| H2 | Kick-Muster 45 Hz, 128 BPM | Kick wirkt tiefer/präsenter; kein Nachziehen, kein „Doppelschlag" (Latenz der Obertöne) |
| H3 | Zwei Töne 50 + 70 Hz | kein Brummen unterhalb (Differenzton), Rauigkeit vergleichbar mit Original |
| H4 | 60-Hz-Ton, der über 5 s auf −70 dBFS ausblendet | kein Pumpen oder Abreißen am Gate (−54 … −66 dBFS) |
| H5 | Bass-Gesang / Sprache | keine Verzerrung der Stimme (Isolation 150 Hz) |
| H6 | Echtes Material: Hardstyle/Uptempo (verzerrte Kicks), Hip-Hop (808), akustischer Kontrabass | Gesamteindruck, Lautheit gegenüber vorher (Presets Car/Box), keine Artefakte bei Seek/Titelwechsel |

Ergebnisse in `docs/TEST_MATRIX.md` (K26–K28) und im Abschlussbericht von Roadmap 03 eintragen.
Je nach Ergebnis sind `thirdHarmonicShare`, die Gate-Schwellen und `OUTPUT_TRIM` die
Stellschrauben; alle drei sind durch Tests festgeschrieben und müssen bei einer Änderung bewusst
mit angepasst werden.

## 8. Bewusst nicht umgesetzt

- **Grundton absenken** (wie MaxxBass im „Replace"-Modus): Würde auf großen Systemen Bass wegnehmen.
  Dafür gibt es den Subsonic-Filter und die EQ-Kurve.
- **Pegelabhängige Stärke** (mehr Obertöne bei leiser Wiedergabe, Fletcher-Munson): Wäre eine
  Funktion der Wiedergabelautstärke, nicht des Signalpegels, und gehört zur Loudness-Kompensation.
- **Weichblende bei mix-Änderung**: Ein Sprung von `mix` ist ein Pegelsprung in einem reinen
  Bassband und war schon vorher so; bei Bedarf nach der Hörprobe nachrüsten.
