package app.vicsmusic.core.data.di

import android.content.Context
import app.vicsmusic.source.api.SourceRegistry
import app.vicsmusic.source.local.LocalMusicSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SourceModule {

    @Provides
    @Singleton
    fun provideSourceRegistry(@ApplicationContext context: Context): SourceRegistry {
        val registry = SourceRegistry()
        val localSource = LocalMusicSource(context)
        registry.register(localSource)
        return registry
    }
}
