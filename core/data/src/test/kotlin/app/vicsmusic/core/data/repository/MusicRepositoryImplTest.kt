package app.vicsmusic.core.data.repository

import app.vicsmusic.core.database.dao.TrackDao
import app.vicsmusic.core.database.entity.TrackEntity
import app.vicsmusic.core.model.AudioQuality
import app.vicsmusic.core.model.StreamInfo
import app.vicsmusic.core.model.Track
import app.vicsmusic.source.api.MusicSource
import app.vicsmusic.source.api.Page
import app.vicsmusic.source.api.SearchType
import app.vicsmusic.source.api.SourceRegistry
import app.vicsmusic.source.local.LocalMusicSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MusicRepositoryImplTest {

    private val trackDao: TrackDao = mockk(relaxed = true)
    private val downloadDao: app.vicsmusic.core.database.dao.DownloadDao = mockk(relaxed = true)
    private val lyricsDao: app.vicsmusic.core.database.dao.LyricsDao = mockk(relaxed = true)
    private val playHistoryDao: app.vicsmusic.core.database.dao.PlayHistoryDao = mockk(relaxed = true)
    private val searchHistoryDao: app.vicsmusic.core.database.dao.SearchHistoryDao = mockk(relaxed = true)
    private val lrclibClient: app.vicsmusic.core.network.LrclibClient = mockk(relaxed = true)
    private val extensionRegistryClient: app.vicsmusic.core.network.ExtensionRegistryClient = mockk(relaxed = true)
    private val declarativeExtensionManager: app.vicsmusic.source.remote.declarative.DeclarativeExtensionManager = mockk(relaxed = true)
    private val context: android.content.Context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
    private val sourceRegistry: SourceRegistry = SourceRegistry()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: MusicRepositoryImpl

    @Before
    fun setup() {
        repository = MusicRepositoryImpl(
            trackDao = trackDao,
            downloadDao = downloadDao,
            lyricsDao = lyricsDao,
            playHistoryDao = playHistoryDao,
            searchHistoryDao = searchHistoryDao,
            sourceRegistry = sourceRegistry,
            lrclibClient = lrclibClient,
            extensionRegistryClient = extensionRegistryClient,
            declarativeExtensionManager = declarativeExtensionManager,
            context = context,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun getAllTracksEmitsMappedDomainTracks() = runTest(testDispatcher) {
        val entity = TrackEntity(
            id = "t1",
            sourceId = "ytmusic",
            title = "Test Song",
            artistId = "a1",
            artistName = "Artist",
            albumId = null,
            albumName = null,
            durationMs = 210000L,
            artworkUrl = "https://example.com/art.jpg",
            isLiked = false,
            addedAt = 1000L,
            lastPlayedAt = null,
            playCount = 0
        )
        every { trackDao.getAllTracks() } returns flowOf(listOf(entity))

        val tracks = repository.getAllTracks().first()
        assertEquals(1, tracks.size)
        assertEquals("Test Song", tracks.first().title)
    }

    @Test
    fun toggleLikeFlipsBooleanAndUpdatesDao() = runTest(testDispatcher) {
        val entity = TrackEntity(
            id = "t1",
            sourceId = "local",
            title = "Test Song",
            artistId = "a1",
            artistName = "Artist",
            albumId = null,
            albumName = null,
            durationMs = 210000L,
            artworkUrl = null,
            isLiked = false,
            addedAt = 1000L,
            lastPlayedAt = null,
            playCount = 0
        )
        every { trackDao.getTrack("t1") } returns flowOf(entity)

        val newLiked = repository.toggleLike("t1")
        assertTrue(newLiked.isSuccess)
        assertTrue(newLiked.getOrThrow())
        coVerify(exactly = 1) { trackDao.setLiked("t1", true) }
    }

    @Test
    fun getStreamInfoDelegatesToRegisteredSource() = runTest(testDispatcher) {
        val mockSource: MusicSource = mockk()
        every { mockSource.id } returns "mock"
        coEvery { mockSource.getStream("mock_1", AudioQuality.HIGH) } returns Result.success(
            StreamInfo(trackId = "mock_1", url = "https://example.com/stream.mp3", bitrateKbps = 320)
        )
        sourceRegistry.register(mockSource)

        val entity = TrackEntity(
            id = "mock_1",
            sourceId = "mock",
            title = "Stream Test",
            artistId = "a1",
            artistName = "Artist",
            albumId = null,
            albumName = null,
            durationMs = 180000L,
            artworkUrl = null,
            isLiked = false,
            addedAt = 1000L,
            lastPlayedAt = null,
            playCount = 0
        )
        every { trackDao.getTrack("mock_1") } returns flowOf(entity)

        val stream = repository.getStreamInfo("mock_1", AudioQuality.HIGH)
        assertTrue(stream.isSuccess)
        assertEquals("https://example.com/stream.mp3", stream.getOrThrow().url)
    }

    @Test
    fun syncLocalTracksSavesAllToTrackDao() = runTest(testDispatcher) {
        val syncResult = repository.syncLocalTracks()
        assertTrue(syncResult.isSuccess)
        assertEquals(0, syncResult.getOrThrow())
    }

    @Test
    fun getStreamInfoResolvesYouTubeSourceIdByPrefix() = runTest(testDispatcher) {
        val mockYtSource: MusicSource = mockk()
        every { mockYtSource.id } returns app.vicsmusic.source.remote.YouTubeMusicSource.SOURCE_ID
        coEvery { mockYtSource.getStream("yt_dQw4w9WgXcQ", AudioQuality.HIGH) } returns Result.success(
            StreamInfo(trackId = "yt_dQw4w9WgXcQ", url = "https://googlevideo.com/stream", bitrateKbps = 160)
        )
        sourceRegistry.register(mockYtSource)

        every { trackDao.getTrack("yt_dQw4w9WgXcQ") } returns flowOf(null)

        val stream = repository.getStreamInfo("yt_dQw4w9WgXcQ", AudioQuality.HIGH)
        assertTrue(stream.isSuccess)
        assertEquals("https://googlevideo.com/stream", stream.getOrThrow().url)
    }
}
