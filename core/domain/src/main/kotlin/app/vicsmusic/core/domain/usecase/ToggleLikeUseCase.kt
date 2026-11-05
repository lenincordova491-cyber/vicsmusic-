package app.vicsmusic.core.domain.usecase

import app.vicsmusic.core.domain.repository.MusicRepository

class ToggleLikeUseCase(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(trackId: String): Result<Boolean> {
        return musicRepository.toggleLike(trackId)
    }
}
