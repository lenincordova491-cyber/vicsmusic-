package app.vicsmusic.core.data.backup

import app.vicsmusic.core.database.dao.PlayHistoryDao
import app.vicsmusic.core.database.dao.PlaylistDao
import app.vicsmusic.core.database.dao.SearchHistoryDao
import app.vicsmusic.core.database.dao.TrackDao
import app.vicsmusic.core.domain.repository.UserPreferencesRepository
import app.vicsmusic.core.model.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupManagerTest {

    private val trackDao: TrackDao = mockk(relaxed = true)
    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val playHistoryDao: PlayHistoryDao = mockk(relaxed = true)
    private val searchHistoryDao: SearchHistoryDao = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)

    private lateinit var backupManager: BackupManager

    @Before
    fun setUp() {
        io.mockk.every { trackDao.getAllTracks() } returns flowOf(emptyList())
        io.mockk.every { playlistDao.getAllPlaylists() } returns flowOf(emptyList())
        io.mockk.every { trackDao.getFavoriteTracks() } returns flowOf(emptyList())
        io.mockk.every { searchHistoryDao.getRecentSearches(any()) } returns flowOf(emptyList())
        io.mockk.every { userPreferencesRepository.userPreferences } returns flowOf(
            UserPreferences(
                backendServerUrl = "https://custom-backend.test",
                isBackendEnabled = true,
                backendUrl = "https://custom-backend.test",
                backendEnabled = true
            )
        )

        backupManager = BackupManager(
            trackDao = trackDao,
            playlistDao = playlistDao,
            playHistoryDao = playHistoryDao,
            searchHistoryDao = searchHistoryDao,
            userPreferencesRepository = userPreferencesRepository
        )
    }

    @Test
    fun `createBackupJson exports formatVersion 2 with backend configuration`() = runTest {
        val result = backupManager.createBackupJson()
        if (result.isFailure) {
            org.junit.Assert.fail("createBackupJson failed: ${result.exceptionOrNull()?.stackTraceToString()}")
        }
        assertTrue(result.isSuccess)
        val json = result.getOrThrow()
        val decoded = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<VicsMusicBackup>(json)

        assertEquals(2, decoded.formatVersion)
        assertEquals(true, decoded.backendEnabled)
        assertEquals("https://custom-backend.test", decoded.backendUrl)
    }

    @Test
    fun `restoreFromJson restores backend config and tracks`() = runTest {
        val jsonV2 = """
            {
                "formatVersion": 2,
                "exportedAt": 1700000000000,
                "backendEnabled": true,
                "backendUrl": "https://restored-backend.test",
                "tracks": [],
                "playlists": [],
                "playlistItems": [],
                "favoriteTrackIds": [],
                "searchHistory": ["Bad Bunny", "Taylor Swift"]
            }
        """.trimIndent()

        val stats = backupManager.restoreFromJson(jsonV2)
        assertTrue(stats.isSuccess)

        coVerify {
            userPreferencesRepository.setBackendConfig(true, "https://restored-backend.test")
            searchHistoryDao.insertSearch(match { it.query == "Bad Bunny" })
            searchHistoryDao.insertSearch(match { it.query == "Taylor Swift" })
        }
    }

    @Test
    fun `restoreFromJson supports v1 backwards compatibility`() = runTest {
        val jsonV1 = """
            {
                "formatVersion": 1,
                "exportedAt": 1600000000000,
                "tracks": [],
                "playlists": [],
                "playlistItems": [],
                "favoriteTrackIds": [],
                "searchHistory": []
            }
        """.trimIndent()

        val stats = backupManager.restoreFromJson(jsonV1)
        assertTrue(stats.isSuccess)
        assertEquals(0, stats.getOrThrow().tracksCount)
    }
}
