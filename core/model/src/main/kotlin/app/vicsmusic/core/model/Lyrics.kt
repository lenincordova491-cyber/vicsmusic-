package app.vicsmusic.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LyricLine(
    val timeMs: Long,
    val text: String
)

@Serializable
data class Lyrics(
    val trackId: String,
    val lines: List<LyricLine> = emptyList(),
    val isSynced: Boolean = false,
    val source: String = "local"
)
