package app.vicsmusic.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import app.vicsmusic.core.model.AudioQuality
import app.vicsmusic.core.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserPreferencesDataStoreTest {

    @Test
    fun defaultPreferencesAreObsidianDark() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rawDataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { context.preferencesDataStoreFile("test_user_prefs_1") }
        )
        val dataStore = UserPreferencesDataStore(rawDataStore)

        val prefs = dataStore.userPreferences.first()
        assertEquals(ThemeMode.DARK, prefs.themeMode)
        assertEquals(AudioQuality.HIGH, prefs.wifiQuality)
        assertEquals(AudioQuality.MEDIUM, prefs.cellularQuality)
        assertEquals(0, prefs.crossfadeDurationSeconds)
    }

    @Test
    fun updatePreferencesPersistsValues() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rawDataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { context.preferencesDataStoreFile("test_user_prefs_2") }
        )
        val dataStore = UserPreferencesDataStore(rawDataStore)

        dataStore.setThemeMode(ThemeMode.LIGHT)
        dataStore.setWifiQuality(AudioQuality.LOSSLESS)
        dataStore.setCrossfadeDuration(8)
        dataStore.setLastPlayed("track_123", 45000L)

        val updated = dataStore.userPreferences.first()
        assertEquals(ThemeMode.LIGHT, updated.themeMode)
        assertEquals(AudioQuality.LOSSLESS, updated.wifiQuality)
        assertEquals(8, updated.crossfadeDurationSeconds)
        assertEquals("track_123", updated.lastPlayedTrackId)
        assertEquals(45000L, updated.lastPlayedPositionMs)
    }
}
