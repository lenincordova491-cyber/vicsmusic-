package app.vicsmusic.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val sourceId: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val albumId: String? = null,
    val albumName: String? = null,
    val durationMs: Long,
    val artworkUrl: String? = null,
    val isLiked: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0
)
