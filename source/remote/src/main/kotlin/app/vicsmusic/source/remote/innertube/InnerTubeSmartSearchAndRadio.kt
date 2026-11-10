package app.vicsmusic.source.remote.innertube

import app.vicsmusic.core.model.Artist
import app.vicsmusic.core.model.Playlist
import app.vicsmusic.core.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parámetros Protobuf oficiales de YouTube Music (`WEB_REMIX`) para filtrar directamente en los
 * servidores de Google en lugar de filtrar en memoria local.
 */
object InnerTubeSearchParams {
    const val ALL = "" // Devuelve Top Result (musicCardShelfRenderer) + Canciones + Artistas + Álbumes
    const val SONGS = "EgWKAQIIAWoQEAMQBBAJEAoQBRAREBAQ"
    const val VIDEOS = "EgWKAQIQAWoQEAMQBBAJEAoQBRAREBAQ"
    const val ALBUMS = "EgWKAQIYAWoQEAMQBBAJEAoQBRAREBAQ"
    const val ARTISTS = "EgWKAQIgAWoQEAMQBBAJEAoQBRAREBAQ"
    const val COMMUNITY_PLAYLISTS = "EgeKAQQoAEABahAQAxAEEAkQChAFEBEQEBA%3D"
    const val FEATURED_PLAYLISTS = "EgeKAQQoADgBahAQAxAEEAkQChAFEBEQEBA%3D"
}

/**
 * Modos de ajuste para la radio continua de InnerTube (`/youtubei/v1/next`),
 * equivalentes a los chips superiores de la cola en YouTube Music.
 */
enum class RadioTunerMode(val params: String) {
    DEFAULT_SMART_MIX("wAEB"),                 // Mezcla equilibrada por IA de Google
    FAMILIAR_FAVORITES("wAEB8gECeAE%3D"),      // "Conocidas": prioriza canciones que el usuario ya conoce
    DEEP_DISCOVERY("wAEB8gECeAI%3D"),          // "Descubrir": prioriza joyas ocultas y artistas nuevos
    UPBEAT_ENERGY("wAEB8gECeAM%3D"),           // "Alegre / Energía"
    CHILL_RELAX("wAEB8gECeAQ%3D")              // "Tranquila / Relax"
}

/**
 * Sugerencia instantánea de búsqueda (< 50 ms) con predicciones de texto y entidades directas.
 */
data class InstantSearchSuggestions(
    val textSuggestions: List<String>,
    val directTracks: List<Track>,
    val directArtists: List<Artist>
)

/**
 * Respuesta multidimensional de búsqueda de InnerTube (igual que la pantalla de Spotify / YouTube Music).
 */
data class FullInnerTubeSearchResponse(
    val topResultTrack: Track?,
    val topResultArtist: Artist?,
    val songs: List<Track>,
    val artists: List<Artist>,
    val playlists: List<Playlist>
)

/**
 * Extensión avanzada para `InnerTubeClient` y `YouTubeMusicSource` que habilita:
 * 1. Autocompletado instantáneo mientras el usuario escribe (`music/get_search_suggestions`).
 * 2. Extracción del "Mejor Resultado" neuronal de Google (`musicCardShelfRenderer`) + Artistas + Playlists reales.
 * 3. Radio infinita personalizada por canción/artista (`RDAMVM` + `RadioTunerMode`) con soporte de cuenta autenticada (`SAPISIDHASH`).
 * 4. Feed de Inicio dinámico (`FEmusic_home`) para poblar `YouTubeMusicSource.browseHome()`.
 */
@Singleton
class InnerTubeSmartSearchAndRadio @Inject constructor(
    private val innerTubeClient: InnerTubeClient,
    private val httpClient: HttpClient,
    private val sessionManager: InnerTubeSessionManager
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 1. AUTOCOMPLETADO INSTANTÁNEO ("Lee la mente mientras escribes" en < 50 ms).
     * Combina el endpoint `music/get_search_suggestions` de InnerTube (que devuelve tanto texto
     * como canciones/artistas sugeridos) con el endpoint ultrarrápido de Google Suggest como respaldo.
     */
    suspend fun getInstantSuggestions(partialQuery: String): InstantSearchSuggestions = withContext(Dispatchers.IO) {
        val clean = partialQuery.trim()
        if (clean.isEmpty()) {
            return@withContext InstantSearchSuggestions(emptyList(), emptyList(), emptyList())
        }

        coroutineScope {
            // Petición A: Endpoint rápido de Google Suggest para autocompletar texto al instante
            val googleSuggestDeferred = async {
                runCatching {
                    val raw = httpClient.get("https://suggestqueries-clients6.youtube.com/complete/search") {
                        parameter("client", "youtube")
                        parameter("ds", "yt")
                        parameter("hl", "es")
                        parameter("gl", "PE")
                        parameter("q", clean)
                    }.bodyAsText()
                    parseGoogleSuggestJsonp(raw)
                }.getOrDefault(emptyList())
            }

            // Petición B: Endpoint `music/get_search_suggestions` de InnerTube (incluye pistas/artistas directos)
            val innerTubeSuggestDeferred = async {
                runCatching {
                    executeInnerTubeSuggestions(clean)
                }.getOrNull()
            }

            val fastTexts = googleSuggestDeferred.await()
            val richSuggestions = innerTubeSuggestDeferred.await()

            val mergedTexts = buildList {
                richSuggestions?.textSuggestions?.let { addAll(it) }
                addAll(fastTexts)
            }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(8)

            InstantSearchSuggestions(
                textSuggestions = mergedTexts,
                directTracks = richSuggestions?.directTracks.orEmpty().take(3),
                directArtists = richSuggestions?.directArtists.orEmpty().take(2)
            )
        }
    }

    /**
     * 2. BÚSQUEDA MULTIDIMENSIONAL COMPLETA (Top Result + Canciones + Artistas + Playlists reales).
     * Lanza en paralelo la búsqueda general (para extraer el `musicCardShelfRenderer` o Mejor Resultado)
     * y la búsqueda filtrada de canciones (`InnerTubeSearchParams.SONGS`) para no perder ningún hit.
     */
    suspend fun searchFullCatalog(query: String): Result<FullInnerTubeSearchResponse> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) {
            return@withContext Result.success(
                FullInnerTubeSearchResponse(null, null, emptyList(), emptyList(), emptyList())
            )
        }

        runCatching {
            coroutineScope {
                val generalDeferred = async {
                    innerTubeClient.search(
                        query = clean,
                        params = InnerTubeSearchParams.ALL
                    )
                }
                val songsDeferred = async {
                    innerTubeClient.search(
                        query = clean,
                        params = InnerTubeSearchParams.SONGS
                    )
                }

                val generalJson = generalDeferred.await()
                val songsJson = songsDeferred.await()

                val parsedGeneral = parseGeneralSearchResponse(generalJson)
                val dedicatedSongs = parseSongsFromSearchResponse(songsJson)

                val allSongs = buildList {
                    parsedGeneral.topResultTrack?.let { add(it) }
                    addAll(dedicatedSongs)
                    addAll(parsedGeneral.songs)
                }.distinctBy { it.id }

                FullInnerTubeSearchResponse(
                    topResultTrack = parsedGeneral.topResultTrack ?: allSongs.firstOrNull(),
                    topResultArtist = parsedGeneral.topResultArtist,
                    songs = allSongs,
                    artists = parsedGeneral.artists,
                    playlists = parsedGeneral.playlists
                )
            }
        }
    }

    /**
     * 3. RADIO CONTINUA PERSONALIZADA ("Autoplay que adivina tu estado de ánimo").
     * Consulta `/youtubei/v1/next` con el prefijo `RDAMVM<videoId>` y el modo `RadioTunerMode`.
     * Si el usuario inició sesión en `InnerTubeSessionManager`, envía su `SAPISIDHASH` para que
     * Google personalice la cola exactamente con el historial de su cuenta.
     */
    suspend fun fetchContinuousSmartRadio(
        seedTrack: Track,
        tunerMode: RadioTunerMode = RadioTunerMode.DEFAULT_SMART_MIX,
        excludedTrackIds: Set<String> = emptySet(),
        limit: Int = 25
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanVideoId = seedTrack.id.removePrefix("yt_").trim()
            val radioPlaylistId = "RDAMVM$cleanVideoId"

            val responseJson = innerTubeClient.nextQueue(
                videoId = cleanVideoId,
                playlistId = radioPlaylistId,
                params = tunerMode.params,
                continuation = null
            )

            val tracks = parseNextQueueTracks(responseJson, cleanVideoId, excludedTrackIds)
            tracks.take(limit)
        }
    }

    /**
     * 4. FEED DE INICIO DINÁMICO (`FEmusic_home`) para reemplazar el stub vacío en `YouTubeMusicSource.browseHome()`.
     * Extrae todos los estantes (`musicCarouselShelfRenderer`) personalizados para el usuario o para Perú (`gl = "PE"`).
     */
    suspend fun browsePersonalizedHome(): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            val root = innerTubeClient.browse(browseId = "FEmusic_home")

            val tracks = ArrayList<Track>()
            val tabs = root.obj("contents", "singleColumnBrowseResultsRenderer")
                ?.arr("tabs") ?: return@runCatching emptyList()

            val sectionContents = tabs.firstOrNull()?.jsonObject
                ?.obj("tabRenderer", "content", "sectionListRenderer")
                ?.arr("contents") ?: return@runCatching emptyList()

            for (section in sectionContents) {
                val carouselItems = section.jsonObject
                    .obj("musicCarouselShelfRenderer")
                    ?.arr("contents") ?: continue

                for (item in carouselItems) {
                    val itemObj = item.jsonObject
                    // Puede venir como musicResponsiveListItemRenderer (canción directa) o musicTwoRowItemRenderer (tarjeta)
                    itemObj.obj("musicResponsiveListItemRenderer")?.let { renderer ->
                        parseResponsiveTrackItem(renderer)?.let { tracks.add(it) }
                    }
                    itemObj.obj("musicTwoRowItemRenderer")?.let { renderer ->
                        parseTwoRowTrackItem(renderer)?.let { tracks.add(it) }
                    }
                }
            }
            tracks.distinctBy { it.id }
        }
    }

    // =========================================================================
    // Parsers internos de alta resiliencia para el árbol JSON de InnerTube
    // =========================================================================

    private suspend fun executeInnerTubeSuggestions(query: String): InstantSearchSuggestions {
        val session = sessionManager.session.value
        val profile = InnerTubeClients.WEB_REMIX
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", profile.clientName)
                    put("clientVersion", profile.clientVersion)
                    put("hl", "es")
                    put("gl", "PE")
                    if (!session.visitorData.isNullOrBlank()) {
                        put("visitorData", session.visitorData)
                    }
                }
            }
            put("input", query)
        }

        val responseText = httpClient.post("${profile.baseUrl}/music/get_search_suggestions?prettyPrint=false") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.UserAgent, profile.userAgent)
            header("X-Goog-Api-Format-Version", "2")
            header("X-YouTube-Client-Name", profile.clientId.toString())
            header("X-YouTube-Client-Version", profile.clientVersion)
            if (session.isLoggedIn) {
                session.cookieHeader?.let { header(HttpHeaders.Cookie, it) }
                sessionManager.buildAuthorizationHeader()?.let { header(HttpHeaders.Authorization, it) }
            }
            setBody(payload.toString())
        }.bodyAsText()

        val root = json.parseToJsonElement(responseText).jsonObject
        val textList = ArrayList<String>()
        val directTracks = ArrayList<Track>()
        val directArtists = ArrayList<Artist>()

        val contents = root.arr("contents") ?: return InstantSearchSuggestions(emptyList(), emptyList(), emptyList())
        for (section in contents) {
            val sectionItems = section.jsonObject
                .obj("searchSuggestionsSectionRenderer")
                ?.arr("contents") ?: continue

            for (entry in sectionItems) {
                val entryObj = entry.jsonObject
                // 1. Sugerencia de texto autocompletado
                val suggestionRuns = entryObj.obj("searchSuggestionRenderer", "suggestion")?.arr("runs")
                if (suggestionRuns != null) {
                    val fullText = suggestionRuns.joinToString("") {
                        it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
                    }.trim()
                    if (fullText.isNotEmpty()) textList.add(fullText)
                }

                // 2. Resultado directo (Canción o Artista dentro del desplegable de sugerencias)
                val responsive = entryObj.obj("musicResponsiveListItemRenderer")
                if (responsive != null) {
                    parseResponsiveTrackItem(responsive)?.let { directTracks.add(it) }
                }
            }
        }

        return InstantSearchSuggestions(textList, directTracks, directArtists)
    }

    private fun parseGeneralSearchResponse(root: JsonObject): FullInnerTubeSearchResponse {
        var topTrack: Track? = null
        var topArtist: Artist? = null
        val songs = ArrayList<Track>()
        val artists = ArrayList<Artist>()
        val playlists = ArrayList<Playlist>()

        val tabs = root.obj("contents", "tabbedSearchResultsRenderer")?.arr("tabs")
        val sections = tabs?.firstOrNull()?.jsonObject
            ?.obj("tabRenderer", "content", "sectionListRenderer")
            ?.arr("contents") ?: return FullInnerTubeSearchResponse(null, null, emptyList(), emptyList(), emptyList())

        for (section in sections) {
            val secObj = section.jsonObject

            // 1. "Mejor Resultado" (Top Result Card - musicCardShelfRenderer)
            secObj.obj("musicCardShelfRenderer")?.let { card ->
                val videoId = card.obj("onTap", "watchEndpoint")?.str("videoId")
                    ?: card.obj("title", "runs")?.arr("runs")?.firstOrNull()?.jsonObject
                        ?.obj("navigationEndpoint", "watchEndpoint")?.str("videoId")

                val title = card.obj("title")?.runsText().orEmpty()
                val subtitle = card.obj("subtitle")?.runsText().orEmpty()
                val thumb = card.obj("thumbnail", "musicThumbnailRenderer", "thumbnail")
                    ?.arr("thumbnails")?.lastOrNull()?.jsonObject?.str("url").orEmpty()

                if (!videoId.isNullOrBlank() && title.isNotBlank()) {
                    val artistName = subtitle.split("•").getOrNull(1)?.trim()
                        ?: subtitle.trim().ifEmpty { "Artista" }
                    topTrack = Track(
                        id = "yt_$videoId",
                        sourceId = "ytmusic",
                        title = title,
                        artistId = artistName.lowercase().replace(' ', '_'),
                        artistName = artistName,
                        albumId = null,
                        albumName = null,
                        durationMs = 0L,
                        artworkUrl = thumb,
                        isLiked = false
                    )
                } else if (title.isNotBlank()) {
                    val browseId = card.obj("onTap", "browseEndpoint")?.str("browseId")
                        ?: title.lowercase().replace(' ', '_')
                    topArtist = Artist(
                        id = browseId,
                        sourceId = "ytmusic",
                        name = title,
                        imageUrl = thumb,
                        bio = subtitle,
                        topTracks = emptyList()
                    )
                }
            }

            // 2. Estantes de resultados (Canciones, Artistas, Playlists)
            val shelfItems = secObj.obj("musicShelfRenderer")?.arr("contents") ?: continue
            for (item in shelfItems) {
                val renderer = item.jsonObject.obj("musicResponsiveListItemRenderer") ?: continue
                val track = parseResponsiveTrackItem(renderer)
                if (track != null) {
                    songs.add(track)
                    continue
                }

                // Si no tiene videoId, verificar si es un Artista o una Playlist con browseId
                val browseId = renderer.obj("navigationEndpoint", "browseEndpoint")?.str("browseId") ?: continue
                val flexCols = renderer.arr("flexColumns") ?: continue
                val primaryText = flexCols.getOrNull(0)?.jsonObject
                    ?.obj("musicResponsiveListItemFlexColumnRenderer", "text")?.runsText().orEmpty()
                val secondaryText = flexCols.getOrNull(1)?.jsonObject
                    ?.obj("musicResponsiveListItemFlexColumnRenderer", "text")?.runsText().orEmpty()
                val thumb = renderer.obj("thumbnail", "musicThumbnailRenderer", "thumbnail")
                    ?.arr("thumbnails")?.lastOrNull()?.jsonObject?.str("url").orEmpty()

                if (browseId.startsWith("UC") || secondaryText.contains("Artista", ignoreCase = true) || secondaryText.contains("suscriptores", ignoreCase = true)) {
                    artists.add(
                        Artist(
                            id = browseId,
                            sourceId = "ytmusic",
                            name = primaryText,
                            imageUrl = thumb,
                            bio = secondaryText,
                            topTracks = emptyList()
                        )
                    )
                } else if (browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RD")) {
                    playlists.add(
                        Playlist(
                            id = browseId,
                            title = primaryText,
                            description = secondaryText,
                            artworkUrl = thumb,
                            trackCount = 0
                        )
                    )
                }
            }
        }

        return FullInnerTubeSearchResponse(
            topResultTrack = topTrack,
            topResultArtist = topArtist,
            songs = songs,
            artists = artists.distinctBy { it.id },
            playlists = playlists.distinctBy { it.id }
        )
    }

    private fun parseSongsFromSearchResponse(root: JsonObject): List<Track> {
        val songs = ArrayList<Track>()
        val tabs = root.obj("contents", "tabbedSearchResultsRenderer")?.arr("tabs")
        val sections = tabs?.firstOrNull()?.jsonObject
            ?.obj("tabRenderer", "content", "sectionListRenderer")
            ?.arr("contents") ?: return emptyList()

        for (section in sections) {
            val items = section.jsonObject.obj("musicShelfRenderer")?.arr("contents") ?: continue
            for (item in items) {
                val renderer = item.jsonObject.obj("musicResponsiveListItemRenderer") ?: continue
                parseResponsiveTrackItem(renderer)?.let { songs.add(it) }
            }
        }
        return songs
    }

    private fun parseResponsiveTrackItem(renderer: JsonObject): Track? {
        val videoId = renderer.obj("playlistItemData")?.str("videoId")
            ?: renderer.obj("overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint")?.str("videoId")
            ?: return null

        val flexColumns = renderer.arr("flexColumns") ?: return null
        val title = flexColumns.getOrNull(0)?.jsonObject
            ?.obj("musicResponsiveListItemFlexColumnRenderer", "text")?.runsText()
            ?.takeIf { it.isNotBlank() } ?: return null

        val subtitleRuns = flexColumns.getOrNull(1)?.jsonObject
            ?.obj("musicResponsiveListItemFlexColumnRenderer", "text")?.arr("runs")

        val parts = subtitleRuns
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content?.trim() }
            ?.filter { it.isNotEmpty() && it != "•" && it != " • " }
            .orEmpty()

        val artistName = parts.firstOrNull { !it.equals("Canción", ignoreCase = true) && !it.equals("Video", ignoreCase = true) }
            ?: "Artista"
        val durationText = parts.lastOrNull { it.contains(':') }
        val durationMs = parseDurationMs(durationText)

        val thumb = renderer.obj("thumbnail", "musicThumbnailRenderer", "thumbnail")
            ?.arr("thumbnails")?.lastOrNull()?.jsonObject?.str("url").orEmpty()

        return Track(
            id = "yt_$videoId",
            sourceId = "ytmusic",
            title = title,
            artistId = artistName.lowercase().replace(' ', '_'),
            artistName = artistName,
            albumId = null,
            albumName = parts.getOrNull(2),
            durationMs = durationMs,
            artworkUrl = thumb,
            isLiked = false
        )
    }

    private fun parseTwoRowTrackItem(renderer: JsonObject): Track? {
        val videoId = renderer.obj("navigationEndpoint", "watchEndpoint")?.str("videoId") ?: return null
        val title = renderer.obj("title")?.runsText()?.takeIf { it.isNotBlank() } ?: return null
        val subtitle = renderer.obj("subtitle")?.runsText().orEmpty()
        val artistName = subtitle.split("•").lastOrNull()?.trim().orEmpty().ifEmpty { "Artista" }
        val thumb = renderer.obj("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail")
            ?.arr("thumbnails")?.lastOrNull()?.jsonObject?.str("url").orEmpty()

        return Track(
            id = "yt_$videoId",
            sourceId = "ytmusic",
            title = title,
            artistId = artistName.lowercase().replace(' ', '_'),
            artistName = artistName,
            albumId = null,
            albumName = null,
            durationMs = 0L,
            artworkUrl = thumb,
            isLiked = false
        )
    }

    private fun parseNextQueueTracks(
        root: JsonObject,
        seedVideoId: String,
        excludedTrackIds: Set<String>
    ): List<Track> {
        val tracks = ArrayList<Track>()
        val tabs = root.obj("contents", "singleColumnMusicWatchNextResultsRenderer", "tabbedRenderer", "watchNextTabbedResultsRenderer")
            ?.arr("tabs") ?: return emptyList()

        val panelContents = tabs.firstOrNull()?.jsonObject
            ?.obj("tabRenderer", "content", "musicQueueRenderer", "content", "playlistPanelRenderer")
            ?.arr("contents") ?: return emptyList()

        for (item in panelContents) {
            val renderer = item.jsonObject.obj("playlistPanelVideoRenderer") ?: continue
            val videoId = renderer.str("videoId") ?: continue
            val fullId = "yt_$videoId"
            if (videoId == seedVideoId || excludedTrackIds.contains(fullId) || excludedTrackIds.contains(videoId)) {
                continue
            }

            val title = renderer.obj("title")?.runsText()?.takeIf { it.isNotBlank() } ?: continue
            val byline = renderer.obj("longBylineText")?.runsText()
                ?: renderer.obj("shortBylineText")?.runsText()
                ?: "Artista"
            val artistName = byline.split("•").firstOrNull()?.trim().orEmpty().ifEmpty { "Artista" }
            val albumName = byline.split("•").getOrNull(1)?.trim()
            val durationMs = parseDurationMs(renderer.obj("lengthText")?.runsText())
            val thumb = renderer.obj("thumbnail")?.arr("thumbnails")
                ?.lastOrNull()?.jsonObject?.str("url").orEmpty()

            tracks.add(
                Track(
                    id = fullId,
                    sourceId = "ytmusic",
                    title = title,
                    artistId = artistName.lowercase().replace(' ', '_'),
                    artistName = artistName,
                    albumId = null,
                    albumName = albumName,
                    durationMs = durationMs,
                    artworkUrl = thumb,
                    isLiked = false
                )
            )
        }
        return tracks.distinctBy { it.id }
    }

    private fun parseGoogleSuggestJsonp(raw: String): List<String> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val jsonSlice = raw.substring(start, end + 1)
        val arr = json.parseToJsonElement(jsonSlice).jsonArray
        val suggestionsArray = arr.getOrNull(1)?.jsonArray ?: return emptyList()
        return suggestionsArray.mapNotNull { entry ->
            entry.jsonArray.getOrNull(0)?.jsonPrimitive?.content?.trim()
        }.filter { it.isNotEmpty() }
    }

    private fun parseDurationMs(durationText: String?): Long {
        if (durationText.isNullOrBlank()) return 0L
        val parts = durationText.trim().split(':').mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            else -> 0L
        }
    }

    private fun JsonObject.obj(vararg keys: String): JsonObject? {
        var cur: JsonElement = this
        for (k in keys) {
            cur = (cur as? JsonObject)?.get(k) ?: return null
        }
        return cur as? JsonObject
    }

    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content

    private fun JsonObject.runsText(): String {
        val runs = this["runs"] as? JsonArray ?: return ""
        return runs.joinToString("") {
            it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
        }.trim()
    }
}
