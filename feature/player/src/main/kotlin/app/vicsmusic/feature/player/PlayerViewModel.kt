package app.vicsmusic.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.domain.usecase.ToggleLikeUseCase
import app.vicsmusic.core.model.Lyrics
import app.vicsmusic.core.model.PlaybackState
import app.vicsmusic.core.player.PlaybackConnection
import app.vicsmusic.core.player.queue.QueueState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackConnection: PlaybackConnection,
    private val musicRepository: MusicRepository,
    private val toggleLikeUseCase: ToggleLikeUseCase
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playbackConnection.playbackState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = playbackConnection.playbackState.value
        )

    val queueState: StateFlow<QueueState> = playbackConnection.queueState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = playbackConnection.queueState.value
        )

    val lyrics: StateFlow<Lyrics?> = playbackState
        .map { it.currentTrack?.id }
        .distinctUntilChanged()
        .flatMapLatest { trackId ->
            flow {
                val track = playbackState.value.currentTrack
                if (track != null && track.id == trackId) {
                    val result = musicRepository.getLyrics(track).getOrNull()
                    emit(result)
                } else {
                    emit(null)
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    fun togglePlayPause() {
        playbackConnection.togglePlayPause()
    }

    fun play() {
        playbackConnection.play()
    }

    fun pause() {
        playbackConnection.pause()
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

    fun toggleShuffle() {
        playbackConnection.toggleShuffle()
    }

    fun toggleRepeat() {
        playbackConnection.toggleRepeat()
    }

    fun playTrackAtIndex(index: Int) {
        playbackConnection.playTrackAtIndex(index)
    }

    fun removeQueueItem(index: Int) {
        playbackConnection.removeQueueItem(index)
    }

    fun toggleLike() {
        val currentTrack = playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            toggleLikeUseCase(currentTrack.id)
        }
    }
}
