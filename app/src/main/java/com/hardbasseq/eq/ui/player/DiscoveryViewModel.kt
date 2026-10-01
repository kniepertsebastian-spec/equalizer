package com.hardbasseq.eq.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardbasseq.eq.discovery.DiscoveryRepository
import com.hardbasseq.eq.discovery.DiscoveryRotation
import com.hardbasseq.eq.discovery.DiscoveryState
import com.hardbasseq.eq.discovery.DiscoveryTrack
import com.hardbasseq.eq.discovery.DiscoveryUpdater
import com.hardbasseq.eq.discovery.GenreMode
import com.hardbasseq.eq.discovery.RefreshResult
import com.hardbasseq.eq.integration.PlayerController
import com.soundcloud.equalizer.player.model.TrackItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

// Feedback line of the discovery section: what a refresh just did (or why it failed).
data class DiscoveryUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

// "Interesting new uploads": watched artists, the weekly playlist built from their
// recent SoundCloud uploads, swipe-away, and playing it.
@HiltViewModel
class DiscoveryViewModel
    @Inject
    constructor(
        private val repository: DiscoveryRepository,
        private val updater: DiscoveryUpdater,
        private val controller: PlayerController,
    ) : ViewModel() {
        val state: StateFlow<DiscoveryState> =
            repository.state.stateIn(viewModelScope, SharingStarted.Eagerly, DiscoveryState())

        private val _uiState = MutableStateFlow(DiscoveryUiState())
        val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

        init {
            // Opening the screen in a new week rebuilds the list right away (the
            // background job may not have run yet).
            refresh(onlyIfDue = true)
        }

        fun addArtist(name: String) {
            viewModelScope.launch {
                val before = repository.current().artists.size
                repository.update { DiscoveryRotation.addArtist(it, name) }
                if (repository.current().artists.size == before) {
                    _uiState.value = DiscoveryUiState(message = "Den Namen gibt es schon (oder er ist ungültig)", isError = true)
                } else {
                    // A new artist forces a rebuild, so their uploads show up right away.
                    refresh(onlyIfDue = true)
                }
            }
        }

        fun removeArtist(name: String) {
            viewModelScope.launch { repository.update { DiscoveryRotation.removeArtist(it, name) } }
        }

        // Genre filter: all three changes force a rebuild, so the list follows right away.
        fun setGenreMode(mode: GenreMode) {
            viewModelScope.launch {
                repository.update { DiscoveryRotation.setGenreMode(it, mode) }
                refresh(onlyIfDue = true)
            }
        }

        fun addGenre(name: String) {
            viewModelScope.launch {
                repository.update { DiscoveryRotation.addGenre(it, name) }
                refresh(onlyIfDue = true)
            }
        }

        fun removeGenre(name: String) {
            viewModelScope.launch {
                repository.update { DiscoveryRotation.removeGenre(it, name) }
                refresh(onlyIfDue = true)
            }
        }

        fun refreshNow() = refresh(onlyIfDue = false)

        fun dismiss(track: DiscoveryTrack) {
            viewModelScope.launch { repository.update { DiscoveryRotation.dismiss(it, track.track.id) } }
        }

        fun play(index: Int) {
            val tracks = state.value.playlist.map { it.toTrackItem() }
            if (tracks.isEmpty()) return
            controller.playQueue(tracks, index.coerceIn(0, tracks.lastIndex))
        }

        fun playAll() = play(0)

        private fun refresh(onlyIfDue: Boolean) {
            viewModelScope.launch {
                _uiState.value = DiscoveryUiState(isLoading = true, message = "Suche neue Uploads …")
                val now = System.currentTimeMillis()
                val offset = TimeZone.getDefault().getOffset(now).toLong()
                _uiState.value =
                    when (val result = updater.refresh(now, offset, onlyIfDue)) {
                        RefreshResult.Skipped -> DiscoveryUiState()
                        is RefreshResult.Updated -> DiscoveryUiState(message = updatedMessage(result))
                        is RefreshResult.Failed -> DiscoveryUiState(message = result.message, isError = true)
                    }
            }
        }

        private fun updatedMessage(result: RefreshResult.Updated): String {
            val base = if (result.count == 0) "Keine neuen Uploads gefunden" else "${result.count} Titel in der Liste"
            return if (result.filteredByGenre > 0) "$base (${result.filteredByGenre} wegen Genre aussortiert)" else base
        }

        // No stream URL on purpose - the player resolves a fresh one by id when it plays.
        private fun DiscoveryTrack.toTrackItem() =
            TrackItem(
                id = track.id,
                title = track.title,
                artist = track.artist,
                artworkUrl = track.artworkUrl,
                streamUrl = null,
                durationMs = track.durationMs,
                createdAtMs = uploadedAtMs,
            )
    }
