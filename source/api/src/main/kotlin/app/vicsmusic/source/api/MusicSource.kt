package app.vicsmusic.source.api

import app.vicsmusic.core.model.Album
import app.vicsmusic.core.model.Artist
import app.vicsmusic.core.model.AudioQuality
import app.vicsmusic.core.model.StreamInfo
import app.vicsmusic.core.model.Track

enum class SourceCapability {
    SEARCH,
    STREAM,
    LYRICS,
    BROWSE,
    PLAYLISTS
}

enum class SearchType {
    ALL,
    TRACKS,
    ALBUMS,
    ARTISTS,
    PLAYLISTS
}

data class Page<T>(
    val items: List<T>,
    val page: Int,
    val hasNextPage: Boolean
)

data class HomeSection(
    val id: String,
    val title: String,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList()
)

interface MusicSource {
    val id: String
    val displayName: String
    val capabilities: Set<SourceCapability>

    suspend fun search(query: String, type: SearchType, page: Int): Result<Page<Track>>
    suspend fun getTrack(id: String): Result<Track>
    suspend fun getStream(id: String, quality: AudioQuality): Result<StreamInfo>
    suspend fun getAlbum(id: String): Result<Album>
    suspend fun getArtist(id: String): Result<Artist>
    suspend fun browseHome(): Result<List<HomeSection>>
}
