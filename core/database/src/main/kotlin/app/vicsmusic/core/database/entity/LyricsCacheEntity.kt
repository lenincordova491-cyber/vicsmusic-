package app.vicsmusic.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lyrics_cache",
    indices = [
        Index("trackId")
    ]
)
data class LyricsCacheEntity(
    @PrimaryKey
    val trackId: String,
    val content: String,
    val source: String
)
