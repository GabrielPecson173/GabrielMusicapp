package com.example.ytmusicapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ytmusicapp.data.db.entity.SkipEventEntity

@Dao
interface SkipEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: SkipEventEntity)

    @Query("SELECT * FROM skip_events WHERE timestamp >= :sinceMs ORDER BY timestamp DESC")
    suspend fun getSince(sinceMs: Long): List<SkipEventEntity>

    @Query("SELECT * FROM skip_events WHERE videoId = :videoId ORDER BY timestamp DESC")
    suspend fun getByVideoId(videoId: String): List<SkipEventEntity>

    @Query("DELETE FROM skip_events WHERE timestamp < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)
}