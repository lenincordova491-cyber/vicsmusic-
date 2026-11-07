package app.vicsmusic.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.vicsmusic.core.model.AudioQuality
import app.vicsmusic.core.model.ThemeMode
import app.vicsmusic.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vicsmusic_preferences")

class UserPreferencesDataStore(private val dataStore: DataStore<Preferences>) {

    object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val WIFI_QUALITY = stringPreferencesKey("wifi_quality")
        val CELLULAR_QUALITY = stringPreferencesKey("cellular_quality")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val LAST_PLAYED_TRACK_ID = stringPreferencesKey("last_played_track_id")
        val LAST_PLAYED_POSITION_MS = longPreferencesKey("last_played_position_ms")
        val ACTIVE_SOURCE_IDS = stringSetPreferencesKey("active_source_ids")
        val IS_ONBOARDING_COMPLETED = androidx.datastore.preferences.core.booleanPreferencesKey("is_onboarding_completed")
        val FAVORITE_ARTISTS = stringSetPreferencesKey("favorite_artists")
    }

    private val defaultSources = setOf(
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
    )

    val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val themeModeName = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.DARK.name
            val themeMode = runCatching { ThemeMode.valueOf(themeModeName) }.getOrDefault(ThemeMode.DARK)

            val wifiQualityName = preferences[PreferencesKeys.WIFI_QUALITY] ?: AudioQuality.HIGH.name
            val wifiQuality = runCatching { AudioQuality.valueOf(wifiQualityName) }.getOrDefault(AudioQuality.HIGH)

            val cellularQualityName = preferences[PreferencesKeys.CELLULAR_QUALITY] ?: AudioQuality.MEDIUM.name
            val cellularQuality = runCatching { AudioQuality.valueOf(cellularQualityName) }.getOrDefault(AudioQuality.MEDIUM)

            val crossfade = preferences[PreferencesKeys.CROSSFADE_SECONDS] ?: 0
            val lastTrackId = preferences[PreferencesKeys.LAST_PLAYED_TRACK_ID]
            val lastPosition = preferences[PreferencesKeys.LAST_PLAYED_POSITION_MS] ?: 0L
            val activeSources = preferences[PreferencesKeys.ACTIVE_SOURCE_IDS] ?: defaultSources
            val isOnboardingCompleted = preferences[PreferencesKeys.IS_ONBOARDING_COMPLETED] ?: false
            val favoriteArtists = preferences[PreferencesKeys.FAVORITE_ARTISTS] ?: emptySet()

            UserPreferences(
                themeMode = themeMode,
                wifiQuality = wifiQuality,
                cellularQuality = cellularQuality,
                crossfadeDurationSeconds = crossfade.coerceIn(0, 12),
                lastPlayedTrackId = lastTrackId,
                lastPlayedPositionMs = lastPosition,
                activeSourceIds = activeSources,
                isOnboardingCompleted = isOnboardingCompleted,
                favoriteArtistIds = favoriteArtists
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun setWifiQuality(quality: AudioQuality) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WIFI_QUALITY] = quality.name
        }
    }

    suspend fun setCellularQuality(quality: AudioQuality) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CELLULAR_QUALITY] = quality.name
        }
    }

    suspend fun setCrossfadeDuration(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CROSSFADE_SECONDS] = seconds.coerceIn(0, 12)
        }
    }

    suspend fun setLastPlayed(trackId: String, positionMs: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_PLAYED_TRACK_ID] = trackId
            preferences[PreferencesKeys.LAST_PLAYED_POSITION_MS] = positionMs
        }
    }

    suspend fun setActiveSourceIds(sourceIds: Set<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_SOURCE_IDS] = sourceIds
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setFavoriteArtists(artists: Set<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FAVORITE_ARTISTS] = artists
        }
    }
}
