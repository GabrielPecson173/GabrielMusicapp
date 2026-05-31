package com.example.ytmusicapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ytmusicapp.data.db.entity.SuppressionEntity

@Dao
interface SuppressionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SuppressionEntity)

    @Query("SELECT COUNT(*) FROM suppression_list WHERE videoId = :videoId AND suppressedUntilMs > :nowMs")
    suspend fun isSuppressed(videoId: String, nowMs: Long): Int

    @Query("DELETE FROM suppression_list WHERE suppressedUntilMs <= :nowMs")
    suspend fun pruneExpired(nowMs: Long)

    @Query("DELETE FROM suppression_list WHERE videoId = :videoId")
    suspend fun remove(videoId: String)
}