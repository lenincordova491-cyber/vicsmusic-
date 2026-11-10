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
    private val playerManager: PlayerManager,
    val sleepTimerManager: app.vicsmusic.core.player.sleeptimer.SleepTimerManager,
    val audioEffectsProcessor: app.vicsmusic.core.player.dsp.AudioEffectsProcessor
) {
    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState
    val queueState: StateFlow<QueueState> = playerManager.queueManager.state
    val playbackSpeed: StateFlow<Float> = playerManager.playbackSpeed
    val sleepTimerState: StateFlow<app.vicsmusic.core.player.sleeptimer.SleepTimerState> = sleepTimerManager.state
    val audioDspState: StateFlow<app.vicsmusic.core.player.dsp.AudioDspState> = audioEffectsProcessor.state

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

    private fun startPlaybackService() {
        try {
            val intent = android.content.Intent(context, PlaybackService::class.java)
            context.startService(intent)
        } catch (_: Exception) {
            // Android 14+ background start limitations
        }
    }

    fun playTrack(track: Track) {
        startPlaybackService()
        playerManager.playTrack(track)
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        startPlaybackService()
        playerManager.playQueue(tracks, startIndex)
    }

    fun playTrackAtIndex(index: Int) {
        startPlaybackService()
        playerManager.playTrackAtIndex(index)
    }

    fun play() {
        startPlaybackService()
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
        playerManager.moveQueueItem(fromIndex, toIndex)
    }

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
    }

    fun startSleepTimer(option: app.vicsmusic.core.player.sleeptimer.SleepTimerOption) {
        sleepTimerManager.startTimer(option)
    }

    fun cancelSleepTimer() {
        sleepTimerManager.cancelTimer()
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        audioEffectsProcessor.setEqualizerEnabled(enabled)
    }

    fun setBandLevel(bandIndex: Short, levelMb: Short) {
        audioEffectsProcessor.setBandLevel(bandIndex, levelMb)
    }

    fun setBassBoost(strength: Short) {
        audioEffectsProcessor.setBassBoost(strength)
    }

    fun applyAutoEqProfile(profileId: String) {
        audioEffectsProcessor.applyAutoEqProfile(profileId)
    }

    fun clearQueue() {
        playerManager.queueManager.clear()
    }

    fun updateTrackLiked(trackId: String, isLiked: Boolean) {
        playerManager.updateTrackLiked(trackId, isLiked)
    }
}
