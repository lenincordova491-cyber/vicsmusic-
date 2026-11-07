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
        favoriteArtistNames: Set<String> = emptySet(),
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
        
        // Initial affinity boost from user-chosen artists (+15.0f)
        for (artist in favoriteArtistNames) {
            artistScores[artist] = 15.0f
        }

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
            val artistScore = artistScores[track.artistName]
                ?: artistScores[track.artistId]
                ?: artistScores.entries.find {
                    it.key.equals(track.artistName, ignoreCase = true) ||
                    it.key.replace("_", " ").equals(track.artistName, ignoreCase = true) ||
                    track.artistName.replace(" ", "_").equals(it.key, ignoreCase = true)
                }?.value
                ?: 0f
            score += artistScore * 2.0f

            // Direct favorite artist match
            val isFavoriteArtist = favoriteArtistNames.any { fav ->
                track.artistId.equals(fav, ignoreCase = true) ||
                track.artistName.equals(fav, ignoreCase = true) ||
                track.artistName.replace(" ", "_").equals(fav, ignoreCase = true) ||
                fav.replace("_", " ").equals(track.artistName, ignoreCase = true)
            }
            if (isFavoriteArtist) {
                score += 10.0f
            }

            // Favorite track bonus
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

    /**
     * Computes an intelligent, Spotify-style Autoplay queue adapted to the current track
     * and the user's historical affinities, preventing repetition and artist fatigue.
     */
    fun computeAutoplayQueue(
        currentTrack: Track,
        allAvailableTracks: List<Track>,
        listeningHistory: List<ListeningEvent> = emptyList(),
        favoriteArtistNames: Set<String> = emptySet(),
        favoriteTrackIds: Set<String> = emptySet(),
        excludedTrackIds: Set<String> = emptySet(),
        count: Int = 8
    ): List<Track> {
        if (allAvailableTracks.isEmpty()) return emptyList()

        // 1. Calculate historical artist affinity & penalties (e.g. skips)
        val artistScores = mutableMapOf<String, Float>()
        for (artist in favoriteArtistNames) {
            artistScores[artist] = 15.0f
        }
        for (event in listeningHistory) {
            val delta = when {
                event.completionPercentage >= COMPLETION_THRESHOLD_POSITIVE -> POINTS_FULL_LISTEN
                event.completionPercentage <= COMPLETION_THRESHOLD_SKIP -> POINTS_SKIP
                else -> POINTS_PARTIAL_LISTEN
            }
            val cur = artistScores.getOrDefault(event.artistName, 0f)
            artistScores[event.artistName] = cur + delta
        }

        val excluded = excludedTrackIds + currentTrack.id

        // 2. Score candidate tracks
        val scored = allAvailableTracks
            .filter { !excluded.contains(it.id) }
            .map { track ->
                var score = 0f

                // Context similarity: same artist
                val isSameArtist = track.artistId.equals(currentTrack.artistId, ignoreCase = true) ||
                        track.artistName.equals(currentTrack.artistName, ignoreCase = true)
                if (isSameArtist) {
                    score += 35.0f
                }

                // Context similarity: same album
                if (track.albumName != null && track.albumName.equals(currentTrack.albumName, ignoreCase = true)) {
                    score += 15.0f
                }

                // User profile: favorite artist match
                val isFavArtist = favoriteArtistNames.any { fav ->
                    track.artistId.equals(fav, ignoreCase = true) ||
                    track.artistName.equals(fav, ignoreCase = true) ||
                    fav.replace("_", " ").equals(track.artistName, ignoreCase = true)
                }
                if (isFavArtist) {
                    score += 20.0f
                }

                // Learned artist score (completion rate vs skip penalty)
                val artistScore = artistScores[track.artistName] ?: artistScores[track.artistId] ?: 0f
                score += artistScore * 2.0f

                // Liked track bonus
                if (track.isLiked || favoriteTrackIds.contains(track.id)) {
                    score += 10.0f
                }

                // Source affinity: prefer tracks with valid artwork
                if (!track.artworkUrl.isNullOrBlank()) {
                    score += 5.0f
                }

                track to score
            }

        // 3. Selection with artist diversity (max 2 songs per artist in the autoplay batch)
        val sorted = scored.sortedByDescending { it.second }
        val result = mutableListOf<Track>()
        val artistCounts = mutableMapOf<String, Int>()

        for ((track, _) in sorted) {
            val key = track.artistName.lowercase()
            val currentCount = artistCounts.getOrDefault(key, 0)
            if (currentCount < 2) {
                result.add(track)
                artistCounts[key] = currentCount + 1
            }
            if (result.size >= count) break
        }

        // If strict diversity didn't fill the quota, fill with remaining highest scored
        if (result.size < count) {
            for ((track, _) in sorted) {
                if (!result.any { it.id == track.id }) {
                    result.add(track)
                }
                if (result.size >= count) break
            }
        }

        return result
    }
}
