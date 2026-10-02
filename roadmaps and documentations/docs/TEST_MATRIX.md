# Test-Matrix

Wird am Ende jeder Arbeitssession aktualisiert (siehe `roadmap.md` §15,
Punkt 10 und §10 M0-Abnahmekriterium "Geräte-Matrix"). Ab 23. September 2026
gilt zusätzlich `roadmap-2026.md` M0 als Vorgabe für Umfang und Spalten
dieser Matrix.

## M3–M6: Was jetzt getestet werden muss (Stand 25. September 2026)

M3 (Geräteprofile/Korrekturebene), M4 (Headroom/Limiter/Metering) und M5
(AutoEQ-Workflow) sind vollständig implementiert (PR #26), M6 Phase 1
(Filtermodell/Biquad-Mathematik, PR #27) ebenfalls – siehe die jeweiligen
„Umsetzungsstand"-Abschnitte in `roadmap-2026.md`. Nichts davon wurde bisher
auf echter Hardware verifiziert; alle vier CI-Läufe waren grün (Build +
Unit-Tests), das deckt aber nur Kompilierbarkeit und die reinen
DSP-/Parsing-Berechnungen ab, kein tatsächliches Geräteverhalten. Diese
Liste ist die konkrete Testphase, die jetzt ansteht, bevor M7 beginnt.

### M3 – Geräteprofile und Korrekturebene

- [ ] **Stabiler Route-Fingerprint (echter Bugfix, höchste Priorität):**
  Bluetooth-Kopfhörer verbinden, HardBass EQ nutzen, Kopfhörer trennen, App
  komplett beenden (aus Übersicht wischen), App neu starten, Kopfhörer
  wieder verbinden. Erwartung: dasselbe Geräteprofil wird wiedererkannt
  (nicht als neues Gerät behandelt) – das war vor dem Fix in dieser Session
  kaputt, weil `AudioDeviceInfo.getId()` laut Android-Doku sessionübergreifend
  nicht stabil ist.
- [ ] Route-Wechsel (Bluetooth an/aus, USB an/aus, zurück zu Lautsprecher)
  während laufender Wiedergabe: Profil wechselt automatisch, subjektiv
  innerhalb von ~2 Sekunden (Abnahmekriterium).
- [ ] Lautsprecherprofil wird nie versehentlich auf Bluetooth-Kopfhörer
  angewandt und umgekehrt (mehrfach schnell hintereinander wechseln).
- [ ] Konfliktregel: manuell ein anderes Korrekturprofil/Klangstil wählen,
  Wiedergabe fortsetzen (Auswahl muss halten), dann Route wechseln (Auswahl
  muss auf das für die neue Route hinterlegte Profil zurückfallen).
- [ ] „Mein Kopfhörer" auf „Kein Korrekturprofil" stellen bei aktivem
  Klangstil-Preset: Korrektur ist hörbar aus, Klangstil bleibt aktiv (und
  umgekehrt) – Abnahmekriterium „unabhängig deaktivierbar".
- [ ] Resultierende kombinierte Kurve wird in der UI sichtbar dargestellt.

### M4 – Headroom, Limiter und Metering

- [ ] „Signal & Sicherheit"-Karte ist dauerhaft sichtbar und zeigt
  Input-Gain, Limiter-Status/Threshold und MBC-Status.
- [ ] Jedes Built-in-Preset (insbesondere „Final Smash") mit Sinus- oder
  Sweep-Testsignal (vorhandene M0-Spike-Werkzeuge) auf hörbares/messbares
  Clipping prüfen – Abnahmekriterium „kein Built-in-Preset clippt".
- [ ] Angezeigter Input-Gain mit dem über den Diagnosereport auslesbaren,
  tatsächlich angewandten Wert vergleichen (müssen übereinstimmen).
- [ ] Limiter im UI deaktivieren, per Diagnosereport/Debug-Effektliste
  bestätigen, dass er tatsächlich aus ist (nicht nur der Schalter).
- [ ] Für ein Preset mit wenig, mittlerem und starkem Boost jeweils prüfen,
  ob die angezeigte Warnstufe (SUFFICIENT/OCCASIONAL_LIMITING/
  HEAVY_LIMITING) zum tatsächlich gehörten Verhalten passt.
- [ ] Prüfen, dass an keiner Stelle der UI eine Schätzung als Messung
  bezeichnet wird (Sichtprüfung der Texte).

### M5 – AutoEQ-Workflow

- [ ] Echten Import: eine reale `GraphicEQ`-Datei (z. B. von
  autoeq.app) über den System-Dateipicker importieren, Vorschau (Quelle/
  Frequenzbereich/max. Boost/Headroom) gegen die Datei prüfen, bestätigen.
- [ ] Dieselbe Prüfung mit einer CSV/TSV-Datei.
- [ ] Eine Datei mit Boost über 12 dB importieren: Zusatzwarnung im
  Vorschau-Dialog erscheint.
- [ ] Eine absichtlich fehlerhafte/leere Datei importieren: abweisbarer
  Fehlerdialog statt stillem Nichtstun oder Absturz.
- [ ] Ein importiertes Korrekturprofil exportieren (Share-Icon), die
  exportierte Datei erneut importieren: Kurve muss identisch sein
  (verlustfreier Round-Trip) – über ein echtes Android-Share-Sheet, nicht
  nur die JSON-Validierung im Unit-Test.
- [ ] Importiertes Profil an ein Geräteprofil binden, Route wechseln und
  zurück: Bindung bleibt erhalten (Zusammenspiel mit M3-Konfliktregel).

### M6 Phase 1 – Parametrisches Filtermodell

- Noch nicht sinnvoll auf echter Hardware testbar: Phase 1 ist bewusst nur
  die Mathematik (Filtermodell + Biquad-Koeffizienten, analytisch
  verifiziert). Die eigentliche, für M6 offene Frage – welcher der drei
  Ausführungspfade (`DynamicsProcessing`/Herstellereffekte, eigener
  Media3-DSP, grafische Approximation) auf den Zielgeräten tragfähig ist – 
  ist selbst der nächste Testschritt, kein nachträglicher Verifikationsschritt:
  - [ ] Auf mindestens Pixel 10 und dem Samsung-Testgerät prüfen, ob
    `DynamicsProcessing`/`PRE_EQ` mit den in `BiquadFilterDesigner`
    berechneten Koeffizienten überhaupt annehmbar ist (Effekt-Query,
    analog zum bestehenden Session-Attach-Spike).
  - [ ] Falls ja: Latenz, CPU-Last, Akkuverbrauch und hörbare Artefakte
    grob einschätzen, um Release-Gate B eine erste Grundlage zu geben.
  - Phase 2 (PEQ-UI, Produktintegration) bleibt bis dahin bewusst nicht
    begonnen.

## M0-Sprint-0-Vorlage (roadmap-2026.md §8, Punkt 1+4)

Noch nicht ausgefüllt – die Zeilen sind eine Vorlage für die tatsächlichen
Gerätetests, die nur auf echter Hardware durchgeführt werden können. Pro
Kombination aus Gerät × Route × Player × Startreihenfolge eine Zeile:

### Ausführungsanleitung für Punkt 4 (vier Startreihenfolgen)

Sprint-0-Punkt 4 ist enger gefasst als die volle Matrix unten (Punkt 1):
„Spotify und SoundCloud in vier Startreihenfolgen testen" = 2 Player × 2
Startreihenfolgen = die ersten vier Datenzeilen der Tabelle unten (Pixel 10,
Lautsprecher, Spotify/SoundCloud × HardBass-EQ-zuerst/Player-zuerst). Alle
weiteren Zeilen (Bluetooth, USB, Samsung) gehören zu Punkt 1 (volle
Geräte-Matrix) und sind für den Sprint-0-Abschluss nicht zwingend, aber
willkommen, falls Zeit ist.

**Voraussetzungen:**

- Debug-APK: entweder aktuellster grüner CI-Lauf auf `main` → Artefakt
  `app-debug-apk`, oder – falls `GDRIVE_SA_KEY` konfiguriert ist (siehe
  `docs/DRIVE_UPLOAD.md`) – `HardBassEQ-debug-latest.apk` im geteilten
  Drive-Ordner. Auf dem Pixel 10 installieren (unbekannte Quellen
  zulassen, falls nötig).
- Spotify und SoundCloud installiert und mit einem Account angemeldet, der
  Wiedergabe erlaubt (kein reiner Vorschau-Modus).
- Beim ersten Start: Benachrichtigungsberechtigung für HardBass EQ
  erlauben (sonst kann der Foreground-Service, der Sessions im Hintergrund
  erkennt, keine sichtbare Notification zeigen – siehe
  `AudioSessionForegroundService`).

**Pro Testfall (einmal für jede der vier Zeilen wiederholen):**

1. **Startreihenfolge herstellen:**
   - „HardBass EQ zuerst": HardBass EQ öffnen und offen lassen, danach erst
     Spotify/SoundCloud öffnen und einen Track abspielen.
   - „Player zuerst": Spotify/SoundCloud öffnen und einen Track abspielen,
     danach erst HardBass EQ öffnen.
2. **Status-Chip ablesen** (oben auf dem HardBass-EQ-Startbildschirm,
   direkt unter „Route: …"): notieren, ob er „Aktiv (Session #…)" zeigt.
   Zeigt er stattdessen „Wartet auf Audio-Session", „Fehler: …" oder
   „Kontrollverlust" → das ist bereits das Ergebnis für „Attach OK?" = Nein.
3. **EQ-Wirkung prüfen:** einen Regler deutlich verschieben (z. B. Bass-
   Makro-Regler weit nach oben) und hören, ob sich der Klang hörbar
   ändert. Das ist die Spalte „EQ-Wirkung hör-/messbar?".
4. **Pause/Resume:** in Spotify/SoundCloud pausieren und fortsetzen,
   Status-Chip erneut prüfen → Spalte „Verhalten nach Pause".
5. **Trackwechsel:** zum nächsten Track springen, Status-Chip erneut
   prüfen → Spalte „Verhalten nach Trackwechsel".
6. **Route-Wechsel** (optional für Punkt 4, aber leicht mitzuerfassen):
   Bluetooth-Kopfhörer verbinden/trennen, Status-Chip erneut prüfen →
   Spalte „Verhalten nach Route-Wechsel".
7. **Diagnosereport sichern:** in HardBass EQ auf „Diagnose & Report"
   tippen, dann oben rechts auf „Kopieren" – der Report enthält exakt die
   Felder, die für die übrigen Spalten gebraucht werden:
   - „Session-ID erhalten": aus „--- Recent Session Events ---" die Zeile
     `Session attached: id=… package=…`.
   - „Kontrollverlust während Wiedergabe?": prüfen, ob dort eine Zeile
     `Engine state: LostControl(…)` auftaucht.
   - „Engine State" ganz oben im Report bestätigt den zuletzt beobachteten
     Zustand noch einmal maschinenlesbar.
   Den kopierten Report als Klartext in die entsprechende Zeile unten
   einfügen (oder als separate Datei/Anhang sichern) – das ist zugleich der
   von den Abnahmekriterien geforderte „dokumentierte Ablauf" bei
   Fehlschlägen.
8. **Zeile unten ausfüllen** und Datum eintragen.

Nach den vier Zeilen: kurz zurückmelden (z. B. hier im Chat oder als PR),
welche der vier Fälle fehlgeschlagen sind, inkl. der kopierten
Diagnoseberichte – daraus lässt sich der nächste Schritt (Punkt 7,
Re-Attach-Implementierung) konkret ableiten.

| Gerät | Route | Player | Startreihenfolge | Session-ID erhalten | Attach OK? | Kontrollverlust während Wiedergabe? | Verhalten nach Pause | Verhalten nach Trackwechsel | Verhalten nach Route-Wechsel | EQ-Wirkung hör-/messbar? | Datum |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Pixel 10 | Lautsprecher | Spotify | HardBass EQ zuerst | | | | | | | | |
| Pixel 10 | Lautsprecher | Spotify | Player zuerst | | | | | | | | |
| Pixel 10 | Lautsprecher | SoundCloud | HardBass EQ zuerst | | | | | | | | |
| Pixel 10 | Lautsprecher | SoundCloud | Player zuerst | | | | | | | | |
| Pixel 10 | Bluetooth-Kopfhörer | Spotify | HardBass EQ zuerst | | | | | | | | |
| Pixel 10 | Bluetooth-Kopfhörer | Spotify | Player zuerst | | | | | | | | |
| Pixel 10 | Bluetooth-Kopfhörer | SoundCloud | HardBass EQ zuerst | | | | | | | | |
| Pixel 10 | Bluetooth-Kopfhörer | SoundCloud | Player zuerst | | | | | | | | |
| Pixel 10 | Kabelgebunden/USB | Spotify | HardBass EQ zuerst | | | | | | | | |
| Pixel 10 | Kabelgebunden/USB | SoundCloud | HardBass EQ zuerst | | | | | | | | |
| _Samsung-Gerät (Modell offen)_ | Lautsprecher | Spotify | HardBass EQ zuerst | | | | | | | | |
| _Samsung-Gerät (Modell offen)_ | Lautsprecher | Spotify | Player zuerst | | | | | | | | |
| _Samsung-Gerät (Modell offen)_ | Lautsprecher | SoundCloud | HardBass EQ zuerst | | | | | | | | |
| _Samsung-Gerät (Modell offen)_ | Lautsprecher | SoundCloud | Player zuerst | | | | | | | | |
| _Samsung-Gerät (Modell offen)_ | Bluetooth-Kopfhörer | Spotify | HardBass EQ zuerst | | | | | | | | |
| _Samsung-Gerät (Modell offen)_ | Bluetooth-Kopfhörer | SoundCloud | HardBass EQ zuerst | | | | | | | | |

Sprint-Abschlusskriterium (roadmap-2026.md §8): mindestens zwei Player und
zwei Audio-Routen ausgefüllt, jeder Fehlschlag mit Diagnosedaten (Kopie des
Diagnoseberichts, seit dieser Session inkl. `DiagnosticsRecorder`-Ereignissen
mit Zeitstempeln) und reproduzierbarem Ablauf belegt.

## Reale Rückmeldung (24. September 2026): Spotify erkannt, SoundCloud/YouTube nicht

Erste tatsächliche Ausführung von Sprint-0-Punkt 4 durch den Nutzer (noch
ohne die formalen Spalten oben ausgefüllt, ohne kopierten Diagnosebericht):
Spotify wird von HardBass EQ als Session erkannt und angebunden; SoundCloud
und YouTube werden **nicht** erkannt.

**Root Cause:** kein Bug im bisherigen `AudioSessionRepository`, sondern die
bekannte Grenze des Broadcast-Mechanismus (`ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`),
siehe `docs/DECISIONS.md` „Eingabedaten für die Entscheidung", Punkt 3, und
den früheren Session-Log-Befund „Control-Intents … No broadcasts received
back." – dieser Broadcast ist ein freiwilliges Opt-in des jeweiligen Players.
Spotify sendet ihn, SoundCloud und YouTube offenbar nicht (mehr). Kein
Empfänger-Code kann einen Player zwingen, ihn zu senden.

**Versuchte, verworfene Gegenmaßnahme:** `AudioSessionRepository.kt` sollte
Sessions zusätzlich über `AudioManager.AudioPlaybackCallback` plus
`AudioPlaybackConfiguration.getAudioSessionId()`/`getPlayerState()` erkennen –
unabhängig von der Kooperation des Players. **CI-Build hat das widerlegt:**
`./gradlew assembleDebug` schlägt mit `Unresolved reference 'playerState'`,
`'PLAYER_STATE_STARTED'` und `'audioSessionId'` fehl
([Lauf 36042295600](https://github.com/kniepertsebastian-spec/equalizer/actions/runs/36042295600)).
Diese drei Member sind in `compileSdk 37`s öffentlichem Stub-JAR schlicht
nicht vorhanden – anders als vermutet sind sie kein Teil der öffentlichen
Android-API, sondern `@SystemApi`/verborgen (nur für Systemapps oder per
Reflection erreichbar, was auf Android 9+ durch die Hidden-API-Policy
zunehmend blockiert wird und speziell auf einem aktuellen Android-16-Gerät
wie dem Pixel 10 sehr wahrscheinlich nicht mehr funktioniert). Die Änderung
wurde vollständig zurückgerollt (`AudioSessionRepository.kt` und
`AndroidManifest.xml` sind wieder im Stand vor diesem Versuch); `MODIFY_AUDIO_SETTINGS`
ist wieder aus dem Manifest entfernt.

**Tatsächlicher Stand:** Es gibt derzeit **keinen bekannten, öffentlichen
API-Weg**, der Fremd-Sessions ohne Kooperation des Players zuverlässig
erkennt. Damit bestätigt sich `docs/DECISIONS.md` Release-Gate A als reales
Problem, nicht nur als theoretisches Risiko: Spotify funktioniert, SoundCloud
und YouTube sind mit dem aktuellen Session-basierten Ansatz (Stand jetzt)
nicht erreichbar. Die verbleibenden, tatsächlich funktionierenden Wege sind
laut `docs/DECISIONS.md`:
- **B/C – eigener Player oder Audio-Capture-Pipeline:**
  `AudioPlaybackCaptureConfiguration` + `MediaProjection` (öffentliche API
  seit API 29) erlaubt es, den System-Sound unabhängig von der Kooperation
  des Players mitzuschneiden, zu verarbeiten und wiederzugeben – technisch
  tragfähig, aber eine grundlegend andere Architektur (eigene Wiedergabe-
  /Capture-Pipeline statt Effekt-Attach auf fremde Sessions), mit eigener
  Nutzerfreigabe (Bildschirmaufnahme-ähnlicher Consent-Dialog) und höherer
  Latenz/Akkulast. Das ist keine kleine Ergänzung mehr, sondern der in
  Release-Gate A beschriebene Architekturentscheid.
- Reflection auf die verborgenen `AudioPlaybackConfiguration`-Member bliebe
  theoretisch möglich, ist aber offiziell nicht unterstützt und auf einem
  Android-16-Gerät voraussichtlich blockiert – nicht empfohlen.

Nächster Schritt liegt bei der Produktentscheidung in
`docs/DECISIONS.md` „Entscheidungsvorlage: Session-EQ vs. eigener Player",
nicht mehr bei einem weiteren Code-Versuch im bisherigen Rahmen.

## Getestete Geräte/Android-Versionen

| Gerät | Android-Version | Getestet am | Ergebnis |
|---|---|---|---|
| Google Pixel 10 | Android 16 | 18. September 2026 | ✅ App startet, Debug-Effektliste (`AudioEffect.queryEffects()`) funktioniert. **Bestätigt vorhanden:** `EqualizerBundle` (NXP, `0bed4300-ddd6-11db-8f34-0002a5d5c51b`), `DynamicsProcessing` (AOSP, `7261676f-6d75-7369-6364-28e2fd3ac39e`), `Dynamic Bass Boost` (NXP, `0634f220-ddd4-11db-a0fc-0002a5d5c51b`), `Loudness Enhancer` (AOSP, `fe3199be-aed0-413f-87bb-11260eb63cf1`) sowie Virtualizer, Visualizer, Noise Suppression, Acoustic Echo Canceler, Haptic Generator, Decibel Spatializer Library (Google Pixel), Multichannel Downmix, diverse Reverb-Effekte. Damit hat dieses Gerät die beiden für den EQ zentralen Effekte (`Equalizer`, `DynamicsProcessing`) – gute Voraussetzung für den Session-Attach-Spike. Die vier o. g. UUIDs stimmen exakt mit den in `AudioCapabilitiesMapperTest` verwendeten Konstanten überein – zusätzliche Bestätigung, dass diese Werte korrekt sind. |

## Session-Attach-Spike (Google Pixel 10, Android 16, 18. September 2026)

Getestet über den Button „Show session-attach spike (M0)" (PR #2, Commit `8bf1a99`).
Eigener Testton in selbst erzeugter Audio-Session (ID `665`), kein Zugriff auf
fremde Sessions oder Session `0`.

**Equalizer** – Attach/Read-back/Release erfolgreich, kein Crash:

| Band | Zentrum | Frequenzbereich |
|---|---|---|
| 0 | 60 Hz | 30–120 Hz |
| 1 | 230 Hz | 120–460 Hz |
| 2 | 910 Hz | 460–1800 Hz |
| 3 | 3600 Hz | 1800–7000 Hz |
| 4 | 14000 Hz | 7000–20000 Hz |

Gain-Bereich: −15.0…15.0 dB. 5 Bänder gesamt.

**DynamicsProcessing-Stufen** – alle vier isoliert getestet, alle **OK**:

| Stufe | Ergebnis | Detail |
|---|---|---|
| INPUT_GAIN | ✅ OK | `inputGain readback=0.0 dB` |
| PRE_EQ | ✅ OK | `preEq bandCount=1` |
| MBC | ✅ OK | `mbc bandCount=1` |
| LIMITER | ✅ OK | `limiter enabled=true` |

Fazit: Pixel 10/Android 16 unterstützt sowohl `Equalizer` als auch alle vier
getesteten `DynamicsProcessing`-Stufen vollständig. Sehr gute Voraussetzung
für M2/M3.

## Control-Intents und Session-0-Experiment (Google Pixel 10, Android 16, 18. September 2026)

Getestet über PR #4, Commit `ca03124`.

**Session-0-Experiment:** `[FAIL] Cannot initialize effect engine for type: …`
(vollständige Fehlermeldung vom Nutzer noch nachzutragen). Sauberes,
erwartetes Fehlschlagen ohne Crash – bestätigt Roadmap §2's Einschätzung,
dass Session-`0`-Zugriff geräteabhängig ist und hier nicht funktioniert.
Kein unterstütztes Feature, wie vorgesehen.

**Control-Intent-Test:** „No broadcasts received back." – reproduziert in
zwei Durchläufen: erst mit festem 300-ms-Delay (Commit `ca03124`), dann
erneut mit aktivem Warten bis zu 3 s über einen `Channel`
(`testControlIntents()`, Commit `066ac6f`). Kein Unterschied – Timing-Problem
damit ausgeschlossen. Die beiden Actions sind laut AOSP-Quellcode
(`frameworks/base/core/res/AndroidManifest.xml`) keine `protected broadcasts`;
Registrierung/Versand folgen dem für API 33+ korrekten Muster
(`RECEIVER_NOT_EXPORTED`). Eine Pixel-spezifische, nicht öffentlich
einsehbare Zusatzsperre ist plausibel, aber ohne `adb logcat` am Gerät nicht
weiter eingrenzbar. **Fazit:** Der klassische Open/Close-Broadcast-Mechanismus
funktioniert auf diesem Gerät für selbst gesendete Broadcasts nicht
zuverlässig – dokumentiertes, valides Spike-Ergebnis (kein Blocker, siehe
`docs/FEASIBILITY.md`).

## Automatisiert (CI, kein physisches Gerät)

| Prüfung | Status |
|---|---|
| `./gradlew assembleDebug` | ✅ Grün, PR #2, Commit `8bf1a99` (Lauf https://github.com/kniepertsebastian-spec/equalizer/actions/runs/35394937425). Zwischenzeitlich war der Workflow ab Commit `c181a9c` fünf Commits lang komplett ungültig (`secrets` in `if:`-Bedingung, siehe `docs/DRIVE_UPLOAD.md`) – 0 Jobs, sofort rot, ohne dass eine Benachrichtigung ankam. |
| `./gradlew testDebugUnitTest` (u.a. `AudioCapabilitiesMapperTest`, `SineWaveGeneratorTest`) | ✅ Grün, gleicher CI-Lauf wie oben. |

CI läuft auf `ubuntu-latest` (GitHub-Actions-Standard-Runner mit vorinstalliertem
Android-SDK), nicht auf einem echten oder emulierten Android-Gerät. Damit ist
nur der Build- und Unit-Test-Pfad abgedeckt, keine Laufzeit-Verifikation von
`AudioEffect`-Verhalten auf einem Gerät.

## Nächste Schritte

1. Sobald ein Emulator- oder Geräte-Zugriff verfügbar ist: Session-Attach-Spike
   (offene M0-Punkte aus `docs/FEASIBILITY.md`) auf mindestens einem Emulator
   plus einem physischen Gerät durchführen und hier protokollieren.

## M1 – Hilt/KSP-Build repariert (19. September 2026)

PR #5 wurde gemergt, obwohl der letzte PR-Lauf
[35400854917](https://github.com/kniepertsebastian-spec/equalizer/actions/runs/35400854917)
auf `fe0db2d` scheiterte: Java-Ziel 17 / Kotlin-Ziel 21.
PR #6 setzt das Kotlin-Ziel explizit auf 17 (ADR 0002).

| Prüfung | Nachweis |
|---|---|
| Debug-Build mit Hilt/KSP | Erfolgreich, Commit `007f53f`, [CI-Lauf 35404523035](https://github.com/kniepertsebastian-spec/equalizer/actions/runs/35404523035) |
| `testDebugUnitTest` | Erfolgreich im selben Lauf; bestehende Mapper-, Testton- und ViewModel-Tests |
| Debug-APK | Artefakt `app-debug-apk` im selben Lauf erfolgreich hochgeladen |
| Lokaler Windows-Build | Nicht ausführbar: kein Java auf PATH, JAVA_HOME nicht gesetzt; Wrapper bricht vor Gradle ab |
| `git diff --check` | Erfolgreich |
| Lint / Formatierung / detekt | Noch nicht in CI eingerichtet; offen in M1 |
| Neues Gerät / Emulator / Hörtest | Nicht durchgeführt; bisherige Pixel-Ergebnisse gelten nur für den damaligen M0-Stand |

Nächster automatisierter Schritt: Android Lint in CI aufnehmen. Die offenen
M0-Geräte- und Bypass-Prüfungen werden durch diesen Build-Fix nicht ersetzt.

## M1 – Android Lint in CI (19. September 2026)

PR #7, Commit `97a887d`,
[CI-Lauf 35405675078](https://github.com/kniepertsebastian-spec/equalizer/actions/runs/35405675078):

| Prüfung | Ergebnis |
|---|---|
| `assembleDebug` | Erfolgreich |
| `testDebugUnitTest` | Erfolgreich |
| `lintDebug` | 0 Fehler, 16 Warnungen |
| `lintRelease` | 0 Fehler, 16 Warnungen |
| Lint-Berichte | Artefakt `android-lint-reports` erfolgreich hochgeladen und geprüft |
| Debug-APK / Testberichte | Erfolgreich hochgeladen |
| Lokale statische Prüfung | `git diff --check` erfolgreich |
| Handy / Emulator | Nicht ausgeführt; reine CI-Erweiterung |

Die 16 Warnungen pro Variante bestehen aus 13 Dependency-Update-Hinweisen,
einem Target-SDK-Hinweis sowie `ObsoleteSdkInt` und `MonochromeLauncherIcon`
für das Launcher-Icon. Keine Baseline oder Suppression hinzugefügt.
Die Warnungen werden als Folgearbeiten geführt, nicht als behoben ausgegeben.
Anleitung und verbleibende Aufgaben: `docs/QUALITY.md`.

## M1 – ktlint eingerichtet (19. September 2026)

`org.jlleitschuh.gradle.ktlint` (`14.2.0`) neu in CI vor dem Build. Da diese
Sandbox weiterhin keinen Android-SDK-Zugriff hat (siehe
`docs/DEPENDENCIES.md`), lief `./gradlew ktlintCheck` selbst nicht lokal –
bestätigt stattdessen mit einem eigenständig heruntergeladenen
`ktlint-cli-1.8.0` (identische Regel-Engine, ohne Android-Gradle-Plugin):

| Prüfung | Ergebnis |
|---|---|
| `ktlint "app/src/**/*.kt"` (vor Formatierung) | 11 Regeltypen verletzt, u. a. Funktionsnamen von `@Composable`-Funktionen (Compose-Konvention vs. ktlint-Standardregel) |
| `.editorconfig` mit `ktlint_function_naming_ignore_when_annotated_with = Composable` | Behebt die Namenskonflikte, ohne Code zu ändern |
| `ktlint --format "app/src/**/*.kt"` | Alle übrigen, automatisch korrigierbaren Verstöße behoben (Importreihenfolge, Mehrzeilen-Ausdrücke, Funktionssignaturen, u. a.) |
| `ktlint "app/src/**/*.kt"` (danach) | 0 Verstöße, Exit-Code 0 |

Endgültige Bestätigung mit dem echten Gradle-Plugin (inkl. Zusammenspiel mit
AGP/Hilt/KSP) steht noch aus und folgt über den nächsten CI-Lauf. detekt bleibt
zurückgestellt, siehe `docs/adr/0003-ktlint-detekt-deferred.md`.

## Kontext-Modi: Was jetzt am Gerät getestet werden muss (30. September 2026)

Automatisiert (CI): Preset-Werte, Namensklassifikation, Subsonic-/Loudness-
Kurven, PCM16-Exciter gegen synthetische Töne, ViewModel-Logik für Umschaltung,
Wiederherstellung und Lautstärkefolge. **Nicht** automatisiert prüfbar:

| # | Prüfung | Erwartung |
|---|---|---|
| K1 | Telefon mit dem Auto per Bluetooth verbinden, Gerät hat noch kein Profil | Preset „Auto“ wird aktiv, „Route: <Fahrzeugname>“ zeigt „Auto erkannt“ |
| K2 | Dasselbe mit einer Bluetooth-Box (Name mit „Speaker“/„Flip“/„Boom“ …) | Preset „Bluetooth-Box“ wird aktiv |
| K3 | Gerät hat einen Namen ohne Stichwort (z. B. Kürzel im Auto) | Kein Automatikwechsel; manuelle Wahl von „Auto“ merkt sich die Route |
| K4 | Verbindung trennen (Route ohne Profil) | vorheriges Preset kommt zurück |
| K5 | Medienlautstärke im Auto von laut nach leise | Bass/Höhen werden hörbar voller, beim Hochregeln wieder neutral; kein Knacken |
| K6 | Subsonic-Schalter A/B bei einem basslastigen Track | weniger Pumpen/Verzerren der Box, Bass straffer; Hörbarkeit hängt vom untersten Systemband ab |
| K7 | Virtual Bass A/B im eingebauten Player (SoundCloud) auf kleiner Box/Telefonlautsprecher | Bass wirkt tiefer/präsenter, keine hörbaren Artefakte, kein Knacken bei Seek/Trackwechsel |
| K8 | Virtual Bass bei Spotify | Schalter hat dort keine Wirkung (erwartet, steht in der UI) |
| K9 | CPU/Akku bei 30 Minuten Wiedergabe mit Virtual Bass | unauffällig gegenüber ausgeschaltet |
| K10 | Android Auto (kabelgebunden/drahtlos) | dokumentieren, ob eine Session erkannt wird und ein Effekt greift |

## Großer Player und Playlist-Import: Was jetzt am Gerät getestet werden muss (30. September 2026)

Automatisiert (CI): Link-Erkennung/-Bereinigung, Playlist-JSON, Queue-Logik,
Zeit-/Cover-Formatierung, ViewModel-Logik für den Import. **Nicht** automatisiert
prüfbar (Netzwerk, Audio, Systemdialoge):

| # | Prüfung | Erwartung |
|---|---|---|
| P1 | Mini-Leiste antippen | großer Player öffnet; Fortschrittsstreifen in der Leiste läuft mit |
| P2 | Balken ziehen und loslassen | spult erst beim Loslassen, Zeit-Labels folgen |
| P3 | Vor/Zurück; Zurück nach >3 s | Vor/Zurück wechselt den Titel; Zurück nach >3 s startet den Titel neu |
| P4 | Titel läuft zu Ende | nächster Titel startet automatisch; am Ende der Queue bleibt Pause |
| P5 | SoundCloud-Playlist-Link einfügen (öffentlich, >50 Titel) | „Gespeichert: … (n Titel)“, n stimmt, Reihenfolge stimmt |
| P6 | Playlist abspielen | erster Titel spielt, Cover/Titel stimmen, Queue zeigt alle Titel |
| P7 | Einzeltitel-Link; `on.soundcloud.com`-Kurzlink; Link mit `?si=…` | spielt sofort |
| P8 | In der SoundCloud-App „Teilen“ → HardBass EQ | App öffnet den Player und importiert; bei laufender App keine zweite Instanz |
| P9 | Spotify-/YouTube-Link einfügen | verständliche Ablehnung, nichts wird abgespielt |
| P10 | Private oder leere Playlist; Profil-Link | verständliche Fehlermeldung |
| P11 | Flugmodus während Playlist läuft | Titel wird übersprungen/Fehler, App stürzt nicht ab |
| P12 | Lange Wiedergabe (30 min) mit Fortschrittsanzeige | Akku/CPU unauffällig |
| P13 | SoundCloud-Suche (Button „Suchen“), Ergebnis antippen | spielt und liegt in der Queue; Vor/Zurück wandert durch die Ergebnisse |

| # | Prüfung (SoundCloud Go) | Erwartung |
|---|---|---|
| G1 | Im Player-Screen „Anmelden“, Konto mit Go-Abo | danach „Angemeldet“ (auch nach App-Neustart) |
| G2 | Go-only-Titel abspielen, angemeldet | volle Länge, kein „Nur 30-Sekunden-Vorschau“ |
| G3 | Derselbe Titel, abgemeldet | „Nur 30-Sekunden-Vorschau“ + Hinweis zum Anmelden |
| G4 | 10 Titel hintereinander | keine Audio-Werbung zwischen den Titeln |
| G5 | Titel, bei dem nur HLS angeboten wird | spielt (HLS-Modul), Equalizer/Virtual Bass greifen |
| G6 | „Abmelden“ | Token weg, Go-Titel wieder nur Vorschau |

| # | Prüfung (Kontext-Modus als Ebene) | Erwartung |
|---|---|---|
| M1 | „Auto“ einschalten, dann Genre auf Uptempo wechseln | „Auto“ bleibt markiert, Subsonic/Loudness/Virtual Bass bleiben an |
| M2 | „Auto“ im Player-Screen ein-/ausschalten | wirkt sofort, Zustand stimmt mit dem Hauptscreen überein |
| M3 | App neu starten | Modus ist weiterhin so, wie zuletzt gesetzt |
| M4 | Mit dem Auto verbinden, später trennen | Modus geht an und beim Trennen wieder aus (nur wenn automatisch eingeschaltet) |
| M5 | Player-Screen: Lesbarkeit | Titel hell-lila, Interpret rosa, „Warteschlange“ hell; oberste Zeile nicht unter der Statusleiste |

| # | Prüfung (SoundCloud-Bibliothek) | Erwartung |
|---|---|---|
| L1 | Angemeldet den Player-Screen öffnen | Karte „Meine SoundCloud-Bibliothek“ lädt; „Meine Playlists“ und „Gelikte Playlists“ erscheinen mit Titelanzahl |
| L2 | „Likes“ antippen | Liked-Titel werden geladen und laufen als Queue |
| L3 | Eine eigene Playlist antippen (>50 Titel) | alle Titel in der Queue, richtige Reihenfolge |
| L4 | Private eigene Playlist | lädt (Token) und spielt |
| L5 | Abmelden | Bibliothek verschwindet; Go-Titel wieder nur Vorschau |
| S1 | Musik im eigenen Player, Handy sperren (selbst und per Timeout), 5 Min. laufen lassen | kein Kratzen, kein Aussetzen |
| S2 | Wie S1 mit Limiter aus (Klang-Feinschliff) | zeigt, ob der Limiter der Auslöser war |
| S3 | Titel antippen | Zeile leuchtet pulsierend mit Equalizer-Symbol |
| S4 | Nächster Titel | vorheriger bekommt Haken und ist gedimmt (Queue, Playlist, Entdecker-Liste) |
| X1 | Öffentliche Spotify-Playlist teilen/einfügen | Fortschritt „Suche … n von m“, danach neue Playlist „… (von Spotify)“ mit den gefundenen Titeln |
| X2 | Meldung nach X1 | nennt „n von m gefunden“ und bis zu 5 nicht gefundene Titel |
| X3 | Private Playlist oder Kurzlink | verständliche Fehlermeldung, nichts gespeichert |
| X4 | Einzelnen Spotify-Titel teilen | Meldung „Suche „Künstler - Titel“ …“; bei sicherem Treffer startet er von selbst |
| X5 | Spotify-Titel mit häufigem Namen (z. B. „Roar“) | richtiger Künstler steht oben in der Trefferliste |
| X6 | Zwei Spotify-Playlist-Links in einem Text teilen/einfügen | eine gemeinsame Playlist „… + 1 weitere (von Spotify)“, doppelte Titel nur einmal |
| X7 | Playlists → „Zusammenführen“, zwei Playlists wählen, Namen eingeben | neue Playlist mit allen Titeln, Originale bleiben |
| X8 | Spotify-Playlist mit mehr als 100 Titeln (bzw. mehrere Teil-Links) importieren | Fortschritt „Teil n von m“, je Block eine Playlist „… – Teil n von m …“ |
| X9 | Import mitten drin abbrechen (App schließen oder Flugmodus), dann Link nochmal einfügen oder „Fortsetzen“ | macht beim gespeicherten Stand weiter, kein Block doppelt |
| X10 | Playlist mit genau 100 Titeln | Meldung weist auf die 100er-Grenze von Spotify hin |
| X11 | Dieselbe Spotify-Playlist zweimal teilen | beim zweiten Mal „Nichts Neues“, keine zweite Playlist |
| X12 | In Spotify einen Titel ergänzen, nochmal teilen | nur der neue Titel kommt als „… (Nachtrag)“ |
| X13 | Importierte Playlist löschen, Link nochmal teilen | wird wieder angelegt |
| X14 | In Spotifys Entwicklerportal App anlegen (Web API, Redirect-URI `hardbasseq://spotify-callback`), Client-ID im Player unter „Spotify-Konto“ eintragen, „Anmelden“ | Browser öffnet Spotify-Login, danach zurück in der App, Toast „Mit Spotify angemeldet“, Karte zeigt „Angemeldet“ |
| X15 | Eigene Spotify-Playlist mit mehr als 100 Titeln teilen | alle Titel werden gelesen (Meldung ohne 100er-Hinweis), Teile à 100 als Playlists |
| X16 | Fremde öffentliche Playlist teilen (angemeldet) | Seiten-Weg mit max. ~100 Titeln, Meldung nennt den Grund („Spotify-Anmeldung: … nur für Playlists, die dir gehören“) |
| X17 | „Abmelden“ | Karte zeigt wieder „Anmelden“, Import nutzt wieder die öffentliche Seite |
| L6 | Playlist aufklappen, auf einen Titel in der Mitte tippen (oder sein Play-Symbol) | die Playlist startet ab diesem Titel, die Titel davor sind in der Warteschlange davor |
| L7 | Playlist mit ≥ 6 Titeln aufklappen, im Suchfeld „angerf“ tippen | nur passende Titel bleiben; Tipp auf einen Treffer startet an seiner Stelle in der ganzen Playlist |
| L8 | Suche ohne Treffer | „Kein Titel passt zu …“ |
| C1 | Titel im Player starten, Handy per Bluetooth mit dem Auto verbunden | Autodisplay zeigt Titel und Interpret (ggf. Cover) statt „Inhalt nicht gefunden“ |
| C2 | Weiter/Zurück/Pause am Lenkrad oder Autoradio | wirkt auf die Warteschlange des Players; Anzeige zieht nach |
| C3 | Sperrbildschirm und Benachrichtigung ansehen | Titel, Interpret und die drei Tasten sind da |
| C4 | Titel pausieren | Benachrichtigung zeigt „Abspielen“ und lässt sich wegwischen |
| A1 | Android Auto: Entwickleroptionen → „Unbekannte Quellen“ an, Handy verbinden | HardBass EQ erscheint in der Medienauswahl |
| A2 | Öffnen | Ordner „Meine Playlists“, „Interesting new uploads“, „Likes“ |
| A3 | Playlist öffnen, Titel antippen | Titel startet, Playlist läuft ab diesem Titel weiter; Titel/Interpret auf dem Display |
| A4 | Weiter/Zurück/Pause im Auto | wirkt wie bei Bluetooth |
| A5 | App vorher komplett beendet, dann Auto verbinden | Browser lädt, Titel startet (Player-Dienst wird dabei gestartet) |
| A6 | Sprachbefehl „Spiel <Playlist-Name> auf HardBass EQ“ | die Playlist startet |
| A7 | „Spiel <Künstler> auf HardBass EQ“ (in keiner Playlist) | SoundCloud-Treffer laufen als Warteschlange |
| L6 | Flugmodus, dann Neu-laden-Symbol | verständliche Fehlermeldung, vorherige Anzeige bleibt |

| # | Prüfung (YouTube/Spotify → SoundCloud) | Erwartung |
|---|---|---|
| B1 | YouTube-Link eines Hardcore-Tracks teilen, der auf SoundCloud liegt | Treffer erscheint, bei „Artist - Titel“ startet er von selbst |
| B2 | Dasselbe mit YouTube Music (`music.youtube.com`) | wie B1 |
| B3 | Spotify-Titel-Link | Trefferliste ohne Auto-Start (kein Künstler bekannt) |
| B4 | Titel, der nicht auf SoundCloud liegt (Mainstream) | „Nichts Passendes …“ oder nur niedrige Trefferquoten |
| B5 | Link mit Zusätzen („(Official Video)“, „ft.“, Remix) | richtiger Titel bzw. Remix wird gefunden |
| B6 | Playlist-/Album-/Kanal-Link | verständliche Ablehnung, kein Netzwerkzugriff |
| B7 | Privates/gesperrtes Video | Meldung „keine Angaben“ |

| # | Prüfung (Interesting new uploads) | Erwartung |
|---|---|---|
| U1 | Einen Künstler eintragen („Merken“) | Liste füllt sich, 15–20 Titel, nur Einzeltitel, Datum „heute/vor n Tagen“ |
| U2 | Mehrere Künstler | Titel abwechselnd, nicht nur von einem |
| U3 | Titel nach links/rechts wischen (und ✕) | verschwindet, Liste füllt aus dem Reservepool auf; nach „Jetzt aktualisieren“ kommt er nicht wieder |
| U4 | „Alles abspielen“, einen Titel antippen | Queue startet dort; Streams werden frisch aufgelöst |
| U5 | Künstler entfernen | seine Titel verschwinden |
| U6 | Nach Montag (Gerät-Datum vorstellen oder warten) App öffnen/Hintergrund | Liste neu, bevorzugt ungesehene Titel |
| U7 | Flugmodus, „Jetzt aktualisieren“ | Fehlermeldung, alte Liste bleibt |
| U8 | Sehr allgemeiner Name (z. B. „Sub“) | zeigt, wie viel Fremdes kommt – ggf. Namen genauer eintragen |
| U9 | Künstler „MBK“, Genre „Wie meine Musik“ (Likes: Uptempo) | kein Schlager-/Pop-MBK in der Liste; Meldung nennt aussortierte Titel |
| U10 | „Eigene“, Stichwort „uptempo“ hinzufügen/entfernen | Liste wird sofort neu gebaut; ohne Stichwörter kein Filter |
| U11 | Genre „Aus“ | alle Treffer wie vorher |
| U12 | Titel ohne Genre-Angabe | erscheint, aber hinter den passenden |
| D1 | Player-Screen → „Klang-Feinschliff“ | Mono-Bass und Limiter sind standardmäßig an |
| D2 | Mono-Bass aus/an, Regler bewegen (SoundCloud-Titel, In-Ears) | Bass wird beim Einschalten straffer/mittiger, Wechsel ohne Neustart |
| D3 | Limiter aus/an bei starkem Bass-Boost (Auto-Modus, Virtual Bass) | mit Limiter kein Kratzen/Clipping; Aus klingt wie vorher |
| D4 | App neu starten | Schalterstellung und Reglerwert bleiben |
| L1 | Playlists → „Neue Playlist“, Namen eingeben | leere Playlist erscheint (0 Titel); leerer Name nicht möglich |
| L2 | Playlist-Symbol am laufenden Titel → Playlist wählen | Meldung „Zu … hinzugefügt“; Zähler steigt; derselbe Titel nochmal → Hinweis, kein Duplikat |
| L3 | Playlist-Symbol in der Warteschlange → „Neu anlegen und hinzufügen“ | neue Playlist mit diesem Titel |
| L4 | Playlist antippen (Namen) → ✕ bei einem Titel | Titel verschwindet; importierte Playlists haben kein ✕ |
| L5 | Eigene Playlist abspielen, App neu starten | Playlist bleibt erhalten |
| H1 | Kopfhörer-Modus an → Karte „Kopfhörer-Power“ erscheint; „Knall“ tippen | Bass deutlich kräftiger, Regler springen auf 7 / 12 dB, Dynamik-Schalter aus; kein Kratzen/Übersteuern |
| H2 | „Standard“ tippen | Klang wie vor der Funktion (2,5 dB / 9 dB / Dynamik an) |
| H3 | Bass-Regler auf 0, Loudness auf 0 | Bass-Anhebung bzw. Loudness entfallen hörbar |
| H4 | App neu starten | Reglerstellungen bleiben; Karte nur im Kopfhörer-Modus sichtbar |
| T1 | Bei SoundCloud angemeldet, Wolken-Symbol an einer Playlist | Meldung „1 Playlist(s) zu SoundCloud übertragen (privat)“; in der SoundCloud-App erscheint sie als private Playlist mit den Titeln; Zeile zeigt „bei SoundCloud“ |
| T2 | Titel hinzufügen, erneut übertragen | dieselbe SoundCloud-Playlist ist aktualisiert, keine zweite |
| T3 | „Alle zu SoundCloud“ | alle nicht leeren Playlists drüben; nicht angemeldet → Hinweis zur Anmeldung |
| N1 | App starten | Startet im Player; unten Leiste „Player“ / „Equalizer“; Wechsel hält Wiedergabe und Zustand |
| N2 | Player → Tab „Suchen“, Begriff eingeben, „Suchen“ | Treffer erscheinen, Tipp startet den Titel; ohne Treffer Hinweis; ohne Netz Fehlermeldung + „Erneut versuchen“ |
| N3 | Quellenwähler „SoundCloud ▾“ | Menü mit SoundCloud, YouTube, Anderer Player; YouTube öffnet den YouTube-Player; „Anderer Player“ zeigt Erklärung |
| N4 | Tab „Playlists“: Link einfügen/teilen | geteilter Link öffnet „Playlists“; importierte Liste steht darunter |
| N5 | Tab „Warteschlange“ leer / gefüllt | leer: Erklärung + „Zur Suche“; gefüllt: Anzahl, laufender Titel, Liste |
| N6 | EQ aus/Bypass/ohne Wiedergabe/mit Wiedergabe | Player zeigt „EQ ist aus“ / „Wartet auf Wiedergabe“ / „EQ aktiv für SoundCloud“, nie „aktiv“ ohne Bestätigung |
| N7 | Equalizer-Bereich bei laufender Musik | Mini-Player mit Titel, Play/Pause und „Zum Player“ |
