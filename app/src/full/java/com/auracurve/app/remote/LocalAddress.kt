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

    /** The web remote's origin for [host] (an IP literal or a name like pixel9.local). */
    fun url(host: String): String = "http://$host:$PORT"

    fun pairingLink(
        host: String,
        token: String,
    ): String = "${url(host)}/#token=$token"

    const val PORT = 8080
}
