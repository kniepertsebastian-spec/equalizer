package com.hardbasseq.eq.preset

import kotlinx.coroutines.flow.Flow

/**
 * Custom (user-saved) presets only - built-ins are compile-time constants
 * (BuiltInPresets.all) and never go through here. See PresetEntity for why.
 */
interface PresetRepository {
    val customPresets: Flow<List<Preset>>

    suspend fun save(preset: Preset)

    suspend fun delete(id: String)
}
