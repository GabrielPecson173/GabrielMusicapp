package com.example.ytmusicapp.data.repository

import com.example.ytmusicapp.data.db.dao.QueueDao
import com.example.ytmusicapp.data.db.dao.TrackDao
import com.example.ytmusicapp.data.db.entity.QueueEntity
import com.example.ytmusicapp.data.db.entity.TrackEntity
import com.example.ytmusicapp.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import com.example.ytmusicapp.domain.usecase.SearchMusicUseCase

@Singleton
class QueueRepository @Inject constructor(
    private val queueDao: QueueDao,
    private val trackDao: TrackDao,
    private val searchMusicUseCase: SearchMusicUseCase
) {
    private var currentSessionId: String = UUID.randomUUID().toString()

    fun observeQueue(): Flow<List<Track>> =
        queueDao.observeQueue().map { entities ->
            entities.map { it.queueEntityToTrack() }
        }

    suspend fun getQueue(): List<Track> =
        queueDao.getQueue().map { it.queueEntityToTrack() }

    suspend fun getCurrentTrack(): Track? =
        queueDao.getCurrentItem()?.queueEntityToTrack()

    suspend fun replaceQueue(tracks: List<Track>, currentIndex: Int = 0) {
        val entities = tracks.mapIndexed { index, track ->
            track.toQueueEntity(
                index = index,
                isCurrent = index == currentIndex,
                sessionId = currentSessionId
            )
        }
        queueDao.replaceQueue(entities)
    }

    suspend fun appendToQueue(tracks: List<Track>) {
        val existing = queueDao.getQueue()
        val startIndex = (existing.maxOfOrNull { it.queueIndex } ?: -1) + 1
        val entities = tracks.mapIndexed { offset, track ->
            track.toQueueEntity(
                index = startIndex + offset,
                isCurrent = false,
                sessionId = currentSessionId
            )
        }
        queueDao.insertAll(entities)
    }

    suspend fun removeFromQueue(mediaId: String) =
        queueDao.removeItem(mediaId)

    suspend fun setCurrentItem(mediaId: String) =
        queueDao.setCurrentItem(mediaId)

    suspend fun clearQueue() =
        queueDao.clearQueue()

    fun newSession() {
        currentSessionId = UUID.randomUUID().toString()
    }

    fun currentSessionId(): String = currentSessionId

    suspend fun generateRelatedTracks(seedTrack: Track, limit: Int = 20): List<Track> {
        val queries = listOf(
            "${seedTrack.artist} mix",
            "songs like ${seedTrack.artist}",
            "similar to ${seedTrack.artist}"
        )
        val results = queries.flatMap { query ->
            try { searchMusicUseCase(query) } catch (_: Exception) { emptyList() }
        }
        return results
            .filter { it.id != seedTrack.id }
            .filter { !it.title.contains(seedTrack.title, ignoreCase = true) } // drop same-title variants
            .distinctBy { it.id }
            .shuffled()
            .take(limit)
    }

    private fun Track.toFeatureVector(): FloatArray {
        val artistScore = artist.hashCode().toFloat() / Int.MAX_VALUE
        val durationBucket = (durationMs / 30_000L).toFloat()
        return floatArrayOf(artistScore, durationBucket)
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val mag = sqrt(normA) * sqrt(normB)
        return if (mag == 0f) 0f else dot / mag
    }

    private fun Track.toQueueEntity(
        index: Int,
        isCurrent: Boolean,
        sessionId: String
    ) = QueueEntity(
        mediaId = id,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        streamUrl = "",
        queueIndex = index,
        isCurrentItem = isCurrent,
        durationMs = durationMs,
        sessionId = sessionId,
        addedAtMs = System.currentTimeMillis()
    )

    private fun QueueEntity.queueEntityToTrack() = Track(
        id = mediaId,
        title = title,
        artist = artist,
        album = "",
        thumbnailUrl = thumbnailUrl ?: "",
        durationMs = durationMs,
        isDownloaded = false
    )

    private fun TrackEntity.trackEntityToTrack() = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        thumbnailUrl = thumbnailUrl,
        durationMs = durationMs,
        isDownloaded = isDownloaded
    )

    suspend fun saveTracksToLibrary(tracks: List<Track>) {
        val entities = tracks.map {
            TrackEntity(
                id = it.id,
                title = it.title,
                artist = it.artist,
                album = it.album,
                thumbnailUrl = it.thumbnailUrl,
                durationMs = it.durationMs,
                isDownloaded = false
            )
        }
        trackDao.insertAll(entities)
    }
}