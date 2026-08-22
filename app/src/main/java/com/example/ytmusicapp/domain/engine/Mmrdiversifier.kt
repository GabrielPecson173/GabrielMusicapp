package com.example.ytmusicapp.domain.engine

import com.example.ytmusicapp.data.db.entity.RecommendationCandidateEntity
import javax.inject.Inject
import javax.inject.Singleton

private const val MMR_RELEVANCE_WEIGHT   = 0.55f
private const val MMR_SIMILARITY_WEIGHT  = 0.45f
// internal (not private) so GenerateQueueUseCase's lightweight post-fallback repetition guard can
// reuse the same same-artist cap instead of inventing a second threshold.
internal const val MAX_SAME_ARTIST       = 2
private const val MAX_SAME_GENRE         = 3

@Singleton
class MMRDiversifier @Inject constructor(
    private val similarityCache: SimilarityMatrixCache,
    private val embeddingProvider: TfIdfEngine
) {

    // videoIds already assigned to higher-ranked shelves — excluded from lower
    private val globalAssigned = mutableSetOf<String>()

    fun resetSession() = globalAssigned.clear()

    // ── MMR diversification for one shelf ────────────────────────────────────

    /**
     * @param maxSameArtist Base same-artist cap for this call, defaulting to the strict
     *   Home-feed-style [MAX_SAME_ARTIST]. Callers with different listening-intent contexts (e.g.
     *   Song Radio, Artist Radio) can loosen this.
     * @param artistCapOverrides Per-artist cap overrides, keyed by artistId, taking precedence
     *   over [maxSameArtist] for that specific artist only — e.g. Artist Radio's seed artist
     *   getting a much higher cap without loosening the cap for every other artist in the pool.
     */
    fun diversify(
        candidates: List<Pair<RecommendationCandidateEntity, Float>>,
        limit: Int,
        embeddingMap: Map<String, FloatArray> = emptyMap(),
        maxSameArtist: Int = MAX_SAME_ARTIST,
        artistCapOverrides: Map<String, Int> = emptyMap()
    ): List<RecommendationCandidateEntity> {
        if (candidates.isEmpty()) return emptyList()

        val selected = mutableListOf<RecommendationCandidateEntity>()
        val remaining = candidates
            .filter { (c, _) -> c.videoId !in globalAssigned }
            .toMutableList()

        val artistCount  = mutableMapOf<String, Int>()
        val genreCount   = mutableMapOf<String, Int>()

        while (selected.size < limit && remaining.isNotEmpty()) {
            val best = remaining.maxByOrNull { (candidate, score) ->
                mmrScore(
                    candidate    = candidate,
                    relevance    = score,
                    selected     = selected,
                    embeddingMap = embeddingMap
                )
            } ?: break

            remaining.remove(best)

            val (candidate, _) = best

            // categorical caps
            val artistHits = artistCount.getOrDefault(candidate.artistId, 0)
            val genreHits  = genreCount.getOrDefault(candidate.genre, 0)
            val artistCap  = artistCapOverrides[candidate.artistId] ?: maxSameArtist
            if (artistHits >= artistCap) continue
            if (candidate.genre.isNotEmpty() && genreHits >= MAX_SAME_GENRE) continue

            selected.add(candidate)
            globalAssigned.add(candidate.videoId)
            artistCount[candidate.artistId] = artistHits + 1
            if (candidate.genre.isNotEmpty()) {
                genreCount[candidate.genre] = genreHits + 1
            }
        }

        return selected
    }

    // ── MMR score ─────────────────────────────────────────────────────────────
    // MMRScore(c) = 0.55*Score(c) - 0.45*max_similarity(c, already_selected)

    private fun mmrScore(
        candidate: RecommendationCandidateEntity,
        relevance: Float,
        selected: List<RecommendationCandidateEntity>,
        embeddingMap: Map<String, FloatArray>
    ): Float {
        if (selected.isEmpty()) return MMR_RELEVANCE_WEIGHT * relevance

        val embA = embeddingMap[candidate.videoId]
        val maxSim = if (embA != null) {
            selected.maxOfOrNull { sel ->
                val embB = embeddingMap[sel.videoId]
                if (embB != null) {
                    similarityCache.getOrCompute(candidate.videoId, embA, sel.videoId, embB)
                } else {
                    metadataSimilarity(candidate, sel)
                }
            } ?: 0f
        } else {
            selected.maxOfOrNull { metadataSimilarity(candidate, it) } ?: 0f
        }

        return MMR_RELEVANCE_WEIGHT * relevance - MMR_SIMILARITY_WEIGHT * maxSim
    }

    // ── Fallback similarity — shared artist or genre ──────────────────────────

    private fun metadataSimilarity(
        a: RecommendationCandidateEntity,
        b: RecommendationCandidateEntity
    ): Float {
        var sim = 0f
        if (a.artistId == b.artistId) sim += 0.6f
        if (a.genre.isNotEmpty() && a.genre == b.genre) sim += 0.4f
        return sim.coerceAtMost(1f)
    }
}