# Test-Matrix

Wird am Ende jeder Arbeitssession aktualisiert (siehe `roadmap.md` §15,
Punkt 10 und §10 M0-Abnahmekriterium "Geräte-Matrix").

## Getestete Geräte/Android-Versionen

| Gerät | Android-Version | Getestet am | Ergebnis |
|---|---|---|---|
| Physisches Gerät (Modell/Android-Version noch nicht erfasst; ein Effekt-Implementor lautet „Google Pixel", evtl. ein Pixel-Gerät – bitte bestätigen) | Unbekannt | 18. September 2026 | ✅ App startet, Debug-Effektliste (`AudioEffect.queryEffects()`) funktioniert. **Bestätigt vorhanden:** `EqualizerBundle` (NXP, `0bed4300-ddd6-11db-8f34-0002a5d5c51b`), `DynamicsProcessing` (AOSP, `7261676f-6d75-7369-6364-28e2fd3ac39e`), `Dynamic Bass Boost` (NXP, `0634f220-ddd4-11db-a0fc-0002a5d5c51b`), `Loudness Enhancer` (AOSP, `fe3199be-aed0-413f-87bb-11260eb63cf1`) sowie Virtualizer, Visualizer, Noise Suppression, Acoustic Echo Canceler, Haptic Generator, Decibel Spatializer Library (Google Pixel), Multichannel Downmix, diverse Reverb-Effekte. Damit hat dieses Gerät die beiden für den EQ zentralen Effekte (`Equalizer`, `DynamicsProcessing`) – gute Voraussetzung für den Session-Attach-Spike. Die vier o. g. UUIDs stimmen exakt mit den in `AudioCapabilitiesMapperTest` verwendeten Konstanten überein – zusätzliche Bestätigung, dass diese Werte korrekt sind. |

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
