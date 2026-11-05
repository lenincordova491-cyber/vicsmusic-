package app.vicsmusic.core.data.repository

import app.vicsmusic.core.database.dao.PlaylistDao
import app.vicsmusic.core.database.entity.PlaylistEntity
import app.vicsmusic.core.database.entity.PlaylistTrackEntity
import app.vicsmusic.core.database.model.asExternalModel
import app.vicsmusic.core.domain.repository.PlaylistRepository
import app.vicsmusic.core.model.Playlist
import app.vicsmusic.core.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    @app.vicsmusic.core.data.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PlaylistRepository {

    override fun getPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().map { list ->
            list.map { it.asExternalModel() }
        }
    }

    override fun getPlaylist(id: String): Flow<Playlist?> {
        return playlistDao.getPlaylist(id).map { it?.asExternalModel() }
    }

    override fun getPlaylistTracks(playlistId: String): Flow<List<Track>> {
        return playlistDao.getTracksForPlaylist(playlistId).map { list ->
            list.map { it.asExternalModel() }
        }
    }

    override suspend fun createPlaylist(title: String, description: String?): Result<Playlist> =
        withContext(ioDispatcher) {
            runCatching {
                val now = System.currentTimeMillis()
                val playlist = Playlist(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    description = description,
                    artworkUrl = null,
                    trackCount = 0,
                    durationMs = 0L,
                    createdAt = now,
                    updatedAt = now
                )
                playlistDao.upsertPlaylist(
                    PlaylistEntity(
                        id = playlist.id,
                        title = playlist.title,
                        description = playlist.description,
                        artworkUrl = playlist.artworkUrl,
                        createdAt = playlist.createdAt,
                        updatedAt = playlist.updatedAt
                    )
                )
                playlist
            }
        }

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            playlistDao.clearPlaylistTracks(playlistId)
            playlistDao.deletePlaylist(playlistId)
        }
    }

    override suspend fun addTrackToPlaylist(playlistId: String, trackId: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                playlistDao.insertPlaylistTrack(
                    PlaylistTrackEntity(
                        playlistId = playlistId,
                        trackId = trackId,
                        position = System.currentTimeMillis().toInt(),
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
        }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                playlistDao.removeTrackFromPlaylist(playlistId, trackId)
            }
        }
}
