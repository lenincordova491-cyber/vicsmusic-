package app.vicsmusic.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: String,
    val title: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val durationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
