package com.auracurve.app.remote

import android.util.Log
import java.io.IOException
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException

private const val TAG = "MdnsResponder"

/**
 * A small mDNS responder (RFC 6762) for Pixel-Car module 04e (R-19): answers A questions (and ANY)
 * for exactly `<name>.local`, so the web remote finds the phone under a fixed name although
 * Android picks a new hotspot address every time. Origin, home-screen shortcut and stored token
 * then stay the same. Modelled on pixel-car-remote/tools/mdns.py.
 *
 * NsdManager is not enough: it announces services, not a host name of our own. jmDNS would be a
 * new dependency for these few bytes.
 *
 * Runs on its own thread while the server runs. It joins the group 224.0.0.251 on every interface
 * with a private IPv4 address and repeats that every [rejoinMs], because the hotspot may come up
 * after the server. Anything else than an A/ANY question for its name is ignored, broken packets
 * are dropped silently.
 */
class MdnsResponder(
    name: String,
    private val interfaces: () -> List<MdnsInterface> = ::systemInterfaces,
    private val rejoinMs: Long = REJOIN_MS,
) {
    /** The bare name (without `.local`); can change while running. */
    @Volatile
    var name: String = name

    @Volatile
    private var running = false

    @Volatile
    private var socket: MulticastSocket? = null
    private var thread: Thread? = null

    @Synchronized
    fun start() {
        if (running) return
        running = true
        thread = Thread(::serve, "mdns-responder").apply { isDaemon = true }.also { it.start() }
    }

    @Synchronized
    fun stop() {
        running = false
        socket?.close()
        thread?.interrupt()
        thread = null
    }

    private fun serve() {
        while (running) {
            val sock =
                try {
                    openSocket()
                } catch (e: IOException) {
                    Log.w(TAG, "mDNS socket not opened: ${e.javaClass.simpleName}")
                    sleepQuietly(rejoinMs)
                    continue
                }
            socket = sock
            if (!running) sock.close()
            try {
                serveOn(sock)
            } finally {
                sock.close()
                socket = null
            }
            // The socket broke while running: open a new one, but not in a tight loop.
            if (running) sleepQuietly(RECEIVE_TIMEOUT_MS.toLong())
        }
    }

    private fun openSocket(): MulticastSocket =
        MulticastSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(PORT))
            timeToLive = 255
            soTimeout = RECEIVE_TIMEOUT_MS
        }

    private fun serveOn(sock: MulticastSocket) {
        val joined = mutableMapOf<String, NetworkInterface>()
        var current = join(sock, joined)
        var lastJoin = System.nanoTime()
        val buffer = ByteArray(MAX_PACKET_BYTES)
        while (running && !sock.isClosed) {
            if ((System.nanoTime() - lastJoin) / 1_000_000 >= rejoinMs) {
                current = join(sock, joined)
                lastJoin = System.nanoTime()
            }
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                sock.receive(packet)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (e: IOException) {
                if (running && !sock.isClosed) Log.w(TAG, "mDNS receive failed: ${e.javaClass.simpleName}")
                return
            }
            val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
            val replies = replies(data, packet.address, packet.port, name, current.flatMap { it.addresses })
            for (reply in replies) send(sock, reply, packet, current)
        }
    }

    /** Joins the group on interfaces that are new, forgets the ones that are gone. */
    private fun join(
        sock: MulticastSocket,
        joined: MutableMap<String, NetworkInterface>,
    ): List<MdnsInterface> {
        val now = runCatching { interfaces() }.getOrElse { emptyList() }
        val byKey = now.associateBy { it.key }
        for ((key, ni) in joined.toMap()) {
            if (key !in byKey) {
                runCatching { sock.leaveGroup(GROUP_ADDRESS, ni) }
                joined.remove(key)
            }
        }
        for (iface in now) {
            if (iface.key in joined) continue
            try {
                sock.joinGroup(GROUP_ADDRESS, iface.networkInterface)
                joined[iface.key] = iface.networkInterface
                Log.i(TAG, "mDNS: ${MdnsName.host(name)} on ${iface.networkInterface.name}")
            } catch (e: IOException) {
                // "Already a member" after a quick off/on keeps working; anything else is tried again later.
                Log.w(TAG, "mDNS: join on ${iface.networkInterface.name} failed: ${e.javaClass.simpleName}")
            }
        }
        return now
    }

    private fun send(
        sock: MulticastSocket,
        reply: Reply,
        query: DatagramPacket,
        current: List<MdnsInterface>,
    ) {
        try {
            if (reply.toGroup) {
                current.firstOrNull { iface -> iface.addresses.any { it.address == reply.via } }?.let {
                    sock.networkInterface = it.networkInterface
                }
                sock.send(DatagramPacket(reply.packet, reply.packet.size, GROUP_ADDRESS))
            } else {
                sock.send(DatagramPacket(reply.packet, reply.packet.size, query.address, query.port))
            }
        } catch (e: IOException) {
            Log.w(TAG, "mDNS answer failed: ${e.javaClass.simpleName}")
        }
    }

    private fun sleepQuietly(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            // stop() interrupts; the loop checks running.
        }
    }

    /** One IPv4 address of an interface with its prefix length, e.g. 10.40.5.1/24. */
    data class LocalIpv4(
        val address: Inet4Address,
        val prefixLength: Int,
    )

    /** An interface the responder joins the group on, with its private IPv4 addresses. */
    class MdnsInterface(
        val networkInterface: NetworkInterface,
        val addresses: List<LocalIpv4>,
    ) {
        // A hotspot switched off and on comes back as a new interface (new index, new address).
        internal val key: String =
            "${networkInterface.name}#${networkInterface.index}#${addresses.joinToString { it.address.hostAddress.orEmpty() }}"
    }

    /** What to send: [packet] to the group (or to the asker), answering with the address [via]. */
    class Reply(
        val packet: ByteArray,
        val toGroup: Boolean,
        val via: Inet4Address,
    )

    companion object {
        const val PORT = 5353
        const val GROUP = "224.0.0.251"
        private const val REJOIN_MS = 10_000L
        private const val RECEIVE_TIMEOUT_MS = 1_000
        private const val MAX_PACKET_BYTES = 9_000
        private val GROUP_ADDRESS = InetSocketAddress(InetAddress.getByAddress(byteArrayOf(224.toByte(), 0, 0, 251.toByte())), PORT)

        /**
         * The answers to [data] from [peer]:[peerPort] for `<name>.local`: none for anything but an
         * A or ANY question for that name (case ignored), none for a broken packet, none when no
         * own private address shares a subnet with the asker.
         */
        fun replies(
            data: ByteArray,
            peer: InetAddress,
            peerPort: Int,
            name: String,
            candidates: List<LocalIpv4>,
        ): List<Reply> {
            val query =
                try {
                    MdnsPacket.parseQuery(data)
                } catch (_: MdnsPacket.MalformedPacketException) {
                    null
                } ?: return emptyList()
            val host = MdnsName.host(name)
            val question =
                query.questions.firstOrNull {
                    it.name.trimEnd('.').equals(host, ignoreCase = true) &&
                        (it.type == MdnsPacket.TYPE_A || it.type == MdnsPacket.TYPE_ANY)
                } ?: return emptyList()
            val via = addressFor(peer, candidates) ?: return emptyList()
            if (peerPort != PORT) {
                // A legacy resolver (not a full mDNS stack): answer it directly, with its id and question.
                val packet = MdnsPacket.buildAnswer(host, via, query.id, question, MdnsPacket.LEGACY_TTL_SECONDS)
                return listOf(Reply(packet, toGroup = false, via = via))
            }
            val packet = MdnsPacket.buildAnswer(host, via)
            val group = Reply(packet, toGroup = true, via = via)
            return if (question.qclass and MdnsPacket.QU_BIT !=
                0
            ) {
                listOf(group, Reply(packet, toGroup = false, via = via))
            } else {
                listOf(group)
            }
        }

        /**
         * The own address in the asker's subnet: private (same rule as [PrivateNetwork]), never
         * loopback, and the asker must be private too. Null if there is none.
         */
        fun addressFor(
            peer: InetAddress,
            candidates: List<LocalIpv4>,
        ): Inet4Address? {
            if (peer !is Inet4Address || !PrivateNetwork.isPrivate(peer)) return null
            return candidates
                .firstOrNull {
                    !it.address.isLoopbackAddress &&
                        PrivateNetwork.isPrivate(it.address) &&
                        sameSubnet(it, peer)
                }?.address
        }

        private fun sameSubnet(
            local: LocalIpv4,
            peer: Inet4Address,
        ): Boolean {
            if (local.prefixLength !in 1..32) return false
            val mask = (0xFFFFFFFFL shl (32 - local.prefixLength)) and 0xFFFFFFFFL
            return (toLong(local.address) and mask) == (toLong(peer) and mask)
        }

        private fun toLong(address: Inet4Address): Long = address.address.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }

        /** The phone's interfaces that are up, can multicast and have a private IPv4 address. */
        fun systemInterfaces(): List<MdnsInterface> =
            NetworkInterface
                .getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .filter { runCatching { it.isUp && !it.isLoopback && it.supportsMulticast() }.getOrDefault(false) }
                .mapNotNull { ni ->
                    val addresses =
                        ni.interfaceAddresses
                            .mapNotNull { ia ->
                                (ia.address as? Inet4Address)
                                    ?.takeIf { PrivateNetwork.isPrivate(it) && !it.isLoopbackAddress }
                                    ?.let { LocalIpv4(it, ia.networkPrefixLength.toInt()) }
                            }
                    if (addresses.isEmpty()) null else MdnsInterface(ni, addresses)
                }
    }
}
