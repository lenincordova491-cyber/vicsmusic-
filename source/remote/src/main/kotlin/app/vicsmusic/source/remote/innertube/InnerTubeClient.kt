package app.vicsmusic.source.remote.innertube

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.util.Locale

class InnerTubeException(message: String, cause: Throwable? = null) : IOException(message, cause)

class InnerTubeClient(
    private val httpClient: HttpClient,
    private val sessionManager: InnerTubeSessionManager,
    private val localeProvider: () -> Locale = { Locale.getDefault() }
) {
    companion object {
        const val BROWSE_HOME = "FEmusic_home"
        const val BROWSE_CHARTS = "FEmusic_charts"
        const val BROWSE_HISTORY = "FEmusic_history"
        const val BROWSE_LIKED_SONGS = "FEmusic_liked_videos"

        const val HEADER_CLIENT_NAME = "X-YouTube-Client-Name"
        const val HEADER_CLIENT_VERSION = "X-YouTube-Client-Version"
        const val HEADER_VISITOR_ID = "X-Goog-Visitor-Id"
        const val DEFAULT_VISITOR_ID = "CgtwZXN3TUdWVFoycyjm8bjXBjIKCgJQRRIEGgAgEg%3D%3D"
        const val DEFAULT_LANGUAGE = "es"
        const val DEFAULT_COUNTRY = "PE"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Consulta páginas de catálogo: Home personalizado, Rankings (Charts), Historial, Álbumes o Artistas.
     */
    suspend fun browse(
        browseId: String,
        params: String? = null,
        continuation: String? = null,
        profile: InnerTubeClientProfile = InnerTubeClients.WEB_REMIX
    ): JsonObject = executePost(
        endpoint = "browse",
        profile = profile,
        continuation = continuation
    ) {
        if (continuation == null) {
            put("browseId", browseId)
            if (!params.isNullOrBlank()) put("params", params)
        }
    }

    /**
     * Obtiene la lista/cola continua (Radio / Automix) calculada por Google para reproducción infinita.
     */
    suspend fun nextQueue(
        videoId: String? = null,
        playlistId: String? = videoId?.let { "RDAMVM$it" },
        params: String? = null,
        continuation: String? = null,
        profile: InnerTubeClientProfile = InnerTubeClients.WEB_REMIX
    ): JsonObject = executePost(
        endpoint = "next",
        profile = profile,
        continuation = continuation
    ) {
        if (videoId != null) put("videoId", videoId)
        if (playlistId != null) put("playlistId", playlistId)
        if (params != null) put("params", params)
        put("isAudioOnly", true)
    }

    suspend fun search(
        query: String,
        params: String? = null,
        continuation: String? = null,
        profile: InnerTubeClientProfile = InnerTubeClients.WEB_REMIX
    ): JsonObject = executePost(
        endpoint = "search",
        profile = profile,
        continuation = continuation
    ) {
        if (continuation == null) {
            put("query", query)
            if (!params.isNullOrBlank()) put("params", params)
        }
    }

    suspend fun player(videoId: String, profile: InnerTubeClientProfile): JsonObject = executePost(
        endpoint = "player",
        profile = profile
    ) {
        put("videoId", videoId)
        put("contentCheckOk", true)
        put("racyCheckOk", true)
    }

    suspend fun reportPlayback(videoId: String): Result<Unit> = runCatching {
        if (!sessionManager.session.value.isLoggedIn) return@runCatching
        executePost(
            endpoint = "playback",
            profile = InnerTubeClients.WEB_REMIX
        ) {
            put("playbackContext", buildJsonObject {
                put("contentPlaybackContext", buildJsonObject {
                    put("signatureTimestamp", (System.currentTimeMillis() / 1000L).toInt())
                })
            })
            put("videoId", videoId)
        }
        Unit
    }

    private suspend fun executePost(
        endpoint: String,
        profile: InnerTubeClientProfile,
        continuation: String? = null,
        bodyBuilder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit
    ): JsonObject {
        val currentSession = sessionManager.session.value
        val locale = localeProvider()
        val requestBody = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", profile.clientName)
                    put("clientVersion", profile.clientVersion)
                    put("hl", locale.language.ifBlank { DEFAULT_LANGUAGE })
                    put("gl", locale.country.ifBlank { DEFAULT_COUNTRY })
                    profile.deviceMake?.let { put("deviceMake", it) }
                    profile.deviceModel?.let { put("deviceModel", it) }
                    profile.osName?.let { put("osName", it) }
                    profile.osVersion?.let { put("osVersion", it) }
                    profile.androidSdkVersion?.let { put("androidSdkVersion", it) }
                    currentSession.visitorData?.let { put("visitorData", it) }
                }
                currentSession.dataSyncId?.let { syncId ->
                    putJsonObject("user") {
                        put("onBehalfOfUser", syncId)
                    }
                }
            }
            bodyBuilder()
        }

        val url = "${profile.baseUrl}/$endpoint"
        val response = try {
            httpClient.post(url) {
                parameter("prettyPrint", "false")
                if (continuation != null) {
                    parameter("ctoken", continuation)
                    parameter("continuation", continuation)
                    if (endpoint == "search") parameter("type", "next")
                }
                contentType(ContentType.Application.Json)
                header(HttpHeaders.UserAgent, profile.userAgent)
                header("X-Goog-Api-Format-Version", "2")
                header(HEADER_CLIENT_NAME, profile.clientId.toString())
                header(HEADER_CLIENT_VERSION, profile.clientVersion)
                profile.origin?.let {
                    header(HttpHeaders.Origin, it)
                    header(HttpHeaders.Referrer, "$it/")
                }

                val visitor = currentSession.visitorData ?: DEFAULT_VISITOR_ID
                header(HEADER_VISITOR_ID, visitor)

                if (currentSession.isLoggedIn) {
                    currentSession.cookieHeader?.let { header(HttpHeaders.Cookie, it) }
                    sessionManager.buildAuthorizationHeader()?.let {
                        header(HttpHeaders.Authorization, it)
                    }
                }
                setBody(requestBody.toString())
            }
        } catch (e: Exception) {
            throw InnerTubeException("Network error calling InnerTube $endpoint (${profile.clientName})", e)
        }

        if (!response.status.isSuccess()) {
            throw InnerTubeException("InnerTube ${profile.clientName} responded HTTP ${response.status.value} on $endpoint")
        }

        val text = response.bodyAsText()
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw InnerTubeException("Malformed InnerTube response on $endpoint", e)
        }

        // Auto-update visitorData if provided
        root.obj("responseContext")?.str("visitorData")?.let { visitor ->
            if (visitor.isNotBlank()) sessionManager.updateVisitorData(visitor)
        }

        return root
    }
}
