package app.vicsmusic.core.model

import kotlinx.serialization.Serializable

@Serializable
data class QueueItem(
    val track: Track,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)
