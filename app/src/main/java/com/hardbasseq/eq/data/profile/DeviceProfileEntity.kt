package com.hardbasseq.eq.data.profile

import androidx.room.Entity
import androidx.room.PrimaryKey

// M3: "Profilwechsel bei Bluetooth-, USB- und Lautsprecherwechsel" - one row per
// route, remembering which voicing (boundPresetId) and correction curve
// (boundCorrectionProfileId, null = BuiltInCorrectionProfiles.None) were last
// selected while that route was active. routeId must be a STABLE fingerprint -
// see AndroidAudioRouteRepository.detectCurrentRoute() for why it isn't simply
// AudioDeviceInfo.getId() (documented as not persistent across sessions).
// boundCorrectionProfileId added without a schema version bump: no reader/writer
// existed for this table yet (same reasoning PresetEntity's own comment uses).
// headphoneAcousticsOverride (chat feature, item 1 of "setz alle Punkte um"):
// same reasoning applies - the app has no shipped release yet (pre-M7), so
// there is no installed schema this could break. null means "no explicit
// choice for this route yet, fall back to AudioDeviceType.defaultHeadphone-
// Acoustics()" - see MainViewModel.effectiveHeadphoneAcoustics.
@Entity(tableName = "device_profiles")
data class DeviceProfileEntity(
    @PrimaryKey val routeId: String,
    val routeType: String,
    val displayName: String,
    val boundPresetId: String,
    val boundCorrectionProfileId: String = "correction_none",
    val headphoneAcousticsOverride: Boolean? = null,
)
