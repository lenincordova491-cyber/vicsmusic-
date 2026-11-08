package app.vicsmusic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.domain.repository.UserPreferencesRepository
import app.vicsmusic.core.model.PlaybackState
import app.vicsmusic.core.model.RepeatMode
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.model.UserPreferences
import app.vicsmusic.core.player.PlaybackConnection
import app.vicsmusic.core.player.queue.QueueState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Ready(val userPreferences: UserPreferences) : MainUiState
}

sealed interface LocalMediaUiState {
    data object Idle : LocalMediaUiState
    data object Loading : LocalMediaUiState
    data class Success(val tracks: List<Track>, val syncedCount: Int) : LocalMediaUiState
    data class Error(val message: String) : LocalMediaUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playbackConnection: PlaybackConnection,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val mainUiState: StateFlow<MainUiState> = userPreferencesRepository.userPreferences
        .map { MainUiState.Ready(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MainUiState.Loading
        )

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    val allTracks: StateFlow<List<Track>> = musicRepository.getAllTracks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val playbackState: StateFlow<PlaybackState> = playbackConnection.playbackState
    val queueState: StateFlow<QueueState> = playbackConnection.queueState

    private val _syncState = MutableStateFlow<LocalMediaUiState>(LocalMediaUiState.Idle)
    val syncState: StateFlow<LocalMediaUiState> = _syncState.asStateFlow()

    fun syncLocalTracks() {
        viewModelScope.launch {
            _syncState.value = LocalMediaUiState.Loading
            val result = musicRepository.syncLocalTracks()
            result.onSuccess { count ->
                _syncState.value = LocalMediaUiState.Success(
                    tracks = allTracks.value,
                    syncedCount = count
                )
            }.onFailure { error ->
                _syncState.value = LocalMediaUiState.Error(
                    error.localizedMessage ?: "Error al sincronizar canciones locales"
                )
            }
        }
    }

    fun playTrack(track: Track) {
        val tracks = allTracks.value
        val index = tracks.indexOfFirst { it.id == track.id }
        if (index != -1) {
            playbackConnection.playQueue(tracks, index)
        } else {
            playbackConnection.playTrack(track)
        }
    }

    fun togglePlayPause() {
        playbackConnection.togglePlayPause()
    }

    fun skipNext() {
        playbackConnection.skipNext()
    }

    fun skipPrevious() {
        playbackConnection.skipPrevious()
    }

    fun seekTo(positionMs: Long) {
        playbackConnection.seekTo(positionMs)
    }

    fun toggleShuffle(): Boolean {
        return playbackConnection.toggleShuffle()
    }

    fun toggleRepeat(): RepeatMode {
        return playbackConnection.toggleRepeat()
    }
}
