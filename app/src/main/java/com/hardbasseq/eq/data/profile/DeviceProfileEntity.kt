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
@Entity(tableName = "device_profiles")
data class DeviceProfileEntity(
    @PrimaryKey val routeId: String,
    val routeType: String,
    val displayName: String,
    val boundPresetId: String,
    val boundCorrectionProfileId: String = "correction_none",
)
