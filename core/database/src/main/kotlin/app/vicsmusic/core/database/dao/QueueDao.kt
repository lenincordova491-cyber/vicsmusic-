package app.vicsmusic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.vicsmusic.core.database.entity.QueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {
    @Query("SELECT * FROM playback_queue ORDER BY position ASC")
    fun getQueue(): Flow<List<QueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueue(items: List<QueueEntity>)

    @Query("DELETE FROM playback_queue")
    suspend fun clearQueue()
}
