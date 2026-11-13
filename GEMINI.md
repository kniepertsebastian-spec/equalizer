# HardBass EQ — Gemini Instructions

## Projekt
HardBass EQ: Equalizer für Android mit begleitendem Player.
- Android: Kotlin, Jetpack Compose, Material 3, Coroutines/StateFlow, Hilt, Room.
- Gradle-Module: `:app`, `:core`, `:player` (das Desktop-Modul wurde am 5. November 2026 entfernt).
- DSP, Preset-Kurven und Headroom-Berechnung liegen soweit möglich in `:core`.

## Allgemeiner Workflow
- Antworte auf Deutsch und halte Erklärungen knapp.
- Lies zuerst die relevanten Dateien, Tests und vorhandenen Projektanweisungen.
- Folge der aktuellen Architektur und bestehenden Konventionen.
- Prüfe vor Änderungen die maßgebliche Roadmap und Dokumentation; lies nicht die gesamte Historie.
- Maßgeblich ist `docs/roadmaps_in_progress/00-INDEX.md` mit den sechs offenen Roadmap-Paketen. Alles unter `docs/roadmaps_finished/` (u. a. `roadmap-2026.md`, `roadmap.md` als Session-Log) ist Archiv.
- `docs/ARCHITECTURE.md`, `docs/QUALITY.md`, `docs/TEST_MATRIX.md`, `docs/DEPENDENCIES.md` und ADRs bei Bedarf konsultieren.
- Dokumentationskopien nur gemeinsam aktualisieren, wenn beide laut Dokumentationsregeln gepflegt werden müssen.
- Implementiere die kleinste vollständige Lösung. Keine sachfremden Refactorings.
- Behaupte niemals, einen Build, Test oder Gerätetest erfolgreich ausgeführt zu haben, wenn das nicht stimmt.

## Code-Reviews
- Melde ausschließlich konkrete, nachvollziehbare und behebbare Fehler.
- Priorisiere Audio-Korrektheit, Clipping, Lifecycle-Fehler, Datenverlust, Abstürze und irreführende UI-Zustände.
- Nenne Findings zuerst, nach Schweregrad sortiert.
- Für jeden Fund: Schweregrad, Datei/Zeile, Auslöser, konkrete Auswirkung und empfohlene Korrektur.
- Prüfe den tatsächlichen Ausführungspfad und bereits vorhandene Schutzmaßnahmen.
- Keine spekulativen Findings, Stilpräferenzen, generischen Best Practices, doppelten Befunde oder Findings-Quoten.
- Ändere während eines Reviews keinen Code, sofern nicht ausdrücklich beauftragt.
- Konzentriere dich auf geänderten Code und direkt betroffene Abhängigkeiten. Unveränderten Code nur einbeziehen, wenn es für einen belastbaren Befund nötig ist oder ein schweres Sicherheits-/Datenproblem besteht.

## Audio-Engine und Android-Lifecycle
- Behaupte nie, dass systemweite Audioverarbeitung auf jedem Gerät oder in jeder Player-App funktioniert.
- Unterscheide zwischen gefundenem Effekt, angebundener Session, erfolgreich angewandten Einstellungen und nachgewiesener Audio-Wirkung.
- Die UI darf `Active` nur anzeigen, wenn der Zustand der Engine dies bestätigt und das Anwenden der Einstellungen erfolgreich war.
- Behandle Session-Wechsel, Kontrollverlust, `DEAD_OBJECT`, nicht unterstützte Parameter und fehlgeschlagene Attach-Vorgänge explizit.
- Vermeide unbegrenzte Retry-Schleifen, Akku-Spam und veraltete Sessions.
- AudioEffect-Objekte müssen einen eindeutigen Owner haben und zuverlässig freigegeben werden.
- Serialisiere konkurrierende Änderungen und Lifecycle-Operationen gemäß dem bestehenden Audio-Layer.
- Berücksichtige Pause/Resume, Trackwechsel, Prozessneustart, Bluetooth-Wechsel und Änderungen des Audioausgangs.
- Session-ID `0`, veraltete APIs und herstellerspezifisches Verhalten niemals als garantierte Kompatibilität behandeln.
- `MediaProjection`/Playback Capture nicht als Umweg zur Bearbeitung und Wiederausgabe fremder App-Audiosignale verwenden.
- Keine AudioEffect-Aufrufe direkt aus Composables; nutze die bestehende ViewModel-/Repository-/Engine-Architektur.

## DSP, EQ und Headroom
- DSP-Mathematik in `:core` halten, wenn sie unabhängig von Android und UI testbar sein soll.
- Filterformeln, Frequenzantworten, Gain-Skalierung, Einheiten und numerische Grenzen explizit berücksichtigen.
- Bei kaskadierten Filtern die kombinierte Frequenzantwort bewerten; den höchsten Einzelband-Gain nicht ungeprüft als tatsächlichen Peak verwenden.
- Positive Verstärkung muss mit korrektem Headroom oder einer nachweislich verfügbaren Schutzstufe gekoppelt sein.
- Niemals einen Limiter oder Input-Gain als aktiv anzeigen, wenn die Engine ihn nicht unterstützt oder die Anwendung fehlgeschlagen ist.
- Bypass muss den vorgesehenen unveränderten Zustand wiederherstellen; keine versteckten Gain- oder DSP-Änderungen zurücklassen.
- Nicht unterstützte Funktionen klar deaktivieren oder als nicht verfügbar kennzeichnen, statt sie stillschweigend zu simulieren.
- Preset-Makros müssen innerhalb der realen Engine-Grenzen bleiben und Änderungen nachvollziehbar auf die zugrunde liegenden Parameter abbilden.
- Änderungen an Preset-Kurven, Filter-Q, Preamp oder DSP-Reihenfolge durch gezielte mathematische Tests absichern.

## Presets, Profile und Persistenz
- Gerätekorrektur und Genre-/Voicing-Presets getrennt halten.
- Bestehende Regeln für Geräteprofile, Routenwechsel, benutzerdefinierte Presets und Wiederherstellung nach Neustarts bewahren.
- Datenbankänderungen mit kompatiblen Room-Migrationen umsetzen; vorhandene Nutzerdaten erhalten.
- JSON-Importe validieren: Schema-Version, Dateigröße, endliche Zahlen, Frequenzen, Gains und Wertebereiche.
- Ungültige Importe sicher ablehnen; importierte Einstellungen nicht ungefragt aktivieren.
- Keine Gerätekennungen, Tracknamen oder Nutzungsdaten in Diagnoseberichten oder Release-Logs ausgeben.
- Keine Analyse des Hörverhaltens, Telemetrie oder Tracking-SDKs hinzufügen, sofern nicht ausdrücklich entschieden.

## Player und gemeinsamer Code
- Keine Android-spezifischen Annahmen in `:core` einführen.
- Änderungen an `:player` auf Wiedergabe, Audio-Session, Fehlerzustände und die Integration mit `:app` prüfen.
- Bei gemeinsamen Typen oder DSP-Algorithmen Auswirkungen auf alle betroffenen Module kontrollieren.
- Keine Plattformunterstützung oder Audiofunktion versprechen, die nicht durch Code oder Tests belegt ist.

## UI und Bedienbarkeit
- Bestehende Compose-, Material-3-, Theme- und Spacing-Konventionen beibehalten.
- Lade-, Fehler-, Warte-, Offline- bzw. nicht unterstützte Zustände verständlich darstellen.
- Regler und Makros müssen mit der tatsächlich angewandten Audio-Konfiguration übereinstimmen.
- Barrierefreiheit, Touch-Bedienung und verständliche deutsche/englische UI-Texte berücksichtigen.
- Keine funktionslosen Regler oder irreführenden Statusanzeigen ergänzen.

## Tests und Verifikation
- Zuerst relevante Modulskripte, Tests und `docs/QUALITY.md` prüfen.
- Für Änderungen im gemeinsamen DSP-Core die Core-Tests und vorhandene `core-verify`-Möglichkeit berücksichtigen.
- Vorhandene Root-Prüfungen:
  - `./gradlew assembleDebug`
  - `./gradlew testDebugUnitTest`
  - `./gradlew lintDebug lintRelease`
  - `./gradlew ktlintCheck`
- Unter Windows `gradlew.bat` verwenden.
- Bei Bedarf gezielte Player-Tests ergänzen.
- Für mathematische Änderungen Randwerte, Clamping, Bypass und kombinierte Filterantworten testen.
- Für Engine-Änderungen Fehlerpfade, Attach/Detach, Retry und Statusübergänge testen.
- Ein erfolgreicher Unit-Test oder Lint-Lauf beweist keine hörbare Funktion auf realen Geräten.
- Hardware-, Emulator- und Player-Kompatibilität nur als geprüft ausgeben, wenn die jeweiligen Tests tatsächlich durchgeführt wurden.
- Wenn Gradle-Abhängigkeiten, Android SDK, Netzwerk oder Testhardware fehlen, Einschränkung klar nennen; verfügbare gezielte Prüfungen trotzdem ausführen.

## Token-Effizienz
- Suche vor dem Öffnen großer Dateien nach relevanten Klassen, Funktionen und Tests.
- Lies nur die Abschnitte der Roadmap und Dokumentation, die für die Aufgabe nötig sind.
- Verwende die aktuelle Roadmap statt das gesamte Session-Log zu laden.
- Vermeide wiederholte Erklärungen und umfangreiche ungefragte Bestandsaufnahmen.
- Keine unnötigen Abstraktionen, Dependencies oder Dokumentationsduplikate.
- Bei Reviews reproduzierbare Fehler statt hypothetischer Randfälle priorisieren.

## Ausgabe
### Reviews
- Findings zuerst, schwerwiegendste zuerst.
- Datei-/Zeilenangabe, Auslöser, Auswirkung und Fix nennen.
- Wenn keine belastbaren Findings vorliegen, klar sagen.

### Implementierungen
- Änderungen und Grund knapp zusammenfassen.
- Tatsächlich ausgeführte Prüfungen auflisten.
- Verbleibende Risiken und nicht verifizierte Hardware-/Audio-Annahmen nennen.
- Keine ungeprüften Erfolgsbehauptungen.

