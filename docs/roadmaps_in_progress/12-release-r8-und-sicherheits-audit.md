# Roadmap 12 – Build-Hygiene, R8-Release-Härtung & Sicherheits-/UI-Nach-Audit

**Priorität:** Kritisch für Store-/Release-Freigabe (Blocker B4, Major M7, Minor m2, Minor m3)  
**Ziel:** R8-Minifizierung & Resource-Shrinking im Release wieder aktivieren, Netzwerksicherheit explizit absichern, Gradle-/Git-Hygiene bereinigen und die im ersten Audit nicht im Volltext eingesehenen Sicherheits- und UI-Dateien systematisch prüfen.  
**Abhängigkeit:** Keine — **100 % überschneidungsfrei** zu den Roadmaps 08, 09, 10, 11 und 13.

---

## Ausgangslage / Befund

1. **R8 & Resource-Shrinking im Release aus (Blocker B4):**  
   In `app/build.gradle.kts:76` steht `isMinifyEnabled = false` und `isShrinkResources` fehlt. Laut Kommentar wurde R8 wegen eines inzwischen behobenen NPE in `MainViewModel` deaktiviert. Folge: aufgeblähte Release-APK/AAB, ungeschützter Bytecode, aktive Debug-Logs im Release.
2. **Fehlende `network_security_config.xml` (Major M7):**  
   Im Dateibaum existiert keine `app/src/main/res/xml/network_security_config.xml`. Cleartext-HTTP-Regeln zwischen `store`- und `full`-Flavor (z. B. Radio-Streams, lokaler Remote-Server) sind nicht zentral abgesichert.
3. **Git- & Gradle-Hygiene (Minor m2, Minor m3):**  
   - `.gitignore` schließt `*.jks`, `*.keystore`, `*.p12`, `keystore.properties` nicht explizit aus.  
   - `libs.versions.toml` pflegt zwei unterschiedliche `core-ktx`-Versionen (`1.16.0` vs. `core-ktx-player 1.13.1`); `:player` hat `minSdk = 24` statt `28`.
4. **Offenes Nach-Audit aus dem Erst-Review:**  
   `AndroidManifest.xml` (alle Module/Flavors), `TokenVault.kt`, `SoundCloudLoginActivity.kt`, `RemoteServerService.kt` sowie die Compose-UI-Screens wurden noch nicht zeilenweise auditiert.

---

## Zugewiesener Dateiumfang

- `app/build.gradle.kts`, `player/app/build.gradle.kts`, `gradle/libs.versions.toml`
- `app/proguard-rules.pro`, `.gitignore`
- `app/src/main/res/xml/network_security_config.xml` *(neu)* & optional `app/src/full/res/xml/network_security_config.xml`
- `app/src/main/AndroidManifest.xml`, `app/src/full/AndroidManifest.xml`, `player/app/src/main/AndroidManifest.xml`, `player/app/src/full/AndroidManifest.xml`
- Audit-Zielobjekte (prüfen → nur konkrete Sicherheits-/a11y-Fixes):
  - `player/app/src/full/java/com/auracurve/app/player/auth/TokenVault.kt`
  - `player/app/src/full/java/com/auracurve/app/player/auth/SoundCloudLoginActivity.kt`
  - `app/src/full/java/com/auracurve/app/remote/RemoteServerService.kt`
  - `app/src/main/java/com/auracurve/app/ui/equalizer/EqualizerScreen.kt`, `app/src/main/java/com/auracurve/app/ui/player/PlayerScreen.kt`, `app/src/main/java/com/auracurve/app/ui/player/nowplaying/NowPlayingScreen.kt`

---

## Arbeitspakete

### P12-1 – R8 & Resource-Shrinking im Release reaktivieren (Blocker B4)
- [ ] In `app/build.gradle.kts` (`buildTypes.release`):
  ```kotlin
  isMinifyEnabled = true
  isShrinkResources = true
  ```
- [ ] In `app/proguard-rules.pro`:
  - `-assumenosideeffects`-Regel zum Entfernen von `android.util.Log.v`, `Log.d`, `Log.i` im Release ergänzen (`Log.w`/`Log.e` ohne sensible Daten für Diagnostik prüfen).
  - Prüfen, ob Ktor (`full`-Flavor), `kotlinx.serialization`, Hilt, Room und Media3 fehlende Keep-Regeln unter R8 brauchen.
- [ ] `storeRelease` und `fullRelease` bauen und `python3 scripts/check_store_apk.py` ausführen.

### P12-2 – `network_security_config.xml` & Manifest-Audit (Major M7)
- [ ] `app/src/main/res/xml/network_security_config.xml` anlegen mit `<base-config cleartextTrafficPermitted="false" />` und im `main`-Manifest über `android:networkSecurityConfig="@xml/network_security_config"` einbinden.
- [ ] Falls der `full`-Flavor unverschlüsselte Radio-Streams oder LAN-Traffic braucht, dies ausschließlich in `app/src/full/res/xml/network_security_config.xml` dokumentiert und so eng wie möglich fassen.
- [ ] Alle 4 Manifest-Dateien (`app` + `player`, `main` + `full`) systematisch prüfen auf:
  - Prinzip minimaler Permissions (keine unnötigen Rechte im `store`-Flavor)
  - explizites `android:exported` an jeder Activity, jedem Service und jedem Receiver
  - `android:allowBackup` i. V. m. `backup_rules.xml` und `data_extraction_rules.xml` (keine Tokens im Cloud-Backup!)
  - korrekte `android:foregroundServiceType`-Deklarationen (`mediaPlayback`, ggf. `connectedDevice`/`location` im `full`-Flavor)

### P12-3 – Gradle- & Git-Hygiene bereinigen (Minor m2, Minor m3)
- [ ] `.gitignore` um `*.jks`, `*.keystore`, `*.p12`, `keystore.properties` ergänzen.
- [ ] `core-ktx-player` in `gradle/libs.versions.toml` und `player/app/build.gradle.kts` auf das zentrale `libs.core.ktx` vereinheitlichen.
- [ ] `minSdk = 28` in `player/app/build.gradle.kts` an `:app` angleichen.

### P12-4 – Sicherheits- & Barrierefreiheits-Nach-Audit
- [ ] **`TokenVault.kt`:** Prüfen, ob OAuth-Tokens per Android Keystore (AES-GCM) verschlüsselt gespeichert werden und nicht im Klartext in SharedPreferences oder Backups landen.
- [ ] **`SoundCloudLoginActivity.kt`:** WebView prüfen (`allowFileAccess = false`, `allowContentAccess = false`, kein unsicheres `addJavascriptInterface`, strenge Host-/Redirect-Prüfung, Cookie-Aufräumen beim Logout).
- [ ] **`RemoteServerService.kt`:** Prüfen auf sichere Token-Prüfung (Constant-Time-Vergleich), Rate-Limiting, Bind-Interface (LAN vs. `0.0.0.0`) und kein Logging von Auth-Tokens.
- [ ] **Compose-UI-Stichprobe (`EqualizerScreen`, `PlayerScreen`, `NowPlayingScreen`):**
  - `contentDescription` an allen interaktiven Icons/Covern
  - Touch-Targets `>= 48.dp`
  - `key = { ... }` in allen `LazyColumn`/`LazyRow`-Listen
  - sauberes Edge-to-Edge-`WindowInsets`-Handling (System-Bars & IME)
  - keine hartcodierten User-Strings (alles in `strings.xml`)

---

## Abnahmekriterien & Verifikation

- [ ] **Release-Build & Store-Check:**
  ```bash
  ./gradlew :app:assembleStoreRelease :app:assembleFullRelease :app:ktlintCheck
  python3 scripts/check_store_apk.py
  ```
- [ ] **Gerätetest mit signierter R8-Release-APK (`storeRelease` & `fullRelease`):**
  - App-Start ohne Crash
  - Room-Datenbank-Migration / Preset-Speichern & JSON-Import/-Export
  - Eigenes Player-Playback + Spotify-Session-Attach
  - `full`-Flavor: Remote-Server-Start & SoundCloud-Login
- [ ] **TalkBack & 200-%-Schriftgröße:** Stichprobe auf EQ- und Player-Screen ohne abgeschnittene Controls.

---

## Umsetzungsstand (23.11.2026)

### P12-1 – R8 & Resource-Shrinking
- `isMinifyEnabled = true`, `isShrinkResources = true` in `app/build.gradle.kts`.
- `proguard-rules.pro`: `-assumenosideeffects` für `Log.v/d/i` (und `isLoggable`); `Log.w/e` bleiben
  (geprüft: sie loggen Klassennamen von Exceptions, keine Tokens).
- Ktor (`full`): `-dontwarn java.lang.management.*` (IntelliJ-Debugger-Detektor, auf Android nie
  aufgerufen) – das war die einzige Lücke, die R8 meldete. Hilt, Room, Media3, WorkManager,
  kotlinx.serialization: Consumer-Rules bzw. bestehende Regeln reichen (R8-Build ohne Fehler).
- **Neu:** `-keepnames` für die Marker-Klassen von `scripts/check_store_apk.py`. Ohne sie benennt R8
  die Klassen um – ein versehentlich in den Store-Build gerutschter Full-Code würde vom Check nicht
  mehr erkannt.
- Lokal gebaut (unsigniert, mit R8): `assembleStoreRelease` 6,7 MB, `assembleFullRelease` 9,5 MB.
  `check_store_apk.py` auf dem Store-Release: keine Marker.

### P12-2 – Network-Security-Config & Manifeste
- `res/xml/network_security_config.xml` (main): `cleartextTrafficPermitted="false"`, nur System-CAs.
  Kein `full`-Override nötig: alle ausgehenden Verbindungen sind HTTPS (Radio-Katalog filtert auf
  `https://`, `DirectStream` spielt kein `http://`); der Remote-Server ist eingehend und von der
  Config nicht betroffen.
- Manifest-Audit: alle Activities/Services/Receiver mit explizitem `exported`; Token-Dateien
  (`sc_auth_prefs.xml`, `spotify_auth.xml`) aus Cloud-Backup und Gerätetransfer ausgeschlossen;
  Remote-Token/DuckDNS in `noBackupFilesDir`. Store-Flavor: nur INTERNET, FGS(+mediaPlayback),
  POST_NOTIFICATIONS, WAKE_LOCK, NETWORK_STATE und das per ADB optionale DUMP.
- Bereinigt: `ACCESS_FINE/COARSE_LOCATION` standen im `full`-Manifest doppelt.
- Bewusst **nicht** geändert: `RemoteServerService` bleibt `specialUse` (ein `location`-Typ würde
  `startForeground` ohne erteilte Standortberechtigung zum Absturz bringen; Hintergrund-Standort ist
  über `ACCESS_BACKGROUND_LOCATION` abgedeckt).

### P12-3 – Gradle & Git
- `.gitignore`: `*.jks`, `*.keystore`, `*.p12`, `keystore.properties` (kein solches File war eingecheckt).
- `core-ktx-player` entfernt, `:player` nutzt `libs.core.ktx` (1.16.0); `:player` `minSdk = 28`.

### P12-4 – Nach-Audit
- `TokenVault.kt`: in Ordnung (AES-256-GCM, Schlüssel im AndroidKeyStore, Klartext-Migration,
  kein Backup). Keine Änderung.
- `SoundCloudLoginActivity.kt`: **Fixes** – `allowFileAccess`/`allowContentAccess = false` für
  WebView und Popup; Navigation nur `https:` (+ `about:blank`), `intent:`/`file:`/`content:`/`http:`
  werden blockiert; die Popup-Übergabe prüft den Host (`soundcloud.com`/`*.soundcloud.com`) statt
  `url.contains("soundcloud.com")`. Kein `addJavascriptInterface`; Cookies beim Logout gelöscht
  (bestand schon). Test: `SoundCloudLoginUrlTest`.
- `RemoteServerService.kt`: in Ordnung – Bind auf `0.0.0.0`, aber jede Anfrage über
  `PrivateNetwork` + `NetworkZone` gefiltert; Token-Vergleich in konstanter Zeit
  (`MessageDigest.isEqual`), `AuthRateLimiter` (10 Fehlversuche/min → 60 s Sperre, auch fürs
  Pairing); keine Token in Logs.
- Compose-Stichprobe: alle interaktiven Icons haben `contentDescription` (die `null`-Fälle sind
  dekorativ neben Text); keine hartcodierten Texte; keine `items()` ohne `key` in den drei Screens.
  **Fix:** `RoundControlButton` hält jetzt mindestens 48 dp Touch-Fläche
  (`minimumInteractiveComponentSize`, betraf den 44-dp-„Mehr“-Knopf im Now-Playing).

### Offen (nur am Gerät nachweisbar)
1. Signierte R8-Release-APKs (`storeRelease`, `fullRelease`) installieren: Start, Presets speichern,
   JSON-Import/-Export, Player + Spotify-Attach, Remote-Server, SoundCloud-Login.
2. TalkBack + 200 % Schrift auf EQ- und Player-Screen.
3. Lokal stürzte `lintVitalAnalyzeFullRelease` einmal in Lint selbst ab (FIR-Fehler bei
  `CarProtectionMonitor.kt`); der zweite Lauf ging durch, die CI auf `main` ist grün – beobachten.
