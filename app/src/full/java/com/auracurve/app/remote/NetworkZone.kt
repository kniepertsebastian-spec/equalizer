package com.auracurve.app.remote

import java.net.InetAddress

/**
 * Which of the phone's own networks the server answers in (Pixel-Car module 05, E-016): the
 * hotspot always, a network the phone itself is a client of (home Wi-Fi, also mobile data or a
 * VPN) only with the switch "Also reachable in Wi-Fi".
 *
 * Android lists the networks the phone uses (ConnectivityManager). Since Android 15 its own
 * hotspot is among them too, as a local network without internet (device test Pixel 9, Android
 * 17, 18 Nov 2026: `ap_br_wlan2`, LOCAL_NETWORK, no INTERNET). [clientAddresses] keeps only the
 * networks that lead to the internet and are not local-only, so a connection whose local address
 * belongs to one of them did not come in through the hotspot.
 */
object NetworkZone {
    /** What the server needs to know about one of the phone's networks. */
    data class Network(
        val addresses: Set<InetAddress>,
        val hasInternet: Boolean,
        val localOnly: Boolean,
    )

    /** The addresses of networks the phone is a client of: with internet, not local-only (the hotspot). */
    fun clientAddresses(networks: List<Network>): Set<InetAddress> =
        networks
            .filter { it.hasInternet && !it.localOnly }
            .flatMapTo(mutableSetOf()) { it.addresses }

    fun allows(
        localAddress: String,
        clientNetworkAddresses: Set<InetAddress>,
        allowClientNetworks: Boolean,
    ): Boolean {
        if (allowClientNetworks) return true
        // Only called after PrivateNetwork.allows: localAddress is an IP literal, nothing is resolved.
        val local =
            runCatching {
                InetAddress.getByName(
                    localAddress
                        .trim()
                        .removePrefix("[")
                        .removeSuffix("]")
                        .substringBefore('%'),
                )
            }.getOrNull() ?: return false
        return local !in clientNetworkAddresses
    }
}
