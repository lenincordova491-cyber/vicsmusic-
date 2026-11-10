package app.vicsmusic.core.data.di

import app.vicsmusic.core.data.repository.AppUpdateRepositoryImpl
import app.vicsmusic.core.data.repository.MusicRepositoryImpl
import app.vicsmusic.core.data.repository.PlaylistRepositoryImpl
import app.vicsmusic.core.data.repository.UserPreferencesRepositoryImpl
import app.vicsmusic.core.domain.repository.AppUpdateRepository
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.domain.repository.PlaylistRepository
import app.vicsmusic.core.domain.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMusicRepository(
        impl: MusicRepositoryImpl
    ): MusicRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(
        impl: PlaylistRepositoryImpl
    ): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: UserPreferencesRepositoryImpl
    ): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(
        impl: AppUpdateRepositoryImpl
    ): AppUpdateRepository
}
