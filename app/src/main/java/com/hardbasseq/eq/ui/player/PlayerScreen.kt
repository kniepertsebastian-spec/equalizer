package com.hardbasseq.eq.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.ProcessingSettings
import com.hardbasseq.eq.ui.eq.EqStatus
import com.hardbasseq.eq.ui.eq.EqStatusLine
import com.hardbasseq.eq.ui.player.library.MergePlaylistsDialog
import com.hardbasseq.eq.ui.player.library.PlaylistDialog
import com.hardbasseq.eq.ui.player.library.PlaylistDialogRequest
import com.hardbasseq.eq.ui.player.library.playlistsTabItems
import com.hardbasseq.eq.ui.theme.SonicPaper
import com.hardbasseq.eq.ui.theme.spacing

// The player tab: search (with the new uploads of watched artists) and the playlists with the
// field for pasting a link. What plays now lives in the shell: the mini player above the
// navigation bar and "Now playing" it expands into (ui/player/shell, docs/PLAYER_REDESIGN.md).
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    discovery: DiscoveryViewModel,
    eqState: AudioEngineState,
    processingSettings: ProcessingSettings,
    onOpenEqualizer: () -> Unit,
    onOpenServices: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val playedIds by viewModel.playedIds.collectAsStateWithLifecycle()
    val currentTrackId = queue.getOrNull(nowPlaying?.queueIndex ?: -1)?.id
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val bridgeState by viewModel.bridgeState.collectAsStateWithLifecycle()
    val discoveryState by discovery.state.collectAsStateWithLifecycle()
    val discoveryUi by discovery.uiState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val requestedTab by viewModel.requestedTab.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(PlayerTab.SEARCH) }
    var searchText by rememberSaveable { mutableStateOf("") }
    val eqStatusKind = EqStatus.kind(eqState, processingSettings)
    // A link shared into the app brings the playlists forward, where its import shows up.
    LaunchedEffect(requestedTab) {
        requestedTab?.let {
            tab = it
            viewModel.consumeRequestedTab()
        }
    }
    // What the playlist dialog is for: null = closed; a track = add it to a playlist;
    // no track = just make a new, empty playlist.
    var playlistDialog by remember { mutableStateOf<PlaylistDialogRequest?>(null) }
    var mergeDialogOpen by remember { mutableStateOf(false) }

    // Signed in (also right after coming back from the sign-in screen): show the library.
    LaunchedEffect(signedIn) { if (signedIn) viewModel.loadLibrary() }

    // Coming back from the sign-in screen: pick up the new account state.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshAccount() }

    // The screen draws edge to edge: keep its content clear of the status and
    // navigation bars. And outside a Card the default text color is near-black, which
    // is unreadable on the dark background - so the default is light here, with the
    // song title and artist colored explicitly below.
    val bars = WindowInsets.systemBars.asPaddingValues()
    playlistDialog?.let { request ->
        PlaylistDialog(
            track = request.track,
            playlists = playlists,
            onAddTo = { playlist ->
                request.track?.let { viewModel.addToPlaylist(playlist, it) }
                playlistDialog = null
            },
            onCreate = { name ->
                viewModel.createPlaylist(name, request.track)
                playlistDialog = null
            },
            onDismiss = { playlistDialog = null },
        )
    }
    if (mergeDialogOpen) {
        MergePlaylistsDialog(
            playlists = playlists,
            onMerge = { chosen, name ->
                viewModel.mergePlaylists(chosen, name)
                mergeDialogOpen = false
            },
            onDismiss = { mergeDialogOpen = false },
        )
    }
    CompositionLocalProvider(LocalContentColor provides SonicPaper) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = spacing.medium,
                    end = spacing.medium,
                    top = bars.calculateTopPadding() + spacing.medium,
                    // The shell below (mini player, navigation bar) already keeps the bottom clear.
                    bottom = spacing.medium,
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.nav_player),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onOpenServices) {
                        Icon(Icons.Default.Link, contentDescription = stringResource(R.string.player_open_services))
                    }
                    SourceSelector(onSelectSoundCloud = { tab = PlayerTab.SEARCH })
                }
            }

            item {
                EqStatusLine(
                    kind = eqStatusKind,
                    pathLabel = if (nowPlaying != null) stringResource(R.string.source_soundcloud) else null,
                    onClick = onOpenEqualizer,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                PlayerTabs(selected = tab, onSelect = { tab = it })
            }

            if (tab == PlayerTab.SEARCH) {
                searchItems(
                    text = searchText,
                    onTextChange = { searchText = it },
                    state = searchState,
                    onSearch = { viewModel.search(searchText) },
                    onPlay = viewModel::playSearchResult,
                    onPlayNext = viewModel::playNext,
                    onAddToQueue = viewModel::addToQueue,
                )

                discoveryItems(
                    state = discoveryState,
                    ui = discoveryUi,
                    nowMs = System.currentTimeMillis(),
                    currentTrackId = currentTrackId,
                    playedIds = playedIds,
                    onAddArtist = discovery::addArtist,
                    onRemoveArtist = discovery::removeArtist,
                    onGenreMode = discovery::setGenreMode,
                    onAddGenre = discovery::addGenre,
                    onRemoveGenre = discovery::removeGenre,
                    onRefresh = discovery::refreshNow,
                    onPlayAll = discovery::playAll,
                    onPlay = discovery::play,
                    onDismiss = discovery::dismiss,
                )
            }

            if (tab == PlayerTab.PLAYLISTS) {
                playlistsTabItems(
                    viewModel = viewModel,
                    playlists = playlists,
                    importState = importState,
                    pendingImport = pendingImport,
                    bridgeState = bridgeState,
                    signedIn = signedIn,
                    libraryState = libraryState,
                    currentTrackId = currentTrackId,
                    playedIds = playedIds,
                    onNewPlaylist = { playlistDialog = PlaylistDialogRequest(null) },
                    onMerge = { mergeDialogOpen = true },
                )
            }
        }
    }
}
