package com.auracurve.app.remote

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Which addresses the remote-control server answers on (Pixel-Car module 04a): only when both
 * ends of a connection are in a private network - RFC 1918 for IPv4, unique local (fc00::/7) or
 * link-local (fe80::/10) for IPv6. A public address on either end (mobile data, a SIM added
 * later, a forwarded port) closes the connection without an answer.
 *
 * Loopback is not private in this sense: the web remote never comes from the phone itself.
 */
object PrivateNetwork {
    /** Both ends private. The strings are the IP literals the server engine reports. */
    fun allows(
        localAddress: String,
        remoteAddress: String,
    ): Boolean = isPrivate(localAddress) && isPrivate(remoteAddress)

    /**
     * True for a private IP literal. Anything that is not an IP literal (a host name, "unknown")
     * is false: this never resolves a name.
     */
    fun isPrivate(address: String): Boolean {
        val parsed = parseLiteral(address) ?: return false
        return isPrivate(parsed)
    }

    fun isPrivate(address: InetAddress): Boolean =
        when (address) {
            is Inet4Address -> isPrivateV4(address.address)
            is Inet6Address -> isPrivateV6(address.address)
            else -> false
        }

    private fun isPrivateV4(b: ByteArray): Boolean {
        val first = b[0].toInt() and 0xFF
        val second = b[1].toInt() and 0xFF
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)
    }

    private fun isPrivateV6(b: ByteArray): Boolean {
        val first = b[0].toInt() and 0xFF
        val second = b[1].toInt() and 0xFF
        // An IPv4 address carried in IPv6 (::ffff:a.b.c.d) counts as that IPv4 address.
        val v4Mapped = (0 until 10).all { b[it].toInt() == 0 } && b[10].toInt() and 0xFF == 0xFF && b[11].toInt() and 0xFF == 0xFF
        if (v4Mapped) return isPrivateV4(b.copyOfRange(12, 16))
        val uniqueLocal = first and 0xFE == 0xFC
        val linkLocal = first == 0xFE && second and 0xC0 == 0x80
        return uniqueLocal || linkLocal
    }

    // Literals only: InetAddress.getByName does no lookup for them, and anything else is refused
    // before it gets there. A zone ("fe80::1%wlan0") is dropped.
    private fun parseLiteral(text: String): InetAddress? {
        val address =
            text
                .trim()
                .removePrefix("[")
                .removeSuffix("]")
                .substringBefore('%')
        if (address.isEmpty()) return null
        val looksV4 = IPV4.matches(address)
        val looksV6 =
            address.contains(':') &&
                address.all { it.isLetterOrDigit() || it == ':' || it == '.' } &&
                address.filter { it.isLetter() }.all { it.lowercaseChar() in 'a'..'f' }
        if (!looksV4 && !looksV6) return null
        return runCatching { InetAddress.getByName(address) }.getOrNull()
    }

    private val IPV4 = Regex("""^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$""")
}
