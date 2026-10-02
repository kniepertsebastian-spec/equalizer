# ADR 0003: ktlint eingerichtet, detekt weiterhin zurückgestellt

Status: Akzeptiert
Datum: 19. September 2026
Kontext: M1 – Projektfundament, Rest der Checkbox "Formatierung und detekt in
CI einrichten" (Nachfolger von ADR 0001/0002)

## Entscheidung

| Tool | Ergebnis |
|---|---|
| ktlint (`org.jlleitschuh.gradle.ktlint`, Plugin-Version `14.2.0`) | Eingerichtet, `ktlintCheck` läuft in CI. |
| detekt | Weiterhin zurückgestellt. |

## Begründung: ktlint

ktlint parst Kotlin-Quelltext mit einem eigenen, gebündelten Kotlin-Compiler
und ist damit – anders als detekt – von der im Projekt verwendeten
Kotlin-Gradle-Plugin-Version (`2.3.20`, siehe ADR 0002) unabhängig. Die
aktuelle Version des Gradle-Plugins (`org.jlleitschuh.gradle.ktlint`,
`14.2.0`) unterstützt aktuelle Kotlin- und Gradle-Versionen ohne bekannte
Einschränkung.

Der `ktlint_official`-Codestil (bereits über `kotlin.code.style=official` in
`gradle.properties` für die IDE gesetzt) wurde für alle bestehenden Dateien
per `ktlint --format` automatisch angewendet – keine manuellen Änderungen an
der Logik. Eine Ausnahme war nötig: ktlints `standard:function-naming`-Regel
lehnt PascalCase-Funktionsnamen ab, was der in Jetpack Compose üblichen
Namenskonvention für `@Composable`-Funktionen widerspricht. Behoben über
`.editorconfig`:

```ini
[*.{kt,kts}]
ktlint_function_naming_ignore_when_annotated_with = Composable
```

## Begründung: detekt bleibt zurückgestellt

Wie in ADR 0001 für Kotlin `2.4.20` beschrieben, unterstützt detekts stabile
Version Kotlin `2.3.20` (die für Hilt/KSP gewählte Version, siehe ADR 0002)
weiterhin nicht. Laut der Diskussion
[detekt/detekt#9170](https://github.com/detekt/detekt/discussions/9170)
("Detekt compatiblity with Newer Kotlin 2.3.20") funktioniert die
Konfiguration mit Kotlin `2.3.20` in der stabilen Zeile nicht; nur die
`2.0.0-alpha.6`-Vorabversion (Stand 4. August 2026) unterstützt sie. Der
[Kompatibilitäts-Hinweis in Issue #7384](https://github.com/detekt/detekt/issues/7384)
bestätigt dasselbe grundsätzliche Muster bereits für Kotlin `2.0.0`: das
Gradle-Plugin hängt eng an einer bestimmten Kotlin-Version.

Eine Alpha-Version für ein Werkzeug einzusetzen, das in CI jeden Build
blockieren kann (kein `continue-on-error`, siehe `docs/QUALITY.md`), ist ein
unverhältnismäßiges Risiko gegenüber dem Nutzen an diesem Projektpunkt. detekt
bleibt daher zurückgestellt, bis eine stabile Version Kotlin `2.3.20` (oder
die zu diesem Zeitpunkt aktuelle, für Hilt/KSP nötige Kotlin-Version)
unterstützt. Diese Prüfung ist in `docs/DECISIONS.md` als offener Punkt
vermerkt und sollte bei der nächsten Kotlin-Versionsänderung erneut
durchgeführt werden.

## Nicht Teil dieses Schritts

Die in `docs/QUALITY.md` bereits offen geführten Punkte (Dependency-Updates,
Icon-Warnungen, instrumentierte Tests, Emulator-/Gerätetests) bleiben
unverändert offen.
