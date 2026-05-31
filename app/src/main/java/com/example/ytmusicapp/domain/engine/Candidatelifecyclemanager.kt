package com.example.ytmusicapp.domain.engine

import com.example.ytmusicapp.data.db.dao.RecommendationCandidateDao
import com.example.ytmusicapp.data.db.dao.ShelfImpressionDao
import com.example.ytmusicapp.data.db.dao.SkipEventDao
import com.example.ytmusicapp.data.db.dao.SuppressionDao
import com.example.ytmusicapp.data.db.entity.RecommendationCandidateEntity
import com.example.ytmusicapp.data.db.entity.SuppressionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val DEGRADE_IMPRESSION_THRESHOLD  = 3
private const val DEGRADE_SCORE_MULTIPLIER      = 0.85f   // (1 - 0.15) per impression
private const val DEMOTE_IMPRESSION_THRESHOLD   = 8
private const val DEMOTE_SCORE_MULTIPLIER       = 0.40f
private const val EVICT_SCORE_THRESHOLD         = 0.10f
private const val EVICT_SUPPRESSION_MS          = 30L * 24 * 60 * 60 * 1000
private const val SCORE_ACTIVE_THRESHOLD        = 0.50f

enum class CandidateState { ACTIVE, DEGRADED, DEMOTED, EVICTED }

@Singleton
class CandidateLifecycleManager @Inject constructor(
    private val candidateDao: RecommendationCandidateDao,
    private val shelfImpressionDao: ShelfImpressionDao,
    private val skipEventDao: SkipEventDao,
    private val suppressionDao: SuppressionDao
) {

    // ── State resolution ──────────────────────────────────────────────────────

    suspend fun resolveState(
        candidate: RecommendationCandidateEntity,
        currentScore: Float,
        nowMs: Long = System.currentTimeMillis()
    ): CandidateState = withContext(Dispatchers.IO) {
        // EVICTED: sub-30s skip, "Not Interested", or score too low
        if (currentScore < EVICT_SCORE_THRESHOLD) return@withContext CandidateState.EVICTED

        val recentSkips = skipEventDao.getByVideoId(candidate.videoId)
        val hasHardSkip = recentSkips.any { it.wasUnder30s }
        if (hasHardSkip) return@withContext CandidateState.EVICTED

        val isSuppressed = suppressionDao.isSuppressed(candidate.videoId, nowMs) > 0
        if (isSuppressed) return@withContext CandidateState.EVICTED

        // impression count
        val totals = shelfImpressionDao.getTotals(candidate.videoId)
        val impressions = totals?.totalImpressions ?: 0

        return@withContext when {
            impressions >= DEMOTE_IMPRESSION_THRESHOLD -> CandidateState.DEMOTED
            impressions >= DEGRADE_IMPRESSION_THRESHOLD -> CandidateState.DEGRADED
            currentScore >= SCORE_ACTIVE_THRESHOLD -> CandidateState.ACTIVE
            else -> CandidateState.DEGRADED
        }
    }

    // ── Score adjustment by lifecycle state ───────────────────────────────────

    fun adjustScore(baseScore: Float, state: CandidateState, impressionCount: Int): Float {
        return when (state) {
            CandidateState.ACTIVE  -> baseScore
            CandidateState.DEGRADED -> {
                val excess = (impressionCount - DEGRADE_IMPRESSION_THRESHOLD).coerceAtLeast(0)
                var adjusted = baseScore
                repeat(excess + 1) { adjusted *= DEGRADE_SCORE_MULTIPLIER }
                adjusted
            }
            CandidateState.DEMOTED  -> baseScore * DEMOTE_SCORE_MULTIPLIER
            CandidateState.EVICTED  -> 0f
        }
    }

    // ── Eviction ──────────────────────────────────────────────────────────────

    suspend fun evict(
        candidate: RecommendationCandidateEntity,
        nowMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        suppressionDao.insert(
            SuppressionEntity(
                videoId = candidate.videoId,
                suppressedUntilMs = nowMs + EVICT_SUPPRESSION_MS
            )
        )
    }

    suspend fun markNotInterested(
        candidate: RecommendationCandidateEntity,
        nowMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        evict(candidate, nowMs)
    }

    // ── Batch processing ──────────────────────────────────────────────────────

    suspend fun filterActiveCandidates(
        candidates: List<RecommendationCandidateEntity>,
        scoreMap: Map<String, Float>,
        nowMs: Long = System.currentTimeMillis()
    ): List<Pair<RecommendationCandidateEntity, Float>> = withContext(Dispatchers.IO) {
        candidates.mapNotNull { candidate ->
            val baseScore = scoreMap[candidate.videoId] ?: 0f
            val state = resolveState(candidate, baseScore, nowMs)
            if (state == CandidateState.EVICTED) return@mapNotNull null

            val totals = shelfImpressionDao.getTotals(candidate.videoId)
            val impressions = totals?.totalImpressions ?: 0
            val adjustedScore = adjustScore(baseScore, state, impressions)

            candidate to adjustedScore
        }.sortedByDescending { it.second }
    }
}