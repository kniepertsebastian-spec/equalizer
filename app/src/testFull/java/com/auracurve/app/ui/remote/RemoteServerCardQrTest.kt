package com.auracurve.app.ui.remote

import android.app.Application
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.auracurve.app.remote.RemoteServerUiState
import com.auracurve.app.ui.theme.AuraCurveTheme
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

// The pairing QR code of the remote card: only on request, in a secure window, following the
// token and the server state. A plain Application: the Hilt app would build the whole graph.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class RemoteServerCardQrTest {
    @get:Rule
    val compose = createComposeRule()

    private val running =
        RemoteServerUiState(available = true, running = true, address = "10.40.5.1", token = "tok1", hostName = "pixel9")

    private fun show(initial: RemoteServerUiState): (RemoteServerUiState) -> Unit {
        var state by mutableStateOf(initial)
        compose.setContent {
            AuraCurveTheme {
                RemoteServerCardContent(
                    state = state,
                    spotifyAccess = true,
                    onEnabledChange = {},
                    onRegenerateToken = {},
                    onStartAtBootChange = {},
                    onHostNameChange = {},
                    onOpenNotificationAccess = {},
                )
            }
        }
        return { state = it }
    }

    private fun dialogs() = compose.onAllNodes(isDialog())

    // The card shows the link as text too: look inside the dialog only.
    private fun inDialog(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test
    fun `the QR code only shows on request, with the name link, in a secure window`() {
        show(running)
        dialogs().assertCountEquals(0)

        compose.onNodeWithText("Show QR code").performClick()
        compose.onNodeWithContentDescription("Pairing link as QR code").assertExists()
        inDialog("http://pixel9.local:8080/#token=tok1").assertExists()
        // The screen reader never reads the token out.
        compose.onAllNodes(hasContentDescription("tok1", substring = true)).assertCountEquals(0)

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        assertNotEquals(0, dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)

        compose.onNodeWithText("Close").performClick()
        dialogs().assertCountEquals(0)
    }

    @Test
    fun `use IP instead switches the link`() {
        show(running)
        compose.onNodeWithText("Show QR code").performClick()
        compose.onNodeWithTag("remote_server_qr_use_ip").performClick()
        inDialog("http://10.40.5.1:8080/#token=tok1").assertExists()
    }

    @Test
    fun `a new token shows the new link, the server going off closes the dialog`() {
        val update = show(running)
        compose.onNodeWithText("Show QR code").performClick()

        update(running.copy(token = "tok2"))
        inDialog("http://pixel9.local:8080/#token=tok2").assertExists()
        dialogs().assertCountEquals(1)

        update(running.copy(token = "tok2", running = false, address = null))
        dialogs().assertCountEquals(0)

        // Back on: the dialog stays closed until asked for again.
        update(running.copy(token = "tok2"))
        dialogs().assertCountEquals(0)
    }
}
