package com.auracurve.app.remote

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * A failed API call in the contract's error format (API-VERTRAG v0, section 2):
 * `{"ok":false,"error":{"code":"…","message":"…"}}`. [message] is for display only; the web
 * remote decides by [code]. Messages are German, like the web remote.
 */
class ApiError(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : Exception(message) {
    fun toJson(): String =
        buildJsonObject {
            put("ok", false)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }.toString()

    companion object {
        fun unauthorized() = ApiError(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Token fehlt oder ist falsch")

        fun badTicket() = ApiError(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Ticket fehlt, abgelaufen oder schon benutzt")

        fun rateLimited() = ApiError(HttpStatusCode.TooManyRequests, "RATE_LIMITED", "Zu viele Fehlversuche, bitte eine Minute warten")

        fun originNotAllowed() = ApiError(HttpStatusCode.Forbidden, "ORIGIN_NOT_ALLOWED", "Fremde Herkunft")

        fun notFound() = ApiError(HttpStatusCode.NotFound, "NOT_FOUND", "Unbekannter Pfad")

        fun methodNotAllowed() = ApiError(HttpStatusCode.MethodNotAllowed, "METHOD_NOT_ALLOWED", "Methode nicht erlaubt")

        fun internal() = ApiError(HttpStatusCode.InternalServerError, "INTERNAL", "Interner Fehler")
    }
}
