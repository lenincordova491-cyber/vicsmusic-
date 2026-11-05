package app.vicsmusic.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists",
    indices = [
        Index("title")
    ]
)
data class PlaylistEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String?,
    val artworkUrl: String?,
    val createdAt: Long,
    val updatedAt: Long
)
