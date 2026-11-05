package app.vicsmusic.feature.home

import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.model.Track
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val musicRepository: MusicRepository = mockk(relaxed = true)
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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when created, emits recent and local tracks`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(musicRepository, playbackConnection)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.recentTracks.size)
        assertEquals(1, state.localTracks.size)
        assertEquals("Test Song", state.localTracks.first().title)
    }

    @Test
    fun `when playTrack called with queue, calls playbackConnection playQueue`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(musicRepository, playbackConnection)
        advanceUntilIdle()

        val list = listOf(sampleTrack)
        viewModel.playTrack(sampleTrack, list)

        verify { playbackConnection.playQueue(list, 0) }
    }
}
