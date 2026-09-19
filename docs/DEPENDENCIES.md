# Dependencies

Stand: 18. September 2026. Geprüft laut Vorgabe in `roadmap.md` §6 vor dem
ersten Projekt-Setup (M0, Aufgabe 1 "Android-Projekt initialisieren").

## Gewählte Versionen

| Komponente | Version | Quelle/Begründung |
|---|---|---|
| Android Gradle Plugin (AGP) | 9.4.0 | Aktuellste stabile Release-Notes-Seite (September 2026): https://developer.android.com/build/releases/agp-9-4-0-release-notes |
| Gradle | 9.7.1 | Aktuellste stabile Gradle-Version (19. August 2026), kompatibel zu AGP 9.x (AGP 9.0 verlangt laut Android-Doku mindestens Gradle 9.1). |
| Kotlin | 2.3.20 | Für Hilt/KSP in M1 festgelegt; ersetzt die ursprüngliche M0-Version 2.4.20. Siehe ADR 0002. |
| Jetpack Compose BOM | 2026.08.00 | Aktuellste stabile BOM-Version, Compose 1.12 Kernmodule: https://android-developers.googleblog.com/2026/08/jetpack-compose-august-2026-release.html |
| `compileSdk` | 37 (Android 17) | Android 17 ist seit Juni 2026 stabil verfügbar; `compileSdk` darf auf der neuesten stabilen Plattform stehen. |
| `targetSdk` | 36 (Android 16) | Play-Store-Pflichtwert bis 31. August 2027 (danach 37); konservativ auf der aktuell verlangten Stufe gehalten. |
| `minSdk` | 28 | Durch Roadmap §3 fest vorgegeben (`DynamicsProcessing` ab API 28 verfügbar). |
| JVM-Zielversion | 17 | Java `compileOptions` und Kotlin `compilerOptions.jvmTarget` explizit auf 17; Build-JDK ist separat 21 (Temurin, siehe CI). |
| ktlint (`org.jlleitschuh.gradle.ktlint`) | 14.2.0 | Aktuellste Plugin-Version (März 2026); unabhängig von der Projekt-Kotlin-Version, da ktlint mit einem eigenen gebündelten Kotlin-Compiler parst. Siehe ADR 0003. |

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

**Korrektur nach erstem CI-Lauf:** AGP 9.0+ bringt Kotlin-Unterstützung fest
eingebaut mit ("built-in Kotlin"); das separate Plugin
`org.jetbrains.kotlin.android` darf danach nicht mehr angewendet werden und
lässt den Build fehlschlagen
(https://developer.android.com/build/migrate-to-built-in-kotlin). Entfernt aus
`build.gradle.kts` (root + `app/`) und aus `gradle/libs.versions.toml`. Das
Compose-Compiler-Plugin `org.jetbrains.kotlin.plugin.compose` bleibt
weiterhin nötig und angewendet. `kotlinOptions { jvmTarget = "17" }` wurde
ebenfalls entfernt, da der JVM-Target-Wert laut Migrationsleitfaden automatisch
von `android.compileOptions.targetCompatibility` übernommen wird.

**Korrektur für M1 (Hilt/KSP):** KSP (für Hilt/Room benötigt) unterstützt
AGP 9s eingebautes Kotlin noch nicht und hinkt zudem der Kotlin-Version
hinterher (zielt auf `2.3.20`, nicht `2.4.20`). Kotlin daher auf `2.3.20`
zurückgestuft, `android.builtInKotlin=false` gesetzt und
`org.jetbrains.kotlin.android` wieder angewendet – Details und Begründung
in `docs/adr/0001-defer-ksp-based-tooling.md` und
`docs/adr/0002-hilt-ksp-setup.md`. Neu dazugekommen: KSP `2.3.12`, Hilt
`2.59.2`, `androidx.hilt:hilt-navigation-compose` `1.4.0`.

## Lokaler Build (sobald ein Android SDK verfügbar ist)

**Korrektur nach PR #5 (19. September 2026):** Mit deaktiviertem
built-in Kotlin wurde das Kotlin-JVM-Ziel nicht mehr aus den Java-Optionen
übernommen. CI-Lauf `35400854917` scheiterte an Java 17 / Kotlin 21.
`app/build.gradle.kts` setzt deshalb `kotlin.compilerOptions.jvmTarget`
explizit auf `JvmTarget.JVM_17`, gemäß der
[Kotlin-Compiler-Dokumentation](https://kotlinlang.org/docs/gradle-compiler-options.html#migrate-away-from-android-kotlinoptions).
Die JVM-Zielprüfung bleibt aktiv; Build-JDK und Abhängigkeiten werden nicht geändert.

In der Windows-Arbeitsumgebung dieser Session ist kein Java auf dem PATH
und `JAVA_HOME` nicht gesetzt. Der lokale Wrapper-Aufruf endet daher vor
der Gradle-Ausführung. Build-/Testnachweise stehen in `docs/TEST_MATRIX.md`.

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

`local.properties` mit `sdk.dir=<Pfad zum Android SDK>` wird von Android Studio
automatisch erzeugt und ist in `.gitignore` ausgeschlossen.
