package app.vicsmusic.feature.library

import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.domain.repository.PlaylistRepository
import app.vicsmusic.core.model.Playlist
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.player.PlaybackConnection
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
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val musicRepository: MusicRepository = mockk(relaxed = true)
    private val playlistRepository: PlaylistRepository = mockk(relaxed = true)
    private val playbackConnection: PlaybackConnection = mockk(relaxed = true)

    private val sampleTrack = Track(
        id = "1",
        sourceId = "local",
        title = "Hotel California",
        artistId = "e1",
        artistName = "Eagles",
        durationMs = 390_000L
    )
    private val samplePlaylist = Playlist(id = "p1", title = "Rock Classics", trackCount = 1)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { musicRepository.getAllTracks() } returns flowOf(listOf(sampleTrack))
        every { musicRepository.getFavoriteTracks() } returns flowOf(listOf(sampleTrack))
        every { playlistRepository.getPlaylists() } returns flowOf(listOf(samplePlaylist))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observes tracks, favorites and playlists`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(musicRepository, playlistRepository, playbackConnection)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.tracks.size)
        assertEquals(1, state.favorites.size)
        assertEquals(1, state.playlists.size)
    }

    @Test
    fun `tab switching updates selected tab`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(musicRepository, playlistRepository, playbackConnection)
        advanceUntilIdle()

        viewModel.selectTab(LibraryTab.FAVORITES)
        assertEquals(LibraryTab.FAVORITES, viewModel.uiState.value.selectedTab)

        viewModel.selectTab(LibraryTab.PLAYLISTS)
        assertEquals(LibraryTab.PLAYLISTS, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun `playTrack delegates to playbackConnection playQueue`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(musicRepository, playlistRepository, playbackConnection)
        advanceUntilIdle()

        val list = listOf(sampleTrack)
        viewModel.playTrack(sampleTrack, list)
        verify { playbackConnection.playQueue(list, 0) }
    }
}
