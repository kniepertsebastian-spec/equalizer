package com.auracurve.app.ui.remote

import android.app.Application
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.auracurve.app.remote.DynDnsStatus
import com.auracurve.app.remote.PairingCode
import com.auracurve.app.remote.RemoteDevice
import com.auracurve.app.remote.RemoteServerUiState
import com.auracurve.app.ui.theme.AuraCurveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

// Module 05 in the remote card: the pairing code with its QR code while it is valid, in a secure
// window, the paired devices and the Wi-Fi switch. A plain Application: the Hilt app would build the whole graph.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class RemoteServerCardQrTest {
    @get:Rule
    val compose = createComposeRule()

    private val running = RemoteServerUiState(available = true, running = true, address = "10.40.5.1", hostName = "pixel9")
    private val code = PairingCode("ABCDEFGH", Long.MAX_VALUE)

    private var pairingStarted = 0
    private var cancelled = 0
    private val removed = mutableListOf<String>()
    private var wifi: Boolean? = null

    private fun show(initial: RemoteServerUiState): (RemoteServerUiState) -> Unit {
        var state by mutableStateOf(initial)
        compose.setContent {
            AuraCurveTheme {
                RemoteServerCardContent(
                    state = state,
                    spotifyAccess = true,
                    onEnabledChange = {},
                    onStartPairing = { pairingStarted++ },
                    onCancelPairing = { cancelled++ },
                    onRemoveDevice = { removed += it },
                    onRemoveAllDevices = {},
                    onAllowWifiChange = { wifi = it },
                    onStartAtBootChange = {},
                    onHostNameChange = {},
                    onOpenNotificationAccess = {},
                )
            }
        }
        return { state = it }
    }

    private fun dialogs() = compose.onAllNodes(isDialog())

    private fun inDialog(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test
    fun `pairing shows the code and its link in a secure window`() {
        val update = show(running)
        dialogs().assertCountEquals(0)
        compose.onNodeWithTag("remote_server_pair").performClick()
        assertEquals(1, pairingStarted)

        update(running.copy(pairing = code))
        inDialog("ABCD-EFGH").assertExists()
        inDialog("http://pixel9.local:8080/#pair=ABCDEFGH").assertExists()
        compose.onNodeWithContentDescription("Pairing link as QR code").assertExists()
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        assertNotEquals(0, dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)

        compose.onNodeWithTag("remote_server_qr_use_ip").performClick()
        inDialog("http://10.40.5.1:8080/#pair=ABCDEFGH").assertExists()

        compose.onNodeWithText("Close").performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun `the address qr carries only the address and starts no pairing`() {
        show(running)
        compose.onNodeWithTag("remote_server_address_qr").performClick()
        assertEquals(0, pairingStarted)
        inDialog("http://pixel9.local:8080").assertExists()
        compose.onNodeWithContentDescription("Web remote address as QR code").assertExists()
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        assertEquals(0, dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)

        compose.onNodeWithTag("remote_server_address_use_ip").performClick()
        inDialog("http://10.40.5.1:8080").assertExists()
        compose.onAllNodes(hasText("#pair", substring = true)).assertCountEquals(0)

        compose.onNodeWithText("Close").performClick()
        dialogs().assertCountEquals(0)
        assertEquals(0, cancelled)
    }

    @Test
    fun `a duckdns name replaces the local name in links and shows whether it is up to date`() {
        val update =
            show(
                running.copy(
                    dynDnsHost = "basti-auto.duckdns.org",
                    dynDnsStatus = DynDnsStatus("10.40.5.1", 0L, failed = false),
                ),
            )
        compose.onNodeWithText("Address: http://basti-auto.duckdns.org:8080").assertExists()
        compose.onNodeWithText("Fixed address: basti-auto.duckdns.org").assertExists()
        compose.onNodeWithText("Points to 10.40.5.1", substring = true).assertExists()
        compose.onNodeWithTag("remote_server_address_qr").performClick()
        inDialog("http://basti-auto.duckdns.org:8080").assertExists()
        compose.onNodeWithText("Close").performClick()

        update(running.copy(dynDnsHost = "basti-auto.duckdns.org", dynDnsStatus = DynDnsStatus(failed = true)))
        compose.onNodeWithText("Update failed", substring = true).assertExists()
        update(running.copy(pairing = code, dynDnsHost = "basti-auto.duckdns.org"))
        inDialog("http://basti-auto.duckdns.org:8080/#pair=ABCDEFGH").assertExists()
    }

    @Test
    fun `a used or expired code or the server going off closes the dialog`() {
        val update = show(running.copy(pairing = code))
        dialogs().assertCountEquals(1)
        update(running)
        dialogs().assertCountEquals(0)
        update(running.copy(pairing = code, running = false, address = null))
        dialogs().assertCountEquals(0)
    }

    @Test
    fun `paired devices are listed and removed one by one, the wi-fi switch reports`() {
        show(running.copy(devices = listOf(RemoteDevice("d1", "Pixel 10", "h", 0), RemoteDevice("d2", "iPhone", "h", 0))))
        compose.onNodeWithText("Paired devices (2)").assertExists()
        compose.onNodeWithText("iPhone").assertExists()
        compose.onNodeWithTag("remote_server_device_d2").assertExists()
        compose.onAllNodes(hasText("Remove"))[1].performClick()
        assertEquals(listOf("d2"), removed)
        compose.onNodeWithTag("remote_server_wifi_switch").performClick()
        assertEquals(true, wifi)
    }
}
