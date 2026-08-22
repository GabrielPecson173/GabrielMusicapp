package com.example.ytmusicapp.domain.engine

import android.util.Log
import com.example.ytmusicapp.BuildConfig
import com.example.ytmusicapp.data.db.dao.PlayEventDao
import com.example.ytmusicapp.data.db.dao.RecommendationCandidateDao
import com.example.ytmusicapp.data.db.dao.SearchEventDao
import com.example.ytmusicapp.data.db.dao.SkipEventDao
import com.example.ytmusicapp.data.db.dao.SuppressionDao
import com.example.ytmusicapp.data.db.entity.RecommendationCandidateEntity
import com.example.ytmusicapp.data.util.ThumbnailResolver
import com.example.ytmusicapp.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SmartQueueEngine"

// How many of the globally top-popularity candidates to scan before relevance-gating.
private const val RETRIEVAL_POOL_SCAN_LIMIT = 800
// Size of the relevance-gated set that personal scoring/taste/lifecycle/MMR run over — this is the
// retrieval stage's job: exclude seed-irrelevant candidates *before* personalization can promote them.
private const val RETRIEVAL_TOP_N = 150
// Below this many same-artist candidates, Artist Radio broadens beyond the artist's own pool.
private const val MIN_ARTIST_POOL = 8
private const val MS_48H = 172_800_000L

private const val TASTE_ARTIST_BONUS = 2.0f
private const val TASTE_GENRE_BONUS = 1.0f

// Context-aware artist-repetition caps (MMRDiversifier's own MAX_SAME_ARTIST=2 stays the strict
// Autoplay/Home-feed baseline). Radio contexts (track- or artist-seeded) get a step looser since
// the seed itself is already a deliberate user choice; Artist Radio's own seed artist specifically
// gets meaningfully more, since "give me more of this artist" is the entire point of that action —
// without this, MMR would throw away most of a 100%-same-artist candidate pool down to 2 tracks.
// These are starting points, not derived constants — worth revisiting after real usage.
private const val EXPLORATORY_MAX_SAME_ARTIST = MAX_SAME_ARTIST + 1   // 3: Song Radio / Artist Radio, non-seed artists
private const val SEED_ARTIST_MAX_SAME_ARTIST = 6                     // Artist Radio's own seed artist
private const val TASTE_AFFINITY_ARTIST_CAP_BONUS = 1                  // high-affinity artists loosen slightly too

/**
 * Wires the existing V0 recommendation intelligence (TrackScoringEngine, TasteProfileEngine,
 * TfIdfEngine, MMRDiversifier, CandidateLifecycleManager, EpsilonGreedyAllocator — previously only
 * consumed by HomeFeedRepository for the Home feed) into a seed-scoped pipeline for Queue, Autoplay,
 * Song Radio and Artist Radio.
 *
 * Pipeline: hard filter -> TF-IDF relevance RETRIEVAL (gate) -> personal/taste RANKING (within the
 * gated set only, so a strongly-liked but seed-irrelevant artist can never crowd out the seed) ->
 * lifecycle degrade/evict -> MMR diversification -> (Radio only) epsilon-greedy discovery slice.
 *
 * Callers (GenerateQueueUseCase) are responsible for topping up thin results with the legacy
 * QueueRepository.generateRelatedTracks live-search fallback — this engine returns fewer than
 * [generateForTrack]'s/[generateForArtist]'s requested limit when the local candidate pool is too
 * thin (cold start / brand-new artist), it does not fall back to network search itself.
 */
@Singleton
class SmartQueueEngine @Inject constructor(
    private val candidateDao: RecommendationCandidateDao,
    private val playEventDao: PlayEventDao,
    private val skipEventDao: SkipEventDao,
    private val searchEventDao: SearchEventDao,
    private val suppressionDao: SuppressionDao,
    private val trackScoringEngine: TrackScoringEngine,
    private val tasteProfileEngine: TasteProfileEngine,
    private val tfIdfEngine: TfIdfEngine,
    private val mmrDiversifier: MMRDiversifier,
    private val candidateLifecycleManager: CandidateLifecycleManager,
    private val epsilonGreedyAllocator: EpsilonGreedyAllocator
) {

    suspend fun generateForTrack(
        seedTrack: Track,
        excludeIds: Set<String>,
        limit: Int,
        exploratory: Boolean
    ): List<Track> = withContext(Dispatchers.IO) {
        val seedText = "${seedTrack.title} ${seedTrack.artist} ${seedTrack.album}"
        val pool = candidateDao.getTopCandidates(RETRIEVAL_POOL_SCAN_LIMIT)
        rankAndDiversify(pool, seedText, excludeIds + seedTrack.id, limit, exploratory)
    }

    suspend fun generateForArtist(
        artistId: String,
        artistName: String,
        excludeIds: Set<String>,
        limit: Int,
        exploratory: Boolean
    ): List<Track> = withContext(Dispatchers.IO) {
        val ownArtist = candidateDao.getByArtistId(artistId)

        // Artist Radio today is artist-seeded recommendation over LOCAL data, not genuine artist
        // similarity. When the same-artist pool is thin we broaden by TF-IDF text relevance against
        // the artist's name over the full candidate pool — that's a text-relevance operation, not an
        // artist-similarity model. Future extension point: an Artist Similarity Provider (e.g.
        // MusicBrainz artist relations or an external similarity service) plugged in as an additional
        // pool source here.
        val pool = if (ownArtist.size >= MIN_ARTIST_POOL) {
            ownArtist
        } else {
            (ownArtist + candidateDao.getTopCandidates(RETRIEVAL_POOL_SCAN_LIMIT)).distinctBy { it.videoId }
        }
        rankAndDiversify(pool, artistName, excludeIds, limit, exploratory, seedArtistId = artistId)
    }

    private suspend fun rankAndDiversify(
        pool: List<RecommendationCandidateEntity>,
        seedText: String,
        excludeIds: Set<String>,
        limit: Int,
        exploratory: Boolean,
        seedArtistId: String? = null
    ): List<Track> {
        if (pool.isEmpty() || limit <= 0) return emptyList()
        val nowMs = System.currentTimeMillis()

        val notExcluded = pool.filter { it.videoId !in excludeIds }
        val notSuppressed = notExcluded.filter { suppressionDao.isSuppressed(it.videoId, nowMs) == 0 }
        if (notSuppressed.isEmpty()) return emptyList()

        // ── RETRIEVAL: gate by relevance to the seed before personalization ever sees the pool.
        // TfIdfEngine holds mutable singleton index state shared with HomeFeedRepository's own
        // buildIndex calls; like HomeFeedRepository already does per-build, we rebuild it for our own
        // pass. A same-instant race with a Home feed rebuild is a rare, non-crashing edge case
        // (dimension mismatches are handled defensively by TfIdfEngine.cosineSimilarity) rather than a
        // correctness issue worth new synchronization infrastructure for in this pass.
        tfIdfEngine.buildIndex(notSuppressed)
        val gated = tfIdfEngine.rankCandidates(seedText, notSuppressed)
            .take(RETRIEVAL_TOP_N)
            .map { it.first }
        if (gated.isEmpty()) return emptyList()

        // ── RANKING: personal affinity/completion/search + taste-cluster bonus, only within the
        // relevance-gated set — a favorite artist irrelevant to the seed was already excluded above.
        val recentPlays = playEventDao.getRecent(200)
        val recentSkips = skipEventDao.getSince(nowMs - MS_48H)
        val recentSearchTokens = searchEventDao.getSince(nowMs - MS_48H)
            .flatMap { it.queryString.lowercase().split(Regex("\\s+")) }
        val favoriteIds = candidateDao.getFavorites().map { it.videoId }.toSet()
        val artistMap = gated.associate { it.videoId to it.artistId }
        val genreMap = gated.associate { it.videoId to it.genre }

        val personalScores = trackScoringEngine.scoreAll(
            candidates = gated,
            recentPlays = recentPlays,
            recentSkips = recentSkips,
            artistMap = artistMap,
            recentSearchTokens = recentSearchTokens,
            nowMs = nowMs
        )

        val tasteProfile = tasteProfileEngine.buildProfile(
            playEvents = recentPlays,
            skipEvents = recentSkips,
            artistMap = artistMap,
            genreMap = genreMap,
            favoriteVideoIds = favoriteIds,
            nowMs = nowMs
        )
        val topArtists = tasteProfile.topArtists.toSet()
        val topGenres = tasteProfile.topGenres.toSet()

        val combinedScores = gated.associate { c ->
            var score = personalScores[c.videoId] ?: 0f
            if (c.artistId in topArtists) score += TASTE_ARTIST_BONUS
            if (c.genre.isNotEmpty() && c.genre in topGenres) score += TASTE_GENRE_BONUS
            c.videoId to score
        }

        // ── LIFECYCLE: degrade/evict by skip + suppression history (impression-based demotion stays
        // inert until the app logs per-track impressions somewhere — today only Home-shelf-level
        // impressions are logged, which this reads by videoId and will simply find none of).
        val lifecycleAdjusted = candidateLifecycleManager.filterActiveCandidates(gated, combinedScores, nowMs)
        if (lifecycleAdjusted.isEmpty()) return emptyList()

        // ── MMR diversification. globalAssigned is shared singleton state with HomeFeedRepository's
        // shelf-building pass; reset it for our own pass the same way HomeFeedRepository resets it at
        // the start of every buildFeed() — same tradeoff, just a second caller of an existing pattern.
        mmrDiversifier.resetSession()
        val embeddingMap = gated.associate { it.videoId to tfIdfEngine.embedText(it.metadataTokens) }
        val exploitLimit = if (exploratory) (limit * 0.85f).toInt().coerceAtLeast(1) else limit

        // Context-aware artist cap: Autoplay stays strict; Radio (track- or artist-seeded) loosens
        // a step; a high-affinity artist loosens slightly further; Artist Radio's own seed artist
        // gets the biggest allowance, specifically for that one artist — not a global bump that
        // would also loosen the cap for every unrelated artist in the pool.
        val baseCap = if (exploratory) EXPLORATORY_MAX_SAME_ARTIST else MAX_SAME_ARTIST
        val artistCapOverrides = buildMap {
            topArtists.forEach { put(it, baseCap + TASTE_AFFINITY_ARTIST_CAP_BONUS) }
            if (seedArtistId != null) put(seedArtistId, SEED_ARTIST_MAX_SAME_ARTIST)
        }
        val diversified = mmrDiversifier.diversify(
            lifecycleAdjusted, exploitLimit, embeddingMap, baseCap, artistCapOverrides
        )

        val finalSelection = if (exploratory) {
            val exploitVideoIds = diversified.map { it.videoId }.toSet()
            val discoverPool = lifecycleAdjusted.map { it.first }.filter { it.videoId !in exploitVideoIds }
            val exploitPairs = diversified.map { it to (combinedScores[it.videoId] ?: 0f) }
            // epsilon = null defers to EpsilonGreedyAllocator's own existing EPSILON_MIN/MAX policy
            // rather than a new hardcoded exploration percentage.
            epsilonGreedyAllocator.allocate(
                exploitPool = exploitPairs,
                discoverPool = discoverPool,
                limit = limit,
                epsilon = null
            )
        } else {
            diversified
        }

        if (BuildConfig.DEBUG) {
            finalSelection.forEachIndexed { index, c ->
                Log.d(
                    TAG,
                    "#$index ${c.title} by ${c.artistName} — personal=${personalScores[c.videoId]} " +
                        "combined=${combinedScores[c.videoId]}"
                )
            }
        }

        return finalSelection.map { it.toTrack() }
    }

    private fun RecommendationCandidateEntity.toTrack() = Track(
        id = videoId,
        title = title,
        artist = artistName,
        album = albumName,
        // Starting point only — YoutubeThumbnailInterceptor verifies and falls back at render
        // time, so this doesn't need to hedge down to a low-res tier itself.
        thumbnailUrl = thumbnailUrl.ifBlank { ThumbnailResolver.fromVideoId(videoId) },
        durationMs = durationMs,
        isDownloaded = false
    )
}
