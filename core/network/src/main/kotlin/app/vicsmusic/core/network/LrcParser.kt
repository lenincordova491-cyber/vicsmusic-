package app.vicsmusic.core.network

import app.vicsmusic.core.model.LyricLine
import app.vicsmusic.core.model.Lyrics

/**
 * Standard parser for LRC (Lyric) format strings containing timestamped lines:
 * e.g., `[01:23.45] Some lyrics line` or `[01:23.456] Some lyrics line`
 */
object LrcParser {

    private val LRC_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\](.*)""")

    fun parse(trackId: String, lrcContent: String?, plainLyrics: String? = null, source: String = "lrclib"): Lyrics {
        if (!lrcContent.isNullOrBlank()) {
            val lines = parseLrcLines(lrcContent)
            if (lines.isNotEmpty()) {
                return Lyrics(
                    trackId = trackId,
                    lines = lines,
                    isSynced = true,
                    source = source
                )
            }
        }

        if (!plainLyrics.isNullOrBlank()) {
            val lines = plainLyrics.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapIndexed { index, line -> LyricLine(timeMs = index * 4000L, text = line) }
                .toList()
            return Lyrics(
                trackId = trackId,
                lines = lines,
                isSynced = false,
                source = source
            )
        }

        return Lyrics(
            trackId = trackId,
            lines = emptyList(),
            isSynced = false,
            source = source
        )
    }

    private fun parseLrcLines(lrc: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        for (rawLine in lrc.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            val match = LRC_REGEX.matchEntire(trimmed) ?: continue
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fractionStr = match.groupValues[3]
            val millis = when (fractionStr.length) {
                0 -> 0L
                1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
                2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
                else -> (fractionStr.take(3).toLongOrNull() ?: 0L)
            }
            val text = match.groupValues[4].trim()
            val timeMs = (minutes * 60 + seconds) * 1000L + millis
            result.add(LyricLine(timeMs = timeMs, text = text))
        }
        return result.sortedBy { it.timeMs }
    }
}
