package app.vicsmusic.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vicsmusic.core.domain.usecase.ToggleLikeUseCase
import app.vicsmusic.core.model.PlaybackState
import app.vicsmusic.core.player.PlaybackConnection
import app.vicsmusic.core.player.queue.QueueState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackConnection: PlaybackConnection,
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
