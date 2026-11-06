package app.vicsmusic.core.network

import app.vicsmusic.core.model.Lyrics
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LrclibClient(
    private val httpClient: HttpClient = VicsHttpClientFactory.create(),
    private val baseUrl: String = "https://lrclib.net/api"
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun getLyrics(
        trackId: String,
        trackName: String,
        artistName: String,
        durationSeconds: Int? = null,
        albumName: String? = null
    ): Result<Lyrics?> = runCatching {
        val cleanTrack = sanitizeTrackTitle(trackName)
        val cleanArtist = artistName.trim()

        val response = httpClient.get("$baseUrl/get") {
            parameter("track_name", cleanTrack)
            parameter("artist_name", cleanArtist)
            if (!albumName.isNullOrBlank()) {
                parameter("album_name", albumName.trim())
            }
            if (durationSeconds != null && durationSeconds > 0) {
                parameter("duration", durationSeconds)
            }
        }

        if (response.status == HttpStatusCode.NotFound) {
            return@runCatching null
        }

        if (response.status != HttpStatusCode.OK) {
            return@runCatching null
        }

        val body = response.bodyAsText()
        parseLrclibResponse(trackId, body)
    }

    suspend fun searchLyrics(
        trackId: String,
        query: String
    ): Result<Lyrics?> = runCatching {
        val response = httpClient.get("$baseUrl/search") {
            parameter("q", query)
        }
        if (response.status != HttpStatusCode.OK) {
            return@runCatching null
        }
        val body = response.bodyAsText()
        val jsonElement = json.parseToJsonElement(body)
        val array = jsonElement.let { if (it is kotlinx.serialization.json.JsonArray) it else null }
        val firstMatch = array?.firstOrNull()?.jsonObject ?: return@runCatching null

        val synced = firstMatch["syncedLyrics"]?.jsonPrimitive?.content
        val plain = firstMatch["plainLyrics"]?.jsonPrimitive?.content
        LrcParser.parse(trackId = trackId, lrcContent = synced, plainLyrics = plain, source = "lrclib")
    }

    fun parseLrclibResponse(trackId: String, jsonBody: String): Lyrics? {
        val obj = runCatching { json.parseToJsonElement(jsonBody).jsonObject }.getOrNull() ?: return null
        val isInstrumental = obj["instrumental"]?.jsonPrimitive?.booleanOrNull ?: false
        if (isInstrumental) {
            return Lyrics(
                trackId = trackId,
                lines = listOf(app.vicsmusic.core.model.LyricLine(0L, "♪ Instrumental ♪")),
                isSynced = true,
                source = "lrclib"
            )
        }

        val synced = obj["syncedLyrics"]?.jsonPrimitive?.content
        val plain = obj["plainLyrics"]?.jsonPrimitive?.content
        if (synced.isNullOrBlank() && plain.isNullOrBlank()) {
            return null
        }
        return LrcParser.parse(trackId = trackId, lrcContent = synced, plainLyrics = plain, source = "lrclib")
    }

    private fun sanitizeTrackTitle(title: String): String {
        return title
            .replace(Regex("""\s*[\(\[](?:feat|ft|official|video|audio|remastered|lyric).*?[\)\]]""", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}
