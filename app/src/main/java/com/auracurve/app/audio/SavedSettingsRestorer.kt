package com.auracurve.app.audio

import com.auracurve.app.diagnostics.DiagnosticsRecorder
import com.auracurve.app.settings.AppSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * E-008 stage 1: puts the user's saved curve on the engine when the process starts without the
 * app on screen (quick tile, Android Auto, WorkManager after boot). Only MainViewModel applies the
 * saved settings otherwise, so without an activity the system EQ ran with factory defaults.
 *
 * Applies only while the engine still holds the defaults, so it never overrides settings a
 * running MainViewModel has already applied. Unlike MainViewModel.restoreSavedState it does not
 * look up the saved preset and does not push virtual bass: the system EQ needs only the saved
 * processing settings, virtual bass belongs to the built-in player.
 */
@Singleton
class SavedSettingsRestorer
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
        private val audioEngine: AudioEngine,
        private val diagnosticsRecorder: DiagnosticsRecorder,
    ) {
        /** Returns true if the saved settings were applied. */
        suspend fun restoreIfEngineUntouched(): Boolean {
            if (audioEngine.currentSettings.value != ProcessingSettings()) return false
            val saved =
                try {
                    appSettingsRepository.liveSettings.first()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Damaged saved data must not stop the service; the engine keeps its defaults.
                    diagnosticsRecorder.record("Saved settings not restored: ${e.javaClass.simpleName}")
                    return false
                } ?: return false
            // Checked again: MainViewModel may have applied its settings while the read was running.
            if (audioEngine.currentSettings.value != ProcessingSettings()) return false
            return audioEngine.apply(saved.processingSettings)
        }
    }
