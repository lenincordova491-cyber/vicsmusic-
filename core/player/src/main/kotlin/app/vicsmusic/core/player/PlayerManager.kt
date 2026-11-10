package app.vicsmusic.core.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
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
import app.vicsmusic.core.model.UserPreferences
import app.vicsmusic.core.player.cache.PlayerCacheManager
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
    private var prepareJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var prewarmedTrackId: String? = null

    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()
    private var pauseAtEndOfCurrentTrack: Boolean = false

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

    /**
     * ForwardingPlayer for MediaSession so Android System Notification, Lock Screen,
     * Bluetooth headsets, and Android Auto always enable and route Next/Previous buttons.
     */
    val sessionPlayer: Player by lazy {
        object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                val queue = queueManager.state.value
                return super.getAvailableCommands().buildUpon()
                    .apply {
                        if (queue.hasNext || queue.items.isNotEmpty()) add(Player.COMMAND_SEEK_TO_NEXT)
                        if (queue.hasPrevious || queue.items.isNotEmpty()) add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    }
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                val queue = queueManager.state.value
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> queue.hasNext || queue.items.isNotEmpty()
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> queue.hasPrevious || queue.items.isNotEmpty()
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun seekToNext() = playNext()
            override fun seekToNextMediaItem() = playNext()
            override fun seekToPrevious() = playPrevious()
            override fun seekToPreviousMediaItem() = playPrevious()
        }
    }

    @Volatile
    private var cachedUserPreferences: UserPreferences? = null

    private fun createExoPlayer(): ExoPlayer {
        val resolvingDataSourceFactory = playerCacheManager.createResolvingDataSourceFactory(
            musicRepository = musicRepository,
            qualityProvider = {
                val prefs = cachedUserPreferences
                if (prefs != null) {
                    resolveNetworkQuality(prefs.wifiQuality, prefs.cellularQuality)
                } else {
                    AudioQuality.HIGH
                }
            }
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(resolvingDataSourceFactory)

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
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                    .build()
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(playerListener)
            }
    }

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

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            recordCurrentTrackFeedback()
            val currentIdx = exoPlayer.currentMediaItemIndex
            val items = queueManager.state.value.items
            if (currentIdx in items.indices) {
                val currentTrack = items[currentIdx]
                queueManager.skipToIndex(currentIdx)
                _playbackState.value = _playbackState.value.copy(
                    currentTrack = currentTrack,
                    positionMs = 0L,
                    durationMs = currentTrack.durationMs,
                    status = if (exoPlayer.isPlaying) PlaybackStatus.PLAYING else PlaybackStatus.BUFFERING
                )
                val remaining = items.size - 1 - currentIdx
                if (remaining <= 2) {
                    triggerContinuousAutoplay(currentTrack)
                }
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
            userPreferencesRepository.userPreferences.collect { prefs ->
                cachedUserPreferences = prefs
            }
        }
        scope.launch {
            queueManager.state.collect { qState ->
                _playbackState.value = _playbackState.value.copy(
                    isShuffleEnabled = qState.isShuffleEnabled,
                    repeatMode = qState.repeatMode
                )
            }
        }
    }

    private fun recordCurrentTrackFeedback() {
        val prevTrack = _playbackState.value.currentTrack ?: return
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val dur = (if (exoPlayer.duration > 0) exoPlayer.duration else prevTrack.durationMs).coerceAtLeast(1L)
        if (pos > 2_000L) {
            val completion = (pos.toFloat() / dur.toFloat()).coerceIn(0.05f, 1.0f)
            scope.launch {
                runCatching { musicRepository.recordPlayHistory(prevTrack.id, completion) }
            }
        }
    }

    private fun resolveNetworkQuality(
        wifiQuality: AudioQuality,
        cellularQuality: AudioQuality
    ): AudioQuality {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return wifiQuality
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return cellularQuality
        return if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) {
            wifiQuality
        } else {
            cellularQuality
        }
    }

    fun buildMediaItem(track: Track): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistName)
            .setAlbumTitle(track.albumName)
            .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
            .setExtras(Bundle().apply { putString("track_id", track.id) })
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse("vicsmusic://track/${track.id}"))
            .setCustomCacheKey(track.id)
            .setMediaMetadata(metadata)
            .build()
    }

    fun playRankingOrList(tracks: List<Track>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        if (tracks.isEmpty()) return
        recordCurrentTrackFeedback()
        prepareJob?.cancel()

        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        queueManager.setQueue(tracks, safeIndex)

        scope.launch(Dispatchers.IO) {
            runCatching { musicRepository.seedCatalogTracks(tracks) }
        }

        val mediaItems = tracks.map { buildMediaItem(it) }
        val player = exoPlayer
        player.setMediaItems(mediaItems, safeIndex, startPositionMs)
        player.prepare()
        player.playWhenReady = true
        player.play()

        val currentTrack = tracks[safeIndex]
        _playbackState.value = _playbackState.value.copy(
            status = PlaybackStatus.BUFFERING,
            currentTrack = currentTrack,
            positionMs = startPositionMs,
            durationMs = currentTrack.durationMs,
            errorMessage = null
        )

        scope.launch {
            val prefs = userPreferencesRepository.userPreferences.firstOrNull()
            val crossfadeSec = prefs?.crossfadeDurationSeconds ?: 0
            if (crossfadeSec > 0) {
                applyCrossfadeIn(crossfadeSec)
            } else {
                player.volume = 1f
            }
        }
    }

    fun appendTracksToExoPlayerQueue(newTracks: List<Track>) {
        if (newTracks.isEmpty()) return
        val currentQueueIds = queueManager.state.value.items.map { it.id }.toSet()
        val uniqueNewTracks = newTracks.filter { !currentQueueIds.contains(it.id) }
        if (uniqueNewTracks.isEmpty()) return

        queueManager.appendAutoplayTracks(uniqueNewTracks)
        val mediaItems = uniqueNewTracks.map { buildMediaItem(it) }
        exoPlayer.addMediaItems(mediaItems)
    }

    fun playTrack(track: Track) {
        val existingIndex = queueManager.state.value.items.indexOfFirst { it.id == track.id }
        if (existingIndex != -1 && exoPlayer.mediaItemCount == queueManager.state.value.items.size) {
            playTrackAtIndex(existingIndex)
        } else {
            playRankingOrList(listOf(track), 0, 0L)
            triggerContinuousAutoplay(track)
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        playRankingOrList(tracks, startIndex, 0L)
        val current = if (startIndex in tracks.indices) tracks[startIndex] else tracks.firstOrNull()
        if (current != null && tracks.size < 20) {
            triggerContinuousAutoplay(current)
        }
    }

    fun playTrackAtIndex(index: Int) {
        val targetTrack = queueManager.skipToIndex(index) ?: return
        val player = exoPlayer
        if (player.mediaItemCount == queueManager.state.value.items.size && index in 0 until player.mediaItemCount) {
            recordCurrentTrackFeedback()
            player.seekToDefaultPosition(index)
            player.playWhenReady = true
            player.play()
            _playbackState.value = _playbackState.value.copy(
                currentTrack = targetTrack,
                positionMs = 0L,
                durationMs = targetTrack.durationMs,
                status = PlaybackStatus.BUFFERING
            )
        } else {
            playRankingOrList(queueManager.state.value.items, index, 0L)
        }
    }

    private fun prepareAndPlay(track: Track) {
        val idx = queueManager.state.value.items.indexOfFirst { it.id == track.id }
        if (idx != -1) {
            playTrackAtIndex(idx)
        } else {
            playRankingOrList(listOf(track), 0, 0L)
        }
    }

    private fun applyCrossfadeIn(seconds: Int) {
        scope.launch {
            val steps = (seconds * 10).coerceIn(5, 120)
            val stepDelay = (seconds * 1000L) / steps
            for (i in 1..steps) {
                if (!exoPlayer.isPlaying) break
                exoPlayer.volume = i.toFloat() / steps
                delay(stepDelay)
            }
            exoPlayer.volume = 1f
        }
    }

    fun startSleepTimer(minutes: Int, stopAtEndOfTrack: Boolean = false) {
        sleepTimerJob?.cancel()
        pauseAtEndOfCurrentTrack = stopAtEndOfTrack
        if (stopAtEndOfTrack) {
            val remaining = (exoPlayer.duration - exoPlayer.currentPosition).coerceAtLeast(0L)
            _sleepTimerRemainingMs.value = remaining
            return
        }
        if (minutes <= 0) {
            _sleepTimerRemainingMs.value = null
            return
        }
        sleepTimerJob = scope.launch {
            var remainingMs = minutes * 60_000L
            while (remainingMs > 0 && isActive) {
                _sleepTimerRemainingMs.value = remainingMs
                delay(1_000L)
                remainingMs -= 1_000L
            }
            _sleepTimerRemainingMs.value = null
            for (i in 20 downTo 1) {
                exoPlayer.volume = i / 20f
                delay(100L)
            }
            pause()
            exoPlayer.volume = 1f
        }
    }

    fun setPlaybackParameters(speed: Float, pitch: Float = 1.0f, skipSilence: Boolean = false) {
        exoPlayer.playbackParameters = PlaybackParameters(
            speed.coerceIn(0.5f, 2.0f),
            pitch.coerceIn(0.5f, 2.0f)
        )
        exoPlayer.skipSilenceEnabled = skipSilence
    }

    fun play() {
        val player = exoPlayer
        if (player.playbackState == Player.STATE_IDLE) {
            val currentTrack = _playbackState.value.currentTrack ?: queueManager.state.value.currentTrack
            if (player.mediaItemCount == 0 && currentTrack != null) {
                prepareAndPlay(currentTrack)
                return
            }
            player.prepare()
        } else if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
        }
        player.playWhenReady = true
        player.play()
        updatePlaybackState()
    }

    fun pause() {
        val player = exoPlayer
        player.playWhenReady = false
        player.pause()
        persistCurrentPosition()
        updatePlaybackState()
    }

    fun togglePlayPause() {
        val player = exoPlayer
        if (player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)) {
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
        val player = exoPlayer
        if (player.hasNextMediaItem()) {
            recordCurrentTrackFeedback()
            player.seekToNextMediaItem()
            player.playWhenReady = true
            player.play()
        } else {
            val nextTrack = queueManager.skipToNext()
            if (nextTrack != null) {
                playRankingOrList(queueManager.state.value.items, queueManager.state.value.currentIndex, 0L)
            } else {
                val current = _playbackState.value.currentTrack
                if (current != null && queueManager.state.value.repeatMode == RepeatMode.OFF) {
                    triggerContinuousAutoplay(current)
                } else if (queueManager.state.value.repeatMode == RepeatMode.ALL) {
                    if (player.mediaItemCount > 0) {
                        player.seekToDefaultPosition(0)
                        player.playWhenReady = true
                        player.play()
                    }
                }
            }
        }
    }

    fun triggerContinuousAutoplay(seedTrack: Track) {
        if (isAutoplayFetching) return
        isAutoplayFetching = true
        scope.launch {
            try {
                val prefs = userPreferencesRepository.userPreferences.first()
                if (!prefs.isAutoplayEnabled) {
                    return@launch
                }

                val currentQueueIds = queueManager.state.value.items.map { it.id }.toSet()
                val radioResult = musicRepository.getContinuousRadioTracks(seedTrack, currentQueueIds)
                val radioTracks = radioResult.getOrNull().orEmpty()

                if (radioTracks.isNotEmpty()) {
                    appendTracksToExoPlayerQueue(radioTracks)
                } else {
                    triggerAutoplay(seedTrack, autoPlayNext = false)
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerManager", "Error en continuous radio autoplay: ${e.message}", e)
                triggerAutoplay(seedTrack, autoPlayNext = false)
            } finally {
                isAutoplayFetching = false
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
                val currentQueueIds = queueManager.state.value.items.map { it.id }.toSet()

                if (!isLocalTrack) {
                    val searchQueries = listOf(
                        "${current.artistName} mix",
                        "${current.artistName} radio",
                        "éxitos ${current.artistName}",
                        "parecidas a ${current.title}"
                    )
                    for (query in searchQueries) {
                        val remainingUnplayed = candidateTracks.filter { !currentQueueIds.contains(it.id) }
                        if (remainingUnplayed.size >= 15) break
                        val searchResult = musicRepository.searchTracks(query).getOrNull().orEmpty()
                            .filter { it.sourceId != "local" }
                        candidateTracks = (candidateTracks + searchResult).distinctBy { it.id }
                    }
                }

                var autoplayList = recommendationEngine.computeAutoplayQueue(
                    currentTrack = current,
                    allAvailableTracks = candidateTracks,
                    listeningHistory = listeningEvents,
                    favoriteArtistNames = prefs.favoriteArtistIds,
                    favoriteTrackIds = candidateTracks.filter { it.isLiked }.map { it.id }.toSet(),
                    excludedTrackIds = currentQueueIds,
                    count = 10
                )

                // Guaranteed infinite fallback pool: never stop playing
                if (autoplayList.isEmpty()) {
                    val unplayedPool = candidateTracks.filter { !currentQueueIds.contains(it.id) && it.id != current.id }
                    if (unplayedPool.isNotEmpty()) {
                        autoplayList = unplayedPool.shuffled(Random(System.currentTimeMillis())).take(10)
                    } else {
                        val fallbackPool = candidateTracks.filter { it.id != current.id }
                        if (fallbackPool.isNotEmpty()) {
                            autoplayList = fallbackPool.shuffled(Random(System.currentTimeMillis())).take(10)
                        }
                    }
                }

                if (autoplayList.isNotEmpty()) {
                    appendTracksToExoPlayerQueue(autoplayList)
                    if (autoPlayNext) {
                        playNext()
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
        val player = exoPlayer
        if (player.currentPosition > 3000L) {
            seekTo(0L)
            return
        }
        if (player.hasPreviousMediaItem()) {
            recordCurrentTrackFeedback()
            player.seekToPreviousMediaItem()
            player.playWhenReady = true
            player.play()
        } else {
            val prevTrack = queueManager.skipToPrevious()
            if (prevTrack != null) {
                playRankingOrList(queueManager.state.value.items, queueManager.state.value.currentIndex, 0L)
            }
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

                // Next Track Pre-warming at 75% duration for instant (<100ms) transition
                val qState = queueManager.state.value
                val nextIndex = qState.currentIndex + 1
                if (duration > 20_000L && currentPos >= (duration * 3) / 4 && nextIndex in qState.items.indices) {
                    val nextTrack = qState.items[nextIndex]
                    if (prewarmedTrackId != nextTrack.id) {
                        prewarmedTrackId = nextTrack.id
                        scope.launch(Dispatchers.IO) {
                            runCatching { musicRepository.getStreamInfo(nextTrack.id, AudioQuality.HIGH) }
                        }
                    }
                }

                // Seamless prefetch: 15 seconds before the final track of the queue finishes
                val isLastTrack = qState.currentIndex >= qState.items.lastIndex
                if (isLastTrack && duration > 20_000L && currentPos >= duration - 15_000L) {
                    val curTrack = qState.currentTrack
                    if (curTrack != null && qState.repeatMode == RepeatMode.OFF) {
                        triggerAutoplay(curTrack, autoPlayNext = false)
                    }
                }

                delay(200)
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
        _exoPlayer?.run {
            removeListener(playerListener)
            release()
        }
        _exoPlayer = null
    }
}
