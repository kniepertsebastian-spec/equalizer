# Device requirements

What a phone needs to run the app, and why. The minimum is what the code enforces or depends on. The recommendation is an estimate: the app has only been verified on one device (see `TEST_MATRIX.md`), and there are no measurements of processor load or memory use yet.

## Minimum

| Requirement | Value | Reason |
|---|---|---|
| Android version | Android 9 (API 28) | `minSdk = 28` in `app/build.gradle.kts`. The system-wide processing uses `DynamicsProcessing`, which exists from API 28 on. |
| Processor architecture | Any (ARM 32/64-bit, x86) | The app is Kotlin only and ships no native library. |
| Memory | 3 GB | Estimate. The player decodes and processes one stream; Compose and Media3 need room next to the music app the equalizer attaches to. |
| Storage | About 100 MB free | Estimate: the APK, the Room database (presets, device profiles), the saved playlists (DataStore) and the image cache. The app stores no music. |
| Audio framework | `Equalizer` and `DynamicsProcessing` effects available | Some manufacturers ship a changed or missing implementation. The diagnostics screen lists the effects the device reports. |
| Network | None for the equalizer | Only the integrated player (streaming, link import) and the update check use the network. |
| Permissions | Notifications (Android 13+) | Needed for the foreground service notification and the media controls. |

## Recommended

| Requirement | Value | Reason |
|---|---|---|
| Android version | Android 13 or newer | Verified on Android 16 (Pixel 10). Android 12 and newer encrypt cloud backups with the lock screen, which the backup rules rely on for the sign-in tokens. |
| Processor | 64-bit ARM, 8 cores, 2020 or newer | Estimate. The player's chain (curve, virtual bass, multiband compressor, limiter) runs in real time on the audio thread; an older or throttled processor can stutter with the screen off. |
| Memory | 6 GB or more | Estimate. Keeps the foreground service and the music app alive side by side. |
| Battery settings | App exempt from battery optimisation on phones with aggressive power management | Otherwise the system may stop the session service and the effect is lost until the app is opened again. |
| Output | Wired or USB audio, or Bluetooth with a stable codec | Bluetooth absolute volume and codec changes can alter the level behind the equalizer. |

## What limits the system-wide equalizer

These are limits of Android, not of the device's performance:

- The effect can only attach to apps that announce an audio session. For other apps the optional `DUMP` permission (granted once over ADB) lets the app find the session.
- Android offers no supported way to process all audio of the device in one place. The effect is attached per session.
- Manufacturer sound enhancements (Dolby, "Adapt Sound" and similar) run in addition and can change the result.

## Android Auto

- Android Auto on the phone, Android 9 or newer.
- While the app is installed from GitHub and not from the Play Store, Android Auto only shows it with "Unknown sources" enabled in Android Auto's developer settings.

## Open

- Measure processor load and memory on at least one low-end and one mid-range device, then replace the estimates above.
- Extend `TEST_MATRIX.md` beyond the Pixel 10: one Samsung, one Xiaomi, one device with Android 9 to 11.
