package app.vicsmusic.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackTest {

    @Test
    fun `stream expiration detects margin properly`() {
        val now = System.currentTimeMillis()
        val streamInfoValid = StreamInfo(
            trackId = "track_1",
            url = "https://example.com/audio.mp3",
            expiresAt = now + 60_000L
        )
        assertFalse(streamInfoValid.isExpired())

        val streamInfoExpired = StreamInfo(
            trackId = "track_2",
            url = "https://example.com/audio2.mp3",
            expiresAt = now + 10_000L // Less than 30s margin
        )
        assertTrue(streamInfoExpired.isExpired())
    }

    @Test
    fun `track creation defaults are set accurately`() {
        val track = Track(
            id = "t_100",
            sourceId = "jamendo",
            title = "Midnight Jam",
            artistId = "a_50",
            artistName = "Luna Sound",
            durationMs = 210_000L
        )
        assertEquals("Midnight Jam", track.title)
        assertFalse(track.isLiked)
        assertEquals(0, track.playCount)
    }
}
