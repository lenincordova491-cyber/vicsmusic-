package app.vicsmusic.core.domain.engine

import app.vicsmusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveRecommendationEngineTest {

    private val engine = AdaptiveRecommendationEngine()

    private fun createTrack(id: String, artist: String, title: String) = Track(
        id = id,
        sourceId = "remote",
        title = title,
        artistId = "art_$artist",
        artistName = artist,
        durationMs = 200_000L
    )

    @Test
    fun computeRecommendations_boostsArtistWithHighCompletion() {
        val t1 = createTrack("1", "Daft Punk", "Get Lucky")
        val t2 = createTrack("2", "Daft Punk", "One More Time")
        val t3 = createTrack("3", "Coldplay", "Yellow")
        val t4 = createTrack("4", "Radiohead", "Creep")

        val history = listOf(
            ListeningEvent(trackId = "1", artistName = "Daft Punk", completionPercentage = 0.95f, timestamp = 1000L),
            ListeningEvent(trackId = "2", artistName = "Daft Punk", completionPercentage = 0.85f, timestamp = 2000L),
            ListeningEvent(trackId = "3", artistName = "Coldplay", completionPercentage = 0.10f, timestamp = 3000L) // skipped
        )

        val result = engine.computeRecommendations(
            allAvailableTracks = listOf(t1, t2, t3, t4),
            listeningHistory = history
        )

        assertEquals("Daft Punk", result.topArtistAffinity)
        assertTrue(result.dailyMix.first().artistName == "Daft Punk")
        assertTrue(result.jumpBackIn.any { it.id == "1" })
    }

    @Test
    fun computeRecommendations_emptyHistory_returnsDefaultSlices() {
        val tracks = listOf(
            createTrack("1", "Queen", "Bohemian Rhapsody"),
            createTrack("2", "The Beatles", "Hey Jude")
        )

        val result = engine.computeRecommendations(
            allAvailableTracks = tracks,
            listeningHistory = emptyList()
        )

        assertEquals(2, result.dailyMix.size)
        assertTrue(result.jumpBackIn.isEmpty())
    }
}
