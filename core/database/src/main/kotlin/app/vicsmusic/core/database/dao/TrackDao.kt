package app.vicsmusic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.vicsmusic.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE id = :id")
    fun getTrack(id: String): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    fun getTracks(ids: List<String>): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getTracksSnapshot(ids: List<String>): List<TrackEntity>

    @Transaction
    suspend fun upsertTracksPreservingHistory(incoming: List<TrackEntity>) {
        if (incoming.isEmpty()) return
        val existingMap = getTracksSnapshot(incoming.map { it.id }).associateBy { it.id }
        val merged = incoming.map { inc ->
            val existing = existingMap[inc.id]
            if (existing != null) {
                inc.copy(
                    isLiked = existing.isLiked,
                    playCount = existing.playCount,
                    lastPlayedAt = existing.lastPlayedAt,
                    addedAt = existing.addedAt
                )
            } else {
                inc
            }
        }
        upsertTracks(merged)
    }

    @Query("SELECT * FROM tracks WHERE isLiked = 1 ORDER BY addedAt DESC")
    fun getFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentlyPlayedTracks(limit: Int = 50): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artistName LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchTracks(query: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY title ASC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE sourceId = :sourceId ORDER BY title ASC")
    fun getTracksBySource(sourceId: String): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET isLiked = :isLiked WHERE id = :trackId")
    suspend fun setLiked(trackId: String, isLiked: Boolean)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :playedAt WHERE id = :trackId")
    suspend fun updatePlayCount(trackId: String, playedAt: Long)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrack(id: String)

    @Query("DELETE FROM tracks WHERE sourceId = :sourceId")
    suspend fun deleteTracksBySource(sourceId: String)
}
