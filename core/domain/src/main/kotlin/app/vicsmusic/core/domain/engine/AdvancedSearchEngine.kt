package app.vicsmusic.core.domain.engine

import app.vicsmusic.core.model.Track
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class LyricMatchHint(
    val trackName: String,
    val artistName: String
)

/**
 * Ultra-advanced multi-signal search & ranking engine V2.
 * - Zero Regex allocations inside loops (precompiled companion regexes).
 * - Non-destructive Spanish phonetic secondary bonus (preserves English titles like "The Hills", "Chill").
 * - Single-row O(min(N,M)) Levenshtein similarity with early length pruning.
 * - Preserves Live/Remix/Acoustic variants when requested in the query.
 */
class AdvancedSearchEngine {

    companion object {
        private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
        private val NON_ALPHANUM_REGEX = Regex("[^a-z0-9\\s]")
        private val MULTI_SPACE_REGEX = Regex("\\s+")
        private val NOISE_TAGS_REGEX = Regex(
            "\\s*[\\(\\[](?:feat|ft|official|oficial|video|audio|remastered|lyric|letra|hd|4k).*?[\\)\\]]",
            RegexOption.IGNORE_CASE
        )
        private val LIVE_OR_REMIX_KEYWORDS = listOf(
            "en vivo", "live", "remix", "acustico", "acoustic", "unplugged", "karaoke", "instrumental"
        )
    }

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val lower = input.lowercase(Locale.ROOT)
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val withoutAccents = DIACRITICS_REGEX.replace(decomposed, "")
        val alphaNum = NON_ALPHANUM_REGEX.replace(withoutAccents, " ")
        return MULTI_SPACE_REGEX.replace(alphaNum, " ").trim()
    }

    fun cleanTitleNoise(title: String, preserveLiveAndRemix: Boolean = false): String {
        val cleaned = NOISE_TAGS_REGEX.replace(title, "").trim()
        return if (preserveLiveAndRemix) title.trim() else cleaned
    }

    fun isLikelyLyricQuery(query: String): Boolean {
        val tokens = normalize(query).split(' ').filter { it.isNotBlank() }
        return tokens.size >= 3 || query.contains("\"") || query.contains("...") || query.contains("letra", ignoreCase = true)
    }

    private fun spanishPhoneticCode(normalized: String): String =
        normalized
            .replace('v', 'b')
            .replace('z', 's')
            .replace("ce", "se")
            .replace("ci", "si")
            .replace("ll", "y")
            .replace("ge", "je")
            .replace("gi", "ji")
            .replace("h", "")

    fun rankSearchResults(
        rawQuery: String,
        candidates: List<Track>,
        lyricHints: List<LyricMatchHint> = emptyList(),
        cachedLyricMatchTrackIds: Set<String> = emptySet(),
        preferredArtists: Set<String> = emptySet()
    ): List<Track> {
        if (candidates.isEmpty()) return emptyList()
        val normQuery = normalize(rawQuery)
        if (normQuery.isEmpty()) return candidates.distinctBy { it.id }

        val queryTokens = normQuery.split(' ').filter { it.isNotBlank() }
        val phoneticQuery = spanishPhoneticCode(normQuery)
        val userWantsSpecialVersion = LIVE_OR_REMIX_KEYWORDS.any { normQuery.contains(it) }
        val normPreferredArtists = preferredArtists.mapTo(HashSet()) { normalize(it) }
        val normLyricHints = lyricHints.map { normalize(it.trackName) to normalize(it.artistName) }

        val scored = candidates.distinctBy { it.id }.map { track ->
            val normTitle = normalize(cleanTitleNoise(track.title, userWantsSpecialVersion))
            val rawNormTitle = normalize(track.title)
            val normArtist = normalize(track.artistName)
            val normAlbum = normalize(track.albumName.orEmpty())
            val fullText = "$normTitle $normArtist $normAlbum".trim()

            var score = 0f

            // 1. Exact or prefix match on title and artist
            when {
                normTitle == normQuery -> score += 120f
                normTitle.startsWith(normQuery) -> score += 85f
                normTitle.contains(normQuery) -> score += 60f
            }
            when {
                normArtist == normQuery -> score += 95f
                normArtist.startsWith(normQuery) -> score += 70f
                normArtist.contains(normQuery) -> score += 45f
            }
            if ("$normTitle $normArtist" == normQuery || "$normArtist $normTitle" == normQuery) {
                score += 140f
            }

            // 2. Token coverage (BM25-lite + fast bounded Levenshtein)
            val candidateTokens = fullText.split(' ').filter { it.isNotBlank() }
            if (queryTokens.isNotEmpty() && candidateTokens.isNotEmpty()) {
                var matchedTokens = 0f
                for (qToken in queryTokens) {
                    val bestTokenSim = candidateTokens.maxOf { cToken ->
                        when {
                            cToken == qToken -> 1.0f
                            cToken.startsWith(qToken) || qToken.startsWith(cToken) -> 0.88f
                            else -> boundedSimilarity(qToken, cToken)
                        }
                    }
                    if (bestTokenSim >= 0.72f) matchedTokens += bestTokenSim
                }
                val coverage = matchedTokens / queryTokens.size
                score += coverage * 75f
            }

            // 3. Secondary Spanish phonetic bonus
            if (phoneticQuery.length >= 4) {
                val phoneticCandidate = spanishPhoneticCode("$normTitle $normArtist")
                if (phoneticCandidate.contains(phoneticQuery)) {
                    score += 28f
                }
            }

            // 4. Lyric phrase match correlation (LRCLIB + cached Room lyrics)
            if (track.id in cachedLyricMatchTrackIds) score += 90f
            for ((hintTrack, hintArtist) in normLyricHints) {
                if (hintTrack.isNotEmpty() && normTitle.contains(hintTrack)) {
                    score += if (hintArtist.isNotEmpty() && normArtist.contains(hintArtist)) 85f else 45f
                }
            }

            // 5. Quality & user taste signals
            if (normArtist in normPreferredArtists) score += 18f
            if (track.isLiked) score += 12f
            if (!track.artworkUrl.isNullOrBlank()) score += 6f
            if (track.durationMs in 60_000L..600_000L) score += 5f

            if (!userWantsSpecialVersion) {
                if (rawNormTitle.contains("karaoke") || rawNormTitle.contains("tributo") || rawNormTitle.contains("parodia")) {
                    score -= 55f
                }
            }

            track to score
        }.sortedByDescending { it.second }

        // Smart deduplication: preserve Live/Remix variants if user searched for them
        val seenKeys = HashSet<String>()
        val finalResults = ArrayList<Track>(scored.size)
        for ((track, _) in scored) {
            val titleKey = if (userWantsSpecialVersion) {
                normalize(track.title)
            } else {
                normalize(cleanTitleNoise(track.title))
            }
            val dedupKey = "$titleKey::${normalize(track.artistName)}"
            if (seenKeys.add(dedupKey)) {
                finalResults.add(track)
            }
        }
        return finalResults
    }

    private fun boundedSimilarity(a: String, b: String): Float {
        if (a == b) return 1f
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1f
        val lenDiff = abs(a.length - b.length)
        if (lenDiff.toFloat() / maxLen > 0.45f) return 0f

        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            System.arraycopy(curr, 0, prev, 0, b.length + 1)
        }
        return (1f - (prev[b.length].toFloat() / maxLen)).coerceIn(0f, 1f)
    }
}
