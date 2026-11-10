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
    val activeSourceIds: Set<String> = setOf(
        "local",
        "ytmusic",
        "ytmusic-spotiflac",
        "spotify-web",
        "soundcloud",
        "apple-music",
        "deezer",
        "jiosaavn",
        "tidal-web",
        "jamendo",
        "audius",
        "somafm",
        "itunes-preview",
        "deezer-preview",
        "internet-archive",
        "openverse-audio",
        "radio-browser",
        "ccmixter",
        "subsonic",
        "lrclib",
        "musicbrainz"
    ),
    val isOnboardingCompleted: Boolean = false,
    val favoriteArtistIds: Set<String> = emptySet(),
    val isAutoplayEnabled: Boolean = true,
    val ytmCookieHeader: String? = null,
    val ytmVisitorData: String? = null,
    val backendServerUrl: String = "http://192.168.1.4:8000",
    val isBackendEnabled: Boolean = true,
    val updateJsonUrl: String = "http://192.168.1.4:8080/update.json",
    val backendEnabled: Boolean = isBackendEnabled,
    val backendUrl: String = backendServerUrl
) {
    val isYtmLoggedIn: Boolean
        get() = !ytmCookieHeader.isNullOrBlank() && (
            ytmCookieHeader.contains("SAPISID=") ||
                ytmCookieHeader.contains("__Secure-3PAPISID=") ||
                ytmCookieHeader.contains("__Secure-1PAPISID=") ||
                ytmCookieHeader.contains("PAPISID=") ||
                ytmCookieHeader.contains("__Secure-3PSID=") ||
                ytmCookieHeader.contains("SID=") ||
                ytmCookieHeader.contains("LOGIN_INFO=")
            )
}
