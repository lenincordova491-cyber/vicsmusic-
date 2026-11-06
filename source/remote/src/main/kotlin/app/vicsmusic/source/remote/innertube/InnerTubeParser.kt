package app.vicsmusic.source.remote.innertube

import app.vicsmusic.core.model.AudioQuality
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.math.abs

/** Track metadata extracted from an InnerTube catalog response. */
data class RemoteTrack(
    val videoId: String,
    val title: String,
    val artistName: String,
    val artistId: String?,
    val albumName: String?,
    val albumId: String?,
    val durationMs: Long,
    val thumbnailUrl: String?
)

data class SearchPageResult(
    val tracks: List<RemoteTrack>,
    val continuation: String?
)

/** Audio-only adaptive format with a directly playable URL. */
data class AudioFormat(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val codecs: String?,
    val bitrate: Int,
    val contentLength: Long?
)

data class PlayerResult(
    val playabilityStatus: String,
    val reason: String?,
    val audioFormats: List<AudioFormat>,
    val expiresInSeconds: Long?,
    val videoDetails: RemoteTrack?
) {
    val isPlayable: Boolean get() = playabilityStatus == STATUS_OK && audioFormats.isNotEmpty()

    companion object {
        const val STATUS_OK = "OK"
    }
}

/**
 * Pure, side-effect-free translation of InnerTube JSON into domain-ready
 * structures. Kept separate from networking so it is fully unit-testable
 * with captured response fixtures.
 */
object InnerTubeParser {

    private const val PAGE_TYPE_ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
    private const val PAGE_TYPE_ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
    private const val SEPARATOR = " • "
    private const val ARTWORK_SIZE = 544
    private const val MEDIUM_TARGET_BITRATE = 128_000

    private val durationRegex = Regex("""^(\d+:)?\d{1,2}:\d{2}$""")
    private val googleThumbSizeRegex = Regex("""=w\d+-h\d+""")

    // ---------------------------------------------------------------- search

    fun parseSearch(root: JsonObject): SearchPageResult {
        val continuationShelf = root.obj("continuationContents", "musicShelfContinuation")
        if (continuationShelf != null) {
            val tracks = continuationShelf.arr("contents").orEmpty()
                .mapNotNull { parseListItem(it.obj("musicResponsiveListItemRenderer")) }
            val continuation = continuationShelf.arr("continuations")?.firstOrNull()
                .str("nextContinuationData", "continuation")
            return SearchPageResult(tracks, continuation)
        }

        val tabs = root.arr("contents", "tabbedSearchResultsRenderer", "tabs").orEmpty()
        val sections = tabs.firstOrNull()
            .arr("tabRenderer", "content", "sectionListRenderer", "contents")
            ?: root.arr("contents", "sectionListRenderer", "contents")
            .orEmpty()

        val tracks = mutableListOf<RemoteTrack>()
        var continuation: String? = null

        for (section in sections) {
            val shelf = section.obj("musicShelfRenderer")
            if (shelf != null) {
                shelf.arr("contents").orEmpty().forEach { item ->
                    parseListItem(item.obj("musicResponsiveListItemRenderer"))?.let { tracks.add(it) }
                }
                if (continuation == null) {
                    continuation = shelf.arr("continuations")?.firstOrNull()
                        .str("nextContinuationData", "continuation")
                }
            }
            val itemSection = section.obj("itemSectionRenderer")
            if (itemSection != null) {
                itemSection.arr("contents").orEmpty().forEach { item ->
                    parseListItem(item.obj("musicResponsiveListItemRenderer"))?.let { tracks.add(it) }
                }
            }
            val cardShelf = section.obj("musicCardShelfRenderer")
            if (cardShelf != null) {
                cardShelf.arr("contents").orEmpty().forEach { item ->
                    parseListItem(item.obj("musicResponsiveListItemRenderer"))?.let { tracks.add(it) }
                }
            }
        }

        return SearchPageResult(tracks.distinctBy { it.videoId }, continuation)
    }

    internal fun parseListItem(item: JsonObject?): RemoteTrack? {
        if (item == null) return null

        val videoId = item.str("playlistItemData", "videoId")
            ?: item.str(
                "overlay", "musicItemThumbnailOverlayRenderer", "content",
                "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint", "videoId"
            )
            ?: return null

        val flexRuns: List<JsonArray> = item.arr("flexColumns").orEmpty().map { column ->
            column.arr("musicResponsiveListItemFlexColumnRenderer", "text", "runs") ?: JsonArray(emptyList())
        }
        val title = flexRuns.firstOrNull()?.firstOrNull().str("text")?.trim()
        if (title.isNullOrEmpty()) return null

        val metadataRuns = flexRuns.drop(1).flatten()
        val artistNames = mutableListOf<String>()
        var artistId: String? = null
        var albumName: String? = null
        var albumId: String? = null
        var durationText: String? = null

        for (run in metadataRuns) {
            val text = run.str("text")?.trim().orEmpty()
            if (text.isEmpty() || text == SEPARATOR.trim()) continue
            val browseEndpoint = run.obj("navigationEndpoint", "browseEndpoint")
            val pageType = browseEndpoint.str(
                "browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType"
            )
            when {
                pageType == PAGE_TYPE_ARTIST -> {
                    artistNames += text
                    if (artistId == null) artistId = browseEndpoint.str("browseId")
                }
                pageType == PAGE_TYPE_ALBUM -> {
                    albumName = text
                    albumId = browseEndpoint.str("browseId")
                }
                durationRegex.matches(text) -> durationText = text
            }
        }

        if (artistNames.isEmpty()) {
            // Artists without a channel page carry no endpoint: take the first plain segment.
            flexRuns.getOrNull(1).orEmpty()
                .mapNotNull { it.str("text")?.trim() }
                .firstOrNull { it.isNotEmpty() && it != SEPARATOR.trim() && !durationRegex.matches(it) }
                ?.let { artistNames += it }
        }

        if (durationText == null) {
            durationText = item.arr("fixedColumns")?.firstOrNull()
                .arr("musicResponsiveListItemFixedColumnRenderer", "text", "runs")
                ?.firstOrNull().str("text")
                ?.takeIf { durationRegex.matches(it.trim()) }
        }

        val thumbnailUrl = item.arr("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")
            ?.lastOrNull().str("url")
            ?.let(::upscaleArtwork)

        return RemoteTrack(
            videoId = videoId,
            title = title,
            artistName = artistNames.joinToString(", "),
            artistId = artistId,
            albumName = albumName,
            albumId = albumId,
            durationMs = durationText?.let(::parseDurationMs) ?: 0L,
            thumbnailUrl = thumbnailUrl
        )
    }

    // ---------------------------------------------------------------- player

    fun parsePlayer(root: JsonObject): PlayerResult {
        val streamingData = root.obj("streamingData")
        val formats = streamingData.arr("adaptiveFormats").orEmpty().mapNotNull(::parseAudioFormat)

        return PlayerResult(
            playabilityStatus = root.str("playabilityStatus", "status") ?: "UNKNOWN",
            reason = root.str("playabilityStatus", "reason"),
            audioFormats = formats,
            expiresInSeconds = streamingData.long("expiresInSeconds"),
            videoDetails = parseVideoDetails(root.obj("videoDetails"))
        )
    }

    private fun parseAudioFormat(element: JsonElement): AudioFormat? {
        val rawMime = element.str("mimeType") ?: return null
        if (!rawMime.startsWith("audio/")) return null
        // Formats exposing only "signatureCipher" are skipped on purpose:
        // stream clients are chosen so that a direct URL is always present.
        val url = element.str("url") ?: return null
        val itag = element.long("itag")?.toInt() ?: return null

        val mimeType = rawMime.substringBefore(';').trim()
        val codecs = rawMime.substringAfter("codecs=", "")
            .trim().trim('"').takeIf { it.isNotEmpty() }

        return AudioFormat(
            itag = itag,
            url = url,
            mimeType = mimeType,
            codecs = codecs,
            bitrate = (element.long("averageBitrate") ?: element.long("bitrate") ?: 0L).toInt(),
            contentLength = element.long("contentLength")
        )
    }

    private fun parseVideoDetails(details: JsonObject?): RemoteTrack? {
        if (details == null) return null
        val videoId = details.str("videoId") ?: return null
        val title = details.str("title") ?: return null
        return RemoteTrack(
            videoId = videoId,
            title = title,
            artistName = details.str("author").orEmpty().removeSuffix(" - Topic"),
            artistId = details.str("channelId"),
            albumName = null,
            albumId = null,
            durationMs = (details.long("lengthSeconds") ?: 0L) * 1000L,
            thumbnailUrl = details.arr("thumbnail", "thumbnails")?.lastOrNull().str("url")
        )
    }

    /** Picks the audio format that best matches the requested [quality]. */
    fun selectFormat(formats: List<AudioFormat>, quality: AudioQuality): AudioFormat? {
        if (formats.isEmpty()) return null
        val byBitrate = formats.sortedBy { it.bitrate }
        return when (quality) {
            AudioQuality.LOW -> byBitrate.first()
            AudioQuality.MEDIUM -> byBitrate.minBy { abs(it.bitrate - MEDIUM_TARGET_BITRATE) }
            AudioQuality.HIGH, AudioQuality.LOSSLESS -> byBitrate.last()
        }
    }

    // ---------------------------------------------------------------- utils

    fun parseDurationMs(text: String): Long {
        val parts = text.trim().split(':').map { it.toLongOrNull() ?: return 0L }
        return parts.fold(0L) { acc, part -> acc * 60 + part } * 1000L
    }

    /** Requests a high-resolution variant of googleusercontent artwork URLs. */
    fun upscaleArtwork(url: String): String =
        if (googleThumbSizeRegex.containsMatchIn(url)) {
            googleThumbSizeRegex.replace(url, "=w$ARTWORK_SIZE-h$ARTWORK_SIZE")
        } else {
            url
        }
}
