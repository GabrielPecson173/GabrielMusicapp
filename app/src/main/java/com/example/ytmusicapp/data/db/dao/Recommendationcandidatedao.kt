package com.example.ytmusicapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.ytmusicapp.data.db.entity.RecommendationCandidateEntity

@Dao
interface RecommendationCandidateDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(candidate: RecommendationCandidateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(candidates: List<RecommendationCandidateEntity>)

    @Update
    suspend fun update(candidate: RecommendationCandidateEntity)

    @Query("SELECT * FROM recommendation_candidates ORDER BY popularityScore DESC LIMIT :limit")
    suspend fun getTopCandidates(limit: Int): List<RecommendationCandidateEntity>

    @Query("SELECT * FROM recommendation_candidates WHERE videoId = :videoId LIMIT 1")
    suspend fun getByVideoId(videoId: String): RecommendationCandidateEntity?

    @Query("SELECT * FROM recommendation_candidates WHERE videoId IN (:videoIds)")
    suspend fun getByVideoIds(videoIds: List<String>): List<RecommendationCandidateEntity>

    @Query("SELECT * FROM recommendation_candidates WHERE isFavorite = 1")
    suspend fun getFavorites(): List<RecommendationCandidateEntity>

    @Query("SELECT COUNT(*) FROM recommendation_candidates")
    suspend fun count(): Int

    @Query("""
        DELETE FROM recommendation_candidates WHERE id IN (
            SELECT c.id FROM recommendation_candidates c
            LEFT JOIN play_events p ON c.videoId = p.videoId
            WHERE p.videoId IS NULL AND c.isFavorite = 0
            ORDER BY c.popularityScore ASC
            LIMIT :excess
        )
    """)
    suspend fun pruneExcess(excess: Int)

    @Query("SELECT * FROM recommendation_candidates WHERE artistId = :artistId")
    suspend fun getByArtistId(artistId: String): List<RecommendationCandidateEntity>

    @Query("SELECT * FROM recommendation_candidates WHERE genre = :genre")
    suspend fun getByGenre(genre: String): List<RecommendationCandidateEntity>

    @Query("DELETE FROM recommendation_candidates WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)
}