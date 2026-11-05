package app.vicsmusic.core.player.queue

import app.vicsmusic.core.database.dao.QueueDao
import app.vicsmusic.core.database.dao.TrackDao
import app.vicsmusic.core.model.RepeatMode
import app.vicsmusic.core.model.Track
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QueueManagerTest {

    private val queueDao: QueueDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private lateinit var queueManager: QueueManager

    private val sampleTracks = listOf(
        Track(id = "1", sourceId = "local", title = "Song 1", artistId = "a1", artistName = "Artist 1", durationMs = 180000L),
        Track(id = "2", sourceId = "local", title = "Song 2", artistId = "a1", artistName = "Artist 1", durationMs = 200000L),
        Track(id = "3", sourceId = "local", title = "Song 3", artistId = "a2", artistName = "Artist 2", durationMs = 220000L)
    )

    @Before
    fun setup() {
        coEvery { queueDao.getQueue() } returns flowOf(emptyList())
        queueManager = QueueManager(queueDao, trackDao)
    }

    @Test
    fun setQueueInitializesCorrectState() {
        queueManager.setQueue(sampleTracks, startIndex = 1)
        val state = queueManager.state.value

        assertEquals(3, state.items.size)
        assertEquals(1, state.currentIndex)
        assertEquals("2", state.currentTrack?.id)
        assertTrue(state.hasNext)
        assertTrue(state.hasPrevious)
    }

    @Test
    fun skipToNextAndPreviousTraversesQueue() {
        queueManager.setQueue(sampleTracks, startIndex = 0)

        val nextTrack = queueManager.skipToNext()
        assertNotNull(nextTrack)
        assertEquals("2", nextTrack?.id)
        assertEquals(1, queueManager.state.value.currentIndex)

        val prevTrack = queueManager.skipToPrevious()
        assertNotNull(prevTrack)
        assertEquals("1", prevTrack?.id)
        assertEquals(0, queueManager.state.value.currentIndex)
    }

    @Test
    fun repeatModesCycleCorrectly() {
        assertEquals(RepeatMode.OFF, queueManager.state.value.repeatMode)

        val modeAll = queueManager.toggleRepeat()
        assertEquals(RepeatMode.ALL, modeAll)

        val modeOne = queueManager.toggleRepeat()
        assertEquals(RepeatMode.ONE, modeOne)

        val modeOff = queueManager.toggleRepeat()
        assertEquals(RepeatMode.OFF, modeOff)
    }

    @Test
    fun repeatAllWrapsQueueAtEnd() {
        queueManager.setQueue(sampleTracks, startIndex = 2)
        queueManager.toggleRepeat() // Repeat ALL

        val nextTrack = queueManager.skipToNext()
        assertNotNull(nextTrack)
        assertEquals("1", nextTrack?.id)
        assertEquals(0, queueManager.state.value.currentIndex)
    }

    @Test
    fun repeatOneKeepsCurrentTrack() {
        queueManager.setQueue(sampleTracks, startIndex = 1)
        queueManager.toggleRepeat() // ALL
        queueManager.toggleRepeat() // ONE

        val nextTrack = queueManager.skipToNext()
        assertEquals("2", nextTrack?.id)
        assertEquals(1, queueManager.state.value.currentIndex)
    }

    @Test
    fun shuffleTogglesAndPreservesCurrentPlayingTrack() {
        queueManager.setQueue(sampleTracks, startIndex = 1)
        assertEquals("2", queueManager.state.value.currentTrack?.id)

        val isShuffled = queueManager.toggleShuffle()
        assertTrue(isShuffled)
        assertTrue(queueManager.state.value.isShuffleEnabled)
        assertEquals("2", queueManager.state.value.currentTrack?.id)

        val isUnshuffled = queueManager.toggleShuffle()
        assertFalse(isUnshuffled)
        assertFalse(queueManager.state.value.isShuffleEnabled)
        assertEquals("2", queueManager.state.value.currentTrack?.id)
    }

    @Test
    fun removeAtReindexesQueueSafely() {
        queueManager.setQueue(sampleTracks, startIndex = 2)
        assertEquals(3, queueManager.state.value.items.size)
        assertEquals(2, queueManager.state.value.currentIndex)

        queueManager.removeAt(0)
        assertEquals(2, queueManager.state.value.items.size)
        assertEquals(1, queueManager.state.value.currentIndex)
        assertEquals("3", queueManager.state.value.currentTrack?.id)
    }

    @Test
    fun moveItemReordersCorrectly() {
        queueManager.setQueue(sampleTracks, startIndex = 0)
        assertEquals("1", queueManager.state.value.currentTrack?.id)

        queueManager.moveItem(0, 2)
        assertEquals("1", queueManager.state.value.items[2].id)
        assertEquals(2, queueManager.state.value.currentIndex)
        assertEquals("1", queueManager.state.value.currentTrack?.id)
    }

    @Test
    fun clearQueueResetsState() {
        queueManager.setQueue(sampleTracks)
        queueManager.clear()

        assertEquals(0, queueManager.state.value.items.size)
        assertEquals(-1, queueManager.state.value.currentIndex)
    }
}
