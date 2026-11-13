# HardBass EQ

**The app is called "Baschtls Gedröhne" in the Android interface and on the home screen.**

HardBass EQ is the internal project name. The Android package ID (`com.hardbasseq.eq`), source files, module names, and existing Spotify callback URI (`hardbasseq://spotify-callback`) retain their original names to preserve compatibility with existing installations, updates, and Spotify integration settings.

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

## Requirements

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
| `docs/roadmap-ux.md` | Current UI and product development plan. |
| `docs/roadmap-2026.md` | Product and technical roadmap for 2026. |
| `docs/DECISIONS.md` | Architecture decisions and implementation rationale. |
| `docs/ARCHITECTURE.md` | Audio engine and service architecture. |
| `docs/STATE_MACHINE.md` | Audio engine and background service state transitions. |
| `docs/TEST_MATRIX.md` | Device testing and test coverage, including outstanding tests. |
| `docs/DEPENDENCIES.md` | Library and dependency versions. |
| `docs/adr/` | Architecture Decision Records. |
| `docs/roadmap.md` | Development session log. |
| `roadmap.md` | Technical review and development roadmap. |
| `roadmap-kontext-dsp.md` | Roadmap for context-based profiles and DSP. |
| `docs/roadmap-private.md` | Roadmap for the private user group. |
| `docs/roadmap-private-status.md` | Current status of the private user group roadmap. |

## SoundCloud Integration

The current development version uses an unofficial SoundCloud access implementation.

An official integration using SoundCloud's supported authentication and API endpoints is planned.

This migration is subject to SoundCloud's approval and compliance with its API Terms of Use, content restrictions, and licensing requirements.

The project does not claim to be an official SoundCloud application or to be affiliated with or endorsed by SoundCloud.

Any future SoundCloud functionality will be subject to applicable access restrictions, attribution requirements, and usage policies.

## Privacy and Data Handling

The application is designed to process audio locally on the Android device.

Account authentication and access to third-party services are handled separately from the system-wide equalizer functionality.

Details about data collection, token handling, network requests, and third-party services should be documented in a dedicated privacy policy before wider distribution.

## Disclaimer

HardBass EQ is an independent Android project.

Spotify and SoundCloud are trademarks of their respective owners. Their names are used solely to identify supported or planned integrations.

HardBass EQ is not an official Spotify or SoundCloud application and is not affiliated with, endorsed by, or sponsored by either service.

## Project Status

HardBass EQ is under active development.

Features, device compatibility, audio processing behavior, and third-party integrations may change between releases.

The project is currently distributed privately through GitHub Releases.
