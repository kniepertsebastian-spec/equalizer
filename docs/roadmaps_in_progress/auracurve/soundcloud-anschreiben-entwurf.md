# SoundCloud-Anfrage · Entwurf (Roadmap 02, Aufgabe 1.5)

**Stand:** 15.11.2026, Entwurf. Vor dem Absenden:

- Alle `[Platzhalter]` füllen.
- Gegen die Bedingungen im Original abgleichen (Roadmap 02, Abschnitt 1.4). Jede Zusage unten muss dazu passen; was dort anders steht, gilt.
- Repository vorher auf privat stellen (Roadmap 02, 1.1).
- Video erst mit der Store-Variante aufnehmen (Roadmap 02, 1.3).

---

**Subject:** API access request – AuraCurve (Android equalizer & player, non-commercial)

Hello SoundCloud Developer team,

I am an independent developer from Germany and I am building **AuraCurve**, an Android app for people who want precise control over how their music sounds. Its core is a parametric equalizer with a live frequency-response curve, bass enhancement and per-device sound profiles. *Feel the bass. Rule the sound.*

**What I am asking for**

I would like to register AuraCurve as an app with access to the official SoundCloud API, so that signed-in users can:

- sign in with their own SoundCloud account (OAuth 2.1 with PKCE, system browser / Custom Tab),
- search for tracks and play them as streams,
- open their own likes and playlists.

Playback runs through the app's own player so the equalizer can be applied to the sound. Nothing else is requested.

**What I commit to**

- **No downloading, ripping or offline caching** of SoundCloud content; streams are only buffered as far as playback requires.
- **Attribution on every screen that shows SoundCloud content:** track title, artist name, artwork, "Powered by SoundCloud" / logo as specified, and a link back to the track on SoundCloud.
- **Non-commercial:** the app is free and shows no ads. [Anpassen, falls sich am Preismodell etwas ändert.]
- **No access to content the user may not play:** SoundCloud Go content is not requested through the API.
- **User data stays on the device:** the access token is stored encrypted on the phone; there is no AuraCurve server and no account data is shared with anyone. [Heute liegen Tokens noch unverschlüsselt; die Verschlüsselung kommt mit Roadmap 03. Die Zusage gilt für die offizielle Anbindung, die ohnehin neu gebaut wird.]
- The app follows the API Terms of Use and rate limits, and I will react to any request from SoundCloud promptly.

**Current state**

An earlier private prototype used SoundCloud's website endpoints. I stopped that approach and want to replace it with the official API before anything is published. The version I am showing you – and the only one that will be offered in Google Play – contains no SoundCloud access at all until the official API is approved. [Satz streichen, falls nach 1.4 nicht passend.]

**Attached**

- 60-second screen recording of AuraCurve (equalizer, curve, bass enhancement, local playback): [Link]
- Store feature graphic: [Link oder Anhang]
- Planned privacy policy: [Link] (Entwurf: `docs/PRIVACY_POLICY_DRAFT.md`, muss öffentlich gehostet sein)

**Contact**

[Name] · [E-Mail] · [Land/Stadt] · App ID (Android): `com.auracurve.app`

Thank you for considering the request. I am happy to answer questions or adjust the integration to your requirements.

Best regards,  
[Name]

---

## Ablauf für das 60-Sekunden-Video (Store-Variante)

| Sekunde | Bild | Zweck |
|---|---|---|
| 0–5 | Startbildschirm mit Name AuraCurve und Slogan | Wiedererkennung |
| 5–25 | Equalizer: Kurve ziehen, Punkte verschieben, Gain-Anzeige | Kern der App |
| 25–40 | Bassverstärkung an/aus, Vergleich A/B | Was die App besonders macht |
| 40–55 | Player mit einem eigenen Titel vom Gerät, EQ wirkt live | Zeigt, wohin SoundCloud-Streams kämen |
| 55–60 | Hinweis: „SoundCloud via official API – pending your approval“ | Klare Absicht |

Keine SoundCloud-Inhalte und kein SoundCloud-Logo im Video, solange keine Erlaubnis vorliegt.
