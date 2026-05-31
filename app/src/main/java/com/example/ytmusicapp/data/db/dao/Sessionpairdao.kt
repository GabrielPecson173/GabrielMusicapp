package com.example.ytmusicapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ytmusicapp.data.db.entity.SessionPairEntity

@Dao
interface SessionPairDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pair: SessionPairEntity)

    @Query("""
        SELECT * FROM session_pairs
        WHERE (videoIdA = :videoId OR videoIdB = :videoId)
        AND sessionTimestamp >= :sinceMs
        ORDER BY pairWeight DESC
    """)
    suspend fun getPairsForVideo(videoId: String, sinceMs: Long): List<SessionPairEntity>

    @Query("DELETE FROM session_pairs WHERE sessionTimestamp < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)
}