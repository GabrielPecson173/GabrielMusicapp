package com.example.ytmusicapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.ytmusicapp.data.db.entity.QueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {

    @Query("SELECT * FROM playback_queue ORDER BY queueIndex ASC")
    fun observeQueue(): Flow<List<QueueEntity>>

    @Query("SELECT * FROM playback_queue ORDER BY queueIndex ASC")
    suspend fun getQueue(): List<QueueEntity>

    @Query("SELECT * FROM playback_queue WHERE isCurrentItem = 1 LIMIT 1")
    suspend fun getCurrentItem(): QueueEntity?

    @Query("SELECT * FROM playback_queue WHERE sessionId = :sessionId ORDER BY addedAtMs DESC LIMIT :limit")
    suspend fun getRecentBySession(sessionId: String, limit: Int = 10): List<QueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QueueEntity>)

    @Query("UPDATE playback_queue SET isCurrentItem = 0")
    suspend fun clearCurrentItem()

    @Query("UPDATE playback_queue SET isCurrentItem = 1 WHERE mediaId = :mediaId")
    suspend fun markCurrentItem(mediaId: String)

    @Transaction
    suspend fun setCurrentItem(mediaId: String) {
        clearCurrentItem()
        markCurrentItem(mediaId)
    }

    @Query("DELETE FROM playback_queue WHERE mediaId = :mediaId")
    suspend fun removeItem(mediaId: String)

    @Query("DELETE FROM playback_queue")
    suspend fun clearQueue()

    @Transaction
    suspend fun replaceQueue(items: List<QueueEntity>) {
        clearQueue()
        insertAll(items)
    }
}