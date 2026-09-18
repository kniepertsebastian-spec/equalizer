# Dependencies

Stand: 18. September 2026. Geprüft laut Vorgabe in `roadmap.md` §6 vor dem
ersten Projekt-Setup (M0, Aufgabe 1 "Android-Projekt initialisieren").

## Gewählte Versionen

| Komponente | Version | Quelle/Begründung |
|---|---|---|
| Android Gradle Plugin (AGP) | 9.4.0 | Aktuellste stabile Release-Notes-Seite (September 2026): https://developer.android.com/build/releases/agp-9-4-0-release-notes |
| Gradle | 9.7.1 | Aktuellste stabile Gradle-Version (19. August 2026), kompatibel zu AGP 9.x (AGP 9.0 verlangt laut Android-Doku mindestens Gradle 9.1). |
| Kotlin | 2.4.20 | Aktuellste stabile JetBrains-Ankündigung (September 2026): https://blog.jetbrains.com/kotlin/2026/09/kotlin-2-4-20-released/ |
| Jetpack Compose BOM | 2026.08.00 | Aktuellste stabile BOM-Version, Compose 1.12 Kernmodule: https://android-developers.googleblog.com/2026/08/jetpack-compose-august-2026-release.html |
| `compileSdk` | 37 (Android 17) | Android 17 ist seit Juni 2026 stabil verfügbar; `compileSdk` darf auf der neuesten stabilen Plattform stehen. |
| `targetSdk` | 36 (Android 16) | Play-Store-Pflichtwert bis 31. August 2027 (danach 37); konservativ auf der aktuell verlangten Stufe gehalten. |
| `minSdk` | 28 | Durch Roadmap §3 fest vorgegeben (`DynamicsProcessing` ab API 28 verfügbar). |
| JVM-Zielversion | 17 | Von AGP 9.x/Kotlin 2.4 empfohlene Baseline; Build-JDK ist 21 (Temurin, siehe CI). |

## Wichtiger Hinweis zur Verifikation

Diese Sandbox-Umgebung kann `dl.google.com` (Android-SDK/Maven-„Google“-Repository)
nicht erreichen (Egress-Policy blockiert `CONNECT` auf diesen Host). Ein lokaler
Android-SDK-Download und damit ein lokaler `./gradlew assembleDebug`-Lauf waren
in dieser Session **nicht möglich**. Der Gradle-Wrapper wurde stattdessen mit dem
im Container vorinstallierten System-Gradle (8.14.3) in einem leeren Scratch-Verzeichnis
erzeugt (`gradle wrapper --gradle-version 9.7.1 --distribution-type bin`) und danach
ins Projekt kopiert.

Die eigentliche Build-Verifikation läuft über `.github/workflows/ci.yml` auf
`ubuntu-latest`-Runnern, die einen vorinstallierten Android-SDK mitbringen und
uneingeschränkten Internetzugang haben. Solange CI nicht grün bestätigt wurde,
gilt Aufgabe 2 aus Roadmap §16 ("reproduzierbaren Debug-Build herstellen") als
technisch vorbereitet, aber noch nicht verifiziert.

## Lokaler Build (sobald ein Android SDK verfügbar ist)

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

`local.properties` mit `sdk.dir=<Pfad zum Android SDK>` wird von Android Studio
automatisch erzeugt und ist in `.gitignore` ausgeschlossen.
