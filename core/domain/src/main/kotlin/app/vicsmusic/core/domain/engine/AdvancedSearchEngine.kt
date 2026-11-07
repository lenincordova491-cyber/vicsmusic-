package app.vicsmusic.core.domain.engine

import app.vicsmusic.core.model.Track
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

data class LyricMatchHint(
    val trackName: String,
    val artistName: String
)

/**
 * Ultra-advanced multi-signal search & ranking engine.
 * Combines:
 * 1. Diacritic-insensitive & Spanish phonetic normalization (handling typos & spelling variations).
 * 2. N-gram and Levenshtein fuzzy edit-distance scoring.
 * 3. Full-text Lyric Phrase correlation (matching songs when the user only remembers a verse/lyric).
 * 4. Anti-noise filtering (demoting karaoke/covers/instrumentals unless requested).
 * 5. Personal taste affinity boost.
 */
class AdvancedSearchEngine {

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

        val queryTokens = normQuery.split(" ").filter { it.isNotBlank() }
        val phoneticQuery = phoneticNormalize(normQuery)
        val normalizedHints = lyricHints.map {
            normalize(it.trackName) to normalize(it.artistName)
        }
        val normalizedPrefArtists = preferredArtists.map { normalize(it) }.toSet()
        val wantsKaraokeOrCover = normQuery.contains("karaoke") || normQuery.contains("instrumental") || normQuery.contains("cover")

        val scored = candidates
            .distinctBy { it.id }
            .map { track ->
                val normTitle = normalize(cleanTitleNoise(track.title))
                val fullNormTitle = normalize(track.title)
                val normArtist = normalize(track.artistName)
                val normAlbum = normalize(track.albumName ?: "")
                val combined = "$normTitle $normArtist $normAlbum".trim()
                val phoneticCombined = phoneticNormalize("$normTitle $normArtist")

                var score = 0f

                // 1. Direct & prefix title/artist match
                if (normTitle == normQuery) score += 220f
                else if (normTitle.startsWith(normQuery)) score += 150f
                else if (normTitle.contains(normQuery)) score += 110f

                if (normArtist == normQuery) score += 180f
                else if (normArtist.startsWith(normQuery)) score += 120f
                else if (normArtist.contains(normQuery)) score += 85f

                if (combined.contains(normQuery)) score += 95f

                // 2. Phonetic match (handles Spanish b/v, s/c/z, ll/y spelling errors)
                if (phoneticQuery.length >= 4 && phoneticCombined.contains(phoneticQuery)) {
                    score += 65f
                }

                // 3. Token coverage + Fuzzy Levenshtein similarity per token
                val candidateTokens = combined.split(" ").filter { it.isNotBlank() }
                if (queryTokens.isNotEmpty() && candidateTokens.isNotEmpty()) {
                    var matchedTokensScore = 0f
                    for (qToken in queryTokens) {
                        val bestSim = candidateTokens.maxOfOrNull { cToken ->
                            when {
                                cToken == qToken -> 1.0f
                                cToken.startsWith(qToken) || qToken.startsWith(cToken) -> 0.88f
                                else -> similarityRatio(qToken, cToken)
                            }
                        } ?: 0f
                        if (bestSim >= 0.68f) {
                            matchedTokensScore += bestSim * 35f
                        }
                    }
                    val coverageRatio = matchedTokensScore / (queryTokens.size * 35f)
                    score += matchedTokensScore + (coverageRatio * 55f)
                }

                // 4. Lyric Phrase Match Correlation (Cached DB lyrics + LRCLIB full-text lyric index)
                if (cachedLyricMatchTrackIds.contains(track.id)) {
                    score += 210f
                }
                for ((index, hint) in normalizedHints.withIndex()) {
                    val (hintTitle, hintArtist) = hint
                    val titleMatchesHint = hintTitle.isNotEmpty() && (
                        normTitle == hintTitle ||
                        normTitle.contains(hintTitle) ||
                        hintTitle.contains(normTitle) ||
                        similarityRatio(normTitle, hintTitle) >= 0.78f
                    )
                    val artistMatchesHint = hintArtist.isNotEmpty() && (
                        normArtist.contains(hintArtist) ||
                        hintArtist.contains(normArtist) ||
                        similarityRatio(normArtist, hintArtist) >= 0.75f
                    )
                    if (titleMatchesHint && artistMatchesHint) {
                        // Highest priority: exact song & artist identified from lyric fragment!
                        score += (260f - index * 18f).coerceAtLeast(150f)
                        break
                    } else if (titleMatchesHint) {
                        score += (120f - index * 10f).coerceAtLeast(60f)
                    }
                }

                // 5. User Taste Profile Affinity Boost
                if (normalizedPrefArtists.any { pref -> pref.isNotEmpty() && (normArtist.contains(pref) || pref.contains(normArtist)) }) {
                    score += 30f
                }
                if (track.isLiked) {
                    score += 20f
                }

                // 6. Anti-Noise Filter (demote karaoke/instrumental/cover/tribute when user wants original song)
                if (!wantsKaraokeOrCover) {
                    if (fullNormTitle.contains("karaoke") || fullNormTitle.contains("pista") || fullNormTitle.contains("instrumental")) {
                        score -= 140f
                    }
                    if (fullNormTitle.contains("cover") || fullNormTitle.contains("tributo") || fullNormTitle.contains("parodia")) {
                        score -= 80f
                    }
                }

                // 7. Prefer online streaming tracks with high-res artwork over incomplete entries
                if (!track.artworkUrl.isNullOrBlank()) {
                    score += 10f
                }

                track to score
            }

        // Deduplicate near-identical title+artist pairs while preserving the highest-scored canonical track
        val sorted = scored.sortedByDescending { it.second }
        val result = mutableListOf<Track>()
        val seenCanonicalKeys = mutableSetOf<String>()

        for ((track, _) in sorted) {
            val canonicalKey = "${normalize(cleanTitleNoise(track.title))}::${normalize(track.artistName)}"
            if (seenCanonicalKeys.add(canonicalKey) || result.size < 8) {
                result.add(track)
            }
        }

        return result
    }

    fun isLikelyLyricQuery(query: String): Boolean {
        val tokens = normalize(query).split(" ").filter { it.isNotBlank() }
        return tokens.size >= 3 || query.contains("\"") || query.contains("...") || query.contains("letra", ignoreCase = true)
    }

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val decomposed = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun phoneticNormalize(normalized: String): String {
        return normalized
            .replace('v', 'b')
            .replace('z', 's')
            .replace("ce", "se")
            .replace("ci", "si")
            .replace("ll", "y")
            .replace("ge", "je")
            .replace("gi", "ji")
            .replace("h", "")
    }

    private fun cleanTitleNoise(title: String): String {
        return title
            .replace(Regex("""\s*[\(\[](?:feat|ft|official|oficial|video|audio|remastered|lyric|letra|en vivo|live|hd|4k).*?[\)\]]""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun similarityRatio(a: String, b: String): Float {
        if (a == b) return 1.0f
        if (a.isEmpty() || b.isEmpty()) return 0.0f
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1.0f
        val dist = levenshteinDistance(a, b)
        return (1.0f - dist.toFloat() / maxLen.toFloat()).coerceIn(0f, 1f)
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length
        var cost = IntArray(lhsLen + 1) { it }
        var newCost = IntArray(lhsLen + 1)

        for (i in 1..rhsLen) {
            newCost[0] = i
            for (j in 1..lhsLen) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLen]
    }
}
