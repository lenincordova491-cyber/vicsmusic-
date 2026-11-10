package app.vicsmusic.core.domain.repository

import app.vicsmusic.core.model.AudioQuality
import app.vicsmusic.core.model.ThemeMode
import app.vicsmusic.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val userPreferences: Flow<UserPreferences>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setWifiQuality(quality: AudioQuality)
    suspend fun setCellularQuality(quality: AudioQuality)
    suspend fun setCrossfadeDuration(seconds: Int)
    suspend fun setLastPlayed(trackId: String, positionMs: Long)
    suspend fun setActiveSourceIds(sourceIds: Set<String>)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setFavoriteArtists(artists: Set<String>)
    suspend fun setAutoplayEnabled(enabled: Boolean)
    suspend fun setYtmSession(cookieHeader: String?, visitorData: String?)
    suspend fun clearYtmSession()
    suspend fun setBackendConfig(enabled: Boolean, url: String)
    suspend fun setBackendServerUrl(url: String)
    suspend fun setBackendEnabled(enabled: Boolean)
    suspend fun setUpdateJsonUrl(url: String)
    suspend fun checkBackendHealth(url: String): Boolean
}
