package com.example.ytmusicapp.domain.engine

import com.example.ytmusicapp.data.db.entity.PlayEventEntity
import com.example.ytmusicapp.data.db.entity.SkipEventEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.sqrt

private const val LAMBDA_LONG_TERM  = 0.023f   // 30-day half-life
private const val LAMBDA_SHORT_TERM = 1.386f   // 12-hour half-life
private const val MS_PER_HOUR       = 3_600_000.0
private const val TOP_CLUSTER_RATIO = 0.50f    // keep top 50% clusters by |score|

private const val W_COMPLETE  =  4.0f
private const val W_SKIP_30_90 = -2.0f
private const val W_SKIP_U30  = -5.0f
private const val W_FAVORITE  =  5.0f

@Singleton
class TasteProfileEngine @Inject constructor() {

    data class ClusterKey(val dimension: String, val value: String)

    data class ClusterScore(
        val key: ClusterKey,
        val longTermScore: Float,
        val shortTermScore: Float
    ) {
        val combinedScore: Float get() = longTermScore + shortTermScore
    }

    data class TasteProfile(
        val clusters: List<ClusterScore>,
        val topArtists: List<String>,
        val topGenres: List<String>
    )

    data class InteractionEvent(
        val artistId: String,
        val artistName: String,
        val genre: String,
        val timestampMs: Long,
        val baseWeight: Float
    )

    // ── Public API ────────────────────────────────────────────────────────────

    fun buildProfile(
        playEvents: List<PlayEventEntity>,
        skipEvents: List<SkipEventEntity>,
        artistMap: Map<String, String>,   // videoId → artistId
        genreMap: Map<String, String>,    // videoId → genre
        favoriteVideoIds: Set<String> = emptySet(),
        nowMs: Long = System.currentTimeMillis()
    ): TasteProfile {
        val interactions = buildInteractions(
            playEvents, skipEvents, artistMap, genreMap, favoriteVideoIds
        )

        val artistClusters = scoreArtistClusters(interactions, nowMs)
        val genreClusters  = scoreGenreClusters(interactions, nowMs)

        val allClusters = (artistClusters + genreClusters)
            .pruneBottomClusters()
            .applyContrastivePolarity()

        val topArtists = allClusters
            .filter { it.key.dimension == "artist" && it.combinedScore > 0 }
            .sortedByDescending { it.combinedScore }
            .take(10)
            .map { it.key.value }

        val topGenres = allClusters
            .filter { it.key.dimension == "genre" && it.combinedScore > 0 }
            .sortedByDescending { it.combinedScore }
            .take(5)
            .map { it.key.value }

        return TasteProfile(
            clusters = allClusters,
            topArtists = topArtists,
            topGenres = topGenres
        )
    }

    fun getShortTermContext(
        playEvents: List<PlayEventEntity>,
        artistMap: Map<String, String>,
        genreMap: Map<String, String>,
        nowMs: Long = System.currentTimeMillis()
    ): List<ClusterScore> {
        val since12h = nowMs - 12L * 60 * 60 * 1000
        val recent = playEvents.filter { it.timestamp >= since12h }
        val interactions = recent.map { event ->
            InteractionEvent(
                artistId   = artistMap[event.videoId] ?: "",
                artistName = artistMap[event.videoId] ?: "",
                genre      = genreMap[event.videoId] ?: "",
                timestampMs = event.timestamp,
                baseWeight  = if (event.isCompleted) W_COMPLETE else W_SKIP_30_90
            )
        }.filter { it.artistId.isNotEmpty() }

        return scoreArtistClusters(interactions, nowMs)
            .map { it.copy(longTermScore = 0f) }
    }

    // ── Cluster scoring ───────────────────────────────────────────────────────

    private fun scoreArtistClusters(
        interactions: List<InteractionEvent>,
        nowMs: Long
    ): List<ClusterScore> {
        val grouped = interactions.groupBy { it.artistId }
        return grouped.map { (artistId, events) ->
            val key = ClusterKey("artist", artistId)
            ClusterScore(
                key            = key,
                longTermScore  = decayedSum(events, nowMs, LAMBDA_LONG_TERM),
                shortTermScore = decayedSum(events, nowMs, LAMBDA_SHORT_TERM)
            )
        }
    }

    private fun scoreGenreClusters(
        interactions: List<InteractionEvent>,
        nowMs: Long
    ): List<ClusterScore> {
        val grouped = interactions.filter { it.genre.isNotEmpty() }.groupBy { it.genre }
        return grouped.map { (genre, events) ->
            val key = ClusterKey("genre", genre)
            ClusterScore(
                key            = key,
                longTermScore  = decayedSum(events, nowMs, LAMBDA_LONG_TERM),
                shortTermScore = decayedSum(events, nowMs, LAMBDA_SHORT_TERM)
            )
        }
    }

    private fun decayedSum(
        events: List<InteractionEvent>,
        nowMs: Long,
        lambda: Float
    ): Float {
        return events.sumOf { event ->
            val deltaHours = ((nowMs - event.timestampMs) / MS_PER_HOUR).coerceAtLeast(0.0)
            val decayed = event.baseWeight * exp((-lambda * deltaHours / 24).toDouble())
            decayed
        }.toFloat()
    }

    // ── Medoid cluster representative ─────────────────────────────────────────
    // Returns the artistId/genre that is closest to the centroid of top cluster members

    fun findMedoid(clusterValues: List<String>, scoreMap: Map<String, Float>): String? {
        if (clusterValues.isEmpty()) return null
        if (clusterValues.size == 1) return clusterValues.first()

        // Use score-weighted centroid approximation:
        // medoid = value with max sum of score similarity to all others
        // Similarity here = 1 - |score_a - score_b| / max_score_range
        val scores = clusterValues.mapNotNull { v -> scoreMap[v]?.let { v to it } }
        val maxScore = scores.maxOfOrNull { it.second } ?: return clusterValues.first()
        val minScore = scores.minOfOrNull { it.second } ?: return clusterValues.first()
        val range = (maxScore - minScore).takeIf { it > 0f } ?: 1f

        return scores.maxByOrNull { (_, score) ->
            scores.sumOf { (_, other) -> (1.0 - kotlin.math.abs(score - other) / range) }
        }?.first
    }

    // ── Pruning ───────────────────────────────────────────────────────────────

    private fun List<ClusterScore>.pruneBottomClusters(): List<ClusterScore> {
        if (isEmpty()) return this
        val sorted = sortedByDescending { kotlin.math.abs(it.combinedScore) }
        val keepCount = (size * TOP_CLUSTER_RATIO).toInt().coerceAtLeast(1)
        return sorted.take(keepCount)
    }

    // ── Contrastive session polarity ──────────────────────────────────────────
    // Amplify sustained positive clusters, suppress ambivalent ones

    private fun List<ClusterScore>.applyContrastivePolarity(): List<ClusterScore> {
        val kappa = 1.0f
        return map { cluster ->
            val positive = cluster.shortTermScore.coerceAtLeast(0f)
            val negative = (-cluster.shortTermScore).coerceAtLeast(0f)
            val polarity = softmax(kappa * (positive - negative))
            cluster.copy(shortTermScore = cluster.shortTermScore * polarity)
        }
    }

    private fun softmax(x: Float): Float {
        val ex = exp(x.toDouble()).toFloat()
        return ex / (ex + 1f)
    }

    // ── Interaction builder ───────────────────────────────────────────────────

    private fun buildInteractions(
        playEvents: List<PlayEventEntity>,
        skipEvents: List<SkipEventEntity>,
        artistMap: Map<String, String>,
        genreMap: Map<String, String>,
        favoriteVideoIds: Set<String>
    ): List<InteractionEvent> {
        val plays = playEvents.mapNotNull { event ->
            val artistId = artistMap[event.videoId] ?: return@mapNotNull null
            InteractionEvent(
                artistId    = artistId,
                artistName  = artistId,
                genre       = genreMap[event.videoId] ?: "",
                timestampMs = event.timestamp,
                baseWeight  = when {
                    event.videoId in favoriteVideoIds -> W_FAVORITE
                    event.isCompleted                 -> W_COMPLETE
                    else                              -> W_SKIP_30_90
                }
            )
        }

        val skips = skipEvents.mapNotNull { event ->
            val artistId = artistMap[event.videoId] ?: return@mapNotNull null
            InteractionEvent(
                artistId    = artistId,
                artistName  = artistId,
                genre       = genreMap[event.videoId] ?: "",
                timestampMs = event.timestamp,
                baseWeight  = if (event.wasUnder30s) W_SKIP_U30 else W_SKIP_30_90
            )
        }

        return plays + skips
    }
}