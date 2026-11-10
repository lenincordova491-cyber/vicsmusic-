package app.vicsmusic.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Chart(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val tracks: List<Track>,
    val coverUrl: String? = null
)
