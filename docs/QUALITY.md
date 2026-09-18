# Qualitätsprüfungen

## Lokal und in CI

Voraussetzungen: JDK 21 und Android SDK wie in `DEPENDENCIES.md` beschrieben.
Im Repository-Verzeichnis ausführen (Windows: `gradlew.bat` statt `./gradlew`):

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug lintRelease
```

Android Lint gehört zum vorhandenen Android Gradle Plugin; es benötigt kein
zusätzliches Plugin und wird nicht automatisch durch `assembleDebug` ausgeführt.
Debug und Release werden explizit geprüft, damit auch variantenspezifische
Quellen und Abhängigkeiten berücksichtigt werden.

Die CI führt diese Schritte vor dem APK-Upload aus. Lint-Fehler lassen den
Job fehlschlagen. Warnungen bleiben in den Berichten sichtbar; es gibt keine
Baseline, keine global deaktivierten Prüfungen und kein `continue-on-error`.

HTML-/XML-Berichte liegen unter `app/build/reports/lint-results-*` und werden
als GitHub-Actions-Artefakt `android-lint-reports` auch nach einem fehlgeschlagenen
Lint-Lauf aufbewahrt. Scheitert der Build schon vor Lint, kann noch kein Bericht
existieren; der Upload meldet dann eine Warnung.

Quelle: [Android Lint über die Kommandozeile](https://developer.android.com/studio/write/lint#commandline).

## Weiterhin offen

- Erster Lauf in PR #7: je Variante 0 Fehler und 16 Warnungen. Davon betreffen
  13 mögliche Dependency-Updates, eine `targetSdk = 36` und zwei das Icon
  (`ObsoleteSdkInt`, `MonochromeLauncherIcon`). Keine Warnung wurde unterdrückt.
  Dependency-/Target-SDK-Updates werden getrennt auf Kompatibilität geprüft;
  die Icon-Hinweise können mit der nächsten UI-Überarbeitung behoben werden.
- Kotlin-Formatierung und detekt mit zur Projektversion passender Konfiguration.
- Instrumentierte Tests, Emulator und Audio-/Bypass-Prüfungen auf echten Geräten.

Ein grüner Lint-Lauf ist eine statische Prüfung und kein Nachweis für korrektes
Audioverhalten auf einem bestimmten Gerät.
