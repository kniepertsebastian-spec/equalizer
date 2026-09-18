# Test-Matrix

Wird am Ende jeder Arbeitssession aktualisiert (siehe `roadmap.md` §15,
Punkt 10 und §10 M0-Abnahmekriterium "Geräte-Matrix").

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
