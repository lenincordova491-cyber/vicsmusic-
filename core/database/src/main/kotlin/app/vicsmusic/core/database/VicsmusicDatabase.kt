package app.vicsmusic.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import app.vicsmusic.core.database.dao.AlbumDao
import app.vicsmusic.core.database.dao.ArtistDao
import app.vicsmusic.core.database.dao.DownloadDao
import app.vicsmusic.core.database.dao.LyricsDao
import app.vicsmusic.core.database.dao.PlayHistoryDao
import app.vicsmusic.core.database.dao.PlaylistDao
import app.vicsmusic.core.database.dao.QueueDao
import app.vicsmusic.core.database.dao.SearchHistoryDao
import app.vicsmusic.core.database.dao.TrackDao
import app.vicsmusic.core.database.entity.AlbumEntity
import app.vicsmusic.core.database.entity.ArtistEntity
import app.vicsmusic.core.database.entity.DownloadEntity
import app.vicsmusic.core.database.entity.LyricsCacheEntity
import app.vicsmusic.core.database.entity.PlayHistoryEntity
import app.vicsmusic.core.database.entity.PlaylistEntity
import app.vicsmusic.core.database.entity.PlaylistTrackEntity
import app.vicsmusic.core.database.entity.QueueEntity
import app.vicsmusic.core.database.entity.SearchHistoryEntity
import app.vicsmusic.core.database.entity.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PlayHistoryEntity::class,
        SearchHistoryEntity::class,
        DownloadEntity::class,
        QueueEntity::class,
        LyricsCacheEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class VicsmusicDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun queueDao(): QueueDao
    abstract fun lyricsDao(): LyricsDao
}
