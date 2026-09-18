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
| `./gradlew assembleDebug` | Ausstehend – erster CI-Lauf nach Push noch nicht bestätigt. |
| `./gradlew testDebugUnitTest` (u.a. `AudioCapabilitiesMapperTest`) | Ausstehend – erster CI-Lauf nach Push noch nicht bestätigt. |

## Nächste Schritte

1. CI-Ergebnis des ersten Pushes in dieser Tabelle nachtragen.
2. Sobald ein Emulator- oder Geräte-Zugriff verfügbar ist: Session-Attach-Spike
   (offene M0-Punkte aus `docs/FEASIBILITY.md`) auf mindestens einem Emulator
   plus einem physischen Gerät durchführen und hier protokollieren.
