# Audio Session Detection & Sound Quality Improvements

## Overview

This document describes recent improvements to automatic audio session detection and sound quality in HardBass EQ.

## Changes Made

### 1. Automatic Audio Playback Detection (NEW)

**Problem**: The EQ service was shutting down after 10 minutes of idle time (no active audio session + app not visible). Once stopped, you had to manually open the app to restart it, even when starting music playback.

**Solution**: Implemented system-wide audio playback monitoring using `AudioManager.AudioPlaybackCallback` (Android 8.0+) that:
- Detects when **any** app starts playing audio (Spotify, YouTube, etc.)
- Keeps the EQ service alive while audio is playing, even without an audio session
- Gives apps time to broadcast their session-open intent
- Only shuts down after 10 minutes of **no audio playback** AND no session AND app not visible

**Files Changed**:
- `app/src/main/java/com/hardbasseq/eq/audio/AudioPlaybackMonitor.kt` (NEW)
- `app/src/main/java/com/hardbasseq/eq/di/AudioModule.kt`
- `app/src/main/java/com/hardbasseq/eq/service/AudioSessionForegroundService.kt`

**How it works**:
1. The foreground service now monitors system-wide audio playback
2. When any app starts playing audio, the service stays alive
3. This gives music apps time to send the `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcast
4. The EQ automatically attaches to the session when it's broadcast
5. The service only stops after 10 minutes of complete silence

### 2. Sound Quality Notes

**Recent Fix (Commit 48ea99b)**: The sound quality was **improved** with several fixes:
- Fixed mono-bass notch at 120 Hz (was canceling bass frequencies)
- Smoother limiter with 120ms release (was 50ms)
- Raised input gain safety ratio to 0.5 (from 0.1) to prevent limiter crushing

**Current Behavior**:
- `INPUT_GAIN_SAFETY_RATIO = 0.5`: The input gain pre-cancels 50% of the peak EQ boost
- This prevents the limiter from working too hard and crushing dynamics
- The limiter still protects against clipping with a 10:1 ratio ceiling

**If you prefer more aggressive/punchier sound**:
- The current 0.5 ratio is a balanced compromise
- Lower values (e.g., 0.3) give more punch but may cause limiter distortion
- Higher values (e.g., 0.8) give more headroom but less impact

## Testing the Changes

### 1. Test Automatic Session Detection

1. **Build and install** the updated app on your Android device
2. **Open the app** and enable the EQ
3. **Navigate to the Diagnostics screen** (usually in settings or main menu)
4. **Close the app** (swipe it away from recents)
5. **Wait 30 seconds**, then **start playing music on Spotify**
6. **Open the app again** and check the Diagnostics screen

**Expected behavior**:
- You should see logs like:
  - `System-wide audio playback: started`
  - `Audio effect control session opened: <session_id>`
  - `Session attached: id=<session_id> package=<package_name>`
  - `Engine state: Active(<session_id>)`

### 2. Test Sound Quality

1. **Play a bass-heavy track** (e.g., hardstyle, drum & bass)
2. **Enable a bass-heavy preset** (e.g., one with +10dB at low frequencies)
3. **Listen for**:
   - Clean, punchy bass (not distorted/flattened)
   - No notch/cancellation around 120 Hz
   - Smooth limiting without pumping

### 3. Check Service Behavior

**Before the fix**:
- Service would stop after 10 minutes of no session
- Music apps would play without EQ until you opened the app again

**After the fix**:
- Service stays alive while any audio is playing
- EQ automatically attaches when session is available
- Only stops after 10 minutes of complete audio silence

## Diagnostics

The app has a built-in diagnostics screen that shows:
- Current engine state (Active, Listening, Detached, Error, etc.)
- Audio route (headphones, speaker, Bluetooth)
- Recent events (last 50):
  - Session attached/detached
  - Route changes
  - Playback state changes
  - Engine state transitions

**How to access diagnostics**:
- Usually found in: Settings → Diagnostics
- Or: Main screen → Menu → Diagnostics

## Known Limitations

1. **API Level**: Audio playback monitoring requires Android 8.0+ (API 26)
   - On older devices, the old behavior remains (manual app restart needed)

2. **Battery Impact**: Monitoring audio playback has minimal battery impact
   - Only active while the service is running
   - Service still stops after 10 minutes of silence to save battery

3. **Session Broadcasts**: Some apps may not properly broadcast audio session intents
   - This is an app-specific issue, not something we can fix
   - Most major apps (Spotify, YouTube Music, etc.) work correctly

## Troubleshooting

### EQ Not Activating Automatically

1. **Check notification permission** (Android 13+)
   - Settings → Apps → HardBass EQ → Permissions → Notifications
   - Must be enabled to show the foreground service notification

2. **Check diagnostics screen**:
   - Look for "System-wide audio playback: started"
   - If missing, the monitoring isn't working

3. **Check battery optimization**:
   - Settings → Apps → HardBass EQ → Battery → Unrestricted
   - Some manufacturers aggressively kill background services

### Sound Quality Issues

1. **Check input gain**: Look at the diagnostics screen
   - Should show negative input gain when using EQ boost
   - E.g., +10dB bass → approximately -5dB input gain

2. **Try disabling high-resolution EQ**:
   - Equalizer screen → High-resolution EQ toggle
   - Some devices don't support the 34-band pre-EQ

3. **Check limiter settings**:
   - Limiter should be enabled by default
   - Threshold: -1.0 dB
   - If disabled, clipping may occur

## Technical Details

### AudioPlaybackMonitor Implementation

```kotlin
interface AudioPlaybackMonitor {
    val isAnyAppPlaying: StateFlow<Boolean>
    fun startMonitoring()
    fun stopMonitoring()
}
```

**Monitors**:
- All `AudioPlaybackConfiguration` objects from `AudioManager`
- Filters for active playback (not silent, not unknown usage)
- Emits state changes via a Flow

**Used by**:
- `AudioSessionForegroundService`: Keeps service alive during playback
- Future features: Could trigger automatic preset switching, logging, etc.

### Service Lifecycle

```
App Start
   ↓
Service Start → Monitor Audio Playback
   ↓
Audio Starts → Stay Alive
   ↓
Session Broadcast → Attach EQ
   ↓
Audio Stops → Start 10min Countdown
   ↓
10min Elapsed + App Closed → Stop Service
```

## Future Improvements

Potential enhancements:
1. **Configurable timeout**: Let users set idle timeout (5/10/15/30 minutes)
2. **Persistent mode**: Option to never auto-stop (battery warning required)
3. **Per-app presets**: Different EQ settings per music app
4. **Smart detection**: Detect music genre and suggest presets

## Questions?

If you experience issues:
1. Check the diagnostics screen first
2. Copy the diagnostics report (button in top-right)
3. Report issues with the diagnostics included

## Changelog

- **2026-11-10**: Added AudioPlaybackMonitor and improved service lifecycle
- **2026-10-29** (Commit 48ea99b): Fixed mono-bass notch, improved limiter, tuned dynamics
