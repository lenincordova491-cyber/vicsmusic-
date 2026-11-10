package app.vicsmusic.core.data.di

import android.content.Context
import app.vicsmusic.core.network.VicsHttpClientFactory
import app.vicsmusic.source.api.SourceRegistry
import app.vicsmusic.source.local.LocalMusicSource
import app.vicsmusic.source.remote.YouTubeMusicSource
import app.vicsmusic.source.remote.innertube.InnerTubeClient
import app.vicsmusic.source.remote.innertube.InnerTubeSessionManager
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
    fun provideInnerTubeSessionManager(): InnerTubeSessionManager {
        return InnerTubeSessionManager()
    }

    @Provides
    @Singleton
    fun provideInnerTubeClient(
        httpClient: HttpClient,
        sessionManager: InnerTubeSessionManager
    ): InnerTubeClient {
        return InnerTubeClient(httpClient, sessionManager)
    }

    @Provides
    @Singleton
    fun provideInnerTubeSmartSearchAndRadio(
        innerTubeClient: InnerTubeClient,
        httpClient: HttpClient,
        sessionManager: InnerTubeSessionManager
    ): app.vicsmusic.source.remote.innertube.InnerTubeSmartSearchAndRadio {
        return app.vicsmusic.source.remote.innertube.InnerTubeSmartSearchAndRadio(
            innerTubeClient = innerTubeClient,
            httpClient = httpClient,
            sessionManager = sessionManager
        )
    }

    @Provides
    @Singleton
    fun provideYouTubeMusicSource(
        innerTubeClient: InnerTubeClient,
        smartSearch: app.vicsmusic.source.remote.innertube.InnerTubeSmartSearchAndRadio
    ): YouTubeMusicSource {
        return YouTubeMusicSource(innerTubeClient, smartSearch)
    }

    @Provides
    @Singleton
    fun provideExtensionRegistryClient(httpClient: HttpClient): app.vicsmusic.core.network.ExtensionRegistryClient {
        return app.vicsmusic.core.network.ExtensionRegistryClient(httpClient)
    }

    @Provides
    @Singleton
    fun provideLrclibClient(httpClient: HttpClient): app.vicsmusic.core.network.LrclibClient {
        return app.vicsmusic.core.network.LrclibClient(httpClient)
    }

    @Provides
    @Singleton
    fun provideLocalMusicSource(
        @ApplicationContext context: Context
    ): LocalMusicSource {
        return LocalMusicSource(context)
    }

    @Provides
    @Singleton
    fun provideSourceRegistry(
        youTubeMusicSource: YouTubeMusicSource,
        localMusicSource: LocalMusicSource
    ): SourceRegistry {
        val registry = SourceRegistry()
        registry.register(youTubeMusicSource)
        registry.register(localMusicSource)
        return registry
    }

    @Provides
    @Singleton
    fun provideDeclarativeExtensionManager(
        @ApplicationContext context: Context,
        httpClient: HttpClient,
        sourceRegistry: SourceRegistry
    ): app.vicsmusic.source.remote.declarative.DeclarativeExtensionManager {
        return app.vicsmusic.source.remote.declarative.DeclarativeExtensionManager(context, httpClient, sourceRegistry)
    }
}
