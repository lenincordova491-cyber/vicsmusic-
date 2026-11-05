package app.vicsmusic.core.domain.repository

import app.vicsmusic.core.model.Playlist
import app.vicsmusic.core.model.Track
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getPlaylists(): Flow<List<Playlist>>
    fun getPlaylist(id: String): Flow<Playlist?>
    fun getPlaylistTracks(playlistId: String): Flow<List<Track>>
    suspend fun createPlaylist(title: String, description: String? = null): Result<Playlist>
    suspend fun deletePlaylist(playlistId: String): Result<Unit>
    suspend fun addTrackToPlaylist(playlistId: String, trackId: String): Result<Unit>
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Result<Unit>
}
