package com.hardbasseq.eq.audio.spike

import android.content.Context
import android.media.audiofx.Equalizer
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.TimeUnit

data class RootSessionZeroProbeResult(
    val rootGranted: Boolean,
    val attachSucceeded: Boolean,
    val detail: String,
)

data class RootAudioBand(
    val index: Int,
    val centerFrequencyHz: Int,
    val minGainDb: Float,
    val maxGainDb: Float,
)

data class RootAudioStartResult(
    val started: Boolean,
    val detail: String,
    val bands: List<RootAudioBand> = emptyList(),
)

/**
 * Experimental root bridge for output-mix Equalizer effects. A separate
 * app_process owns the effect as UID 0 and accepts a small line protocol over
 * stdin/stdout. This is device-dependent and not the production audio backend.
 */
class RootSessionZeroProbe(
    private val context: Context,
) {
    private val mutex = Mutex()
    private var activeProcess: Process? = null
    private var activeInput: BufferedReader? = null
    private var activeOutput: BufferedWriter? = null

    suspend fun run(timeoutSeconds: Long = 30): RootSessionZeroProbeResult =
        withContext(Dispatchers.IO) {
            val process = startProcess()
                ?: return@withContext RootSessionZeroProbeResult(
                    rootGranted = false,
                    attachSucceeded = false,
                    detail = "Could not start su or app_process.",
                )
            val input = process.inputStream.bufferedReader()
            val output = process.outputStream.bufferedWriter()
            try {
                output.send("PROBE")
                val result = input.readLine().orEmpty()
                output.send("EXIT")
                process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                RootSessionZeroProbeResult(
                    rootGranted = result.contains("ROOT_UID=0"),
                    attachSucceeded = result.startsWith("PROBE_OK"),
                    detail = result.ifBlank { "Root process exited without a result." },
                )
            } catch (e: Exception) {
                process.destroyForcibly()
                RootSessionZeroProbeResult(
                    rootGranted = false,
                    attachSucceeded = false,
                    detail = "Root probe failed: ${e.message ?: e}",
                )
            } finally {
                input.close()
                output.close()
                process.destroy()
            }
        }

    suspend fun startRootEqualizer(): RootAudioStartResult =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                stopProcess()
                val process = startProcess()
                    ?: return@withContext RootAudioStartResult(false, "Could not start su or app_process.")
                val input = process.inputStream.bufferedReader()
                val output = process.outputStream.bufferedWriter()
                output.send("START")
                val response = input.readLine().orEmpty()
                if (!response.startsWith("START_OK|")) {
                    output.send("EXIT")
                    process.waitFor(2, TimeUnit.SECONDS)
                    input.close()
                    output.close()
                    process.destroy()
                    return@withContext RootAudioStartResult(false, response.ifBlank { "No response from root process." })
                }

                activeProcess = process
                activeInput = input
                activeOutput = output
                RootAudioStartResult(
                    started = true,
                    detail = response,
                    bands = parseBands(response),
                )
            }
        }

    suspend fun setBandGain(
        bandIndex: Int,
        gainDb: Float,
    ): String =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                transact("SET|$bandIndex|${(gainDb * 100f).toInt()}")
            }
        }

    suspend fun stopRootEqualizer() =
        mutex.withLock {
            withContext(Dispatchers.IO) { stopProcess() }
        }

    private fun startProcess(): Process? {
        val apkPath = context.applicationInfo.sourceDir
        val command =
            "CLASSPATH=${shellQuote(apkPath)} app_process /system/bin " +
                RootSessionZeroProcess::class.java.name
        return runCatching {
            ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
        }.getOrNull()
    }

    private fun transact(command: String): String {
        val process = activeProcess ?: return "Root equalizer is not active."
        return runCatching {
            activeOutput?.send(command)
            activeInput?.readLine() ?: "Root process exited (code ${process.exitValue()})."
        }.getOrElse { "Root command failed: ${it.message ?: it}" }
    }

    private fun stopProcess() {
        val process = activeProcess ?: return
        runCatching { activeOutput?.send("STOP") }
        runCatching { process.waitFor(2, TimeUnit.SECONDS) }
        runCatching { activeInput?.close() }
        runCatching { activeOutput?.close() }
        process.destroy()
        if (process.isAlive) process.destroyForcibly()
        activeProcess = null
        activeInput = null
        activeOutput = null
    }

    private fun parseBands(response: String): List<RootAudioBand> {
        val values = response.split('|')
        if (values.size < 5) return emptyList()
        val minGain = values[2].toFloatOrNull()?.div(100f) ?: return emptyList()
        val maxGain = values[3].toFloatOrNull()?.div(100f) ?: return emptyList()
        return values.drop(4).mapIndexedNotNull { index, value ->
            value.toIntOrNull()?.let { centerMilliHz ->
                RootAudioBand(index, centerMilliHz / 1000, minGain, maxGain)
            }
        }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private fun BufferedWriter.send(command: String) {
        write(command)
        newLine()
        flush()
    }
}

/** app_process entry point. It never enables an effect for the PROBE command. */
object RootSessionZeroProcess {
    @JvmStatic
    fun main(args: Array<String>) {
        val input = System.`in`.bufferedReader()
        val output = System.out.bufferedWriter()
        var equalizer: Equalizer? = null
        val uid = Process.myUid()

        fun respond(value: String) {
            output.write(value.replace('\n', ' ').replace('\r', ' '))
            output.newLine()
            output.flush()
        }

        while (true) {
            val command = input.readLine() ?: break
            when {
                command == "EXIT" -> break
                command == "PROBE" -> {
                    if (uid != 0) {
                        respond("PROBE_FAIL|ROOT_UID=$uid|reason=not-root")
                    } else {
                        var probe: Equalizer? = null
                        try {
                            probe = Equalizer(0, 0)
                            respond("PROBE_OK|ROOT_UID=0|BANDS=${probe.numberOfBands}")
                        } catch (e: Exception) {
                            respond("PROBE_FAIL|ROOT_UID=0|reason=${e.message ?: e}")
                        } finally {
                            probe?.release()
                        }
                    }
                }
                command == "START" -> {
                    try {
                        if (uid != 0) error("not-root uid=$uid")
                        equalizer?.release()
                        val instance = Equalizer(0, 0)
                        val range = instance.bandLevelRange
                        val centers =
                            (0 until instance.numberOfBands.toInt()).joinToString("|") { index ->
                                instance.getCenterFreq(index.toShort()).toString()
                            }
                        instance.enabled = true
                        equalizer = instance
                        respond(
                            "START_OK|ROOT_UID=0|${range[0]}|${range[1]}|$centers",
                        )
                    } catch (e: Exception) {
                        equalizer?.release()
                        equalizer = null
                        respond("START_FAIL|ROOT_UID=$uid|reason=${e.message ?: e}")
                    }
                }
                command.startsWith("SET|") -> {
                    val parts = command.split('|')
                    val index = parts.getOrNull(1)?.toIntOrNull()
                    val gain = parts.getOrNull(2)?.toIntOrNull()
                    val effect = equalizer
                    if (index == null || gain == null || effect == null) {
                        respond("SET_FAIL|reason=invalid-state-or-parameters")
                    } else {
                        runCatching {
                            effect.setBandLevel(index.toShort(), gain.toShort())
                            "SET_OK|$index|$gain"
                        }.getOrElse { "SET_FAIL|reason=${it.message ?: it}" }.let(::respond)
                    }
                }
                command == "STOP" -> {
                    equalizer?.release()
                    equalizer = null
                    respond("STOP_OK")
                    break
                }
                else -> respond("ERROR|reason=unknown-command")
            }
        }
        equalizer?.release()
    }
}
