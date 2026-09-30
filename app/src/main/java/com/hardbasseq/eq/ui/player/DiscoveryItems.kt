package com.hardbasseq.eq.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.discovery.DiscoveryState
import com.hardbasseq.eq.discovery.DiscoveryTrack
import com.hardbasseq.eq.playlist.AgeFormat
import com.hardbasseq.eq.playlist.PlayerFormat
import com.hardbasseq.eq.ui.theme.HardBassCardBorder
import com.hardbasseq.eq.ui.theme.PlayerArtistColor
import com.hardbasseq.eq.ui.theme.PlayerTextColor
import com.hardbasseq.eq.ui.theme.PlayerTextMutedColor
import com.hardbasseq.eq.ui.theme.PlayerTitleColor
import kotlin.math.abs
import kotlin.math.roundToInt

// Share of the row's width a swipe has to cover to count as "away".
private const val SWIPE_AWAY_FRACTION = 0.4f

// "Interesting new uploads" as items of the player screen's list: the artists to
// watch, and the weekly playlist with swipe-away rows.
fun LazyListScope.discoveryItems(
    state: DiscoveryState,
    ui: DiscoveryUiState,
    nowMs: Long,
    onAddArtist: (String) -> Unit,
    onRemoveArtist: (String) -> Unit,
    onRefresh: () -> Unit,
    onPlayAll: () -> Unit,
    onPlay: (Int) -> Unit,
    onDismiss: (DiscoveryTrack) -> Unit,
) {
    item(key = "discovery-header") {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, HardBassCardBorder),
        ) {
            DiscoveryHeader(state, ui, onAddArtist, onRemoveArtist, onRefresh, onPlayAll)
        }
    }
    items(state.playlist, key = { "discovery-${it.track.id}" }) { item ->
        val index = state.playlist.indexOf(item)
        SwipeAwayRow(onDismiss = { onDismiss(item) }) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPlay(index) }
                        .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        text = item.track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PlayerTitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            listOf(
                                item.track.artist,
                                PlayerFormat.duration(item.track.durationMs),
                                AgeFormat.daysAgo(nowMs, item.uploadedAtMs),
                            ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = PlayerArtistColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onDismiss(item) }) {
                    Icon(Icons.Default.Close, contentDescription = "Nicht mehr vorschlagen", tint = PlayerTextMutedColor)
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
    onRefresh: () -> Unit,
    onPlayAll: () -> Unit,
) {
    var artistText by remember { mutableStateOf("") }
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Interesting new uploads",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (ui.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Jetzt aktualisieren")
                }
            }
        }
        Text(
            text =
                "Neue Uploads deiner Künstler - auch von anderen hochgeladen. " +
                    "15-20 Titel, wechselt jeden Montag. Wisch einen Titel weg, dann kommt er nie wieder.",
            style = MaterialTheme.typography.bodySmall,
            color = PlayerTextMutedColor,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = artistText,
                onValueChange = { artistText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Künstlername, z. B. Angerfist") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Button(
                onClick = {
                    onAddArtist(artistText)
                    artistText = ""
                },
                enabled = artistText.isNotBlank(),
            ) {
                Text("Merken")
            }
        }
        if (state.artists.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.artists.forEach { artist ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, HardBassCardBorder),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = artist, style = MaterialTheme.typography.labelMedium, color = PlayerTextColor)
                            IconButton(onClick = { onRemoveArtist(artist) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "$artist entfernen", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        ui.message?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (ui.isError) MaterialTheme.colorScheme.error else PlayerTextMutedColor,
            )
        }
        if (state.playlist.isNotEmpty()) {
            TextButton(onClick = onPlayAll) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.size(4.dp))
                Text("Alles abspielen (${state.playlist.size} Titel)")
            }
        } else if (state.artists.isEmpty()) {
            Text(
                text = "Noch keine Künstler - trag oben einen Namen ein.",
                style = MaterialTheme.typography.bodySmall,
                color = PlayerTextMutedColor,
            )
        }
    }
}

// A row that can be swiped sideways out of the list. Follows the finger; past 40 % of
// its width it counts as dismissed, otherwise it snaps back. The close button on the row
// does the same without a gesture.
@Composable
private fun SwipeAwayRow(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .onSizeChanged { widthPx = it.width.toFloat() },
    ) {
        Box(
            modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(offsetX) > widthPx * SWIPE_AWAY_FRACTION) currentOnDismiss() else offsetX = 0f
                            },
                            onDragCancel = { offsetX = 0f },
                        ) { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount
                        }
                    },
        ) {
            content()
        }
    }
}
