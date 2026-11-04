package app.vicsmusic.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AudioQuality {
    LOW,      // ~96 kbps
    MEDIUM,   // ~160 kbps
    HIGH,     // ~320 kbps
    LOSSLESS  // FLAC
}

@Serializable
data class StreamInfo(
    val trackId: String,
    val url: String,
    val mimeType: String = "audio/mpeg",
    val bitrateKbps: Int = 320,
    val expiresAt: Long? = null,
    val headers: Map<String, String> = emptyMap()
) {
    fun isExpired(): Boolean {
        val exp = expiresAt ?: return false
        return System.currentTimeMillis() >= exp - 30_000L // 30s margin
    }
}
