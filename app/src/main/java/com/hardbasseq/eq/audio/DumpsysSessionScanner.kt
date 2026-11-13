package com.hardbasseq.eq.audio

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds the audio sessions of apps that never announce them (roadmap 06, P5-2), by reading the
 * player list of `dumpsys audio`. Optional: it only works once the user granted
 * `android.permission.DUMP` over ADB,
 *
 *     adb shell pm grant com.hardbasseq.eq android.permission.DUMP
 *
 * which Android lets a user do for this one permission without root. Without the grant everything
 * stays as it was; nothing here asks for it or nags about it.
 */
interface SessionScanner {
    fun isAvailable(): Boolean

    /** Sessions of apps that are playing right now; empty if unavailable or unreadable. */
    suspend fun scan(): List<AudioSession>
}

object NoSessionScanner : SessionScanner {
    override fun isAvailable(): Boolean = false

    override suspend fun scan(): List<AudioSession> = emptyList()
}

@Singleton
class DumpsysSessionScanner
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : SessionScanner {
        override fun isAvailable(): Boolean = context.checkSelfPermission(DUMP_PERMISSION) == PackageManager.PERMISSION_GRANTED

        override suspend fun scan(): List<AudioSession> {
            if (!isAvailable()) return emptyList()
            return withContext(Dispatchers.IO) {
                runCatching {
                    val text = runDumpsys() ?: return@runCatching emptyList()
                    DumpsysAudioParser.parseStarted(text).mapNotNull { playback ->
                        val packageName = context.packageManager.getPackagesForUid(playback.uid)?.firstOrNull() ?: return@mapNotNull null
                        // The own player announces itself the regular way.
                        if (packageName == context.packageName) return@mapNotNull null
                        AudioSession(sessionId = playback.sessionId, packageName = packageName, active = true)
                    }
                }.getOrDefault(emptyList())
            }
        }

        private fun runDumpsys(): String? {
            val process = ProcessBuilder("dumpsys", "audio").redirectErrorStream(true).start()
            try {
                val output = process.inputStream.bufferedReader().use { it.readText() }
                if (!process.waitFor(DUMP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) return null
                return output
            } finally {
                process.destroy()
            }
        }

        private companion object {
            const val DUMP_PERMISSION = "android.permission.DUMP"
            const val DUMP_TIMEOUT_SECONDS = 5L
        }
    }
