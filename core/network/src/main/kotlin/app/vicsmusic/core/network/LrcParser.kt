package app.vicsmusic.core.network

import app.vicsmusic.core.model.LyricLine
import app.vicsmusic.core.model.Lyrics

/**
 * Enhanced LRC parser supporting:
 * - [mm:ss.xx], [mm:ss.xxx], and [mm:ss:xx]
 * - Global [offset:+/-ms] tags
 * - Multiple timestamps on a single line (e.g., [00:15.20][01:15.20]Chorus)
 * - Smooth time-interpolated fallback for plain lyrics
 */
object LrcParser {

    private val TIMESTAMP_REGEX = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val OFFSET_REGEX = Regex("\\[offset:\\s*([+-]?\\d+)\\s*]", RegexOption.IGNORE_CASE)

    fun parse(
        trackId: String,
        lrcContent: String?,
        plainLyrics: String? = null,
        source: String = "lrclib",
        durationMs: Long = 210_000L
    ): Lyrics {
        val validLrc = lrcContent?.takeIf { it.isNotBlank() && it != "null" }
        if (validLrc != null) {
            val lines = parseLrcLines(validLrc)
            if (lines.isNotEmpty()) {
                return Lyrics(
                    trackId = trackId,
                    lines = lines,
                    isSynced = true,
                    source = source
                )
            }
        }

        val fallbackPlain = plainLyrics?.takeIf { it.isNotBlank() && it != "null" } ?: validLrc
        if (!fallbackPlain.isNullOrBlank()) {
            val rawLines = fallbackPlain.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("[") }
                .toList()
            if (rawLines.isNotEmpty()) {
                val effectiveDuration = if (durationMs > 15_000L) durationMs else 210_000L
                val usableWindow = (effectiveDuration * 0.88).toLong().coerceAtLeast(30_000L)
                val stepMs = (usableWindow / rawLines.size.coerceAtLeast(1)).coerceAtLeast(2_500L)
                val lines = rawLines.mapIndexed { idx, line ->
                    LyricLine(timeMs = 4_000L + idx * stepMs, text = line)
                }
                return Lyrics(
                    trackId = trackId,
                    lines = lines,
                    isSynced = false,
                    source = source
                )
            }
        }

        return Lyrics(
            trackId = trackId,
            lines = emptyList(),
            isSynced = false,
            source = source
        )
    }

    fun parseLrcLines(rawLrc: String): List<LyricLine> {
        if (rawLrc.isBlank()) return emptyList()
        val globalOffsetMs = OFFSET_REGEX.find(rawLrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val parsedLines = mutableListOf<LyricLine>()

        for (rawLine in rawLrc.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            val matches = TIMESTAMP_REGEX.findAll(trimmed).toList()
            if (matches.isEmpty()) continue

            val textStart = matches.last().range.last + 1
            val lyricText = trimmed.substring(textStart).trim()
            if (lyricText.isEmpty()) continue

            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fractionStr = match.groupValues[3]
                val millis = when (fractionStr.length) {
                    1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
                    3 -> fractionStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val timeMs = ((minutes * 60_000L) + (seconds * 1_000L) + millis - globalOffsetMs)
                    .coerceAtLeast(0L)
                parsedLines.add(LyricLine(timeMs = timeMs, text = lyricText))
            }
        }
        return parsedLines.sortedBy { it.timeMs }
    }
}
