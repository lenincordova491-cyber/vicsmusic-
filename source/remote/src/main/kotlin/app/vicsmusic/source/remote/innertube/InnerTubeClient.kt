package app.vicsmusic.source.remote.innertube

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.util.Locale

class InnerTubeException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Minimal HTTP transport for the InnerTube endpoints used by Vicsmusic:
 * `/search` (catalog) and `/player` (stream extraction).
 */
class InnerTubeClient(
    private val httpClient: HttpClient,
    private val localeProvider: () -> Locale = { Locale.getDefault() }
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var currentVisitorId: String = DEFAULT_VISITOR_ID

    suspend fun search(
        query: String,
        params: String?,
        continuation: String?,
        profile: InnerTubeClientProfile = InnerTubeClients.WEB_REMIX
    ): JsonObject {
        val url = buildString {
            append(profile.baseUrl).append("/search?prettyPrint=false")
            if (continuation != null) {
                val token = continuation.encodeURLParameter()
                append("&ctoken=").append(token)
                append("&continuation=").append(token)
                append("&type=next")
            }
        }
        val body = buildJsonObject {
            put("context", buildContext(profile))
            if (continuation == null) {
                put("query", query)
                params?.let { put("params", it) }
            }
        }
        return post(url, profile, body)
    }

    suspend fun player(videoId: String, profile: InnerTubeClientProfile): JsonObject {
        val body = buildJsonObject {
            put("context", buildContext(profile))
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        return post("${profile.baseUrl}/player?prettyPrint=false", profile, body)
    }

    private suspend fun post(url: String, profile: InnerTubeClientProfile, body: JsonObject): JsonObject {
        val response = try {
            httpClient.post(url) {
                header(HttpHeaders.UserAgent, profile.userAgent)
                header(HEADER_CLIENT_NAME, profile.clientId.toString())
                header(HEADER_CLIENT_VERSION, profile.clientVersion)
                header(HEADER_VISITOR_ID, currentVisitorId)
                profile.origin?.let {
                    header(HttpHeaders.Origin, it)
                    header(HttpHeaders.Referrer, "$it/")
                }
                setBody(TextContent(body.toString(), ContentType.Application.Json))
            }
        } catch (e: IOException) {
            throw InnerTubeException("Network error calling InnerTube (${profile.clientName})", e)
        }

        if (!response.status.isSuccess()) {
            throw InnerTubeException("InnerTube ${profile.clientName} responded HTTP ${response.status.value}")
        }
        return runCatching {
            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            root.str("responseContext", "visitorData")?.let { visitor ->
                if (visitor.isNotBlank()) currentVisitorId = visitor
            }
            root
        }.getOrElse { throw InnerTubeException("Malformed InnerTube response", it) }
    }

    private fun buildContext(profile: InnerTubeClientProfile): JsonObject {
        val locale = localeProvider()
        return buildJsonObject {
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
            }
        }
    }

    private companion object {
        const val HEADER_CLIENT_NAME = "X-YouTube-Client-Name"
        const val HEADER_CLIENT_VERSION = "X-YouTube-Client-Version"
        const val HEADER_VISITOR_ID = "X-Goog-Visitor-Id"
        const val DEFAULT_VISITOR_ID = "CgtwZXN3TUdWVFoycyjm8bjXBjIKCgJQRRIEGgAgEg%3D%3D"
        const val DEFAULT_LANGUAGE = "es"
        const val DEFAULT_COUNTRY = "US"
    }
}
