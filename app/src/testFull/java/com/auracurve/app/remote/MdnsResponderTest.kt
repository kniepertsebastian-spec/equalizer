package com.auracurve.app.remote

import com.auracurve.app.remote.MdnsPacket.MalformedPacketException
import com.auracurve.app.remote.MdnsResponder.LocalIpv4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress

class MdnsResponderTest {
    private fun ip(text: String): Inet4Address = InetAddress.getByName(text) as Inet4Address

    private val hotspot = LocalIpv4(ip("10.40.5.1"), 24)
    private val wifi = LocalIpv4(ip("192.168.178.20"), 24)

    /** A query in wire format, like a phone's resolver sends it. */
    private fun query(
        name: String,
        type: Int = MdnsPacket.TYPE_A,
        qclass: Int = MdnsPacket.CLASS_IN,
        id: Int = 0,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf((id ushr 8).toByte(), id.toByte(), 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        out.write(MdnsPacket.encodeName(name))
        out.write(byteArrayOf((type ushr 8).toByte(), type.toByte(), (qclass ushr 8).toByte(), qclass.toByte()))
        return out.toByteArray()
    }

    // --- names on the wire ---

    @Test
    fun `a name is encoded as length-prefixed labels and read back`() {
        val encoded = MdnsPacket.encodeName("pixel9.local")
        assertArrayEquals(byteArrayOf(6) + "pixel9".toByteArray() + byteArrayOf(5) + "local".toByteArray() + byteArrayOf(0), encoded)
        val (name, end) = MdnsPacket.readName(encoded, 0)
        assertEquals("pixel9.local", name)
        assertEquals(encoded.size, end)
    }

    @Test
    fun `a compressed name follows the pointer and ends after it`() {
        // At 12: "local" (7 bytes). At 19: "pixel9" + pointer to 12.
        val data = ByteArray(12) + MdnsPacket.encodeName("local") + byteArrayOf(6) + "pixel9".toByteArray() + byteArrayOf(0xC0.toByte(), 12)
        val (name, end) = MdnsPacket.readName(data, 19)
        assertEquals("pixel9.local", name)
        assertEquals(data.size, end)
    }

    @Test(expected = MalformedPacketException::class)
    fun `a pointer loop is refused`() {
        val data = ByteArray(12) + byteArrayOf(0xC0.toByte(), 12)
        MdnsPacket.readName(data, 12)
    }

    @Test
    fun `cut off names, pointers and questions are refused`() {
        val full = query("pixel9.local")
        for (cut in listOf(13, 18, 25, full.size - 2)) {
            assertThrowsMalformed { MdnsPacket.parseQuery(full.copyOf(cut)) }
        }
        assertThrowsMalformed { MdnsPacket.readName(byteArrayOf(0xC0.toByte()), 0) }
        assertThrowsMalformed { MdnsPacket.parseQuery(ByteArray(5)) }
        // A label type other than plain or pointer (0x40, 0x80) is not understood.
        assertThrowsMalformed { MdnsPacket.readName(byteArrayOf(0x41, 0), 0) }
    }

    @Test
    fun `a response is not a query`() {
        val response = MdnsPacket.buildAnswer("pixel9.local", ip("10.40.5.1"))
        assertNull(MdnsPacket.parseQuery(response))
    }

    @Test
    fun `the multicast answer carries one A record with cache flush`() {
        val packet = MdnsPacket.buildAnswer("pixel9.local", ip("10.40.5.1"))
        assertEquals(0x84, packet[2].toInt() and 0xFF)
        assertEquals(0, packet[5].toInt()) // no question
        assertEquals(1, packet[7].toInt()) // one answer
        val (name, off) = MdnsPacket.readName(packet, 12)
        assertEquals("pixel9.local", name)
        assertEquals(MdnsPacket.TYPE_A, u16(packet, off))
        assertEquals(MdnsPacket.CLASS_IN or MdnsPacket.CACHE_FLUSH, u16(packet, off + 2))
        assertEquals(MdnsPacket.TTL_SECONDS, u16(packet, off + 6))
        assertEquals(4, u16(packet, off + 8))
        assertArrayEquals(ip("10.40.5.1").address, packet.copyOfRange(off + 10, off + 14))
    }

    // --- what the responder answers ---

    @Test
    fun `an A question for the own name gets a multicast answer from the asker's subnet`() {
        val replies = MdnsResponder.replies(query("pixel9.local"), ip("10.40.5.77"), 5353, "pixel9", listOf(wifi, hotspot))
        assertEquals(1, replies.size)
        assertTrue(replies[0].toGroup)
        assertEquals(ip("10.40.5.1"), replies[0].via)
    }

    @Test
    fun `case does not matter, ANY counts as A`() {
        assertEquals(1, MdnsResponder.replies(query("PIXEL9.Local"), ip("10.40.5.77"), 5353, "pixel9", listOf(hotspot)).size)
        assertEquals(
            1,
            MdnsResponder
                .replies(
                    query("pixel9.local", type = MdnsPacket.TYPE_ANY),
                    ip("10.40.5.77"),
                    5353,
                    "pixel9",
                    listOf(hotspot),
                ).size,
        )
    }

    @Test
    fun `other names, other types and broken packets get nothing`() {
        val peer = ip("10.40.5.77")
        assertTrue(MdnsResponder.replies(query("pixel10.local"), peer, 5353, "pixel9", listOf(hotspot)).isEmpty())
        assertTrue(MdnsResponder.replies(query("pixel9.local.evil"), peer, 5353, "pixel9", listOf(hotspot)).isEmpty())
        assertTrue(MdnsResponder.replies(query("pixel9.local", type = 28), peer, 5353, "pixel9", listOf(hotspot)).isEmpty()) // AAAA
        assertTrue(MdnsResponder.replies(query("pixel9.local").copyOf(20), peer, 5353, "pixel9", listOf(hotspot)).isEmpty())
        assertTrue(MdnsResponder.replies(ByteArray(3), peer, 5353, "pixel9", listOf(hotspot)).isEmpty())
    }

    @Test
    fun `a renamed responder answers to the new name only`() {
        val peer = ip("10.40.5.77")
        assertTrue(MdnsResponder.replies(query("pixel9.local"), peer, 5353, "car-remote", listOf(hotspot)).isEmpty())
        assertEquals(1, MdnsResponder.replies(query("car-remote.local"), peer, 5353, "car-remote", listOf(hotspot)).size)
    }

    @Test
    fun `a QU question also gets a unicast answer`() {
        val replies =
            MdnsResponder.replies(
                query("pixel9.local", qclass = MdnsPacket.CLASS_IN or MdnsPacket.QU_BIT),
                ip("10.40.5.77"),
                5353,
                "pixel9",
                listOf(hotspot),
            )
        assertEquals(listOf(true, false), replies.map { it.toGroup })
    }

    @Test
    fun `a legacy resolver gets its id and question back, directly and with a short TTL`() {
        val replies = MdnsResponder.replies(query("pixel9.local", id = 0x1234), ip("10.40.5.77"), 40000, "pixel9", listOf(hotspot))
        assertEquals(1, replies.size)
        assertFalse(replies[0].toGroup)
        val packet = replies[0].packet
        assertEquals(0x1234, u16(packet, 0))
        assertEquals(1, u16(packet, 4))
        val (_, afterQuestion) = MdnsPacket.readName(packet, 12)
        val (_, afterName) = MdnsPacket.readName(packet, afterQuestion + 4)
        assertEquals(MdnsPacket.CLASS_IN, u16(packet, afterName + 2))
        assertEquals(MdnsPacket.LEGACY_TTL_SECONDS, u16(packet, afterName + 6))
    }

    // --- which address ---

    @Test
    fun `the address is the own one in the asker's subnet`() {
        val both = listOf(hotspot, wifi)
        assertEquals(ip("10.40.5.1"), MdnsResponder.addressFor(ip("10.40.5.200"), both))
        assertEquals(ip("192.168.178.20"), MdnsResponder.addressFor(ip("192.168.178.3"), both))
        assertNull(MdnsResponder.addressFor(ip("10.40.6.2"), both))
    }

    @Test
    fun `only private addresses, never loopback, never for a public asker`() {
        val public = LocalIpv4(ip("8.8.8.1"), 24)
        val loopback = LocalIpv4(ip("127.0.0.1"), 8)
        assertNull(MdnsResponder.addressFor(ip("8.8.8.8"), listOf(public)))
        assertNull(MdnsResponder.addressFor(ip("127.0.0.2"), listOf(loopback)))
        // CGNAT (100.64/10) is not private under PrivateNetwork either.
        assertNull(MdnsResponder.addressFor(ip("100.64.0.2"), listOf(LocalIpv4(ip("100.64.0.1"), 10))))
        assertNull(MdnsResponder.addressFor(InetAddress.getByName("fe80::1"), listOf(hotspot)))
    }

    @Test
    fun `odd prefix lengths are handled`() {
        assertEquals(ip("10.0.0.1"), MdnsResponder.addressFor(ip("10.255.1.1"), listOf(LocalIpv4(ip("10.0.0.1"), 8))))
        assertEquals(ip("10.0.0.1"), MdnsResponder.addressFor(ip("10.0.0.1"), listOf(LocalIpv4(ip("10.0.0.1"), 32))))
        assertNull(MdnsResponder.addressFor(ip("10.0.0.2"), listOf(LocalIpv4(ip("10.0.0.1"), 32))))
        assertNull(MdnsResponder.addressFor(ip("10.0.0.2"), listOf(LocalIpv4(ip("10.0.0.1"), 0))))
        assertNull(MdnsResponder.addressFor(ip("10.0.0.2"), listOf(LocalIpv4(ip("10.0.0.1"), -1))))
    }

    // --- the name setting ---

    @Test
    fun `names are a-z, 0-9 and dash, 1 to 30 characters, stored in lower case`() {
        assertEquals("pixel9", MdnsName.normalize("pixel9"))
        assertEquals("pixel9", MdnsName.normalize(" Pixel9 "))
        assertEquals("car-remote-2", MdnsName.normalize("car-remote-2"))
        assertEquals("a".repeat(30), MdnsName.normalize("a".repeat(30)))
        assertNull(MdnsName.normalize(""))
        assertNull(MdnsName.normalize("a".repeat(31)))
        assertNull(MdnsName.normalize("pixel.9"))
        assertNull(MdnsName.normalize("pixel_9"))
        assertNull(MdnsName.normalize("pixél"))
        assertEquals("pixel9.local", MdnsName.host(MdnsName.DEFAULT))
    }

    @Test
    fun `the card and the pairing link use the name`() {
        val state = RemoteServerUiState(available = true, running = true, address = "10.40.5.1", token = "abc", hostName = "pixel9")
        assertEquals("http://pixel9.local:8080", state.nameUrl)
        assertEquals("http://pixel9.local:8080/#token=abc", state.pairingLink)
        assertNull(state.copy(address = null).pairingLink)
    }

    private fun u16(
        data: ByteArray,
        off: Int,
    ): Int = ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)

    private fun assertThrowsMalformed(block: () -> Unit) {
        try {
            block()
        } catch (_: MalformedPacketException) {
            return
        }
        throw AssertionError("expected MalformedPacketException")
    }
}
