package app.vicsmusic.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "play_history",
    indices = [
        Index("trackId"),
        Index("playedAt"),
        Index(value = ["trackId", "playedAt"])
    ]
)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val trackId: String,
    val playedAt: Long,
    val completionPercentage: Float
)
