package com.example.ytmusicapp.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ytmusicapp.data.db.entity.ShelfImpressionEntity

@Dao
interface ShelfImpressionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(impression: ShelfImpressionEntity)

    @Query("SELECT * FROM shelf_impressions WHERE sectionId = :sectionId ORDER BY timestamp DESC")
    suspend fun getBySectionId(sectionId: String): List<ShelfImpressionEntity>

    @Query("""
        SELECT SUM(impressionCount) AS totalImpressions, SUM(clickCount) AS totalClicks
        FROM shelf_impressions WHERE sectionId = :sectionId
    """)
    suspend fun getTotals(sectionId: String): ShelfTotals?

    @Query("DELETE FROM shelf_impressions WHERE timestamp < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)
}

data class ShelfTotals(
    @ColumnInfo(name = "totalImpressions") val totalImpressions: Int,
    @ColumnInfo(name = "totalClicks") val totalClicks: Int
)