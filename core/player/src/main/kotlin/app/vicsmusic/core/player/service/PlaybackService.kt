package app.vicsmusic.core.player.service

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.player.PlayerManager
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Servicio de reproducción en segundo plano y biblioteca multimedia para Android Auto.
 * Implementa el árbol de categorías jerárquico y control de reproducción táctil del coche.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    lateinit var musicRepository: MusicRepository

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var mediaLibrarySession: MediaLibrarySession? = null

    private val callback = object : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Vicsmusic")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()

            serviceScope.launch(Dispatchers.IO) {
                try {
                    val items = ImmutableList.builder<MediaItem>()

                    when (parentId) {
                        ROOT_ID -> {
                            // Nivel raíz en Android Auto: Carpetas navegables
                            items.add(buildFolder(MEDIA_ID_FAVORITES, "Canciones Favoritas", "Tus temas marcados con corazón"))
                            items.add(buildFolder(MEDIA_ID_DOWNLOADS, "Descargas sin conexión", "Música disponible en el dispositivo"))
                            items.add(buildFolder(MEDIA_ID_RECENTS, "Escuchadas recientemente", "Historial de reproducción"))
                            items.add(buildFolder(MEDIA_ID_QUEUE, "Cola actual", "Pistas en reproducción en cola"))
                            items.add(buildFolder(MEDIA_ID_CHARTS, "Top 50 & Rankings", "Los temas más escuchados"))
                        }
                        MEDIA_ID_FAVORITES -> {
                            val favorites = musicRepository.getFavoriteTracks().firstOrNull().orEmpty()
                            favorites.forEach { items.add(it.toAutoMediaItem()) }
                        }
                        MEDIA_ID_DOWNLOADS -> {
                            val downloads = musicRepository.getDownloadedTracks().firstOrNull().orEmpty()
                            downloads.forEach { items.add(it.toAutoMediaItem()) }
                        }
                        MEDIA_ID_RECENTS -> {
                            val recents = musicRepository.getRecentTracks(50).firstOrNull().orEmpty()
                            recents.forEach { items.add(it.toAutoMediaItem()) }
                        }
                        MEDIA_ID_QUEUE -> {
                            val queueTracks = playerManager.queueManager.state.value.items
                            queueTracks.forEach { items.add(it.toAutoMediaItem()) }
                        }
                        MEDIA_ID_CHARTS -> {
                            val charts = musicRepository.getTopCharts().getOrNull().orEmpty()
                            val topTracks = charts.flatMap { it.tracks }.distinctBy { it.id }.take(50)
                            topTracks.forEach { items.add(it.toAutoMediaItem()) }
                        }
                        else -> {
                            // Por defecto devolver cola actual
                            val currentTracks = playerManager.queueManager.state.value.items
                            currentTracks.forEach { items.add(it.toAutoMediaItem()) }
                        }
                    }

                    future.set(LibraryResult.ofItemList(items.build(), params))
                } catch (e: Exception) {
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
                }
            }

            return future
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            serviceScope.launch(Dispatchers.Main) {
                val trackIds = mediaItems.map { it.mediaId }
                val tracksMap = withContext(Dispatchers.IO) {
                    musicRepository.getAllTracks().firstOrNull().orEmpty().associateBy { it.id }
                }
                val domainTracks = trackIds.mapNotNull { id ->
                    tracksMap[id] ?: run {
                        val item = mediaItems.firstOrNull { it.mediaId == id }
                        item?.let {
                            Track(
                                id = it.mediaId,
                                sourceId = "remote",
                                title = it.mediaMetadata.title?.toString() ?: "Canción",
                                artistId = "artist_${it.mediaId}",
                                artistName = it.mediaMetadata.artist?.toString() ?: "Artista",
                                albumId = null,
                                albumName = it.mediaMetadata.albumTitle?.toString(),
                                durationMs = 180_000L,
                                artworkUrl = it.mediaMetadata.artworkUri?.toString()
                            )
                        }
                    }
                }
                if (domainTracks.isNotEmpty()) {
                    playerManager.playRankingOrList(
                        domainTracks,
                        startIndex.coerceIn(0, domainTracks.lastIndex),
                        startPositionMs
                    )
                }
            }
            return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            serviceScope.launch(Dispatchers.Main) {
                val trackIds = mediaItems.map { it.mediaId }
                val tracksMap = withContext(Dispatchers.IO) {
                    musicRepository.getAllTracks().firstOrNull().orEmpty().associateBy { it.id }
                }
                val domainTracks = trackIds.mapNotNull { id ->
                    tracksMap[id] ?: run {
                        val item = mediaItems.firstOrNull { it.mediaId == id }
                        item?.let {
                            Track(
                                id = it.mediaId,
                                sourceId = "remote",
                                title = it.mediaMetadata.title?.toString() ?: "Canción",
                                artistId = "artist_${it.mediaId}",
                                artistName = it.mediaMetadata.artist?.toString() ?: "Artista",
                                albumId = null,
                                albumName = it.mediaMetadata.albumTitle?.toString(),
                                durationMs = 180_000L,
                                artworkUrl = it.mediaMetadata.artworkUri?.toString()
                            )
                        }
                    }
                }
                if (domainTracks.isNotEmpty()) {
                    playerManager.appendTracksToExoPlayerQueue(domainTracks)
                }
            }
            return Futures.immediateFuture(mediaItems)
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val baseResult = super.onConnect(session, controller)
            val sessionCommands = baseResult.availableSessionCommands
                .buildUpon()
                .add(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_TOGGLE_REPEAT, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                baseResult.availablePlayerCommands
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_TOGGLE_SHUFFLE -> playerManager.toggleShuffle()
                ACTION_TOGGLE_REPEAT -> playerManager.toggleRepeat()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val sessionActivityPendingIntent = packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
            PendingIntent.getActivity(
                this,
                0,
                sessionIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, playerManager.sessionPlayer, callback)
            .apply {
                if (sessionActivityPendingIntent != null) {
                    setSessionActivity(sessionActivityPendingIntent)
                }
            }
            .build()

        // Sincronización continua de estado con Widgets y Android Auto
        serviceScope.launch {
            playerManager.playbackState.collect { state ->
                val track = state.currentTrack
                val intent = Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
                    putExtra("title", track?.title ?: "Vicsmusic")
                    putExtra("artist", track?.artistName ?: "Toca para reproducir")
                    putExtra("isPlaying", state.isPlaying)
                    putExtra("artworkUrl", track?.artworkUrl)
                    setPackage(packageName)
                }
                sendBroadcast(intent)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_WIDGET_PLAY_PAUSE -> playerManager.togglePlayPause()
            ACTION_WIDGET_NEXT -> playerManager.playNext()
            ACTION_WIDGET_PREV -> playerManager.playPrevious()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = playerManager.exoPlayer
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == androidx.media3.common.Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaLibrarySession?.run {
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }

    private fun buildFolder(id: String, title: String, subtitle: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    private fun Track.toAutoMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse("vicsmusic://track/$id"))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artistName)
                    .setAlbumTitle(albumName)
                    .setArtworkUri(artworkUrl?.let { Uri.parse(it) })
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build()
            )
            .build()
    }

    companion object {
        const val ROOT_ID = "vicsmusic_root"
        const val MEDIA_ID_FAVORITES = "vicsmusic_favorites"
        const val MEDIA_ID_DOWNLOADS = "vicsmusic_downloads"
        const val MEDIA_ID_RECENTS = "vicsmusic_recents"
        const val MEDIA_ID_QUEUE = "vicsmusic_queue"
        const val MEDIA_ID_CHARTS = "vicsmusic_charts"

        const val ACTION_TOGGLE_SHUFFLE = "app.vicsmusic.ACTION_TOGGLE_SHUFFLE"
        const val ACTION_TOGGLE_REPEAT = "app.vicsmusic.ACTION_TOGGLE_REPEAT"

        const val ACTION_PLAYBACK_STATE_CHANGED = "app.vicsmusic.ACTION_PLAYBACK_STATE_CHANGED"
        const val ACTION_WIDGET_PLAY_PAUSE = "app.vicsmusic.ACTION_WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "app.vicsmusic.ACTION_WIDGET_NEXT"
        const val ACTION_WIDGET_PREV = "app.vicsmusic.ACTION_WIDGET_PREV"
    }
}
