# Review: HardBass EQ (Android)

> **Hinweis zur Ablage:** Die Bilder unter `screenshots/` (Entwürfe 01 bis 05) liegen nicht im Repository; die Verweise in diesem Review laufen ins Leere, bis die Dateien ergänzt werden.

Stand: 15.11.2026 · Repository `equalizer`, Zweig `claude/pwa-android-app-review-es0r6c`, Pull Request [#74](https://github.com/kniepertsebastian-spec/equalizer/pull/74) (Entwurf).

## Kurzurteil

**Gesamtnote: 6,5 von 10.** Der Equalizer selbst ist stark. Was die Note drückt, liegt außerhalb der Klangverarbeitung: der inoffizielle Zugang zu SoundCloud, die Ablage der Anmeldedaten und die fehlende Vorbereitung für den Play Store.

| Bereich | Note | Kurzbegründung |
|---|---|---|
| Klangverarbeitung, Architektur, Tests | 8 / 10 | Saubere Trennung (`:core` ohne Android), viele Tests, nachvollziehbare Entscheidungen in `docs/`. Bisher nur auf einem Gerät geprüft. |
| Vorzeigbar gegenüber SoundCloud | 3 / 10 | So nicht. Der Code nutzt heute das, wofür du erst um Erlaubnis bitten willst. |
| Sicherheit der Anmeldungen | 5 / 10 | Für dich und Freunde vertretbar, für fremde Nutzer nicht. |
| Optik | 6 / 10 | Farben und Schrift stimmen. Der Aufbau versteckt das Wichtigste. |
| Reife für den Play Store | 3 / 10 | Mehrere Ausschlussgründe, alle lösbar. |

Deine Fragen in einem Satz:

- **Kann ich ihn so SoundCloud zeigen?** Nein. Erst drei Dinge ändern, siehe Abschnitt 2.
- **Ist er sicher für Nutzerkonten?** Eigene Konten hat die App nicht. Die Anmeldung bei SoundCloud und Spotify ist für eine Veröffentlichung nicht sicher genug, siehe Abschnitt 3.
- **Queue:** vier Ursachen gefunden und behoben, CI ist grün, am Gerät noch nicht geprüft. Abschnitt 4.
- **Optik:** fünf Entwürfe liegen bei. Abschnitt 5.
- **Play Store:** Liste in Abschnitt 6.
- **Hebt er sich ab?** Ja, aber an einer anderen Stelle, als du vermutlich denkst. Abschnitt 7.
- **Anforderungen:** Abschnitt 8.

## 1 · Was in dieser Sitzung geändert wurde

Alles liegt im Pull Request #74, drei Commits, CI grün (Build, Lint, Unit- und Oberflächentests).

1. **Queue repariert** (`aa24db1`), Einzelheiten in Abschnitt 4.
2. **Repository aufgeräumt** (`dabdfa2`, `02868c5`):
   - Notizen aus dem Hauptverzeichnis nach `docs/` verschoben, ein doppeltes Dokument entfernt. Im Hauptverzeichnis liegen nur noch Build-Dateien, README, Lizenz und die Anweisungsdateien.
   - README: neue Tabelle „Device Requirements“, Abschnitt „Build Requirements“, Verweis auf die Datenschutz-Notiz.
   - Neu: `docs/REQUIREMENTS.md` (Mindest- und empfohlene Anforderungen mit Begründung) und `docs/PRIVACY.md` (was die App speichert und welche Server sie anspricht, als Grundlage für eine Datenschutzerklärung).
3. **CI:** Die Bildschirmfotos, die der Oberflächentest ohnehin erzeugt, werden jetzt als `player-screenshots` aufbewahrt.

**Einschränkung:** In dieser Umgebung gibt es kein Android-SDK. Die Module `:app` und `:player` konnte ich nicht lokal bauen; geprüft hat sie die CI. Auf einem Telefon lief nichts davon. Ziehen und Wischen in der Queue bitte vor dem Zusammenführen einmal am Gerät ausprobieren.

Nicht geändert habe ich die Oberfläche. `CLAUDE.md` verlangt für größere Umgestaltungen erst einen Plan und deine Freigabe. Abschnitt 5 ist dieser Plan.

## 2 · Kann ich den EQ so SoundCloud zeigen?

**So nicht.** Wer um Erlaubnis bittet und dabei eine App vorlegt, die den Zugang schon ohne Erlaubnis nutzt, liefert den Ablehnungsgrund mit. Die README ist an dieser Stelle ehrlich (`README.md:162-172`: inoffizieller Zugang, Umstieg geplant, keine Verbindung zu SoundCloud). Das ist gut, reicht aber nicht.

### Was SoundCloud im Code sehen würde

| Befund | Ort | Warum es ein Problem ist |
|---|---|---|
| Das Player-Modul trägt den Paketnamen `com.soundcloud.equalizer.player`. | `player/app/build.gradle.kts:9`, 22 Quelldateien | Der Namensraum `com.soundcloud` gehört SoundCloud. Das sieht nach einer App von SoundCloud aus und ist der erste Punkt, an dem eine Rechtsabteilung hängen bleibt. Auch der Play Store wertet so etwas als Vortäuschung. |
| Die App liest die Kennung (`client_id`) aus dem Code der SoundCloud-Website aus. | `player/.../soundcloud/SoundCloudClient.kt:89` | Das ist der Zugang der Website, kein Zugang für Entwickler. |
| Sie spricht die interne Schnittstelle `api-v2.soundcloud.com` an. | `SoundCloudClient.kt:159`, `:186`, `:288`, `:341` | Nicht dokumentiert, nicht für Dritte freigegeben. |
| Die Anmeldung läuft in einer eingebetteten Browseransicht, die sich als Chrome ausgibt; danach liest die App das Sitzungs-Cookie aus. | `player/.../auth/SoundCloudLoginActivity.kt:43`, `:118-128` | Der Nutzer gibt sein Passwort in einem Fenster ein, das die App kontrolliert. Genau das soll eine offizielle Anmeldung verhindern. |
| Geteilte YouTube-Links werden über die YouTube-Seite aufgelöst. | `docs/PRIVACY.md` (Tabelle der Server) | Für SoundCloud nebensächlich, für den Play Store nicht. |

### Was SoundCloud laut eigenen Bedingungen erwartet

Die Entwicklerseite `developers.soundcloud.com` war aus dieser Sitzung nicht erreichbar. Die folgenden Punkte stammen aus Suchergebnissen und sind **nicht am Original geprüft**; lies die Nutzungsbedingungen der Schnittstelle selbst, bevor du schreibst:

- Eine App registriert man über ein SoundCloud-Konto; dafür wird nach den Suchergebnissen ein Artist-Pro-Abo verlangt.
- Anfragen werden einzeln geprüft.
- Kein Herunterladen oder Zwischenspeichern von Titeln („Ripping“), Inhalte aus SoundCloud Go sind über die Schnittstelle nicht verfügbar.
- Die öffentliche Schnittstelle ist in der Regel nicht für kommerzielle Nutzung gedacht.
- Herkunft und Urheber müssen genannt werden.

### So würde ich vorgehen

1. **Paketnamen ändern** auf `com.hardbasseq.player` oder ähnlich. Reine Fleißarbeit, aber Pflicht vor jedem Kontakt.
2. **Eine Vorführversion ohne inoffiziellen Zugang bauen.** Der Equalizer, die Klangkette und der Player mit Dateien vom Gerät funktionieren ohne SoundCloud. Diese Version zeigst du. Der inoffizielle Zugang bleibt in einer getrennten Build-Variante, die du nicht verteilst.
3. **Sichtbarkeit des Repositorys entscheiden.** Wenn es öffentlich ist, sieht SoundCloud den inoffiziellen Zugang ohnehin. Dann entweder vorher auf privat stellen oder im Anschreiben offen sagen, dass es einen Prototyp gab und er durch den offiziellen Weg ersetzt wird.
4. **Eine Seite Anschreiben**, auf Englisch: was die App ist (Equalizer für eigene Musik, Schwerpunkt Bass), was du brauchst (Anmeldung, Suche, Wiedergabe, Likes und Playlists des angemeldeten Nutzers), was du zusagst (kein Herunterladen, kein Zwischenspeichern, Nennung von Titel, Künstler und SoundCloud, Link zurück, keine Werbung, nicht kommerziell oder dein Preismodell), dazu ein Bildschirmvideo von 60 Sekunden.
5. **Den Umstieg vorbereiten.** Die Aufgabe steht schon in `docs/roadmaps_in_progress/04-phase-1-2-offizielle-soundcloud-api.md`.

Zum Vorzeigen eignen sich die Entwürfe aus Abschnitt 5 und die Titelgrafik (`screenshots/05-feature-graphic-store.png`).

## 3 · Sicherheit: Konten und Anmeldedaten

Die App hat **keine eigenen Nutzerkonten und keinen eigenen Server**. Es gibt also keine Datenbank mit Passwörtern, die verloren gehen könnte. Die Frage ist, wie sicher die Anmeldungen bei SoundCloud und Spotify auf dem Telefon liegen.

| Punkt | Befund | Ort | Bewertung |
|---|---|---|---|
| SoundCloud-Token | Liegt unverschlüsselt in den App-Einstellungen (`sc_auth_prefs`). | `SoundCloudLoginActivity.kt:48-58` | Andere Apps kommen auf einem normalen Telefon nicht heran. Auf einem gerooteten Gerät oder über ein Backup schon. Mittel. |
| Spotify-Token | Ebenso unverschlüsselt (`spotify_auth`), einschließlich Erneuerungs-Token. | `player/.../spotify/SpotifyApiClient.kt:32` | Mittel. |
| Backup | `allowBackup="true"`; die Tokens wandern ins Android-Backup. | `app/src/main/AndroidManifest.xml:30`, `res/xml/data_extraction_rules.xml` | Bequem, aber ein Token gehört nicht ins Backup. Mittel. |
| SoundCloud-Anmeldung | Eingebettete Browseransicht mit gefälschter Browserkennung; die App liest das Cookie. | `SoundCloudLoginActivity.kt:43`, `:118-128` | Hoch. Der Nutzer muss der App sein Passwort anvertrauen. Für fremde Nutzer nicht vertretbar. |
| Spotify-Anmeldung | Offizieller Ablauf mit PKCE und Zustandsprüfung. | `SpotifyApiClient.kt:55-67` | Gut. |
| Selbst-Update | Nur von der eigenen GitHub-Adresse, mit Prüfsumme und Signaturprüfung. | `ui/update/ReleaseCatalog.kt:62`, `ReleaseUpdateClient.kt:82`, `:120` | Gut gemacht. Für den Play Store trotzdem nicht erlaubt (Abschnitt 6). |
| Berechtigungen | Vordergrunddienst, Benachrichtigungen, Internet, Paketinstallation, optional `DUMP`. Kein Mikrofon, keine Kontakte, kein Standort. | `AndroidManifest.xml:5-18` | Sparsam. |
| Tracking | Keine Analyse-, Werbe- oder Absturzbibliothek. | `docs/PRIVACY.md` | Gut, und ein Verkaufsargument. |

**Urteil:** Für dich und einen kleinen Kreis in Ordnung. Für eine Veröffentlichung fehlen drei Dinge: die offizielle Anmeldung bei SoundCloud, verschlüsselte Ablage der Tokens über den Android-Keystore und der Ausschluss der Tokens aus dem Backup. Außerdem ein „Abmelden“, das auch die Cookies der Browseransicht löscht.

Nicht geprüft: die exportierten Komponenten im Manifest (`AndroidManifest.xml:45`, `:90`, `:100`, `:111`, `:128`) einzeln darauf, ob fremde Apps sie missbrauchen können. Das gehört vor einer Veröffentlichung in eine eigene Prüfung.

## 4 · Die Queue

### Was falsch war

1. **Zeilen waren nach ihrer Position benannt.** Nach dem Wegwischen übernahm der nachrückende Titel den Wischzustand der entfernten Zeile und stand halb aus dem Bild geschoben da. Das dürfte der Fehler gewesen sein, der am meisten auffiel.
2. **Befehle nannten nur die Position.** Zwischen Tippen und Ausführung im Wiedergabedienst vergeht ein Moment. Wer schnell zweimal entfernte oder verschob, traf beim zweiten Mal den Nachbarn.
3. **Ziehen rechnete mit veralteten Maßen.** Direkt nach einem Tausch waren die Zeilen noch nicht neu vermessen. Die gezogene Zeile sprang zurück oder übersprang eine. Zusätzlich scrollte die Liste mit, wenn die oberste sichtbare Zeile beteiligt war.
4. **„Kommende leeren“ schickte einen Befehl je Titel.** Bei langen Listen dauerte das und konnte mit anderen Befehlen kollidieren.

### Was jetzt anders ist

- Jede Zeile hat einen eigenen, dauerhaften Schlüssel, auch wenn derselbe Titel zweimal in der Queue steht (`core/.../playlist/QueueRowKeys.kt`).
- Jeder Befehl trägt die Kennung des Titels mit. Der Dienst sucht den Titel an der genannten Stelle und sonst an der nächstliegenden; ist er weg, passiert nichts (`core/.../playlist/QueuePositions.kt`, `AudioPlayerService.kt`).
- Das Ziehen wartet, bis die Liste neu vermessen ist, und hält die Scrollposition fest (`QueueReorder.kt`, `QueueSheet.kt`).
- „Kommende leeren“ ist ein einziger Befehl.

Tests: 17 neue Fälle in `:core` (lokal gelaufen, grün), dazu neue Fälle in `QueueReorderTest`, `PlayerViewModelTest` und ein Oberflächentest, der eine Zeile wegwischt und prüft, dass die nächste an ihrem Platz bleibt (in der CI gelaufen, grün).

### Was offen bleibt

- Beim Ziehen an den oberen oder unteren Rand scrollt die Liste nicht von selbst weiter. Lange Queues lassen sich deshalb nur stückweise umsortieren.
- Bei Zufallswiedergabe zeigt die Liste die gespeicherte Reihenfolge, nicht die gespielte. Ob das so gewollt ist, solltest du entscheiden.
- `AndroidPlayerController.send` ruft `startService` ohne Absicherung auf (`integration/PlayerController.kt:340`). Liegt die App im Hintergrund, kann Android das ablehnen und die App abstürzen lassen.

## 5 · Optik: Vorschläge

Alle Bilder sind **Entwürfe (Mockups)**, gebaut mit den Farben und Schriften der App (`ui/theme/Color.kt`, Barlow). Es sind keine Bildschirmfotos der laufenden App; die konnte ich hier nicht starten. Die echten Fotos aus der CI ließen sich aus dieser Sitzung nicht herunterladen. Du findest sie im Pull Request unter „Checks“ → „Artifacts“ → `player-screenshots`.

### 5.1 Equalizer: die Kurve gehört nach oben und bleibt dort

![Equalizer, Vorschlag](screenshots/01-equalizer-vorschlag.png)

![Aufbau heute und Vorschlag](screenshots/02-equalizer-aufbau-heute-vs-vorschlag.png)

**Heute** ist der Bildschirm eine lange Liste mit 15 Abschnitten (`ui/equalizer/EqualizerScreen.kt`). Die Kurve kommt an sechster Stelle und verschwindet beim Scrollen. Wer unten ein Band verstellt, sieht die Wirkung nicht. `CLAUDE.md` nennt die Kurve das Herzstück; der Aufbau behandelt sie wie einen Abschnitt unter vielen.

**Vorschlag:**

- **Fester Kopf:** Route und Ein/Aus, darunter eine Statuszeile, darunter die Kurve. Dieser Teil scrollt nicht weg.
- **Statuszeile mit Aussage:** „EQ ACTIVE · SoundCloud · 48 kHz“ links, Headroom rechts. Grün nur hier, wie in der Design-Richtlinie vorgesehen. Der Zustand steht als Text da, nicht nur als Farbe.
- **Gewähltes Band direkt unter der Kurve:** Frequenz, Gain und Güte als drei Felder mit Plus und Minus. Punkt ziehen für grob, Felder für genau.
- **Vier Reiter statt langer Liste:** „Sound goal“, „Device“, „Dynamics“, „Fine-tune“. Es fällt nichts weg; die 15 Abschnitte werden nur gruppiert. Einsteiger bleiben im ersten Reiter, Fortgeschrittene finden die parametrischen Bänder im vierten.
- **Pegelanzeige klein und immer sichtbar** am unteren Rand, mit dem Eingriff des Limiters in Gelb.

Aufwand: mittel bis groß. Die Bausteine gibt es alle; neu ist die Anordnung und die Aufteilung auf Reiter. Auf kleinen Telefonen muss der feste Kopf schrumpfen können.

### 5.2 Playlists: Bibliothek statt Textliste

![Playlists heute und Vorschlag](screenshots/03-playlists-heute-vs-vorschlag.png)

**Heute** (links, schematisch nach `ui/player/library/PlaylistsTab.kt` und `PlaylistRows.kt` nachgebaut): Die Playlists stehen als Textzeilen ohne Cover in derselben Karte wie der Import. Jede Zeile hat drei gleich gewichtete Symbole, darunter „Löschen“ direkt neben „Abspielen“. Ein Tipp klappt die Titel an Ort und Stelle auf, ohne Scroll-Optimierung. Eine Suche erscheint erst in der aufgeklappten Liste und erst ab sechs Titeln.

**Vorschlag** (rechts):

- **Suchfeld ganz oben**, immer sichtbar, durchsucht Playlists und Titel.
- **Ein „+“** bündelt „Neue Playlist“, „Link importieren“ und „Dateien vom Gerät“. Der Import verschwindet aus dem Blickfeld, bis man ihn braucht.
- **Filter** als Reihe: Alle, Auf dem Gerät, SoundCloud, Importiert, jeweils mit Anzahl.
- **Karten mit Cover:** ein Mosaik aus den ersten vier Covern der Playlist. Die Adressen der Cover sind schon gespeichert; es braucht keine neue Datenquelle.
- **Eine Hauptaktion je Karte:** Abspielen. Löschen, Hochladen und Zusammenführen wandern ins Menü der Playlist-Seite. Das verhindert versehentliches Löschen.
- Die laufende Playlist ist durch Rahmen und Pause-Symbol erkennbar.

### 5.3 Playlist als eigene Seite, eine Suche für alles

![Playlist-Seite und Suche](screenshots/04-playlist-detail-und-suche.png)

- **Playlist-Seite** (links): großes Cover, Titel, Anzahl und Dauer, Herkunft als Marke. Zwei klare Knöpfe: „Play“ in Amber, „Shuffle“ daneben. Darunter die Suche in dieser Playlist und die Sortierung. Der laufende Titel hat Balken, Symbol und Farbe; gehörte Titel einen Haken und sind abgeschwächt. Die Liste lädt nur, was sichtbar ist, und bleibt auch bei 500 Titeln flüssig.
- **Suche** (rechts): ein Feld für alles. Zuerst Treffer aus den eigenen Playlists, mit der Playlist, in der sie liegen. Dann passende Playlists. Dann SoundCloud, mit „+“ zum Hinzufügen. Der Suchbegriff ist im Treffer hervorgehoben. Die heutigen Reiter „Search“ und „Playlists“ fallen dadurch zusammen.

**Verhältnis zum bestehenden Plan:** In `docs/roadmaps_in_progress/05-phase-3-player-redesign-plan-und-freigabe.md` steht als Schritt S6 bereits eine neue Bibliothek mit fester Suche, Abschnitten und Playlist-Seite. Du hast sie am 13.11.2026 freigegeben, mit der Bedingung, dass vorher der Gerätetest der Schritte S1 bis S5 läuft. Meine Entwürfe verfeinern S6 um drei Dinge: Cover-Karten statt Zeilen, die gemeinsame Suche über eigene Titel und SoundCloud, und das Bündeln der Import-Wege hinter „+“. Deshalb habe ich nichts davon gebaut; es braucht deine Freigabe und den Gerätetest.

### 5.4 Titelgrafik für Store, README und Anschreiben

![Titelgrafik](screenshots/05-feature-graphic-store.png)

1024 × 500 Pixel, das Format der Play-Store-Titelgrafik. Sie zeigt das Unterscheidungsmerkmal (die Kurve) und drei Aussagen, die stimmen: kein Root, keine Werbung und kein Tracking, Verarbeitung auf dem Gerät. „Systemweit“ steht bewusst nicht darauf, siehe Abschnitt 8.

### 5.5 Kleinigkeiten mit großer Wirkung

- **Ein Name.** Die App heißt auf dem Telefon „Baschtls Gedröhne“ (`res/values/strings.xml:3`), im Repository und in der README „HardBass EQ“. Für SoundCloud und den Store muss es einer sein.
- **Deutsch fehlt.** Es gibt nur `res/values` mit englischen Texten. Für einen deutschen Store-Eintrag ist eine deutsche Übersetzung fast Pflicht.
- **Leere Zustände.** Eine leere Bibliothek sollte zeigen, was man tun kann (drei Wege mit je einem Knopf), nicht nur einen Satz.

## 6 · Was du für den Play Store brauchst

Die README sagt bisher, eine Veröffentlichung im Play Store sei nicht geplant (`README.md:140`). Falls du das änderst:

### Konto und Formalien

| Was | Stand | Zu tun |
|---|---|---|
| Entwicklerkonto | – | Einmalig 25 US-Dollar, Identitätsprüfung mit Ausweis. Name und Land sind öffentlich; bei Apps mit Einnahmen auch eine Anschrift. |
| Geschlossener Test | – | Für persönliche Konten, die nach dem 13.11.2023 angelegt wurden: mindestens 12 Tester, 14 Tage am Stück, bevor du die Veröffentlichung beantragen kannst. Das ist die längste Wartezeit, also früh beginnen. |
| Datenschutzerklärung | Entwurf in `docs/PRIVACY.md` | Um Verantwortlichen, Rechtsgrundlagen und Rechte ergänzen, unter einer öffentlichen Adresse ablegen, im Store und in der App verlinken. |
| Formular „Datensicherheit“ | – | Passend zu `docs/PRIVACY.md` ausfüllen. Die Tokens und Suchanfragen an SoundCloud zählen als Daten, die das Gerät verlassen. |
| Altersfreigabe, Zielgruppe, Werbung | – | Fragebögen in der Play Console. |
| Eintrag | Titelgrafik als Entwurf vorhanden | Symbol 512 × 512, Titelgrafik 1024 × 500, mindestens zwei Bildschirmfotos, Kurz- und Langbeschreibung, am besten deutsch und englisch. |

### Technik

| Was | Stand | Zu tun |
|---|---|---|
| Ziel-API | `targetSdk = 36` (`app/build.gradle.kts:28`) | Erfüllt die Vorgabe, die seit 31.08.2026 gilt. |
| Format | APK über GitHub | Der Store verlangt ein App Bundle (`.aab`) und die Signierung durch Google Play. |
| Selbst-Update | `REQUEST_INSTALL_PACKAGES` (`AndroidManifest.xml:9`), eigener Updater | **Ausschlussgrund.** Apps im Store dürfen sich nicht selbst aktualisieren. Lösung: eine Store-Variante ohne Updater und ohne diese Berechtigung; die GitHub-Variante behält ihn. |
| Vordergrunddienst | Typ `mediaPlayback` erklärt | In der Play Console begründen, mit kurzem Video. Für einen Player und einen Equalizer ist das ein anerkannter Fall. |
| Sichtbarkeit anderer Apps | `<queries>` nur für Start-Intents (`AndroidManifest.xml:21`) | Eng gefasst, in Ordnung. |
| `DUMP` | optional, nur per ADB erteilbar (`AndroidManifest.xml:16`) | Zulässig, aber in der Beschreibung erklären. |
| Codeverkleinerung | aus (`app/build.gradle.kts:59`) | Kein Hindernis. Die App wird größer als nötig. |
| Gerätetests | ein Gerät (Pixel 10, Android 16) | Vor einer Veröffentlichung mindestens ein Samsung, ein Xiaomi und ein Gerät mit Android 9 bis 11. Die Hersteller verändern die Audio-Effekte am stärksten. |

### Inhaltliche Risiken

- **SoundCloud ohne Erlaubnis.** Eine App, die Inhalte eines Dienstes ohne dessen Genehmigung abruft, verstößt gegen die Store-Regeln und kann auf Beschwerde entfernt werden. Ohne Zusage von SoundCloud gehört der SoundCloud-Teil nicht in die Store-Variante.
- **Markennamen.** „SoundCloud“ und „Spotify“ dürfen im Titel und im Symbol nicht vorkommen und in der Beschreibung nur erklärend.
- **Spotify-Import.** Die Entwicklerbedingungen von Spotify schränken ein, Inhalte in andere Musikdienste zu überführen. Vor einer Veröffentlichung lesen.
- **YouTube-Links.** Das Auslesen der YouTube-Seite ist in den Bedingungen von YouTube nicht vorgesehen.
- **Lautstärke.** Der Warnhinweis bei „Headphone power“ ist vorhanden (`strings.xml:154`). Gut; in der Beschreibung ebenfalls erwähnen.
- **Lizenz.** Das Repository ist proprietär (`LICENSE`). Die Schriften (Barlow) und Bibliotheken brauchen einen Lizenzhinweis in der App; `docs/licenses/` ist die Grundlage.

Hinweis: Google hat eine Pflicht zur Verifizierung von Entwicklern auch für Apps außerhalb des Stores angekündigt, die ab 2027 weltweit gelten soll. Das würde auch die Verteilung über GitHub betreffen. Den Stand habe ich in dieser Sitzung nicht nachgeprüft.

## 7 · Hebt sich der EQ von anderen ab?

Der Vergleich stützt sich auf meinen Kenntnisstand über die genannten Apps, nicht auf einen Test in dieser Sitzung.

| App | Stärke | Wo HardBass EQ steht |
|---|---|---|
| Wavelet | Sehr große Sammlung fertiger Kopfhörer-Korrekturen (AutoEq), läuft ohne Root, sehr verbreitet. | Beim systemweiten Equalizer gleichauf in der Technik (gleiche Android-Schnittstelle), im Umfang der Profile dahinter. Der Import von AutoEq- und Wavelet-Dateien ist vorhanden; das senkt die Wechselhürde. |
| Poweramp Equalizer | Parametrisch, ausgereift, erkennt viele Player, kostenpflichtig. | Ähnlicher Anspruch. HardBass EQ ist verständlicher für Einsteiger (Klangziele), Poweramp ist ausgereifter und breiter getestet. |
| RootlessJamesDSP | Quelloffen, verarbeitet wirklich alle Audioausgaben, mächtige Effekte. | Anderer Weg mit anderen Nachteilen (Einrichtung, Verzögerung, manche Apps sperren sich). HardBass EQ ist einfacher einzurichten. |
| Eingebaute Equalizer der Musik-Apps | Bequem, aber grob. | Deutlich überlegen. |

**Wo du dich wirklich abhebst**

1. **Player und Equalizer aus einer Hand.** Im eigenen Player läuft eine vollständige Klangkette (Kurve, virtueller Bass, Mehrband-Kompressor, Limiter), die ein systemweiter Equalizer unter Android nicht bieten kann. Das hat keiner der Genannten in dieser Form.
2. **Bass ohne Übersteuern, sichtbar gemacht.** Headroom-Anzeige, automatische Pegelabsenkung, Hinweis „der Limiter wird arbeiten“. Andere Apps lassen einen in die Verzerrung laufen.
3. **Eine klare Zielgruppe.** Hardbass, Hardstyle, Techno. Eine App „für Bass-Musik“ findet leichter Fans als der hundertste allgemeine Equalizer.
4. **Zwei Ebenen.** Klangziele für Einsteiger, parametrische Bänder für Fortgeschrittene.
5. **Kein Tracking, keine Werbung, Verarbeitung auf dem Gerät.**
6. **Presets teilen per Link und QR-Code.** Gut für Mundpropaganda in einer Szene.

**Die ehrliche Einschränkung:** Punkt 1 ist dein stärkstes Argument und hängt zugleich an SoundCloud. Ohne Zusage bleibt ein Player für Dateien vom Gerät plus ein systemweiter Equalizer, der auf Augenhöhe mit Wavelet ist, aber nicht darüber. Das reicht für eine Nische, wenn Optik und Zielgruppe stimmen. Mit SoundCloud wäre es ein Angebot, das es so nicht gibt.

**Empfehlung zur Positionierung:** nicht „noch ein Equalizer“, sondern „Bass, den du siehst und steuerst, ohne dass es verzerrt“. Die Kurve und die Headroom-Anzeige gehören in jedes Bild.

## 8 · Mindest- und empfohlene Anforderungen

Ausführlich mit Begründung in `docs/REQUIREMENTS.md` (neu im Pull Request). Werte mit „geschätzt“ beruhen nicht auf Messungen; geprüft ist nur ein Pixel 10 mit Android 16.

| | Minimum | Empfohlen |
|---|---|---|
| Android | 9 (API 28). Fest im Code: `minSdk = 28`; die verwendete Klangverarbeitung gibt es erst ab dieser Version. | 13 oder neuer |
| Prozessor | beliebig (ARM 32/64 Bit, x86); die App enthält keinen nativen Code | 64-Bit-ARM, 8 Kerne, Baujahr 2020 oder neuer (geschätzt) |
| Arbeitsspeicher | 3 GB (geschätzt) | 6 GB oder mehr (geschätzt) |
| Speicherplatz | etwa 100 MB frei (geschätzt); die App speichert keine Musik | – |
| Audio | Das Gerät muss die Android-Effekte `Equalizer` und `DynamicsProcessing` bereitstellen. Die Diagnoseseite der App zeigt, ob das der Fall ist. | Kabel oder USB, oder Bluetooth mit stabilem Codec |
| Netz | für den Equalizer keins; nur Player, Link-Import und Update-Prüfung brauchen Internet | – |
| Berechtigungen | Benachrichtigungen (ab Android 13) | zusätzlich Ausnahme von der Akku-Optimierung bei Herstellern mit strenger Stromsparfunktion |
| Android Auto | Android Auto auf dem Telefon; solange die App nicht aus dem Play Store stammt, nur mit „Unbekannte Quellen“ in den Entwicklereinstellungen von Android Auto | – |

**Grenzen von Android, nicht der Hardware:**

- Der Equalizer hängt sich an einzelne Apps, die ihre Audio-Sitzung bekannt geben. Er ist nicht „systemweit“ im Wortsinn. In der Werbung deshalb „für deine Musik-Apps“ sagen.
- Klangverbesserer der Hersteller (Dolby und Ähnliches) laufen zusätzlich und verändern das Ergebnis.

**Offen:** Prozessorlast und Speicherbedarf auf einem günstigen und einem mittleren Gerät messen und die geschätzten Werte ersetzen.

## 9 · Nicht geprüft

- Verhalten auf einem echten Gerät, auch nicht die Queue-Reparatur.
- Lokaler Build von `:app` und `:player` (kein Android-SDK in dieser Umgebung); geprüft hat die CI.
- Die echten Bildschirmfotos aus der CI (Download aus dieser Sitzung blockiert).
- Die Bedingungen von SoundCloud, Spotify und YouTube im Original.
- Klangqualität, Verzögerung, Akkuverbrauch.
- Exportierte Komponenten auf Missbrauch durch andere Apps.
- Die Vergleichs-Apps im direkten Test.
