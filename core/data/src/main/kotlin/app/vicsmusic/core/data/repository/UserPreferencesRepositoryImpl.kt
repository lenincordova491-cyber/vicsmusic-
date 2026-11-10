package app.vicsmusic.core.data.repository

import app.vicsmusic.core.datastore.UserPreferencesDataStore
import app.vicsmusic.core.domain.repository.UserPreferencesRepository
import app.vicsmusic.core.model.AudioQuality
import app.vicsmusic.core.model.ThemeMode
import app.vicsmusic.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: UserPreferencesDataStore,
    private val innerTubeBackendClient: app.vicsmusic.core.network.InnerTubeBackendClient = app.vicsmusic.core.network.InnerTubeBackendClient()
) : UserPreferencesRepository {

    override val userPreferences: Flow<UserPreferences> = dataStore.userPreferences

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.setThemeMode(mode)
    }

    override suspend fun setWifiQuality(quality: AudioQuality) {
        dataStore.setWifiQuality(quality)
    }

    override suspend fun setCellularQuality(quality: AudioQuality) {
        dataStore.setCellularQuality(quality)
    }

    override suspend fun setCrossfadeDuration(seconds: Int) {
        dataStore.setCrossfadeDuration(seconds)
    }

    override suspend fun setLastPlayed(trackId: String, positionMs: Long) {
        dataStore.setLastPlayed(trackId, positionMs)
    }

    override suspend fun setActiveSourceIds(sourceIds: Set<String>) {
        dataStore.setActiveSourceIds(sourceIds)
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.setOnboardingCompleted(completed)
    }

    override suspend fun setFavoriteArtists(artists: Set<String>) {
        dataStore.setFavoriteArtists(artists)
    }

    override suspend fun setAutoplayEnabled(enabled: Boolean) {
        dataStore.setAutoplayEnabled(enabled)
    }

    override suspend fun setYtmSession(cookieHeader: String?, visitorData: String?) {
        dataStore.setYtmSession(cookieHeader, visitorData)
    }

    override suspend fun clearYtmSession() {
        dataStore.clearYtmSession()
    }

    override suspend fun setBackendConfig(enabled: Boolean, url: String) {
        dataStore.setBackendConfig(enabled, url)
    }

    override suspend fun setBackendServerUrl(url: String) {
        dataStore.setBackendServerUrl(url)
    }

    override suspend fun setBackendEnabled(enabled: Boolean) {
        dataStore.setBackendEnabled(enabled)
    }

    override suspend fun setUpdateJsonUrl(url: String) {
        dataStore.setUpdateJsonUrl(url)
    }

    override suspend fun checkBackendHealth(url: String): Boolean {
        return innerTubeBackendClient.checkHealth(url)
    }
}
