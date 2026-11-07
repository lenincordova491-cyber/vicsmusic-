package app.vicsmusic.core.domain.usecase

import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.model.Track

class ToggleLikeUseCase(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(trackId: String): Result<Boolean> {
        return musicRepository.toggleLike(trackId)
    }

    suspend operator fun invoke(track: Track): Result<Boolean> {
        return musicRepository.toggleTrackLike(track)
    }
}
