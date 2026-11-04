package app.vicsmusic.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Album(
    val id: String,
    val sourceId: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val artworkUrl: String? = null,
    val releaseYear: Int? = null,
    val trackCount: Int = 0,
    val tracks: List<Track> = emptyList()
)
