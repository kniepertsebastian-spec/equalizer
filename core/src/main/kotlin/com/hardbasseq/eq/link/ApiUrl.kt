package com.hardbasseq.eq.link

// SoundCloud's paged responses carry a `next_href` for the following page. Whether
// it already contains a client_id (possibly a stale one) is not something to rely
// on, so the id is always replaced by the current one before the request is made.
object ApiUrl {
    private val clientIdParam = Regex("([?&])client_id=[^&#]*&?")

    fun withClientId(
        url: String,
        clientId: String,
    ): String {
        val stripped = url.replace(clientIdParam) { match -> if (match.value.endsWith("&")) match.groupValues[1] else "" }
        val separator = if (stripped.contains('?')) "&" else "?"
        return "$stripped${separator}client_id=$clientId"
    }
}
