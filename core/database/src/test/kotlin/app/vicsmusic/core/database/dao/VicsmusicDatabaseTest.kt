package app.vicsmusic.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.vicsmusic.core.database.VicsmusicDatabase
import app.vicsmusic.core.database.entity.PlaylistEntity
import app.vicsmusic.core.database.entity.PlaylistTrackEntity
import app.vicsmusic.core.database.entity.QueueEntity
import app.vicsmusic.core.database.entity.SearchHistoryEntity
import app.vicsmusic.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VicsmusicDatabaseTest {

    private lateinit var database: VicsmusicDatabase
    private lateinit var trackDao: TrackDao
    private lateinit var playlistDao: PlaylistDao
    private lateinit var queueDao: QueueDao
    private lateinit var searchHistoryDao: SearchHistoryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VicsmusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        trackDao = database.trackDao()
        playlistDao = database.playlistDao()
        queueDao = database.queueDao()
        searchHistoryDao = database.searchHistoryDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndRetrieveTrack() = runTest {
        val track = TrackEntity(
            id = "local_1",
            sourceId = "local",
            title = "Midnight City",
            artistId = "m83",
            artistName = "M83",
            albumId = "hurry_up",
            albumName = "Hurry Up, We're Dreaming",
            durationMs = 243000L,
            artworkUrl = null,
            isLiked = false,
            addedAt = 1000L,
            lastPlayedAt = null,
            playCount = 0
        )

        trackDao.upsertTrack(track)

        val retrieved = trackDao.getTrack("local_1").first()
        assertNotNull(retrieved)
        assertEquals("Midnight City", retrieved?.title)
        assertEquals("M83", retrieved?.artistName)
    }

    @Test
    fun toggleLikeAndFavorites() = runTest {
        val track1 = TrackEntity(
            id = "t1",
            sourceId = "local",
            title = "Track One",
            artistId = "a1",
            artistName = "Artist 1",
            albumId = null,
            albumName = null,
            durationMs = 180000L,
            artworkUrl = null,
            isLiked = false,
            addedAt = 100L,
            lastPlayedAt = null,
            playCount = 0
        )
        val track2 = track1.copy(id = "t2", title = "Track Two", addedAt = 200L, isLiked = true)

        trackDao.upsertTracks(listOf(track1, track2))

        val favoritesBefore = trackDao.getFavoriteTracks().first()
        assertEquals(1, favoritesBefore.size)
        assertEquals("t2", favoritesBefore.first().id)

        // Like track 1
        trackDao.setLiked("t1", true)
        val favoritesAfter = trackDao.getFavoriteTracks().first()
        assertEquals(2, favoritesAfter.size)
    }

    @Test
    fun searchTracksQuery() = runTest {
        val track1 = TrackEntity(
            id = "s1",
            sourceId = "local",
            title = "Bohemian Rhapsody",
            artistId = "queen",
            artistName = "Queen",
            albumId = null,
            albumName = null,
            durationMs = 354000L,
            artworkUrl = null,
            isLiked = false,
            addedAt = 100L,
            lastPlayedAt = null,
            playCount = 0
        )
        val track2 = track1.copy(id = "s2", title = "Another One Bites the Dust")

        trackDao.upsertTracks(listOf(track1, track2))

        val searchResult = trackDao.searchTracks("Bohemian").first()
        assertEquals(1, searchResult.size)
        assertEquals("Bohemian Rhapsody", searchResult.first().title)

        val searchByArtist = trackDao.searchTracks("Queen").first()
        assertEquals(2, searchByArtist.size)
    }

    @Test
    fun playlistOperations() = runTest {
        val playlist = PlaylistEntity(
            id = "p1",
            title = "Roadtrip Bangers",
            description = "Best tracks for long drives",
            artworkUrl = null,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        playlistDao.upsertPlaylist(playlist)

        val track = TrackEntity(
            id = "t10",
            sourceId = "local",
            title = "Drive",
            artistId = "incubus",
            artistName = "Incubus",
            albumId = null,
            albumName = null,
            durationMs = 230000L,
            artworkUrl = null,
            isLiked = false,
            addedAt = 1000L,
            lastPlayedAt = null,
            playCount = 0
        )
        trackDao.upsertTrack(track)

        val playlistTrack = PlaylistTrackEntity(
            playlistId = "p1",
            trackId = "t10",
            position = 0,
            addedAt = 1000L
        )
        playlistDao.insertPlaylistTrack(playlistTrack)

        val tracksInPlaylist = playlistDao.getTracksForPlaylist("p1").first()
        assertEquals(1, tracksInPlaylist.size)
        assertEquals("Drive", tracksInPlaylist.first().title)

        // Remove track
        playlistDao.removeTrackFromPlaylist("p1", "t10")
        val tracksAfterRemoval = playlistDao.getTracksForPlaylist("p1").first()
        assertTrue(tracksAfterRemoval.isEmpty())
    }

    @Test
    fun queuePersistence() = runTest {
        val queueItems = listOf(
            QueueEntity(position = 0, trackId = "track_a", addedAt = 100L),
            QueueEntity(position = 1, trackId = "track_b", addedAt = 101L),
            QueueEntity(position = 2, trackId = "track_c", addedAt = 102L)
        )

        queueDao.insertQueue(queueItems)
        val restoredQueue = queueDao.getQueue().first()
        assertEquals(3, restoredQueue.size)
        assertEquals("track_a", restoredQueue[0].trackId)
        assertEquals("track_c", restoredQueue[2].trackId)

        queueDao.clearQueue()
        val emptyQueue = queueDao.getQueue().first()
        assertTrue(emptyQueue.isEmpty())
    }

    @Test
    fun searchHistoryOperations() = runTest {
        searchHistoryDao.insertSearch(SearchHistoryEntity(query = "Daft Punk", searchedAt = 200L))
        searchHistoryDao.insertSearch(SearchHistoryEntity(query = "Justice", searchedAt = 300L))

        val recent = searchHistoryDao.getRecentSearches().first()
        assertEquals(2, recent.size)
        assertEquals("Justice", recent.first().query)

        searchHistoryDao.deleteSearch("Justice")
        val afterDelete = searchHistoryDao.getRecentSearches().first()
        assertEquals(1, afterDelete.size)
        assertEquals("Daft Punk", afterDelete.first().query)
    }
}
