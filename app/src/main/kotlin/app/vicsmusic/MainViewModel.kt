package app.vicsmusic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LocalMediaUiState {
    data object Idle : LocalMediaUiState
    data object Loading : LocalMediaUiState
    data class Success(val tracks: List<Track>, val syncedCount: Int) : LocalMediaUiState
    data class Error(val message: String) : LocalMediaUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    val allTracks: StateFlow<List<Track>> = musicRepository.getAllTracks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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
}
