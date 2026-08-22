package com.example.ytmusicapp.domain.engine

import com.example.ytmusicapp.data.db.entity.PlayEventEntity
import com.example.ytmusicapp.data.db.entity.RecommendationCandidateEntity
import com.example.ytmusicapp.data.db.entity.SkipEventEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.max

private const val WEIGHT_AFFINITY     = 0.40f
private const val WEIGHT_COMPLETION   = 0.35f
private const val WEIGHT_SEARCH       = 0.25f

private const val SKIP_UNDER_30S_MULTIPLIER = 0.20f
private const val FATIGUE_AMPLITUDE         = 10.0f
private const val FATIGUE_LAMBDA            = 0.693f   // 24hr half-life
private const val AFFINITY_LAMBDA           = 0.023f   // 30-day half-life

private const val W_COMPLETE_LISTEN  =  4.0f
private const val W_FAVORITE         =  5.0f
private const val W_PLAYLIST_ADD     =  5.0f
private const val W_REPEAT           =  4.0f
private const val W_SEARCH_CLICK     =  3.0f
private const val W_SKIP_30_90       = -2.0f
private const val W_SKIP_UNDER_30    = -5.0f

private const val MS_PER_HOUR = 3_600_000.0
private const val DEFAULT_COMPLETION = 0.50f

@Singleton
class TrackScoringEngine @Inject constructor() {

    data class ScoringContext(
        val candidate: RecommendationCandidateEntity,
        val playEvents: List<PlayEventEntity>,
        val skipEvents: List<SkipEventEntity>,
        val artistPlayEvents: List<PlayEventEntity>,   // all plays for same artistId
        val artistSkipEvents: List<SkipEventEntity>,   // all skips for same artistId
        val recentSearchTokens: List<String>,          // tokenized queries last 48hrs
        val nowMs: Long = System.currentTimeMillis()
    )

    // ── Batch scoring ─────────────────────────────────────────────────────────
    // Shared by HomeFeedRepository (shelves) and SmartQueueEngine (queue/autoplay/radio) so both
    // consumers of this engine build the same ScoringContext grouping the same way.

    fun scoreAll(
        candidates: List<RecommendationCandidateEntity>,
        recentPlays: List<PlayEventEntity>,
        recentSkips: List<SkipEventEntity>,
        artistMap: Map<String, String>,
        recentSearchTokens: List<String>,
        nowMs: Long = System.currentTimeMillis()
    ): Map<String, Float> {
        val playsByVideoId  = recentPlays.groupBy { it.videoId }
        val skipsByVideoId  = recentSkips.groupBy { it.videoId }
        val playsByArtistId = recentPlays.groupBy { artistMap[it.videoId] ?: "" }
        val skipsByArtistId = recentSkips.groupBy { artistMap[it.videoId] ?: "" }

        return candidates.associate { candidate ->
            val ctx = ScoringContext(
                candidate          = candidate,
                playEvents         = playsByVideoId[candidate.videoId] ?: emptyList(),
                skipEvents         = skipsByVideoId[candidate.videoId] ?: emptyList(),
                artistPlayEvents   = playsByArtistId[candidate.artistId] ?: emptyList(),
                artistSkipEvents   = skipsByArtistId[candidate.artistId] ?: emptyList(),
                recentSearchTokens = recentSearchTokens,
                nowMs              = nowMs
            )
            candidate.videoId to score(ctx)
        }
    }

    fun score(ctx: ScoringContext): Float {
        val affinity    = affinity(ctx)
        val completion  = completion(ctx)
        val search      = searchOverlap(ctx)

        val base = WEIGHT_AFFINITY * affinity +
                WEIGHT_COMPLETION * completion +
                WEIGHT_SEARCH * search

        val skipPenalty = skipPenalty(ctx)
        val fatigue     = fatigue(ctx)

        return (base * skipPenalty - fatigue).coerceAtLeast(0f)
    }

    // ── A(c,t) — decayed affinity ─────────────────────────────────────────────
    // Sum of weighted events for artist + genre, decayed by 30-day half-life

    private fun affinity(ctx: ScoringContext): Float {
        var sum = 0f

        ctx.artistPlayEvents.forEach { event ->
            val deltaHours = deltaHours(event.timestamp, ctx.nowMs)
            val w = if (event.isCompleted) W_COMPLETE_LISTEN else W_SKIP_30_90
            sum += w * exp((-AFFINITY_LAMBDA * deltaHours / 24).toDouble()).toFloat()
        }

        ctx.artistSkipEvents.forEach { event ->
            val deltaHours = deltaHours(event.timestamp, ctx.nowMs)
            val w = if (event.wasUnder30s) W_SKIP_UNDER_30 else W_SKIP_30_90
            sum += w * exp((-AFFINITY_LAMBDA * deltaHours / 24).toDouble()).toFloat()
        }

        // candidate-specific plays on top
        ctx.playEvents.forEach { event ->
            val deltaHours = deltaHours(event.timestamp, ctx.nowMs)
            val w = if (event.isCompleted) W_COMPLETE_LISTEN else 0f
            sum += w * exp((-AFFINITY_LAMBDA * deltaHours / 24).toDouble()).toFloat()
        }

        if (ctx.candidate.isFavorite) sum += W_FAVORITE

        return sum.coerceAtLeast(0f)
    }

    // ── C(c) — completion multiplier ─────────────────────────────────────────

    private fun completion(ctx: ScoringContext): Float {
        if (ctx.playEvents.isEmpty()) return DEFAULT_COMPLETION
        return ctx.playEvents.map { it.completionRate }.average().toFloat().coerceIn(0f, 1f)
    }

    // ── S(c) — search token overlap last 48hrs ────────────────────────────────

    private fun searchOverlap(ctx: ScoringContext): Float {
        if (ctx.recentSearchTokens.isEmpty()) return 0f
        val candidateTokens = ctx.candidate.metadataTokens
            .lowercase()
            .split(Regex("\\s+"))
            .toSet()

        return ctx.recentSearchTokens
            .map { token -> if (candidateTokens.contains(token)) 1f else 0f }
            .maxOrNull() ?: 0f
    }

    // ── Skip penalty — multiply 0.20 per sub-30s skip ────────────────────────

    private fun skipPenalty(ctx: ScoringContext): Float {
        val under30sCount = ctx.skipEvents.count { it.wasUnder30s }
        if (under30sCount == 0) return 1f
        var penalty = 1f
        repeat(under30sCount) { penalty *= SKIP_UNDER_30S_MULTIPLIER }
        return penalty
    }

    // ── P_fatigue — 24hr half-life fatigue penalty ────────────────────────────

    private fun fatigue(ctx: ScoringContext): Float {
        val lastPlayMs = ctx.playEvents.maxOfOrNull { it.timestamp } ?: return 0f
        val deltaHours = deltaHours(lastPlayMs, ctx.nowMs)
        return FATIGUE_AMPLITUDE * exp((-FATIGUE_LAMBDA * deltaHours / 24).toDouble()).toFloat()
    }

    private fun deltaHours(pastMs: Long, nowMs: Long): Double =
        max(0.0, (nowMs - pastMs) / MS_PER_HOUR)
}