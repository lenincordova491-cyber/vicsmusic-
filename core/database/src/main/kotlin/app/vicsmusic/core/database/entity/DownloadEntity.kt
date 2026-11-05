package app.vicsmusic.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "downloads",
    indices = [
        Index("state"),
        Index("downloadedAt")
    ]
)
data class DownloadEntity(
    @PrimaryKey
    val trackId: String,
    val localPath: String,
    val format: String,
    val quality: String,
    val downloadedAt: Long,
    val sizeBytes: Long,
    val state: String
)
