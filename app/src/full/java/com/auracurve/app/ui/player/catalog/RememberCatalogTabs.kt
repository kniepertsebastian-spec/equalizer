package com.auracurve.app.ui.player.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auracurve.app.R
import com.auracurve.app.player.catalog.AudiusGenre
import com.auracurve.app.player.catalog.RadioGenre
import com.auracurve.app.player.catalog.RadioStation
import com.auracurve.app.player.model.TrackItem
import com.auracurve.app.playlist.PlayerFormat
import com.auracurve.app.ui.equalizer.ChoiceChip
import com.auracurve.app.ui.player.PlayerTab
import com.auracurve.app.ui.player.TrackQueueMenu
import com.auracurve.app.ui.player.library.LibraryRow
import com.auracurve.app.ui.theme.SonicSteel
import com.auracurve.app.ui.theme.spacing

// Flavor "full": the Radio and Audius tabs of the player (AuraCurve roadmap 06, approved
// 15.11.2026: https only, two tabs, one genre at a time, play only - no saving yet).
@Composable
fun rememberCatalogTabs(
    onPlayNext: (TrackItem) -> Unit,
    onAddToQueue: (TrackItem) -> Unit,
): CatalogTabs {
    val radio: RadioViewModel = hiltViewModel()
    val audius: AudiusViewModel = hiltViewModel()
    val radioGenre by radio.genre.collectAsStateWithLifecycle()
    val radioState by radio.state.collectAsStateWithLifecycle()
    val audiusGenre by audius.genre.collectAsStateWithLifecycle()
    val audiusState by audius.state.collectAsStateWithLifecycle()
    val audiusQuery by audius.query.collectAsStateWithLifecycle()
    return CatalogTabs(listOf(PlayerTab.RADIO, PlayerTab.AUDIUS)) { tab ->
        when (tab) {
            PlayerTab.RADIO ->
                radioItems(
                    genre = radioGenre,
                    state = radioState,
                    onShown = radio::ensureLoaded,
                    onGenre = radio::selectGenre,
                    onRetry = radio::reload,
                    onPlay = radio::play,
                    onPlayNext = { onPlayNext(it.toTrack()) },
                    onAddToQueue = { onAddToQueue(it.toTrack()) },
                )
            PlayerTab.AUDIUS ->
                audiusItems(
                    genre = audiusGenre,
                    state = audiusState,
                    query = audiusQuery,
                    onShown = audius::ensureLoaded,
                    onGenre = audius::selectGenre,
                    onSearch = audius::search,
                    onShowTrending = audius::showTrending,
                    onRetry = audius::reload,
                    onPlay = audius::play,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                )
            else -> Unit
        }
    }
}

internal fun LazyListScope.radioItems(
    genre: RadioGenre,
    state: CatalogUiState<RadioStation>,
    onShown: () -> Unit,
    onGenre: (RadioGenre) -> Unit,
    onRetry: () -> Unit,
    onPlay: (RadioStation) -> Unit,
    onPlayNext: (RadioStation) -> Unit,
    onAddToQueue: (RadioStation) -> Unit,
) {
    item(key = "radio-head") {
        LaunchedEffect(Unit) { onShown() }
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            Text(stringResource(R.string.catalog_radio_intro), style = MaterialTheme.typography.bodySmall, color = SonicSteel)
            GenreChips(RadioGenre.entries.map { it.label }, RadioGenre.entries.indexOf(genre)) { onGenre(RadioGenre.entries[it]) }
        }
    }
    statusItems("radio", state, onRetry)
    itemsIndexed(state.items, key = { _, station -> "radio-${station.uuid}" }) { _, station ->
        LibraryRow(
            title = station.name,
            subtitle = stationDetail(station, stringResource(R.string.radio_live)),
            onPlay = { onPlay(station) },
            showCover = true,
            artworkUrl = station.faviconUrl,
            menu = {
                TrackQueueMenu(
                    trackTitle = station.name,
                    onPlayNext = { onPlayNext(station) },
                    onAddToQueue = { onAddToQueue(station) },
                )
            },
        )
    }
    if (state.items.isNotEmpty()) {
        item(key = "radio-source") { SourceNote(stringResource(R.string.catalog_radio_source)) }
    }
}

private fun LazyListScope.audiusItems(
    genre: AudiusGenre,
    state: CatalogUiState<TrackItem>,
    query: String,
    onShown: () -> Unit,
    onGenre: (AudiusGenre) -> Unit,
    onSearch: (String) -> Unit,
    onShowTrending: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (Int) -> Unit,
    onPlayNext: (TrackItem) -> Unit,
    onAddToQueue: (TrackItem) -> Unit,
) {
    item(key = "audius-head") {
        LaunchedEffect(Unit) { onShown() }
        var text by rememberSaveable { mutableStateOf(query) }
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            Text(stringResource(R.string.catalog_audius_intro), style = MaterialTheme.typography.bodySmall, color = SonicSteel)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.audius_search_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(text) }),
            )
            GenreChips(AudiusGenre.entries.map { it.label }, AudiusGenre.entries.indexOf(genre)) { onGenre(AudiusGenre.entries[it]) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text =
                        if (query.isBlank()) {
                            stringResource(
                                R.string.audius_trending,
                                genre.label,
                            )
                        } else {
                            stringResource(R.string.audius_results_for, query)
                        },
                    style = MaterialTheme.typography.titleSmall,
                )
                if (query.isNotBlank()) {
                    TextButton(onClick = {
                        text = ""
                        onShowTrending()
                    }) { Text(stringResource(R.string.audius_show_trending)) }
                }
            }
        }
    }
    statusItems("audius", state, onRetry)
    itemsIndexed(state.items, key = { index, track -> "audius-${track.id}-$index" }) { index, track ->
        LibraryRow(
            title = track.title,
            subtitle = listOf(track.artist, PlayerFormat.duration(track.durationMs)).filter { it.isNotBlank() }.joinToString(" · "),
            onPlay = { onPlay(index) },
            showCover = true,
            artworkUrl = track.artworkUrl,
            menu = { TrackQueueMenu(trackTitle = track.title, onPlayNext = { onPlayNext(track) }, onAddToQueue = { onAddToQueue(track) }) },
        )
    }
    if (state.items.isNotEmpty()) {
        item(key = "audius-source") { SourceNote(stringResource(R.string.catalog_audius_source)) }
    }
}

// Loading, failed (with retry) or empty - nothing while the list shows.
private fun <T> LazyListScope.statusItems(
    prefix: String,
    state: CatalogUiState<T>,
    onRetry: () -> Unit,
) {
    when {
        state.isLoading ->
            item(key = "$prefix-loading") { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
        state.error != null ->
            item(key = "$prefix-error") {
                Column {
                    Text(
                        text = stringResource(R.string.catalog_load_failed, state.error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
            }
        state.loaded && state.items.isEmpty() ->
            item(key = "$prefix-empty") {
                Text(stringResource(R.string.catalog_empty), style = MaterialTheme.typography.bodyMedium, color = SonicSteel)
            }
    }
}

@Composable
private fun GenreChips(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label -> ChoiceChip(text = label, selected = index == selectedIndex, onClick = { onSelect(index) }) }
    }
}

@Composable
private fun SourceNote(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = SonicSteel)
}

// "Live · DE · MP3 192 kbit/s": what the station is, without what is unknown.
private fun stationDetail(
    station: RadioStation,
    live: String,
): String {
    val bitrate = if (station.bitrateKbps > 0) "${station.bitrateKbps} kbit/s" else ""
    val format = listOf(station.codec, bitrate).filter { it.isNotBlank() && it != "UNKNOWN" }.joinToString(" ")
    return listOf(live, station.countryCode, format).filter { it.isNotBlank() }.joinToString(" · ")
}
