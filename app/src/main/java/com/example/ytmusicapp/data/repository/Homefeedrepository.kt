package com.example.ytmusicapp.data.repository

import com.example.ytmusicapp.data.db.dao.PlayEventDao
import com.example.ytmusicapp.data.db.dao.RecommendationCandidateDao
import com.example.ytmusicapp.data.db.dao.ShelfImpressionDao
import com.example.ytmusicapp.data.db.dao.SkipEventDao
import com.example.ytmusicapp.data.db.dao.SuppressionDao
import com.example.ytmusicapp.data.db.entity.RecommendationCandidateEntity
import com.example.ytmusicapp.data.db.entity.ShelfImpressionEntity
import com.example.ytmusicapp.domain.engine.CandidateLifecycleManager
import com.example.ytmusicapp.domain.engine.EpsilonGreedyAllocator
import com.example.ytmusicapp.domain.engine.MMRDiversifier
import com.example.ytmusicapp.domain.engine.TasteProfileEngine
import com.example.ytmusicapp.domain.engine.TfIdfEngine
import com.example.ytmusicapp.domain.engine.TrackScoringEngine
import com.example.ytmusicapp.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private const val SHELF_LIMIT           = 15
private const val CANDIDATE_FETCH_LIMIT = 200
private const val MS_24H                = 86_400_000L
private const val MS_48H                = 172_800_000L
private const val MS_7D                 = 604_800_000L
private const val MS_45D               = 3_888_000_000L

private const val ALPHA = 0.30f
private const val BETA  = 0.35f
private const val GAMMA = 0.20f
private const val DELTA = 0.15f

data class HomeSection(
    val id: String,
    val title: String,
    val tracks: List<Track>,
    val isEphemeral: Boolean = false
)

@Singleton
class HomeFeedRepository @Inject constructor(
    private val playEventDao: PlayEventDao,
    private val skipEventDao: SkipEventDao,
    private val candidateDao: RecommendationCandidateDao,
    private val suppressionDao: SuppressionDao,
    private val shelfImpressionDao: ShelfImpressionDao,
    private val searchRepository: SearchRepository,
    private val recommendationRepository: RecommendationRepository,
    private val scoringEngine: TrackScoringEngine,
    private val tasteProfileEngine: TasteProfileEngine,
    private val tfIdfEngine: TfIdfEngine,
    private val lifecycleManager: CandidateLifecycleManager,
    private val mmrDiversifier: MMRDiversifier,
    private val epsilonGreedyAllocator: EpsilonGreedyAllocator
) {
    // ── Refresh trigger ───────────────────────────────────────────────────────
    private val _refreshTrigger = MutableSharedFlow<Unit>(replay = 1)

    /**
     * Emits the full feed on subscribe, then re-emits on every refresh() call.
     * HomeViewModel collects this flow.
     */
    fun feedFlow(): Flow<List<HomeSection>> = flow {
        emit(buildFeed())
        _refreshTrigger.collect {
            emit(buildFeed())
        }
    }.flowOn(Dispatchers.IO)

    /** Pull-to-refresh + WorkManager Tier 2 entry point. */
    suspend fun refresh() {
        _refreshTrigger.emit(Unit)
    }

    /** Called by HomeViewModel when a shelf enters the viewport (>50% visible). */
    suspend fun logImpression(sectionId: String) {
        withContext(Dispatchers.IO) {
            shelfImpressionDao.insert(
                ShelfImpressionEntity(
                    sectionId       = sectionId,
                    timestamp       = System.currentTimeMillis(),
                    clickCount      = 0,
                    impressionCount = 1
                )
            )
        }
    }

    /** Called by HomeViewModel when user taps a track inside a shelf. */
    suspend fun logSectionClick(sectionId: String) {
        withContext(Dispatchers.IO) {
            shelfImpressionDao.insert(
                ShelfImpressionEntity(
                    sectionId       = sectionId,
                    timestamp       = System.currentTimeMillis(),
                    clickCount      = 1,
                    impressionCount = 0
                )
            )
        }
    }

    // ── Feed builder ──────────────────────────────────────────────────────────

    suspend fun buildFeed(
        nowMs: Long = System.currentTimeMillis()
    ): List<HomeSection> = withContext(Dispatchers.IO) {
        mmrDiversifier.resetSession()

        val candidates  = candidateDao.getTopCandidates(CANDIDATE_FETCH_LIMIT)
        val recentPlays = playEventDao.getRecent(200)
        val recentSkips = skipEventDao.getSince(nowMs - MS_48H)
        val favorites   = candidateDao.getFavorites()
        val nowHour     = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val artistMap   = candidates.associate { it.videoId to it.artistId }
        val genreMap    = candidates.associate { it.videoId to it.genre }
        val favoriteIds = favorites.map { it.videoId }.toSet()

        val tasteProfile = tasteProfileEngine.buildProfile(
            playEvents       = recentPlays,
            skipEvents       = recentSkips,
            artistMap        = artistMap,
            genreMap         = genreMap,
            favoriteVideoIds = favoriteIds,
            nowMs            = nowMs
        )

        tfIdfEngine.buildIndex(candidates)

        val scoreMap = buildScoreMap(
            candidates         = candidates,
            recentPlays        = recentPlays,
            recentSkips        = recentSkips.toList(),
            artistMap          = artistMap,
            recentSearchTokens = emptyList(),
            nowMs              = nowMs
        )

        coroutineScope {
            val continueListening  = async { shelfContinueListening(nowMs) }
            val recentlyPlayed     = async { shelfRecentlyPlayed(nowMs) }
            val becauseYouPlayed   = async { shelfBecauseYouPlayed(candidates, scoreMap, nowMs) }
            val similarToFavorites = async { shelfSimilarToFavorites(favorites, candidates, scoreMap) }
            val trendingForYou     = async { shelfTrendingForYou(candidates, tasteProfile, scoreMap) }
            val rediscover         = async { shelfRediscover(nowMs) }
            val newArtists         = async { shelfNewArtists(candidates, tasteProfile, scoreMap) }
            val recommendedAlbums  = async { shelfRecommendedAlbums(tasteProfile) }
            val nightMix           = async { shelfNightMix(candidates, scoreMap, nowHour) }
            val workoutMix         = async { shelfWorkoutMix(candidates, scoreMap) }

            val allShelves = listOfNotNull(
                continueListening.await(),
                recentlyPlayed.await(),
                becauseYouPlayed.await(),
                similarToFavorites.await(),
                trendingForYou.await(),
                rediscover.await(),
                newArtists.await(),
                recommendedAlbums.await(),
                nightMix.await(),
                workoutMix.await()
            ).filter { it.tracks.isNotEmpty() }

            android.util.Log.d("HomeFeed", "shelves built: ${allShelves.map { it.title }}")
            rankShelves(allShelves, tasteProfile, nowMs)
        }
    }

    // ── 1. Continue Listening ─────────────────────────────────────────────────

    private suspend fun shelfContinueListening(nowMs: Long): HomeSection? {
        val partial = playEventDao.getPartiallyPlayed(10)
        if (partial.isEmpty()) return null
        val tracks = partial.mapNotNull { event ->
            candidateDao.getByVideoId(event.videoId)?.toTrack()
        }
        return HomeSection("continue_listening", "Continue Listening", tracks)
    }

    // ── 2. Recently Played ────────────────────────────────────────────────────

    private suspend fun shelfRecentlyPlayed(nowMs: Long): HomeSection? {
        val recent = playEventDao.getRecent(20)
        val seen = mutableSetOf<String>()
        val tracks = recent.mapNotNull { event ->
            if (!seen.add(event.videoId)) return@mapNotNull null
            candidateDao.getByVideoId(event.videoId)?.toTrack()
        }.take(SHELF_LIMIT)
        return if (tracks.isEmpty()) null
        else HomeSection("recently_played", "Recently Played", tracks)
    }

    // ── 3. Because You Played X ───────────────────────────────────────────────

    private suspend fun shelfBecauseYouPlayed(
        candidates: List<RecommendationCandidateEntity>,
        scoreMap: Map<String, Float>,
        nowMs: Long
    ): HomeSection? {
        val seedEvent     = playEventDao.getRecent(1).firstOrNull() ?: return null
        val seedCandidate = candidateDao.getByVideoId(seedEvent.videoId) ?: return null

        val related = try {
            searchRepository.getRelatedTracks(seedEvent.videoId)
        } catch (_: Exception) { emptyList() }

        related.forEach { track ->
            recommendationRepository.fetchAndIngestRelatedTracks(track.id)
        }

        val pairs     = recommendationRepository.getSessionPairs(seedEvent.videoId)
        val pairedIds = pairs.map {
            if (it.videoIdA == seedEvent.videoId) it.videoIdB else it.videoIdA
        }.toSet()

        val scored = candidates
            .filter { it.videoId in pairedIds || related.any { r -> r.id == it.videoId } }
            .filter { suppressionDao.isSuppressed(it.videoId, nowMs) == 0 }
            .map { it to (scoreMap[it.videoId] ?: 0f) }

        val diversified = mmrDiversifier.diversify(scored, SHELF_LIMIT)
        if (diversified.isEmpty()) return null

        return HomeSection(
            id     = "because_you_played",
            title  = "Because You Played ${seedCandidate.title}",
            tracks = diversified.map { it.toTrack() }
        )
    }

    // ── 4. Similar to Favorites ───────────────────────────────────────────────

    private suspend fun shelfSimilarToFavorites(
        favorites: List<RecommendationCandidateEntity>,
        candidates: List<RecommendationCandidateEntity>,
        scoreMap: Map<String, Float>
    ): HomeSection? {
        if (favorites.isEmpty()) return null
        val seedText = favorites.take(5).joinToString(" ") {
            "${it.title} ${it.artistName} ${it.genre}"
        }
        val ranked = tfIdfEngine.rankCandidates(seedText, candidates)
            .filter { (c, _) -> favorites.none { f -> f.videoId == c.videoId } }
            .take(SHELF_LIMIT * 2)
            .map { (c, tfidfScore) ->
                val baseScore = scoreMap[c.videoId] ?: 0f
                c to ((baseScore + tfidfScore) / 2f)
            }

        val diversified = mmrDiversifier.diversify(ranked, SHELF_LIMIT)
        if (diversified.isEmpty()) return null

        return HomeSection("similar_to_favorites", "Similar to Favorites", diversified.map { it.toTrack() })
    }

    // ── 5. Trending for You ───────────────────────────────────────────────────

    private suspend fun shelfTrendingForYou(
        candidates: List<RecommendationCandidateEntity>,
        tasteProfile: TasteProfileEngine.TasteProfile,
        scoreMap: Map<String, Float>
    ): HomeSection? {
        val trendingQuery = if (tasteProfile.topGenres.isNotEmpty())
            "trending ${tasteProfile.topGenres.first()} music"
        else "trending music"

        val results = try {
            searchRepository.search(trendingQuery)
        } catch (_: Exception) { emptyList() }

        val reRanked = results.map { track ->
            val candidate = candidates.find { it.videoId == track.id }
            val score = candidate?.let { scoreMap[it.videoId] } ?: 0f
            track to score
        }.sortedByDescending { it.second }
            .take(SHELF_LIMIT)
            .map { it.first }

        return if (reRanked.isEmpty()) null
        else HomeSection("trending_for_you", "Trending for You", reRanked)
    }

    // ── 6. Rediscover Favorites ───────────────────────────────────────────────

    private suspend fun shelfRediscover(nowMs: Long): HomeSection? {
        val recent     = playEventDao.getRecent(500).map { it.videoId }.toSet()
        val allPlays   = playEventDao.getRecent(2000)
        val playCounts = allPlays.groupingBy { it.videoId }.eachCount()

        val rediscoverIds = playCounts
            .filter { (videoId, count) -> count >= 5 && videoId !in recent }
            .keys.take(SHELF_LIMIT)

        val tracks = rediscoverIds.mapNotNull { id ->
            candidateDao.getByVideoId(id)?.toTrack()
        }
        return if (tracks.isEmpty()) null
        else HomeSection("rediscover", "Rediscover Favorites", tracks)
    }

    // ── 7. New Artists to Explore ─────────────────────────────────────────────

    private suspend fun shelfNewArtists(
        candidates: List<RecommendationCandidateEntity>,
        tasteProfile: TasteProfileEngine.TasteProfile,
        scoreMap: Map<String, Float>
    ): HomeSection? {
        val topArtists = tasteProfile.topArtists.take(5)
        if (topArtists.isEmpty()) return null

        val playedArtistIds = candidates
            .filter { (scoreMap[it.videoId] ?: 0f) > 0f }
            .map { it.artistId }.toSet()

        val newArtistTracks = candidates
            .filter { it.artistId !in playedArtistIds }
            .map { it to (scoreMap[it.videoId] ?: it.popularityScore) }
            .sortedByDescending { it.second }

        val exploitPool = candidates
            .filter { it.artistId in playedArtistIds }
            .map { it to (scoreMap[it.videoId] ?: 0f) }

        val allocated = epsilonGreedyAllocator.allocate(
            exploitPool  = exploitPool,
            discoverPool = newArtistTracks.map { it.first },
            limit        = SHELF_LIMIT,
            epsilon      = 0.40f
        )
        return if (allocated.isEmpty()) null
        else HomeSection("new_artists", "New Artists to Explore", allocated.map { it.toTrack() })
    }

    // ── 8. Recommended Albums ─────────────────────────────────────────────────

    private suspend fun shelfRecommendedAlbums(
        tasteProfile: TasteProfileEngine.TasteProfile
    ): HomeSection? {
        val topArtist = tasteProfile.topArtists.firstOrNull() ?: return null
        val results = try {
            searchRepository.search("$topArtist album")
        } catch (_: Exception) { emptyList() }
        return if (results.isEmpty()) null
        else HomeSection("recommended_albums", "Recommended Albums", results.take(SHELF_LIMIT))
    }

    // ── 9. Night Listening Mix (9PM–5AM) ──────────────────────────────────────

    private suspend fun shelfNightMix(
        candidates: List<RecommendationCandidateEntity>,
        scoreMap: Map<String, Float>,
        nowHour: Int
    ): HomeSection? {
        val isNightTime = nowHour >= 21 || nowHour < 5
        if (!isNightTime) return null

        val nightKeywords = setOf("chill", "acoustic", "ambient", "classical", "lofi", "lo-fi", "sleep", "calm")
        val nightCandidates = candidates.filter { c ->
            val tokens = c.metadataTokens.lowercase()
            nightKeywords.any { tokens.contains(it) }
        }.map { it to (scoreMap[it.videoId] ?: 0f) }

        val diversified = mmrDiversifier.diversify(nightCandidates, SHELF_LIMIT)
        return if (diversified.isEmpty()) null
        else HomeSection("night_mix", "Night Listening Mix", diversified.map { it.toTrack() }, isEphemeral = true)
    }

    // ── 10. Workout Mix ───────────────────────────────────────────────────────

    private suspend fun shelfWorkoutMix(
        candidates: List<RecommendationCandidateEntity>,
        scoreMap: Map<String, Float>
    ): HomeSection? {
        val workoutKeywords = setOf("workout", "gym", "run", "electronic", "hype", "cardio", "edm", "bass", "energy")
        val workoutCandidates = candidates.filter { c ->
            val tokens = c.metadataTokens.lowercase()
            workoutKeywords.any { tokens.contains(it) }
        }.map { it to (scoreMap[it.videoId] ?: 0f) }

        val diversified = mmrDiversifier.diversify(workoutCandidates, SHELF_LIMIT)
        return if (diversified.isEmpty()) null
        else HomeSection("workout_mix", "Workout Mix", diversified.map { it.toTrack() }, isEphemeral = true)
    }

    // ── Shelf ranking ─────────────────────────────────────────────────────────

    private suspend fun rankShelves(
        shelves: List<HomeSection>,
        tasteProfile: TasteProfileEngine.TasteProfile,
        nowMs: Long
    ): List<HomeSection> {
        val scored = shelves.map { shelf ->
            val shelfScore = ALPHA * contextMatchScore(shelf, tasteProfile) +
                    BETA  * historicalAffinityScore(shelf) +
                    GAMMA * freshnessScore(shelf) -
                    DELTA * fatigueScore(shelf.id, nowMs)
            shelf to shelfScore
        }
        val pinned   = scored.filter { it.first.id == "continue_listening" }
        val unpinned = scored.filter { it.first.id != "continue_listening" }
            .sortedByDescending { it.second }
        return (pinned + unpinned).map { it.first }
    }

    private fun contextMatchScore(shelf: HomeSection, tasteProfile: TasteProfileEngine.TasteProfile) = when (shelf.id) {
        "night_mix", "workout_mix" -> 1.0f
        "because_you_played"       -> 0.9f
        "similar_to_favorites"     -> if (tasteProfile.topArtists.isNotEmpty()) 0.85f else 0.3f
        "trending_for_you"         -> if (tasteProfile.topGenres.isNotEmpty()) 0.7f else 0.4f
        else                       -> 0.5f
    }

    private fun historicalAffinityScore(shelf: HomeSection) = when (shelf.id) {
        "continue_listening"   -> 1.0f
        "recently_played"      -> 0.9f
        "rediscover"           -> 0.8f
        "similar_to_favorites" -> 0.75f
        "because_you_played"   -> 0.7f
        else                   -> 0.4f
    }

    private fun freshnessScore(shelf: HomeSection) = when (shelf.id) {
        "trending_for_you"   -> 1.0f
        "new_artists"        -> 0.9f
        "recommended_albums" -> 0.7f
        "because_you_played" -> 0.6f
        else                 -> 0.4f
    }

    private suspend fun fatigueScore(shelfId: String, nowMs: Long): Float {
        val totals = shelfImpressionDao.getBySectionId(shelfId)
        val recentImpressions = totals.count { it.timestamp >= nowMs - MS_24H }
        return (recentImpressions / 10f).coerceAtMost(1f)
    }

    // ── Score map ─────────────────────────────────────────────────────────────

    private fun buildScoreMap(
        candidates: List<RecommendationCandidateEntity>,
        recentPlays: List<com.example.ytmusicapp.data.db.entity.PlayEventEntity>,
        recentSkips: List<com.example.ytmusicapp.data.db.entity.SkipEventEntity>,
        artistMap: Map<String, String>,
        recentSearchTokens: List<String>,
        nowMs: Long
    ): Map<String, Float> {
        val playsByVideoId  = recentPlays.groupBy { it.videoId }
        val skipsByVideoId  = recentSkips.groupBy { it.videoId }
        val playsByArtistId = recentPlays.groupBy { artistMap[it.videoId] ?: "" }
        val skipsByArtistId = recentSkips.groupBy { artistMap[it.videoId] ?: "" }

        return candidates.associate { candidate ->
            val ctx = TrackScoringEngine.ScoringContext(
                candidate          = candidate,
                playEvents         = playsByVideoId[candidate.videoId] ?: emptyList(),
                skipEvents         = skipsByVideoId[candidate.videoId] ?: emptyList(),
                artistPlayEvents   = playsByArtistId[candidate.artistId] ?: emptyList(),
                artistSkipEvents   = skipsByArtistId[candidate.artistId] ?: emptyList(),
                recentSearchTokens = recentSearchTokens,
                nowMs              = nowMs
            )
            candidate.videoId to scoringEngine.score(ctx)
        }
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private fun RecommendationCandidateEntity.toTrack() = Track(
        id           = videoId,
        title        = title,
        artist       = artistName,
        album        = albumName,
        thumbnailUrl = "",
        durationMs   = durationMs,
        isDownloaded = false
    )
}