package app.vicsmusic.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ERROR
}

@Serializable
enum class RepeatMode {
    OFF,
    ONE,
    ALL
}

@Serializable
data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val currentTrack: Track? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val errorMessage: String? = null
)
