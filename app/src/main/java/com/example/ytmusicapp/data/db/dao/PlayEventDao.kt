package com.example.ytmusicapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ytmusicapp.data.db.entity.PlayEventEntity

@Dao
interface PlayEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: PlayEventEntity)

    @Query("SELECT * FROM play_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<PlayEventEntity>

    @Query("SELECT * FROM play_events WHERE videoId = :videoId ORDER BY timestamp DESC")
    suspend fun getByVideoId(videoId: String): List<PlayEventEntity>

    @Query("""
        SELECT * FROM play_events
        WHERE completionRate BETWEEN 0.10 AND 0.90
        GROUP BY videoId
        ORDER BY MAX(timestamp) DESC
        LIMIT :limit
    """)
    suspend fun getPartiallyPlayed(limit: Int): List<PlayEventEntity>

    @Query("DELETE FROM play_events WHERE timestamp < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)
}