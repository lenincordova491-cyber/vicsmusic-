package app.vicsmusic.source.remote.declarative

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
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

class JsonMusicSource(
    val definition: ExtensionDefinition,
    private val httpClient: HttpClient
) : MusicSource {

    override val id: String = definition.id
    override val displayName: String = definition.name

    override val capabilities: Set<SourceCapability> = definition.capabilities.mapNotNull { cap ->
        when (cap.uppercase()) {
            "SEARCH" -> SourceCapability.SEARCH
            "STREAM" -> SourceCapability.STREAM
            "BROWSE" -> SourceCapability.BROWSE
            "LYRICS" -> SourceCapability.LYRICS
            else -> null
        }
    }.toSet()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val trackCache = ConcurrentHashMap<String, Track>()
    private val streamUrlCache = ConcurrentHashMap<String, String>()

    override suspend fun search(query: String, type: SearchType, page: Int): Result<Page<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpointConfig = definition.endpoints["searchTracks"]?.jsonObject
                ?: throw UnsupportedOperationException("Search not supported in extension ${definition.id}")

            val pageSize = definition.paging?.pageSize ?: 25
            val offset = (page - 1) * pageSize
            val params = mapOf(
                "query" to query,
                "limit" to pageSize.toString(),
                "offset" to offset.toString(),
                "page" to page.toString(),
                "appVersion" to "1.0.0"
            )

            val fullUrl = buildRequestUrl(endpointConfig, params)
            val jsonText = executeGet(fullUrl)
            val root = json.parseToJsonElement(jsonText)

            val responseConfig = endpointConfig["response"]?.jsonObject
            val itemsPath = responseConfig?.get("itemsPath")?.jsonPrimitive?.contentOrNull ?: "$"
            val items = JsonPathEvaluator.extractList(root, itemsPath)

            val mapConfig = responseConfig?.get("map")?.jsonObject ?: JsonObject(emptyMap())
            val tracks = items.mapNotNull { item -> mapItemToTrack(item, mapConfig) }

            tracks.forEach { trackCache[it.id] = it }

            Page(
                items = tracks,
                page = page,
                hasNextPage = tracks.size >= pageSize
            )
        }
    }

    override suspend fun browseHome(): Result<List<HomeSection>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpointConfig = definition.endpoints["home"]?.jsonObject
                ?: return@runCatching emptyList()

            val pageSize = definition.paging?.pageSize ?: 20
            val params = mapOf(
                "limit" to pageSize.toString(),
                "offset" to "0",
                "page" to "1",
                "appVersion" to "1.0.0"
            )

            val fullUrl = buildRequestUrl(endpointConfig, params)
            val jsonText = executeGet(fullUrl)
            val root = json.parseToJsonElement(jsonText)

            val responseConfig = endpointConfig["response"]?.jsonObject
            val itemsPath = responseConfig?.get("itemsPath")?.jsonPrimitive?.contentOrNull ?: "$"
            val items = JsonPathEvaluator.extractList(root, itemsPath)

            val mapConfig = responseConfig?.get("map")?.jsonObject ?: JsonObject(emptyMap())
            val tracks = items.mapNotNull { item -> mapItemToTrack(item, mapConfig) }

            tracks.forEach { trackCache[it.id] = it }

            val title = endpointConfig["sectionTitle"]?.jsonPrimitive?.contentOrNull
                ?: "${definition.name} Destacados"

            listOf(
                HomeSection(
                    id = "${definition.id}_home",
                    title = title,
                    tracks = tracks
                )
            )
        }
    }

    override suspend fun getTrack(id: String): Result<Track> = withContext(Dispatchers.IO) {
        runCatching {
            trackCache[id] ?: throw NoSuchElementException("Track $id not found in memory cache for ${definition.id}")
        }
    }

    override suspend fun getStream(id: String, quality: AudioQuality): Result<StreamInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedUrl = streamUrlCache[id]
            if (!cachedUrl.isNullOrBlank()) {
                if (cachedUrl.startsWith("vicsmusic://")) {
                    throw UnsupportedOperationException("Virtual metadata URI requires fallback resolution: $cachedUrl")
                }
                if (definition.id == "piped-music" && cachedUrl.contains("/streams/")) {
                    val jsonText = executeGet(cachedUrl)
                    val root = json.parseToJsonElement(jsonText)
                    val directAudioUrl = JsonPathEvaluator.extractString(root, "$.audioStreams[0].url")
                        ?: throw IllegalStateException("No audioStreams found in Piped response for $id")
                    streamUrlCache[id] = directAudioUrl
                    return@runCatching StreamInfo(
                        trackId = id,
                        url = directAudioUrl,
                        mimeType = "audio/mp4",
                        bitrateKbps = 160
                    )
                }
                return@runCatching StreamInfo(
                    trackId = id,
                    url = cachedUrl,
                    mimeType = "audio/mp3",
                    bitrateKbps = 320
                )
            }

            val rawId = id.removePrefix("${definition.id}_")
            val resolveConfig = definition.endpoints["resolveStream"]?.jsonObject
            if (resolveConfig != null) {
                val url = buildRequestUrl(resolveConfig, mapOf("id" to rawId, "appVersion" to "1.0.0"))
                val jsonText = executeGet(url)
                val root = json.parseToJsonElement(jsonText)
                val responseConfig = resolveConfig["response"]?.jsonObject
                val streamUrlPath = responseConfig?.get("streamUrlPath")?.jsonPrimitive?.contentOrNull
                val resolvedStream = if (streamUrlPath != null) {
                    JsonPathEvaluator.extractString(root, streamUrlPath)
                } else null
                if (!resolvedStream.isNullOrBlank()) {
                    streamUrlCache[id] = resolvedStream
                    return@runCatching StreamInfo(
                        trackId = id,
                        url = resolvedStream,
                        mimeType = "audio/mp3",
                        bitrateKbps = 320
                    )
                }
            }

            // Fallback when streamUrlCache was cleared after app restart: re-search by rawId
            search(rawId, SearchType.TRACKS, 1)
            val reResolved = streamUrlCache[id]
            if (!reResolved.isNullOrBlank()) {
                return@runCatching StreamInfo(
                    trackId = id,
                    url = reResolved,
                    mimeType = "audio/mp3",
                    bitrateKbps = 320
                )
            }

            throw NoSuchElementException("Stream URL for $id not found in extension ${definition.id}")
        }
    }

    override suspend fun getAlbum(id: String): Result<Album> = Result.failure(UnsupportedOperationException())
    override suspend fun getArtist(id: String): Result<Artist> = Result.failure(UnsupportedOperationException())

    private fun buildRequestUrl(endpoint: JsonObject, params: Map<String, String>): String {
        val pathTemplate = endpoint["path"]?.jsonPrimitive?.contentOrNull ?: ""
        val resolvedPath = TemplateEngine.resolve(pathTemplate, params)

        val queryObj = endpoint["query"]?.jsonObject
        val queryParams = mutableListOf<String>()

        queryObj?.forEach { (k, v) ->
            when (v) {
                is JsonArray -> {
                    v.forEach { elem ->
                        val rawVal = (elem as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: ""
                        val resolvedVal = TemplateEngine.resolve(rawVal, params, urlEncode = true)
                        queryParams.add("$k=$resolvedVal")
                    }
                }
                is kotlinx.serialization.json.JsonPrimitive -> {
                    val rawVal = v.contentOrNull ?: ""
                    val resolvedVal = TemplateEngine.resolve(rawVal, params, urlEncode = true)
                    queryParams.add("$k=$resolvedVal")
                }
                else -> {}
            }
        }

        val baseUrl = definition.http.baseUrl.removeSuffix("/")
        val path = if (resolvedPath.startsWith("/")) resolvedPath else "/$resolvedPath"
        val queryStr = if (queryParams.isNotEmpty()) "?" + queryParams.joinToString("&") else ""
        return "$baseUrl$path$queryStr"
    }

    private suspend fun executeGet(url: String): String {
        val userAgent = definition.http.userAgent?.let {
            TemplateEngine.resolve(it, mapOf("appVersion" to "1.0.0"))
        } ?: "Vicsmusic/1.0.0"

        val response = httpClient.get(url) {
            header("User-Agent", userAgent)
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value} from $url")
        }
        return response.bodyAsText()
    }

    private fun mapItemToTrack(item: JsonElement, mapConfig: JsonObject): Track? {
        val rawId = extractField(item, mapConfig["id"]) ?: return null
        val itemParams = mapOf("id" to rawId)
        val title = extractField(item, mapConfig["title"], itemParams) ?: "Sin título"
        val artist = extractField(item, mapConfig["artist"], itemParams) ?: definition.name
        val album = extractField(item, mapConfig["album"], itemParams)
        val durationMs = extractLongField(item, mapConfig["durationMs"]) ?: 180000L
        val artworkUrl = extractField(item, mapConfig["artworkUrl"], itemParams)
        val streamUrl = extractField(item, mapConfig["streamUrl"], itemParams)

        val trackId = "${definition.id}_$rawId"

        if (!streamUrl.isNullOrBlank()) {
            streamUrlCache[trackId] = streamUrl
        }

        return Track(
            id = trackId,
            sourceId = definition.id,
            title = title,
            artistId = "${definition.id}_${artist.lowercase().replace(" ", "_")}",
            artistName = artist,
            albumId = album?.let { "${definition.id}_${it.lowercase().replace(" ", "_")}" },
            albumName = album,
            durationMs = durationMs,
            artworkUrl = artworkUrl
        )
    }

    private fun extractField(
        item: JsonElement,
        mapping: JsonElement?,
        extraParams: Map<String, String> = emptyMap()
    ): String? {
        if (mapping == null) return null
        return when (mapping) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                val raw = mapping.contentOrNull ?: return null
                when {
                    raw.startsWith("$") -> JsonPathEvaluator.extractString(item, raw)
                    raw.contains("{{") -> TemplateEngine.resolve(raw, extraParams, itemContext = item)
                    else -> raw
                }
            }
            is JsonObject -> {
                val path = mapping["path"]?.jsonPrimitive?.contentOrNull
                val template = mapping["template"]?.jsonPrimitive?.contentOrNull
                val replaceArr = mapping["replace"]

                if (template != null) {
                    TemplateEngine.resolve(template, extraParams, itemContext = item)
                } else if (path != null) {
                    var value = JsonPathEvaluator.extractString(item, path)
                    if (value != null && replaceArr is JsonArray && replaceArr.size == 2) {
                        val from = replaceArr[0].jsonPrimitive.contentOrNull ?: ""
                        val to = replaceArr[1].jsonPrimitive.contentOrNull ?: ""
                        value = value.replace(from, to)
                    }
                    value
                } else null
            }
            else -> null
        }
    }

    private fun extractLongField(item: JsonElement, mapping: JsonElement?): Long? {
        if (mapping == null) return null
        return when (mapping) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                val path = mapping.contentOrNull ?: return null
                JsonPathEvaluator.extractLong(item, path)
            }
            is JsonObject -> {
                val path = mapping["path"]?.jsonPrimitive?.contentOrNull ?: return null
                val base = JsonPathEvaluator.extractLong(item, path) ?: return null
                val mul = mapping["mul"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 1L
                base * mul
            }
            else -> null
        }
    }
}
