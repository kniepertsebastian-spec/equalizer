package com.hardbasseq.eq.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.integration.PlayerSource
import com.hardbasseq.eq.playlist.PlayerFormat
import com.hardbasseq.eq.ui.theme.PlayerTextMutedColor
import com.hardbasseq.eq.ui.theme.PlayerTitleColor
import com.hardbasseq.eq.ui.theme.spacing

// The three areas of the player screen, always reachable through the tab row.
enum class PlayerTab(
    val labelRes: Int,
) {
    SEARCH(R.string.tab_search),
    PLAYLISTS(R.string.tab_playlists),
    QUEUE(R.string.tab_queue),
}

@Composable
fun PlayerTabs(
    selected: PlayerTab,
    onSelect: (PlayerTab) -> Unit,
    queueCount: Int,
) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = Color.Transparent,
        contentColor = PlayerTitleColor,
    ) {
        PlayerTab.entries.forEach { tab ->
            val label =
                if (tab == PlayerTab.QUEUE && queueCount > 0) {
                    "${stringResource(tab.labelRes)} ($queueCount)"
                } else {
                    stringResource(tab.labelRes)
                }
            Tab(selected = selected == tab, onClick = { onSelect(tab) }, text = { Text(label, maxLines = 1) })
        }
    }
}

// "SoundCloud ▾": the visible source switch. SoundCloud is the built-in player (this screen);
// YouTube opens its own player screen; "other player" explains how Spotify & co. work.
@Composable
fun SourceSelector(onSelectSource: (PlayerSource) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    val current = stringResource(R.string.source_soundcloud)
    val description = stringResource(R.string.source_selector_description, current)
    Column {
        TextButton(
            onClick = { menuOpen = true },
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            Text(current)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.source_soundcloud)) },
                trailingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onSelectSource(PlayerSource.SOUNDCLOUD)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.source_youtube)) },
                onClick = {
                    menuOpen = false
                    onSelectSource(PlayerSource.YOUTUBE)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.source_other)) },
                onClick = {
                    menuOpen = false
                    infoOpen = true
                },
            )
        }
    }
    if (infoOpen) {
        AlertDialog(
            onDismissRequest = { infoOpen = false },
            title = { Text(stringResource(R.string.source_other_title)) },
            text = { Text(stringResource(R.string.source_other_text)) },
            confirmButton = { TextButton(onClick = { infoOpen = false }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

// The search tab: a field, and below it the results (or a loading / empty / error explanation).
fun LazyListScope.searchItems(
    text: String,
    onTextChange: (String) -> Unit,
    state: SearchUiState,
    onSearch: () -> Unit,
    onPlay: (Int) -> Unit,
) {
    item {
        val spacing = MaterialTheme.spacing
        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_field_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = onSearch, enabled = text.isNotBlank() && !state.isLoading) {
                    Text(stringResource(R.string.search_action))
                }
            }
        }
    }
    when (state.outcome) {
        SearchOutcome.IDLE ->
            if (!state.isLoading) {
                item {
                    Text(
                        text = stringResource(R.string.search_empty_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PlayerTextMutedColor,
                    )
                }
            }

        SearchOutcome.NO_RESULTS ->
            item {
                Text(
                    text = stringResource(R.string.search_no_results, state.query),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlayerTextMutedColor,
                )
            }

        SearchOutcome.FAILED ->
            item {
                Column {
                    Text(
                        text = stringResource(R.string.search_failed, state.errorDetail.orEmpty()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(R.string.search_offline_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = PlayerTextMutedColor,
                    )
                    TextButton(onClick = onSearch) { Text(stringResource(R.string.action_retry)) }
                }
            }

        SearchOutcome.RESULTS ->
            itemsIndexed(state.results, key = { index, track -> "search-${track.id}-$index" }) { index, track ->
                LibraryRow(
                    title = track.title,
                    subtitle =
                        listOf(track.artist, PlayerFormat.duration(track.durationMs))
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                    onPlay = { onPlay(index) },
                )
            }
    }
}

// An empty area with what is missing and one next action.
@Composable
fun EmptyState(
    title: String,
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = PlayerTitleColor)
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = PlayerTextMutedColor)
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
