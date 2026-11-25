package com.auracurve.app.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.discovery.DiscoveryState
import com.auracurve.app.discovery.DiscoveryTrack
import com.auracurve.app.discovery.GenreMode
import com.auracurve.app.playlist.AgeFormat
import com.auracurve.app.playlist.PlayerFormat
import com.auracurve.app.ui.components.cyber.CyberCard
import com.auracurve.app.ui.components.cyber.CyberOutlineButton
import com.auracurve.app.ui.equalizer.ChoiceChip
import com.auracurve.app.ui.player.common.SwipeAwayRow
import com.auracurve.app.ui.theme.CyberColors
import com.auracurve.app.ui.theme.CyberDims
import com.auracurve.app.ui.theme.PillShape
import com.auracurve.app.ui.theme.SonicPaper
import com.auracurve.app.ui.theme.SonicSteel

// "Interesting new uploads" as items of the player screen's list: the artists to
// watch, and the weekly playlist with swipe-away rows.
fun LazyListScope.discoveryItems(
    state: DiscoveryState,
    ui: DiscoveryUiState,
    nowMs: Long,
    currentTrackId: Long?,
    playedIds: Set<Long>,
    onAddArtist: (String) -> Unit,
    onRemoveArtist: (String) -> Unit,
    onGenreMode: (GenreMode) -> Unit,
    onAddGenre: (String) -> Unit,
    onRemoveGenre: (String) -> Unit,
    onRefresh: () -> Unit,
    onPlayAll: () -> Unit,
    onPlay: (Int) -> Unit,
    onDismiss: (DiscoveryTrack) -> Unit,
) {
    item(key = "discovery-header") {
        // A HUD frame: the discovery is the tab's one tool card.
        CyberCard(modifier = Modifier.fillMaxWidth(), hud = true, contentPadding = PaddingValues(0.dp)) {
            DiscoveryHeader(state, ui, onAddArtist, onRemoveArtist, onGenreMode, onAddGenre, onRemoveGenre, onRefresh, onPlayAll)
        }
    }
    items(state.playlist, key = { "discovery-${it.track.id}" }) { item ->
        val index = state.playlist.indexOf(item)
        val playState = trackPlayState(item.track.id, currentTrackId, playedIds)
        SwipeAwayRow(onDismiss = { onDismiss(item) }) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .markWhenCurrent(playState)
                        .clickable { onPlay(index) }
                        .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { TrackStateIcon(playState) }
                CoverThumb(url = item.track.artworkUrl)
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
                    Text(
                        text = item.track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SonicPaper.copy(alpha = playState.textAlpha()),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            listOf(
                                item.track.artist,
                                item.genre,
                                PlayerFormat.duration(item.track.durationMs),
                                ageLabel(AgeFormat.daysSince(nowMs, item.uploadedAtMs)),
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = SonicSteel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onDismiss(item) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.discovery_dismiss), tint = SonicSteel)
                }
            }
        }
    }
}

@Composable
private fun DiscoveryHeader(
    state: DiscoveryState,
    ui: DiscoveryUiState,
    onAddArtist: (String) -> Unit,
    onRemoveArtist: (String) -> Unit,
    onGenreMode: (GenreMode) -> Unit,
    onAddGenre: (String) -> Unit,
    onRemoveGenre: (String) -> Unit,
    onRefresh: () -> Unit,
    onPlayAll: () -> Unit,
) {
    var artistText by remember { mutableStateOf("") }
    var genreText by remember { mutableStateOf("") }
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.discovery_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (ui.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.discovery_refresh))
                }
            }
        }
        Text(
            text =
                stringResource(R.string.discovery_intro),
            style = MaterialTheme.typography.bodySmall,
            color = SonicSteel,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = artistText,
                onValueChange = { artistText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.discovery_artist_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
            )
            Spacer(modifier = Modifier.size(8.dp))
            CyberOutlineButton(
                text = stringResource(R.string.discovery_remember),
                onClick = {
                    onAddArtist(artistText)
                    artistText = ""
                },
                enabled = artistText.isNotBlank(),
                techCorners = false,
                color = CyberColors.violet,
            )
        }
        if (state.artists.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.artists.forEach { artist ->
                    TagChip(
                        text = artist,
                        removeDescription = stringResource(R.string.discovery_remove_artist, artist),
                        onRemove = { onRemoveArtist(artist) },
                    )
                }
            }
        }
        if (state.artists.isNotEmpty()) {
            GenreSection(
                state = state,
                genreText = genreText,
                onGenreTextChange = { genreText = it },
                onGenreMode = onGenreMode,
                onAddGenre = {
                    onAddGenre(genreText)
                    genreText = ""
                },
                onRemoveGenre = onRemoveGenre,
            )
        }
        ui.message?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (ui.isError) MaterialTheme.colorScheme.error else SonicSteel,
            )
        }
        if (state.playlist.isNotEmpty()) {
            TextButton(onClick = onPlayAll) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.size(4.dp))
                Text(stringResource(R.string.discovery_play_all, state.playlist.size))
            }
        } else if (state.artists.isEmpty()) {
            Text(
                text = stringResource(R.string.discovery_no_artists),
                style = MaterialTheme.typography.bodySmall,
                color = SonicSteel,
            )
        }
    }
}

// Which genres an upload has to fit: off, the genres of the user's likes, or their own
// keywords - so a namesake artist in another genre does not show up.
@Composable
private fun GenreSection(
    state: DiscoveryState,
    genreText: String,
    onGenreTextChange: (String) -> Unit,
    onGenreMode: (GenreMode) -> Unit,
    onAddGenre: () -> Unit,
    onRemoveGenre: (String) -> Unit,
) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.discovery_genre_must_match),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            GenreMode.AUTO to stringResource(R.string.discovery_mode_auto),
            GenreMode.MANUAL to stringResource(R.string.discovery_mode_manual),
            GenreMode.OFF to stringResource(R.string.discovery_mode_off),
        ).forEach { (mode, label) ->
            ChoiceChip(text = label, selected = state.genreMode == mode, onClick = { onGenreMode(mode) })
        }
    }
    when (state.genreMode) {
        GenreMode.OFF ->
            Text(
                stringResource(R.string.discovery_genre_off_desc),
                style = MaterialTheme.typography.bodySmall,
                color = SonicSteel,
            )

        GenreMode.AUTO ->
            Text(
                text =
                    if (state.autoGenres.isEmpty()) {
                        stringResource(R.string.discovery_no_taste)
                    } else {
                        stringResource(R.string.discovery_taste_detected, state.autoGenres.joinToString(", "))
                    },
                style = MaterialTheme.typography.bodySmall,
                color = SonicSteel,
            )

        GenreMode.MANUAL -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = genreText,
                    onValueChange = onGenreTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            stringResource(R.string.discovery_genre_example),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.size(8.dp))
                CyberOutlineButton(
                    text = stringResource(R.string.discovery_add),
                    onClick = onAddGenre,
                    enabled = genreText.isNotBlank(),
                    techCorners = false,
                    color = CyberColors.violet,
                )
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.genres.forEach { genre ->
                    TagChip(
                        text = genre,
                        removeDescription = stringResource(R.string.discovery_remove_genre, genre),
                        onRemove = { onRemoveGenre(genre) },
                    )
                }
            }
        }
    }
}

// A remembered artist or genre: a magenta tag with its remove key.
@Composable
private fun TagChip(
    text: String,
    removeDescription: String,
    onRemove: () -> Unit,
) {
    Surface(
        shape = PillShape,
        color = CyberColors.magenta.copy(alpha = 0.16f),
        border = BorderStroke(CyberDims.border, CyberColors.magenta),
    ) {
        Row(modifier = Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = text, style = MaterialTheme.typography.labelMedium, color = SonicPaper)
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = removeDescription,
                    tint = CyberColors.magenta,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ageLabel(days: Long): String =
    when (days) {
        0L -> stringResource(R.string.age_today)
        1L -> stringResource(R.string.age_yesterday)
        else -> stringResource(R.string.age_days_ago, days)
    }
