package app.vicsmusic.core.domain.repository

import app.vicsmusic.core.model.AudioQuality
import app.vicsmusic.core.model.StreamInfo
import app.vicsmusic.core.model.Track
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    fun getFavoriteTracks(): Flow<List<Track>>
    fun getRecentTracks(): Flow<List<Track>>
    suspend fun searchTracks(query: String): Result<List<Track>>
    suspend fun getStreamInfo(trackId: String, quality: AudioQuality): Result<StreamInfo>
    suspend fun toggleLike(trackId: String): Result<Boolean>
}
