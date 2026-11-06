package app.vicsmusic.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun parse_validTimestampedLrc_ordersLinesCorrectly() {
        val lrc = """
            [00:12.50] Line 1 Intro
            [00:18.20] Line 2 First Verse
            [01:05.80] Line 3 Chorus starts here
        """.trimIndent()

        val lyrics = LrcParser.parse(trackId = "track_1", lrcContent = lrc)

        assertTrue(lyrics.isSynced)
        assertEquals(3, lyrics.lines.size)
        assertEquals(12500L, lyrics.lines[0].timeMs)
        assertEquals("Line 1 Intro", lyrics.lines[0].text)
        assertEquals(18200L, lyrics.lines[1].timeMs)
        assertEquals("Line 2 First Verse", lyrics.lines[1].text)
        assertEquals(65800L, lyrics.lines[2].timeMs)
        assertEquals("Line 3 Chorus starts here", lyrics.lines[2].text)
    }

    @Test
    fun parse_millisWithThreeDigits_parsesAccurately() {
        val lrc = """
            [02:01.350] Three digit millisecond line
        """.trimIndent()

        val lyrics = LrcParser.parse(trackId = "track_2", lrcContent = lrc)

        assertTrue(lyrics.isSynced)
        assertEquals(1, lyrics.lines.size)
        assertEquals(121350L, lyrics.lines[0].timeMs)
        assertEquals("Three digit millisecond line", lyrics.lines[0].text)
    }

    @Test
    fun parse_plainTextFallback_whenLrcIsEmpty() {
        val plain = """
            First plain line
            Second plain line
        """.trimIndent()

        val lyrics = LrcParser.parse(trackId = "track_3", lrcContent = null, plainLyrics = plain)

        assertFalse(lyrics.isSynced)
        assertEquals(2, lyrics.lines.size)
        assertEquals("First plain line", lyrics.lines[0].text)
        assertEquals("Second plain line", lyrics.lines[1].text)
    }

    @Test
    fun parse_instrumentalResponse_detected() {
        val json = """
            {
                "id": 999,
                "trackName": "Orion",
                "artistName": "Metallica",
                "instrumental": true
            }
        """.trimIndent()

        val client = LrclibClient()
        val lyrics = client.parseLrclibResponse("track_4", json)

        assertNotNull(lyrics)
        assertTrue(lyrics!!.isSynced)
        assertEquals("♪ Instrumental ♪", lyrics.lines[0].text)
    }
}
