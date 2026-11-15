# Roadmap 02 · Vorbereitung & Anfrage bei SoundCloud

**Enthält:** Phase 1  
**Ziel:** Saubere Trennung zwischen Store-/Vorführversion und Privatversion sowie Einreichung der offiziellen API-Anfrage bei SoundCloud.  
**Abgleich mit dem Repository:** 15.11.2026 (Basis `c4e361e`, Zweig `claude/festive-wright-qs2q7k`). Übersicht und Leitplanken: [`00-uebersicht-und-leitplanken.md`](00-uebersicht-und-leitplanken.md)

---

## Aufgabenübersicht

| # | Aufgabe | Review-Befund | Aufwand | Wer | Status |
|---|---|---|---|---|---|
| 1.1 | **Grundsatzentscheidungen treffen:** App kostenlos oder kostenpflichtig anbieten? Repository öffentlich oder privat führen? *(Der Name AuraCurve steht bereits fest.)* | Review 2, 5.5 | S | du | [~] |
| 1.2 | **Paketnamen bereinigen:** Paketnamen des Player-Moduls von `com.soundcloud.equalizer.player` auf den eigenen Namespace ändern (22 Quelldateien, Manifest, Tests). *Wird in einem Zug mit M.3 umgesetzt.* | Review 2 | M | Umsetzung | [x] |
| 1.3 | **Build-Varianten (Flavors) anlegen:**<br>1. *Vorführ- & Store-Variante:* ohne inoffiziellen SoundCloud-Zugang, ohne Selbst-Update und ohne YouTube-Auflösung.<br>2. *Private Variante:* mit dem heutigen Funktionsumfang. | Review 2, 6 | M–L | Umsetzung | [ ] Plan liegt vor, wartet auf Freigabe |
| 1.4 | **SoundCloud-Bedingungen prüfen:** Nutzungsbedingungen der SoundCloud-Schnittstelle im Original lesen und Zusagen im Anschreiben darauf abstimmen. Prüfen, ob für die Registrierung ein *Artist-Pro-Abo* nötig ist. | Review 2 | S | du | [ ] |
| 1.5 | **Bewerbungspaket erstellen:** Einseitiges Anschreiben auf Englisch unter dem Namen *AuraCurve* + 60-Sekunden-Bildschirmvideo der sauberen Vorführvariante. Titelgrafik liegt aus M.5 vor. | Review 2 | S–M | du, Umsetzung | [~] Entwurf liegt vor |
| 1.6 | **Anfrage absenden:** Bewerbung bei SoundCloud einreichen. Danach **nicht untätig warten**, sondern direkt mit Roadmap 03 (Sicherheit & Gerätetests) weitermachen. | – | S | du | [ ] |

**Reihenfolge:** 1.1 und 1.2 (erledigt bis auf die Punkte unten) → 1.3 nach Freigabe des Plans → 1.4 parallel → 1.5 (Video erst mit der Store-Variante aus 1.3) → 1.6.

---

## 1.1 · Entscheidungen (15.11.2026)

- **Preis:** kostenlos, keine Werbung. Im Anschreiben steht deshalb „non-commercial“.
- **Repository:** wird **privat**. Umstellen musst du selbst (GitHub → Settings → General → Danger Zone → Change visibility), und zwar **bevor** die Anfrage rausgeht. Die Git-Historie enthält den inoffiziellen Zugang; ein öffentliches Repo würde ihn SoundCloud zeigen.
- **Anwendungs-ID:** `com.auracurve.app` (siehe 1.2).

Status `[~]`: entschieden, aber das Repo ist noch öffentlich.

> **Codebefund – Folge für das Selbst-Update:** `ReleaseUpdateClient.kt:21` fragt `api.github.com/repos/kniepertsebastian-spec/equalizer/releases` **ohne Anmeldung** ab, und `ReleaseCatalog.kt:62` lädt nur von `github.com/kniepertsebastian-spec/equalizer/releases/download/`. Bei einem privaten Repo antwortet GitHub darauf mit 404: **Die Privatversion findet ab der Umstellung keine Updates mehr.** Möglichkeiten (Entscheidung bei dir, gehört zu M.4):
> 1. Ein zweites, öffentliches Repo nur für Releases (enthält keinen Code, nur APK und `update.json`). `release.yml` lädt dorthin hoch, beide Konstanten zeigen dorthin. *Empfehlung.*
> 2. Selbst-Update hinnehmen, dass es nicht mehr geht, und APKs von Hand verteilen.
>
> Ein Zugangstoken in der App ist **keine** Option: Es würde mit jeder APK ausgeliefert.

---

## 1.2 · Paketnamen (umgesetzt zusammen mit M.3)

Voraussetzungen laut Rückmeldung vom 15.11.2026 erfüllt: Namensprüfung M.1 und Queue-Gerätetest 0.1 sind erledigt.

**Umgesetzt:**

| Vorher | Nachher | Wo |
|---|---|---|
| `com.hardbasseq.eq` (Anwendungs-ID, Namespace, Pakete) | `com.auracurve.app` | `gradle.properties`, `:app` (main, test, androidTest), `:core` (main, test) |
| `com.soundcloud.equalizer.player` | `com.auracurve.app.player` | `player/app/build.gradle.kts`, 22 Quelldateien, 4 Tests, Manifest von `:app` |
| Intent-Aktionen `com.soundcloud.equalizer.player.*`, `com.hardbasseq.eq.action.*`, Extra `com.hardbasseq.eq.artwork_url` | `com.auracurve.app.player.*`, `com.auracurve.app.action.*`, `com.auracurve.app.artwork_url` | `AudioPlayerService`, `PlayerLaunch`, `QuickCommandIntents`, `QuickWidgetProvider`, `TrackMediaItems`, Manifest |
| Room-Schemaordner `app/schemas/com.hardbasseq.eq.data.db.AppDatabase/` | `app/schemas/com.auracurve.app.data.db.AppDatabase/` | Ordnername folgt dem Klassennamen; `DatabaseUpgradeTest` angepasst |
| `packageName` in `update.json` | `com.auracurve.app` | `.github/workflows/release.yml`, `ReleaseCatalogTest` |
| ProGuard-Regeln, ADB-Hinweise (`pm grant … DUMP`) | neue ID | `proguard-rules.pro`, Manifest-Kommentar, `DumpsysSessionScanner`, `DiagnosticsReportFormatter` (+ Test) |
| Pfadangaben in der Doku | neue Pfade | `README.md`, `docs/ARCHITECTURE.md`, `DECISIONS.md`, `AUDIO_IMPROVEMENTS.md`, `FEASIBILITY.md`, `HIGH_RES_EQ_IMPROVEMENTS.md`, `PLAYER_REDESIGN.md`, `TEST_MATRIX.md`, Roadmap 06 |

Im Code (ohne `.md`) kommen `com.hardbasseq.eq` und `com.soundcloud.equalizer` nicht mehr vor (`git grep`). Die Importblöcke sind nach ktlint-Regel neu sortiert.

**Geprüft in dieser Sitzung:** `:core:test`, `:core:ktlintCheck`, `:app:ktlintCheck` grün. **Nicht lokal prüfbar** (kein Android-SDK): Bau und Tests von `:app` und `:player`. Geprüft hat sie die CI im Pull Request #76 (`build-and-test` grün auf `3e553d2`: Build, Lint, Unit- und Oberflächentests).

**Folgen, die du kennen musst:**
- Für Android ist das eine **neue App**. Die alte Installation (`com.hardbasseq.eq`) bekommt kein Update, Presets und Playlists ziehen nicht mit. Alte App erst deinstallieren, wenn du die Daten nicht mehr brauchst (Presets lassen sich vorher als Link teilen).
- Die alte Installation sieht neue Releases im Selbst-Update nicht mehr (anderer `packageName` in `update.json`). Das ist gewollt: Android würde sie ohnehin nicht als Update annehmen.

**Bewusst nicht geändert** (gehört zu M.2 bzw. braucht eigene Entscheidung):
- URI-Schema `hardbasseq://` (geteilte Preset-Links, Spotify-Rücksprung `hardbasseq://spotify-callback`) und der interne Platzhalter `hardbasseq-sc://`. Eine Änderung macht bereits geteilte Preset-Links ungültig und verlangt, dass jeder Nutzer die Redirect-URI in seiner Spotify-App neu einträgt. Vorschlag für M.2: neues Schema `auracurve://` einführen und `hardbasseq://` zusätzlich weiter annehmen.
- Gradle-Schlüssel `hardbasseq.*` (`applicationId`, `versionCode`, Signatur), Keystore-Alias `hardbasseq`, Datenbankdatei `hardbasseq.db`, `rootProject.name`, `app_name`, Kommentare mit „HardBass EQ“/„SC Equalizer Player“. Nichts davon ist für SoundCloud oder den Store sichtbar.

---

## 1.3 · Build-Varianten – Plan zur Freigabe

*Noch nicht umgesetzt. Bitte freigeben oder ändern; erst danach wird Code angefasst.*

### Ziel

Die Store-Variante enthält den inoffiziellen Zugang **nicht im ausgelieferten Code** (nicht nur ausgeblendet). Wer die APK zerlegt, findet weder `api-v2.soundcloud.com` noch das Auslesen der `client_id` noch die Login-WebView.

### Aufbau

Eine Flavor-Dimension `distribution` in `:app` und `:player`:

| Flavor | Anwendungs-ID | Inhalt |
|---|---|---|
| `store` | `com.auracurve.app` | Equalizer, Klangkette, Player für Dateien vom Gerät, Android Auto, Widgets/Kacheln. Kein SoundCloud, kein Spotify-Import, kein Link-Import, kein Selbst-Update, keine YouTube-Auflösung. |
| `full` | `com.auracurve.app.full` *(Vorschlag, siehe E1)* | Heutiger Funktionsumfang. |

Code-Aufteilung (Quellordner je Flavor):

- **`:player`**
  - `main`: Wiedergabe-Kern (`AudioPlayerService`, Queue, DSP, `ResolvingTrackMediaSource`, Modelle) und eine neue Schnittstelle `TrackSource` (Stream auflösen, suchen, Bibliothek, Links).
  - `full`: `SoundCloudClient`, `SoundCloudLoginActivity` (+ eigener Manifest-Eintrag), `DiscoveryLoader`, `LibraryLoader`, `LinkResolver` (inkl. YouTube-/Spotify-oEmbed), `SpotifyApiClient`, `SpotifyLoginActivity`, SoundCloud-Fehlertexte; `TrackSource`-Umsetzung über SoundCloud.
  - `store`: `TrackSource`-Umsetzung nur für lokale Dateien.
- **`:app`**
  - `main`: `PlayerController` bleibt die Schnittstelle; neu ein `PlayerFeatures`-Objekt (SoundCloud, Link-Import, Spotify-Import, Selbst-Update an/aus), per Hilt bereitgestellt. Die Oberfläche blendet Entdecken, Bibliothek, Verknüpfte Dienste und Link-Import danach aus.
  - `full`: SoundCloud-Teil des `PlayerController`, `ReleaseUpdateClient`/`ReleaseCatalog`/`ReleaseUpdateNotice`, `REQUEST_INSTALL_PACKAGES` im Flavor-Manifest.
  - `store`: leere `ReleaseUpdateNotice`, Hilt-Modul mit abgeschalteten Funktionen.
- **Tests:** SoundCloud-, Spotify- und Update-Tests nach `testFull`; gemeinsame Tests laufen in beiden Varianten.
- **CI (`ci.yml`):** beide Varianten bauen und testen. Neuer Prüfschritt: In der Store-APK dürfen `api-v2.soundcloud.com`, `client_id`, `youtube.com/oembed` und `REQUEST_INSTALL_PACKAGES` nicht vorkommen (Suche in DEX und Manifest).
- **Release (`release.yml`):** baut `fullRelease` für das Selbst-Update. Das Store-Bundle (`bundleStoreRelease`) kommt mit Roadmap 05.

### Entscheidungen, die ich dafür brauche

- **E1 – ID der Privatversion:** `com.auracurve.app.full` (beide Versionen gleichzeitig installierbar, Store-ID bleibt sauber) *(Empfehlung)* oder dieselbe ID für beide.
- **E2 – Spotify in der Store-Variante:** raus *(Empfehlung: der Import führt nur zur SoundCloud-Suche und liest öffentliche Spotify-Seiten aus)* oder drin lassen.
- **E3 – Zeitpunkt:** direkt nach dem Merge dieses Pull Requests, damit die Dateien nicht zweimal umziehen.

### Aufwand und Risiko

M–L, etwa 30 bis 40 Dateien. `PlayerViewModel` (958 Zeilen) und `AudioPlayerService` (973 Zeilen) müssen an die neue Schnittstelle; dabei kein Verhalten der Privatversion ändern. Prüfung nur über die CI und einen Gerätetest beider Varianten.

---

## 1.4 · Was du in den Bedingungen nachsehen solltest

`developers.soundcloud.com` ist aus der Arbeitsumgebung gesperrt; die Liste stammt aus dem Review (Abschnitt 2) und ist **nicht am Original geprüft**. Für jeden Punkt im Original nachsehen und im Anschreiben-Entwurf anpassen:

- [ ] Braucht die App-Registrierung ein **Artist-Pro-Abo** (oder ein anderes Abo)?
- [ ] Gibt es ein Formular/Verfahren für neue Apps, oder nur per Support-Anfrage? Wohin geht die Anfrage?
- [ ] Verbot von Herunterladen und Zwischenspeichern („Ripping“), auch kurzfristigem Puffern?
- [ ] Ist ein Equalizer/DSP auf dem Stream erlaubt (Veränderung des Klangs)? **Wichtigster Punkt für AuraCurve.**
- [ ] Pflichtangaben zur Herkunft: Logo, „Powered by SoundCloud“, Link zum Titel, Künstlername.
- [ ] Nicht-kommerzielle Nutzung: Was zählt als kommerziell (Spenden, Pro-Version)?
- [ ] Inhalte von SoundCloud Go: wirklich nicht über die Schnittstelle verfügbar?
- [ ] Hintergrundwiedergabe, Android Auto, Benachrichtigung mit Steuerung erlaubt?
- [ ] Ratenbegrenzung und Vorgaben für die Anmeldung (OAuth 2.1 mit PKCE, Redirect-URI).

---

## 1.5 · Bewerbungspaket

- **Anschreiben:** Entwurf in [`soundcloud-anschreiben-entwurf.md`](soundcloud-anschreiben-entwurf.md). Platzhalter in `[eckigen Klammern]`; nach 1.4 abgleichen.
- **Video (60 s):** erst mit der Store-Variante aus 1.3 aufnehmen. Ablauf-Vorschlag steht im Entwurf.
- **Titelgrafik:** M.5 ist noch offen; die Entwürfe unter `screenshots/` liegen nicht im Repository.

---

## Definition of Done

Die Anfrage ist abgeschickt und nichts, was SoundCloud zu sehen bekommt, nutzt noch den Namen oder den internen/inoffiziellen Zugang von SoundCloud.

**Stand 15.11.2026:** Paketname erledigt (CI grün). Offen: Repo privat stellen, Store-Variante (1.3), Bedingungen (1.4), Video und Titelgrafik, sichtbarer App-Name „Baschtls Gedröhne“ (M.2).
