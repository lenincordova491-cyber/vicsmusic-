package app.vicsmusic.core.domain.engine

import app.vicsmusic.core.model.Track
import kotlin.math.exp

data class ListeningEvent(
    val trackId: String,
    val artistName: String,
    val completionPercentage: Float, // 0.0 to 1.0
    val timestamp: Long
)

data class UserTasteProfile(
    val topArtists: List<String> = emptyList(),
    val dominantGenre: String = "Mix Personalizado",
    val tasteSummaryLabel: String = "Adaptado a tu gusto musical",
    val artistScores: Map<String, Float> = emptyMap(),
    val genreScores: Map<String, Float> = emptyMap()
)

data class RecommendationResult(
    val dailyMix: List<Track>,
    val recommendedForYou: List<Track>,
    val jumpBackIn: List<Track>,
    val topArtistAffinity: String?,
    val tasteProfile: UserTasteProfile = UserTasteProfile()
)

/**
 * Deep User Taste Analysis & Adaptive Recommendation Engine.
 * Evaluates completion ratios, skips, replays, exponential recency decay, explicit likes,
 * and genre/vibe clusters to personalize both the Home feed and the Playback Queue.
 */
class AdaptiveRecommendationEngine {

    companion object {
        private const val COMPLETION_THRESHOLD_LOVED = 0.85f
        private const val COMPLETION_THRESHOLD_POSITIVE = 0.60f
        private const val COMPLETION_THRESHOLD_SKIP = 0.22f

        private const val POINTS_LOVED_LISTEN = 4.5f
        private const val POINTS_FULL_LISTEN = 3.0f
        private const val POINTS_PARTIAL_LISTEN = 1.0f
        private const val POINTS_SKIP = -2.8f
        private const val POINTS_LIKED = 8.0f
        private const val POINTS_REPLAY_BONUS = 3.5f

        const val GENRE_CUMBIA = "Cumbia y Fiesta"
        const val GENRE_ROCK_POP = "Rock y Pop"
        const val GENRE_URBANO = "Urbano Latino"
        const val GENRE_SALSA = "Salsa y Tropical"
        const val GENRE_GLOBAL_POP = "Pop y Tendencias"
    }

    /**
     * Analyzes all user signals (history, completion rates, skips, replays, likes, onboarding artists)
     * to build a multi-dimensional UserTasteProfile.
     */
    fun analyzeUserTaste(
        allAvailableTracks: List<Track>,
        listeningHistory: List<ListeningEvent>,
        favoriteTrackIds: Set<String> = emptySet(),
        favoriteArtistNames: Set<String> = emptySet()
    ): UserTasteProfile {
        val now = System.currentTimeMillis()
        val artistScores = mutableMapOf<String, Float>()
        val genreScores = mutableMapOf<String, Float>()
        val trackById = allAvailableTracks.associateBy { it.id }

        // 1. Seed from explicit favorite artists
        for (rawArtist in favoriteArtistNames) {
            val clean = rawArtist.replace("_", " ").trim()
            if (clean.isNotEmpty()) {
                artistScores[clean] = (artistScores[clean] ?: 0f) + 18.0f
                val genre = inferGenreByArtist(clean)
                genreScores[genre] = (genreScores[genre] ?: 0f) + 12.0f
            }
        }

        // 2. Explicit liked tracks boost
        for (track in allAvailableTracks) {
            if (track.isLiked || favoriteTrackIds.contains(track.id)) {
                val artistKey = track.artistName.trim()
                artistScores[artistKey] = (artistScores[artistKey] ?: 0f) + POINTS_LIKED
                val genre = inferTrackGenre(track)
                genreScores[genre] = (genreScores[genre] ?: 0f) + 8.0f
            }
        }

        // 3. Behavioral history with exponential recency decay & replay detection
        val positiveCountsByTrack = mutableMapOf<String, Int>()
        for (event in listeningHistory) {
            val hoursAgo = ((now - event.timestamp).coerceAtLeast(0L) / 3_600_000.0).toFloat()
            // Half-life around 72 hours, with 1.4x boost for very recent listens
            val recencyWeight = (0.65f + 0.75f * exp(-hoursAgo / 72.0f)).coerceIn(0.65f, 1.4f)

            val baseDelta = when {
                event.completionPercentage >= COMPLETION_THRESHOLD_LOVED -> POINTS_LOVED_LISTEN
                event.completionPercentage >= COMPLETION_THRESHOLD_POSITIVE -> POINTS_FULL_LISTEN
                event.completionPercentage <= COMPLETION_THRESHOLD_SKIP -> POINTS_SKIP
                else -> POINTS_PARTIAL_LISTEN
            }

            var weightedDelta = baseDelta * recencyWeight
            if (event.completionPercentage >= COMPLETION_THRESHOLD_POSITIVE) {
                val count = (positiveCountsByTrack[event.trackId] ?: 0) + 1
                positiveCountsByTrack[event.trackId] = count
                if (count > 1) {
                    weightedDelta += POINTS_REPLAY_BONUS
                }
            }

            val artistKey = event.artistName.trim()
            if (artistKey.isNotEmpty() && !artistKey.equals("Artista", ignoreCase = true)) {
                artistScores[artistKey] = (artistScores[artistKey] ?: 0f) + weightedDelta
            }

            val track = trackById[event.trackId]
            val genre = if (track != null) inferTrackGenre(track) else inferGenreByArtist(artistKey)
            genreScores[genre] = (genreScores[genre] ?: 0f) + (weightedDelta * 0.8f)
        }

        val topArtists = artistScores.entries
            .filter { it.value > 0f }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(4)

        val dominantGenre = genreScores.entries
            .filter { it.value > 0f }
            .maxByOrNull { it.value }
            ?.key
            ?: GENRE_GLOBAL_POP

        val summaryLabel = when {
            topArtists.size >= 2 -> "Tu estilo: ${topArtists.take(2).joinToString(", ")} • $dominantGenre"
            topArtists.size == 1 -> "Afinidad principal: ${topArtists.first()} • $dominantGenre"
            else -> " Algoritmo adaptativo • $dominantGenre"
        }.trim()

        return UserTasteProfile(
            topArtists = topArtists,
            dominantGenre = dominantGenre,
            tasteSummaryLabel = summaryLabel,
            artistScores = artistScores,
            genreScores = genreScores
        )
    }

    /**
     * Scores and clusters tracks into personalized recommendation groups for the Home screen.
     */
    fun computeRecommendations(
        allAvailableTracks: List<Track>,
        listeningHistory: List<ListeningEvent>,
        favoriteTrackIds: Set<String> = emptySet(),
        favoriteArtistNames: Set<String> = emptySet(),
        maxPerSection: Int = 12
    ): RecommendationResult {
        if (allAvailableTracks.isEmpty()) {
            return RecommendationResult(
                dailyMix = emptyList(),
                recommendedForYou = emptyList(),
                jumpBackIn = emptyList(),
                topArtistAffinity = null
            )
        }

        val tasteProfile = analyzeUserTaste(
            allAvailableTracks = allAvailableTracks,
            listeningHistory = listeningHistory,
            favoriteTrackIds = favoriteTrackIds,
            favoriteArtistNames = favoriteArtistNames
        )

        val topArtist = tasteProfile.topArtists.firstOrNull()

        // Score every track using artist affinity + genre affinity + likes + playCount
        val scoredTracks = allAvailableTracks.map { track ->
            var score = 0f

            val artistScore = resolveArtistScore(track, tasteProfile.artistScores)
            score += artistScore * 2.2f

            val genre = inferTrackGenre(track)
            val genreScore = tasteProfile.genreScores[genre] ?: 0f
            score += genreScore * 1.1f

            val isFavoriteArtist = favoriteArtistNames.any { fav ->
                matchesArtist(track, fav)
            }
            if (isFavoriteArtist) {
                score += 14.0f
            }

            if (track.isLiked || favoriteTrackIds.contains(track.id)) {
                score += POINTS_LIKED
            }

            score += (track.playCount.coerceAtMost(20)) * 0.6f

            track to score
        }

        val completedTrackIds = listeningHistory
            .filter { it.completionPercentage >= COMPLETION_THRESHOLD_POSITIVE }
            .map { it.trackId }
            .toSet()

        val jumpBackIn = allAvailableTracks
            .filter { completedTrackIds.contains(it.id) }
            .take(maxPerSection)

        // Daily Mix: Balanced top-scored tracks with artist diversity (max 2 per artist in top 10)
        val sortedByScore = scoredTracks.sortedByDescending { it.second }
        val dailyMix = selectWithDiversity(sortedByScore.map { it.first }, maxPerArtist = 2, limit = maxPerSection)

        // Recommended For You: Discovery-oriented tracks aligned with user's top genres & artists
        val discoveryCandidates = sortedByScore
            .filter { (track, _) -> !completedTrackIds.contains(track.id) }
            .map { it.first }
        val recommendedForYou = selectWithDiversity(
            candidates = discoveryCandidates.ifEmpty { sortedByScore.map { it.first } },
            maxPerArtist = 2,
            limit = maxPerSection
        )

        return RecommendationResult(
            dailyMix = if (dailyMix.isNotEmpty()) dailyMix else allAvailableTracks.take(maxPerSection),
            recommendedForYou = recommendedForYou,
            jumpBackIn = jumpBackIn,
            topArtistAffinity = topArtist,
            tasteProfile = tasteProfile
        )
    }

    /**
     * Computes an intelligent, Spotify-style Autoplay queue adapted to BOTH the current track's
     * sonic/genre vibe AND the user's learned taste profile, preventing repetition and artist fatigue.
     */
    fun computeAutoplayQueue(
        currentTrack: Track,
        allAvailableTracks: List<Track>,
        listeningHistory: List<ListeningEvent> = emptyList(),
        favoriteArtistNames: Set<String> = emptySet(),
        favoriteTrackIds: Set<String> = emptySet(),
        excludedTrackIds: Set<String> = emptySet(),
        count: Int = 10
    ): List<Track> {
        if (allAvailableTracks.isEmpty()) return emptyList()

        val tasteProfile = analyzeUserTaste(
            allAvailableTracks = allAvailableTracks,
            listeningHistory = listeningHistory,
            favoriteTrackIds = favoriteTrackIds,
            favoriteArtistNames = favoriteArtistNames
        )

        val currentGenre = inferTrackGenre(currentTrack)
        val excluded = excludedTrackIds + currentTrack.id

        val scored = allAvailableTracks
            .filter { !excluded.contains(it.id) }
            .map { track ->
                var score = 0f

                // 1. Contextual continuity: same genre/vibe cluster as current track
                val candidateGenre = inferTrackGenre(track)
                if (candidateGenre == currentGenre) {
                    score += 28.0f
                }

                // 2. Context similarity: same artist
                val isSameArtist = track.artistId.equals(currentTrack.artistId, ignoreCase = true) ||
                        track.artistName.equals(currentTrack.artistName, ignoreCase = true)
                if (isSameArtist) {
                    score += 32.0f
                }

                // 3. Context similarity: same album
                if (!track.albumName.isNullOrBlank() && track.albumName.equals(currentTrack.albumName, ignoreCase = true) && !track.albumName.equals("Hits", ignoreCase = true)) {
                    score += 12.0f
                }

                // 4. User Taste Profile: learned artist score + genre affinity
                val artistScore = resolveArtistScore(track, tasteProfile.artistScores)
                score += artistScore * 2.0f

                val genreAffinity = tasteProfile.genreScores[candidateGenre] ?: 0f
                score += genreAffinity * 0.9f

                // 5. Explicit favorite artist match
                if (favoriteArtistNames.any { fav -> matchesArtist(track, fav) }) {
                    score += 18.0f
                }

                // 6. Liked track bonus
                if (track.isLiked || favoriteTrackIds.contains(track.id)) {
                    score += 12.0f
                }

                if (!track.artworkUrl.isNullOrBlank()) {
                    score += 5.0f
                }

                track to score
            }

        val sorted = scored.sortedByDescending { it.second }.map { it.first }
        return selectWithDiversity(sorted, maxPerArtist = 2, limit = count)
    }

    private fun selectWithDiversity(
        candidates: List<Track>,
        maxPerArtist: Int,
        limit: Int
    ): List<Track> {
        val result = mutableListOf<Track>()
        val artistCounts = mutableMapOf<String, Int>()

        for (track in candidates) {
            val key = track.artistName.lowercase().trim()
            val currentCount = artistCounts.getOrDefault(key, 0)
            if (currentCount < maxPerArtist) {
                result.add(track)
                artistCounts[key] = currentCount + 1
            }
            if (result.size >= limit) break
        }

        if (result.size < limit) {
            for (track in candidates) {
                if (result.none { it.id == track.id }) {
                    result.add(track)
                }
                if (result.size >= limit) break
            }
        }
        return result
    }

    private fun resolveArtistScore(track: Track, artistScores: Map<String, Float>): Float {
        artistScores[track.artistName]?.let { return it }
        artistScores[track.artistId]?.let { return it }
        return artistScores.entries.find { (key, _) ->
            matchesArtist(track, key)
        }?.value ?: 0f
    }

    private fun matchesArtist(track: Track, candidate: String): Boolean {
        val cleanCandidate = candidate.replace("_", " ").trim()
        return track.artistId.equals(candidate, ignoreCase = true) ||
                track.artistName.equals(cleanCandidate, ignoreCase = true) ||
                track.artistName.contains(cleanCandidate, ignoreCase = true) ||
                cleanCandidate.contains(track.artistName, ignoreCase = true)
    }

    fun inferTrackGenre(track: Track): String {
        val album = track.albumName.orEmpty().lowercase()
        return when {
            album.contains("cumbia") -> GENRE_CUMBIA
            album.contains("salsa") -> GENRE_SALSA
            album.contains("rock") -> GENRE_ROCK_POP
            album.contains("urbano") || album.contains("reggaeton") -> GENRE_URBANO
            else -> inferGenreByArtist("${track.artistName} ${track.artistId}")
        }
    }

    private fun inferGenreByArtist(artistSignal: String): String {
        val s = artistSignal.lowercase()
        return when {
            listOf("grupo 5", "grupo_5", "agua marina", "armonia 10", "armonia_10", "corazon serrano", "ke personajes", "angeles azules", "destellos", "hijos del sol", "chacal", "maroyu", "amar azul", "rafaga", "cumbia").any { s.contains(it) } -> GENRE_CUMBIA
            listOf("marc anthony", "oscar d", "ruben blades", "grupo niche", "gilberto santa rosa", "hector lavoe", "willie colon", "frankie ruiz", "eddie santiago", "salsa").any { s.contains(it) } -> GENRE_SALSA
            listOf("soda stereo", "queen", "coldplay", "enanitos verdes", "mana", "maná", "prisioneros", "caifanes", "jarabe de palo", "mojarras", "hombres g", "libido", "amen", "mar de copas", "imagine dragons", "rock").any { s.contains(it) } -> GENRE_ROCK_POP
            listOf("bad bunny", "karol g", "feid", "bizarrap", "manuel turizo", "quevedo", "rauw", "rosalia", "maluma", "daddy yankee", "don omar", "ozuna", "j balvin", "myke towers").any { s.contains(it) } -> GENRE_URBANO
            else -> GENRE_GLOBAL_POP
        }
    }
}
