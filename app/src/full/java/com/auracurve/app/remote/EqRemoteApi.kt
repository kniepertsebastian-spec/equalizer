package com.auracurve.app.remote

import com.auracurve.app.R
import com.auracurve.app.context.SoundContext
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
) {
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
        }

    suspend fun state(): JsonObject = withContext(mainDispatcher) { stateNow() }

    /** The full EQ state whenever any part of it changes, from the app, a tile or this API. */
    fun changes(): Flow<JsonObject> =
        combine(
            controller.processingSettings.map { it.masterEnabled to it.bypass }.distinctUntilChanged(),
            controller.activePreset,
            controller.activeGoal,
            controller.activeContext,
            controller.contextFollowsRoute,
        ) { _, _, _, _, _ -> stateNow() }.distinctUntilChanged()

    suspend fun setBypass(body: JsonObject): JsonObject {
        val enabled = body.bool("enabled")
        return change { controller.setBypass(enabled) }
    }

    suspend fun setPreset(body: JsonObject): JsonObject {
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
        }
    }

    companion object {
        fun GenrePreset.apiId(): String = id.removePrefix("genre_").uppercase()

        fun SoundContext.labelRes(): Int =
            when (this) {
                SoundContext.CAR -> R.string.context_name_car
                SoundContext.BLUETOOTH_SPEAKER -> R.string.context_name_speaker
            }

        fun notReady() = ApiError(HttpStatusCode.ServiceUnavailable, "ENGINE_NOT_READY", "Der EQ ist noch nicht gestartet")

        fun unknown(message: String) = ApiError(HttpStatusCode.BadRequest, "UNKNOWN_VALUE", message)

        fun badRequest(message: String) = ApiError(HttpStatusCode.BadRequest, "BAD_REQUEST", message)
    }
}

/** A required boolean field, else 400 BAD_REQUEST. */
internal fun JsonObject.bool(name: String): Boolean =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: throw EqRemoteApi.badRequest("$name fehlt")

/** A string field, or null. */
internal fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
