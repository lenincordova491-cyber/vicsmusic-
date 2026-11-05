package app.vicsmusic.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

@Serializable
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val wifiQuality: AudioQuality = AudioQuality.HIGH,
    val cellularQuality: AudioQuality = AudioQuality.MEDIUM,
    val crossfadeDurationSeconds: Int = 0,
    val lastPlayedTrackId: String? = null,
    val lastPlayedPositionMs: Long = 0L,
    val activeSourceIds: Set<String> = setOf("local", "jamendo")
)
