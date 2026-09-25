package com.hardbasseq.eq.settings

import kotlinx.coroutines.flow.Flow

interface AppSettingsRepository {
    // Null means "nothing saved yet, or what was saved didn't survive
    // validation" - callers fall back to their own defaults either way rather
    // than treating that as an error.
    val liveSettings: Flow<LiveSettings?>

    suspend fun save(liveSettings: LiveSettings)
}
