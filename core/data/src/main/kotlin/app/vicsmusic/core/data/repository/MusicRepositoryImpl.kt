package app.vicsmusic.core.data.repository

import android.content.Context
import app.vicsmusic.core.database.dao.DownloadDao
import app.vicsmusic.core.database.dao.LyricsDao
import app.vicsmusic.core.database.dao.PlayHistoryDao
import app.vicsmusic.core.database.dao.TrackDao
import app.vicsmusic.core.database.entity.DownloadEntity
import app.vicsmusic.core.database.entity.LyricsCacheEntity
import app.vicsmusic.core.database.entity.PlayHistoryEntity
import app.vicsmusic.core.database.model.asEntity
import app.vicsmusic.core.database.model.asExternalModel
import app.vicsmusic.core.domain.engine.ListeningEvent
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.model.AudioQuality
import app.vicsmusic.core.model.DownloadStatus
import app.vicsmusic.core.model.Lyrics
import app.vicsmusic.core.model.StreamInfo
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.network.LrcParser
import app.vicsmusic.core.network.LrclibClient
import app.vicsmusic.core.network.VicsHttpClientFactory
import app.vicsmusic.source.api.SearchType
import app.vicsmusic.source.api.SourceRegistry
import app.vicsmusic.source.local.LocalMusicSource
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val trackDao: TrackDao,
    private val downloadDao: DownloadDao,
    private val lyricsDao: LyricsDao,
    private val playHistoryDao: PlayHistoryDao,
    private val sourceRegistry: SourceRegistry,
    private val lrclibClient: LrclibClient,
    private val extensionRegistryClient: app.vicsmusic.core.network.ExtensionRegistryClient,
    @ApplicationContext private val context: Context,
    @app.vicsmusic.core.data.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MusicRepository {

    override fun getAllTracks(): Flow<List<Track>> {
        return trackDao.getAllTracks().map { list ->
            list.map { it.asExternalModel() }
        }
    }

    override fun getTrack(id: String): Flow<Track?> {
        return trackDao.getTrack(id).map { it?.asExternalModel() }
    }

    override fun getFavoriteTracks(): Flow<List<Track>> {
        return trackDao.getFavoriteTracks().map { list ->
            list.map { it.asExternalModel() }
        }
    }

    override fun getRecentTracks(limit: Int): Flow<List<Track>> {
        return trackDao.getRecentlyPlayedTracks(limit).map { list ->
            list.map { it.asExternalModel() }
        }
    }

    override fun getDownloadedTracks(): Flow<List<Track>> {
        return downloadDao.getAllDownloads().map { downloads ->
            downloads
                .filter { it.state == DownloadStatus.COMPLETED.name && File(it.localPath).exists() }
                .mapNotNull { dl ->
                    trackDao.getTrack(dl.trackId).firstOrNull()?.asExternalModel()
                }
        }
    }

    override fun getPlayHistory(limit: Int): Flow<List<ListeningEvent>> {
        return playHistoryDao.getHistory(limit).map { history ->
            history.map { entity ->
                val track = trackDao.getTrack(entity.trackId).firstOrNull()
                ListeningEvent(
                    trackId = entity.trackId,
                    artistName = track?.artistName ?: "Artista",
                    completionPercentage = entity.completionPercentage,
                    timestamp = entity.playedAt
                )
            }
        }
    }

    override suspend fun searchTracks(query: String): Result<List<Track>> = withContext(ioDispatcher) {
        runCatching {
            val localDbTracks = trackDao.searchTracks(query).firstOrNull() ?: emptyList()
            if (localDbTracks.isNotEmpty()) {
                localDbTracks.map { it.asExternalModel() }
            } else {
                val allResults = mutableListOf<Track>()
                for (source in sourceRegistry.getAllSources()) {
                    val page = source.search(query, SearchType.TRACKS, 1).getOrNull()
                    if (page != null) {
                        allResults.addAll(page.items)
                    }
                }
                if (allResults.isNotEmpty()) {
                    trackDao.upsertTracks(allResults.map { it.asEntity() })
                }
                allResults
            }
        }
    }

    override suspend fun getStreamInfo(trackId: String, quality: AudioQuality): Result<StreamInfo> =
        withContext(ioDispatcher) {
            runCatching {
                // Check if locally downloaded on device (100% offline available)
                val download = downloadDao.getDownload(trackId).firstOrNull()
                if (download != null && download.state == DownloadStatus.COMPLETED.name) {
                    val localFile = File(download.localPath)
                    if (localFile.exists()) {
                        return@runCatching StreamInfo(
                            trackId = trackId,
                            url = localFile.toURI().toString(),
                            mimeType = "audio/mp4",
                            bitrateKbps = 320,
                            headers = emptyMap()
                        )
                    }
                }

                // Remote streaming from registered source
                val cachedTrack = trackDao.getTrack(trackId).firstOrNull()
                val sourceId = cachedTrack?.sourceId ?: resolveSourceId(trackId)
                val source = sourceRegistry.getSource(sourceId)
                    ?: throw IllegalStateException("Music source '$sourceId' not available for track $trackId")
                source.getStream(trackId, quality).getOrThrow()
            }
        }

    override suspend fun downloadTrack(track: Track, onProgress: (Float) -> Unit): Result<String> =
        withContext(ioDispatcher) {
            runCatching {
                val streamInfo = getStreamInfo(track.id, AudioQuality.HIGH).getOrThrow()
                val downloadDir = File(context.filesDir, "downloads").apply { if (!exists()) mkdirs() }
                val safeFileName = track.id.replace(Regex("[^a-zA-Z0-9_-]"), "_") + ".m4a"
                val targetFile = File(downloadDir, safeFileName)

                onProgress(0.1f)
                val client = VicsHttpClientFactory.create()
                val response = client.get(streamInfo.url)
                if (response.status != HttpStatusCode.OK) {
                    throw IllegalStateException("Failed to download stream, HTTP status ${response.status}")
                }

                val bytes = response.bodyAsBytes()
                targetFile.writeBytes(bytes)
                onProgress(1.0f)

                val downloadEntity = DownloadEntity(
                    trackId = track.id,
                    localPath = targetFile.absolutePath,
                    format = "m4a",
                    quality = AudioQuality.HIGH.name,
                    downloadedAt = System.currentTimeMillis(),
                    sizeBytes = targetFile.length(),
                    state = DownloadStatus.COMPLETED.name
                )
                downloadDao.upsertDownload(downloadEntity)
                trackDao.upsertTracks(listOf(track.asEntity()))

                targetFile.absolutePath
            }
        }

    override suspend fun deleteDownloadedTrack(trackId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val download = downloadDao.getDownload(trackId).firstOrNull()
            if (download != null) {
                val file = File(download.localPath)
                if (file.exists()) {
                    file.delete()
                }
                downloadDao.deleteDownload(trackId)
            }
        }
    }

    override suspend fun getLyrics(track: Track): Result<Lyrics?> = withContext(ioDispatcher) {
        runCatching {
            val cached = lyricsDao.getLyrics(track.id).firstOrNull()
            if (cached != null && cached.content.isNotBlank()) {
                return@runCatching LrcParser.parse(
                    trackId = track.id,
                    lrcContent = cached.content,
                    source = cached.source
                )
            }

            val durationSec = (track.durationMs / 1000).toInt().takeIf { it > 0 }
            val fetched = lrclibClient.getLyrics(
                trackId = track.id,
                trackName = track.title,
                artistName = track.artistName,
                durationSeconds = durationSec,
                albumName = track.albumName
            ).getOrNull() ?: lrclibClient.searchLyrics(
                trackId = track.id,
                query = "${track.title} ${track.artistName}"
            ).getOrNull()

            if (fetched != null && fetched.lines.isNotEmpty()) {
                val rawLrc = fetched.lines.joinToString("\n") { line ->
                    val totalSec = line.timeMs / 1000
                    val min = totalSec / 60
                    val sec = totalSec % 60
                    val ms = (line.timeMs % 1000) / 10
                    String.format(Locale.US, "[%02d:%02d.%02d] %s", min, sec, ms, line.text)
                }
                lyricsDao.insertLyrics(
                    LyricsCacheEntity(
                        trackId = track.id,
                        content = rawLrc,
                        source = fetched.source
                    )
                )
            }
            fetched
        }
    }

    override suspend fun recordPlayHistory(trackId: String, completionPercentage: Float) = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        playHistoryDao.insertHistory(
            PlayHistoryEntity(
                trackId = trackId,
                playedAt = now,
                completionPercentage = completionPercentage
            )
        )
        trackDao.updatePlayCount(trackId, now)
    }

    override suspend fun toggleLike(trackId: String): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            val current = trackDao.getTrack(trackId).firstOrNull()
                ?: throw NoSuchElementException("Track $trackId not found in local database")
            val newLikedStatus = !current.isLiked
            trackDao.setLiked(trackId, newLikedStatus)
            newLikedStatus
        }
    }

    override suspend fun syncLocalTracks(): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            val localSource = sourceRegistry.getSource(LocalMusicSource.SOURCE_ID) as? LocalMusicSource
                ?: throw IllegalStateException("LocalMusicSource is not registered")
            val localTracks = localSource.getAllTracks().getOrThrow()
            if (localTracks.isNotEmpty()) {
                trackDao.upsertTracks(localTracks.map { it.asEntity() })
            }
            localTracks.size
        }
    }

    override suspend fun getAvailableExtensions(): Result<List<app.vicsmusic.core.model.ExtensionItem>> =
        withContext(ioDispatcher) {
            runCatching {
                extensionRegistryClient.fetchRegistry().getOrElse {
                    extensionRegistryClient.parseRegistryJson(app.vicsmusic.core.network.ExtensionRegistryClient.FALLBACK_REGISTRY_JSON)
                }.extensions
            }
        }

    override suspend fun seedCatalogTracks(tracks: List<Track>): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            trackDao.upsertTracks(tracks.map { it.asEntity() })
        }
    }

    private fun resolveSourceId(trackId: String): String {
        return when {
            trackId.startsWith("local_") -> LocalMusicSource.SOURCE_ID
            trackId.startsWith(app.vicsmusic.source.remote.YouTubeMusicSource.TRACK_PREFIX) -> app.vicsmusic.source.remote.YouTubeMusicSource.SOURCE_ID
            trackId.startsWith("jamendo_") -> "jamendo"
            else -> "local"
        }
    }
}
