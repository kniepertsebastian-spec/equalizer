# Privacy and data handling

Draft. This file describes what the code of the **full** flavor (`com.auracurve.app.full`) does today. The store flavor (`com.auracurve.app`) has none of the SoundCloud, Spotify, YouTube or update traffic below; its draft privacy policy is [`PRIVACY_POLICY_DRAFT.md`](PRIVACY_POLICY_DRAFT.md). It is the technical basis for a privacy policy; it has not been reviewed legally and is not yet a privacy policy in the legal sense (controller, legal bases and rights are missing).

## Summary

- The app has no server of its own and no user accounts of its own.
- It contains no analytics, advertising or crash-reporting library.
- Audio is processed on the device. No audio is recorded, stored or uploaded.
- It talks to third-party servers only for the integrated player, link import and the update check.

## What is stored on the device

| Data | Where | Purpose |
|---|---|---|
| Presets, device profiles, curves | Room database | The equalizer's settings. |
| Playlists and their tracks (title, artist, cover address, track id) | DataStore | The player's saved playlists. |
| App settings | DataStore | Preferences. |
| SoundCloud sign-in token | Shared preferences (`sc_auth_prefs`) | Playing the signed-in user's library and full-length tracks. |
| Spotify sign-in (access and refresh token, the user's own client id) | Shared preferences (`spotify_auth`) | Reading the user's playlists for import. |
| Names of installed apps that can be launched | Read at runtime, not stored | Showing which app an audio session belongs to. |

The sign-in tokens are stored unencrypted in the app's private storage. Other apps cannot read them on a phone that is not rooted. They are included in Android's backup (cloud backup only when it is encrypted with the lock screen, see `app/src/main/res/xml/data_extraction_rules.xml`), so a restore on a new phone keeps the sign-in.

## Which servers the app contacts

| Server | When | What is sent |
|---|---|---|
| `soundcloud.com`, `api-v2.soundcloud.com` and SoundCloud's media servers | Using the integrated player with SoundCloud | Search terms, track and playlist ids, the sign-in token when signed in. |
| `accounts.spotify.com`, `api.spotify.com`, `open.spotify.com` | Importing a Spotify playlist or link | The link or playlist id, the sign-in token when signed in. |
| `www.youtube.com` | A shared YouTube link is matched to a SoundCloud track | The link, to read the title. |
| `api.audius.co` and Audius' content nodes | The Audius tab: trending, search, playback | The chosen genre or search term, the track id, the app name `AuraCurve`. |
| `*.api.radio-browser.info` and the radio stations' servers | The Radio tab and playing a station | The chosen genre, a "click" for the played station; the station gets the stream request. |
| `api.github.com`, `github.com` | Update check and download | Nothing about the user; the request reveals the IP address to GitHub. |
| Image servers of the above | Showing covers | The image address. |

A background job runs every six hours while a connection is available; once a new week has started it refreshes the list "Interesting new uploads" from SoundCloud. Every request reveals the device's IP address to the server it goes to; the app adds no identifier of its own.

## What the app does not do

- No tracking, no advertising id, no usage statistics.
- No access to the microphone, contacts, location or files other than audio files the user picks.
- No transfer of presets, playlists or tokens to the developer.

## Known weak points

- The SoundCloud sign-in in the current development version is not the official API: the sign-in happens in an embedded browser view and the session token is read from it. This is to be replaced by SoundCloud's official sign-in (see `roadmaps_in_progress/04-phase-1-2-offizielle-soundcloud-api.md`).
- Tokens are not encrypted with the Android Keystore.

## Before publishing

- Add the controller (name, address, contact), the legal bases and the rights of the people concerned.
- Host the policy at a public address and link it in the app and in the store listing.
- Fill in the Play Console data safety form to match this file.
