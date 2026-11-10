package app.vicsmusic.core.network

import app.vicsmusic.core.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class InnerTubeBackendSearchResult(
    val success: Boolean = false,
    val query: String = "",
    val count: Int = 0,
    val results: List<InnerTubeBackendItem> = emptyList()
)

@Serializable
data class InnerTubeBackendItem(
    @SerialName("video_id") val videoId: String,
    val title: String,
    val thumbnail: String? = null,
    val duration: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Long? = null,
    val channel: InnerTubeBackendChannel? = null,
    val views: Long? = null
) {
    fun toDomainTrack(): Track = Track(
        id = "yt_$videoId",
        sourceId = "ytmusic",
        title = title,
        artistId = channel?.id?.let { "yt_$it" } ?: "yt_artist_unknown",
        artistName = channel?.name?.ifBlank { "Artista desconocido" } ?: "Artista desconocido",
        durationMs = (durationSeconds ?: 0L) * 1000L,
        artworkUrl = thumbnail
    )
}

@Serializable
data class InnerTubeBackendChannel(
    val name: String = "",
    val id: String? = null
)

@Serializable
data class InnerTubeHealthResponse(
    val status: String = "",
    val version: String = "",
    val service: String = ""
)

/**
 * Cliente HTTP de alta velocidad para consultar el backend dedicado de Python InnerTube-API.
 * Ofrece búsquedas cacheadas en memoria (< 20 ms) con fallback inmediato al cliente nativo
 * si el servidor no está accesible en la red local.
 */
class InnerTubeBackendClient(
    private val httpClient: HttpClient = VicsHttpClientFactory.create()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun checkHealth(baseUrl: String): Boolean = runCatching {
        val cleanUrl = baseUrl.trimEnd('/')
        val response = httpClient.get("$cleanUrl/health") {
            timeout {
                requestTimeoutMillis = 2_000L
                connectTimeoutMillis = 1_500L
                socketTimeoutMillis = 2_000L
            }
        }
        response.status == HttpStatusCode.OK
    }.getOrDefault(false)

    suspend fun search(query: String, baseUrl: String, limit: Int = 25): Result<List<Track>> = runCatching {
        val clean = query.trim()
        if (clean.isEmpty()) return@runCatching emptyList()

        val cleanUrl = baseUrl.trimEnd('/')
        val response: HttpResponse = httpClient.get("$cleanUrl/api/search") {
            parameter("query", clean)
            parameter("limit", limit)
            timeout {
                requestTimeoutMillis = 5_000L
                connectTimeoutMillis = 2_500L
                socketTimeoutMillis = 5_000L
            }
        }

        if (response.status == HttpStatusCode.OK) {
            val bodyText = response.body<String>()
            val parsed = json.decodeFromString<InnerTubeBackendSearchResult>(bodyText)
            parsed.results.map { it.toDomainTrack() }
        } else {
            emptyList()
        }
    }
}
