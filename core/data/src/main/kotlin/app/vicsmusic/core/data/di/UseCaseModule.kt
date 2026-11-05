package app.vicsmusic.core.data.di

import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.domain.repository.PlaylistRepository
import app.vicsmusic.core.domain.usecase.GetLocalTracksUseCase
import app.vicsmusic.core.domain.usecase.GetPlaylistsUseCase
import app.vicsmusic.core.domain.usecase.GetTrackStreamUseCase
import app.vicsmusic.core.domain.usecase.SearchTracksUseCase
import app.vicsmusic.core.domain.usecase.ToggleLikeUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideGetLocalTracksUseCase(
        musicRepository: MusicRepository
    ): GetLocalTracksUseCase = GetLocalTracksUseCase(musicRepository)

    @Provides
    @Singleton
    fun provideSearchTracksUseCase(
        musicRepository: MusicRepository
    ): SearchTracksUseCase = SearchTracksUseCase(musicRepository)

    @Provides
    @Singleton
    fun provideGetTrackStreamUseCase(
        musicRepository: MusicRepository
    ): GetTrackStreamUseCase = GetTrackStreamUseCase(musicRepository)

    @Provides
    @Singleton
    fun provideToggleLikeUseCase(
        musicRepository: MusicRepository
    ): ToggleLikeUseCase = ToggleLikeUseCase(musicRepository)

    @Provides
    @Singleton
    fun provideGetPlaylistsUseCase(
        playlistRepository: PlaylistRepository
    ): GetPlaylistsUseCase = GetPlaylistsUseCase(playlistRepository)
}
