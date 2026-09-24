# Architektur

Stand: M1 (Projektfundament). Dieses Dokument beschreibt den aktuellen
Stand, nicht die vollständige Zielarchitektur. Ab 23. September 2026 ist
`roadmap-2026.md` §5 ("Empfohlene technische Architektur") die aktuelle
Referenz für die Zielarchitektur (Domänenpipeline + Komponententabelle:
`AudioSessionCoordinator`, `CurveComposer`, `CapabilityAdapter`,
`HeadroomCalculator`, `DiagnosticsRecorder` u. a.) – wird laufend
nachgeführt, wenn neue Meilensteine landen. `DiagnosticsRecorder` ist seit
dieser Session bereits als erste Komponente umgesetzt (siehe unten).

## Modulstruktur

Aktuell ein einzelnes App-Modul (`app/`), wie in Roadmap §7 für frühe
Meilensteine ausdrücklich erlaubt. Innerhalb des Moduls ist bereits nach
Roadmap-Paketen getrennt:

```text
com.hardbasseq.eq/
  MainActivity.kt
  audio/                 # Audio-Schnittstellen + Android-Implementierung
    spike/                # M0-Spikes, siehe docs/FEASIBILITY.md – kein Produktionscode
  ui/
    main/                 # MainViewModel (State + Business-Logik)
    navigation/           # AppNavHost (Compose-Navigationsgraph)
    theme/                # Compose-Theme
```

Die Trennung Audio-API/Android-Backend (Roadmap §7: "Vor Meilenstein 2
sollen die Audio-Schnittstellen jedoch klar vom Android-Backend getrennt
sein") existiert bereits für `AudioEffectRepository` (Interface) vs.
`AndroidAudioEffectRepository` (Implementierung). Die vollständigen
`AudioEngine`/`AudioSessionRepository`/`AudioRouteRepository`-Schnittstellen
aus Roadmap §7 kommen mit M2.

## Datenfluss / Zustandshaltung

`MainViewModel` hält den UI-Zustand des Startbildschirms (`StateFlow`) und
kapselt den einzigen Repository-Aufruf, der bisher existiert
(`AudioEffectRepository.queryAvailableEffects()`). Die Composable
(`MainScreen`) liest nur Zustand und leitet Events weiter – keine
Geschäftslogik direkt in der Composable (M1-Abnahmekriterium).

Der Session-Attach-Spike-Bereich (`SessionAttachSpikeController` +
`SessionAttachSpikeSection`) ist bewusst **nicht** in `MainViewModel`
integriert: Er ist explizit als M0-Wegwerf-Diagnosecode markiert (siehe
`docs/FEASIBILITY.md`) und wird nicht Teil der Produktarchitektur.

`DiagnosticsRecorder` (`diagnostics/DiagnosticsRecorder.kt`) ist dagegen
Produktcode: ein `@Singleton`, den `AudioSessionForegroundService` mit
Session-/Route-/Engine-State-Ereignissen füttert und den `MainViewModel`
für den Diagnosebericht (`DiagnosticsReportFormatter`) ausliest. Bewusst
nur ein beschränkter In-Memory-Ringpuffer (50 Einträge), keine Persistenz –
siehe Fehler-/Logstrategie unten zu personenbezogenen Daten.

## Dependency Injection

Hilt (`@HiltAndroidApp` auf `HardBassEqApplication`, `@AndroidEntryPoint`
auf `MainActivity`, `@HiltViewModel` auf `MainViewModel`, injiziert über
`hiltViewModel()` in `AppNavHost`). `AudioModule` bindet
`AudioEffectRepository` auf `AndroidAudioEffectRepository`;
`DispatcherModule` stellt einen qualifizierten `@DefaultDispatcher`
bereit. Das dafür nötige KSP-Tooling kollidiert mit AGP 9s eingebautem
Kotlin – siehe `docs/adr/0001-defer-ksp-based-tooling.md` (Problem) und
`docs/adr/0002-hilt-ksp-setup.md` (umgesetzter Workaround: Kotlin auf
2.3.20, `android.builtInKotlin=false`, `org.jetbrains.kotlin.android`
wieder angewendet).

## Navigation

`AppNavHost` mit aktuell einer Route (`home`). Struktur ist so angelegt,
dass die in Roadmap §7 geplanten Feature-Bereiche (Onboarding, Presets,
Profile, Diagnose) eigene Routen bekommen können, ohne den Graphen
umzubauen.

## Threading-Regeln (verbindlich, Roadmap §7)

- Keine `AudioEffect`-Aufrufe direkt aus Composables.
- Jedes Effektobjekt hat genau einen Owner im Audio-Layer.
- Erstellen/Anwenden/Freigeben seriell über einen `Mutex`
  (`SessionAttachSpikeController` implementiert das bereits als Vorlage
  für die spätere `AudioEngine`).
- Effekte werden immer in `finally` freigegeben.

## Fehler- und Logstrategie

- Keine dedizierte Logging-Bibliothek bisher eingerichtet, da noch keine
  produktive Logik existiert, die strukturiertes Logging bräuchte.
- Verbindliche Regel für jeden zukünftigen Logging-Code (Release-Build):
  **keine** Medien-/Tracknamen, keine Bluetooth-Gerätenamen oder
  MAC-Adressen, keine sonstigen personenbezogenen oder eindeutig
  identifizierenden Daten in Log-Zeilen (Roadmap §12).
- Fehlerzustände (verlorene Effekt-Kontrolle, tote Session, nicht
  unterstützte Parameter) werden als Zustände modelliert, nicht als
  Exceptions nach außen geworfen – siehe `DynamicsProcessingSpike`/
  `EqualizerSpike`/`SessionZeroExperiment` als Vorlage (jede Methode fängt
  Fehler ab und gibt ein Ergebnis-Objekt zurück statt zu crashen).

## Paketname / Arbeitstitel

Zentral in `gradle.properties` (`hardbasseq.applicationId`,
`hardbasseq.namespace`), referenziert aus `app/build.gradle.kts`. Der
Anzeigename (`app_name`) bleibt in `strings.xml`, da das der
Android-übliche Ort dafür ist.

## CI / Qualitätssicherung

Aktuell: `assembleDebug`, `testDebugUnitTest`, optionaler Drive-Upload
(siehe `docs/DRIVE_UPLOAD.md`). Android Lint, ktlint/Spotless und detekt
sind für M1 vorgesehen, aber bewusst noch nicht eingerichtet – siehe
`docs/adr/0001-defer-ksp-based-tooling.md`.
