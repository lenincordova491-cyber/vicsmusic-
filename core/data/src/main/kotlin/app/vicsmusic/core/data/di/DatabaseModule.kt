package app.vicsmusic.core.data.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VicsmusicDatabase {
        return Room.databaseBuilder(
            context,
            VicsmusicDatabase::class.java,
            "vicsmusic.db"
        ).fallbackToDestructiveMigration().build()
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
