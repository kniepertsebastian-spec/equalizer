package com.auracurve.app.remote

import java.net.Inet4Address
import java.net.NetworkInterface

/** The phone's own private IPv4 address (hotspot or Wi-Fi), for the notification and the pairing link. */
object LocalAddress {
    fun privateIpv4(): String? =
        runCatching {
            NetworkInterface
                .getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { PrivateNetwork.isPrivate(it) }
                ?.hostAddress
        }.getOrNull()

    fun pairingLink(
        address: String,
        token: String,
    ): String = "http://$address:$PORT/#token=$token"

    const val PORT = 8080
}
