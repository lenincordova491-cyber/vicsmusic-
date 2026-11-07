package app.vicsmusic.source.local

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMusicSource(
    private val context: Context
) : MusicSource {

    override val id: String = SOURCE_ID
    override val displayName: String = "Música local"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.SEARCH,
        SourceCapability.STREAM,
        SourceCapability.BROWSE
    )

    private val contentResolver: ContentResolver get() = context.contentResolver

    suspend fun getAllTracks(): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            queryTracks()
        }
    }

    override suspend fun search(query: String, type: SearchType, page: Int): Result<Page<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val allTracks = queryTracks(
                    selection = "${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ?",
                    selectionArgs = arrayOf("%$query%", "%$query%")
                )
                val pageSize = 50
                val startIndex = (page - 1) * pageSize
                val pagedItems = if (startIndex < allTracks.size) {
                    allTracks.subList(startIndex, minOf(startIndex + pageSize, allTracks.size))
                } else {
                    emptyList()
                }
                Page(
                    items = pagedItems,
                    page = page,
                    hasNextPage = (startIndex + pageSize) < allTracks.size
                )
            }
        }

    override suspend fun getTrack(id: String): Result<Track> = withContext(Dispatchers.IO) {
        runCatching {
            val numericId = id.removePrefix(ID_PREFIX)
            val tracks = queryTracks(
                selection = "${MediaStore.Audio.Media._ID} = ?",
                selectionArgs = arrayOf(numericId)
            )
            tracks.firstOrNull() ?: throw NoSuchElementException("Track $id not found in MediaStore")
        }
    }

    override suspend fun getStream(id: String, quality: AudioQuality): Result<StreamInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val numericId = id.removePrefix(ID_PREFIX).toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid local track id: $id")
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    numericId
                ).toString()

                StreamInfo(
                    trackId = id,
                    url = contentUri,
                    mimeType = "audio/*",
                    bitrateKbps = 320,
                    expiresAt = null
                )
            }
        }

    override suspend fun getAlbum(id: String): Result<Album> = withContext(Dispatchers.IO) {
        runCatching {
            val tracks = queryTracks(
                selection = "${MediaStore.Audio.Media.ALBUM_ID} = ?",
                selectionArgs = arrayOf(id)
            )
            val first = tracks.firstOrNull() ?: throw NoSuchElementException("Album $id not found")
            Album(
                id = id,
                sourceId = SOURCE_ID,
                title = first.albumName ?: "Álbum desconocido",
                artistId = first.artistId,
                artistName = first.artistName,
                artworkUrl = first.artworkUrl,
                trackCount = tracks.size,
                tracks = tracks
            )
        }
    }

    override suspend fun getArtist(id: String): Result<Artist> = withContext(Dispatchers.IO) {
        runCatching {
            val tracks = queryTracks(
                selection = "${MediaStore.Audio.Media.ARTIST_ID} = ?",
                selectionArgs = arrayOf(id)
            )
            val first = tracks.firstOrNull() ?: throw NoSuchElementException("Artist $id not found")
            Artist(
                id = id,
                sourceId = SOURCE_ID,
                name = first.artistName,
                imageUrl = first.artworkUrl,
                topTracks = tracks
            )
        }
    }

    override suspend fun browseHome(): Result<List<HomeSection>> = withContext(Dispatchers.IO) {
        runCatching {
            val tracks = queryTracks(limit = 20)
            listOf(
                HomeSection(
                    id = "local_tracks",
                    title = "Música en este dispositivo",
                    tracks = tracks
                )
            )
        }
    }

    fun scanAllLocalTracks(): List<Track> = queryTracks(
        selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        selectionArgs = null,
        limit = null
    )

    private fun queryTracks(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        limit: Int? = null
    ): List<Track> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val tracks = mutableListOf<Track>()
        val cursor: Cursor? = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val isMusicColumn = c.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC)
            val dateAddedColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (c.moveToNext()) {
                if (limit != null && tracks.size >= limit) break

                if (isMusicColumn != -1) {
                    val isMusic = c.getInt(isMusicColumn)
                    if (isMusic == 0) continue
                }

                val rawDuration = c.getLong(durationColumn)
                if (rawDuration < 5000L) continue

                val rawId = c.getLong(idColumn)
                val rawTitle = c.getString(titleColumn) ?: "Pista desconocida"
                val rawArtist = c.getString(artistColumn) ?: "<unknown>"
                val rawArtistId = c.getString(artistIdColumn) ?: "unknown_artist"
                val rawAlbum = c.getString(albumColumn)
                val rawAlbumId = c.getLong(albumIdColumn)
                val rawAdded = c.getLong(dateAddedColumn) * 1000L

                val artworkUri = if (rawAlbumId > 0) {
                    ContentUris.withAppendedId(ALBUM_ART_URI, rawAlbumId).toString()
                } else null

                tracks.add(
                    Track(
                        id = "$ID_PREFIX$rawId",
                        sourceId = SOURCE_ID,
                        title = rawTitle,
                        artistId = rawArtistId,
                        artistName = if (rawArtist == "<unknown>") "Artista desconocido" else rawArtist,
                        albumId = if (rawAlbumId > 0) rawAlbumId.toString() else null,
                        albumName = if (rawAlbum == "<unknown>") null else rawAlbum,
                        durationMs = rawDuration,
                        artworkUrl = artworkUri,
                        addedAt = if (rawAdded > 0) rawAdded else System.currentTimeMillis()
                    )
                )
            }
        }
        return tracks
    }

    companion object {
        const val SOURCE_ID = "local"
        const val ID_PREFIX = "local_"
        private val ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")
    }
}
