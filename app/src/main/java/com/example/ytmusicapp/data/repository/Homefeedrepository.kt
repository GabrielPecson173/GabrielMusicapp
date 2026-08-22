package com.example.ytmusicapp.data.repository

import com.example.ytmusicapp.data.db.dao.PlayEventDao
import com.example.ytmusicapp.data.db.dao.RecommendationCandidateDao
import com.example.ytmusicapp.data.db.dao.ShelfImpressionDao
import com.example.ytmusicapp.data.db.dao.SkipEventDao
import com.example.ytmusicapp.data.db.dao.SuppressionDao
import com.example.ytmusicapp.data.db.entity.RecommendationCandidateEntity
import com.example.ytmusicapp.domain.engine.EpsilonGreedyAllocator
import com.example.ytmusicapp.domain.engine.MMRDiversifier
import com.example.ytmusicapp.domain.engine.TasteProfileEngine
import com.example.ytmusicapp.domain.engine.TfIdfEngine
import com.example.ytmusicapp.domain.engine.TrackScoringEngine
import com.example.ytmusicapp.data.util.ThumbnailResolver
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

private const val ALPHA = 0.30f
private const val BETA  = 0.35f
private const val GAMMA = 0.20f
private const val DELTA = 0.15f

val GENRE_DISPLAY_NAMES: Map<String, String> = mapOf(
    "phonk"      to "Phonk",
    "lo-fi"      to "Lo-Fi",
    "rap"        to "Rap",
    "rnb"        to "R&B",
    "electronic" to "Electronic",
    "pop"        to "Pop",
    "rock"       to "Rock",
    "jazz"       to "Jazz",
    "classical"  to "Classical",
    "workout"    to "Workout"
)

data class HomeSection(
    val id: String,
    val title: String,
    val tracks: List<Track>,
    val genre: String? = null,
    val isEphemeral: Boolean = false
)

@Singleton
class HomeFeedRepository @Inject constructor(
    private val playEventDao: PlayEventDao,
    private val skipEventDao: SkipEventDao,
    private val candidateDao: RecommendationCandidateDao,
    private val suppressionDao: SuppressionDao,
    private val shelfImpressionDao: ShelfImpressionDao,
    private val recommendationRepository: RecommendationRepository,
    private val scoringEngine: TrackScoringEngine,
    private val tasteProfileEngine: TasteProfileEngine,
    private val tfIdfEngine: TfIdfEngine,
    private val mmrDiversifier: MMRDiversifier,
    private val epsilonGreedyAllocator: EpsilonGreedyAllocator
) {
    // ── Phase A: in-memory stale-while-revalidate cache ───────────────────────

    @Volatile private var cachedFeed: List<HomeSection>? = null

    private val _refreshTrigger = MutableSharedFlow<Unit>(replay = 1)

    fun feedFlow(): Flow<List<HomeSection>> = flow {
        cachedFeed?.let { emit(it) }

        if (cachedFeed == null) {
            val initial = buildFeed()
            cachedFeed = initial
            emit(initial)
        }

        _refreshTrigger.collect {
            val fresh = buildFeed()
            cachedFeed = fresh
            emit(fresh)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun refresh() {
        _refreshTrigger.emit(Unit)
    }

    // Impression/click telemetry is logged exclusively via EventTracker (data/tracker/Eventtracker.kt)
    // — a duplicate write path used to exist here too (shelfImpressionDao.insert), causing every
    // impression/click to be double-counted in shelf_impressions. That inflated the numbers
    // CandidateLifecycleManager's degrade/demote thresholds read, and bypassed the diagnosticsEnabled
    // privacy toggle EventTracker respects. Removed; see HomeScreen.kt's direct eventTracker calls.

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

        val scoreMap = scoringEngine.scoreAll(
            candidates         = candidates,
            recentPlays        = recentPlays,
            recentSkips        = recentSkips.toList(),
            artistMap          = artistMap,
            recentSearchTokens = emptyList(),
            nowMs              = nowMs
        )

        coroutineScope {
            val continueListening  = async { shelfContinueListening() }
            val recentlyPlayed     = async { shelfRecentlyPlayed() }
            val becauseYouPlayed   = async { shelfBecauseYouPlayed(candidates, scoreMap, nowMs) }
            val similarToFavorites = async { shelfSimilarToFavorites(favorites, candidates, scoreMap) }
            val trendingForYou     = async { shelfTrendingForYou(candidates, tasteProfile, scoreMap, nowMs) }
            val rediscover         = async { shelfRediscover() }
            val newArtists         = async { shelfNewArtists(candidates, tasteProfile, scoreMap) }
            val recommendedAlbums  = async { shelfRecommendedAlbums(candidates, tasteProfile, scoreMap) }
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

    suspend fun buildGenreShelf(
        genre: String,
        nowMs: Long = System.currentTimeMillis()
    ): HomeSection? = withContext(Dispatchers.IO) {
        val candidates  = candidateDao.getTopCandidates(CANDIDATE_FETCH_LIMIT)
        val recentPlays = playEventDao.getRecent(200)
        val recentSkips = skipEventDao.getSince(nowMs - MS_48H)
        val artistMap   = candidates.associate { it.videoId to it.artistId }

        val scoreMap = scoringEngine.scoreAll(
            candidates         = candidates,
            recentPlays        = recentPlays,
            recentSkips        = recentSkips.toList(),
            artistMap          = artistMap,
            recentSearchTokens = emptyList(),
            nowMs              = nowMs
        )

        val keywords = GENRE_KEYWORDS[genre] ?: emptySet()
        val genreCandidates = candidates
            .filter { c ->
                c.genre == genre ||
                        (c.genre.isBlank() && keywords.any { kw -> c.metadataTokens.lowercase().contains(kw) })
            }
            .filter { suppressionDao.isSuppressed(it.videoId, nowMs) == 0 }
            .map { it to (scoreMap[it.videoId] ?: it.popularityScore) }

        if (genreCandidates.isEmpty()) return@withContext null

        val diversified = mmrDiversifier.diversify(genreCandidates, SHELF_LIMIT)
        if (diversified.isEmpty()) return@withContext null

        val displayName = GENRE_DISPLAY_NAMES[genre] ?: genre.replaceFirstChar { c -> c.uppercase() }
        HomeSection(
            id     = "genre_$genre",
            title  = displayName,
            tracks = diversified.map { it.toTrack() },
            genre  = genre
        )
    }

    // ── 1. Continue Listening ─────────────────────────────────────────────────

    private suspend fun shelfContinueListening(): HomeSection? {
        val partial = playEventDao.getPartiallyPlayed(10)
        if (partial.isEmpty()) return null
        val tracks = partial.mapNotNull { event ->
            candidateDao.getByVideoId(event.videoId)?.toTrack()
        }
        return if (tracks.isEmpty()) null
        else HomeSection("continue_listening", "Continue Listening", tracks)
    }

    // ── 2. Recently Played ────────────────────────────────────────────────────

    private suspend fun shelfRecentlyPlayed(): HomeSection? {
        val recent = playEventDao.getRecent(20)
        val seen   = mutableSetOf<String>()
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

        val pairs     = recommendationRepository.getSessionPairs(seedEvent.videoId)
        val pairedIds = pairs.map {
            if (it.videoIdA == seedEvent.videoId) it.videoIdB else it.videoIdA
        }.toSet()

        val artistId = seedCandidate.artistId
        val artistCandidates = candidates
            .filter { it.artistId == artistId && it.videoId != seedEvent.videoId }
            .map { it.videoId }
            .toSet()

        val scored = candidates
            .filter { it.videoId in pairedIds || it.videoId in artistCandidates }
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
        scoreMap: Map<String, Float>,
        nowMs: Long
    ): HomeSection? {
        val topGenre = tasteProfile.topGenres.firstOrNull()

        val filtered = if (topGenre != null) {
            candidates.filter { it.genre == topGenre }
        } else {
            candidates
        }

        val scored = filtered
            .filter { suppressionDao.isSuppressed(it.videoId, nowMs) == 0 }
            .map { it to (scoreMap[it.videoId] ?: it.popularityScore) }
            .sortedByDescending { it.second }
            .take(SHELF_LIMIT)

        if (scored.isEmpty()) return null

        return HomeSection(
            id     = "trending_for_you",
            title  = "Trending for You",
            tracks = scored.map { it.first.toTrack() }
        )
    }

    // ── 6. Rediscover Favorites ───────────────────────────────────────────────

    private suspend fun shelfRediscover(): HomeSection? {
        val recentIds   = playEventDao.getRecent(500).map { it.videoId }.toSet()
        val frequentIds = playEventDao.getVideoIdsPlayedAtLeast(minCount = 5, limit = SHELF_LIMIT + 20)
            .filter { it !in recentIds }
            .take(SHELF_LIMIT)

        val tracks = frequentIds.mapNotNull { id ->
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
        candidates: List<RecommendationCandidateEntity>,
        tasteProfile: TasteProfileEngine.TasteProfile,
        scoreMap: Map<String, Float>
    ): HomeSection? {
        val topArtist = tasteProfile.topArtists.firstOrNull() ?: return null

        val albumTracks = candidates
            .filter { it.artistName.equals(topArtist, ignoreCase = true) }
            .map { it to (scoreMap[it.videoId] ?: it.popularityScore) }
            .sortedByDescending { it.second }
            .take(SHELF_LIMIT)

        if (albumTracks.isEmpty()) return null

        return HomeSection(
            id     = "recommended_albums",
            title  = "Recommended Albums",
            tracks = albumTracks.map { it.first.toTrack() }
        )
    }

    // ── 9. Night Listening Mix (9PM–5AM) ──────────────────────────────────────

    private suspend fun shelfNightMix(
        candidates: List<RecommendationCandidateEntity>,
        scoreMap: Map<String, Float>,
        nowHour: Int
    ): HomeSection? {
        if (nowHour in 5..20) return null

        val nightKeywords = setOf("chill", "acoustic", "ambient", "classical", "lo-fi", "sleep", "calm")
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

    // ── Mapper ────────────────────────────────────────────────────────────────

    @Suppress("SpellCheckingInspection")
    private fun RecommendationCandidateEntity.toTrack() = Track(
        id           = videoId,
        title        = title,
        artist       = artistName,
        album        = albumName,
        // Starting point only — YoutubeThumbnailInterceptor verifies and falls back at render
        // time, so this doesn't need to hedge down to a low-res tier itself.
        thumbnailUrl = thumbnailUrl.ifBlank { ThumbnailResolver.fromVideoId(videoId) },
        durationMs   = durationMs,
        isDownloaded = false
    )
}