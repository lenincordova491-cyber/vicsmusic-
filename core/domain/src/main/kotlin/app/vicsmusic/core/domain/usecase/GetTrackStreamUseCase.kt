package app.vicsmusic.core.domain.usecase

import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.model.AudioQuality
import app.vicsmusic.core.model.StreamInfo

class GetTrackStreamUseCase(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(trackId: String, quality: AudioQuality = AudioQuality.HIGH): Result<StreamInfo> {
        return musicRepository.getStreamInfo(trackId, quality)
    }
}
