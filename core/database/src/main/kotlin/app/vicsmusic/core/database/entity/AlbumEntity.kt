package app.vicsmusic.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "albums",
    indices = [
        Index("title"),
        Index("artistId")
    ]
)
data class AlbumEntity(
    @PrimaryKey
    val id: String,
    val sourceId: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val artworkUrl: String?,
    val year: Int?,
    val trackCount: Int
)
