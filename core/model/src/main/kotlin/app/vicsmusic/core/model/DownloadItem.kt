package app.vicsmusic.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED
}

@Serializable
data class DownloadItem(
    val trackId: String,
    val localPath: String,
    val format: String = "mp3",
    val quality: AudioQuality = AudioQuality.HIGH,
    val downloadedAt: Long = System.currentTimeMillis(),
    val sizeBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED
)
