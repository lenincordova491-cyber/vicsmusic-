package app.vicsmusic.core.domain.usecase

import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.model.Track
import kotlinx.coroutines.flow.Flow

class GetLocalTracksUseCase(
    private val musicRepository: MusicRepository
) {
    operator fun invoke(): Flow<List<Track>> {
        return musicRepository.getAllTracks()
    }

    suspend fun sync(): Result<Int> {
        return musicRepository.syncLocalTracks()
    }
}
