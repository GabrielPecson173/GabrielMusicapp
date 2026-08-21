package com.example.ytmusicapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ytmusicapp.data.db.entity.PlaybackPositionEntity

@Dao
interface PlaybackPositionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: PlaybackPositionEntity)

    @Query("SELECT * FROM playback_positions WHERE videoId = :videoId LIMIT 1")
    suspend fun get(videoId: String): PlaybackPositionEntity?

    @Query("DELETE FROM playback_positions")
    suspend fun clearAll()
}
