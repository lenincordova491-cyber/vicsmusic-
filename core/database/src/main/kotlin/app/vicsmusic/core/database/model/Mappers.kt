package app.vicsmusic.core.database.model

import app.vicsmusic.core.database.entity.AlbumEntity
import app.vicsmusic.core.database.entity.ArtistEntity
import app.vicsmusic.core.database.entity.PlaylistEntity
import app.vicsmusic.core.database.entity.TrackEntity
import app.vicsmusic.core.model.Album
import app.vicsmusic.core.model.Artist
import app.vicsmusic.core.model.Playlist
import app.vicsmusic.core.model.Track

fun TrackEntity.asExternalModel() = Track(
    id = id,
    sourceId = sourceId,
    title = title,
    artistId = artistId,
    artistName = artistName,
    albumId = albumId,
    albumName = albumName,
    durationMs = durationMs,
    artworkUrl = artworkUrl,
    isLiked = isLiked,
    addedAt = addedAt,
    lastPlayedAt = lastPlayedAt,
    playCount = playCount
)

fun Track.asEntity() = TrackEntity(
    id = id,
    sourceId = sourceId,
    title = title,
    artistId = artistId,
    artistName = artistName,
    albumId = albumId,
    albumName = albumName,
    durationMs = durationMs,
    artworkUrl = artworkUrl,
    isLiked = isLiked,
    addedAt = addedAt,
    lastPlayedAt = lastPlayedAt,
    playCount = playCount
)

fun AlbumEntity.asExternalModel() = Album(
    id = id,
    sourceId = sourceId,
    title = title,
    artistId = artistId,
    artistName = artistName,
    artworkUrl = artworkUrl,
    releaseYear = year,
    trackCount = trackCount
)

fun Album.asEntity() = AlbumEntity(
    id = id,
    sourceId = sourceId,
    title = title,
    artistId = artistId,
    artistName = artistName,
    artworkUrl = artworkUrl,
    year = releaseYear,
    trackCount = trackCount
)

fun ArtistEntity.asExternalModel() = Artist(
    id = id,
    sourceId = sourceId,
    name = name,
    imageUrl = artworkUrl
)

fun Artist.asEntity() = ArtistEntity(
    id = id,
    sourceId = sourceId,
    name = name,
    artworkUrl = imageUrl
)

fun PlaylistEntity.asExternalModel(trackCount: Int = 0, durationMs: Long = 0L) = Playlist(
    id = id,
    title = title,
    description = description,
    artworkUrl = artworkUrl,
    trackCount = trackCount,
    durationMs = durationMs,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Playlist.asEntity() = PlaylistEntity(
    id = id,
    title = title,
    description = description,
    artworkUrl = artworkUrl,
    createdAt = createdAt,
    updatedAt = updatedAt
)
