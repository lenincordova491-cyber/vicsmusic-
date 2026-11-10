package app.vicsmusic.feature.search

import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.domain.usecase.SearchTracksUseCase
import app.vicsmusic.core.model.ExtensionItem
import app.vicsmusic.core.model.ExtensionRegistry
import app.vicsmusic.core.model.FullSearchResults
import app.vicsmusic.core.model.SearchSuggestions
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.network.ExtensionRegistryClient
import app.vicsmusic.core.player.PlaybackConnection
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val searchTracksUseCase: SearchTracksUseCase = mockk()
    private val playbackConnection: PlaybackConnection = mockk(relaxed = true)
    private val extensionRegistryClient: ExtensionRegistryClient = mockk()
    private val musicRepository: MusicRepository = mockk(relaxed = true)

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
        every { musicRepository.getSearchHistory(any()) } returns flowOf(emptyList())
        coEvery { musicRepository.getSearchSuggestions(any()) } returns Result.success(SearchSuggestions())
        coEvery { musicRepository.searchFullCatalog(any()) } returns Result.success(FullSearchResults())
        coEvery { searchTracksUseCase(any()) } returns Result.success(listOf(track1, track2))
        coEvery { extensionRegistryClient.fetchRegistry(any()) } returns Result.success(
            ExtensionRegistry(
                version = 1,
                extensions = listOf(
                    ExtensionItem(
                        id = "spotify-web",
                        name = "spotify-web",
                        displayName = "Spotify Web",
                        version = "1.0.0"
                    )
                )
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load loads catalog extensions`() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(searchTracksUseCase, playbackConnection, extensionRegistryClient, musicRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoadingCatalog)
        assertEquals(1, state.catalogExtensions.size)
        assertEquals("Spotify Web", state.catalogExtensions.first().displayName)
    }

    @Test
    fun `when query is typed, debounces and performs search`() = runTest(testDispatcher) {
        coEvery { musicRepository.searchFullCatalog("Queen") } returns Result.success(
            FullSearchResults(
                topResultTrack = track1,
                tracks = listOf(track1, track2)
            )
        )

        val viewModel = SearchViewModel(searchTracksUseCase, playbackConnection, extensionRegistryClient, musicRepository)
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
        coEvery { musicRepository.searchFullCatalog("Bohemian") } returns Result.success(
            FullSearchResults(
                topResultTrack = track1,
                tracks = listOf(track1)
            )
        )

        val viewModel = SearchViewModel(searchTracksUseCase, playbackConnection, extensionRegistryClient, musicRepository)
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
        coEvery { musicRepository.searchFullCatalog("Queen") } returns Result.success(
            FullSearchResults(
                topResultTrack = track1,
                tracks = listOf(track1)
            )
        )

        val viewModel = SearchViewModel(searchTracksUseCase, playbackConnection, extensionRegistryClient, musicRepository)
        viewModel.onQueryChange("Queen")
        advanceTimeBy(350)
        advanceUntilIdle()

        viewModel.playTrack(track1)
        verify { playbackConnection.playQueue(listOf(track1), 0) }
    }
}
