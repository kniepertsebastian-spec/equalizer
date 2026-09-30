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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardbasseq.eq.playlist.PlayerFormat
import com.hardbasseq.eq.playlist.SavedPlaylist
import com.hardbasseq.eq.ui.theme.HardBassCardBorder
import com.hardbasseq.eq.ui.theme.spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

// The full player: what the mini bar at the bottom of the main screen expands into.
// Cover, seek bar, previous/next, the current queue, and the saved playlists with
// the field for pasting a SoundCloud link.
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    var linkText by remember { mutableStateOf("") }

    // Coming back from the sign-in screen: pick up the new account state.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshAccount() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                }
                Text(
                    text = "Player",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenSearch) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.size(spacing.small))
                    Text("Suchen")
                }
            }
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
                    text = playing?.title ?: "Nichts läuft",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = playing?.artist ?: "Füge unten einen SoundCloud-Link ein oder nutze „Suchen“",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, HardBassCardBorder),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SoundCloud-Konto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text =
                                if (signedIn) {
                                    "Angemeldet. Mit einem Go-Abo laufen Titel in voller Länge."
                                } else {
                                    "Nicht angemeldet. Melde dich mit deinem Go-Konto an, damit Go-Titel in voller Länge laufen."
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (signedIn) {
                        TextButton(onClick = viewModel::signOut) { Text("Abmelden") }
                    } else {
                        Button(onClick = viewModel::signIn) { Text("Anmelden") }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, HardBassCardBorder),
            ) {
                Column(modifier = Modifier.padding(spacing.medium)) {
                    Text("Playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "SoundCloud-Link zu einem Titel oder einer Playlist einfügen, oder aus der SoundCloud-App teilen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        MaterialTheme.colorScheme.onSurfaceVariant
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
                    if (playlists.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = spacing.small))
                        playlists.forEach { playlist ->
                            PlaylistRow(
                                playlist = playlist,
                                onPlay = { viewModel.playPlaylist(playlist) },
                                onDelete = { viewModel.deletePlaylist(playlist) },
                            )
                        }
                    }
                }
            }
        }

        if (queue.isNotEmpty()) {
            item {
                Text("Warteschlange", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            itemsIndexed(queue, key = { index, track -> "${track.id}-$index" }) { index, track ->
                val isCurrent = nowPlaying?.queueIndex == index
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            .clickable { viewModel.playQueueIndex(index) }
                            .padding(horizontal = spacing.small, vertical = spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (track.durationMs > 0) {
                        Text(
                            text = PlayerFormat.duration(track.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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

@Composable
private fun PlaylistRow(
    playlist: SavedPlaylist,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.tracks.size} Titel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Playlist abspielen")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Playlist löschen")
        }
    }
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
