# Roadmap 04 – Offizielle SoundCloud-API und OAuth 2.1

**Modell-Empfehlung:** Opus 5.5 für Architektur und Sicherheitsprüfung; Sonnet 5.5 kann klar abgegrenzte Implementierungsschritte übernehmen.  
**Ziel:** P1-2 der ursprünglichen Roadmap vorbereiten und – nur nach Freigabe – implementieren.  
**Harte Voraussetzung:** Vor Implementierung schriftliche Klärung mit SoundCloud, insbesondere zur Drittanbieter-Wiedergabe und lokalen Echtzeit-DSP-Verarbeitung.

## P1-2 Zielbild: offizielle SoundCloud-API mit OAuth 2.1 und PKCE (L)

- Anmeldung in einem Chrome Custom Tab, Rücksprung über `hardbasseq://soundcloud-callback`. Das ist dasselbe Muster wie bei `SpotifyLoginActivity`. Im echten Browser funktioniert die Google-Anmeldung, weil SoundCloud dort der Vertragspartner von Google ist.
- Voraussetzung: eigene App bei SoundCloud registrieren (`client_id`, `client_secret`). Eingabe der eigenen Zugangsdaten in „Verknüpfte Dienste“, wie heute bei Spotify.
- `SoundCloudClient` auf `api.soundcloud.com` umstellen: `/me`, Likes, Playlists, Suche, `/tracks/{id}/streams`.
- Access-Token läuft nach etwa einer Stunde ab; Refresh-Token ist einmalig verwendbar. Erneuerung mit Sperre gegen parallele Anfragen bauen.
- Bekannte Einschränkung: Die offizielle API liefert für Go+-Katalogtitel voraussichtlich nur Vorschau oder gar keinen Stream. Vor der Umstellung mit dem eigenen Konto testen, welche regelmäßig gehörten Titel betroffen sind.
- Endpunkte, Berechtigungen, Limits und aktuelle Authentifizierungsanforderungen beim Umsetzen gegen die offizielle Entwicklerdokumentation prüfen.

## Vorbedingungen – nicht überspringen
1. SoundCloud schriftlich um Freigabe des konkreten Anwendungsfalls bitten: integrierter Drittanbieter-Player, Suche, Likes, Playlists, Stream-Wiedergabe und lokale Echtzeit-EQ/DSP-Verarbeitung.
2. Klären, ob private Verteilung über GitHub Releases zulässig ist und ob eine separate Vereinbarung erforderlich ist.
3. OAuth-Client-Modell und Umgang mit einem möglicherweise erforderlichen Client Secret verbindlich klären. Kein vertrauliches Secret in die Android-App einbetten.
4. Go+-Beschränkungen, Attribution, Android Auto und weitere API-Vorgaben klären.
5. Ergebnisse und erlaubten Umfang in `docs/DECISIONS.md` dokumentieren.

## Implementierung
Erst nach positiver schriftlicher Klärung und geklärter Credential-Architektur beginnen. Danach Authentifizierung, Callback, Token-Speicherung/-Erneuerung und API-Endpunkte in getrennten, testbaren Schritten migrieren.

## Abschlusskriterien
- Die offizielle API wird statt Scraping, Web-Cookies, aus Webseiten extrahierten `client_id`-Werten oder inoffiziellen API-Endpunkten verwendet.
- OAuth-Flow und Refresh-Token-Rotation sind gegen parallele Anfragen abgesichert.
- Keine Client Secrets oder Tokens gelangen in Quellcode, Logs oder Release-Artefakte.
- Nicht verfügbare und nicht autorisierte Inhalte werden korrekt behandelt; keine Umgehung von Go+- oder sonstigen Zugriffsbeschränkungen.
- Attribution und Links erfüllen die schriftlich bestätigten SoundCloud-Vorgaben.
- Tests, Build, Lint und echte Login-/Playback-Gerätetests sind dokumentiert.

## Abbruchregel
Wenn SoundCloud den Use Case nicht freigibt oder die notwendige sichere OAuth-Architektur nicht angeboten werden kann, keine alternative Scraping-Lösung als Ersatz implementieren. Entscheidung dokumentieren und SoundCloud-Playback zurückstellen.
