# Roadmap 06 · Weiterentwicklung

**Enthält:** Phase 7 (nach dem Release)  
**Ziel:** Mehr Musik im eigenen Player, auf legalen Wegen, und Klangprofile, die sich ohne Handarbeit ergeben.  
**Stand:** 15.11.2026. Übersicht: [`00-uebersicht-und-leitplanken.md`](00-uebersicht-und-leitplanken.md)

> **Grenze:** Titel von Spotify, YouTube Music, Tidal & Co. laufen nur in deren eigenen Apps (Kopierschutz). AuraCurve erreicht sie über den Equalizer an der Audio-Sitzung, nicht im eigenen Player. Nachbauten wie librespot verstoßen gegen die Bedingungen und kommen nicht in Frage.

---

## Teil A · Mehr Quellen im eigenen Player

| # | Aufgabe | Aufwand | Wer | Status |
|---|---|---|---|---|
| 6.1 | **Internetradio** (Radio Browser): nur elektronische Sender, ein Genre zur Zeit (All electronic, Hardstyle, Hardcore, Techno, Trance, House, Drum & Bass, Dubstep, EDM), nur https, beliebteste zuerst, „Klick“ beim Abspielen. Reiter „Radio“. | M | Umsetzung | [~] umgesetzt in **AuraCurve Full** (PR #78); Gerätetest offen |
| 6.2 | **Audius**: Trending der Woche je Genre und Suche innerhalb des Genres (All electronic, Hardstyle, Hardcore, Hard Dance, Techno, Trance, House, Drum & Bass, Dubstep, Trap). Reiter „Audius“. | M | Umsetzung | [~] umgesetzt in **AuraCurve Full** (PR #78); Gerätetest offen |
| 6.3 | **Radio und Audius für die Store-Variante freigeben:** Nutzungsbedingungen von Audius im Original lesen (die Seite ist eine JavaScript-App und war aus der Arbeitsumgebung nicht lesbar; die Entwicklerdoku nennt einen freien Plan „ohne Einschränkungen“, 10 Anfragen/s), Datenschutzerklärung um Audius, Radio Browser und die Sender erweitern, Store-Texte anpassen. Erst dann in den Flavor `store` übernehmen. | S | du, Umsetzung | [ ] |
| 6.4 | **Speichern:** Radiosender als Favoriten, Audius-Titel in Playlists. Dafür müssen Playlists die Stream-Adresse direkter Quellen mitspeichern (heute nur die Titel-ID). | M | Umsetzung | [ ] |
| 6.5 | **Laufender Titel im Radio** (ICY-Metadaten des Streams) in Mini-Player, Benachrichtigung und Auto. | S–M | Umsetzung | [ ] |
| 6.6 | **Eigene Musiksammlung im Netz:** Jellyfin, Nextcloud/WebDAV oder DLNA als Quelle. | M–L | Umsetzung | [ ] |

### Umsetzung 6.1/6.2 (15.11.2026)

Freigegeben: nur https, zwei neue Reiter, ein Genre zur Zeit, zunächst nur abspielen (nicht speicherbar).

- **Wiedergabe:** Radio und Audius bekommen wie Gerätedateien eine eigene negative ID (`DirectStream` in `:core`) und spielen ihre https-Adresse direkt, ohne SoundCloud-Abfrage; HLS-Sender werden als HLS markiert. Plain `http://` spielt nie direkt.
- **Audius** spielt über `/v1/tracks/{id}/stream?app_name=AuraCurve`, das jedes Mal auf eine frisch signierte Adresse weiterleitet; nichts in der Warteschlange läuft ab. Höchstens drei Anfragen gleichzeitig, bei „429“ ein zweiter Versuch nach einer Pause (sonst überschreitet „All electronic“ mit elf Genres die 10 Anfragen/s).
- **Radio Browser** nach dessen Doku: sprechender User-Agent, zufälliger Server aus der Serverliste mit Ausweichen, `is_https=true`, `hidebroken`, Sortierung nach Klicks, ein „Klick“ beim Abspielen.
- **Code:** Clients und Parser in `:player` `src/full/.../catalog`, Reiter und ViewModels in `:app` `src/full/.../ui/player/catalog`; der gemeinsame Code kennt nur die Reiter-Schnittstelle `CatalogTabs`. Geladen wird erst, wenn ein Reiter zum ersten Mal offen ist.
- **Store-Variante:** enthält nichts davon; `check_store_apk.py` prüft zusätzlich auf `api.audius.co` und `radio-browser.info`.
- **Tests:** Parser gegen echte, gekürzte API-Antworten; ViewModels mit Fakes; Abspiel-Adressen; einmaliger Live-Aufruf beider Clients gegen die echten APIs (Radio „All“ 80 Sender, Audius „All electronic“ 80 Titel, Suche 45 Treffer), nicht im Repository.

## Teil B · Klangprofile automatisch

| # | Aufgabe | Aufwand | Wer | Status |
|---|---|---|---|---|
| 6.7 | **Großer AutoEQ-Katalog** (heute 10 Modelle in `BuiltInAutoEqCatalog`): einige hundert bis tausend gängige Kopfhörer, kompakt in der App, Lizenzen der Messquellen prüfen; bei eindeutigem Treffer automatisch übernehmen. | M | Umsetzung | [ ] |
| 6.8 | **Einmessen per Mikrofon** für Auto und Lautsprecher (Testsignal, Messung am Sitzplatz, Korrektur). Braucht die Mikrofon-Berechtigung und einen Absatz in der Datenschutzerklärung. | L | Umsetzung | [ ] |
