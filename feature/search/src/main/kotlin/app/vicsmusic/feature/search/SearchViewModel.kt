package app.vicsmusic.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vicsmusic.core.domain.usecase.SearchTracksUseCase
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.player.PlaybackConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchFilter {
    ALL,
    TRACKS,
    ARTISTS,
    ALBUMS
}

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.ALL,
    val isSearching: Boolean = false,
    val results: List<Track> = emptyList(),
    val filteredResults: List<Track> = emptyList(),
    val errorMessage: String? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchTracksUseCase: SearchTracksUseCase,
    private val playbackConnection: PlaybackConnection
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    performSearch(query)
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        queryFlow.value = newQuery
    }

    fun onFilterSelect(filter: SearchFilter) {
        _uiState.update { current ->
            val filtered = filterTracks(current.results, filter)
            current.copy(filter = filter, filteredResults = filtered)
        }
    }

    private suspend fun performSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    results = emptyList(),
                    filteredResults = emptyList(),
                    errorMessage = null
                )
            }
            return
        }

        _uiState.update { it.copy(isSearching = true, errorMessage = null) }
        val result = searchTracksUseCase(trimmed)
        result.fold(
            onSuccess = { tracks ->
                _uiState.update { current ->
                    val filtered = filterTracks(tracks, current.filter)
                    current.copy(
                        isSearching = false,
                        results = tracks,
                        filteredResults = filtered,
                        errorMessage = null
                    )
                }
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = error.localizedMessage ?: "Search failed"
                    )
                }
            }
        )
    }

    private fun filterTracks(tracks: List<Track>, filter: SearchFilter): List<Track> {
        val query = _uiState.value.query.trim().lowercase()
        return when (filter) {
            SearchFilter.ALL -> tracks
            SearchFilter.TRACKS -> tracks.filter { it.title.lowercase().contains(query) }
            SearchFilter.ARTISTS -> tracks.filter { it.artistName.lowercase().contains(query) }
            SearchFilter.ALBUMS -> tracks.filter { it.albumName?.lowercase()?.contains(query) == true }
        }
    }

    fun playTrack(track: Track) {
        val queue = _uiState.value.filteredResults.ifEmpty { listOf(track) }
        val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playbackConnection.playQueue(queue, index)
    }
}
