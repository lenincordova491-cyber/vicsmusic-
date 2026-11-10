package app.vicsmusic.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchSuggestions(
    val textSuggestions: List<String> = emptyList(),
    val directTracks: List<Track> = emptyList(),
    val directArtists: List<Artist> = emptyList()
)

@Serializable
data class FullSearchResults(
    val topResultTrack: Track? = null,
    val topResultArtist: Artist? = null,
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)
