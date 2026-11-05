package app.vicsmusic.core.domain.repository

import app.vicsmusic.core.model.AudioQuality
import app.vicsmusic.core.model.StreamInfo
import app.vicsmusic.core.model.Track
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    fun getAllTracks(): Flow<List<Track>>
    fun getTrack(id: String): Flow<Track?>
    fun getFavoriteTracks(): Flow<List<Track>>
    fun getRecentTracks(limit: Int = 50): Flow<List<Track>>
    suspend fun searchTracks(query: String): Result<List<Track>>
    suspend fun getStreamInfo(trackId: String, quality: AudioQuality): Result<StreamInfo>
    suspend fun toggleLike(trackId: String): Result<Boolean>
    suspend fun syncLocalTracks(): Result<Int>
}
