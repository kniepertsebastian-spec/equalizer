# Roadmap 07 – Lärmabhängiger EQ: Messlücken im Pre-EQ (PoC)

**Modell-Empfehlung:** Sonnet 5.5 für L1–L2; Opus 5.5 für L3 (Architektur) und L4 (Android-Rechte)
**Ziel:** Prüfen, ob schmale, feste Messlücken im `DynamicsProcessing`-Pre-EQ das Fahrgeräusch über das Mikrofon messbar machen. So entfällt die Echo-Kompensation, also das Herausrechnen der eigenen Musik.
**Hintergrund:** Feature 12 aus dem Car-Computer-Brainstorming (EQ-remote, Pixel 9 headless im Auto). Ein lärmabhängiger EQ hebt die Frequenzen an, die das Fahrgeräusch überdeckt.
**Abhängigkeit:** L0 (Akustik-Test ohne Code) muss positiv ausgefallen sein.

**Einordnung (Claude, 19.11.2026):** Vom Nutzer übernommen, Inhalt unverändert bis auf diesen Absatz und den Abschnitt „Alternative ohne Messlücken“. Gehört zu Modul 11, F12 im Repo EQ-remote (`pixel-car-computer-roadmaps/docs/modules/11-fahrzeugfunktionen.md`). F6 (tempoabhängige Lautstärke) ist vom Nutzer bestätigt und wird mit F12 zu einem gemeinsamen „Fahrgeräusch-Ausgleich“ geplant. L0 und L4 brauchen keinen Code und kommen in der Gesamtreihenfolge ganz nach vorn (EQ-remote `ROADMAP.md`).

## Befund aus dem Code (Stand 18.11.2026)
- Parametrische Bänder (`ParametricCurve`) sind im Systempfad **keine echten Filter**. Sie werden zur Zielkurve (6 Punkte pro Oktave) und dann über `PreEqMapper` auf die 34 festen Bänder verteilt. Ein schmales Q wird dabei verschmiert, und `MAX_Q = 10`. Für Messlücken sind sie deshalb ungeeignet.
- Pre-EQ: `PreEqLayout.CUTOFFS_HZ` (34 Bänder, etwa Terzbänder, 25 Hz bis 20 kHz). Die Bandgrenzen werden in `AndroidAudioEngine.createDynamicsConfig` **beim Anlegen festgelegt**, danach ändern sich nur die Verstärkungen.
- Laut Kommentar in `PreEqLayout` arbeitet `DynamicsProcessing` mit einer Kurzzeit-FFT, deren Raster bei 20-ms-Frames etwa 23 Hz breit ist. Schmaler als etwa 23–46 Hz wird ein Band also nicht. Ab etwa 1 kHz ist das schmal genug (40 Hz entsprechen rund 1/25 Oktave). Den Bass misst man stattdessen unterhalb des Subsonic-Filters.
- Verstärkung je Band ist auf ±15 dB begrenzt (`PreEqLayout.MAX_GAIN_DB` im Mapper, `coerceIn(-15f, 15f)` in `AndroidAudioEngine.applyInternal`).
- Rückfallkette in `AndroidAudioEngine` (etwa Zeile 193): `CUTOFFS_HZ` → `COARSE_CUTOFFS_HZ` → ohne Pre-EQ.

## L0 Akustik-Test ohne Code (Voraussetzung)
Testdatei `notch_testsignal_48k.wav`: 5 s Stille, 30 s rosa Rauschen mit Lücken bei 1.000, 2.500 und 5.000 Hz (je 40 Hz breit, in der Datei rund 37 dB tief), 5 s Stille. Abspielen im stehenden Auto bei normaler und bei hoher Lautstärke, mit EQ im Bypass, und mit Spectroid auf dem Pixel am Einbauort messen.
- [ ] Ergebnis eingetragen (Lückentiefe je Frequenz, normal und laut)
- [ ] **Weiter nur, wenn bei normaler Lautstärke ≥ 15 dB.** Sonst Roadmap beenden und als Alternative „nur in leisen Momenten messen“ prüfen.

## L1 Ein Messband im Pre-EQ (S) – nur auf eigenem Branch
Minimale Änderung ohne Oberfläche und ohne Einstellung, nur um zu messen.
- [ ] `PreEqLayout` (in `core/.../dsp/PreEqMapper.kt`): `MEASUREMENT_NOTCH_HZ = 1000f to 1046f`, `NOTCH_GAIN_DB = -15f`, `CUTOFFS_WITH_NOTCH_HZ = (CUTOFFS_HZ + 1000f + 1046f).sorted()`. Das teilt das Band 920–1120 Hz in drei Teile, insgesamt 36 Bänder.
- [ ] `AndroidAudioEngine`: `CUTOFFS_WITH_NOTCH_HZ` als erste Bandaufteilung in die Rückfallkette aufnehmen.
- [ ] `CurveCalculator.preEqGains`: Nach `PreEqMapper.map` das Band mit den Grenzen 1000/1046 auf `NOTCH_GAIN_DB` setzen, nur wenn diese Aufteilung aktiv ist (`capabilities.preEqCutoffsHz`). Die Nachbarbänder bleiben an der Kurve.
- [ ] Unit-Test: Die neue Liste ist streng aufsteigend und enthält die Lücke. Am Mapping prüfen, dass das Messband genau `NOTCH_GAIN_DB` erhält und die Nachbarbänder der Kurve folgen.
- [ ] Bekannte Nebenwirkungen nur dokumentieren, nicht beheben: Die Hochauflösungs-Anzeige und die Remote-API zeigen 36 Bänder.
- **Fertig, wenn:** Build, Tests und Lint grün sind und der Diagnosebericht am Pixel 36 aktive Pre-EQ-Bänder zeigt.

## L2 Gerätemessung (S)
- [ ] Prüfen, ob das Pixel 9 die 36 Bänder annimmt. Diagnosebericht ansehen, ein Rückfall auf 34 oder 16 Bänder wird im Log protokolliert.
- [ ] Spotify mit echter Musik und L1-Build: Messlücke bei 1 kHz mit Spectroid am Einbauort messen, bei normaler und hoher Lautstärke.
- [ ] Hörprobe mit ruhiger Musik (Klavier, Stimme): Ist die Lücke hörbar?
- [ ] Prüfen, ob die Kanten steil genug sind: Lückentiefe im Vergleich zu L0 (dort Rauschen aus der Datei)
- **Fertig, wenn:** Messwerte und Hörprobe dokumentiert sind und klar entschieden ist, ob es mit L3 weitergeht oder der Ansatz verworfen wird.

## L3 Saubere Umsetzung planen (M) – nur nach positivem L2, erst Plan, dann Freigabe
- [ ] 2–3 Lösungsansätze mit Vor- und Nachteilen skizzieren, bevor Code entsteht
- [ ] Einstellung `noiseMeasurement` in `ProcessingSettings`. Beim Umschalten wird `DynamicsProcessing` neu angelegt, wie heute bei `highResolutionEq`.
- [ ] 4–6 Messbänder ab etwa 1 kHz (z. B. 1.000, 2.500, 5.000 Hz). Bass unterhalb des Subsonic-Filters messen.
- [ ] Messbänder in der Oberfläche, der Remote-API und bei der Preset-Speicherung ausblenden. Sie dürfen nie zur Klangkurve gehören.
- [ ] ±15-dB-Grenze nur für Messbänder lockern (Ziel etwa −30 dB). Vorher prüfen, ob `DynamicsProcessing` das zulässt.
- [ ] Messkette: Mikrofon (`UNPROCESSED`), FFT mit 4.096–8.192 Punkten, Grundrauschen über das untere Perzentil der letzten 5–10 s, Einfrieren bei Sprache, beim Titelwechsel und beim Ducking
- [ ] Regelung: wenige Stellgrößen (Bass-Shelf, Mitten, Präsenz), Totzone 1,5 dB, höchstens 0,3–0,5 dB pro Sekunde, höchstens +6 dB, Plausibilitätsprüfung über das Tempo gegen Rückkopplung

## L4 Mikrofon im Hintergrund (M) – früh prüfen, kann das Feature kippen
- [ ] Startet ein Foreground Service vom Typ `microphone` auf Android 16 headless, also ohne sichtbare Activity und nach dem Booten? Einschränkungen seit Android 14/15 prüfen.
- [ ] Ist die Quelle `UNPROCESSED` auf dem Pixel 9 verfügbar? (`AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)`)
- [ ] Datenschutz: Nur Pegel pro Band im Speicher, nie Audio aufzeichnen. In `docs/` dokumentieren.

## Alternative ohne Messlücken (Vorschlag Claude, 19.11.2026) – in L3 als Ansatz mit bewerten
Frage des Nutzers: Geht die Messung genauer als mit wenigen Messbändern?
- **Bekannte Musik pro Band herausrechnen:** Die App kennt das Signal, das sie ausgibt. Pro Band (z. B. die 34 Terzbänder) über 3–10 s den Pegel am Mikrofon gegen den verzögerten Pegel der Musik auftragen: Mikrofon ≈ G·Musik + Lärm. Weil Musik schwankt und Fahrgeräusch sich langsam ändert, liefert eine lineare Regression G (Übertragung Radio → Innenraum → Mikrofon) und den Lärm je Band. Verzögerung (Bluetooth 150–300 ms) per Kreuzkorrelation. Keine Echo-Kompensation pro Sample nötig, nur Pegel. Prinzip wie bei Bose AudioPilot.
- **Vorteile:** ganzes Spektrum in Terzauflösung statt 4–6 Stützstellen, nichts Hörbares am Klang, keine Änderung an `DynamicsProcessing` und an der ±15-dB-Grenze.
- **Offen:** Referenzsignal bei Spotify. Eigener Player: PCM direkt. Fremde Apps: `Visualizer` (8 bit, bis ~20 Messungen/s, `RECORD_AUDIO`) reicht vermutlich für Pegel pro Band; `AudioPlaybackCapture` bräuchte bei jedem Start eine Freigabe und scheidet headless aus. Als **L0b** prüfbar: im stehenden Auto rosa Rauschen bzw. Musik plus Mikrofonaufnahme, Regression offline am Laptop auswerten.
- **Ergänzend, für beide Ansätze:** Pausen zwischen Titeln als saubere Lärmmessung; Tempo und Drehzahl (OBD2, F6) als Plausibilitätsprüfung und lernendes Lärmprofil, wenn die Messung unsicher ist; Mikrofonposition prüfen (Pixel-Mikro am Einbauort vs. externes USB-C-Mikro an der Kopfstütze, ca. 10 €).

## Verbindliche Prüfungen
- L1 nur auf eigenem Branch. Nichts in `main`, bevor L2 positiv ist.
- Nach jedem Arbeitspunkt Build, relevante Tests und Lint ausführen.
- Unit-Tests belegen keine Akustik. L0 und L2 sind nur mit echten Messwerten vom Gerät erledigt.
- Am Ende geänderte Dateien, Messwerte, Entscheidungen, offene Punkte und Commit-ID dokumentieren.
