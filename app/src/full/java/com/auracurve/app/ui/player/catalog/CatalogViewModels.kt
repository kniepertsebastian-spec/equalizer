package com.auracurve.app.ui.player.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auracurve.app.integration.PlayerController
import com.auracurve.app.player.catalog.AudiusCatalog
import com.auracurve.app.player.catalog.AudiusGenre
import com.auracurve.app.player.catalog.RadioCatalog
import com.auracurve.app.player.catalog.RadioGenre
import com.auracurve.app.player.catalog.RadioStation
import com.auracurve.app.player.model.TrackItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// What a catalog tab shows: loading, the entries, or why there are none.
data class CatalogUiState<T>(
    val isLoading: Boolean = false,
    val items: List<T> = emptyList(),
    val error: String? = null,
    val loaded: Boolean = false,
)

// Shared loading logic: one request at a time, a newer one replaces a running one.
abstract class CatalogViewModel<T> : ViewModel() {
    private val _state = MutableStateFlow(CatalogUiState<T>())
    val state: StateFlow<CatalogUiState<T>> = _state.asStateFlow()
    private var job: Job? = null

    protected abstract suspend fun fetch(): List<T>

    /** Loads the first time the tab is shown, not before: no request without a reason. */
    fun ensureLoaded() {
        if (!_state.value.loaded && job == null) reload()
    }

    fun reload() {
        job?.cancel()
        _state.value = _state.value.copy(isLoading = true, error = null)
        job =
            viewModelScope.launch {
                _state.value =
                    try {
                        CatalogUiState(items = fetch(), loaded = true)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        CatalogUiState(error = e.message ?: e.javaClass.simpleName, loaded = true)
                    }
            }
    }
}

// Electronic internet radio (Radio Browser), one genre at a time.
@HiltViewModel
class RadioViewModel
    @Inject
    constructor(
        private val client: RadioCatalog,
        private val controller: PlayerController,
    ) : CatalogViewModel<RadioStation>() {
        private val _genre = MutableStateFlow(RadioGenre.ALL)
        val genre: StateFlow<RadioGenre> = _genre.asStateFlow()

        override suspend fun fetch(): List<RadioStation> = client.stations(_genre.value)

        fun selectGenre(genre: RadioGenre) {
            if (genre == _genre.value) return
            _genre.value = genre
            reload()
        }

        // A station is a queue of its own: there is nothing to skip to on a live stream.
        fun play(station: RadioStation) {
            controller.playQueue(listOf(station.toTrack()), 0)
            viewModelScope.launch { client.countClick(station.uuid) }
        }
    }

// Electronic tracks on Audius: this week's trending of a genre, or a search narrowed to it.
@HiltViewModel
class AudiusViewModel
    @Inject
    constructor(
        private val client: AudiusCatalog,
        private val controller: PlayerController,
    ) : CatalogViewModel<TrackItem>() {
        private val _genre = MutableStateFlow(AudiusGenre.ALL)
        val genre: StateFlow<AudiusGenre> = _genre.asStateFlow()

        // The search that is shown; blank means trending.
        private val _query = MutableStateFlow("")
        val query: StateFlow<String> = _query.asStateFlow()

        override suspend fun fetch(): List<TrackItem> {
            val query = _query.value
            return if (query.isBlank()) client.trending(_genre.value) else client.search(query, _genre.value)
        }

        fun selectGenre(genre: AudiusGenre) {
            if (genre == _genre.value) return
            _genre.value = genre
            reload()
        }

        fun search(text: String) {
            _query.value = text.trim()
            reload()
        }

        fun showTrending() = search("")

        // Like a search result: the list becomes the queue, starting at the tapped track.
        fun play(index: Int) {
            val tracks = state.value.items
            if (index in tracks.indices) controller.playQueue(tracks, index)
        }
    }
