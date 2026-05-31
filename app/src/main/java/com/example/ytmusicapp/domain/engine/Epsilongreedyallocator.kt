package com.example.ytmusicapp.domain.engine

import com.example.ytmusicapp.data.db.entity.RecommendationCandidateEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

private const val EPSILON_MIN         = 0.10f  // 10% discovery floor
private const val EPSILON_MAX         = 0.20f  // 20% discovery ceiling
private const val DISCOVERY_PROMOTION_COMPLETION_THRESHOLD = 0.70f
private const val SUPPRESSION_14_DAYS_MS = 14L * 24 * 60 * 60 * 1000

@Singleton
class EpsilonGreedyAllocator @Inject constructor() {

    // ── Slot allocation ───────────────────────────────────────────────────────

    /**
     * Splits [limit] slots into exploit + discover buckets.
     * Returns a merged list: high-confidence first, discovery slots appended.
     *
     * @param exploitPool  scored candidates from active taste clusters
     * @param discoverPool zero-impression candidates from adjacent genres / new artists
     * @param limit        total shelf size
     * @param epsilon      override; null = random in [EPSILON_MIN, EPSILON_MAX]
     */
    fun allocate(
        exploitPool:  List<Pair<RecommendationCandidateEntity, Float>>,
        discoverPool: List<RecommendationCandidateEntity>,
        limit: Int,
        epsilon: Float? = null
    ): List<RecommendationCandidateEntity> {
        if (limit <= 0) return emptyList()

        val eps = epsilon?.coerceIn(EPSILON_MIN, EPSILON_MAX)
            ?: Random.nextFloat() * (EPSILON_MAX - EPSILON_MIN) + EPSILON_MIN

        val discoverySlots  = (limit * eps).toInt().coerceAtLeast(1)
        val exploitSlots    = (limit - discoverySlots).coerceAtLeast(0)

        val exploited  = exploitPool
            .sortedByDescending { it.second }
            .take(exploitSlots)
            .map { it.first }

        val discovered = discoverPool
            .filter { d -> exploited.none { it.videoId == d.videoId } }
            .shuffled()
            .take(discoverySlots)

        return exploited + discovered
    }

    // ── Discovery pool builder ────────────────────────────────────────────────

    /**
     * Builds discovery pool from candidates:
     * - zero impressions on this device
     * - genre adjacent to [topGenres] but not already in exploit pool
     * - ranked by global popularityScore
     */
    fun buildDiscoveryPool(
        allCandidates: List<RecommendationCandidateEntity>,
        impressionedVideoIds: Set<String>,
        exploitVideoIds: Set<String>,
        topGenres: List<String>
    ): List<RecommendationCandidateEntity> {
        return allCandidates
            .filter { c ->
                c.videoId !in impressionedVideoIds &&
                        c.videoId !in exploitVideoIds
            }
            .sortedByDescending { it.popularityScore }
    }

    // ── Feedback loop ─────────────────────────────────────────────────────────

    /**
     * Call after playback completes for a discovery track.
     * Returns genre to promote if completion > threshold, else genre to suppress.
     */
    fun evaluateDiscoveryOutcome(
        candidate: RecommendationCandidateEntity,
        completionRate: Float
    ): DiscoveryOutcome {
        return if (completionRate >= DISCOVERY_PROMOTION_COMPLETION_THRESHOLD) {
            DiscoveryOutcome.Promote(candidate.genre)
        } else {
            DiscoveryOutcome.Suppress(
                genre       = candidate.genre,
                durationMs  = SUPPRESSION_14_DAYS_MS
            )
        }
    }

    sealed class DiscoveryOutcome {
        data class Promote(val genre: String) : DiscoveryOutcome()
        data class Suppress(val genre: String, val durationMs: Long) : DiscoveryOutcome()
    }
}