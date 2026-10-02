# ADR 0001: KSP-basiertes Tooling (Hilt, Room) und Detekt vorerst zurückgestellt

Status: Akzeptiert
Datum: 18. September 2026
Kontext: M1 – Projektfundament

## Kontext

Roadmap M1 verlangt "Hilt, Coroutines, DataStore, Room und Serialization
einrichten" sowie CI mit "Android Lint, Formatierung und detekt". Beim
Versuch, das umzusetzen, kamen zwei unabhängige Versionskonflikte zutage:

1. **KSP ist nicht mit AGP 9s eingebautem Kotlin kompatibel.** In M0 wurde
   `org.jetbrains.kotlin.android` entfernt, weil AGP 9.0+ Kotlin fest
   eingebaut mitbringt und das Plugin den Build sonst fatal abbricht (siehe
   `docs/DEPENDENCIES.md`). Hilt und Room brauchen aber KSP
   (`com.google.devtools.ksp`) für Annotation Processing, und KSP
   unterstützt aktuell (Stand 18. September 2026, siehe
   https://github.com/google/ksp/issues/2615 und die KSP-eigene
   `gradle.properties` im offiziellen Repo) AGP 9s eingebautes Kotlin nicht.
2. **KSP hinkt der Kotlin-Version hinterher.** Die neueste KSP-Version
   (`2.3.12`) zielt laut ihrer eigenen Build-Konfiguration auf Kotlin
   `2.3.20` – nicht auf das im Projekt verwendete Kotlin `2.4.20`. Es gibt
   noch keine KSP-Version für die 2.4-Linie.
3. **Detekt 2.0 (mit Kotlin-2.4-Unterstützung) ist nur als Alpha
   verfügbar** (`2.0.0-alpha.6`, Stand 18. September 2026). Für ein Projekt,
   das laut Roadmap §6 "aktuelle stabile" Versionen verwenden soll, ist ein
   Alpha-Tool als CI-Gate ungeeignet.

## Entscheidung

Hilt, Room, DataStore-Nutzung (konkrete Schemas), Kotlin-Serialization-Typen
und Detekt werden **nicht** in diesem M1-Durchlauf eingerichtet. Stattdessen:

- **DataStore, Serialization:** Nur die Gradle-Abhängigkeiten werden
  vorbereitet, sobald sie konkret gebraucht werden (M4 – Profile und
  Persistenz laut Roadmap: "implementieren" statt "einrichten" – das ist
  ohnehin der richtige Zeitpunkt für konkrete Schemas).
- **Hilt/Room/KSP:** Zurückgestellt auf einen eigenen, isoliert getesteten
  Folge-Schritt. Wenn er kommt, ist der bekannte, dokumentierte Workaround:
  `android.builtInKotlin=false` in `gradle.properties` setzen, das
  `org.jetbrains.kotlin.android`-Plugin (Version passend zu einer
  existierenden KSP-Version, aktuell `2.3.20`) wieder anwenden, und die
  Compose-Compiler-Plugin-Version entsprechend mitziehen. Das bedeutet ein
  gezieltes Downgrade von Kotlin `2.4.20` auf `2.3.20` **nur wenn** dieser
  Schritt umgesetzt wird – nicht vorher.
- **Detekt/ktlint:** Zurückgestellt, bis entweder eine stabile Detekt-2.0
  veröffentlicht ist oder eine 1.23.x-Version mit nachgewiesener
  Kotlin-2.4-Verträglichkeit gefunden wird.
- Das aktuelle M1-Ziel "Keine Geschäftslogik lebt in Composables" wird
  stattdessen mit einem einfachen, manuell konstruierten `ViewModel`
  (`MainViewModel` + `MainViewModelFactory`) erreicht – ohne Hilt.

## Begründung

Am selben Tag hat ein unzureichend geprüfter AGP-9-Kotlin-Konflikt fünf
CI-Läufe in Folge unbemerkt rot laufen lassen (siehe `docs/DEPENDENCIES.md`,
`docs/FEASIBILITY.md`). Mehrere gleichzeitige, bereits als riskant
identifizierte Versionskonflikte (KSP+AGP9, KSP+Kotlin-2.4, Detekt-Alpha) in
einem Schritt einzuführen, ohne lokale Build-Verifikation (diese Sandbox hat
keinen Android-SDK-Zugriff), wiederholt genau das Risiko. Stattdessen wird
der sichere Teil von M1 nun umgesetzt, und das riskante Tooling bekommt
einen eigenen, kleinen, leicht zurückrollbaren Schritt.

## Konsequenzen

- M1 wird in diesem Durchlauf nicht vollständig abgeschlossen; die
  betroffenen Roadmap-Checkboxen bleiben bewusst offen (siehe `roadmap.md`).
- Sobald Hilt/Room/KSP nachgezogen werden, ist eine erneute ADR fällig, die
  den tatsächlich funktionierenden Versions-Satz dokumentiert (dieses ADR
  beschreibt nur den geplanten Ansatz, nicht ein bereits verifiziertes
  Ergebnis).
