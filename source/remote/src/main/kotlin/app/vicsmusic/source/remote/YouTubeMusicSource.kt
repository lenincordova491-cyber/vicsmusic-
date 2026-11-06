package app.vicsmusic.source.remote

import app.vicsmusic.core.model.Album
import app.vicsmusic.core.model.Artist
import app.vicsmusic.core.model.AudioQuality
import app.vicsmusic.core.model.StreamInfo
import app.vicsmusic.core.model.Track
import app.vicsmusic.source.api.HomeSection
import app.vicsmusic.source.api.MusicSource
import app.vicsmusic.source.api.Page
import app.vicsmusic.source.api.SearchType
import app.vicsmusic.source.api.SourceCapability
import app.vicsmusic.source.remote.innertube.InnerTubeClient
import app.vicsmusic.source.remote.innertube.InnerTubeClients
import app.vicsmusic.source.remote.innertube.InnerTubeException
import app.vicsmusic.source.remote.innertube.InnerTubeParser
import app.vicsmusic.source.remote.innertube.RemoteTrack
import io.ktor.http.HttpHeaders

class YouTubeMusicSource(
    private val client: InnerTubeClient
) : MusicSource {

    override val id: String = SOURCE_ID
    override val displayName: String = "YouTube Music"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.SEARCH,
        SourceCapability.STREAM
    )

    override suspend fun search(
        query: String,
        type: SearchType,
        page: Int
    ): Result<Page<Track>> = runCatching {
        val params = if (type == SearchType.TRACKS || type == SearchType.ALL) {
            InnerTubeClients.SEARCH_FILTER_SONGS
        } else {
            null
        }
        val json = client.search(query = query, params = params, continuation = null)
        val parsed = InnerTubeParser.parseSearch(json)
        Page(
            items = parsed.tracks.map { it.toDomainTrack() },
            page = page,
            hasNextPage = parsed.continuation != null
        )
    }

    override suspend fun getTrack(id: String): Result<Track> = runCatching {
        val videoId = id.removePrefix(TRACK_PREFIX)
        for (profile in InnerTubeClients.STREAM_CLIENTS) {
            val parsed = runCatching {
                InnerTubeParser.parsePlayer(client.player(videoId, profile))
            }.getOrNull() ?: continue
            parsed.videoDetails?.let { return@runCatching it.toDomainTrack() }
        }
        throw InnerTubeException("Track metadata unavailable for $id")
    }

    override suspend fun getStream(
        id: String,
        quality: AudioQuality
    ): Result<StreamInfo> = runCatching {
        val videoId = id.removePrefix(TRACK_PREFIX)
        var lastReason: String? = null
        for (profile in InnerTubeClients.STREAM_CLIENTS) {
            val parsedResult = try {
                InnerTubeParser.parsePlayer(client.player(videoId, profile))
            } catch (t: Throwable) {
                lastReason = t.message
                null
            } ?: continue

            if (!parsedResult.isPlayable) {
                lastReason = parsedResult.reason ?: parsedResult.playabilityStatus
                continue
            }
            val format = InnerTubeParser.selectFormat(parsedResult.audioFormats, quality) ?: continue
            val expiresAt = parsedResult.expiresInSeconds?.let {
                System.currentTimeMillis() + it * 1000L
            }
            return@runCatching StreamInfo(
                trackId = id,
                url = format.url,
                mimeType = format.mimeType,
                bitrateKbps = (format.bitrate / 1000).coerceAtLeast(1),
                expiresAt = expiresAt,
                headers = mapOf(HttpHeaders.UserAgent to profile.userAgent)
            )
        }
        throw InnerTubeException("Unable to resolve stream for $id (${lastReason ?: "all clients failed"})")
    }

    override suspend fun getAlbum(id: String): Result<Album> =
        Result.failure(UnsupportedOperationException("Album browse not implemented in stub"))

    override suspend fun getArtist(id: String): Result<Artist> =
        Result.failure(UnsupportedOperationException("Artist browse not implemented in stub"))

    override suspend fun browseHome(): Result<List<HomeSection>> =
        Result.success(emptyList())

    private fun RemoteTrack.toDomainTrack(): Track = Track(
        id = "$TRACK_PREFIX$videoId",
        sourceId = SOURCE_ID,
        title = title,
        artistId = artistId ?: "yt_artist_unknown",
        artistName = artistName.ifBlank { "Unknown artist" },
        albumId = albumId,
        albumName = albumName,
        durationMs = durationMs,
        artworkUrl = thumbnailUrl
    )

    companion object {
        const val SOURCE_ID = "ytmusic"
        const val TRACK_PREFIX = "yt_"
    }
}
