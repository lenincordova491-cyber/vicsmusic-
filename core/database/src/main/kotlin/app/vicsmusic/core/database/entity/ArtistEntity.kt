package app.vicsmusic.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "artists",
    indices = [
        Index("name")
    ]
)
data class ArtistEntity(
    @PrimaryKey
    val id: String,
    val sourceId: String,
    val name: String,
    val artworkUrl: String?
)
