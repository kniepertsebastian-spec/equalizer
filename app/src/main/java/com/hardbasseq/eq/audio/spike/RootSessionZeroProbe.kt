package com.hardbasseq.eq.audio.spike

import android.content.Context
import android.media.audiofx.Equalizer
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class RootSessionZeroProbeResult(
    val rootGranted: Boolean,
    val attachSucceeded: Boolean,
    val detail: String,
)

/**
 * Starts a short-lived Android runtime through `su` and asks it to construct,
 * but never enable, an Equalizer on output session 0. This is an engineering
 * probe only; it does not provide an active system-wide equalizer backend.
 */
class RootSessionZeroProbe(
    private val context: Context,
) {
    suspend fun run(timeoutSeconds: Long = 30): RootSessionZeroProbeResult =
        withContext(Dispatchers.IO) {
            val apkPath = context.applicationInfo.sourceDir
            val command =
                "CLASSPATH=${shellQuote(apkPath)} app_process /system/bin " +
                    RootSessionZeroProcess::class.java.name
            val process =
                try {
                    ProcessBuilder("su", "-c", command)
                        .redirectErrorStream(true)
                        .start()
                } catch (e: Exception) {
                    return@withContext RootSessionZeroProbeResult(
                        rootGranted = false,
                        attachSucceeded = false,
                        detail = "Could not start su: ${e.message ?: e}",
                    )
                }

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@withContext RootSessionZeroProbeResult(
                    rootGranted = false,
                    attachSucceeded = false,
                    detail = "Timed out waiting for root approval or probe result.",
                )
            }

            val output =
                process.inputStream
                    .bufferedReader()
                    .use { it.readText() }
                    .trim()
            val rootGranted = output.contains("ROOT_UID=0")
            val succeeded = output.contains("ATTACH=OK")
            RootSessionZeroProbeResult(
                rootGranted = rootGranted,
                attachSucceeded = succeeded,
                detail = output.ifBlank { "su exited with code ${process.exitValue()} and no output." },
            )
        }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}

/** Entry point loaded by app_process while running as root. */
object RootSessionZeroProcess {
    @JvmStatic
    fun main(args: Array<String>) {
        val uid = Process.myUid()
        if (uid != 0) {
            println("ROOT_UID=$uid ATTACH=SKIPPED reason=not-root")
            return
        }

        var equalizer: Equalizer? = null
        try {
            equalizer = Equalizer(0, 0)
            println("ROOT_UID=0 ATTACH=OK BANDS=${equalizer.numberOfBands}")
        } catch (e: Exception) {
            println("ROOT_UID=0 ATTACH=FAIL reason=${e.message ?: e}")
        } finally {
            equalizer?.release()
        }
    }
}
