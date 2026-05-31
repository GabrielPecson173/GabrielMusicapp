package com.example.ytmusicapp.data.repository

import com.example.ytmusicapp.data.db.dao.PlayEventDao
import com.example.ytmusicapp.data.db.dao.RecommendationCandidateDao
import com.example.ytmusicapp.data.db.dao.SessionPairDao
import com.example.ytmusicapp.data.db.dao.SkipEventDao
import com.example.ytmusicapp.data.db.dao.SuppressionDao
import com.example.ytmusicapp.data.db.entity.PlayEventEntity
import com.example.ytmusicapp.data.db.entity.RecommendationCandidateEntity
import com.example.ytmusicapp.data.db.entity.SessionPairEntity
import com.example.ytmusicapp.data.db.entity.SkipEventEntity
import com.example.ytmusicapp.data.db.entity.SuppressionEntity
import com.example.ytmusicapp.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CANDIDATE_CAP = 2_000
private const val SUPPRESSION_14_DAYS_MS = 14L * 24 * 60 * 60 * 1000
private const val SUPPRESSION_30_DAYS_MS = 30L * 24 * 60 * 60 * 1000

@Singleton
class RecommendationRepository @Inject constructor(
    private val playEventDao: PlayEventDao,
    private val skipEventDao: SkipEventDao,
    private val candidateDao: RecommendationCandidateDao,
    private val suppressionDao: SuppressionDao,
    private val sessionPairDao: SessionPairDao,
    private val searchRepository: SearchRepository
) {

    // ── Playback telemetry ────────────────────────────────────────────────────

    suspend fun trackPlayback(
        videoId: String,
        playDurationMs: Long,
        totalDurationMs: Long,
        isCompleted: Boolean
    ) = withContext(Dispatchers.IO) {
        val completionRate = if (totalDurationMs > 0)
            (playDurationMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
        else 0f

        playEventDao.insert(
            PlayEventEntity(
                videoId = videoId,
                timestamp = System.currentTimeMillis(),
                playbackDurationMs = playDurationMs,
                totalDurationMs = totalDurationMs,
                completionRate = completionRate,
                isCompleted = isCompleted
            )
        )
    }

    // ── Candidate ingestion ───────────────────────────────────────────────────

    suspend fun fetchAndIngestRelatedTracks(videoId: String) =
        withContext(Dispatchers.IO) {
            val related = searchRepository.getRelatedTracks(videoId)
            val nowMs = System.currentTimeMillis()

            val entities = related.mapNotNull { track ->
                if (isSuppressed(track.id)) return@mapNotNull null
                track.toCandidateEntity()
            }

            candidateDao.insertAll(entities)
            pruneToCapIfNeeded()
        }

    private suspend fun pruneToCapIfNeeded() {
        val count = candidateDao.count()
        if (count > CANDIDATE_CAP) {
            candidateDao.pruneExcess(count - CANDIDATE_CAP)
        }
    }

    // ── Candidate queries ─────────────────────────────────────────────────────

    suspend fun getCandidates(): List<RecommendationCandidateEntity> =
        withContext(Dispatchers.IO) {
            candidateDao.getTopCandidates(CANDIDATE_CAP)
        }

    suspend fun getRecentPlays(limit: Int): List<PlayEventEntity> =
        withContext(Dispatchers.IO) {
            playEventDao.getRecent(limit)
        }

    suspend fun getRecentSkips(sinceMs: Long): List<SkipEventEntity> =
        withContext(Dispatchers.IO) {
            skipEventDao.getSince(sinceMs)
        }

    suspend fun getFavorites(): List<RecommendationCandidateEntity> =
        withContext(Dispatchers.IO) {
            candidateDao.getFavorites()
        }

    suspend fun markFavorite(videoId: String) = withContext(Dispatchers.IO) {
        val candidate = candidateDao.getByVideoId(videoId) ?: return@withContext
        candidateDao.update(candidate.copy(isFavorite = true))
    }

    // ── Suppression ───────────────────────────────────────────────────────────

    suspend fun suppressTrack(videoId: String, durationMs: Long = SUPPRESSION_30_DAYS_MS) =
        withContext(Dispatchers.IO) {
            suppressionDao.insert(
                SuppressionEntity(
                    videoId = videoId,
                    suppressedUntilMs = System.currentTimeMillis() + durationMs
                )
            )
        }

    suspend fun suppressGenre(genre: String, durationMs: Long = SUPPRESSION_14_DAYS_MS) =
        withContext(Dispatchers.IO) {
            val genreCandidates = candidateDao.getByGenre(genre)
            genreCandidates.forEach { candidate ->
                suppressionDao.insert(
                    SuppressionEntity(
                        videoId = candidate.videoId,
                        suppressedUntilMs = System.currentTimeMillis() + durationMs
                    )
                )
            }
        }

    suspend fun isSuppressed(videoId: String): Boolean =
        withContext(Dispatchers.IO) {
            suppressionDao.isSuppressed(videoId, System.currentTimeMillis()) > 0
        }

    // ── Session co-occurrence pairs ───────────────────────────────────────────

    suspend fun logSessionPair(videoIdA: String, videoIdB: String, weight: Float) =
        withContext(Dispatchers.IO) {
            sessionPairDao.insert(
                SessionPairEntity(
                    videoIdA = videoIdA,
                    videoIdB = videoIdB,
                    pairWeight = weight,
                    sessionTimestamp = System.currentTimeMillis()
                )
            )
        }

    suspend fun getSessionPairs(videoId: String): List<SessionPairEntity> =
        withContext(Dispatchers.IO) {
            val since24h = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            sessionPairDao.getPairsForVideo(videoId, since24h)
        }

    // ── Playback position save/restore ────────────────────────────────────────

    private val playbackPositions = mutableMapOf<String, Long>()

    fun savePlaybackPosition(videoId: String, positionMs: Long) {
        playbackPositions[videoId] = positionMs
    }

    fun getPlaybackPosition(videoId: String): Long =
        playbackPositions[videoId] ?: 0L

    // ── Maintenance ───────────────────────────────────────────────────────────

    suspend fun pruneStaleCandidates() = withContext(Dispatchers.IO) {
        pruneToCapIfNeeded()
        suppressionDao.pruneExpired(System.currentTimeMillis())
        val cutoff24h = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        sessionPairDao.deleteOlderThan(cutoff24h)
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun Track.toCandidateEntity() = RecommendationCandidateEntity(
        videoId = id,
        title = title,
        artistId = artist,
        artistName = artist,
        albumId = album,
        albumName = album,
        genre = "",
        durationMs = durationMs,
        popularityScore = 0f,
        metadataTokens = tokenize(title, artist, album),
        isFavorite = false
    )

    private fun tokenize(vararg fields: String): String =
        fields.flatMap { it.lowercase().split(Regex("\\s+")) }
            .filter { it.length > 2 }
            .distinct()
            .joinToString(" ")
}