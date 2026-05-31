package com.example.ytmusicapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.ytmusicapp.data.db.entity.HomeFeedSectionEntity

@Dao
interface HomeFeedSectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(section: HomeFeedSectionEntity)

    @Update
    suspend fun update(section: HomeFeedSectionEntity)

    @Query("SELECT * FROM home_feed_sections ORDER BY displayOrder ASC")
    suspend fun getAll(): List<HomeFeedSectionEntity>

    @Query("DELETE FROM home_feed_sections WHERE sectionId = :sectionId")
    suspend fun delete(sectionId: String)

    @Query("DELETE FROM home_feed_sections WHERE isEphemeral = 1")
    suspend fun deleteAllEphemeral()
}