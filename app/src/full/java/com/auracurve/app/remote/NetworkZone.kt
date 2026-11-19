package com.auracurve.app.remote

import java.net.InetAddress

/**
 * Which of the phone's own networks the server answers in (Pixel-Car module 05, E-016): the
 * hotspot always, a network the phone itself is a client of (home Wi-Fi, also mobile data or a
 * VPN) only with the switch "Also reachable in Wi-Fi". Since 19 Nov 2026 also a Wi-Fi on the list
 * of trusted Wi-Fi names: in the car the Pixel joins the hotspot of the driver's phone to get
 * internet (no SIM), and the remote control must reach it there without opening every Wi-Fi.
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
        /** The Wi-Fi name (SSID) if the network is a Wi-Fi and Android tells it (needs the location permission). */
        val wifiName: String? = null,
    )

    /**
     * The addresses of networks the phone is a client of: with internet, not local-only (the
     * hotspot), and not a Wi-Fi on the trusted list. Those count like the hotspot.
     */
    fun clientAddresses(
        networks: List<Network>,
        trustedWifi: Set<String> = emptySet(),
    ): Set<InetAddress> {
        val trusted = trustedWifi.mapNotNullTo(mutableSetOf(), ::cleanWifiName)
        return networks
            .filter { it.hasInternet && !it.localOnly }
            .filterNot { network -> cleanWifiName(network.wifiName)?.let { it in trusted } == true }
            .flatMapTo(mutableSetOf()) { it.addresses }
    }

    /**
     * A Wi-Fi name as the user means it: Android puts quotes around SSIDs it can show as text and
     * reports `<unknown ssid>` without the location permission. Null if there is no usable name.
     */
    fun cleanWifiName(raw: String?): String? {
        val name =
            raw
                ?.trim()
                ?.removeSurrounding("\"")
                ?.trim()
                .orEmpty()
        if (name.isEmpty() || name == UNKNOWN_SSID || name.length > MAX_WIFI_NAME_LENGTH) return null
        return name
    }

    /** What Android reports instead of the name without the location permission. */
    const val UNKNOWN_SSID = "<unknown ssid>"

    /** An SSID has at most 32 bytes. */
    const val MAX_WIFI_NAME_LENGTH = 32

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
