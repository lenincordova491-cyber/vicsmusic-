package app.vicsmusic.core.data.repository

import app.vicsmusic.core.database.dao.TrackDao
import app.vicsmusic.core.database.model.asEntity
import app.vicsmusic.core.database.model.asExternalModel
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.model.AudioQuality
import app.vicsmusic.core.model.StreamInfo
import app.vicsmusic.core.model.Track
import app.vicsmusic.source.api.MusicSource
import app.vicsmusic.source.api.SearchType
import app.vicsmusic.source.api.SourceRegistry
import app.vicsmusic.source.local.LocalMusicSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val trackDao: TrackDao,
    private val sourceRegistry: SourceRegistry,
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

    override suspend fun searchTracks(query: String): Result<List<Track>> = withContext(ioDispatcher) {
        runCatching {
            val localDbTracks = trackDao.searchTracks(query).firstOrNull() ?: emptyList()
            if (localDbTracks.isNotEmpty()) {
                localDbTracks.map { it.asExternalModel() }
            } else {
                // Query registered sources
                val allResults = mutableListOf<Track>()
                for (source in sourceRegistry.getAllSources()) {
                    val page = source.search(query, SearchType.TRACKS, 1).getOrNull()
                    if (page != null) {
                        allResults.addAll(page.items)
                    }
                }
                // Cache into Room
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
                val cachedTrack = trackDao.getTrack(trackId).firstOrNull()
                val sourceId = cachedTrack?.sourceId ?: resolveSourceId(trackId)
                val source = sourceRegistry.getSource(sourceId)
                    ?: throw IllegalStateException("Music source '$sourceId' not available for track $trackId")
                source.getStream(trackId, quality).getOrThrow()
            }
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

    private fun resolveSourceId(trackId: String): String {
        return when {
            trackId.startsWith("local_") -> LocalMusicSource.SOURCE_ID
            trackId.startsWith("jamendo_") -> "jamendo"
            else -> "local"
        }
    }
}
