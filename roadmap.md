# HardBass EQ – Technical Review & Engineering Roadmap

Stand: Oktober 2026 | Fokus: Stabilität (Android 14/15 & Samsung OneUI), Audio-Lifecycle, Standby-Effizienz, 34-Band-DSP & Klangqualität.

---

## 1. Critical / Blocker (Nachgewiesene Absturzursachen & schwerwiegende Funktionsfehler)

### [CRIT-01] Unbehandelte `ForegroundServiceStartNotAllowedException` & `START_STICKY` in `AudioSessionForegroundService` (Android 14/15 Crash & Samsung "Tiefes Standby"-Warnung)
- **Priorität:** Critical / Blocker
- **Kategorie:** Stabilität / Android 14 & 15 Lifecycle
- **Status:** Erledigt (in Review-Patch umgesetzt)
- **Begründung / Code-Evidenz (Bestätigter Fehler):**
  - `app/src/main/java/com/hardbasseq/eq/service/AudioSessionForegroundService.kt` (Zeile 46 & 75–81), `app/build.gradle.kts` (`targetSdk = 36`), `app/src/AndroidManifest.xml` (`foregroundServiceType="mediaPlayback"`).
  - `startForeground(NOTIFICATION_ID, buildNotification())` wurde ohne `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` und ohne `try/catch` aufgerufen. Zudem gab `onStartCommand` `START_STICKY` zurück.
  - Wenn Android 14/15 (z. B. Samsung Galaxy S21 Ultra SM-G998B, API 35, nach Android-Auto-Trennung oder Prozess-Cleanup) den Service wegen `START_STICKY` im Hintergrund neu startet, während die App weder sichtbar ist noch aktiv Audio abspielt, wirft das System `ForegroundServiceStartNotAllowedException`.
  - Da `InMemoryDiagnosticsRecorder` nur im RAM lebt, ging der Crash-Grund beim nächsten App-Start verloren (`Last Error: none`), während Samsungs Device Care (`Freecess`) die wiederholten Prozess-Crashes zählte und die Warnung *„HardBass EQ stürzt häufig ab. Versetze die App in ein tiefes Standby“* anzeigte.
- **Betroffene Dateien:**
  - `app/src/main/java/com/hardbasseq/eq/service/AudioSessionForegroundService.kt`
- **Vorgeschlagene / Umgesetzte Lösung:**
  - Umstellung auf `ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), fgsType)` mit explizitem `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`.
  - Absicherung durch `try/catch (Exception)` mit Diagnose-Protokollierung und sauberem `stopSelf()` statt Prozess-Absturz.
  - Änderung des Rückgabewerts in `onStartCommand` von `START_STICKY` auf `START_NOT_STICKY`.
- **Akzeptanzkriterien:**
  - Ein Hintergrund-Startversuch des Services ohne Berechtigung darf niemals zum Prozess-Crash führen.
  - Ablehnungen durch das OS werden im `DiagnosticsRecorder` protokolliert.
- **Abhängigkeiten / Risiken:** Keine; erhält alle bestehenden Funktionen im regulären Betrieb.

---

### [CRIT-02] Race Condition zwischen `markDetached()` (Main-Thread) und laufenden Coroutines in `AndroidAudioEngine`
- **Priorität:** Critical / Blocker
- **Kategorie:** Thread-Sicherheit / Native AudioEffect Lifecycle
- **Status:** Erledigt (in Review-Patch umgesetzt)
- **Begründung / Code-Evidenz (Bestätigter Fehler):**
  - `app/src/main/java/com/hardbasseq/eq/audio/AndroidAudioEngine.kt` (Zeile 81–88 & 237–253).
  - `markDetached()` wurde aus `AudioSessionForegroundService.onDestroy()` auf dem Main-Thread **ohne `mutex`** aufgerufen und gab `equalizer` und `dynamicsProcessing` direkt frei, während `apply()` oder `attachLocked()` auf `Dispatchers.Default` gleichzeitig auf dieselben nativen Instanzen zugreifen konnten.
- **Betroffene Dateien:**
  - `app/src/main/java/com/hardbasseq/eq/audio/AndroidAudioEngine.kt`
- **Vorgeschlagene / Umgesetzte Lösung:**
  - `@Volatile` auf den Effekt-Referenzen, lokales Nullsetzen vor `.release()` in `releaseEffectsLocked()` und `mutex.tryLock()` mit Fallback auf `engineScope.launch { mutex.withLock { ... } }` in `markDetached()`.
- **Akzeptanzkriterien:**
  - Kein paralleler Zugriff auf `Equalizer` oder `DynamicsProcessing` während `onDestroy()` / `markDetached()`.

---

## 2. High (Audio-Lifecycle, Standby, DSP-Ausfälle und Klangqualitätsprobleme)

### [HIGH-01] Klangverlust & Pumpen (auch ohne 34-Band-Modus) durch überaggressives MBC-Auto-Makeup-Gain (+6 dB) direkt in den 10:1 Hard-Limiter
- **Priorität:** High
- **Kategorie:** Klangqualität / DSP-Gain-Staging
- **Status:** Erledigt (in Review-Patch umgesetzt)
- **Begründung / Code-Evidenz (Bestätigter Fehler):**
  - `app/src/main/java/com/hardbasseq/eq/audio/AndroidAudioEngine.kt` (Zeile 317–362).
  - Die Formel `((-mbcThresholdDb) * (1f - 1f / mbcRatio) * 0.85f).coerceIn(0f, 6f)` addierte bei `-8 dB` Threshold und `3.0:1` Ratio pauschal **+4,53 dB (bis zu +6 dB) `postGain`** auf alle 4 Bänder des Multiband-Kompressors (inkl. Mitten bei 1500 Hz und Höhen bei 20 kHz), während gleichzeitig das `inputGainDb` in `MainViewModel` gelockert wurde (im Diagnosebericht nur `-1.35 dB`).
  - Dadurch fuhr das Signal dauerhaft in den nachgeschalteten Brickwall-Limiter (`threshold = -0.3 dB`, `1 ms` Attack, `10:1` Ratio), was Transienten zerquetschte und Mitten/Höhen bei jedem Bassschlag wegdrückte.
- **Betroffene Dateien:**
  - `app/src/main/java/com/hardbasseq/eq/audio/AndroidAudioEngine.kt`
- **Vorgeschlagene / Umgesetzte Lösung:**
  - Reduzierung des MBC-Makeup-Faktors auf `0.45f` (Cap `3.0 dB`) sowie abgestuftes `postGain` für Mitten (`0.6x`) und Höhen (`0.5x`) plus Erhöhung der Limiter-Attack-Zeit von `1 ms` auf `2 ms` zum Erhalt des Kick-Transienten-Clicks.
- **Akzeptanzkriterien:**
  - Kein permanentes Anschlagen des Limiters bei moderater Lautstärke; klare Mitten und Höhen bleiben bei Kicks stabil.

---

### [HIGH-02] 34-Band-PreEq-Frequenzverteilung (`PreEqLayout.CUTOFFS_HZ`) hat Sub-Bin-Clustering im Tiefbass (5-Hz-Abstände) und zu grobe Auflösung in Mitten/Präsenz
- **Priorität:** High
- **Kategorie:** Klangqualität / 34-Band-Equalizer
- **Status:** Erledigt (in Review-Patch umgesetzt)
- **Begründung / Code-Evidenz (Bestätigter Fehler):**
  - `core/src/src/main/kotlin/com/hardbasseq/eq/dsp/PreEqMapper.kt` (Zeile 13–50).
  - Die bisherigen 34 Bänder enthielten im Bass Abstände von nur 4–5 Hz (`31, 36, 40, 45, 50, 55 Hz`), was schmaler ist als die physikalische FFT-Bin-Breite von Androids `DynamicsProcessing` STFT-Engine (~46–93 Hz bei 48 kHz). Das erzeugt bei starken Pegeldifferenzen (Subsonic-Cut vs. Kick-Boost) Phasenverschmierung/Pre-Ringing, während oberhalb von 1 kHz nur 8 grobe Bänder existierten.
- **Betroffene Dateien:**
  - `core/src/src/main/kotlin/com/hardbasseq/eq/dsp/PreEqMapper.kt`
- **Vorgeschlagene / Umgesetzte Lösung:**
  - Neuverteilung der 34 Bänder auf ein streng logarithmisches ~1/3-Oktav-Raster von `24 Hz` bis `20000 Hz` (`LOWEST_HZ = 18f`), das sowohl hohe Auflösung im Bass (14 Bänder <= 275 Hz) als auch feine Kontrolle über Mitten, Präsenz und Höhen bietet.
- **Akzeptanzkriterien:**
  - Alle 34 Bänder sind streng monoton steigend und wahren einen Mindestabstand von >= 18 % (ca. 1/4 bis 1/3 Oktave) ohne STFT-Sub-Bin-Überlappung.

---

### [HIGH-03] Unbeabsichtigte Überlagerung von bis zu 8 Zielkurven (insb. automatische `HeadphoneComfortCurve` -1.5 dB Präsenzsenke + `LoudnessCompensationCurve` bei Bluetooth)
- **Priorität:** High
- **Kategorie:** Klangqualität / Signalweg-Architektur
- **Status:** Offen (Plan erstellt, erfordert Prüfung in `MainViewModel.kt`)
- **Begründung / Code-Evidenz (Bestätigter Befund):**
  - `app/src/main/java/com/hardbasseq/eq/ui/equalizer/EqualizerScreen.kt` (Zeile 173–205), `app/src/main/java/com/hardbasseq/eq/audio/AudioRoute.kt` (Zeile 38–42), `core/src/src/main/kotlin/com/hardbasseq/eq/dsp/HeadphoneComfortCurve.kt` (Zeile 28–39).
  - Für jedes normale Bluetooth-Gerät (`AudioDeviceType.BLUETOOTH`) setzt `defaultHeadphoneAcoustics()` standardmäßig `effectiveHeadphoneAcoustics = true`.
  - Dadurch werden automatisch `HeadphoneComfortCurve` (+2.5 dB Bass, **-1.5 dB Senke bei 4.5 kHz**, -1.0 dB bei 8 kHz) UND `LoudnessCompensationCurve` (bis zu +9 dB Bass / +3.6 dB Höhen abhängig von der Systemlautstärke) auf das gewählte Preset und das AutoEQ-Profil aufaddiert.
  - Die pauschale -1.5 dB Senke bei 4.5 kHz nimmt dem Klang genau die Präsenz/Klarheit, während der zusätzliche Loudness-Bass-Boost das Headroom-Budget aufbraucht.
- **Betroffene Dateien:**
  - `core/src/src/main/kotlin/com/hardbasseq/eq/dsp/HeadphoneComfortCurve.kt`
  - `core/src/src/main/kotlin/com/hardbasseq/eq/dsp/HeadphonePower.kt`
  - `app/src/main/java/com/hardbasseq/eq/ui/main/MainViewModel.kt`
- **Vorgeschlagene Lösung:**
  - `HeadphonePower.DEFAULT_LOUDNESS_DB` standardmäßig auf `0f` (Opt-in statt Opt-out) setzen und die feste `-1.5 dB` Präsenzsenke bei `4500 Hz` in `HeadphoneComfortCurve` entfernen oder optional machen, damit der Klang bei aktiviertem Kopfhörer-Modus nicht ungefragt dumpfer wird.
- **Akzeptanzkriterien:**
  - Ein Wechsel auf „Flat“ mit verbundenen Bluetooth-Kopfhörern erzeugt ohne expliziten Nutzer-Eingriff einen neutralen 0-dB-Frequenzgang.

---

### [HIGH-04] Fehlende statische Capability-Erkennung vor dem ersten Session-Attach (`Has Equalizer: false` im Diagnosebericht)
- **Priorität:** High
- **Kategorie:** Audio-Engine / Diagnostik
- **Status:** Erledigt (in Review-Patch umgesetzt)
- **Begründung / Code-Evidenz (Bestätigter Fehler):**
  - `app/src/main/java/com/hardbasseq/eq/audio/AndroidAudioEngine.kt` (Zeile 32) & `AndroidAudioEffectRepository.kt`.
  - `AndroidAudioEngine` initialisierte `_capabilities` mit `AudioCapabilities()` (alle Flags `false`) und nutzte `AndroidAudioEffectRepository` überhaupt nicht. Solange keine Session aktiv war (`Listening`), meldete der Diagnosebericht fälschlicherweise `Has Equalizer: false` und `Has DynamicsProcessing: false`.
- **Betroffene Dateien:**
  - `app/src/main/java/com/hardbasseq/eq/audio/AndroidAudioEngine.kt`
  - `app/src/main/java/com/hardbasseq/eq/audio/AndroidAudioEffectRepository.kt`
- **Vorgeschlagene / Umgesetzte Lösung:**
  - Injektion von `AudioEffectRepository` in `AndroidAudioEngine` und Initialisierung von `_capabilities` über `AudioCapabilitiesMapper.map(...)`.
- **Akzeptanzkriterien:**
  - Auch im Zustand `Listening` / `Detached` zeigt der Diagnosebericht die wahren Plattform-Capabilities (`Has Equalizer: true`, `Has DynamicsProcessing: true`) an.

---

### [HIGH-05] Unnötiges Hintergrund-Monitoring von `SystemVolumeRepository` ohne aktive Audio-Session
- **Priorität:** High
- **Kategorie:** Standby / Ressourcenverbrauch
- **Status:** Erledigt (in Review-Patch umgesetzt)
- **Begründung / Code-Evidenz (Bestätigter Fehler):**
  - `app/src/main/java/com/hardbasseq/eq/service/AudioSessionForegroundService.kt` (Zeile 50) & `SystemVolumeRepository.kt`.
  - `volumeRepository.startMonitoring()` lief dauerhaft ab Service-Start, selbst wenn `activeSession == null` war. Jede Lautstärkeänderung im System feuerte den Broadcast-Receiver und aktualisierte `currentLevelDb`.
- **Betroffene Dateien:**
  - `app/src/main/java/com/hardbasseq/eq/service/AudioSessionForegroundService.kt`
- **Vorgeschlagene / Umgesetzte Lösung:**
  - `volumeRepository.startMonitoring()` wird erst bei `session != null` aktiviert und bei `session == null` sofort via `volumeRepository.stopMonitoring()` gestoppt.
- **Akzeptanzkriterien:**
  - Im Zustand `Listening` (ohne Audio-Session) ist kein `VOLUME_CHANGED_ACTION` Receiver aktiv.

---

## 3. Medium (34-Band-Verbesserungen, Diagnostik, Performance und UI)

### [MED-01] Sofortige Re-Konfiguration beim Umschalten des 34-Band-Schalters (`highResolutionEq`) zur Laufzeit
- **Priorität:** Medium
- **Kategorie:** 34-Band-Equalizer / Audio-Engine
- **Status:** Erledigt (in Review-Patch umgesetzt)
- **Begründung / Code-Evidenz (Bestätigter Fehler):**
  - `app/src/main/java/com/hardbasseq/eq/audio/AndroidAudioEngine.kt` (Zeile 134–142 & 255–261).
  - `createDynamicsConfig` wurde nur innerhalb von `attachLocked()` aufgerufen. Wenn der Nutzer `highResolutionEq` während einer bereits laufenden Session ein- oder ausschaltete, wurde `DynamicsProcessing` in `apply()` nicht mit der neuen `PreEq`-Konfiguration neu aufgebaut.
- **Betroffene Dateien:**
  - `app/src/main/java/com/hardbasseq/eq/audio/AndroidAudioEngine.kt`
- **Vorgeschlagene / Umgesetzte Lösung:**
  - `apply()` prüft `previousHighRes != settings.highResolutionEq` und führt bei aktivem Attach automatisch ein sauberes Re-Attach mit der neuen `DynamicsProcessing.Config` durch; außerdem prüft `usePreEq` in `applyInternal()` explizit `settings.highResolutionEq`.
- **Akzeptanzkriterien:**
  - Das Ein- und Ausschalten des 34-Band-Modus während laufender Musik schaltet deterministisch zwischen `DynamicsProcessing.PreEq` und dem klassischen `Equalizer` um.

---

### [MED-02] Diagnosebericht unterscheidet nicht zwischen konfigurierten Settings und tatsächlich auf der Hardware aktiven DSP-Stufen
- **Priorität:** Medium
- **Kategorie:** Diagnostik
- **Status:** Erledigt (in Review-Patch umgesetzt)
- **Begründung / Code-Evidenz (Bestätigter Fehler):**
  - `app/src/main/java/com/hardbasseq/eq/diagnostics/DiagnosticsReportFormatter.kt` (Zeile 53–63).
  - Unter `--- Active DSP Stages ---` wurden die Werte aus `ProcessingSettings` ausgegeben, auch wenn `engineState == Listening` und `hasDynamicsProcessing == false` war.
- **Betroffene Dateien:**
  - `app/src/main/java/com/hardbasseq/eq/diagnostics/DiagnosticsReportFormatter.kt`
- **Vorgeschlagene / Umgesetzte Lösung:**
  - Ausgabe von `Hardware Processing Applied: true/false` sowie `applied=true/false` hinter Input Gain, Limiter und MBC.
- **Akzeptanzkriterien:**
  - Im Zustand `WAITING` zeigt der Diagnosebericht eindeutig `Hardware Processing Applied: false` und `applied=false`.

---

### [MED-03] UI-Hauptkurve (`NeonCurve`) zeigt im 34-Band-Modus weiterhin nur die 5 Punkte des klassischen System-EQs
- **Priorität:** Medium
- **Kategorie:** UI / 34-Band-Equalizer
- **Status:** Offen
- **Begründung / Code-Evidenz (Bestätigter Befund):**
  - `app/src/main/java/com/hardbasseq/eq/ui/equalizer/EqualizerScreen.kt` (Zeile 216–220).
  - `val bandPoints = bands.sortedBy { it.centerFreqHz }.map { ... }` verwendet immer die 5 Hardware-Bänder aus `capabilities.bands`, auch wenn `preEqActive == true` ist und tatsächlich die 34 `preEqGainsDb`-Bänder angewendet werden.
- **Betroffene Dateien:**
  - `app/src/main/java/com/hardbasseq/eq/ui/equalizer/EqualizerScreen.kt`
- **Vorgeschlagene Lösung:**
  - Wenn `preEqActive == true` und `settings.preEqGainsDb.isNotEmpty()`, sollte `curvePoints` die 34 Band-Mittenfrequenzen mit `settings.preEqGainsDb` darstellen, damit die gezeichnete Kurve exakt den 34-Band-Frequenzgang widerspiegelt.
- **Akzeptanzkriterien:**
  - Beim Aktivieren von `highResolutionEq` zeigt `NeonCurve` die hochaufgelöste 34-Band-Kurve statt der 5-Band-Näherung.

---

### [MED-04] Persistenter Last-Crash-Eintrag über Prozess-Tode hinweg
- **Priorität:** Medium
- **Kategorie:** Diagnostik
- **Status:** Offen
- **Begründung / Code-Evidenz (Bestätigter Befund):**
  - `app/src/main/java/com/hardbasseq/eq/diagnostics/DiagnosticsRecorder.kt` (Zeile 27–38).
  - Da `InMemoryDiagnosticsRecorder` alle Ereignisse nur im RAM hält, sind nach einem echten Prozess-Crash oder ANR beim nächsten Öffnen der App alle vorherigen Events verloren (`Last Error: none`).
- **Betroffene Dateien:**
  - `app/src/main/java/com/hardbasseq/eq/diagnostics/DiagnosticsRecorder.kt`
- **Vorgeschlagene Lösung:**
  - Abfrage von `ActivityManager.getHistoricalProcessExitReasons()` (ab API 30) im `DiagnosticsReportFormatter`, um den letzten Exit-Grund des Systems (`REASON_CRASH`, `REASON_ANR`, `REASON_EXCESSIVE_RESOURCE_USAGE`, `REASON_USER_REQUESTED`) datenschutzarm im Diagnosebericht auszugeben.
- **Akzeptanzkriterien:**
  - Nach einem Hintergrund-Kill oder Crash nennt der Diagnosebericht den letzten `ApplicationExitInfo`-Grund samt Zeitstempel.

---

## 4. Low / Future (Sinnvolle zusätzliche Funktionen & langfristige Verbesserungen)

### [LOW-01] Stereo-Linked LookaheadLimiter in `PlayerDspPcm16`
- **Priorität:** Low / Future
- **Kategorie:** Klangqualität (In-App-Player)
- **Status:** Offen
- **Begründung / Code-Evidenz:**
  - `core/src/src/main/kotlin/com/hardbasseq/eq/dsp/PlayerDspPcm16.kt` (Zeile 12–13 & 46).
  - Aktuell läuft pro Stereokanal eine unabhängige `LookaheadLimiter`-Instanz, was bei starken einseitigen Transienten das Stereobild kurzzeitig verschieben kann.
- **Betroffene Dateien:**
  - `core/src/src/main/kotlin/com/hardbasseq/eq/dsp/PlayerDspPcm16.kt`
  - `core/src/src/main/kotlin/com/hardbasseq/eq/dsp/LookaheadLimiter.kt`
- **Vorgeschlagene Lösung:**
  - Gemeinsame Gain-Reduction-Berechnung über `max(abs(L), abs(R))` im Stereo-Betrieb.
- **Akzeptanzkriterien:**
  - Ein einseitiger Impuls auf L senkt L und R um exakt denselben Faktor ab (Phasen-/Panning-Treue).

---

## 5. Tests (Fehlende Tests & notwendige Geräte-/Android-Kompatibilitätsprüfungen)

### [TEST-01] Unit-Tests für 34-Band `PreEqMapper` (Neutralität, Monotonie, Sub-Bin-Abstand & Manual-Deltas)
- **Priorität:** High
- **Kategorie:** Tests
- **Status:** Erledigt (Testklasse bereitgestellt)
- **Begründung / Code-Evidenz:**
  - Absicherung der überarbeiteten 34-Band-Frequenzverteilung (`PreEqLayout.CUTOFFS_HZ`) und der `PreEqMapper.map()`-Berechnung bei flacher Kurve, Makro-Änderungen und manuellen Band-Deltas.
- **Betroffene Dateien:**
  - `core/src/src/test/kotlin/com/hardbasseq/eq/dsp/PreEqMapperTest.kt`
- **Akzeptanzkriterien:**
  - Flache Kurve + 0-Makros ergeben exakt `0.0f` auf allen 34 Bändern; alle Bandgrenzen sind streng monoton steigend.

---

### [TEST-02] Verifikation auf realem Samsung Galaxy S21 Ultra (SM-G998B, Android 15 / OneUI 7) mit Android Auto
- **Priorität:** High
- **Kategorie:** Hardware-/Geräteverifikation
- **Status:** Offen (Benötigt physisches Testgerät des Freundes)
- **Begründung / Code-Evidenz:**
  - In der lokalen Testumgebung steht kein physisches SM-G998B mit Android Auto Head-Unit zur Verfügung.
- **Vorgeschlagene Prüfschritte:**
  1. APK mit `CRIT-01`, `HIGH-01`, `HIGH-02`, `HIGH-04` und `MED-02` auf dem SM-G998B installieren.
  2. Diagnosebericht direkt nach App-Start (ohne laufende Musik) exportieren -> prüfen, dass `Has Equalizer: true` und `Has DynamicsProcessing: true` sowie `Hardware Processing Applied: false` gemeldet werden.
  3. Android Auto verbinden, Musik (z. B. Spotify oder interner Player) starten, Android Auto trennen -> prüfen, dass weder ein Crash noch eine Samsung Device Care Standby-Warnung auftritt.
- **Akzeptanzkriterien:**
  - Keine Standby-/Crash-Warnung nach 48 Stunden Alltagsbetrieb inkl. Android-Auto-Wechseln.

---

## 6. Technical Debt (Wartbarkeitsprobleme & notwendige Bereinigungen)

### [DEBT-01] Doppeltes `src/src/`-Verzeichnis und verwaister Ordner `Neuer Ordner` im `:core`-Modul
- **Priorität:** Medium
- **Kategorie:** Technical Debt / Build-Struktur
- **Status:** Offen
- **Begründung / Code-Evidenz:**
  - Im Repository liegen alle `:core`-Quellen unter `core/src/src/main/kotlin/...` statt im Gradle-Standardpfad `core/src/main/kotlin/...`; außerdem existiert ein leerer Ordner `core/Neuer Ordner/`.
- **Betroffene Dateien:**
  - `core/build.gradle.kts`
  - `core/src/src/...`
  - `core/Neuer Ordner/`
- **Vorgeschlagene Lösung:**
  - Löschen von `core/Neuer Ordner/` und Verschieben von `core/src/src/*` nach `core/src/*` (inkl. Anpassung etwaiger `sourceSets` in `core/build.gradle.kts`).
- **Akzeptanzkriterien:**
  - `./gradlew testDebugUnitTest` läuft mit regulärem Gradle-Verzeichnislayout (`core/src/main/kotlin`) grün durch.

---

### [DEBT-02] Redundante Klasse `Media3DspPipeline.kt` in `:app`
- **Priorität:** Low
- **Kategorie:** Technical Debt / Toter Code
- **Status:** Offen
- **Begründung / Code-Evidenz:**
  - `app/src/main/java/com/hardbasseq/eq/audio/media3/Media3DspPipeline.kt` enthält nur eine primitive Input-Gain-Schleife und wird im produktiven Code nicht genutzt, da die echte In-App-DSP-Pipeline in `:core` (`PlayerDspPcm16.kt` / `BassExciterPcm16.kt`) und `:player` liegt.
- **Betroffene Dateien:**
  - `app/src/main/java/com/hardbasseq/eq/audio/media3/Media3DspPipeline.kt`
  - `app/src/test/java/com/hardbasseq/eq/audio/media3/Media3DspPipelineTest.kt`
- **Vorgeschlagene Lösung:**
  - Entfernen der ungenutzten Stub-Klasse nach Überführung etwaiger Basis-Tests nach `PlayerDspPcm16Test`.
- **Akzeptanzkriterien:**
  - Keine ungenutzten DSP-Pipeline-Klassen im `:app`-Modul.
