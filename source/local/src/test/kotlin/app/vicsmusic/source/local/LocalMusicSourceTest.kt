package app.vicsmusic.source.local

import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import app.vicsmusic.core.model.AudioQuality
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.fakes.RoboCursor

@RunWith(RobolectricTestRunner::class)
class LocalMusicSourceTest {

    private lateinit var context: Context
    private lateinit var localMusicSource: LocalMusicSource

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        localMusicSource = LocalMusicSource(context)

        val cursor = RoboCursor().apply {
            setColumnNames(
                listOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ARTIST_ID,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.IS_MUSIC,
                    MediaStore.Audio.Media.DATE_ADDED
                )
            )
            setResults(
                arrayOf(
                    arrayOf(
                        12345L,
                        "Strobe",
                        "deadmau5",
                        "deadmau5_id",
                        "For Lack of a Better Name",
                        678L,
                        637000L,
                        1,
                        1600000000L
                    )
                )
            )
        }
        shadowOf(context.contentResolver).setCursor(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor)
    }

    @Test
    fun getAllTracksReturnsInsertedMedia() = runTest {
        val result = localMusicSource.getAllTracks()
        assertTrue(result.isSuccess)
        val tracks = result.getOrThrow()
        assertTrue("Tracks was not empty", tracks.isNotEmpty())

        val strobe = tracks.firstOrNull { it.title == "Strobe" }
        assertNotNull(strobe)
        assertEquals("deadmau5", strobe?.artistName)
        assertEquals("local_12345", strobe?.id)
        assertEquals(637000L, strobe?.durationMs)
    }

    @Test
    fun getStreamResolvesValidContentUri() = runTest {
        val streamResult = localMusicSource.getStream("local_12345", AudioQuality.HIGH)
        assertTrue(streamResult.isSuccess)
        val stream = streamResult.getOrThrow()
        assertEquals("local_12345", stream.trackId)
        assertTrue(stream.url.contains("12345"))
    }
}
