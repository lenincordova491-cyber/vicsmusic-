package app.vicsmusic.core.network

import app.vicsmusic.core.model.Chart
import app.vicsmusic.core.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.concurrent.ConcurrentHashMap

data class SpotifyChartDef(
    val id: String,
    val playlistId: String,
    val defaultTitle: String,
    val defaultSubtitle: String,
    val emoji: String,
    val fallbackCoverUrl: String
)

class SpotifyChartsClient(
    private val httpClient: HttpClient = VicsHttpClientFactory.create()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, CachedChart>()
    private val thumbnailCache = ConcurrentHashMap<String, String>()
    private val mutex = Mutex()

    private data class CachedChart(
        val chart: Chart,
        val timestamp: Long
    )

    companion object {
        private const val CACHE_TTL_MS = 2 * 60 * 60 * 1000L // 2 horas de caché para rankings diarios
        private val NEXT_DATA_REGEX = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

        val DEFAULT_CHARTS = listOf(
            SpotifyChartDef(
                id = "top_50_peru",
                playlistId = "37i9dQZEVXbJfdy5b0KP7W",
                defaultTitle = "Top 50 - Perú",
                defaultSubtitle = "Tu actualización diaria de las canciones más sonadas en Perú",
                emoji = "🇵🇪",
                fallbackCoverUrl = "https://charts-images.scdn.co/assets/locale_en/regional/daily/region_pe_default.jpg"
            ),
            SpotifyChartDef(
                id = "top_50_global",
                playlistId = "37i9dQZEVXbMDoHDwVN2tF",
                defaultTitle = "Top 50 - Global",
                defaultSubtitle = "Las canciones más escuchadas en el mundo ahora mismo",
                emoji = "🌍",
                fallbackCoverUrl = "https://charts-images.scdn.co/assets/locale_en/regional/daily/region_global_default.jpg"
            ),
            SpotifyChartDef(
                id = "viva_latino",
                playlistId = "37i9dQZF1DX10zKzsJ2jva",
                defaultTitle = "Viva Latino",
                defaultSubtitle = "El epicentro del ritmo y los mayores hits de la música latina",
                emoji = "✨",
                fallbackCoverUrl = "https://image-cdn-ak.spotifycdn.com/image/ab67706f000000031530b39927b056d87492f573"
            ),
            SpotifyChartDef(
                id = "top_50_mexico",
                playlistId = "37i9dQZEVXbO3qyFxbkOE1",
                defaultTitle = "Top 50 - México",
                defaultSubtitle = "Las canciones más escuchadas en México hoy",
                emoji = "🇲🇽",
                fallbackCoverUrl = "https://charts-images.scdn.co/assets/locale_en/regional/daily/region_mx_default.jpg"
            ),
            SpotifyChartDef(
                id = "top_50_argentina",
                playlistId = "37i9dQZEVXbMMy2roB9myp",
                defaultTitle = "Top 50 - Argentina",
                defaultSubtitle = "Las canciones más escuchadas en Argentina hoy",
                emoji = "🇦🇷",
                fallbackCoverUrl = "https://charts-images.scdn.co/assets/locale_en/regional/daily/region_ar_default.jpg"
            ),
            SpotifyChartDef(
                id = "top_50_espana",
                playlistId = "37i9dQZEVXbNFJfN1Vw8d9",
                defaultTitle = "Top 50 - España",
                defaultSubtitle = "Las canciones más escuchadas en España hoy",
                emoji = "🇪🇸",
                fallbackCoverUrl = "https://charts-images.scdn.co/assets/locale_en/regional/daily/region_es_default.jpg"
            ),
            SpotifyChartDef(
                id = "top_50_usa",
                playlistId = "37i9dQZEVXbLRQDuF5jeBp",
                defaultTitle = "Top 50 - EE.UU.",
                defaultSubtitle = "Las canciones más escuchadas en Estados Unidos hoy",
                emoji = "🇺🇸",
                fallbackCoverUrl = "https://charts-images.scdn.co/assets/locale_en/regional/daily/region_us_default.jpg"
            )
        )
    }

    suspend fun getLiveCharts(definitions: List<SpotifyChartDef> = DEFAULT_CHARTS): List<Chart> = coroutineScope {
        definitions.map { def ->
            async {
                fetchChart(def)
            }
        }.awaitAll()
    }

    suspend fun fetchChart(def: SpotifyChartDef): Chart {
        val now = System.currentTimeMillis()
        val cached = cache[def.id]
        if (cached != null && (now - cached.timestamp) < CACHE_TTL_MS && cached.chart.tracks.isNotEmpty()) {
            return cached.chart
        }

        return try {
            val url = "https://open.spotify.com/embed/playlist/${def.playlistId}"
            val response = httpClient.get(url) {
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                header(HttpHeaders.AcceptLanguage, "es-ES,es;q=0.9,en;q=0.8")
            }

            if (!response.status.isSuccess()) {
                return cached?.chart ?: fallbackEmptyChart(def)
            }

            val html = response.bodyAsText()
            val match = NEXT_DATA_REGEX.find(html) ?: return cached?.chart ?: fallbackEmptyChart(def)
            val jsonText = match.groupValues[1]

            val root = json.parseToJsonElement(jsonText).jsonObject
            val entity = root["props"]?.jsonObject
                ?.get("pageProps")?.jsonObject
                ?.get("state")?.jsonObject
                ?.get("data")?.jsonObject
                ?.get("entity")?.jsonObject ?: return cached?.chart ?: fallbackEmptyChart(def)

            val name = entity["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: def.defaultTitle
            val subtitle = entity["subtitle"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: def.defaultSubtitle

            val coverUrl = extractCoverUrl(entity) ?: def.fallbackCoverUrl

            val trackList = entity["trackList"]?.jsonArray.orEmpty()
            val parsedTracks = mutableListOf<Track>()

            for (item in trackList) {
                val itemObj = item.jsonObject
                val uri = itemObj["uri"]?.jsonPrimitive?.content.orEmpty()
                val rawId = uri.substringAfterLast(":", "")
                if (rawId.isBlank()) continue

                val title = itemObj["title"]?.jsonPrimitive?.content.orEmpty().trim()
                val artists = itemObj["subtitle"]?.jsonPrimitive?.content.orEmpty()
                    .replace("\u00A0", " ")
                    .replace("&nbsp;", " ")
                    .trim()
                val durationMs = itemObj["duration"]?.jsonPrimitive?.longOrNull ?: 180000L

                val track = Track(
                    id = "sp_$rawId",
                    sourceId = "spotify",
                    title = title,
                    artistId = "sp_artist_${artists.hashCode()}",
                    artistName = artists.ifBlank { "Varios Artistas" },
                    albumId = "sp_album_${def.id}",
                    albumName = name,
                    durationMs = durationMs,
                    artworkUrl = thumbnailCache[rawId] ?: coverUrl
                )
                parsedTracks.add(track)
            }

            // Enriquecer los thumbnails de las primeras canciones en paralelo
            val enrichedTracks = coroutineScope {
                val deferredThumbnails = parsedTracks.take(15).map { track ->
                    async {
                        val rawId = track.id.removePrefix("sp_")
                        val thumb = fetchTrackThumbnail(rawId)
                        if (thumb != null) {
                            track.copy(artworkUrl = thumb)
                        } else {
                            track
                        }
                    }
                }
                val topEnriched = deferredThumbnails.awaitAll()
                topEnriched + parsedTracks.drop(15)
            }

            val finalChart = Chart(
                id = def.id,
                title = name,
                subtitle = subtitle,
                emoji = def.emoji,
                tracks = enrichedTracks,
                coverUrl = coverUrl
            )

            cache[def.id] = CachedChart(finalChart, now)
            finalChart
        } catch (e: Exception) {
            cached?.chart ?: fallbackEmptyChart(def)
        }
    }

    private suspend fun fetchTrackThumbnail(trackId: String): String? {
        val cached = thumbnailCache[trackId]
        if (cached != null) return cached

        return try {
            val url = "https://open.spotify.com/oembed?url=https://open.spotify.com/track/$trackId"
            val response = httpClient.get(url) {
                header(HttpHeaders.UserAgent, "Mozilla/5.0")
            }
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val obj = json.parseToJsonElement(body).jsonObject
                val thumb = obj["thumbnail_url"]?.jsonPrimitive?.content
                if (!thumb.isNullOrBlank()) {
                    thumbnailCache[trackId] = thumb
                    thumb
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractCoverUrl(entity: JsonObject): String? {
        // 1. entity.coverArt.sources[0].url
        entity["coverArt"]?.jsonObject?.get("sources")?.jsonArray?.firstOrNull()?.let { src ->
            val url = src.jsonObject["url"]?.jsonPrimitive?.content
            if (!url.isNullOrBlank()) return url
        }

        // 2. entity.visualIdentity.image[last].url
        entity["visualIdentity"]?.jsonObject?.get("image")?.jsonArray?.lastOrNull()?.let { img ->
            val url = img.jsonObject["url"]?.jsonPrimitive?.content
            if (!url.isNullOrBlank()) return url
        }

        // 3. entity.attributes con key image_url o header_image_url_desktop
        entity["attributes"]?.jsonArray?.forEach { attr ->
            val key = attr.jsonObject["key"]?.jsonPrimitive?.content
            val value = attr.jsonObject["value"]?.jsonPrimitive?.content
            if ((key == "image_url" || key == "header_image_url_desktop") && !value.isNullOrBlank()) {
                return value
            }
        }

        return null
    }

    private fun fallbackEmptyChart(def: SpotifyChartDef): Chart = Chart(
        id = def.id,
        title = def.defaultTitle,
        subtitle = def.defaultSubtitle,
        emoji = def.emoji,
        tracks = emptyList(),
        coverUrl = def.fallbackCoverUrl
    )
}
