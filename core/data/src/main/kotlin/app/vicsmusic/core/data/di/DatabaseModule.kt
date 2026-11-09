package app.vicsmusic.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.vicsmusic.core.database.VicsmusicDatabase
import app.vicsmusic.core.database.dao.AlbumDao
import app.vicsmusic.core.database.dao.ArtistDao
import app.vicsmusic.core.database.dao.DownloadDao
import app.vicsmusic.core.database.dao.LyricsDao
import app.vicsmusic.core.database.dao.PlayHistoryDao
import app.vicsmusic.core.database.dao.PlaylistDao
import app.vicsmusic.core.database.dao.QueueDao
import app.vicsmusic.core.database.dao.SearchHistoryDao
import app.vicsmusic.core.database.dao.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_artistName` ON `tracks` (`artistName`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_albumId` ON `tracks` (`albumId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_isLiked` ON `tracks` (`isLiked`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_lastPlayedAt` ON `tracks` (`lastPlayedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_sourceId_isLiked_lastPlayedAt` ON `tracks` (`sourceId`, `isLiked`, `lastPlayedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_history_playedAt` ON `play_history` (`playedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_history_trackId_playedAt` ON `play_history` (`trackId`, `playedAt`)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VicsmusicDatabase {
        return Room.databaseBuilder(
            context,
            VicsmusicDatabase::class.java,
            "vicsmusic.db"
        )
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideTrackDao(db: VicsmusicDatabase): TrackDao = db.trackDao()

    @Provides
    fun provideAlbumDao(db: VicsmusicDatabase): AlbumDao = db.albumDao()

    @Provides
    fun provideArtistDao(db: VicsmusicDatabase): ArtistDao = db.artistDao()

    @Provides
    fun providePlaylistDao(db: VicsmusicDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun providePlayHistoryDao(db: VicsmusicDatabase): PlayHistoryDao = db.playHistoryDao()

    @Provides
    fun provideSearchHistoryDao(db: VicsmusicDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides
    fun provideDownloadDao(db: VicsmusicDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideQueueDao(db: VicsmusicDatabase): QueueDao = db.queueDao()

    @Provides
    fun provideLyricsDao(db: VicsmusicDatabase): LyricsDao = db.lyricsDao()
}
