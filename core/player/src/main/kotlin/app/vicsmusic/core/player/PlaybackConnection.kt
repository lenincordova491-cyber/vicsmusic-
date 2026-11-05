package app.vicsmusic.core.player

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.vicsmusic.core.model.PlaybackState
import app.vicsmusic.core.model.RepeatMode
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.player.queue.QueueState
import app.vicsmusic.core.player.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class PlaybackConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerManager: PlayerManager
) {
    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState
    val queueState: StateFlow<QueueState> = playerManager.queueManager.state

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    init {
        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                runCatching {
                    mediaController = controllerFuture?.get()
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun playTrack(track: Track) {
        playerManager.playTrack(track)
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        playerManager.playQueue(tracks, startIndex)
    }

    fun playTrackAtIndex(index: Int) {
        playerManager.playTrackAtIndex(index)
    }

    fun play() {
        playerManager.play()
    }

    fun pause() {
        playerManager.pause()
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun skipNext() {
        playerManager.playNext()
    }

    fun skipPrevious() {
        playerManager.playPrevious()
    }

    fun toggleShuffle(): Boolean {
        return playerManager.toggleShuffle()
    }

    fun toggleRepeat(): RepeatMode {
        return playerManager.toggleRepeat()
    }

    fun removeQueueItem(index: Int) {
        playerManager.queueManager.removeAt(index)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playerManager.queueManager.moveItem(fromIndex, toIndex)
    }

    fun clearQueue() {
        playerManager.queueManager.clear()
    }
}
