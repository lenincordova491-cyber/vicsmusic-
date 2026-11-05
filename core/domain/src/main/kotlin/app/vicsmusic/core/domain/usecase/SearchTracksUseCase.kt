package app.vicsmusic.core.domain.usecase

import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.model.Track

class SearchTracksUseCase(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(query: String): Result<List<Track>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        return musicRepository.searchTracks(trimmed)
    }
}
