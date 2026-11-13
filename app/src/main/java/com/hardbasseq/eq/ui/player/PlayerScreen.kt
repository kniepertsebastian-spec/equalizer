package com.hardbasseq.eq.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.ProcessingSettings
import com.hardbasseq.eq.context.SoundContext
import com.hardbasseq.eq.playlist.PlayerFormat
import com.hardbasseq.eq.preset.TargetPoint
import com.hardbasseq.eq.ui.eq.EqStatus
import com.hardbasseq.eq.ui.eq.EqStatusLine
import com.hardbasseq.eq.ui.equalizer.ContextModeChips
import com.hardbasseq.eq.ui.equalizer.SonicStyle
import com.hardbasseq.eq.ui.equalizer.displayName
import com.hardbasseq.eq.ui.player.library.MergePlaylistsDialog
import com.hardbasseq.eq.ui.player.library.PlaylistDialog
import com.hardbasseq.eq.ui.player.library.PlaylistDialogRequest
import com.hardbasseq.eq.ui.player.library.playlistsTabItems
import com.hardbasseq.eq.ui.player.nowplaying.SeekBar
import com.hardbasseq.eq.ui.theme.SonicPaper
import com.hardbasseq.eq.ui.theme.SonicRule
import com.hardbasseq.eq.ui.theme.SonicSteel
import com.hardbasseq.eq.ui.theme.spacing
import com.soundcloud.equalizer.player.playback.RepeatSetting

// The full player: what the mini bar at the bottom of the main screen expands into.
// Cover, seek bar, previous/next, the current queue, and the saved playlists with
// the field for pasting a SoundCloud link.
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    discovery: DiscoveryViewModel,
    activeContext: SoundContext?,
    onContextModeChanged: (SoundContext?) -> Unit,
    eqState: AudioEngineState,
    processingSettings: ProcessingSettings,
    onOpenEqualizer: () -> Unit,
    onOpenServices: () -> Unit,
    soundName: String,
    soundCurve: List<TargetPoint>,
    dspViewModel: PlayerDspViewModel = hiltViewModel(),
) {
    val spacing = MaterialTheme.spacing
    val style = SonicStyle
    val dsp by dspViewModel.settings.collectAsStateWithLifecycle()
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
                    bottom = bars.calculateBottomPadding() + spacing.medium,
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
                val playing = nowPlaying
                val artworkDescription = stringResource(R.string.artwork_description)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ArtworkFrame(modifier = Modifier.fillMaxWidth(0.72f)) {
                        CoverImage(
                            url = PlayerFormat.largeArtwork(playing?.artworkUrl),
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .clip(MaterialTheme.shapes.medium)
                                    .semantics { contentDescription = artworkDescription },
                            placeholderIconSize = 64.dp,
                        )
                    }
                    Spacer(modifier = Modifier.height(spacing.medium))
                    Text(
                        text = playing?.title ?: stringResource(R.string.player_nothing_playing),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = style.text,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = playing?.artist ?: stringResource(R.string.player_idle_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = style.mutedText,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (playing?.isPreview == true) {
                        Text(
                            text = stringResource(R.string.preview_only),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(if (signedIn) R.string.preview_hint_account else R.string.preview_hint_sign_in),
                            style = MaterialTheme.typography.bodySmall,
                            color = style.mutedText,
                            textAlign = TextAlign.Center,
                        )
                    }
                    playing?.notice?.let { notice ->
                        Text(
                            text = noticeText(notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (notice.isProblem()) MaterialTheme.colorScheme.error else style.mutedText,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(modifier = Modifier.height(spacing.small))
                    SeekBar(
                        positionMs = playing?.positionMs ?: 0L,
                        durationMs = playing?.durationMs ?: 0L,
                        enabled = playing != null && !playing.isLoading,
                        onSeek = viewModel::seekTo,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        ShuffleButton(enabled = playing != null, on = playing?.shuffle == true, onToggle = viewModel::toggleShuffle)
                        IconButton(onClick = viewModel::previous, enabled = playing != null, modifier = Modifier.size(56.dp)) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = stringResource(R.string.player_previous),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        PlayKey(onClick = viewModel::togglePlayback, enabled = playing != null) {
                            if (playing?.isLoading == true) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp), color = style.background)
                            } else {
                                Icon(
                                    imageVector = if (playing?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription =
                                        if (playing?.isPlaying == true) {
                                            stringResource(R.string.player_pause)
                                        } else {
                                            stringResource(R.string.player_play)
                                        },
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }
                        IconButton(onClick = viewModel::next, enabled = playing != null, modifier = Modifier.size(56.dp)) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = stringResource(R.string.player_next),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        RepeatButton(
                            enabled = playing != null,
                            setting = playing?.repeat ?: RepeatSetting.OFF,
                            onCycle = viewModel::cycleRepeat,
                        )
                    }
                    IconButton(
                        onClick = { viewModel.currentTrack()?.let { playlistDialog = PlaylistDialogRequest(it) } },
                        enabled = viewModel.currentTrack() != null,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.queue_add_to_playlist))
                    }
                    PlayerSoundChip(
                        soundName = soundName,
                        curve = soundCurve,
                        contextName = activeContext?.displayName(),
                        onClick = onOpenEqualizer,
                    )
                }
            }

            item {
                PlayerTabs(selected = tab, onSelect = { tab = it }, queueCount = queue.size)
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

            if (tab == PlayerTab.QUEUE) {
                item {
                    Column {
                        Text(
                            text = stringResource(R.string.queue_title) + " · " + stringResource(R.string.queue_count, queue.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SonicPaper,
                        )
                        nowPlaying?.let { playing ->
                            Text(
                                text = stringResource(R.string.queue_now, playing.title),
                                style = MaterialTheme.typography.bodySmall,
                                color = SonicSteel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (playing.shuffle) {
                                Text(
                                    text = stringResource(R.string.queue_shuffle_order_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SonicSteel,
                                )
                            }
                        }
                    }
                }
                if (queue.isEmpty()) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.queue_empty_title),
                            text = stringResource(R.string.queue_empty_text),
                            actionLabel = stringResource(R.string.queue_go_search),
                            onAction = { tab = PlayerTab.SEARCH },
                        )
                    }
                } else {
                    itemsIndexed(queue, key = { index, track -> "${track.id}-$index" }) { index, track ->
                        val isCurrent = nowPlaying?.queueIndex == index
                        val playState = if (isCurrent) TrackPlayState.CURRENT else trackPlayState(track.id, currentTrackId, playedIds)
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .markWhenCurrent(playState)
                                    .clickable { viewModel.playQueueIndex(index) }
                                    .padding(horizontal = spacing.small, vertical = spacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { TrackStateIcon(playState) }
                            CoverThumb(url = track.artworkUrl)
                            Spacer(modifier = Modifier.size(spacing.small))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = SonicPaper.copy(alpha = playState.textAlpha()),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SonicSteel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (track.durationMs > 0) {
                                Text(
                                    text = PlayerFormat.duration(track.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SonicSteel,
                                )
                            }
                            QueueEntryMenu(
                                trackTitle = track.title,
                                canMoveUp = index > 0,
                                canMoveDown = index < queue.lastIndex,
                                onMoveUp = { viewModel.moveQueueItem(index, index - 1) },
                                onMoveDown = { viewModel.moveQueueItem(index, index + 1) },
                                onRemove = { viewModel.removeQueueItem(index) },
                                onAddToPlaylist = { playlistDialog = PlaylistDialogRequest(track) },
                            )
                        }
                    }
                }
            }

            item {
                // The listening-context mode is an overlay on the EQ (subsonic, loudness,
                // virtual bass, limiter), so it applies to everything playing here too.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, SonicRule),
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Text(
                            stringResource(R.string.context_mode_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.context_mode_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = SonicSteel,
                        )
                        Spacer(modifier = Modifier.height(spacing.small))
                        ContextModeChips(
                            activeContext = activeContext,
                            onContextModeChanged = onContextModeChanged,
                        )
                    }
                }
            }

            item {
                PlayerDspCard(
                    settings = dsp,
                    onMonoBass = dspViewModel::setMonoBass,
                    onCutoff = dspViewModel::setMonoBassCutoff,
                    onLimiter = dspViewModel::setLimiter,
                )
            }
        }
    }
}
