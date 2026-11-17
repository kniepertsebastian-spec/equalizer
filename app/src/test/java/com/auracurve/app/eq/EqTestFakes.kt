package com.auracurve.app.eq

import com.auracurve.app.audio.AudioEffectDescriptor
import com.auracurve.app.audio.AudioEffectRepository
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.AudioRouteRepository
import com.auracurve.app.audio.AudioSession
import com.auracurve.app.audio.AudioSessionRepository
import com.auracurve.app.audio.KnownEffectTypeIds
import com.auracurve.app.audio.SystemVolumeRepository
import com.auracurve.app.auto.CarConnectionRepository
import com.auracurve.app.correction.CorrectionProfile
import com.auracurve.app.correction.CorrectionProfileRepository
import com.auracurve.app.data.profile.AppProfileEntity
import com.auracurve.app.data.profile.DeviceProfileEntity
import com.auracurve.app.integration.PlayerBridge
import com.auracurve.app.integration.PlayerSource
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.PresetRepository
import com.auracurve.app.preset.TargetPoint
import com.auracurve.app.profile.AppProfileRepository
import com.auracurve.app.profile.DeviceProfileRepository
import com.auracurve.app.settings.AppSettingsRepository
import com.auracurve.app.settings.LiveSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

// Fakes for the E-008 stage 2 tests (characterization and EqController). Same behavior as the
// private fakes in MainViewModelTest, which stay untouched.

class EqFakeEffectRepository(
    private val descriptors: List<AudioEffectDescriptor> = emptyList(),
) : AudioEffectRepository {
    override val knownEffectTypeIds =
        KnownEffectTypeIds(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

    override fun queryAvailableEffects(): List<AudioEffectDescriptor> = descriptors
}

class EqFakeCarConnectionRepository : CarConnectionRepository {
    override val carConnected = MutableStateFlow(false)
}

class EqFakeAppProfileRepository : AppProfileRepository {
    private val profiles = MutableStateFlow<List<AppProfileEntity>>(emptyList())
    override val allProfiles: Flow<List<AppProfileEntity>> = profiles

    override suspend fun getProfile(packageName: String): AppProfileEntity? = profiles.value.firstOrNull { it.packageName == packageName }

    override suspend fun saveProfile(
        packageName: String,
        boundPresetId: String,
        boundCorrectionProfileId: String,
    ) {
        profiles.value = profiles.value.filterNot { it.packageName == packageName } +
            AppProfileEntity(packageName, boundPresetId, boundCorrectionProfileId)
    }

    override suspend fun deleteProfile(packageName: String) {
        profiles.value = profiles.value.filterNot { it.packageName == packageName }
    }
}

class EqFakeSessionRepository : AudioSessionRepository {
    override val sessions = MutableStateFlow<List<AudioSession>>(emptyList())
    override val activeSession = MutableStateFlow<AudioSession?>(null)

    override fun startListening() = Unit

    override fun stopListening() = Unit

    override fun setActiveSession(session: AudioSession?) {
        activeSession.value = session
    }
}

class EqFakeRouteRepository : AudioRouteRepository {
    private val _activeRoute = MutableStateFlow(AudioRoute())
    override val activeRoute: StateFlow<AudioRoute> = _activeRoute.asStateFlow()

    fun setRoute(route: AudioRoute) {
        _activeRoute.value = route
    }

    override fun startMonitoring() = Unit

    override fun stopMonitoring() = Unit
}

class EqFakeVolumeRepository : SystemVolumeRepository {
    private val _currentLevelDb = MutableStateFlow(0f)
    override val currentLevelDb: StateFlow<Float> = _currentLevelDb.asStateFlow()

    fun setLevelDb(levelDb: Float) {
        _currentLevelDb.value = levelDb
    }

    override fun startMonitoring() = Unit

    override fun stopMonitoring() = Unit
}

class EqFakePlayerBridge : PlayerBridge {
    var lastVirtualBassMix: Float? = null
    var lastPlayerCurve: List<TargetPoint> = emptyList()
    var lastSubsonicCutoffHz: Float = 0f
    var launchCount = 0
    var stopCount = 0

    override fun launchPlayer(source: PlayerSource) {
        launchCount++
    }

    override fun stopPlayer() {
        stopCount++
    }

    override fun togglePlayback() = Unit

    override fun setPlayerCurve(
        curve: List<TargetPoint>,
        subsonicCutoffHz: Float,
    ) {
        lastPlayerCurve = curve
        lastSubsonicCutoffHz = subsonicCutoffHz
    }

    override fun setVirtualBassMix(mix: Float) {
        lastVirtualBassMix = mix
    }
}

class EqFakePresetRepository(
    initial: List<Preset> = emptyList(),
) : PresetRepository {
    private val presets = MutableStateFlow(initial)
    override val customPresets: Flow<List<Preset>> = presets
    val savedPresets = mutableListOf<Preset>()

    override suspend fun save(preset: Preset) {
        savedPresets.add(preset)
        presets.value = presets.value.filterNot { it.id == preset.id } + preset
    }

    override suspend fun delete(id: String) {
        presets.value = presets.value.filterNot { it.id == id }
    }
}

class EqFakeAppSettingsRepository(
    initial: LiveSettings? = null,
) : AppSettingsRepository {
    private val settings = MutableStateFlow(initial)
    override val liveSettings: Flow<LiveSettings?> = settings
    val savedSettings = mutableListOf<LiveSettings>()

    override suspend fun save(liveSettings: LiveSettings) {
        savedSettings.add(liveSettings)
        settings.value = liveSettings
    }
}

class EqFakeCorrectionProfileRepository(
    initial: List<CorrectionProfile> = emptyList(),
) : CorrectionProfileRepository {
    private val profiles = MutableStateFlow(initial)
    override val customProfiles: Flow<List<CorrectionProfile>> = profiles

    override suspend fun save(profile: CorrectionProfile) {
        profiles.value = profiles.value.filterNot { it.id == profile.id } + profile
    }

    override suspend fun delete(id: String) {
        profiles.value = profiles.value.filterNot { it.id == id }
    }
}

class EqFakeDeviceProfileRepository(
    initial: Map<String, DeviceProfileEntity> = emptyMap(),
) : DeviceProfileRepository {
    private val byRoute = initial.toMutableMap()
    private val allFlow = MutableStateFlow(initial.values.toList())
    override val allProfiles: Flow<List<DeviceProfileEntity>> = allFlow
    val savedBindings = mutableListOf<DeviceProfileEntity>()

    override suspend fun getProfileForRoute(routeId: String): DeviceProfileEntity? = byRoute[routeId]

    override suspend fun saveProfile(
        routeId: String,
        routeType: String,
        displayName: String,
        boundPresetId: String,
        boundCorrectionProfileId: String,
        headphoneAcousticsOverride: Boolean?,
        contextMode: String?,
    ) {
        val entity =
            DeviceProfileEntity(
                routeId,
                routeType,
                displayName,
                boundPresetId,
                boundCorrectionProfileId,
                headphoneAcousticsOverride,
                contextMode,
            )
        byRoute[routeId] = entity
        savedBindings.add(entity)
        allFlow.value = byRoute.values.toList()
    }
}
