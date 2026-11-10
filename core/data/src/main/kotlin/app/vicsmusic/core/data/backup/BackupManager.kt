package app.vicsmusic.core.data.backup

import app.vicsmusic.core.database.dao.PlayHistoryDao
import app.vicsmusic.core.database.dao.PlaylistDao
import app.vicsmusic.core.database.dao.SearchHistoryDao
import app.vicsmusic.core.database.dao.TrackDao
import app.vicsmusic.core.database.entity.PlaylistEntity
import app.vicsmusic.core.database.entity.PlaylistTrackEntity
import app.vicsmusic.core.database.entity.SearchHistoryEntity
import app.vicsmusic.core.database.model.asEntity
import app.vicsmusic.core.database.model.asExternalModel
import app.vicsmusic.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class VicsMusicBackup(
    val formatVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val tracks: List<Track> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val playlistItems: List<BackupPlaylistItem> = emptyList(),
    val favoriteTrackIds: List<String> = emptyList(),
    val searchHistory: List<String> = emptyList()
)

@Serializable
data class BackupPlaylist(
    val id: String,
    val title: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val trackCount: Int = 0
)

@Serializable
data class BackupPlaylistItem(
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val addedAt: Long
)

data class BackupRestoreStats(
    val tracksCount: Int,
    val playlistsCount: Int,
    val favoritesCount: Int
)

/**
 * Gestor de Respaldo y Restauración total de la biblioteca personal de Vicsmusic.
 * Permite exportar e importar favoritos, playlists, historial y canciones cacheadas en JSON.
 */
@Singleton
class BackupManager @Inject constructor(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val playHistoryDao: PlayHistoryDao,
    private val searchHistoryDao: SearchHistoryDao
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun createBackupJson(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val allTracks = trackDao.getAllTracks().firstOrNull().orEmpty().map { it.asExternalModel() }
            val playlists = playlistDao.getAllPlaylists().firstOrNull().orEmpty()
            val favorites = trackDao.getFavoriteTracks().firstOrNull().orEmpty().map { it.id }
            val searchHistory = searchHistoryDao.getRecentSearches(100).firstOrNull().orEmpty().map { it.query }

            val backupPlaylists = playlists.map {
                BackupPlaylist(
                    id = it.id,
                    title = it.title,
                    description = it.description,
                    artworkUrl = it.artworkUrl,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    trackCount = 0
                )
            }

            // Recolectar items de cada playlist
            val allPlaylistItems = mutableListOf<BackupPlaylistItem>()
            for (pl in playlists) {
                val tracks = playlistDao.getTracksForPlaylist(pl.id).firstOrNull().orEmpty()
                tracks.forEachIndexed { idx, t ->
                    allPlaylistItems.add(
                        BackupPlaylistItem(
                            playlistId = pl.id,
                            trackId = t.id,
                            position = idx,
                            addedAt = pl.createdAt + idx
                        )
                    )
                }
            }

            val backup = VicsMusicBackup(
                formatVersion = 1,
                exportedAt = System.currentTimeMillis(),
                tracks = allTracks,
                playlists = backupPlaylists,
                playlistItems = allPlaylistItems,
                favoriteTrackIds = favorites,
                searchHistory = searchHistory
            )

            json.encodeToString(backup)
        }
    }

    suspend fun exportToFile(targetFile: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonContent = createBackupJson().getOrThrow()
            if (targetFile.parentFile?.exists() == false) {
                targetFile.parentFile?.mkdirs()
            }
            targetFile.writeText(jsonContent, Charsets.UTF_8)
            targetFile
        }
    }

    suspend fun restoreFromJson(jsonContent: String): Result<BackupRestoreStats> = withContext(Dispatchers.IO) {
        runCatching {
            val backup = json.decodeFromString<VicsMusicBackup>(jsonContent)

            // 1. Restaurar canciones
            if (backup.tracks.isNotEmpty()) {
                val trackEntities = backup.tracks.map { track ->
                    val isFav = backup.favoriteTrackIds.contains(track.id)
                    track.asEntity().copy(isLiked = isFav)
                }
                trackDao.upsertTracks(trackEntities)
            }

            // 2. Restaurar playlists
            for (pl in backup.playlists) {
                playlistDao.upsertPlaylist(
                    PlaylistEntity(
                        id = pl.id,
                        title = pl.title,
                        description = pl.description,
                        artworkUrl = pl.artworkUrl,
                        createdAt = pl.createdAt,
                        updatedAt = pl.updatedAt
                    )
                )
            }

            // 3. Restaurar items de playlist
            for (item in backup.playlistItems) {
                playlistDao.insertPlaylistTrack(
                    PlaylistTrackEntity(
                        playlistId = item.playlistId,
                        trackId = item.trackId,
                        position = item.position,
                        addedAt = item.addedAt
                    )
                )
            }

            // 4. Restaurar historial de búsqueda
            for (query in backup.searchHistory) {
                searchHistoryDao.insertSearch(SearchHistoryEntity(query, System.currentTimeMillis()))
            }

            BackupRestoreStats(
                tracksCount = backup.tracks.size,
                playlistsCount = backup.playlists.size,
                favoritesCount = backup.favoriteTrackIds.size
            )
        }
    }

    suspend fun restoreFromFile(file: File): Result<BackupRestoreStats> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists() || file.length() == 0L) {
                throw IllegalArgumentException("Archivo de respaldo no encontrado o vacío: ${file.name}")
            }
            val content = file.readText(Charsets.UTF_8)
            restoreFromJson(content).getOrThrow()
        }
    }
}
