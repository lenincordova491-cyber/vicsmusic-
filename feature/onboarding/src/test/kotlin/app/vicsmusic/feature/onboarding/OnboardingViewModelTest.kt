package app.vicsmusic.feature.onboarding

import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.domain.repository.UserPreferencesRepository
import app.vicsmusic.core.model.Track
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val musicRepository: MusicRepository = mockk(relaxed = true)

    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = OnboardingViewModel(userPreferencesRepository, musicRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasNoSelections_andCannotContinue() {
        val state = viewModel.uiState.value
        assertTrue(state.selectedArtistIds.isEmpty())
        assertTrue(state.selectedArtists.isEmpty())
        assertEquals("Todos", state.selectedGenre)
        assertFalse(state.canContinue)
        assertEquals("0/3", state.selectionCountText)
    }

    @Test
    fun togglingArtists_updatesSelections_andEnablesContinueWhenThreeOrMore() {
        viewModel.onArtistToggled("grupo_5")
        viewModel.onArtistToggled("soda_stereo")
        assertEquals(2, viewModel.uiState.value.selectedArtistIds.size)
        assertFalse(viewModel.uiState.value.canContinue)

        viewModel.onArtistToggled("the_weeknd")
        assertEquals(3, viewModel.uiState.value.selectedArtistIds.size)
        assertTrue(viewModel.uiState.value.canContinue)

        // Selected artist objects are also populated
        assertEquals(3, viewModel.uiState.value.selectedArtists.size)
        assertTrue(viewModel.uiState.value.selectedArtists.any { it.name == "Grupo 5" })
    }

    @Test
    fun selectingGenre_updatesSelectedGenre() {
        viewModel.onGenreSelected("Cumbia")
        assertEquals("Cumbia", viewModel.uiState.value.selectedGenre)
    }

    @Test
    fun searchTracks_debouncesAndFindsRemoteArtists() = runTest(testDispatcher) {
        val mockTrack = Track(
            id = "yt_123",
            title = "Un Cumbión",
            artistName = "Artista Nuevo Desconocido",
            artistId = "remote_artist_123",
            artworkUrl = "https://example.com/art.jpg",
            durationMs = 180000L,
            sourceId = "innertube"
        )
        coEvery { musicRepository.searchTracks("Artista") } returns Result.success(listOf(mockTrack))

        viewModel.onSearchQueryChanged("Artista")
        advanceTimeBy(400) // Advance past 350ms debounce

        val state = viewModel.uiState.value
        assertEquals("Artista", state.searchQuery)
        assertTrue(state.remoteArtists.isNotEmpty())
        assertEquals("Artista Nuevo Desconocido", state.remoteArtists.first().name)
    }

    @Test
    fun completeOnboarding_savesSelectedArtistsAndMarksCompleted() = runTest(testDispatcher) {
        viewModel.onArtistToggled("grupo_5")
        viewModel.onArtistToggled("soda_stereo")
        viewModel.onArtistToggled("bad_bunny")

        var finished = false
        viewModel.completeOnboarding { finished = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(finished)
        coVerify(exactly = 1) {
            userPreferencesRepository.setFavoriteArtists(match {
                it.contains("Grupo 5") && it.contains("Soda Stereo") && it.contains("Bad Bunny")
            })
        }
        coVerify(exactly = 1) {
            userPreferencesRepository.setOnboardingCompleted(true)
        }
    }
}
