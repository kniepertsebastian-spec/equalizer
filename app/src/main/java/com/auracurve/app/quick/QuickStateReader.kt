package com.auracurve.app.quick

import com.auracurve.app.preset.BuiltInPresets
import com.auracurve.app.preset.PresetRepository
import com.auracurve.app.settings.AppSettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The state tiles and widget show: what the live view model publishes, or - when the app's screen
 * was never opened in this process - what was last saved.
 */
@Singleton
class QuickStateReader
    @Inject
    constructor(
        private val controls: QuickControls,
        private val settings: AppSettingsRepository,
        private val presets: PresetRepository,
    ) {
        suspend fun current(): QuickState? {
            controls.state.value?.let { return it }
            val saved = settings.liveSettings.first() ?: return null
            val name =
                (BuiltInPresets.all + presets.customPresets.first())
                    .firstOrNull { it.id == saved.activePresetId }
                    ?.name
                    .orEmpty()
            return QuickState(saved.processingSettings.masterEnabled, name)
        }
    }
