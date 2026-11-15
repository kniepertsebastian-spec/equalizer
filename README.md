# HardBass EQ

**The app is called "Baschtls Gedröhne" in the Android interface and on the home screen.**

HardBass EQ is the internal project name. The Android application ID and the package of every module (`:app`, `:core`, `:player`) is `com.auracurve.app` (the player module uses `com.auracurve.app.player`). The change from `com.hardbasseq.eq` makes this a new app for Android: it does not update an installation of the old ID, and presets/playlists do not move over automatically. The Spotify callback URI (`hardbasseq://spotify-callback`) keeps its original name so existing Spotify developer-app settings keep working.

An Android equalizer built for powerful, controlled bass, featuring an interactive frequency-response curve, customizable sound profiles, device-specific presets, and an integrated music player.

The project combines system-wide Android audio effects with a dedicated player featuring its own real-time audio processing pipeline.

## Features

### Equalizer

The interactive frequency-response curve is the central part of the application.

- Direct manipulation of frequency points.
- Customizable equalizer curves and presets.
- Sound profiles for different listening environments.
- Device-specific profiles for cars, speakers, and headphones.
- AutoEQ corrections for supported headphones and audio devices.

### System-Wide Audio Processing

HardBass EQ can attach Android audio effects to audio sessions reported by compatible applications.

- Equalizer processing.
- Android DynamicsProcessing integration.
- Support for Spotify and other compatible audio players.
- Automatic detection of available audio sessions.

Compatibility depends on the individual audio application, Android version, device manufacturer, and available audio-effect implementations.

### Integrated Music Player

The application includes an integrated player based on AndroidX Media3 / ExoPlayer.

The current development version includes an unofficial SoundCloud integration. A migration to the official SoundCloud API is planned, subject to SoundCloud's approval and applicable API requirements.

Planned and implemented player capabilities include:

- SoundCloud track search.
- Access to the authenticated user's likes and playlists.
- Integrated audio playback.
- Background playback and media controls.
- Android Auto support.
- Real-time audio processing directly in the playback pipeline.

The player includes several audio-processing components:

- Curve-based equalization.
- Subsonic filtering.
- Virtual Bass.
- Limiting and dynamics control.

Audio processing is designed to run locally in real time during playback.

### Context-Based Audio Profiles

HardBass EQ can detect the currently connected audio output and select an appropriate sound profile.

Supported profile categories include:

- Car audio.
- Speakers.
- Headphones.

### Background Service

A background service monitors audio sessions and waits for compatible applications to start playback.

To reduce unnecessary background activity, the service automatically stops after ten minutes without an active audio session. Opening the application starts it again.

## Project Structure

The project is organized into three main modules.

| Module | Description |
|---|---|
| `app` | Main Android application built with Kotlin, Jetpack Compose, Hilt, and Room. Contains the UI, audio engine, and services. |
| `core` | Platform-independent Kotlin logic, including presets, frequency-curve mathematics, DSP components, and link resolution. |
| `player` | Integrated media player library based on AndroidX Media3 / ExoPlayer, including SoundCloud-related functionality and Android Auto integration. |

## Device Requirements

| | Minimum | Recommended |
|---|---|---|
| Android version | Android 9 (API 28) | Android 13 or newer |
| Processor | Any 64-bit or 32-bit ARM or x86 device (the app ships no native code) | 64-bit ARM, 8 cores, 2020 or newer |
| Memory | 3 GB | 6 GB or more |
| Audio effects | A working `DynamicsProcessing` and `Equalizer` effect in the device's audio framework | Same; tested daily on a Pixel 10 with Android 16 |
| Network | Only for the integrated player's streaming, link import and update check | Wi-Fi or mobile data with a stable connection |
| Android Auto | Optional; Android Auto with "unknown sources" enabled while the app is installed from GitHub | Same |

Android 9 is the lower limit because the system-wide processing uses `DynamicsProcessing`, which Android added in API 28. The processor and memory figures are estimates, not measurements: the only device the app has been verified on is listed in `docs/TEST_MATRIX.md`. Details and the reasoning are in [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md).

## Build Requirements

- JDK 21
- Android SDK
- Android Studio (recommended)
- A configured Android SDK path in `local.properties`

## Building

Build a debug APK:

```bash
./gradlew :app:assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/
```

Run unit tests:

```bash
./gradlew testDebugUnitTest :core:test
```

Run code quality checks:

```bash
./gradlew ktlintCheck lintDebug
```

## Releases

Signed release APKs are generated through GitHub Actions.

A release build is triggered when a GitHub Release is published using a version tag such as:

```text
v0.2.1
```

The application is currently distributed privately through GitHub Releases. There are no current plans for Google Play Store distribution.

## Documentation

Project documentation is maintained in the `docs/` directory.

| File | Description |
|---|---|
| `docs/roadmaps_in_progress/00-INDEX.md` | The one valid work plan: index of the six open roadmap packages. |
| `docs/roadmaps_finished/` | Archive of finished and historical roadmaps (UX, 2026, engineering review, context/DSP, private group, session log). Kept for reference, not maintained. |
| `docs/DECISIONS.md` | Architecture decisions and implementation rationale. |
| `docs/ARCHITECTURE.md` | Audio engine and service architecture. |
| `docs/STATE_MACHINE.md` | Audio engine and background service state transitions. |
| `docs/TEST_MATRIX.md` | Device testing and test coverage, including outstanding tests. |
| `docs/DEPENDENCIES.md` | Library and dependency versions. |
| `docs/adr/` | Architecture Decision Records. |
| `docs/REQUIREMENTS.md` | Minimum and recommended device requirements, with the reason for each. |
| `docs/PRIVACY.md` | What the app stores, which servers it contacts and what it does not collect (draft of the privacy policy). |
| `docs/AUDIO_IMPROVEMENTS.md`, `docs/HIGH_RES_EQ_IMPROVEMENTS.md` | Notes on audio-session detection and the high-resolution equalizer. |

The repository root holds only the build files, this README, the license and the instruction files for coding assistants (`CLAUDE.md`, `GEMINI.md`, `skills-lock.json`). Everything else is under `docs/`.

## SoundCloud Integration

The current development version uses an unofficial SoundCloud access implementation.

An official integration using SoundCloud's supported authentication and API endpoints is planned.

This migration is subject to SoundCloud's approval and compliance with its API Terms of Use, content restrictions, and licensing requirements.

The project does not claim to be an official SoundCloud application or to be affiliated with or endorsed by SoundCloud.

Any future SoundCloud functionality will be subject to applicable access restrictions, attribution requirements, and usage policies.

## Privacy and Data Handling

The application is designed to process audio locally on the Android device.

Account authentication and access to third-party services are handled separately from the system-wide equalizer functionality.

The app contains no analytics, advertising or crash-reporting library and has no server of its own. What it stores on the device, which servers it contacts and how sign-in tokens are handled is described in [`docs/PRIVACY.md`](docs/PRIVACY.md). That file is a draft and has not been reviewed legally.

## Disclaimer

HardBass EQ is an independent Android project.

Spotify and SoundCloud are trademarks of their respective owners. Their names are used solely to identify supported or planned integrations.

HardBass EQ is not an official Spotify or SoundCloud application and is not affiliated with, endorsed by, or sponsored by either service.

## Project Status

HardBass EQ is under active development.

Features, device compatibility, audio processing behavior, and third-party integrations may change between releases.

The project is currently distributed privately through GitHub Releases.
