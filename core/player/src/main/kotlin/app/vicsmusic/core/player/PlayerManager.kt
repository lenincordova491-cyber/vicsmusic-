package app.vicsmusic.core.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.domain.repository.UserPreferencesRepository
import app.vicsmusic.core.model.AudioQuality
import app.vicsmusic.core.model.PlaybackState
import app.vicsmusic.core.model.PlaybackStatus
import app.vicsmusic.core.model.RepeatMode
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.player.cache.PlayerCacheManager
import app.vicsmusic.core.player.focus.AudioFocusManager
import app.vicsmusic.core.player.queue.QueueManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class PlayerManager @Inject constructor(
    private val context: Context,
    val queueManager: QueueManager,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playerCacheManager: PlayerCacheManager
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    val exoPlayer: ExoPlayer by lazy {
        val cacheDataSourceFactory = playerCacheManager.createCacheDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cacheDataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, false) // We handle audio focus explicitly
            .setHandleAudioBecomingNoisy(false) // Handled in AudioFocusManager
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(playerListener)
            }
    }

    private val audioFocusManager = AudioFocusManager(
        context = context,
        onPause = { pause() },
        onResume = { play() },
        onSetVolume = { volume -> exoPlayer.volume = volume }
    )

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            updatePlaybackState()
            when (state) {
                Player.STATE_READY -> {
                    startProgressTracker()
                }
                Player.STATE_ENDED -> {
                    stopProgressTracker()
                    playNext()
                }
                Player.STATE_IDLE -> {
                    stopProgressTracker()
                }
                Player.STATE_BUFFERING -> {}
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackState()
            if (isPlaying) {
                startProgressTracker()
            } else {
                stopProgressTracker()
                persistCurrentPosition()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackState.value = _playbackState.value.copy(
                status = PlaybackStatus.ERROR,
                errorMessage = error.localizedMessage
            )
            // Retry with next song on persistent error
            scope.launch {
                delay(1500)
                playNext()
            }
        }
    }

    init {
        scope.launch {
            queueManager.state.collect { qState ->
                _playbackState.value = _playbackState.value.copy(
                    isShuffleEnabled = qState.isShuffleEnabled,
                    repeatMode = qState.repeatMode
                )
            }
        }
    }

    fun playTrack(track: Track) {
        queueManager.addToQueue(track)
        val index = queueManager.state.value.items.indexOfFirst { it.id == track.id }
        if (index != -1) {
            playTrackAtIndex(index)
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        queueManager.setQueue(tracks, startIndex)
        playTrackAtIndex(startIndex)
    }

    fun playTrackAtIndex(index: Int) {
        val track = queueManager.skipToIndex(index) ?: return
        prepareAndPlay(track)
    }

    private fun prepareAndPlay(track: Track) {
        if (!audioFocusManager.requestAudioFocus()) {
            return
        }

        _playbackState.value = _playbackState.value.copy(
            status = PlaybackStatus.BUFFERING,
            currentTrack = track,
            positionMs = 0L,
            durationMs = track.durationMs
        )

        scope.launch {
            val streamResult = musicRepository.getStreamInfo(track.id, AudioQuality.HIGH)
            streamResult.onSuccess { streamInfo ->
                val metadata = MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artistName)
                    .setAlbumTitle(track.albumName)
                    .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
                    .setExtras(Bundle().apply { putString("track_id", track.id) })
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(Uri.parse(streamInfo.url))
                    .setMediaMetadata(metadata)
                    .build()

                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
            }.onFailure { error ->
                _playbackState.value = _playbackState.value.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = error.localizedMessage
                )
            }
        }
    }

    fun play() {
        if (!exoPlayer.isPlaying) {
            if (audioFocusManager.requestAudioFocus()) {
                exoPlayer.play()
            }
        }
    }

    fun pause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            persistCurrentPosition()
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(positionMs = positionMs)
    }

    fun playNext() {
        val nextTrack = queueManager.skipToNext()
        if (nextTrack != null) {
            prepareAndPlay(nextTrack)
        } else {
            exoPlayer.stop()
            audioFocusManager.abandonAudioFocus()
            _playbackState.value = _playbackState.value.copy(
                status = PlaybackStatus.IDLE,
                currentTrack = null,
                positionMs = 0L
            )
        }
    }

    fun playPrevious() {
        if (exoPlayer.currentPosition > 3000L) {
            seekTo(0L)
            return
        }
        val prevTrack = queueManager.skipToPrevious()
        if (prevTrack != null) {
            prepareAndPlay(prevTrack)
        }
    }

    fun toggleShuffle(): Boolean {
        return queueManager.toggleShuffle()
    }

    fun toggleRepeat(): RepeatMode {
        return queueManager.toggleRepeat()
    }

    private fun updatePlaybackState() {
        val currentTrack = queueManager.state.value.currentTrack
        val isPlaying = exoPlayer.isPlaying
        val exoState = exoPlayer.playbackState

        val status = when {
            exoState == Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
            isPlaying -> PlaybackStatus.PLAYING
            exoState == Player.STATE_READY -> PlaybackStatus.PAUSED
            exoState == Player.STATE_ENDED || exoState == Player.STATE_IDLE -> PlaybackStatus.IDLE
            else -> PlaybackStatus.IDLE
        }

        _playbackState.value = _playbackState.value.copy(
            status = status,
            currentTrack = currentTrack,
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
            durationMs = if (exoPlayer.duration > 0) exoPlayer.duration else (currentTrack?.durationMs ?: 0L),
            errorMessage = null
        )
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && exoPlayer.isPlaying) {
                _playbackState.value = _playbackState.value.copy(
                    positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    durationMs = if (exoPlayer.duration > 0) exoPlayer.duration else _playbackState.value.durationMs
                )
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun persistCurrentPosition() {
        val track = _playbackState.value.currentTrack ?: return
        val pos = exoPlayer.currentPosition
        scope.launch {
            userPreferencesRepository.setLastPlayed(track.id, pos)
        }
    }

    fun release() {
        stopProgressTracker()
        audioFocusManager.abandonAudioFocus()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        playerCacheManager.release()
    }
}
