package com.hardbasseq.eq.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The sample lines imitate the layout of `dumpsys audio` as it is known from several Android versions.
// The real output varies by version and vendor; this is why the parser only looks for tokens.
class DumpsysAudioParserTest {
    private val dump =
        """
        Audio Service state
          mMode=NORMAL
        Playback activity:
          AudioPlaybackConfiguration piid:12 deviceIds:[2] role:media type:android.media.AudioTrack u/pid:10187/6021 state:started attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0x0 tags= bundle=null session:4711 mute reason:0
          AudioPlaybackConfiguration piid:13 deviceIds:[2] role:media type:android.media.MediaPlayer u/pid:10215/7342 state:paused attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0x0 tags= bundle=null session:4720
          AudioPlaybackConfiguration piid:14 type:android.media.AudioTrack u/pid:1041/900 state:started session:4800
          AudioPlaybackConfiguration piid:15 type:android.media.AudioTrack u/pid:10187/6021 state:started session:4711
          AudioPlaybackConfiguration piid:16 type:android.media.AudioTrack u/pid:10300/9000 state:started
          mSomething u/pid:10400/1 session:55
        """.trimIndent()

    @Test
    fun parsePlaybacks_findsAppPlayersWithTheirSession() {
        val playbacks = DumpsysAudioParser.parsePlaybacks(dump)

        assertEquals(listOf(4711, 4720), playbacks.map { it.sessionId })
        assertEquals(10187, playbacks.first().uid)
    }

    @Test
    fun parseStarted_dropsPausedPlayers() {
        val started = DumpsysAudioParser.parseStarted(dump)

        assertEquals(listOf(4711), started.map { it.sessionId })
        assertTrue(started.all { it.started })
    }

    @Test
    fun systemUidsAndLinesWithoutASessionAreIgnored() {
        val playbacks = DumpsysAudioParser.parsePlaybacks(dump)

        assertFalse("uid 1041 is the system", playbacks.any { it.uid == 1041 })
        assertFalse("no session on that line", playbacks.any { it.uid == 10300 })
        assertFalse("no state on that line", playbacks.any { it.uid == 10400 })
    }

    @Test
    fun sameSessionTwiceIsOneEntry() {
        assertEquals(1, DumpsysAudioParser.parsePlaybacks(dump).count { it.sessionId == 4711 })
    }

    @Test
    fun emptyOrUnrelatedTextGivesNothing() {
        assertTrue(DumpsysAudioParser.parsePlaybacks("").isEmpty())
        assertTrue(
            DumpsysAudioParser.parsePlaybacks("Permission Denial: can't dump audio from pid 1 requires android.permission.DUMP").isEmpty(),
        )
    }

    @Test
    fun alternativeSessionSpelling_isRecognised() {
        val text = "  AudioPlaybackConfiguration piid:1 u/pid:10500/1 state:started sessionId=77"

        assertEquals(77, DumpsysAudioParser.parseStarted(text).single().sessionId)
    }
}
