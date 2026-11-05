package app.vicsmusic.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.player.PlaybackConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val recentTracks: List<Track> = emptyList(),
    val localTracks: List<Track> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playbackConnection: PlaybackConnection
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeHomeData()
    }

    private fun observeHomeData() {
        viewModelScope.launch {
            combine(
                musicRepository.getRecentTracks(15),
                musicRepository.getAllTracks()
            ) { recent, local ->
                HomeUiState(
                    isLoading = false,
                    recentTracks = recent,
                    localTracks = local,
                    errorMessage = null
                )
            }.catch { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.localizedMessage ?: "Unknown error"
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun syncLocalTracks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            musicRepository.syncLocalTracks()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun playTrack(track: Track, fromQueue: List<Track> = emptyList()) {
        if (fromQueue.isNotEmpty()) {
            val index = fromQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            playbackConnection.playQueue(fromQueue, index)
        } else {
            playbackConnection.playTrack(track)
        }
    }
}
