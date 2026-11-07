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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random
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

    @Volatile
    private var _exoPlayer: ExoPlayer? = null

    val exoPlayer: ExoPlayer
        get() {
            val existing = _exoPlayer
            if (existing != null) {
                return existing
            }
            return synchronized(this) {
                _exoPlayer ?: createExoPlayer().also { _exoPlayer = it }
            }
        }

    private fun createExoPlayer(): ExoPlayer {
        val cacheDataSourceFactory = playerCacheManager.createCacheDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cacheDataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true) // Built-in audio focus handling
            .setHandleAudioBecomingNoisy(true) // Pauses automatically on headphone disconnect
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

    private var consecutiveErrorCount = 0

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            updatePlaybackState()
            when (state) {
                Player.STATE_READY -> {
                    consecutiveErrorCount = 0
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
            android.util.Log.e("PlayerManager", "ExoPlayer error [${error.errorCodeName} / ${error.errorCode}]: ${error.message}", error)
            consecutiveErrorCount++
            _playbackState.value = _playbackState.value.copy(
                status = PlaybackStatus.ERROR,
                errorMessage = error.localizedMessage
            )
            val hasNext = queueManager.state.value.currentIndex < queueManager.state.value.items.size - 1
            if (consecutiveErrorCount <= 2 && hasNext) {
                scope.launch {
                    delay(1500)
                    playNext()
                }
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
            triggerAutoplay(track, autoPlayNext = false)
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        queueManager.setQueue(tracks, startIndex)
        playTrackAtIndex(startIndex)
        val current = queueManager.state.value.currentTrack
        if (current != null && tracks.size < 20) {
            triggerAutoplay(current, autoPlayNext = false)
        }
    }

    fun playTrackAtIndex(index: Int) {
        val track = queueManager.skipToIndex(index) ?: return
        prepareAndPlay(track)
    }

    private fun prepareAndPlay(track: Track) {
        audioFocusManager.requestAudioFocus()

        _playbackState.value = _playbackState.value.copy(
            status = PlaybackStatus.BUFFERING,
            currentTrack = track,
            positionMs = 0L,
            durationMs = track.durationMs
        )

        // Seamless queue lookahead: if fewer than 5 tracks remain ahead in queue, prefetch autoplay
        val qState = queueManager.state.value
        val remaining = qState.items.size - (qState.currentIndex + 1)
        if (remaining < 5 && qState.repeatMode == RepeatMode.OFF) {
            triggerAutoplay(track, autoPlayNext = false)
        }

        scope.launch {
            // Ensure track metadata is always saved in DB so history/favorites never lose title/artist
            musicRepository.seedCatalogTracks(listOf(track))

            val streamResult = musicRepository.getStreamInfo(track.id, AudioQuality.HIGH)
            streamResult.onSuccess { streamInfo ->
                if (streamInfo.headers.isNotEmpty()) {
                    playerCacheManager.updateRequestHeaders(streamInfo.headers)
                }

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
                    .setCustomCacheKey(track.id)
                    .setMediaMetadata(metadata)
                    .build()

                val player = exoPlayer
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
            }.onFailure { error ->
                android.util.Log.e("PlayerManager", "Stream extraction failed for ${track.id}: ${error.message}", error)
                _playbackState.value = _playbackState.value.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = "No se pudo reproducir '${track.title}'"
                )
            }
        }
    }

    fun play() {
        val player = exoPlayer
        if (!player.isPlaying) {
            audioFocusManager.requestAudioFocus()
            player.play()
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

    private var isAutoplayFetching = false
    private val recommendationEngine = app.vicsmusic.core.domain.engine.AdaptiveRecommendationEngine()

    fun playNext() {
        val nextTrack = queueManager.skipToNext()
        if (nextTrack != null) {
            prepareAndPlay(nextTrack)
        } else {
            val current = _playbackState.value.currentTrack
            if (current != null && queueManager.state.value.repeatMode == RepeatMode.OFF) {
                triggerAutoplay(current, autoPlayNext = true)
            } else if (queueManager.state.value.repeatMode == RepeatMode.ALL) {
                val loopTrack = queueManager.skipToIndex(0)
                if (loopTrack != null) {
                    prepareAndPlay(loopTrack)
                }
            }
        }
    }

    private fun triggerAutoplay(current: Track, autoPlayNext: Boolean = false) {
        if (isAutoplayFetching) return
        isAutoplayFetching = true
        scope.launch {
            try {
                val prefs = userPreferencesRepository.userPreferences.first()
                if (!prefs.isAutoplayEnabled) {
                    if (autoPlayNext) {
                        val loopTrack = queueManager.skipToIndex(0)
                        if (loopTrack != null) {
                            prepareAndPlay(loopTrack)
                        }
                    }
                    return@launch
                }

                val allTracks = musicRepository.getAllTracks().firstOrNull() ?: emptyList()
                val listeningEvents = musicRepository.getPlayHistory(50).firstOrNull() ?: emptyList()

                val isLocalTrack = current.sourceId == "local"
                val sameSourceTracks = if (isLocalTrack) {
                    allTracks.filter { it.sourceId == "local" }
                } else {
                    allTracks.filter { it.sourceId != "local" }
                }

                var candidateTracks = sameSourceTracks
                if (!isLocalTrack) {
                    val currentArtistTracks = sameSourceTracks.filter {
                        it.artistName.equals(current.artistName, ignoreCase = true) ||
                        it.artistId.equals(current.artistId, ignoreCase = true)
                    }
                    if (currentArtistTracks.size < 4) {
                        val searchResult = musicRepository.searchTracks(current.artistName).getOrNull().orEmpty()
                            .filter { it.sourceId != "local" }
                        candidateTracks = (sameSourceTracks + searchResult).distinctBy { it.id }
                    }
                }

                val currentQueueIds = queueManager.state.value.items.map { it.id }.toSet()
                var autoplayList = recommendationEngine.computeAutoplayQueue(
                    currentTrack = current,
                    allAvailableTracks = candidateTracks,
                    listeningHistory = listeningEvents,
                    favoriteArtistNames = prefs.favoriteArtistIds,
                    favoriteTrackIds = candidateTracks.filter { it.isLiked }.map { it.id }.toSet(),
                    excludedTrackIds = currentQueueIds,
                    count = 10
                )

                // Guaranteed fallback pool within the SAME source type (never mix local and remote)
                if (autoplayList.isEmpty()) {
                    val fallbackPool = candidateTracks
                        .filter { it.id != current.id && !currentQueueIds.contains(it.id) }
                        .ifEmpty { candidateTracks.filter { it.id != current.id } }
                    if (fallbackPool.isNotEmpty()) {
                        autoplayList = fallbackPool.shuffled(Random(System.currentTimeMillis())).take(10)
                    }
                }

                if (autoplayList.isNotEmpty()) {
                    queueManager.appendAutoplayTracks(autoplayList)
                    if (autoPlayNext) {
                        val nextTrack = queueManager.skipToNext()
                        if (nextTrack != null) {
                            prepareAndPlay(nextTrack)
                        }
                    }
                } else if (autoPlayNext) {
                    val loopTrack = queueManager.skipToIndex(0)
                    if (loopTrack != null) {
                        prepareAndPlay(loopTrack)
                    }
                }
            } catch (_: Exception) {
                if (autoPlayNext) {
                    val loopTrack = queueManager.skipToIndex(0)
                    if (loopTrack != null) {
                        prepareAndPlay(loopTrack)
                    }
                }
            } finally {
                isAutoplayFetching = false
            }
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

    fun updateTrackLiked(trackId: String, isLiked: Boolean) {
        val cur = _playbackState.value.currentTrack
        if (cur != null && cur.id == trackId) {
            _playbackState.value = _playbackState.value.copy(
                currentTrack = cur.copy(isLiked = isLiked)
            )
        }
        queueManager.updateTrackLiked(trackId, isLiked)
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
                val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val duration = if (exoPlayer.duration > 0) exoPlayer.duration else _playbackState.value.durationMs

                _playbackState.value = _playbackState.value.copy(
                    positionMs = currentPos,
                    durationMs = duration
                )

                // Seamless prefetch: 15 seconds before the final track of the queue finishes
                val qState = queueManager.state.value
                val isLastTrack = qState.currentIndex >= qState.items.lastIndex
                if (isLastTrack && duration > 20_000L && currentPos >= duration - 15_000L) {
                    val curTrack = qState.currentTrack
                    if (curTrack != null && qState.repeatMode == RepeatMode.OFF) {
                        triggerAutoplay(curTrack, autoPlayNext = false)
                    }
                }

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
        _exoPlayer?.run {
            removeListener(playerListener)
            release()
        }
        _exoPlayer = null
    }
}
