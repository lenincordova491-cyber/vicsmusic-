package app.vicsmusic.core.domain.repository

import app.vicsmusic.core.model.AudioQuality
import app.vicsmusic.core.model.Lyrics
import app.vicsmusic.core.model.StreamInfo
import app.vicsmusic.core.model.Track
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    fun getAllTracks(): Flow<List<Track>>
    fun getTrack(id: String): Flow<Track?>
    fun getFavoriteTracks(): Flow<List<Track>>
    fun getRecentTracks(limit: Int = 50): Flow<List<Track>>
    fun getDownloadedTracks(): Flow<List<Track>>
    fun getPlayHistory(limit: Int = 100): Flow<List<app.vicsmusic.core.domain.engine.ListeningEvent>>
    suspend fun searchTracks(query: String): Result<List<Track>>
    suspend fun getStreamInfo(trackId: String, quality: AudioQuality): Result<StreamInfo>
    suspend fun toggleLike(trackId: String): Result<Boolean>
    suspend fun syncLocalTracks(): Result<Int>
    suspend fun getLyrics(track: Track): Result<Lyrics?>
    suspend fun downloadTrack(track: Track, onProgress: (Float) -> Unit = {}): Result<String>
    suspend fun deleteDownloadedTrack(trackId: String): Result<Unit>
    suspend fun recordPlayHistory(trackId: String, completionPercentage: Float)
    suspend fun getAvailableExtensions(): Result<List<app.vicsmusic.core.model.ExtensionItem>>
    suspend fun seedCatalogTracks(tracks: List<Track>): Result<Unit>
}
