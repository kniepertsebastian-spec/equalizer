package com.auracurve.app.ui.player

import com.auracurve.app.R
import com.auracurve.app.text.FakeTextProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Flavor "store" (AuraCurve roadmap 02, 1.3): a link that still reaches the import is refused
// with a plain message, nothing is looked up and nothing is pending.
class NoLinkImportTest {
    private val texts = FakeTextProvider()

    @Test
    fun `a link is refused with the not available message`() =
        runTest {
            val importState = MutableStateFlow(ImportUiState())
            val bridgeState = MutableStateFlow<BridgeUiState?>(null)
            val import = NoLinkImport(texts)
            import.attach(LinkImportSession(TestScope(), importState, bridgeState))

            import.importText("https://soundcloud.com/artist/track")

            assertTrue(importState.value.isError)
            assertEquals(texts.get(R.string.online_sources_unavailable), importState.value.message)
            assertNull(bridgeState.value)
            assertNull(import.pending.first())
        }
}
