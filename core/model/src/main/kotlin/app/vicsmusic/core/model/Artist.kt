package app.vicsmusic.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Artist(
    val id: String,
    val sourceId: String,
    val name: String,
    val imageUrl: String? = null,
    val bio: String? = null,
    val topTracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList()
)
