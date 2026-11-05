package app.vicsmusic.core.domain.usecase

import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.domain.repository.PlaylistRepository
import app.vicsmusic.core.model.AudioQuality
import app.vicsmusic.core.model.Playlist
import app.vicsmusic.core.model.StreamInfo
import app.vicsmusic.core.model.Track
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainUseCasesTest {

    private val musicRepository: MusicRepository = mockk()
    private val playlistRepository: PlaylistRepository = mockk()

    @Test
    fun getLocalTracksUseCaseReturnsFlow() = runTest {
        val tracks = listOf(
            Track(
                id = "local_1",
                sourceId = "local",
                title = "Test Song",
                artistId = "artist_1",
                artistName = "Test Artist",
                durationMs = 200000L
            )
        )
        every { musicRepository.getAllTracks() } returns flowOf(tracks)

        val useCase = GetLocalTracksUseCase(musicRepository)
        val result = useCase().first()

        assertEquals(1, result.size)
        assertEquals("Test Song", result.first().title)
    }

    @Test
    fun searchTracksUseCaseFiltersEmptyQuery() = runTest {
        val useCase = SearchTracksUseCase(musicRepository)
        val emptyResult = useCase("   ")
        assertTrue(emptyResult.isSuccess)
        assertTrue(emptyResult.getOrThrow().isEmpty())

        coEvery { musicRepository.searchTracks("Rock") } returns Result.success(emptyList())
        val searchResult = useCase("Rock")
        assertTrue(searchResult.isSuccess)
        coVerify(exactly = 1) { musicRepository.searchTracks("Rock") }
    }

    @Test
    fun getTrackStreamUseCaseCallsRepository() = runTest {
        val stream = StreamInfo(
            trackId = "local_1",
            url = "content://media/1",
            bitrateKbps = 320
        )
        coEvery { musicRepository.getStreamInfo("local_1", AudioQuality.HIGH) } returns Result.success(stream)

        val useCase = GetTrackStreamUseCase(musicRepository)
        val result = useCase("local_1", AudioQuality.HIGH)

        assertTrue(result.isSuccess)
        assertEquals("content://media/1", result.getOrThrow().url)
    }

    @Test
    fun toggleLikeUseCaseCallsRepository() = runTest {
        coEvery { musicRepository.toggleLike("t1") } returns Result.success(true)

        val useCase = ToggleLikeUseCase(musicRepository)
        val result = useCase("t1")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())
    }

    @Test
    fun getPlaylistsUseCaseReturnsPlaylists() = runTest {
        val playlists = listOf(
            Playlist(id = "p1", title = "Favorites")
        )
        every { playlistRepository.getPlaylists() } returns flowOf(playlists)

        val useCase = GetPlaylistsUseCase(playlistRepository)
        val result = useCase().first()

        assertEquals(1, result.size)
        assertEquals("Favorites", result.first().title)
    }
}
