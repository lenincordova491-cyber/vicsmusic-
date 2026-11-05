package app.vicsmusic.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.domain.repository.PlaylistRepository
import app.vicsmusic.core.model.Playlist
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.player.PlaybackConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab {
    TRACKS,
    FAVORITES,
    PLAYLISTS
}

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.TRACKS,
    val isLoading: Boolean = true,
    val tracks: List<Track> = emptyList(),
    val favorites: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackConnection: PlaybackConnection
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        observeLibraryData()
    }

    private fun observeLibraryData() {
        viewModelScope.launch {
            combine(
                musicRepository.getAllTracks(),
                musicRepository.getFavoriteTracks(),
                playlistRepository.getPlaylists()
            ) { tracks, favorites, playlists ->
                LibraryUiState(
                    selectedTab = _uiState.value.selectedTab,
                    isLoading = false,
                    tracks = tracks,
                    favorites = favorites,
                    playlists = playlists,
                    errorMessage = null
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun syncTracks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            musicRepository.syncLocalTracks()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun playTrack(track: Track, fromList: List<Track>) {
        val index = fromList.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playbackConnection.playQueue(fromList, index)
    }
}
