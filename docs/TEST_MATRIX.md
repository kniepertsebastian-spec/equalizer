# Test-Matrix

Wird am Ende jeder Arbeitssession aktualisiert (siehe `roadmap.md` §15,
Punkt 10 und §10 M0-Abnahmekriterium "Geräte-Matrix").

## Getestete Geräte/Android-Versionen

| Gerät | Android-Version | Getestet am | Ergebnis |
|---|---|---|---|
| — | — | — | Noch kein Emulator/Gerät verfügbar; diese Sandbox hat keinen Android-SDK-Zugriff (siehe `docs/FEASIBILITY.md`). |

## Automatisiert (CI, kein physisches Gerät)

| Prüfung | Status |
|---|---|
| `./gradlew assembleDebug` | ✅ Grün, PR #1, Commit `4e61b82` (Lauf https://github.com/kniepertsebastian-spec/equalizer/actions/runs/35390078428). Erster Versuch auf Commit `eafc8a0` schlug fehl (AGP-9-Kotlin-Plugin-Konflikt, siehe `docs/DEPENDENCIES.md`). |
| `./gradlew testDebugUnitTest` (u.a. `AudioCapabilitiesMapperTest`) | ✅ Grün, gleicher CI-Lauf wie oben. |

CI läuft auf `ubuntu-latest` (GitHub-Actions-Standard-Runner mit vorinstalliertem
Android-SDK), nicht auf einem echten oder emulierten Android-Gerät. Damit ist
nur der Build- und Unit-Test-Pfad abgedeckt, keine Laufzeit-Verifikation von
`AudioEffect`-Verhalten auf einem Gerät.

## Nächste Schritte

1. Sobald ein Emulator- oder Geräte-Zugriff verfügbar ist: Session-Attach-Spike
   (offene M0-Punkte aus `docs/FEASIBILITY.md`) auf mindestens einem Emulator
   plus einem physischen Gerät durchführen und hier protokollieren.
