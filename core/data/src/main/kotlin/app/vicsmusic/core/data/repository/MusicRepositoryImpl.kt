package app.vicsmusic.core.data.repository

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import app.vicsmusic.core.database.dao.DownloadDao
import app.vicsmusic.core.database.dao.LyricsDao
import app.vicsmusic.core.database.dao.PlayHistoryDao
import app.vicsmusic.core.database.dao.PlaylistDao
import app.vicsmusic.core.database.dao.SearchHistoryDao
import app.vicsmusic.core.database.dao.TrackDao
import app.vicsmusic.core.database.entity.DownloadEntity
import app.vicsmusic.core.database.entity.LyricsCacheEntity
import app.vicsmusic.core.database.entity.PlayHistoryEntity
import app.vicsmusic.core.database.entity.SearchHistoryEntity
import app.vicsmusic.core.database.model.asEntity
import app.vicsmusic.core.database.model.asExternalModel
import app.vicsmusic.core.domain.engine.AdvancedSearchEngine
import app.vicsmusic.core.domain.engine.ListeningEvent
import app.vicsmusic.core.domain.engine.LyricMatchHint
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
import app.vicsmusic.source.api.SourceCapability
import app.vicsmusic.source.api.SourceRegistry
import app.vicsmusic.source.local.LocalMusicSource
import app.vicsmusic.source.remote.declarative.DeclarativeExtensionManager
import app.vicsmusic.source.remote.innertube.InnerTubeClient
import app.vicsmusic.source.remote.innertube.InnerTubeParser
import app.vicsmusic.source.remote.innertube.InnerTubeSessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val downloadDao: DownloadDao,
    private val lyricsDao: LyricsDao,
    private val playHistoryDao: PlayHistoryDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val sourceRegistry: SourceRegistry,
    private val lrclibClient: LrclibClient,
    private val extensionRegistryClient: app.vicsmusic.core.network.ExtensionRegistryClient,
    private val declarativeExtensionManager: DeclarativeExtensionManager,
    private val innerTubeSessionManager: InnerTubeSessionManager,
    private val innerTubeClient: InnerTubeClient,
    private val spotifyChartsClient: app.vicsmusic.core.network.SpotifyChartsClient = app.vicsmusic.core.network.SpotifyChartsClient(),
    private val innerTubeSmartSearch: app.vicsmusic.source.remote.innertube.InnerTubeSmartSearchAndRadio =
        app.vicsmusic.source.remote.innertube.InnerTubeSmartSearchAndRadio(
            innerTubeClient = innerTubeClient,
            httpClient = app.vicsmusic.core.network.VicsHttpClientFactory.create(),
            sessionManager = innerTubeSessionManager
        ),
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

    private val advancedSearchEngine = AdvancedSearchEngine()
    private val playlistImporter = app.vicsmusic.core.data.importer.PlaylistImporter(
        playlistDao = playlistDao,
        trackDao = trackDao,
        innerTubeClient = innerTubeClient
    )

    private suspend fun upsertTracksPreservingUserState(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val ids = tracks.map { it.id }
        val existingById = trackDao.getTracks(ids).firstOrNull().orEmpty().associateBy { it.id }
        val merged = tracks.map { remote ->
            val existing = existingById[remote.id]
            if (existing != null) {
                remote.asEntity().copy(
                    isLiked = existing.isLiked,
                    addedAt = existing.addedAt,
                    lastPlayedAt = existing.lastPlayedAt,
                    playCount = existing.playCount,
                    durationMs = if (remote.durationMs > 0L) remote.durationMs else existing.durationMs,
                    artworkUrl = remote.artworkUrl ?: existing.artworkUrl
                )
            } else {
                remote.asEntity()
            }
        }
        trackDao.upsertTracks(merged)
    }

    override suspend fun searchTracks(query: String): Result<List<Track>> = withContext(ioDispatcher) {
        runCatching {
            val clean = query.trim()
            if (clean.isEmpty()) return@runCatching emptyList()

            // 1. Guardar historial
            runCatching { searchHistoryDao.insertSearch(SearchHistoryEntity(clean, System.currentTimeMillis())) }

            // 2. Buscar en catálogo local y letras cacheadas en Room (excluyendo previews de 30s obsoletos)
            val deprecatedPreviewSources = setOf("itunes-preview", "deezer-preview", "openverse-audio", "ccmixter", "internet-archive")
            val directLocal = trackDao.searchTracks(clean).firstOrNull()?.map { it.asExternalModel() }.orEmpty()
                .filter { it.sourceId != "local" && it.sourceId !in deprecatedPreviewSources }
            val allCatalogOnline = trackDao.getAllTracks().firstOrNull()?.map { it.asExternalModel() }.orEmpty()
                .filter { it.sourceId != "local" && it.sourceId !in deprecatedPreviewSources }
            val cachedLyricMatchIds = runCatching {
                lyricsDao.searchCachedLyrics(clean).map { it.trackId }.toSet()
            }.getOrDefault(emptySet())
            val cachedLyricTracks = allCatalogOnline.filter { cachedLyricMatchIds.contains(it.id) }

            // 3. Búsqueda paralela multi-señal únicamente en fuentes de pistas completas (sin previews de 30s)
            val isLyricLike = advancedSearchEngine.isLikelyLyricQuery(clean)
            val ytSource = sourceRegistry.getSource(app.vicsmusic.source.remote.YouTubeMusicSource.SOURCE_ID)
            val searchableSources = sourceRegistry.getAllSources().filter { source ->
                source !is LocalMusicSource &&
                    SourceCapability.SEARCH in source.capabilities &&
                    SourceCapability.STREAM in source.capabilities &&
                    source.id !in deprecatedPreviewSources &&
                    source.id in setOf("ytmusic", "jiosaavn", "piped-music", "soundcloud", "podcasts-apple")
            }

            val (remoteDirect, lrclibCandidates, remoteLyricSearch) = coroutineScope {
                val directDeferred = async {
                    searchableSources.map { source ->
                        async {
                            withTimeoutOrNull(6_000L) {
                                source.search(clean, SearchType.TRACKS, 1).getOrNull()?.items
                                    ?.filter { t -> t.sourceId != "local" }.orEmpty()
                            }.orEmpty()
                        }
                    }.awaitAll().flatten()
                }

                val lrclibDeferred = async {
                    if (clean.length >= 4) {
                        withTimeoutOrNull(4_500L) {
                            lrclibClient.searchSongsByLyricPhrase(clean, limit = 4)
                        }.orEmpty()
                    } else {
                        emptyList()
                    }
                }

                val ytLyricDeferred = async {
                    if (isLyricLike && ytSource != null && !clean.lowercase().contains("letra")) {
                        withTimeoutOrNull(5_500L) {
                            ytSource.search("$clean letra", SearchType.TRACKS, 1).getOrNull()?.items.orEmpty()
                        }.orEmpty()
                    } else {
                        emptyList()
                    }
                }

                Triple(directDeferred.await(), lrclibDeferred.await(), ytLyricDeferred.await())
            }

            // 4. Si LRCLIB identificó canciones exactas por un fragmento de letra que no están en los resultados directos,
            // resolverlas inmediatamente en YouTube Music para que el usuario pueda reproducirlas al instante
            val resolvedLyricTracks = mutableListOf<Track>()
            if (ytSource != null && lrclibCandidates.isNotEmpty()) {
                val topHintsToResolve = lrclibCandidates.take(2).filter { candidate ->
                    val normCandTitle = advancedSearchEngine.normalize(candidate.trackName)
                    remoteDirect.none { advancedSearchEngine.normalize(it.title).contains(normCandTitle) }
                }
                coroutineScope {
                    val jobs = topHintsToResolve.map { cand ->
                        async {
                            withTimeoutOrNull(4_500L) {
                                ytSource.search("${cand.trackName} ${cand.artistName}", SearchType.TRACKS, 1)
                                    .getOrNull()?.items?.take(2).orEmpty()
                            }.orEmpty()
                        }
                    }
                    jobs.forEach { resolvedLyricTracks.addAll(it.await()) }
                }
            }

            val allRemote = (remoteDirect + remoteLyricSearch + resolvedLyricTracks).distinctBy { it.id }
            if (allRemote.isNotEmpty()) {
                upsertTracksPreservingUserState(allRemote)
            }

            val lyricHints = lrclibCandidates.map { LyricMatchHint(it.trackName, it.artistName) }
            val candidates = (allRemote + directLocal + cachedLyricTracks).distinctBy { it.id }
            val userPreferredArtists = allCatalogOnline
                .filter { it.isLiked || it.playCount > 0 }
                .map { it.artistName }
                .toSet()

            advancedSearchEngine.rankSearchResults(
                rawQuery = clean,
                candidates = candidates,
                lyricHints = lyricHints,
                cachedLyricMatchTrackIds = cachedLyricMatchIds,
                preferredArtists = userPreferredArtists
            )
        }
    }

    override suspend fun getSearchSuggestions(partialQuery: String): Result<app.vicsmusic.core.model.SearchSuggestions> =
        withContext(ioDispatcher) {
            runCatching {
                val clean = partialQuery.trim()
                if (clean.isEmpty()) {
                    return@runCatching app.vicsmusic.core.model.SearchSuggestions()
                }
                val instant = innerTubeSmartSearch.getInstantSuggestions(clean)
                if (instant.directTracks.isNotEmpty()) {
                    upsertTracksPreservingUserState(instant.directTracks)
                }
                app.vicsmusic.core.model.SearchSuggestions(
                    textSuggestions = instant.textSuggestions,
                    directTracks = instant.directTracks,
                    directArtists = instant.directArtists
                )
            }
        }

    override suspend fun searchFullCatalog(query: String): Result<app.vicsmusic.core.model.FullSearchResults> =
        withContext(ioDispatcher) {
            runCatching {
                val clean = query.trim()
                if (clean.isEmpty()) {
                    return@runCatching app.vicsmusic.core.model.FullSearchResults()
                }

                val (innerTubeFull, hybridTracks) = coroutineScope {
                    val fullDeferred = async {
                        withTimeoutOrNull(6_500L) {
                            innerTubeSmartSearch.searchFullCatalog(clean).getOrNull()
                        }
                    }
                    val tracksDeferred = async {
                        searchTracks(clean).getOrDefault(emptyList())
                    }
                    Pair(fullDeferred.await(), tracksDeferred.await())
                }

                val combinedCandidates = buildList {
                    innerTubeFull?.topResultTrack?.let { add(it) }
                    innerTubeFull?.songs?.let { addAll(it) }
                    addAll(hybridTracks)
                }.distinctBy { it.id }

                if (combinedCandidates.isNotEmpty()) {
                    upsertTracksPreservingUserState(combinedCandidates)
                }

                val allCatalogOnline = trackDao.getAllTracks().firstOrNull()?.map { it.asExternalModel() }.orEmpty()
                val preferredArtists = allCatalogOnline
                    .filter { it.isLiked || it.playCount > 0 }
                    .map { it.artistName }
                    .toSet()

                val smartRanking = advancedSearchEngine.analyzeAndRankSearch(
                    rawQuery = clean,
                    candidates = combinedCandidates,
                    preferredArtists = preferredArtists
                )

                val finalTracks = smartRanking.rankedTracks
                val bestTopTrack = innerTubeFull?.topResultTrack ?: smartRanking.topResult ?: finalTracks.firstOrNull()

                val realArtists = innerTubeFull?.artists.orEmpty().map { artist ->
                    val artistTracks = finalTracks.filter {
                        it.artistName.contains(artist.name, ignoreCase = true) ||
                            artist.name.contains(it.artistName, ignoreCase = true)
                    }
                    artist.copy(
                        imageUrl = artist.imageUrl ?: artistTracks.firstOrNull()?.artworkUrl,
                        topTracks = artistTracks.ifEmpty { finalTracks.take(5) }
                    )
                }

                val fallbackArtists = if (realArtists.isEmpty()) {
                    smartRanking.matchedArtists.take(6).map { name ->
                        val artistTracks = finalTracks.filter { it.artistName.equals(name, ignoreCase = true) }
                        app.vicsmusic.core.model.Artist(
                            id = "art_${name.lowercase().replace(" ", "_")}",
                            sourceId = "ytmusic",
                            name = name,
                            imageUrl = artistTracks.firstOrNull()?.artworkUrl,
                            topTracks = artistTracks
                        )
                    }
                } else {
                    realArtists
                }

                val realPlaylists = innerTubeFull?.playlists.orEmpty()
                val finalPlaylists = if (realPlaylists.isNotEmpty()) {
                    realPlaylists
                } else if (finalTracks.isNotEmpty()) {
                    listOf(
                        app.vicsmusic.core.model.Playlist(
                            id = "pl_this_is_${clean.lowercase().replace(" ", "_")}",
                            title = "This Is ${clean.replaceFirstChar { it.uppercase() }}",
                            description = "Los temas esenciales y más reproducidos en YouTube Music",
                            artworkUrl = bestTopTrack?.artworkUrl ?: finalTracks.firstOrNull()?.artworkUrl,
                            trackCount = finalTracks.size
                        ),
                        app.vicsmusic.core.model.Playlist(
                            id = "pl_radio_${clean.lowercase().replace(" ", "_")}",
                            title = "Radio de ${clean.replaceFirstChar { it.uppercase() }}",
                            description = "Mix continuo RDAMVM adaptado a tu búsqueda",
                            artworkUrl = finalTracks.getOrNull(1)?.artworkUrl ?: finalTracks.firstOrNull()?.artworkUrl,
                            trackCount = finalTracks.size
                        )
                    )
                } else {
                    emptyList()
                }

                app.vicsmusic.core.model.FullSearchResults(
                    topResultTrack = bestTopTrack,
                    topResultArtist = innerTubeFull?.topResultArtist ?: fallbackArtists.firstOrNull(),
                    tracks = finalTracks,
                    artists = fallbackArtists,
                    playlists = finalPlaylists
                )
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
                            url = Uri.fromFile(localFile).toString(),
                            mimeType = "audio/mpeg",
                            bitrateKbps = 320,
                            headers = emptyMap()
                        )
                    }
                }

                // Remote streaming from registered source or external charts
                val cachedTrack = trackDao.getTrack(trackId).firstOrNull()
                val sourceId = cachedTrack?.sourceId ?: resolveSourceId(trackId)
                val deprecatedPreviewSources = setOf("itunes-preview", "deezer-preview", "openverse-audio", "ccmixter", "internet-archive")
                val source = if (sourceId in deprecatedPreviewSources) null else sourceRegistry.getSource(sourceId)

                if (source != null) {
                    val primaryResult = source.getStream(trackId, quality)
                    if (primaryResult.isSuccess) {
                        return@runCatching primaryResult.getOrThrow()
                    }
                }

                // Self-healing fallback: if track is from an external source (Spotify, Piped, charts)
                // or the primary source failed/is unavailable, resolve by videoId or title + artist on YouTube Music!
                val ytSource = sourceRegistry.getSource(app.vicsmusic.source.remote.YouTubeMusicSource.SOURCE_ID)
                if (sourceId == "piped-music" && ytSource != null) {
                    val rawVideoId = trackId.removePrefix("piped-music_")
                    if (rawVideoId.isNotBlank()) {
                        val directYtStream = ytSource.getStream("yt_$rawVideoId", quality).getOrNull()
                        if (directYtStream != null) {
                            return@runCatching directYtStream.copy(trackId = trackId)
                        }
                    }
                }

                if (sourceId != "local" && cachedTrack != null && cachedTrack.title.isNotBlank()) {
                    val fallbackEngine = ytSource ?: source
                    if (fallbackEngine != null) {
                        val query = "${cachedTrack.title} ${cachedTrack.artistName}".trim()
                        val candidates = fallbackEngine.search(query, SearchType.TRACKS, 1).getOrNull()?.items.orEmpty()
                        for (candidate in candidates.take(3)) {
                            val fallbackStream = fallbackEngine.getStream(candidate.id, quality).getOrNull()
                            if (fallbackStream != null) {
                                return@runCatching fallbackStream.copy(trackId = trackId)
                            }
                        }
                    }
                }

                throw IllegalStateException("Music stream not available for track $trackId (source: $sourceId)")
            }
        }

    override suspend fun downloadTrack(track: Track, onProgress: (Float) -> Unit): Result<String> =
        withContext(ioDispatcher) {
            runCatching {
                val streamInfo = getStreamInfo(track.id, AudioQuality.HIGH).getOrThrow()

                // Guardar en almacenamiento del dispositivo (carpeta Music/BigMusic)
                val externalMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val bigMusicPublic = File(externalMusic, "BigMusic")
                val downloadDir = if (bigMusicPublic.exists() || bigMusicPublic.mkdirs()) {
                    bigMusicPublic
                } else {
                    File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir, "BigMusic").apply {
                        if (!exists()) mkdirs()
                    }
                }

                val cleanArtist = track.artistName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Desconocido" }
                val cleanTitle = track.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Cancion" }
                val targetFile = File(downloadDir, "$cleanArtist - $cleanTitle.mp3")

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val channelId = "big_music_downloads"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
                    val channel = NotificationChannel(
                        channelId,
                        "Descargas de Big Music",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Progreso de descarga de canciones en Big Music"
                        setShowBadge(false)
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val notifId = (track.id.hashCode() and 0x7FFFFFFF)

                fun notifyProgress(percent: Int) {
                    if (notificationManager == null) return
                    val notif = Notification.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setContentTitle("Descargando canción")
                        .setContentText("${track.artistName} - ${track.title} ($percent%)")
                        .setProgress(100, percent, false)
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .build()
                    notificationManager.notify(notifId, notif)
                }

                fun notifyComplete() {
                    if (notificationManager == null) return
                    val notif = Notification.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("Descarga completada")
                        .setContentText("${track.artistName} - ${track.title}")
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .build()
                    notificationManager.notify(notifId, notif)
                }

                val url = URL(streamInfo.url)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 30000
                    streamInfo.headers.forEach { (k, v) -> setRequestProperty(k, v) }
                }

                if (conn.responseCode !in 200..299) {
                    notificationManager?.cancel(notifId)
                    throw IllegalStateException("Fallo al descargar stream: HTTP ${conn.responseCode}")
                }

                val totalBytes = conn.contentLengthLong.takeIf { it > 0L }
                    ?: (streamInfo.bitrateKbps * 1024L * (track.durationMs / 1000L) / 8L).coerceAtLeast(1L)
                var downloadedBytes = 0L

                notifyProgress(0)
                onProgress(0.01f)

                try {
                    conn.inputStream.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var read: Int
                            var lastPercent = -1
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                if (totalBytes > 0) {
                                    val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 99)
                                    if (percent != lastPercent && percent % 2 == 0) {
                                        lastPercent = percent
                                        onProgress(percent / 100f)
                                        notifyProgress(percent)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    notificationManager?.cancel(notifId)
                    if (targetFile.exists()) targetFile.delete()
                    throw e
                }

                onProgress(1.0f)
                notifyComplete()

                // Inyección de etiquetas ID3v2.4 y carátula embebida en alta resolución
                runCatching {
                    app.vicsmusic.core.data.metadata.Id3Tagger.tagMp3(
                        file = targetFile,
                        title = track.title,
                        artist = track.artistName,
                        album = track.albumName,
                        artworkUrl = track.artworkUrl
                    )
                }

                // Notificar al sistema multimedia de Android para que aparezca en el reproductor nativo y galería
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("audio/mpeg", "audio/*"),
                    null
                )

                val downloadEntity = DownloadEntity(
                    trackId = track.id,
                    localPath = targetFile.absolutePath,
                    format = "mp3",
                    quality = AudioQuality.HIGH.name,
                    downloadedAt = System.currentTimeMillis(),
                    sizeBytes = targetFile.length(),
                    state = DownloadStatus.COMPLETED.name
                )
                downloadDao.upsertDownload(downloadEntity)
                upsertTracksPreservingUserState(listOf(track))

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
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                }
                downloadDao.deleteDownload(trackId)
            }
        }
    }

    override suspend fun getLyrics(track: Track): Result<Lyrics?> = withContext(ioDispatcher) {
        runCatching {
            val cached = lyricsDao.getLyrics(track.id).firstOrNull()
            if (cached != null && cached.content.isNotBlank()) {
                val parsed = LrcParser.parse(
                    trackId = track.id,
                    lrcContent = cached.content,
                    plainLyrics = cached.content,
                    source = cached.source,
                    durationMs = track.durationMs
                )
                if (parsed.lines.isNotEmpty()) {
                    return@runCatching parsed
                }
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
                query = "${track.title} ${track.artistName}",
                durationMs = track.durationMs
            ).getOrNull()

            if (fetched != null && fetched.lines.isNotEmpty()) {
                val content = if (fetched.isSynced) {
                    fetched.lines.joinToString("\n") { line ->
                        val totalSec = line.timeMs / 1000
                        val min = totalSec / 60
                        val sec = totalSec % 60
                        val ms = (line.timeMs % 1000) / 10
                        String.format(Locale.US, "[%02d:%02d.%02d] %s", min, sec, ms, line.text)
                    }
                } else {
                    fetched.lines.joinToString("\n") { it.text }
                }
                lyricsDao.insertLyrics(
                    LyricsCacheEntity(
                        trackId = track.id,
                        content = content,
                        source = fetched.source
                    )
                )
            }
            fetched
        }
    }

    override suspend fun getCanvasVideoUrl(track: Track): Result<String?> = withContext(ioDispatcher) {
        runCatching {
            if (track.sourceId == "local") return@runCatching null

            val canvasDir = File(context.cacheDir, "canvas").apply { if (!exists()) mkdirs() }
            val safeId = track.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val cachedFile = File(canvasDir, "canvas_$safeId.mp4")
            if (cachedFile.exists() && cachedFile.length() > 16_384L) {
                return@runCatching Uri.fromFile(cachedFile).toString()
            }

            // 1. Try direct videoId if track is already a YouTube/Piped music video
            val directVideoId = when {
                track.id.startsWith(app.vicsmusic.source.remote.YouTubeMusicSource.TRACK_PREFIX) ->
                    track.id.removePrefix(app.vicsmusic.source.remote.YouTubeMusicSource.TRACK_PREFIX)
                track.id.startsWith("piped-music_") ->
                    track.id.removePrefix("piped-music_")
                else -> null
            }

            if (!directVideoId.isNullOrBlank()) {
                val playerRoot = runCatching { innerTubeClient.player(directVideoId) }.getOrNull()
                val directCanvasUrl = playerRoot?.let { InnerTubeParser.parseCanvasVideoUrl(it) }
                if (!directCanvasUrl.isNullOrBlank()) {
                    return@runCatching directCanvasUrl
                }
            }

            // 2. If the track is an audio-only Topic track or from Spotify/JioSaavn/SoundCloud,
            // resolve the official music video on YouTube to extract its 360p/480p H.264 stream
            val videoQuery = "${track.title} ${track.artistName} official video".trim()
            val searchRoot = runCatching {
                innerTubeClient.search(query = videoQuery, params = null)
            }.getOrNull()

            val candidateVideoId = searchRoot
                ?.let { InnerTubeParser.parseSearch(it).tracks }
                ?.firstOrNull { it.videoId != directVideoId }
                ?.videoId

            if (!candidateVideoId.isNullOrBlank()) {
                val mvPlayerRoot = runCatching { innerTubeClient.player(candidateVideoId) }.getOrNull()
                val mvCanvasUrl = mvPlayerRoot?.let { InnerTubeParser.parseCanvasVideoUrl(it) }
                if (!mvCanvasUrl.isNullOrBlank()) {
                    return@runCatching mvCanvasUrl
                }
            }

            null
        }
    }

    override suspend fun importPlaylistFromUrl(url: String): Result<app.vicsmusic.core.model.Playlist> = withContext(ioDispatcher) {
        playlistImporter.importPlaylist(url).map { imported ->
            app.vicsmusic.core.model.Playlist(
                id = imported.playlistId,
                title = imported.title,
                artworkUrl = imported.artworkUrl,
                trackCount = imported.trackCount
            )
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
            val newLikedStatus = if (current != null) !current.isLiked else true
            if (current != null) {
                trackDao.setLiked(trackId, newLikedStatus)
            }
            newLikedStatus
        }
    }

    override suspend fun toggleTrackLike(track: Track): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            val current = trackDao.getTrack(track.id).firstOrNull()
            val newLikedStatus = if (current != null) !current.isLiked else (!track.isLiked)
            val updatedEntity = if (current != null) {
                current.copy(isLiked = newLikedStatus)
            } else {
                track.asEntity().copy(isLiked = newLikedStatus)
            }
            trackDao.upsertTrack(updatedEntity)
            newLikedStatus
        }
    }

    override suspend fun syncLocalTracks(): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            val localSource = sourceRegistry.getSource(LocalMusicSource.SOURCE_ID) as? LocalMusicSource
                ?: return@runCatching 0
            val tracks = localSource.scanAllLocalTracks()
            if (tracks.isNotEmpty()) {
                trackDao.upsertTracks(tracks.map { it.asEntity() })
            }
            tracks.size
        }
    }

    override suspend fun getAvailableExtensions(): Result<List<app.vicsmusic.core.model.ExtensionItem>> =
        withContext(ioDispatcher) {
            runCatching {
                val declarative = declarativeExtensionManager.getAvailableExtensions()
                if (declarative.isNotEmpty()) {
                    declarative
                } else {
                    extensionRegistryClient.fetchRegistry().getOrElse {
                        extensionRegistryClient.parseRegistryJson(app.vicsmusic.core.network.ExtensionRegistryClient.FALLBACK_REGISTRY_JSON)
                    }.extensions
                }
            }
        }

    override suspend fun getTopCharts(): Result<List<app.vicsmusic.core.model.Chart>> = withContext(ioDispatcher) {
        runCatching {
            val charts = spotifyChartsClient.getLiveCharts()
            val allTracks = charts.flatMap { it.tracks }
            if (allTracks.isNotEmpty()) {
                trackDao.upsertTracksPreservingHistory(allTracks.map { it.asEntity() })
            }
            charts
        }
    }

    override suspend fun seedCatalogTracks(tracks: List<Track>): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val obsoleteIds = listOf(
                "yt_bL18EwQ4s1c", "yt_E7wJTI-1dvQ", "yt_fV-w75x1k9g",
                "yt_gG08o-M6h_w", "yt_xG8_l7M4k58", "yt_k5v3h7f9x2w",
                "yt_c8_v2k4f7mA", "yt_ca48oMV5GwY", "yt_c4_z9x1k2bA",
                "yt_A_g3lMcWUCE", "yt_TIW_y3z8s70"
            )
            for (oldId in obsoleteIds) {
                trackDao.deleteTrack(oldId)
            }
            val previewSourcesToPurge = listOf(
                "itunes-preview",
                "deezer-preview",
                "openverse-audio",
                "ccmixter",
                "internet-archive"
            )
            for (badSource in previewSourcesToPurge) {
                trackDao.deleteTracksBySource(badSource)
            }
            val existingTracks = trackDao.getAllTracks().firstOrNull().orEmpty().associateBy { it.id }
            val entitiesToUpsert = tracks.map { track ->
                val existing = existingTracks[track.id]
                if (existing != null) {
                    existing.copy(
                        title = track.title,
                        artistName = track.artistName,
                        albumName = track.albumName,
                        artworkUrl = track.artworkUrl ?: existing.artworkUrl,
                        durationMs = if (track.durationMs > 0) track.durationMs else existing.durationMs
                    )
                } else {
                    track.asEntity()
                }
            }
            trackDao.upsertTracks(entitiesToUpsert)
        }
    }

    override fun getSearchHistory(limit: Int): Flow<List<String>> =
        searchHistoryDao.getRecentSearches(limit).map { list -> list.map { it.query } }

    override suspend fun addSearchHistory(query: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val clean = query.trim()
            if (clean.isNotEmpty()) {
                searchHistoryDao.insertSearch(SearchHistoryEntity(clean, System.currentTimeMillis()))
            }
        }
    }

    override suspend fun deleteSearchHistory(query: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            searchHistoryDao.deleteSearch(query)
        }
    }

    override suspend fun clearSearchHistory(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            searchHistoryDao.clearSearchHistory()
        }
    }

    override fun updateYouTubeSession(cookieHeader: String?, visitorData: String?) {
        innerTubeSessionManager.updateSession(cookieHeader, visitorData)
    }

    override fun clearYouTubeSession() {
        innerTubeSessionManager.clearSession()
    }

    override fun hasYouTubeSession(): Boolean = innerTubeSessionManager.hasSession()

    override suspend fun syncAuthenticatedHistoryAndLikes(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            if (!innerTubeSessionManager.hasSession()) {
                return@runCatching
            }

            // 1. Sincronizar Feed de Inicio personalizado (FEmusic_home: Quick Picks, Mixes y Recomendaciones)
            runCatching {
                val homeResponse = innerTubeClient.browse(InnerTubeClient.BROWSE_HOME)
                val homeTracks = InnerTubeParser.parseBrowseTracks(homeResponse)
                    .map { it.toDomainTrack() }
                if (homeTracks.isNotEmpty()) {
                    trackDao.upsertTracksPreservingHistory(homeTracks.map { it.asEntity() })
                }
            }

            // 2. Sincronizar Historial de escucha autenticado de Google (FEmusic_history)
            runCatching {
                val historyResponse = innerTubeClient.browse(InnerTubeClient.BROWSE_HISTORY)
                val historyTracks = InnerTubeParser.parseBrowseTracks(historyResponse)
                    .map { it.toDomainTrack() }

                if (historyTracks.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    val entities = historyTracks.mapIndexed { index, track ->
                        track.asEntity().copy(
                            lastPlayedAt = now - (index * 1_000L),
                            playCount = 1
                        )
                    }
                    trackDao.upsertTracksPreservingHistory(entities)
                    historyTracks.take(30).forEachIndexed { index, track ->
                        playHistoryDao.insertHistory(
                            PlayHistoryEntity(
                                trackId = track.id,
                                playedAt = now - (index * 1_000L),
                                completionPercentage = 1.0f
                            )
                        )
                    }
                }
            }

            // 3. Sincronizar Canciones que me gustan (FEmusic_liked_videos)
            runCatching {
                val likedResponse = innerTubeClient.browse(InnerTubeClient.BROWSE_LIKED_SONGS)
                val likedTracks = InnerTubeParser.parseBrowseTracks(likedResponse)
                    .map { it.toDomainTrack().copy(isLiked = true) }

                if (likedTracks.isNotEmpty()) {
                    val likedEntities = likedTracks.map { it.asEntity().copy(isLiked = true) }
                    trackDao.upsertTracksPreservingHistory(likedEntities)
                }
            }
            Unit
        }
    }

    override suspend fun getContinuousRadioTracks(
        seedTrack: Track,
        excludedTrackIds: Set<String>
    ): Result<List<Track>> = withContext(ioDispatcher) {
        runCatching {
            val effectiveSeed = if (seedTrack.id.startsWith(app.vicsmusic.source.remote.YouTubeMusicSource.TRACK_PREFIX)) {
                seedTrack
            } else {
                val ytSource = sourceRegistry.getSource(app.vicsmusic.source.remote.YouTubeMusicSource.SOURCE_ID)
                val query = "${seedTrack.title} ${seedTrack.artistName}".trim()
                ytSource?.search(query, SearchType.TRACKS, 1)?.getOrNull()?.items?.firstOrNull() ?: seedTrack
            }

            val smartTracks = innerTubeSmartSearch.fetchContinuousSmartRadio(
                seedTrack = effectiveSeed,
                excludedTrackIds = excludedTrackIds
            ).getOrNull().orEmpty()

            val rawTracks = if (smartTracks.isNotEmpty()) {
                smartTracks
            } else {
                val rawVideoId = effectiveSeed.id.removePrefix(app.vicsmusic.source.remote.YouTubeMusicSource.TRACK_PREFIX)
                val response = innerTubeClient.nextQueue(
                    videoId = rawVideoId,
                    playlistId = "RDAMVM$rawVideoId"
                )
                InnerTubeParser.parseNextQueueTracks(response).map { it.toDomainTrack() }
            }

            val filtered = rawTracks
                .filter { it.id != seedTrack.id && it.id != effectiveSeed.id && !excludedTrackIds.contains(it.id) }
                .distinctBy { it.id }

            if (filtered.isNotEmpty()) {
                trackDao.upsertTracksPreservingHistory(filtered.map { it.asEntity() })
            }

            filtered
        }
    }

    private fun resolveSourceId(trackId: String): String {
        if (trackId.startsWith("local_")) return LocalMusicSource.SOURCE_ID
        if (trackId.startsWith(app.vicsmusic.source.remote.YouTubeMusicSource.TRACK_PREFIX)) {
            return app.vicsmusic.source.remote.YouTubeMusicSource.SOURCE_ID
        }
        val prefix = trackId.substringBefore('_')
        if (sourceRegistry.getSource(prefix) != null) {
            return prefix
        }
        return "local"
    }
}
