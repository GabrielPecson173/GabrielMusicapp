package com.example.ytmusicapp.data.repository

import com.example.ytmusicapp.data.db.dao.TrackDao
import com.example.ytmusicapp.data.extraction.ExtractionStrategy
import com.example.ytmusicapp.domain.model.StreamInfo
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val extractionStrategy: ExtractionStrategy
) {

    fun getAllTracks(): Flow<List<Track>> =
        trackDao.getAllTracks().map { entities ->
            entities.map { it.toDomain() }
        }

    fun getTrackById(id: String): Flow<Track?> =
        trackDao.getTrackById(id).map { it?.toDomain() }

    suspend fun saveTrack(track: Track) =
        trackDao.insert(track.toEntity())

    suspend fun deleteTrack(track: Track) =
        trackDao.delete(track.toEntity())

    suspend fun getStreamInfo(videoId: String): StreamInfo =
        extractionStrategy.getStreamUrl(videoId)

    private fun TrackEntity.toDomain() = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        thumbnailUrl = thumbnailUrl,
        durationMs = durationMs,
        isDownloaded = isDownloaded
    )

    private fun Track.toEntity() = TrackEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        thumbnailUrl = thumbnailUrl,
        durationMs = durationMs,
        isDownloaded = isDownloaded
    )
}