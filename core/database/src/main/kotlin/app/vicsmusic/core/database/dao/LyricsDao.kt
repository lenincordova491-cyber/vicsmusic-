package app.vicsmusic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.vicsmusic.core.database.entity.LyricsCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics_cache WHERE trackId = :trackId")
    fun getLyrics(trackId: String): Flow<LyricsCacheEntity?>

    @Query("SELECT * FROM lyrics_cache WHERE content LIKE '%' || :query || '%' LIMIT 20")
    suspend fun searchCachedLyrics(query: String): List<LyricsCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE trackId = :trackId")
    suspend fun deleteLyrics(trackId: String)
}
