package com.example.ytmusicapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ytmusicapp.data.db.dao.PlayEventDao
import com.example.ytmusicapp.data.db.dao.RecommendationCandidateDao
import com.example.ytmusicapp.data.repository.HomeFeedRepository
import com.example.ytmusicapp.data.repository.RecommendationRepository
import com.example.ytmusicapp.domain.engine.SimilarityMatrixCache
import com.example.ytmusicapp.domain.engine.TrackScoringEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private const val KEY_SEED_VIDEO_ID = "seed_video_id"
private const val RELATED_FETCH_LIMIT = 5   // top N recently played → fetch related each

@HiltWorker
class FeedRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val homeFeedRepository: HomeFeedRepository,
    private val recommendationRepository: RecommendationRepository,
    private val candidateDao: RecommendationCandidateDao,
    private val playEventDao: PlayEventDao,
    private val scoringEngine: TrackScoringEngine,
    private val similarityMatrixCache: SimilarityMatrixCache
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            fetchFreshCandidates()
            rescoreCandidatePool()
            clearSimilarityCache()
            triggerFeedRebuild()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("Tier2Worker", "Feed refresh failed", e)
            Result.retry()
        }
    }

    // ── 1. Fetch fresh candidates via getRelatedTracks ────────────────────────
    // Top N recently played seeds → ingest their related tracks into candidate pool

    private suspend fun fetchFreshCandidates() {
        val recentPlays = playEventDao.getRecent(RELATED_FETCH_LIMIT)
        recentPlays.forEach { event ->
            try {
                recommendationRepository.fetchAndIngestRelatedTracks(event.videoId)
            } catch (_: Exception) {
                // one seed failing must not abort others
            }
        }
    }

    // ── 2. Re-score entire candidate pool ────────────────────────────────────

    private suspend fun rescoreCandidatePool() {
        val nowMs       = System.currentTimeMillis()
        val candidates  = candidateDao.getTopCandidates(2_000)
        val recentPlays = playEventDao.getRecent(500)
        val artistMap   = candidates.associate { it.videoId to it.artistId }

        val playsByVideoId  = recentPlays.groupBy { it.videoId }
        val playsByArtistId = recentPlays.groupBy { artistMap[it.videoId] ?: "" }

        candidates.forEach { candidate ->
            val ctx = TrackScoringEngine.ScoringContext(
                candidate          = candidate,
                playEvents         = playsByVideoId[candidate.videoId] ?: emptyList(),
                skipEvents         = emptyList(),
                artistPlayEvents   = playsByArtistId[candidate.artistId] ?: emptyList(),
                artistSkipEvents   = emptyList(),
                recentSearchTokens = emptyList(),
                nowMs              = nowMs
            )
            // Score computed — HomeFeedRepository re-reads from DB on next feedFlow emit
            scoringEngine.score(ctx)
        }
    }

    // ── 3. Clear similarity cache — stale after candidate pool changes ────────

    private fun clearSimilarityCache() {
        similarityMatrixCache.clear()
    }

    // ── 4. Trigger HomeFeedRepository to re-emit updated feed ────────────────

    private suspend fun triggerFeedRebuild() {
        homeFeedRepository.refresh()
    }
}