package com.hardbasseq.eq.profile

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// The user's own name for an audio device ("Sony WH-1000XM5" -> "My headphones"), keyed by
// the stable route id. Only changes what the app shows; the device profile itself is untouched.
interface DeviceAliasStore {
    val aliases: StateFlow<Map<String, String>>

    // A blank name removes the alias again.
    fun setAlias(
        routeId: String,
        name: String,
    )
}

class InMemoryDeviceAliasStore : DeviceAliasStore {
    private val state = MutableStateFlow<Map<String, String>>(emptyMap())
    override val aliases: StateFlow<Map<String, String>> = state.asStateFlow()

    override fun setAlias(
        routeId: String,
        name: String,
    ) {
        val trimmed = name.trim()
        state.value = if (trimmed.isEmpty()) state.value - routeId else state.value + (routeId to trimmed)
    }
}

@Singleton
class SharedPreferencesDeviceAliasStore
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
    ) : DeviceAliasStore {
        private val prefs = context.getSharedPreferences("device_aliases", Context.MODE_PRIVATE)
        private val state = MutableStateFlow(prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap())
        override val aliases: StateFlow<Map<String, String>> = state.asStateFlow()

        override fun setAlias(
            routeId: String,
            name: String,
        ) {
            val trimmed = name.trim()
            prefs.edit().apply { if (trimmed.isEmpty()) remove(routeId) else putString(routeId, trimmed) }.apply()
            state.value = if (trimmed.isEmpty()) state.value - routeId else state.value + (routeId to trimmed)
        }
    }
