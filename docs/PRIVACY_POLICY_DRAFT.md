# Datenschutzerklärung · Entwurf für die Store-Variante

**Stand:** 15.11.2026. **Entwurf, rechtlich nicht geprüft.** Gilt nur für die Store-Variante `com.auracurve.app` (Flavor `store`). Grundlage ist `docs/PRIVACY.md`, abgeglichen mit dem Code der Store-Variante (Berechtigungen aus dem gebauten APK, `scripts/check_store_apk.py`). Die Privatvariante (`com.auracurve.app.full`) wird nicht verteilt und braucht diese Erklärung nicht.

**Vor dem Veröffentlichen ausfüllen:** alle `[Platzhalter]`; Text öffentlich hosten (z. B. GitHub Pages eines öffentlichen Repos oder eigene Domain) und in der Play Console sowie im SoundCloud-Anschreiben verlinken. Am besten von jemandem mit Datenschutz-Erfahrung gegenlesen lassen.

**Sobald die offizielle SoundCloud-Anbindung kommt (Roadmap 04), muss diese Erklärung erweitert werden** (Anmeldung, Token, übertragene Daten an SoundCloud).

---

## Deutsch

### Datenschutzerklärung für die App „AuraCurve“

**Verantwortlicher:** [Vorname Nachname], [Anschrift], [E-Mail-Adresse]

#### 1. Kurz gesagt

AuraCurve verarbeitet keine personenbezogenen Daten auf einem Server. Die App hat kein Nutzerkonto, keine Werbung, keine Analyse- oder Absturzmeldedienste und sendet nichts an den Entwickler. Alles, was die App speichert, bleibt auf deinem Gerät.

#### 2. Was die App auf deinem Gerät speichert

| Daten | Zweck |
|---|---|
| Equalizer-Einstellungen, Presets, eigene Kurven, Profile für Ausgabegeräte und Apps | Damit dein Klang erhalten bleibt |
| Playlists mit Titel, Interpret und Verweis auf die Audiodatei auf deinem Gerät | Für den eingebauten Player |
| Namen von Ausgabegeräten (z. B. Bluetooth-Kopfhörer), denen du ein Profil gibst | Um das passende Profil zu wählen |
| App-Einstellungen | Deine Vorlieben |

Diese Daten verlassen dein Gerät nicht über die App. Sie sind Teil der Android-Sicherung, wenn du sie auf deinem Gerät eingeschaltet hast; für eine Sicherung in der Cloud nur, wenn sie durch deine Bildschirmsperre verschlüsselt ist. Du löschst alle Daten, indem du die App-Daten in den Android-Einstellungen löschst oder die App deinstallierst.

#### 3. Audio

Der Klang wird ausschließlich auf deinem Gerät verarbeitet. AuraCurve nimmt nichts auf, speichert keine Audiodaten und lädt nichts hoch. Die App verwendet kein Mikrofon.

#### 4. Berechtigungen

| Berechtigung | Wofür |
|---|---|
| Benachrichtigungen | Anzeige der laufenden Wiedergabe und des Equalizer-Dienstes |
| Vordergrunddienst (Medienwiedergabe), Wake-Lock | Wiedergabe und Equalizer bei ausgeschaltetem Bildschirm |
| Start nach dem Hochfahren | Geplante Hintergrundaufgaben des Systems (Android WorkManager) |
| Internet, Netzwerkstatus | Von eingebundenen Android-Bibliotheken deklariert; die Store-Version stellt selbst keine Verbindung zu einem Server her |
| „Erweiterte App-Erkennung“ (DUMP) | Optional, nur über einen Computer per ADB erteilbar; liest, welche App gerade Audio abspielt, auf dem Gerät |

Die App sieht, welche Apps auf deinem Gerät installiert sind und gestartet werden können, um eine Audio-Sitzung der richtigen App zuzuordnen. Diese Liste wird nicht gespeichert und nicht übertragen.

#### 5. Android Auto

Wenn du AuraCurve mit Android Auto nutzt, zeigt dein Auto die Playlists und den laufenden Titel an. Dabei werden Daten nur zwischen deinem Telefon und dem Auto ausgetauscht, nicht mit uns.

#### 6. Dateien

AuraCurve liest nur Audio- und Preset-Dateien, die du selbst auswählst, und nur, um sie abzuspielen oder zu importieren.

#### 7. Deine Rechte

Da wir keine personenbezogenen Daten über dich erhalten, können wir dir keine Auskunft über gespeicherte Daten geben; sie liegen nur auf deinem Gerät, wo du sie selbst einsehen und löschen kannst. Du hast das Recht, dich bei einer Datenschutz-Aufsichtsbehörde zu beschweren, für [Bundesland] beim [Landesbeauftragten für den Datenschutz …].

#### 8. Kinder

Die App richtet sich nicht gezielt an Kinder unter 13 Jahren und erhebt keine Daten über sie.

#### 9. Änderungen

Ändert sich, was die App mit Daten macht (zum Beispiel durch eine Anbindung an einen Musikdienst), passen wir diese Erklärung vor der neuen Version an. Stand: [Datum].

---

## English

### Privacy policy for the app "AuraCurve"

**Controller:** [First name last name], [address], [email address]

**1. In short.** AuraCurve does not process personal data on any server. The app has no user account, no ads, no analytics or crash reporting and sends nothing to the developer. Everything the app stores stays on your device.

**2. What the app stores on your device:** equalizer settings, presets, your own curves, profiles for output devices and apps; playlists with title, artist and a reference to the audio file on your device; names of output devices (e.g. Bluetooth headphones) you give a profile; app settings. This data does not leave your device through the app. It is part of Android's backup if you have turned it on; cloud backup only when it is encrypted with your screen lock. Clearing the app's data in Android's settings or uninstalling the app deletes it.

**3. Audio.** Sound is processed on your device only. AuraCurve records nothing, stores no audio and uploads nothing. The app does not use the microphone.

**4. Permissions.** Notifications (playback and equalizer service); foreground service for media playback and wake lock (playback and equalizer with the screen off); run at startup (scheduled system tasks via Android WorkManager); internet and network state (declared by bundled Android libraries; the store version itself does not connect to any server); "extended app detection" (DUMP), optional and only grantable from a computer via ADB, to see on the device which app is playing audio. The app sees which apps on your device can be launched, to match an audio session to its app; this list is neither stored nor transmitted.

**5. Android Auto.** With Android Auto your car shows your playlists and the current track. Data is only exchanged between your phone and your car, not with us.

**6. Files.** AuraCurve reads only audio and preset files you pick yourself, and only to play or import them.

**7. Your rights.** As we receive no personal data about you, there is nothing we could disclose; the data lives on your device only, where you can view and delete it. You have the right to lodge a complaint with a data protection supervisory authority.

**8. Children.** The app is not directed at children under 13 and collects no data about them.

**9. Changes.** If what the app does with data changes (for example through a connection to a music service), this policy is updated before that version is released. Last updated: [date].

---

## Play Console: Datensicherheit (Vorschlag)

| Frage | Antwort für die Store-Variante |
|---|---|
| Erhebt oder teilt die App Nutzerdaten? | **Nein** (keine Daten verlassen das Gerät) |
| Daten bei der Übertragung verschlüsselt? | entfällt (keine Übertragung) |
| Können Nutzer das Löschen ihrer Daten beantragen? | entfällt; Löschen über App-Daten löschen/Deinstallieren |

## Offene Punkte und Empfehlung

- **Internet-Berechtigung:** Die Store-Variante enthält keinen Netzwerk-Code mehr, deklariert `INTERNET` und `ACCESS_NETWORK_STATE` aber weiter (Manifeste von `:app` und `:player`). Empfehlung: in einem Store-Manifest mit `tools:node="remove"` entfernen und am Gerät prüfen, dass Wiedergabe, Cover und Android Auto unverändert laufen. Dann kann Abschnitt 4 sagen: „AuraCurve hat keinen Internetzugriff.“ Noch nicht umgesetzt.
- Prüfen, ob Gerätenamen (Abschnitt 2) als personenbezogen gelten und ob die Datensicherheits-Angabe dazu passt.
- Hosting-Adresse festlegen; davon hängt auch das SoundCloud-Anschreiben ab (Platzhalter „Planned privacy policy“).
