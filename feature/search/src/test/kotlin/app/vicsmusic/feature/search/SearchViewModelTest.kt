package app.vicsmusic.feature.search

import app.vicsmusic.core.domain.usecase.SearchTracksUseCase
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.player.PlaybackConnection
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val searchTracksUseCase: SearchTracksUseCase = mockk()
    private val playbackConnection: PlaybackConnection = mockk(relaxed = true)

    private val track1 = Track(
        id = "1",
        sourceId = "local",
        title = "Bohemian Rhapsody",
        artistId = "q1",
        artistName = "Queen",
        albumId = "a1",
        albumName = "A Night at the Opera",
        durationMs = 354_000L
    )
    private val track2 = Track(
        id = "2",
        sourceId = "local",
        title = "Radio Ga Ga",
        artistId = "q1",
        artistName = "Queen",
        albumId = "a2",
        albumName = "The Works",
        durationMs = 345_000L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when query is typed, debounces and performs search`() = runTest(testDispatcher) {
        coEvery { searchTracksUseCase("Queen") } returns Result.success(listOf(track1, track2))

        val viewModel = SearchViewModel(searchTracksUseCase, playbackConnection)
        viewModel.onQueryChange("Queen")

        advanceTimeBy(350)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertEquals(2, state.results.size)
        assertEquals(2, state.filteredResults.size)
    }

    @Test
    fun `filter changes filter results accordingly`() = runTest(testDispatcher) {
        coEvery { searchTracksUseCase("Bohemian") } returns Result.success(listOf(track1))

        val viewModel = SearchViewModel(searchTracksUseCase, playbackConnection)
        viewModel.onQueryChange("Bohemian")

        advanceTimeBy(350)
        advanceUntilIdle()

        viewModel.onFilterSelect(SearchFilter.TRACKS)
        assertEquals(1, viewModel.uiState.value.filteredResults.size)

        viewModel.onFilterSelect(SearchFilter.ARTISTS)
        assertEquals(0, viewModel.uiState.value.filteredResults.size)
    }

    @Test
    fun `playTrack delegates to playbackConnection playQueue`() = runTest(testDispatcher) {
        coEvery { searchTracksUseCase("Queen") } returns Result.success(listOf(track1))

        val viewModel = SearchViewModel(searchTracksUseCase, playbackConnection)
        viewModel.onQueryChange("Queen")
        advanceTimeBy(350)
        advanceUntilIdle()

        viewModel.playTrack(track1)
        verify { playbackConnection.playQueue(listOf(track1), 0) }
    }
}
