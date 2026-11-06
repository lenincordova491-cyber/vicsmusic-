package app.vicsmusic.feature.home

import app.vicsmusic.core.domain.engine.AdaptiveRecommendationEngine
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.domain.repository.UserPreferencesRepository
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.model.UserPreferences
import app.vicsmusic.core.player.PlaybackConnection
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val musicRepository: MusicRepository = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val playbackConnection: PlaybackConnection = mockk(relaxed = true)

    private val sampleTrack = Track(
        id = "local_1",
        sourceId = "local",
        title = "Test Song",
        artistId = "artist_1",
        artistName = "Test Artist",
        durationMs = 180_000L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { musicRepository.getRecentTracks(any()) } returns flowOf(listOf(sampleTrack))
        every { musicRepository.getAllTracks() } returns flowOf(listOf(sampleTrack))
        every { musicRepository.getDownloadedTracks() } returns flowOf(emptyList())
        every { musicRepository.getPlayHistory(any()) } returns flowOf(emptyList())
        every { userPreferencesRepository.userPreferences } returns flowOf(UserPreferences())
        coEvery { musicRepository.getAvailableExtensions() } returns Result.success(emptyList())
        coEvery { musicRepository.searchTracks(any()) } returns Result.success(listOf(sampleTrack))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when created, initializes Spotify catalog state properly`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            musicRepository = musicRepository,
            userPreferencesRepository = userPreferencesRepository,
            playbackConnection = playbackConnection,
            recommendationEngine = AdaptiveRecommendationEngine()
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.quickAccessTracks.isNotEmpty())
        assertEquals(HomeCategoryFilter.ALL, state.selectedFilter)
    }

    @Test
    fun `when category selected, state updates correctly`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            musicRepository = musicRepository,
            userPreferencesRepository = userPreferencesRepository,
            playbackConnection = playbackConnection
        )
        advanceUntilIdle()

        viewModel.onFilterSelected(HomeCategoryFilter.DOWNLOADS)
        assertEquals(HomeCategoryFilter.DOWNLOADS, viewModel.uiState.value.selectedFilter)
    }

    @Test
    fun `playTrack delegates to PlaybackConnection`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            musicRepository = musicRepository,
            userPreferencesRepository = userPreferencesRepository,
            playbackConnection = playbackConnection
        )

        viewModel.playTrack(sampleTrack)
        verify { playbackConnection.playTrack(sampleTrack) }
    }
}
