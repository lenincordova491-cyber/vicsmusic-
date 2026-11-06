package app.vicsmusic.core.domain.engine

import app.vicsmusic.core.model.Track

data class ListeningEvent(
    val trackId: String,
    val artistName: String,
    val completionPercentage: Float, // 0.0 to 1.0
    val timestamp: Long
)

data class RecommendationResult(
    val dailyMix: List<Track>,
    val recommendedForYou: List<Track>,
    val jumpBackIn: List<Track>,
    val topArtistAffinity: String?
)

/**
 * Adaptive recommendation engine that personalizes the user's music catalog
 * based on listening history, completion rates, skips, and artist affinities.
 */
class AdaptiveRecommendationEngine {

    companion object {
        private const val COMPLETION_THRESHOLD_POSITIVE = 0.65f
        private const val COMPLETION_THRESHOLD_SKIP = 0.20f
        private const val POINTS_FULL_LISTEN = 3.0f
        private const val POINTS_PARTIAL_LISTEN = 1.0f
        private const val POINTS_SKIP = -2.0f
        private const val POINTS_LIKED = 5.0f
    }

    /**
     * Scores and clusters tracks into personalized recommendation groups.
     */
    fun computeRecommendations(
        allAvailableTracks: List<Track>,
        listeningHistory: List<ListeningEvent>,
        favoriteTrackIds: Set<String> = emptySet(),
        maxPerSection: Int = 10
    ): RecommendationResult {
        if (allAvailableTracks.isEmpty()) {
            return RecommendationResult(
                dailyMix = emptyList(),
                recommendedForYou = emptyList(),
                jumpBackIn = emptyList(),
                topArtistAffinity = null
            )
        }

        // 1. Calculate artist affinity scores
        val artistScores = mutableMapOf<String, Float>()
        for (event in listeningHistory) {
            val scoreDelta = when {
                event.completionPercentage >= COMPLETION_THRESHOLD_POSITIVE -> POINTS_FULL_LISTEN
                event.completionPercentage <= COMPLETION_THRESHOLD_SKIP -> POINTS_SKIP
                else -> POINTS_PARTIAL_LISTEN
            }
            val current = artistScores.getOrDefault(event.artistName, 0f)
            artistScores[event.artistName] = current + scoreDelta
        }

        // Find top artist
        val topArtist = artistScores.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key

        // 2. Score every track
        val scoredTracks = allAvailableTracks.map { track ->
            var score = 0f

            // Artist affinity
            val artistScore = artistScores[track.artistName] ?: 0f
            score += artistScore * 2.0f

            // Favorite bonus
            if (track.isLiked || favoriteTrackIds.contains(track.id)) {
                score += POINTS_LIKED
            }

            // Play count bonus
            score += (track.playCount.coerceAtMost(20)) * 0.5f

            track to score
        }

        // 3. Jump Back In: Recently completed tracks
        val completedTrackIds = listeningHistory
            .filter { it.completionPercentage >= COMPLETION_THRESHOLD_POSITIVE }
            .map { it.trackId }
            .toSet()

        val jumpBackIn = allAvailableTracks
            .filter { completedTrackIds.contains(it.id) }
            .take(maxPerSection)

        // 4. Daily Mix: Highly rated and affinity-aligned tracks
        val dailyMix = scoredTracks
            .sortedByDescending { it.second }
            .map { it.first }
            .take(maxPerSection)

        // 5. Recommended For You: Balanced mix of familiar and discoverable songs
        val recommendedForYou = scoredTracks
            .filter { (track, _) -> !completedTrackIds.contains(track.id) }
            .sortedByDescending { it.second }
            .map { it.first }
            .ifEmpty { allAvailableTracks.shuffled() }
            .take(maxPerSection)

        return RecommendationResult(
            dailyMix = if (dailyMix.isNotEmpty()) dailyMix else allAvailableTracks.take(maxPerSection),
            recommendedForYou = recommendedForYou,
            jumpBackIn = jumpBackIn,
            topArtistAffinity = topArtist
        )
    }
}
