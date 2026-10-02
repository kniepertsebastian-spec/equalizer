# Verständlichkeitstest – Kernabläufe (UX-Roadmap, P2)

Zweck: prüfen, ob Nutzer ohne EQ-Vorkenntnisse die Kernabläufe ohne Anleitung schaffen, und Verständnisprobleme in
Beschriftungen und Navigation zurückspielen. **Durchführung steht aus** (braucht Testpersonen und ein Gerät; nicht
von Claude erledigbar). Dieses Protokoll ist die Vorlage.

## Aufbau

- 5 Testpersonen, davon mindestens 2 ohne EQ-Erfahrung; je Person 20 Minuten, Gerät mit aktueller Version.
- Gelesen wird nichts vor dem Test. Die Person denkt laut; die Testleitung greift nicht ein und notiert Zeit,
  Fehlgriffe und Zitate.
- Einmal in Deutsch, einmal in Englisch (Systemsprache), mit normaler und mit größter Schrift, mindestens eine
  Person mit TalkBack.

## Aufgaben

| # | Aufgabe | Erfolgskriterium |
|---|---|---|
| 1 | „Spiele einen Titel von Angerfist auf SoundCloud.“ (Quelle wechseln, suchen) | Suche im Player-Tab „Suchen“ gefunden, Titel läuft, < 60 s |
| 2 | „Importiere eine Playlist aus einem Link.“ | Tab „Playlists“, Link eingefügt, Playlist gefunden |
| 3 | „Zeig mir, was als Nächstes kommt.“ | Tab „Warteschlange“ geöffnet |
| 4 | „Schalte den Equalizer ein und sag mir, ob er wirklich wirkt.“ | Status „EQ aktiv für …“ gefunden und richtig gedeutet |
| 5 | „Der Gesang geht in der Musik unter – mach ihn präsenter.“ | Klangziel „Gesang vorne“ gewählt |
| 6 | „Vergleiche den Klang mit und ohne EQ.“ | „Original“/„EQ“ benutzt, Hinweis zur Lautstärke-Angleichung verstanden |
| 7 | „Wo meldest du dich bei SoundCloud/Spotify an oder ab?“ | „Verknüpfte Dienste“ gefunden |
| 8 | „Wo stellst du den Klang für deine Kopfhörer ein?“ | Bereich „Klangprofile“ gefunden |

## Fragen nach dem Test

1. Was ist der Unterschied zwischen „Verknüpfte Dienste“ und „Klangprofile“?
2. Was bedeutet „EQ aktiv für SoundCloud“? Wann würdest du ihm nicht trauen?
3. Was passiert, wenn du auf „Original“ tippst? Warum ist die Lautstärke ähnlich?
4. Welche Beschriftung war unklar oder hat dich in die Irre geführt?

## Auswertung

Je Aufgabe Erfolgsquote, Zeit und Fehlgriffe; Beschriftungen mit zwei oder mehr Fehlgriffen werden umformuliert,
Navigationspunkte, die zwei oder mehr Personen nicht fanden, umbenannt oder verlegt (Änderungen in
`app/src/main/res/values*/strings.xml`, beide Sprachen gleichzeitig).
