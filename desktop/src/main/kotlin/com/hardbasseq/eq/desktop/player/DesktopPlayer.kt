package com.hardbasseq.eq.desktop.player

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/** Decodes any stream the JVM can read to signed 16-bit little-endian PCM. */
internal fun toPcm16(stream: AudioInputStream): AudioInputStream {
    val base = stream.format
    val alreadyPcm16 =
        base.encoding == AudioFormat.Encoding.PCM_SIGNED &&
            base.sampleSizeInBits == 16 &&
            !base.isBigEndian
    if (alreadyPcm16) return stream
    val rate = if (base.sampleRate > 0f) base.sampleRate else DEFAULT_SAMPLE_RATE
    val target =
        AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, base.channels, base.channels * 2, rate, false)
    return AudioSystem.getAudioInputStream(target, stream)
}

private const val DEFAULT_SAMPLE_RATE = 44100f
private const val CHUNK_FRAMES = 4096

/**
 * Pushes [source] (PCM16 little-endian) through [chain] chunk by chunk and hands
 * every processed chunk to [sink]. Returns early once [keepGoing] is false.
 */
internal fun pumpThroughChain(
    source: AudioInputStream,
    chain: () -> DesktopDspChain,
    keepGoing: () -> Boolean = { true },
    sink: (ByteArray, Int) -> Unit,
) {
    val format = source.format
    val frameBytes = format.frameSize
    val channels = format.channels
    val rate = format.sampleRate.toInt()
    val buffer = ByteArray(CHUNK_FRAMES * frameBytes)
    var activeChain: DesktopDspChain? = null
    var carry = 0
    while (keepGoing()) {
        val read = source.read(buffer, carry, buffer.size - carry)
        if (read < 0) break
        val bytes = carry + read
        val usable = bytes - bytes % frameBytes
        // The chain may be swapped while playing (new preset): start it clean.
        val current = chain()
        if (current !== activeChain) {
            current.configure(channels, rate)
            activeChain = current
        }
        val pcm = ByteBuffer.wrap(buffer, 0, usable).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = ShortArray(usable / 2)
        pcm.get(samples)
        current.process(samples)
        pcm.rewind()
        pcm.put(samples)
        sink(buffer, usable)
        carry = bytes - usable
        if (carry > 0) System.arraycopy(buffer, usable, buffer, 0, carry)
    }
}

/** Renders a whole stream offline; used by tests and handy for exporting a processed file. */
fun renderThroughChain(
    stream: AudioInputStream,
    chain: DesktopDspChain,
): ByteArray {
    val out = ByteArrayOutputStream()
    pumpThroughChain(toPcm16(stream), { chain }) { bytes, count -> out.write(bytes, 0, count) }
    return out.toByteArray()
}

/**
 * Plays a file on the default output through a [DesktopDspChain]. Formats the
 * JVM can decode out of the box are WAV, AIFF and AU; MP3/FLAC need a JavaX
 * Sound service provider on the classpath.
 */
class DesktopPlayer {
    @Volatile private var chain: DesktopDspChain? = null

    @Volatile private var running = false
    private var worker: Thread? = null
    private var line: SourceDataLine? = null

    val isPlaying: Boolean get() = running

    /** Takes effect immediately, also while a file is playing. */
    fun setChain(newChain: DesktopDspChain) {
        chain = newChain
    }

    fun play(
        file: File,
        initialChain: DesktopDspChain,
        onFinished: (Throwable?) -> Unit = {},
    ) {
        stop()
        chain = initialChain
        running = true
        worker =
            Thread({
                var failure: Throwable? = null
                try {
                    AudioSystem.getAudioInputStream(file).use { raw ->
                        toPcm16(raw).use { pcm ->
                            val output = AudioSystem.getSourceDataLine(pcm.format)
                            line = output
                            output.open(pcm.format)
                            output.start()
                            pumpThroughChain(pcm, { chain ?: initialChain }, { running }) { bytes, count ->
                                output.write(bytes, 0, count)
                            }
                            if (running) output.drain()
                            output.close()
                        }
                    }
                } catch (t: Throwable) {
                    failure = t
                } finally {
                    running = false
                    line = null
                    onFinished(failure)
                }
            }, "hardbasseq-player").apply {
                isDaemon = true
                start()
            }
    }

    fun stop() {
        running = false
        line?.run {
            stop()
            flush()
        }
        worker?.join(STOP_JOIN_MS)
        worker = null
    }

    private companion object {
        const val STOP_JOIN_MS = 2000L
    }
}
