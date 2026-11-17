package com.auracurve.app.remote

import java.io.ByteArrayOutputStream
import java.net.Inet4Address

/**
 * The few pieces of the DNS message format (RFC 1035, RFC 6762) the mDNS responder needs
 * (Pixel-Car module 04e, R-19): read the questions of a query, write one A answer. Pure, so the
 * parsing can be tested without a network.
 */
object MdnsPacket {
    const val TYPE_A = 1
    const val TYPE_ANY = 255
    const val CLASS_IN = 1
    const val CACHE_FLUSH = 0x8000
    const val QU_BIT = 0x8000
    const val TTL_SECONDS = 120

    /** RFC 6762, 6.7: answers to a "legacy" resolver (not from port 5353) get a short TTL. */
    const val LEGACY_TTL_SECONDS = 10

    private const val HEADER_BYTES = 12
    private const val MAX_POINTER_HOPS = 20
    private const val MAX_LABEL_BYTES = 63

    class MalformedPacketException(
        message: String,
    ) : Exception(message)

    data class Question(
        val name: String,
        val type: Int,
        val qclass: Int,
    )

    data class Query(
        val id: Int,
        val questions: List<Question>,
    )

    fun encodeName(name: String): ByteArray {
        val out = ByteArrayOutputStream()
        for (label in name.trimEnd('.').split('.')) {
            val raw = label.toByteArray(Charsets.UTF_8)
            require(raw.size in 1..MAX_LABEL_BYTES) { "Label length ${raw.size}" }
            out.write(raw.size)
            out.write(raw)
        }
        out.write(0)
        return out.toByteArray()
    }

    /**
     * Reads a (possibly compressed) name at [offset]. Returns the name and the offset right after
     * it in the original position (a pointer counts two bytes there).
     */
    fun readName(
        data: ByteArray,
        offset: Int,
    ): Pair<String, Int> {
        val labels = mutableListOf<String>()
        var off = offset
        var end = -1
        var hops = 0
        while (true) {
            if (off < 0 || off >= data.size) throw MalformedPacketException("Name cut off")
            val n = data[off].toInt() and 0xFF
            when {
                n and 0xC0 == 0xC0 -> {
                    if (off + 1 >= data.size) throw MalformedPacketException("Pointer cut off")
                    if (end < 0) end = off + 2
                    off = ((n and 0x3F) shl 8) or (data[off + 1].toInt() and 0xFF)
                    if (++hops > MAX_POINTER_HOPS) throw MalformedPacketException("Pointer loop")
                }
                n and 0xC0 != 0 -> throw MalformedPacketException("Unknown label type")
                n == 0 -> return labels.joinToString(".") to (if (end < 0) off + 1 else end)
                else -> {
                    if (off + 1 + n > data.size) throw MalformedPacketException("Label cut off")
                    labels += String(data, off + 1, n, Charsets.UTF_8)
                    off += 1 + n
                }
            }
        }
    }

    /** The questions of a query; null for a response (QR bit set). Throws on a broken packet. */
    fun parseQuery(data: ByteArray): Query? {
        if (data.size < HEADER_BYTES) throw MalformedPacketException("Header cut off")
        val id = u16(data, 0)
        val flags = u16(data, 2)
        if (flags and 0x8000 != 0) return null
        val count = u16(data, 4)
        var off = HEADER_BYTES
        val questions = ArrayList<Question>(minOf(count, 16))
        repeat(count) {
            val (name, next) = readName(data, off)
            if (next + 4 > data.size) throw MalformedPacketException("Question cut off")
            questions += Question(name, u16(data, next), u16(data, next + 2))
            off = next + 4
        }
        return Query(id, questions)
    }

    /**
     * A response with one A record for [name]. With [question] it is a legacy unicast answer:
     * the query's id and question go back, without the cache-flush bit.
     */
    fun buildAnswer(
        name: String,
        address: Inet4Address,
        id: Int = 0,
        question: Question? = null,
        ttl: Int = TTL_SECONDS,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        // Header: id, flags (response, authoritative), 0 or 1 question, 1 answer.
        writeU16(out, id)
        writeU16(out, 0x8400)
        writeU16(out, if (question != null) 1 else 0)
        writeU16(out, 1)
        writeU16(out, 0)
        writeU16(out, 0)
        if (question != null) {
            out.write(encodeName(question.name))
            writeU16(out, question.type)
            writeU16(out, question.qclass and 0x7FFF)
        }
        out.write(encodeName(name))
        writeU16(out, TYPE_A)
        writeU16(out, if (question != null) CLASS_IN else CLASS_IN or CACHE_FLUSH)
        writeU16(out, ttl ushr 16)
        writeU16(out, ttl and 0xFFFF)
        writeU16(out, 4)
        out.write(address.address)
        return out.toByteArray()
    }

    private fun u16(
        data: ByteArray,
        off: Int,
    ): Int {
        if (off + 2 > data.size) throw MalformedPacketException("Field cut off")
        return ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)
    }

    private fun writeU16(
        out: ByteArrayOutputStream,
        value: Int,
    ) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}

/** The name the phone answers to as `<name>.local` (04e). Default `pixel9`, changeable in the card. */
object MdnsName {
    const val DEFAULT = "pixel9"
    const val MAX_LENGTH = 30
    private val VALID = Regex("^[a-z0-9-]{1,$MAX_LENGTH}$")

    /** The name in lower case, or null if it is not 1 to 30 of a-z, 0-9 and '-'. */
    fun normalize(input: String): String? = input.trim().lowercase().takeIf { VALID.matches(it) }

    fun host(name: String): String = "$name.local"
}
