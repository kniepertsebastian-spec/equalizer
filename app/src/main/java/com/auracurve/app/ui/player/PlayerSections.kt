package com.auracurve.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.player.model.TrackItem
import com.auracurve.app.playlist.PlayerFormat
import com.auracurve.app.ui.equalizer.ChoiceChip
import com.auracurve.app.ui.equalizer.SonicStyle
import com.auracurve.app.ui.player.library.LibraryRow
import com.auracurve.app.ui.theme.PillShape
import com.auracurve.app.ui.theme.SonicPaper
import com.auracurve.app.ui.theme.SonicSteel
import com.auracurve.app.ui.theme.SonicViolet
import com.auracurve.app.ui.theme.spacing

// The three areas of the player screen, always reachable through the tab row.
enum class PlayerTab(
    val labelRes: Int,
) {
    SEARCH(R.string.tab_search),
    PLAYLISTS(R.string.tab_playlists),

    // Online catalogs of the full flavor (see rememberCatalogTabs); the store flavor has none.
    RADIO(R.string.tab_radio),
    AUDIUS(R.string.tab_audius),
}

@Composable
fun PlayerTabs(
    selected: PlayerTab,
    onSelect: (PlayerTab) -> Unit,
    tabs: List<PlayerTab> = PlayerTab.entries,
) {
    // Filter chips (pills) in a row that scrolls sideways when the tabs do not fit.
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { tab ->
            ChoiceChip(
                text = stringResource(tab.labelRes),
                selected = selected == tab,
                onClick = { onSelect(tab) },
                modifier = Modifier.semantics { role = Role.Tab },
            )
        }
    }
}

// "SoundCloud ▾": the visible source switch. SoundCloud is the built-in player (this screen);
// "other player" explains how Spotify & co. work.
@Composable
fun SourceSelector(onSelectSoundCloud: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    val current = stringResource(R.string.source_soundcloud)
    val description = stringResource(R.string.source_selector_description, current)
    Column {
        Row(
            modifier =
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(PillShape)
                    .background(SonicStyle.surfaceVariant)
                    .clickable(role = Role.Button) { menuOpen = true }
                    .semantics { contentDescription = description }
                    .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current, style = MaterialTheme.typography.labelLarge, color = SonicPaper)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = SonicSteel)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.source_soundcloud)) },
                trailingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onSelectSoundCloud()
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
    onPlayNext: (TrackItem) -> Unit,
    onAddToQueue: (TrackItem) -> Unit,
) {
    item {
        val spacing = MaterialTheme.spacing
        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            PillTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = stringResource(R.string.search_field_hint),
                leadingIcon = Icons.Default.Search,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                trailing = {
                    // The search key at the end of the field; a spinner while it searches.
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = SonicViolet)
                    } else {
                        PlayKey(onClick = onSearch, enabled = text.isNotBlank(), size = 40.dp, glow = false) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = stringResource(R.string.search_action),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                },
            )
        }
    }
    when (state.outcome) {
        SearchOutcome.IDLE ->
            if (!state.isLoading) {
                item {
                    Text(
                        text = stringResource(R.string.search_empty_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SonicSteel,
                    )
                }
            }

        SearchOutcome.NO_RESULTS ->
            item {
                Text(
                    text = stringResource(R.string.search_no_results, state.query),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SonicSteel,
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
                        color = SonicSteel,
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
                    showCover = true,
                    artworkUrl = track.artworkUrl,
                    menu = {
                        TrackQueueMenu(
                            trackTitle = track.title,
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                        )
                    },
                )
            }
    }
}

// A text field in the app's look: a pill on the raised surface, no outline, an optional symbol
// at the start and a control at the end.
@Composable
internal fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val style = SonicStyle
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        shape = PillShape,
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null, tint = style.mutedText) } },
        trailingIcon = trailing?.let { content -> { Box(modifier = Modifier.padding(end = 6.dp)) { content() } } },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = style.surface,
                unfocusedContainerColor = style.surface,
                disabledContainerColor = style.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedTextColor = style.text,
                unfocusedTextColor = style.text,
                cursorColor = style.accent,
                focusedPlaceholderColor = style.mutedText,
                unfocusedPlaceholderColor = style.mutedText,
            ),
    )
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
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = SonicPaper)
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = SonicSteel)
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
