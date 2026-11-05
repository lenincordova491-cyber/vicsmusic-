package app.vicsmusic.core.player

import app.vicsmusic.core.model.PlaybackState
import app.vicsmusic.core.model.PlaybackStatus
import app.vicsmusic.core.model.RepeatMode
import app.vicsmusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackStateTest {

    @Test
    fun defaultPlaybackStateIsIdle() {
        val state = PlaybackState()
        assertEquals(PlaybackStatus.IDLE, state.status)
        assertNull(state.currentTrack)
        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.durationMs)
        assertFalse(state.isShuffleEnabled)
        assertEquals(RepeatMode.OFF, state.repeatMode)
        assertNull(state.errorMessage)
    }

    @Test
    fun playbackStateUpdatesPropertiesCorrectly() {
        val track = Track(
            id = "t1",
            sourceId = "local",
            title = "Test Song",
            artistId = "a1",
            artistName = "Artist",
            durationMs = 240000L
        )
        val state = PlaybackState(
            status = PlaybackStatus.PLAYING,
            currentTrack = track,
            positionMs = 12000L,
            durationMs = 240000L,
            isShuffleEnabled = true,
            repeatMode = RepeatMode.ALL
        )

        assertEquals(PlaybackStatus.PLAYING, state.status)
        assertEquals("Test Song", state.currentTrack?.title)
        assertEquals(12000L, state.positionMs)
        assertEquals(240000L, state.durationMs)
        assertEquals(RepeatMode.ALL, state.repeatMode)
    }
}
