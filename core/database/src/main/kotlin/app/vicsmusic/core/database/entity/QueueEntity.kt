package app.vicsmusic.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_queue",
    indices = [
        Index("position")
    ]
)
data class QueueEntity(
    @PrimaryKey
    val position: Int,
    val trackId: String,
    val addedAt: Long
)
