package app.vicsmusic.core.data.importer

import app.vicsmusic.core.database.dao.PlaylistDao
import app.vicsmusic.core.database.dao.TrackDao
import app.vicsmusic.core.database.entity.PlaylistEntity
import app.vicsmusic.core.database.entity.PlaylistTrackEntity
import app.vicsmusic.core.database.model.asEntity
import app.vicsmusic.core.model.Track
import app.vicsmusic.source.remote.innertube.InnerTubeClient
import app.vicsmusic.source.remote.innertube.InnerTubeParser
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedPlaylistResult(
    val playlistId: String,
    val title: String,
    val trackCount: Int,
    val artworkUrl: String?
)

/**
 * Importador universal de listas de reproducción desde enlaces públicos de Spotify y YouTube Music.
 */
@Singleton
class PlaylistImporter @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val innerTubeClient: InnerTubeClient
) {
    private val httpClient: HttpClient by lazy {
        app.vicsmusic.core.network.VicsHttpClientFactory.create()
    }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val nextDataRegex = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    suspend fun importPlaylist(urlOrId: String): Result<ImportedPlaylistResult> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = urlOrId.trim()
            when {
                trimmed.contains("spotify.com") || trimmed.startsWith("spotify:playlist:") -> {
                    importSpotifyPlaylist(trimmed)
                }
                trimmed.contains("youtube.com") || trimmed.contains("youtu.be") || trimmed.startsWith("VL") || trimmed.startsWith("PL") || trimmed.startsWith("RD") -> {
                    importYouTubePlaylist(trimmed)
                }
                else -> {
                    // Intento por defecto: Spotify primero, fallback YouTube Music
                    runCatching { importSpotifyPlaylist(trimmed) }
                        .getOrElse { importYouTubePlaylist(trimmed) }
                }
            }
        }
    }

    private suspend fun importSpotifyPlaylist(url: String): ImportedPlaylistResult {
        val playlistId = extractSpotifyId(url)
            ?: throw IllegalArgumentException("Enlace de Spotify no válido: $url")

        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
        val response = httpClient.get(embedUrl) {
            header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("No se pudo obtener la playlist de Spotify (HTTP ${response.status.value})")
        }

        val html = response.bodyAsText()
        val match = nextDataRegex.find(html)
            ?: throw IllegalStateException("Estructura de Spotify no reconocida")

        val root = json.parseToJsonElement(match.groupValues[1]).jsonObject
        val entity = root["props"]?.jsonObject
            ?.get("pageProps")?.jsonObject
            ?.get("state")?.jsonObject
            ?.get("data")?.jsonObject
            ?.get("entity")?.jsonObject
            ?: throw IllegalStateException("Playlist de Spotify no encontrada")

        val playlistTitle = entity["name"]?.jsonPrimitive?.content ?: "Playlist de Spotify"
        val coverUrl = entity["coverArt"]?.jsonObject
            ?.get("sources")?.jsonArray?.lastOrNull()
            ?.jsonObject?.get("url")?.jsonPrimitive?.content

        val trackList = entity["trackList"]?.jsonArray.orEmpty()
        val tracks = mutableListOf<Track>()

        for (elem in trackList) {
            val item = elem.jsonObject
            val uri = item["uri"]?.jsonPrimitive?.content ?: ""
            val rawId = uri.substringAfterLast(":")
            val title = item["title"]?.jsonPrimitive?.content ?: continue
            val artist = item["subtitle"]?.jsonPrimitive?.content ?: "Varios Artistas"
            val durationMs = item["duration"]?.jsonPrimitive?.longOrNull ?: 180_000L

            tracks.add(
                Track(
                    id = "sp_$rawId",
                    sourceId = "spotify",
                    title = title,
                    artistId = "sp_artist_${artist.hashCode()}",
                    artistName = artist,
                    albumId = "sp_album_$playlistId",
                    albumName = playlistTitle,
                    durationMs = durationMs,
                    artworkUrl = coverUrl
                )
            )
        }

        if (tracks.isEmpty()) {
            throw IllegalStateException("La playlist de Spotify no contiene canciones reproducibles.")
        }

        return saveImportedPlaylist("Spotify: $playlistTitle", tracks, coverUrl)
    }

    private suspend fun importYouTubePlaylist(url: String): ImportedPlaylistResult {
        val rawId = extractYouTubePlaylistId(url)
            ?: throw IllegalArgumentException("Enlace de YouTube no válido: $url")
        val browseId = if (rawId.startsWith("VL")) rawId else "VL$rawId"

        val browseResponse = innerTubeClient.browse(browseId)
        val rawTracks = InnerTubeParser.parseBrowseTracks(browseResponse)
        val domainTracks = rawTracks.map { it.toDomainTrack() }

        if (domainTracks.isEmpty()) {
            throw IllegalStateException("No se encontraron canciones en la lista de YouTube: $rawId")
        }

        val coverUrl = domainTracks.firstOrNull()?.artworkUrl
        return saveImportedPlaylist("YouTube: Lista importada", domainTracks, coverUrl)
    }

    private suspend fun saveImportedPlaylist(
        title: String,
        tracks: List<Track>,
        coverUrl: String?
    ): ImportedPlaylistResult {
        val playlistId = "pl_imported_${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()

        // 1. Guardar tracks en base de datos
        trackDao.upsertTracks(tracks.map { it.asEntity() })

        // 2. Crear registro de Playlist
        val playlistEntity = PlaylistEntity(
            id = playlistId,
            title = title,
            description = "Importada desde enlace externo",
            artworkUrl = coverUrl,
            createdAt = now,
            updatedAt = now
        )
        playlistDao.upsertPlaylist(playlistEntity)

        // 3. Crear asociaciones de Playlist - Tracks
        tracks.forEachIndexed { index, track ->
            playlistDao.insertPlaylistTrack(
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = track.id,
                    position = index,
                    addedAt = now + index
                )
            )
        }

        return ImportedPlaylistResult(
            playlistId = playlistId,
            title = title,
            trackCount = tracks.size,
            artworkUrl = coverUrl
        )
    }

    private fun extractSpotifyId(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.startsWith("spotify:playlist:")) {
            return trimmed.removePrefix("spotify:playlist:")
        }
        val regex = Regex("""playlist/([a-zA-Z0-9]+)""")
        return regex.find(trimmed)?.groupValues?.get(1)
    }

    private fun extractYouTubePlaylistId(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.startsWith("PL") || trimmed.startsWith("VL") || trimmed.startsWith("RD")) {
            return trimmed
        }
        val regex = Regex("""[?&]list=([a-zA-Z0-9_-]+)""")
        return regex.find(trimmed)?.groupValues?.get(1)
    }
}
