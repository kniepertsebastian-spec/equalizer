package com.auracurve.app.player.playback

import android.content.Context
import com.auracurve.app.dsp.PlayerDspSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * What the player's audio chain reads (PlayerDspAudioProcessor, on ExoPlayer's playback thread,
 * once per buffer): the output-stage switches, the exact curve and the virtual bass amount.
 * Each read must be cheap and lock-free.
 */
interface PlayerDspSource {
    val currentSettings: PlayerDspSettings
    val curve: PlayerCurve
    val virtualBassMix: Float

    companion object {
        /** The process-wide objects (PlayerDspState, PlayerCurveState, VirtualBassState). */
        val ProcessWide: PlayerDspSource =
            object : PlayerDspSource {
                override val currentSettings: PlayerDspSettings get() = PlayerDspState.settings.value
                override val curve: PlayerCurve get() = PlayerCurveState.current
                override val virtualBassMix: Float get() = VirtualBassState.mix
            }
    }
}

/**
 * The player's DSP settings, curve and virtual bass behind one interface (roadmap 11 P11-4, step
 * 1-2 of ADR 0004): code that reads or writes them takes a store instead of reaching for the
 * global objects, so a test hands in an [InMemoryPlayerDspStore] and leaves no state behind for
 * the next one. [SharedPlayerDspStore] is the app's store, still backed by those objects
 * because AudioPlayerService is not Hilt-managed yet (see ADR 0004).
 */
interface PlayerDspStore : PlayerDspSource {
    val settings: StateFlow<PlayerDspSettings>

    override val currentSettings: PlayerDspSettings get() = settings.value

    /** Reads the saved settings, off the main thread; harmless to call more than once. */
    suspend fun load()

    fun updateSettings(transform: (PlayerDspSettings) -> PlayerDspSettings)

    fun setCurve(curve: PlayerCurve)

    /** 0 = off, 1 = full; values outside are clamped. */
    fun setVirtualBassMix(mix: Float)
}

class SharedPlayerDspStore(
    context: Context,
) : PlayerDspStore {
    private val appContext = context.applicationContext

    override val settings: StateFlow<PlayerDspSettings> get() = PlayerDspState.settings
    override val curve: PlayerCurve get() = PlayerCurveState.current
    override val virtualBassMix: Float get() = VirtualBassState.mix

    override suspend fun load() = withContext(Dispatchers.IO) { PlayerDspState.load(appContext) }

    override fun updateSettings(transform: (PlayerDspSettings) -> PlayerDspSettings) = PlayerDspState.update(appContext, transform)

    override fun setCurve(curve: PlayerCurve) {
        PlayerCurveState.current = curve
    }

    override fun setVirtualBassMix(mix: Float) {
        VirtualBassState.mix = mix.coerceIn(0f, 1f)
    }
}

/** Keeps everything to itself and saves nothing: for tests, and anything that must not touch the player's shared state. */
class InMemoryPlayerDspStore(
    initial: PlayerDspSettings = PlayerDspSettings(),
) : PlayerDspStore {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<PlayerDspSettings> = state.asStateFlow()

    @Volatile
    override var curve: PlayerCurve = PlayerCurve(emptyList(), 0f)
        private set

    @Volatile
    override var virtualBassMix: Float = 0f
        private set

    override suspend fun load() = Unit

    override fun updateSettings(transform: (PlayerDspSettings) -> PlayerDspSettings) {
        state.value = transform(state.value)
    }

    override fun setCurve(curve: PlayerCurve) {
        this.curve = curve
    }

    override fun setVirtualBassMix(mix: Float) {
        virtualBassMix = mix.coerceIn(0f, 1f)
    }
}
