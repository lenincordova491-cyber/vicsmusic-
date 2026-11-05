package app.vicsmusic.core.domain.usecase

import app.vicsmusic.core.domain.repository.PlaylistRepository
import app.vicsmusic.core.model.Playlist
import kotlinx.coroutines.flow.Flow

class GetPlaylistsUseCase(
    private val playlistRepository: PlaylistRepository
) {
    operator fun invoke(): Flow<List<Playlist>> {
        return playlistRepository.getPlaylists()
    }
}
