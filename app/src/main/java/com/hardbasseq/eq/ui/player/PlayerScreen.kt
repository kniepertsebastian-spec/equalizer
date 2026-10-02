package com.hardbasseq.eq.ui.player

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
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
import com.hardbasseq.eq.integration.PlayerSource
import com.hardbasseq.eq.playlist.PlayerFormat
import com.hardbasseq.eq.playlist.PlaylistSearch
import com.hardbasseq.eq.playlist.SavedPlaylist
import com.hardbasseq.eq.ui.eq.EqStatus
import com.hardbasseq.eq.ui.eq.EqStatusLine
import com.hardbasseq.eq.ui.equalizer.ContextModeChips
import com.hardbasseq.eq.ui.theme.HardBassCardBorder
import com.hardbasseq.eq.ui.theme.PlayerArtistColor
import com.hardbasseq.eq.ui.theme.PlayerTextColor
import com.hardbasseq.eq.ui.theme.PlayerTextMutedColor
import com.hardbasseq.eq.ui.theme.PlayerTitleColor
import com.hardbasseq.eq.ui.theme.spacing
import com.soundcloud.equalizer.player.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

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
    onSelectSource: (PlayerSource) -> Unit,
    dspViewModel: PlayerDspViewModel = hiltViewModel(),
) {
    val spacing = MaterialTheme.spacing
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
    var linkText by remember { mutableStateOf("") }
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
    CompositionLocalProvider(LocalContentColor provides PlayerTextColor) {
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
                    SourceSelector(
                        onSelectSource = { source ->
                            if (source == PlayerSource.SOUNDCLOUD) tab = PlayerTab.SEARCH else onSelectSource(source)
                        },
                    )
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ArtworkImage(
                        url = PlayerFormat.largeArtwork(playing?.artworkUrl),
                        modifier =
                            Modifier
                                .fillMaxWidth(0.7f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp)),
                    )
                    Spacer(modifier = Modifier.height(spacing.medium))
                    Text(
                        text = playing?.title ?: stringResource(R.string.player_nothing_playing),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PlayerTitleColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = playing?.artist ?: stringResource(R.string.player_idle_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (playing != null) PlayerArtistColor else PlayerTextMutedColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (playing?.isPreview == true) {
                        Text(
                            text = "Nur 30-Sekunden-Vorschau",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
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
                        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                    ) {
                        IconButton(onClick = viewModel::previous, enabled = playing != null) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Zurück", modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = viewModel::togglePlayback, enabled = playing != null, modifier = Modifier.size(64.dp)) {
                            if (playing?.isLoading == true) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            } else {
                                Icon(
                                    imageVector = if (playing?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playing?.isPlaying == true) "Pause" else "Abspielen",
                                    modifier = Modifier.size(48.dp),
                                )
                            }
                        }
                        IconButton(onClick = viewModel::next, enabled = playing != null) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Weiter", modifier = Modifier.size(36.dp))
                        }
                        IconButton(
                            onClick = { viewModel.currentTrack()?.let { playlistDialog = PlaylistDialogRequest(it) } },
                            enabled = viewModel.currentTrack() != null,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Zur Playlist hinzufügen")
                        }
                    }
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
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, HardBassCardBorder),
                    ) {
                        Column(modifier = Modifier.padding(spacing.medium)) {
                            Text(
                                stringResource(R.string.playlists_import_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text =
                                    "Link einfügen oder teilen: SoundCloud-Titel/-Playlist, einzelne YouTube-/Spotify-Titel oder eine " +
                                        "öffentliche Spotify-Playlist (wird auf SoundCloud gesucht).",
                                style = MaterialTheme.typography.bodySmall,
                                color = PlayerTextMutedColor,
                            )
                            Spacer(modifier = Modifier.height(spacing.small))
                            OutlinedTextField(
                                value = linkText,
                                onValueChange = { linkText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("https://soundcloud.com/…") },
                                singleLine = true,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (importState.isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                                importState.message?.let { message ->
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color =
                                            if (importState.isError) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                PlayerTextMutedColor
                                            },
                                        modifier = Modifier.weight(1f).padding(horizontal = spacing.small),
                                    )
                                } ?: Spacer(modifier = Modifier.weight(1f))
                                Button(
                                    onClick = {
                                        viewModel.importFromText(linkText)
                                        linkText = ""
                                    },
                                    enabled = linkText.isNotBlank() && !importState.isLoading,
                                ) {
                                    Text("Hinzufügen")
                                }
                            }
                            // A long Spotify import that stopped (app closed, no connection): go on or drop it.
                            // While one is running the saved position is only a checkpoint, not a pause.
                            pendingImport?.takeIf { !importState.isLoading }?.let { pending ->
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Spotify-Import „${pending.title}“ angehalten: ${pending.nextIndex} von ${pending.total}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PlayerTextMutedColor,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = viewModel::resumeSpotifyImport) { Text("Fortsetzen") }
                                    TextButton(onClick = viewModel::discardSpotifyImport) { Text("Verwerfen") }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { playlistDialog = PlaylistDialogRequest(null) }) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                                    Spacer(modifier = Modifier.size(spacing.small))
                                    Text("Neue Playlist")
                                }
                                if (playlists.size >= 2) {
                                    TextButton(onClick = { mergeDialogOpen = true }) { Text("Zusammenführen") }
                                }
                                if (playlists.isNotEmpty()) {
                                    TextButton(onClick = { viewModel.syncToSoundCloud(playlists) }, enabled = !importState.isLoading) {
                                        Text("Alle zu SoundCloud")
                                    }
                                }
                            }
                            if (playlists.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = spacing.small))
                                playlists.forEach { playlist ->
                                    PlaylistRow(
                                        playlist = playlist,
                                        onPlay = { viewModel.playPlaylist(playlist) },
                                        onPlayTrack = { viewModel.playPlaylist(playlist, it) },
                                        onDelete = { viewModel.deletePlaylist(playlist) },
                                        onSendToSoundCloud = { viewModel.syncToSoundCloud(listOf(playlist)) },
                                        onRemoveTrack = { viewModel.removeFromPlaylist(playlist, it) },
                                        currentTrackId = currentTrackId,
                                        playedIds = playedIds,
                                    )
                                }
                            }
                        }
                    }
                }
                if (playlists.isEmpty()) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.playlists_empty_title),
                            text = stringResource(R.string.playlists_empty_text),
                            actionLabel = stringResource(R.string.playlists_new_button),
                            onAction = { playlistDialog = PlaylistDialogRequest(null) },
                        )
                    }
                }
                bridgeState?.let { bridge ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, HardBassCardBorder),
                        ) {
                            Column(modifier = Modifier.padding(spacing.medium)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Auf SoundCloud gefunden: ${bridge.label}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = viewModel::dismissBridge) { Text("Schließen") }
                                }
                                Text(
                                    text =
                                        if (bridge.startedAutomatically) {
                                            "Der beste Treffer läuft. Nicht der richtige? Wähl einen anderen:"
                                        } else {
                                            "Kein sicherer Treffer - wähle den passenden Titel:"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PlayerTextMutedColor,
                                )
                                bridge.matches.forEach { match ->
                                    LibraryRow(
                                        title = match.track.title,
                                        subtitle =
                                            listOf(
                                                match.track.artist,
                                                PlayerFormat.duration(match.track.durationMs),
                                                "${(match.score * 100).toInt()} %",
                                            ).joinToString(" · "),
                                        onPlay = { viewModel.playBridgeMatch(match) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (signedIn) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, HardBassCardBorder),
                        ) {
                            Column(modifier = Modifier.padding(spacing.medium)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Meine SoundCloud-Bibliothek",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (libraryState.isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        IconButton(onClick = viewModel::loadLibrary) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Bibliothek neu laden")
                                        }
                                    }
                                }
                                libraryState.message?.let { message ->
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (libraryState.isError) MaterialTheme.colorScheme.error else PlayerTextMutedColor,
                                    )
                                }
                                LibraryRow(
                                    title = "Likes",
                                    subtitle = "Alle Titel, die du geliked hast",
                                    onPlay = viewModel::playLikedTracks,
                                )
                            }
                        }
                    }
                    libraryState.library?.let { library ->
                        if (library.own.isNotEmpty()) {
                            item { Text("Meine Playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                            items(library.own, key = { "own-${it.id}" }) { playlist ->
                                LibraryRow(
                                    title = playlist.title,
                                    subtitle = "${playlist.trackCount} Titel",
                                    onPlay = { viewModel.playLibraryPlaylist(playlist) },
                                )
                            }
                        }
                        if (library.liked.isNotEmpty()) {
                            item { Text("Gelikte Playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                            items(library.liked, key = { "liked-${it.id}" }) { playlist ->
                                LibraryRow(
                                    title = playlist.title,
                                    subtitle = "${playlist.trackCount} Titel",
                                    onPlay = { viewModel.playLibraryPlaylist(playlist) },
                                )
                            }
                        }
                    }
                }
            }

            if (tab == PlayerTab.QUEUE) {
                item {
                    Column {
                        Text(
                            text = stringResource(R.string.queue_title) + " · " + stringResource(R.string.queue_count, queue.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PlayerTextColor,
                        )
                        nowPlaying?.let { playing ->
                            Text(
                                text = stringResource(R.string.queue_now, playing.title),
                                style = MaterialTheme.typography.bodySmall,
                                color = PlayerTextMutedColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
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
                                    .glowWhenCurrent(playState)
                                    .clickable { viewModel.playQueueIndex(index) }
                                    .padding(horizontal = spacing.small, vertical = spacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { TrackStateIcon(playState) }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = PlayerTitleColor.copy(alpha = playState.textAlpha()),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PlayerArtistColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (track.durationMs > 0) {
                                Text(
                                    text = PlayerFormat.duration(track.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PlayerTextMutedColor,
                                )
                            }
                            IconButton(onClick = { playlistDialog = PlaylistDialogRequest(track) }) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Zur Playlist hinzufügen")
                            }
                        }
                    }
                }
            }

            item {
                // The listening-context mode is an overlay on the EQ (subsonic, loudness,
                // virtual bass, limiter), so it applies to everything playing here too.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, HardBassCardBorder),
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Text("Klangmodus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Bleibt an, auch wenn du das Preset wechselst.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PlayerTextMutedColor,
                        )
                        Spacer(modifier = Modifier.height(spacing.small))
                        ContextModeChips(
                            activeContext = activeContext,
                            onContextModeChanged = onContextModeChanged,
                            accent = PlayerTitleColor,
                            border = HardBassCardBorder,
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

// Dragging shows where the thumb is without seeking on every frame; the seek
// happens once, when the finger lifts.
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: PlayerFormat.progress(positionMs, durationMs)
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * durationMs).toLong()) }
                dragFraction = null
            },
            enabled = enabled && durationMs > 0,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = PlayerFormat.duration(dragFraction?.let { (it * durationMs).toLong() } ?: positionMs),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(text = PlayerFormat.duration(durationMs), style = MaterialTheme.typography.labelSmall)
        }
    }
}

// One tappable entry of the SoundCloud library: tap anywhere (or the play button) to
// load its tracks and play them as the queue.
@Composable
internal fun LibraryRow(
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onPlay)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = PlayerTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = PlayerTextMutedColor)
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Abspielen")
        }
    }
}

// A saved playlist: tap the name to show its tracks. Tracks of playlists made on the
// device can be removed there; imported ones are refreshed by importing the link again.
@Composable
private fun PlaylistRow(
    playlist: SavedPlaylist,
    onPlay: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onDelete: () -> Unit,
    onSendToSoundCloud: () -> Unit,
    onRemoveTrack: (Long) -> Unit,
    currentTrackId: Long?,
    playedIds: Set<Long>,
) {
    var expanded by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expanded = !expanded },
            ) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PlayerTitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${playlist.tracks.size} Titel" + if (playlist.soundCloudId != null) " · bei SoundCloud" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlayerTextMutedColor,
                )
            }
            IconButton(onClick = onSendToSoundCloud, enabled = playlist.tracks.isNotEmpty()) {
                Icon(Icons.Default.CloudUpload, contentDescription = "Zu SoundCloud übertragen")
            }
            IconButton(onClick = onPlay, enabled = playlist.tracks.isNotEmpty()) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Playlist abspielen")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Playlist löschen")
            }
        }
        if (expanded) {
            if (playlist.tracks.isEmpty()) {
                Text(
                    text = "Noch leer – füge Titel über das Playlist-Symbol im Player oder in der Warteschlange hinzu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlayerTextMutedColor,
                )
            }
            // A search box once the list is long enough to need one.
            if (playlist.tracks.size >= SEARCH_MIN_TRACKS) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("In dieser Playlist suchen") },
                    singleLine = true,
                )
            }
            val hits = PlaylistSearch.filter(playlist.tracks, search)
            if (hits.isEmpty() && playlist.tracks.isNotEmpty()) {
                Text(text = "Kein Titel passt zu „$search“.", style = MaterialTheme.typography.bodySmall, color = PlayerTextMutedColor)
            }
            hits.forEach { hit ->
                val track = hit.track
                val playState = trackPlayState(track.id, currentTrackId, playedIds)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp)
                            .glowWhenCurrent(playState)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPlayTrack(hit.index) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { TrackStateIcon(playState) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = PlayerTitleColor.copy(alpha = playState.textAlpha()),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = PlayerArtistColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onPlayTrack(hit.index) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Ab diesem Titel abspielen")
                    }
                    if (playlist.sourceUrl == null) {
                        IconButton(onClick = { onRemoveTrack(track.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Aus Playlist entfernen")
                        }
                    }
                }
            }
        }
    }
}

// From this many tracks on, an opened playlist shows a search box.
private const val SEARCH_MIN_TRACKS = 6

// Opens the playlist dialog: with a track it adds that track somewhere, without one it
// only makes a new playlist.
private data class PlaylistDialogRequest(
    val track: TrackItem?,
)

// Pick two or more playlists and a name: one new playlist with all their tracks, each once.
@Composable
private fun MergePlaylistsDialog(
    playlists: List<SavedPlaylist>,
    onMerge: (List<SavedPlaylist>, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var chosenIds by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playlists zusammenführen") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name der neuen Playlist") },
                    singleLine = true,
                )
                playlists.forEach { playlist ->
                    val checked = playlist.id in chosenIds
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { chosenIds = if (checked) chosenIds - playlist.id else chosenIds + playlist.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(
                            text = "${playlist.title} (${playlist.tracks.size})",
                            modifier = Modifier.padding(start = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onMerge(playlists.filter { it.id in chosenIds }, name) },
                enabled = chosenIds.size >= 2 && name.isNotBlank(),
            ) { Text("Zusammenführen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun PlaylistDialog(
    track: TrackItem?,
    playlists: List<SavedPlaylist>,
    onAddTo: (SavedPlaylist) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    // Only playlists made on the device take tracks: imported ones mirror their link.
    val local = playlists.filter { it.sourceUrl == null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (track != null) "Zur Playlist hinzufügen" else "Neue Playlist") },
        text = {
            Column {
                if (track != null) {
                    Text(text = track.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    local.forEach { playlist ->
                        TextButton(onClick = { onAddTo(playlist) }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${playlist.title} (${playlist.tracks.size})",
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    HorizontalDivider()
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name der neuen Playlist") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text(if (track != null) "Neu anlegen und hinzufügen" else "Anlegen")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// No image-loading library in this app, and one cover at a time is all this screen
// shows: a plain download on the IO dispatcher, re-run whenever the URL changes.
@Composable
private fun ArtworkImage(
    url: String?,
    modifier: Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, url) {
        value = null
        if (url != null) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val connection =
                            URL(url).openConnection().apply {
                                connectTimeout = 10_000
                                readTimeout = 10_000
                            }
                        connection.getInputStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                    }.getOrNull()
                }
        }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(bitmap = loaded, contentDescription = "Cover", contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(64.dp))
        }
    }
}
