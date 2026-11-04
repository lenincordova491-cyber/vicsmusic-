package app.vicsmusic.core.domain

import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.model.Track
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {

    private val repository = mockk<MusicRepository>()

    @Test
    fun `search tracks returns repository result successfully`() = runTest {
        val sampleTracks = listOf(
            Track(
                id = "1",
                sourceId = "local",
                title = "Starlight",
                artistId = "10",
                artistName = "Muse",
                durationMs = 240_000L
            )
        )
        coEvery { repository.searchTracks("Starlight") } returns Result.success(sampleTracks)

        val result = repository.searchTracks("Starlight")
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("Starlight", result.getOrNull()?.first()?.title)
    }
}
