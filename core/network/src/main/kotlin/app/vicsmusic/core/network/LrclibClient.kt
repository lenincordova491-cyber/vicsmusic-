package app.vicsmusic.core.network

import app.vicsmusic.core.model.Lyrics
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class LrclibSongCandidate(
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val durationSec: Int,
    val syncedLyrics: String?,
    val plainLyrics: String?
)

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
        val cleanArtist = sanitizeArtistName(artistName)

        val response = httpClient.get("$baseUrl/get") {
            parameter("track_name", cleanTrack)
            parameter("artist_name", cleanArtist)
            if (!albumName.isNullOrBlank() && !albumName.equals("Hits", ignoreCase = true) && !albumName.equals("Single", ignoreCase = true)) {
                parameter("album_name", albumName.trim())
            }
            if (durationSeconds != null && durationSeconds > 0) {
                parameter("duration", durationSeconds)
            }
        }

        if (response.status == HttpStatusCode.OK) {
            val parsed = parseLrclibResponse(trackId, response.bodyAsText(), (durationSeconds ?: 210) * 1000L)
            if (parsed != null && parsed.lines.isNotEmpty()) {
                return@runCatching parsed
            }
        }

        // Fallback: search by clean track + artist, then by clean track only
        searchLyrics(trackId, "$cleanTrack $cleanArtist", (durationSeconds ?: 210) * 1000L).getOrNull()
            ?: searchLyrics(trackId, cleanTrack, (durationSeconds ?: 210) * 1000L).getOrNull()
    }

    suspend fun searchLyrics(
        trackId: String,
        query: String,
        durationMs: Long = 210_000L
    ): Result<Lyrics?> = runCatching {
        val cleanQuery = sanitizeTrackTitle(query)
        val response = httpClient.get("$baseUrl/search") {
            parameter("q", cleanQuery)
        }
        if (response.status != HttpStatusCode.OK) {
            return@runCatching null
        }
        val body = response.bodyAsText()
        val array = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull()
            ?: return@runCatching null

        val objects = array.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
        if (objects.isEmpty()) return@runCatching null

        // Prefer first entry that has synced LRC timestamps
        val bestMatch = objects.firstOrNull { obj ->
            val s = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull
            !s.isNullOrBlank() && s != "null"
        } ?: objects.firstOrNull { obj ->
            val p = obj["plainLyrics"]?.jsonPrimitive?.contentOrNull
            !p.isNullOrBlank() && p != "null"
        } ?: return@runCatching null

        val synced = bestMatch["syncedLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" }
        val plain = bestMatch["plainLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" }
        LrcParser.parse(
            trackId = trackId,
            lrcContent = synced,
            plainLyrics = plain,
            source = "lrclib",
            durationMs = durationMs
        )
    }

    /**
     * Searches LRCLIB's full-text lyrics and metadata index to identify songs when
     * the user types a lyric fragment or partial phrase without knowing the song title.
     */
    suspend fun searchSongsByLyricPhrase(query: String, limit: Int = 5): List<LrclibSongCandidate> {
        return runCatching {
            val response = httpClient.get("$baseUrl/search") {
                parameter("q", query.trim())
            }
            if (response.status != HttpStatusCode.OK) return@runCatching emptyList()
            val array = json.parseToJsonElement(response.bodyAsText()) as? JsonArray ?: return@runCatching emptyList()
            array.mapNotNull { elem ->
                val obj = runCatching { elem.jsonObject }.getOrNull() ?: return@mapNotNull null
                val trackName = obj["trackName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
                    ?: return@mapNotNull null
                val artistName = obj["artistName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
                    ?: return@mapNotNull null
                val albumName = obj["albumName"]?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" }
                val durationSec = obj["duration"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 210
                val synced = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" && it.isNotBlank() }
                val plain = obj["plainLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" && it.isNotBlank() }
                LrclibSongCandidate(
                    trackName = trackName,
                    artistName = artistName,
                    albumName = albumName,
                    durationSec = durationSec,
                    syncedLyrics = synced,
                    plainLyrics = plain
                )
            }.distinctBy { "${it.trackName.lowercase()}_${it.artistName.lowercase()}" }.take(limit)
        }.getOrDefault(emptyList())
    }

    fun parseLrclibResponse(trackId: String, jsonBody: String, durationMs: Long = 210_000L): Lyrics? {
        val obj = runCatching { json.parseToJsonElement(jsonBody).jsonObject }.getOrNull() ?: return null
        val isInstrumental = obj["instrumental"]?.jsonPrimitive?.booleanOrNull ?: false
        if (isInstrumental) {
            return Lyrics(
                trackId = trackId,
                lines = listOf(app.vicsmusic.core.model.LyricLine(0L, "♪ Pista Instrumental ♪")),
                isSynced = true,
                source = "lrclib"
            )
        }

        val synced = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" }
        val plain = obj["plainLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" }
        if (synced.isNullOrBlank() && plain.isNullOrBlank()) {
            return null
        }
        return LrcParser.parse(
            trackId = trackId,
            lrcContent = synced,
            plainLyrics = plain,
            source = "lrclib",
            durationMs = durationMs
        )
    }

    fun sanitizeTrackTitle(title: String): String {
        return title
            .replace(Regex("""\s*[\(\[](?:feat|ft|official|oficial|video|audio|remastered|lyric|letra|en vivo|live|hd|4k|hq|videoclip|visualizer).*?[\)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*[-|]\s*(?:video oficial|official video|letra|lyrics|audio).*$""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    fun sanitizeArtistName(artist: String): String {
        return artist
            .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""VEVO$""", RegexOption.IGNORE_CASE), "")
            .split(",", "&", " feat", " ft.", " x ", " X ")
            .firstOrNull()
            ?.trim()
            ?.ifEmpty { artist.trim() }
            ?: artist.trim()
    }
}
