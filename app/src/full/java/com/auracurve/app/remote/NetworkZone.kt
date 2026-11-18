package com.auracurve.app.remote

import java.net.InetAddress

/**
 * Which of the phone's own networks the server answers in (Pixel-Car module 05, E-016): the
 * hotspot always, a network the phone itself is a client of (home Wi-Fi, also mobile data or a
 * VPN) only with the switch "Also reachable in Wi-Fi".
 *
 * Android lists the networks the phone uses as a client (ConnectivityManager); the hotspot it
 * provides is not among them. So a connection whose local address belongs to one of those client
 * networks did not come in through the hotspot.
 */
object NetworkZone {
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
