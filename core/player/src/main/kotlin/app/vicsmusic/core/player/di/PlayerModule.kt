package app.vicsmusic.core.player.di

import android.content.Context
import app.vicsmusic.core.database.dao.QueueDao
import app.vicsmusic.core.database.dao.TrackDao
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.domain.repository.UserPreferencesRepository
import app.vicsmusic.core.player.PlaybackConnection
import app.vicsmusic.core.player.PlayerManager
import app.vicsmusic.core.player.cache.PlayerCacheManager
import app.vicsmusic.core.player.queue.QueueManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun providePlayerCacheManager(@ApplicationContext context: Context): PlayerCacheManager {
        return PlayerCacheManager(context)
    }

    @Provides
    @Singleton
    fun provideQueueManager(
        queueDao: QueueDao,
        trackDao: TrackDao
    ): QueueManager {
        return QueueManager(queueDao, trackDao)
    }

    @Provides
    @Singleton
    fun providePlayerManager(
        @ApplicationContext context: Context,
        queueManager: QueueManager,
        musicRepository: MusicRepository,
        userPreferencesRepository: UserPreferencesRepository,
        playerCacheManager: PlayerCacheManager
    ): PlayerManager {
        return PlayerManager(
            context = context,
            queueManager = queueManager,
            musicRepository = musicRepository,
            userPreferencesRepository = userPreferencesRepository,
            playerCacheManager = playerCacheManager
        )
    }

    @Provides
    @Singleton
    fun providePlaybackConnection(
        @ApplicationContext context: Context,
        playerManager: PlayerManager
    ): PlaybackConnection {
        return PlaybackConnection(context, playerManager)
    }
}
