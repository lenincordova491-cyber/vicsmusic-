package app.vicsmusic.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [
        Index("title"),
        Index("artistName"),
        Index("albumId"),
        Index("isLiked"),
        Index("lastPlayedAt")
    ]
)
data class TrackEntity(
    @PrimaryKey
    val id: String,
    val sourceId: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val albumId: String?,
    val albumName: String?,
    val durationMs: Long,
    val artworkUrl: String?,
    val isLiked: Boolean,
    val addedAt: Long,
    val lastPlayedAt: Long?,
    val playCount: Int
)
