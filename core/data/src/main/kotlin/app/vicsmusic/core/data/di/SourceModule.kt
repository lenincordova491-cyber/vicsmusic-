package app.vicsmusic.core.data.di

import android.content.Context
import app.vicsmusic.core.network.VicsHttpClientFactory
import app.vicsmusic.source.api.SourceRegistry
import app.vicsmusic.source.local.LocalMusicSource
import app.vicsmusic.source.remote.YouTubeMusicSource
import app.vicsmusic.source.remote.innertube.InnerTubeClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SourceModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient {
        return VicsHttpClientFactory.create()
    }

    @Provides
    @Singleton
    fun provideInnerTubeClient(httpClient: HttpClient): InnerTubeClient {
        return InnerTubeClient(httpClient)
    }

    @Provides
    @Singleton
    fun provideYouTubeMusicSource(innerTubeClient: InnerTubeClient): YouTubeMusicSource {
        return YouTubeMusicSource(innerTubeClient)
    }

    @Provides
    @Singleton
    fun provideExtensionRegistryClient(httpClient: HttpClient): app.vicsmusic.core.network.ExtensionRegistryClient {
        return app.vicsmusic.core.network.ExtensionRegistryClient(httpClient)
    }

    @Provides
    @Singleton
    fun provideSourceRegistry(
        @ApplicationContext context: Context,
        youTubeMusicSource: YouTubeMusicSource
    ): SourceRegistry {
        val registry = SourceRegistry()
        val localSource = LocalMusicSource(context)
        registry.register(localSource)
        registry.register(youTubeMusicSource)
        return registry
    }
}
