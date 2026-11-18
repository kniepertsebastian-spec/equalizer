package com.auracurve.app.remote

import com.auracurve.app.R
import com.auracurve.app.context.SoundContext
import com.auracurve.app.dsp.ParametricBand
import com.auracurve.app.dsp.ParametricCurve
import com.auracurve.app.dsp.ParametricFilterBounds
import com.auracurve.app.eq.CompareSide
import com.auracurve.app.eq.EqController
import com.auracurve.app.preset.BuiltInGenrePresets
import com.auracurve.app.preset.GenrePreset
import com.auracurve.app.preset.PresetIntensity
import com.auracurve.app.preset.PresetIntensityResolver
import com.auracurve.app.preset.SoundGoal
import com.auracurve.app.text.TextProvider
import com.auracurve.app.ui.equalizer.nameRes
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The EQ part of the remote-control API (Pixel-Car module 04b). Every call goes to the
 * [EqController], never to a view model, so it works with the app closed. The controller lives on
 * the main thread: calls switch to [mainDispatcher] first.
 *
 * Ids are the names from the code (contract section 1.3): genres as their id without "genre_" in
 * upper case (TERROR, UPTEMPO, ...), intensities, goals and contexts as their enum names.
 */
class EqRemoteApi(
    private val controller: EqController,
    private val texts: TextProvider,
    private val mainDispatcher: CoroutineDispatcher,
    /** Runs the comparison's safety timer (04f); null in tests that do not need it. */
    private val scope: CoroutineScope? = null,
    private val compareTimeoutMs: Long = COMPARE_TIMEOUT_MS,
) {
    // A comparison started over the API: ended by itself after [compareTimeoutMs] or when the last
    // web remote disconnects, so a dropped connection never leaves the original playing.
    private var remoteCompare: Job? = null

    /** False until the controller has loaded the saved state: the endpoints answer 503 then. */
    val ready: Boolean get() = controller.isRestored

    fun options(): JsonObject =
        buildJsonObject {
            putJsonArray("genres") {
                BuiltInGenrePresets.all.forEach {
                    addJsonObject {
                        put("id", it.apiId())
                        put("label", it.displayName)
                    }
                }
            }
            putJsonArray("intensities") {
                PresetIntensity.entries.forEach {
                    addJsonObject {
                        put("id", it.name)
                        put("label", it.displayName)
                    }
                }
            }
            putJsonArray("goals") {
                SoundGoal.entries.forEach {
                    addJsonObject {
                        put("id", it.name)
                        put("label", texts.get(it.nameRes()))
                    }
                }
            }
            putJsonArray("contexts") {
                SoundContext.entries.forEach {
                    addJsonObject {
                        put("id", it.name)
                        put("label", texts.get(it.labelRes()))
                    }
                }
            }
            // 04f: every preset the app offers, built-in and the user's own, in the app's order.
            putJsonArray("presets") {
                controller.allPresets.value.forEach {
                    addJsonObject {
                        put("id", it.id)
                        put("label", it.name)
                        put("builtIn", it.metadata.builtIn)
                    }
                }
            }
            put("highResolutionAvailable", controller.highResolutionAvailable)
            putJsonObject("limits") {
                val bands = controller.capabilities.value.bands
                putJsonObject("bandGainDb") {
                    put("min", bands.maxOfOrNull { it.minGainDb } ?: 0f)
                    put("max", bands.minOfOrNull { it.maxGainDb } ?: 0f)
                }
                putJsonObject("parametric") {
                    put("maxBands", ParametricCurve.MAX_BANDS)
                    putJsonObject("frequencyHz") {
                        put("min", ParametricFilterBounds.MIN_FREQUENCY_HZ)
                        put("max", ParametricFilterBounds.MAX_FREQUENCY_HZ)
                    }
                    putJsonObject("gainDb") {
                        put("min", ParametricFilterBounds.MIN_GAIN_DB)
                        put("max", ParametricFilterBounds.MAX_GAIN_DB)
                    }
                    putJsonObject("q") {
                        put("min", ParametricFilterBounds.MIN_Q)
                        put("max", ParametricFilterBounds.MAX_Q)
                    }
                }
            }
        }

    suspend fun state(): JsonObject = withContext(mainDispatcher) { stateNow() }

    /** The full EQ state whenever any part of it changes, from the app, a tile or this API. */
    fun changes(): Flow<JsonObject> =
        combine(
            listOf(
                controller.processingSettings,
                controller.activePreset,
                controller.activeGoal,
                controller.activeContext,
                controller.contextFollowsRoute,
                controller.isDirty,
                controller.parametricBands,
                controller.compare,
                controller.capabilities,
            ),
        ) { stateNow() }.distinctUntilChanged()

    suspend fun setBypass(body: JsonObject): JsonObject {
        val enabled = body.bool("enabled")
        return change { controller.setBypass(enabled) }
    }

    suspend fun setPreset(body: JsonObject): JsonObject {
        body.string("presetId")?.let { id ->
            val preset = controller.allPresets.value.firstOrNull { it.id == id } ?: throw unknown("Unbekanntes Preset")
            return change { controller.selectPreset(preset) }
        }
        val genre = BuiltInGenrePresets.all.firstOrNull { it.apiId() == body.string("genre") } ?: throw unknown("Unbekanntes Genre")
        val intensity =
            PresetIntensity.entries.firstOrNull { it.name == body.string("intensity") } ?: throw unknown("Unbekannte Intensität")
        return change { controller.selectGenreIntensity(genre, intensity) }
    }

    suspend fun setGoal(body: JsonObject): JsonObject {
        val goal = SoundGoal.entries.firstOrNull { it.name == body.string("goal") } ?: throw unknown("Unbekanntes Ziel")
        return change { controller.setSoundGoal(goal) }
    }

    /** `{"context":"CAR"}`, `{"context":null}` (off) or `{"auto":true}` (back to the route's detection). */
    suspend fun setContext(body: JsonObject): JsonObject {
        if ((body["auto"] as? JsonPrimitive)?.booleanOrNull == true) return change { controller.setContextAutomatic() }
        if (!body.containsKey("context")) throw unknown("Unbekannter Kontext")
        val raw = body["context"]
        val context =
            if (raw is JsonNull) {
                null
            } else {
                SoundContext.entries.firstOrNull { it.name == (raw as? JsonPrimitive)?.contentOrNull }
                    ?: throw unknown("Unbekannter Kontext")
            }
        return change { controller.setContextMode(context) }
    }

    /** `{"link":"auracurve://preset?..."}`: becomes a user preset and is selected. Returns `{id,label}`. */
    suspend fun importPreset(body: JsonObject): JsonObject {
        if (!ready) throw notReady()
        val link = body.string("link") ?: throw badRequest("link fehlt")
        val preset =
            withContext(mainDispatcher) { controller.importPresetLink(link) }.getOrElse {
                throw ApiError(HttpStatusCode.BadRequest, "INVALID_LINK", "Der Preset-Link ist ungültig")
            }
        return buildJsonObject {
            put("id", preset.id)
            put("label", preset.name)
        }
    }

    /** `{"name":"…"}`, 1 to 40 characters: the current sound as a new user preset. Returns `{id,label}`. */
    suspend fun savePreset(body: JsonObject): JsonObject {
        if (!ready) throw notReady()
        val name = body.string("name")?.trim().orEmpty()
        if (name.isEmpty() || name.length > MAX_PRESET_NAME) throw badRequest("Der Name muss 1 bis $MAX_PRESET_NAME Zeichen lang sein")
        val preset = withContext(mainDispatcher) { controller.saveAsNewPreset(name) }
        return buildJsonObject {
            put("id", preset.id)
            put("label", preset.name)
        }
    }

    /** Discards the own changes, like "Reset" in the app. */
    suspend fun reset(): JsonObject = change { controller.selectPreset(controller.activePreset.value) }

    /** The 34-band pre-EQ on or off (U-4: only that). The engine rebuilds the effect: a short gap is expected. */
    suspend fun setHighResolution(body: JsonObject): JsonObject {
        val enabled = body.bool("enabled")
        if (enabled && !controller.highResolutionAvailable) {
            throw ApiError(HttpStatusCode.Conflict, "SOURCE_UNAVAILABLE", "Dieses Gerät kann den High-Quality-EQ nicht anlegen")
        }
        return change { controller.setHighResolutionEq(enabled) }
    }

    /** `{"index":2,"gainDb":-3.5}`: one classic band; outside the device's limits is 400, never clamped. */
    suspend fun setBand(body: JsonObject): JsonObject {
        val index = body.number("index")?.takeIf { it % 1.0 == 0.0 }?.toInt() ?: throw badRequest("index fehlt")
        val gain = body.number("gainDb")?.toFloat() ?: throw badRequest("gainDb fehlt")
        val band =
            controller.capabilities.value.bands
                .firstOrNull { it.index == index } ?: throw outOfRange("Unbekanntes Band")
        if (gain !in band.minGainDb..band.maxGainDb) throw outOfRange("gainDb außerhalb ${band.minGainDb} … ${band.maxGainDb}")
        return change { controller.setBandGain(index, gain) }
    }

    /** `{"bands":[{"frequencyHz","gainDb","q"}]}`: the whole list (idempotent), at most 8, all inside the limits. */
    suspend fun setParametric(body: JsonObject): JsonObject {
        val list = body["bands"] as? JsonArray ?: throw badRequest("bands fehlt")
        if (list.size > ParametricCurve.MAX_BANDS) throw outOfRange("Höchstens ${ParametricCurve.MAX_BANDS} Bänder")
        val bands =
            list.map { element ->
                val band = element as? JsonObject ?: throw badRequest("Band muss ein Objekt sein")
                val frequency = band.number("frequencyHz")?.toFloat() ?: throw badRequest("frequencyHz fehlt")
                val gain = band.number("gainDb")?.toFloat() ?: throw badRequest("gainDb fehlt")
                val q = band.number("q")?.toFloat() ?: throw badRequest("q fehlt")
                if (frequency !in
                    ParametricFilterBounds.MIN_FREQUENCY_HZ..ParametricFilterBounds.MAX_FREQUENCY_HZ
                ) {
                    throw outOfRange("frequencyHz außerhalb der Grenzen")
                }
                if (gain !in
                    ParametricFilterBounds.MIN_GAIN_DB..ParametricFilterBounds.MAX_GAIN_DB
                ) {
                    throw outOfRange("gainDb außerhalb der Grenzen")
                }
                if (q !in ParametricFilterBounds.MIN_Q..ParametricFilterBounds.MAX_Q) throw outOfRange("q außerhalb der Grenzen")
                ParametricBand(frequencyHz = frequency, gainDb = gain, q = q)
            }
        return change { controller.replaceParametricBands(bands) }
    }

    /**
     * "Tap for original" (U-5, loudness-matched): `{"side":"ORIGINAL"}`, `{"side":"EQ"}` or
     * `{"side":null}` to end. Ends by itself after [compareTimeoutMs].
     */
    suspend fun setCompare(body: JsonObject): JsonObject {
        if (!body.containsKey("side")) throw badRequest("side fehlt")
        val raw = body["side"]
        val side =
            if (raw is JsonNull) {
                null
            } else {
                CompareSide.entries.firstOrNull { it.name == (raw as? JsonPrimitive)?.contentOrNull } ?: throw unknown("Unbekannte Seite")
            }
        return change {
            remoteCompare?.cancel()
            remoteCompare = null
            if (side == null) {
                controller.endCompare()
            } else {
                controller.setCompareSide(side)
                remoteCompare =
                    scope?.launch {
                        delay(compareTimeoutMs)
                        withContext(mainDispatcher) { endRemoteCompareNow() }
                    }
            }
        }
    }

    /** The last web remote went away: a comparison it started must not stay on the original. */
    suspend fun onAllClientsGone() = withContext(mainDispatcher) { endRemoteCompareNow() }

    private fun endRemoteCompareNow() {
        val job = remoteCompare ?: return
        remoteCompare = null
        job.cancel()
        controller.endCompare()
    }

    private suspend fun change(action: () -> Unit): JsonObject {
        if (!ready) throw notReady()
        return withContext(mainDispatcher) {
            action()
            stateNow()
        }
    }

    private fun stateNow(): JsonObject {
        val settings = controller.processingSettings.value
        val preset = controller.activePreset.value
        val pair = PresetIntensityResolver.parse(preset.id, BuiltInGenrePresets.all)
        return buildJsonObject {
            put("masterEnabled", settings.masterEnabled)
            put("bypass", settings.bypass)
            putJsonObject("preset") {
                if (pair != null) {
                    put("genre", pair.first.apiId())
                    put("intensity", pair.second.name)
                } else {
                    put("presetId", preset.id)
                }
            }
            put("goal", controller.activeGoal.value.name)
            put("context", controller.activeContext.value?.name)
            put("contextAuto", controller.contextFollowsRoute.value)
            // 04f
            put("presetId", preset.id)
            put("dirty", controller.isDirty.value)
            put("highResolution", settings.highResolutionEq)
            putJsonArray("bands") {
                controller.capabilities.value.bands.forEach { band ->
                    addJsonObject {
                        put("index", band.index)
                        put("frequencyHz", band.centerFreqHz)
                        put("gainDb", settings.bandGainsDb[band.index] ?: 0f)
                    }
                }
            }
            putJsonArray("parametric") {
                controller.parametricBands.value.forEach { band ->
                    addJsonObject {
                        put("frequencyHz", band.frequencyHz)
                        put("gainDb", band.gainDb)
                        put("q", band.q)
                    }
                }
            }
            val compare = controller.compare.value
            if (compare == null) put("compare", null as String?) else putJsonObject("compare") { put("side", compare.side.name) }
        }
    }

    companion object {
        fun GenrePreset.apiId(): String = id.removePrefix("genre_").uppercase()

        fun SoundContext.labelRes(): Int =
            when (this) {
                SoundContext.CAR -> R.string.context_name_car
                SoundContext.BLUETOOTH_SPEAKER -> R.string.context_name_speaker
                SoundContext.SOUNDBAR -> R.string.context_name_soundbar
                SoundContext.IN_EAR -> R.string.context_name_in_ear
            }

        fun notReady() = ApiError(HttpStatusCode.ServiceUnavailable, "ENGINE_NOT_READY", "Der EQ ist noch nicht gestartet")

        fun unknown(message: String) = ApiError(HttpStatusCode.BadRequest, "UNKNOWN_VALUE", message)

        fun badRequest(message: String) = ApiError(HttpStatusCode.BadRequest, "BAD_REQUEST", message)

        fun outOfRange(message: String) = ApiError(HttpStatusCode.BadRequest, "OUT_OF_RANGE", message)

        const val COMPARE_TIMEOUT_MS = 120_000L
        const val MAX_PRESET_NAME = 40
    }
}

/** A required boolean field, else 400 BAD_REQUEST. */
internal fun JsonObject.bool(name: String): Boolean =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: throw EqRemoteApi.badRequest("$name fehlt")

/** A number field (not a string), or null. */
internal fun JsonObject.number(name: String): Double? = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

/** A string field, or null. */
internal fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
