package com.example.ytmusicapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ytmusicapp.data.db.entity.TrackEmbeddingEntity

@Dao
interface TrackEmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: TrackEmbeddingEntity)

    @Query("SELECT * FROM track_embeddings WHERE videoId = :videoId LIMIT 1")
    suspend fun getByVideoId(videoId: String): TrackEmbeddingEntity?

    @Query("SELECT * FROM track_embeddings")
    suspend fun getAll(): List<TrackEmbeddingEntity>

    @Query("DELETE FROM track_embeddings WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)
}