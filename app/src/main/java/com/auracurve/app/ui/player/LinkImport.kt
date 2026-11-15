package com.auracurve.app.ui.player

import com.auracurve.app.R
import com.auracurve.app.text.TextProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

// A long import that stopped half way (app closed, no connection), as the playlists tab shows it.
data class PendingImport(
    val title: String,
    val nextIndex: Int,
    val total: Int,
)

// Where an import reports to: the player screen's message line and the list of SoundCloud matches
// for a shared YouTube / Spotify song. Owned by PlayerViewModel, which hands it over once.
class LinkImportSession(
    val scope: CoroutineScope,
    val importState: MutableStateFlow<ImportUiState>,
    val bridgeState: MutableStateFlow<BridgeUiState?>,
)

// Pasted and shared links (AuraCurve roadmap 02, 1.3). The "full" flavor imports SoundCloud tracks
// and playlists and looks Spotify / YouTube songs up on SoundCloud (SoundCloudLinkImport); the
// "store" flavor has no online source and no link parser at all (NoLinkImport).
interface LinkImport {
    val pending: Flow<PendingImport?>

    fun attach(session: LinkImportSession)

    fun importText(text: String)

    fun resume()

    fun discard()
}

// No link import: the store flavor hides the link field, so this only answers a stray call.
class NoLinkImport(
    private val texts: TextProvider? = null,
) : LinkImport {
    private var session: LinkImportSession? = null

    override val pending: Flow<PendingImport?> = flowOf(null)

    override fun attach(session: LinkImportSession) {
        this.session = session
    }

    override fun importText(text: String) {
        val message = texts?.get(R.string.online_sources_unavailable).orEmpty()
        session?.importState?.value = ImportUiState(message = message, isError = true)
    }

    override fun resume() = Unit

    override fun discard() = Unit
}
