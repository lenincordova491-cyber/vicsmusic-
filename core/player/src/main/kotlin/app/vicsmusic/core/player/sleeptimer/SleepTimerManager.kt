package app.vicsmusic.core.player.sleeptimer

import androidx.media3.common.Player
import app.vicsmusic.core.player.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class SleepTimerOption(val minutes: Int, val title: String) {
    MIN_15(15, "15 minutos"),
    MIN_30(30, "30 minutos"),
    MIN_45(45, "45 minutos"),
    MIN_60(60, "1 hora"),
    END_OF_TRACK(0, "Al terminar esta canción")
}

data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingSeconds: Long = 0L,
    val totalSeconds: Long = 0L,
    val isEndOfTrack: Boolean = false
) {
    val formattedRemaining: String
        get() {
            if (isEndOfTrack) return "Al terminar canción"
            val m = remainingSeconds / 60
            val s = remainingSeconds % 60
            return "%02d:%02d".format(m, s)
        }
}

@Singleton
class SleepTimerManager @Inject constructor(
    private val playerManager: PlayerManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private val endOfTrackListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            if (_state.value.isActive && _state.value.isEndOfTrack) {
                // When transitioning to next track, the previous track finished
                stopPlaybackAndReset()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (_state.value.isActive && _state.value.isEndOfTrack && playbackState == Player.STATE_ENDED) {
                stopPlaybackAndReset()
            }
        }
    }

    init {
        playerManager.exoPlayer.addListener(endOfTrackListener)
    }

    fun startTimer(option: SleepTimerOption) {
        cancelTimer()

        if (option == SleepTimerOption.END_OF_TRACK) {
            _state.value = SleepTimerState(
                isActive = true,
                remainingSeconds = 0L,
                totalSeconds = 0L,
                isEndOfTrack = true
            )
            return
        }

        val totalSecs = option.minutes * 60L
        _state.value = SleepTimerState(
            isActive = true,
            remainingSeconds = totalSecs,
            totalSeconds = totalSecs,
            isEndOfTrack = false
        )

        timerJob = scope.launch {
            var currentSecs = totalSecs
            while (isActive && currentSecs > 0) {
                delay(1000L)
                currentSecs--

                // Gentle volume fade-out during the final 12 seconds
                if (currentSecs <= 12L) {
                    val volumeFactor = (currentSecs / 12f).coerceIn(0.05f, 1.0f)
                    playerManager.exoPlayer.volume = volumeFactor
                }

                _state.update {
                    it.copy(remainingSeconds = currentSecs)
                }
            }

            if (isActive) {
                stopPlaybackAndReset()
            }
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        playerManager.exoPlayer.volume = 1.0f
        _state.value = SleepTimerState()
    }

    private fun stopPlaybackAndReset() {
        timerJob?.cancel()
        timerJob = null
        playerManager.pause()
        playerManager.exoPlayer.volume = 1.0f
        _state.value = SleepTimerState()
    }
}
