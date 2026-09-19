# Qualitätsprüfungen

## Lokal und in CI

Voraussetzungen: JDK 21 und Android SDK wie in `DEPENDENCIES.md` beschrieben.
Im Repository-Verzeichnis ausführen (Windows: `gradlew.bat` statt `./gradlew`):

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug lintRelease
./gradlew ktlintCheck
./gradlew ktlintFormat  # behebt automatisch korrigierbare Verstöße
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

## Formatierung (ktlint)

`org.jlleitschuh.gradle.ktlint` (Plugin-Version `14.2.0`) prüft den
`ktlint_official`-Codestil (`kotlin.code.style=official` in
`gradle.properties`) über `ktlintCheck`, ebenfalls ohne Baseline oder
Unterdrückung. `.editorconfig` erlaubt PascalCase für `@Composable`-Funktionen
(sonst von ktlints Namensregel abgelehnt). Details und die Begründung, warum
detekt (noch) nicht dazukommt, stehen in `docs/adr/0003-ktlint-detekt-deferred.md`.

## Weiterhin offen

- Erster Lauf in PR #7: je Variante 0 Fehler und 16 Warnungen. Davon betreffen
  13 mögliche Dependency-Updates, eine `targetSdk = 36` und zwei das Icon
  (`ObsoleteSdkInt`, `MonochromeLauncherIcon`). Keine Warnung wurde unterdrückt.
  Dependency-/Target-SDK-Updates werden getrennt auf Kompatibilität geprüft;
  die Icon-Hinweise können mit der nächsten UI-Überarbeitung behoben werden.
- detekt: zurückgestellt, da dessen stabile Version Kotlin 2.3.20 nicht
  unterstützt (siehe ADR 0003); bei der nächsten Kotlin-Versionsänderung
  erneut prüfen.
- Instrumentierte Tests, Emulator und Audio-/Bypass-Prüfungen auf echten Geräten.

Ein grüner Lint-Lauf ist eine statische Prüfung und kein Nachweis für korrektes
Audioverhalten auf einem bestimmten Gerät.
