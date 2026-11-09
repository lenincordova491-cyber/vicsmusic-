package app.vicsmusic.core.player.cache

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.model.AudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class PlayerCacheManager @Inject constructor(
    private val context: Context
) {
    private val cacheDir = File(context.cacheDir, "vicsmusic_media_cache")
    private val databaseProvider = StandaloneDatabaseProvider(context)
    private val maxCacheSize: Long = 512 * 1024 * 1024L // 512 MB LRU

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("com.google.android.apps.youtube.vr.oculus/1.56.21 (Linux; U; Android 12; en_US; Quest 2; Build/SQ3A.220605.009.A1) gzip")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(15_000)

    val simpleCache: SimpleCache by lazy {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val evictor = LeastRecentlyUsedCacheEvictor(maxCacheSize)
        SimpleCache(cacheDir, evictor, databaseProvider)
    }

    fun updateRequestHeaders(headers: Map<String, String>) {
        val ua = headers["User-Agent"] ?: headers["user-agent"]
        if (!ua.isNullOrBlank()) {
            httpDataSourceFactory.setUserAgent(ua)
        }
        httpDataSourceFactory.setDefaultRequestProperties(headers)
    }

    fun createCacheDataSourceFactory(): CacheDataSource.Factory {
        val upstreamFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(simpleCache))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun createResolvingDataSourceFactory(
        musicRepository: MusicRepository,
        qualityProvider: () -> AudioQuality = { AudioQuality.HIGH }
    ): DataSource.Factory {
        val cacheDataSourceFactory = createCacheDataSourceFactory()

        val resolver = ResolvingDataSource.Resolver { dataSpec ->
            val uri = dataSpec.uri
            val uriString = uri.toString()

            if (uri.scheme == "vicsmusic" && uri.host == "track") {
                val trackId = uri.lastPathSegment ?: uriString.substringAfter("vicsmusic://track/")
                val streamResult = runBlocking(Dispatchers.IO) {
                    musicRepository.getStreamInfo(trackId, qualityProvider())
                }

                val streamInfo = streamResult.getOrElse {
                    throw IOException("Error resolviendo stream para track $trackId: ${it.message}", it)
                }

                if (streamInfo.headers.isNotEmpty()) {
                    updateRequestHeaders(streamInfo.headers)
                }

                dataSpec.buildUpon()
                    .setUri(Uri.parse(streamInfo.url))
                    .setKey(trackId)
                    .build()
            } else if (dataSpec.key.isNullOrBlank() && (uriString.contains("googlevideo.com") || uriString.contains("youtube"))) {
                val videoId = uri.getQueryParameter("id") ?: uri.getQueryParameter("docid")
                if (!videoId.isNullOrBlank()) {
                    dataSpec.buildUpon().setKey(videoId).build()
                } else {
                    dataSpec
                }
            } else {
                dataSpec
            }
        }

        return ResolvingDataSource.Factory(cacheDataSourceFactory, resolver)
    }

    fun release() {
        // SimpleCache is maintained for the application process lifetime.
    }
}
