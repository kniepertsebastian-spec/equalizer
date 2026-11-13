package com.hardbasseq.eq.audio

/** One player the audio service lists as playing for an app (roadmap 06, P5-2). */
data class DumpedPlayback(
    val uid: Int,
    val sessionId: Int,
    val started: Boolean,
)

/**
 * Reads the player list out of `dumpsys audio`. That is how apps that never announce their audio
 * session by broadcast (many players, browsers, some music apps) can still be found; the app may
 * read it only after the user granted `android.permission.DUMP` over ADB.
 *
 * The text is not a stable API: its layout changes between Android versions and vendors. The parser
 * therefore looks for the few tokens it needs on a line (`u/pid:UID/PID`, `state:...`,
 * `session:N`) instead of a fixed layout, and skips every line that lacks one of them. Lines of
 * other sections of the dump are ignored for the same reason.
 */
object DumpsysAudioParser {
    private val uidPattern = Regex("""u/pid:(\d+)/(\d+)""")
    private val statePattern = Regex("""\bstate:(\w+)""")
    private val sessionPattern = Regex("""\bsession(?:Id)?[:=](\d+)""", RegexOption.IGNORE_CASE)

    // The uid space of regular apps starts at 10000 (Process.FIRST_APPLICATION_UID); lower ids are the system.
    private const val FIRST_APP_UID = 10_000

    fun parsePlaybacks(text: String): List<DumpedPlayback> =
        text
            .lineSequence()
            .mapNotNull(::parseLine)
            .distinctBy { it.sessionId }
            .toList()

    /** Playbacks that are playing right now. */
    fun parseStarted(text: String): List<DumpedPlayback> = parsePlaybacks(text).filter { it.started }

    private fun parseLine(line: String): DumpedPlayback? {
        val uid =
            uidPattern
                .find(line)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: return null
        val state = statePattern.find(line)?.groupValues?.get(1) ?: return null
        val session =
            sessionPattern
                .find(line)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: return null
        if (uid < FIRST_APP_UID || session <= 0) return null
        return DumpedPlayback(uid = uid, sessionId = session, started = state.equals("started", ignoreCase = true))
    }
}
