package com.example.ytmusicapp.data.repository

import com.example.ytmusicapp.data.db.dao.DownloadDao
import com.example.ytmusicapp.data.db.dao.MusicBrainzDao
import com.example.ytmusicapp.data.db.dao.PlayEventDao
import com.example.ytmusicapp.data.db.dao.RecommendationCandidateDao
import com.example.ytmusicapp.data.db.dao.TrackDao
import com.example.ytmusicapp.data.db.entity.DownloadEntity
import com.example.ytmusicapp.data.db.entity.MbAlbumEntity
import com.example.ytmusicapp.data.db.entity.MbAlbumTrackEntity
import com.example.ytmusicapp.data.db.entity.MbArtistResolutionEntity
import com.example.ytmusicapp.data.db.entity.RecommendationCandidateEntity
import com.example.ytmusicapp.data.db.entity.TrackEntity
import com.example.ytmusicapp.data.util.ArtistNameUtils
import com.example.ytmusicapp.domain.model.Album
import com.example.ytmusicapp.domain.model.Artist
import com.example.ytmusicapp.domain.model.Track
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val CANDIDATE_LIMIT = 2_000
private const val MAX_ALBUM_RESULTS = 12
private const val TAG = "ArtistRepository"
private const val DURATION_TOLERANCE_MS = 10_000L
// MusicBrainz calls are still hard-serialized behind a single ~1.1s-apart rate-limiter mutex
// regardless of this value (see MusicBrainzRepository.rateLimitedGet) — raising this doesn't
// speed up MusicBrainz itself. What it does do: let one album's YouTube-video-matching phase
// (NewPipe searches, bounded separately by trackSearchSemaphore, NOT MusicBrainz-rate-limited)
// overlap with another album's MusicBrainz-fetch phase, instead of the whole per-album pipeline
// serializing behind a cap of 2. Meaningful for a large discography (dozens-to-100+ release
// groups) without ever exceeding MusicBrainz's real rate limit.
private const val ALBUM_CONCURRENCY = 6
private const val TRACK_SEARCH_CONCURRENCY = 3
// YouTube search isn't rate-limited the way MusicBrainz is, so paginating the fast-preview search
// a few pages deep is cheap and fast — 3 pages is a few dozen extra results, not unbounded crawling.
private const val FAST_PREVIEW_MAX_PAGES = 3

@Singleton
class ArtistRepository @Inject constructor(
    private val candidateDao: RecommendationCandidateDao,
    private val trackDao: TrackDao,
    private val downloadDao: DownloadDao,
    private val playEventDao: PlayEventDao,
    private val searchRepository: SearchRepository,
    private val musicBrainzRepository: MusicBrainzRepository,
    private val musicBrainzDao: MusicBrainzDao
) {

    // Outlives any single screen — background album resolution keeps making progress (and
    // caching results) even if the user navigates away, and is shared across every observer
    // of the same artist (ArtistScreen, ArtistSectionScreen, AlbumScreen all hit the same
    // session instead of triggering redundant work).
    private val resolverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // App-wide caps, not per-artist: this is what actually fixes minutes-long loads for a
    // large discography — without these, N albums each fire bursts of NewPipe searches at
    // once, and it's that contention (not raw sequential math) that balloons wall-clock time.
    private val albumSemaphore = Semaphore(ALBUM_CONCURRENCY)
    private val trackSearchSemaphore = Semaphore(TRACK_SEARCH_CONCURRENCY)

    private val sessions = ConcurrentHashMap<String, ArtistResolutionSession>()

    /** Human-readable trace of the last album lookup *for this artist* — kept per-session, not
     *  as a single shared field, since a shared field meant whichever artist's background work
     *  happened to write last would clobber the debug text shown on a completely different
     *  artist's page. Surfaced in the UI (ArtistScreen) right where Albums would render —
     *  temporary diagnostic scaffolding, remove once the album lookup is confirmed working. */
    fun getLastAlbumDebugInfo(artistKey: String): String =
        sessions[ArtistNameUtils.normalize(artistKey)]?.debug ?: "not run yet"

    private class ArtistResolutionSession(val normalizedKey: String, val rawArtistKey: String) {
        val state = MutableStateFlow<Artist?>(null)
        val started = AtomicBoolean(false)
        val retrying = AtomicBoolean(false)
        val updateMutex = Mutex()
        val inFlight = ConcurrentHashMap<String, Deferred<Album>>()
        @Volatile var albums: List<Album> = emptyList()
        @Volatile var localTracks: List<Track> = emptyList()
        @Volatile var displayName: String = rawArtistKey
        @Volatile var debug: String = "not run yet"
    }

    /** Hot, per-artist stream of progressively-more-complete [Artist] snapshots. First emission
     *  is local-only data plus MusicBrainz album *shells* (metadata + cover art, no tracks yet —
     *  fast: local DB reads plus 2 rate-limited MusicBrainz calls, ~2s). Later emissions replace
     *  shells with fully-resolved albums as background work finishes. The session (and its
     *  background work) is shared across every collector and outlives any one of them. */
    fun observeArtistCatalog(artistKey: String): Flow<Artist> {
        val session = sessionFor(artistKey)
        return session.state.filterNotNull()
    }

    /** Priority path for a user-opened album: resolves (or joins an already in-flight
     *  resolution of) just that one album, ahead of whatever the background queue is doing —
     *  it isn't queued behind unresolved albums, though it can still have to wait out the
     *  [ALBUM_CONCURRENCY] cap if both slots are already busy with other work in progress. */
    suspend fun getAlbumDetail(artistKey: String, albumTitle: String): Album? {
        val session = sessionFor(artistKey)
        val firstState = session.state.filterNotNull().first()
        val current = firstState.albums.firstOrNull { it.title == albumTitle } ?: return null
        if (!current.isLoading) return current

        return resolveOrJoin(
            session = session,
            group = MbReleaseGroup(current.id, current.title, current.year),
            artistName = session.displayName,
            normalizedArtistKey = session.normalizedKey,
            localTracks = session.localTracks
        )
    }

    private fun sessionFor(artistKey: String): ArtistResolutionSession {
        val key = ArtistNameUtils.normalize(artistKey)
        val session = sessions.getOrPut(key) { ArtistResolutionSession(key, artistKey) }
        if (session.started.compareAndSet(false, true)) {
            resolverScope.launch { runSession(session) }
        } else {
            // The session already ran to completion once this process — without this, revisiting
            // the same artist page just resubscribes to that frozen result forever, and anything
            // that failed (artist lookup itself, or an individual album) stays stuck that way
            // until the app process restarts and the in-memory session map is wiped. Retries
            // only what's actually still broken — a no-op (no network) for an artist that's
            // already fully resolved.
            resolverScope.launch { reconcileSession(session) }
        }
        return session
    }

    /** Every track the app currently knows about, deduped by id (candidate data wins — it
     *  carries album/genre metadata the other two sources don't). */
    private suspend fun allKnownTracks(): List<Track> = withContext(Dispatchers.IO) {
        val candidates = candidateDao.getTopCandidates(CANDIDATE_LIMIT)
        val library = trackDao.getAllTracksOnce()
        val downloads = downloadDao.getAllDownloads().first()

        val byId = LinkedHashMap<String, Track>()
        library.forEach { byId[it.id] = it.toTrack() }
        downloads.forEach { byId[it.trackId] = it.toTrack() }
        candidates.forEach { byId[it.videoId] = it.toTrack() }
        byId.values.toList()
    }

    // Strips ANY parenthetical/bracketed qualifier — "(feat. X)", "(Konvict Remix)", "(Radio
    // Edit)", "[Live]" — to get a title's "base song" identity for collapsing duplicate/remix
    // entries of the same track down to one representative row.
    private val TITLE_QUALIFIER = Regex("""\s*[(\[][^)\]]*[)\]]\s*""")

    /** Canonical MusicBrainz titles are clean ("Happier Than Ever"), but a lot of official
     *  YouTube uploads (Vevo-style, "Topic" channels) title the *video* as "Artist - Song Title"
     *  instead of just the song title. Comparing those literally against MusicBrainz's title
     *  always failed — this is why an artist like Billie Eilish could resolve real release-
     *  groups from MusicBrainz but end up with zero matched tracks despite the songs plainly
     *  being available. Strips a leading "<artist> <-/–/—/:/|>" prefix before the normal
     *  qualifier-stripping, so both sides converge on the same base title. */
    private fun baseTitleKey(title: String, artistName: String): String {
        val prefix = Regex("""^\s*${Regex.escape(artistName)}\s*[-–—:|]\s*""", RegexOption.IGNORE_CASE)
        return ArtistNameUtils.normalize(
            title.replace(prefix, "").replace(TITLE_QUALIFIER, " ").trim()
        )
    }

    /** Bidirectional substring check rather than exact equality — channel names routinely carry
     *  extra branding a plain `==` can't see through (VTuber channels especially, e.g. "Nerissa
     *  Ravencroft Ch. hololive-EN"). Recognizes the artist is genuinely credited without needing
     *  to enumerate every possible branding convention. */
    private fun trackCreditsArtist(track: Track, normalizedArtistKey: String): Boolean =
        ArtistNameUtils.extractArtists(track.title, track.artist).any {
            val n = ArtistNameUtils.normalize(it)
            n.contains(normalizedArtistKey) || normalizedArtistKey.contains(n)
        }

    // ── Session lifecycle ─────────────────────────────────────────────────────────

    /** Shared trigger for the YouTube search-based album fallback, used both when MusicBrainz has
     *  no confident artist match at all AND when it has a confident match but (after merging
     *  whatever's already cached) zero actual release-groups to show — those are different
     *  reasons but the same recovery action, and consolidating them into one helper avoids the
     *  fiddly "is this catalog actually empty" condition getting copy-pasted slightly wrong across
     *  runSession's two branches and reconcileSession. [base] is whatever albums are already known
     *  (local-only, or already-cached MusicBrainz albums) — runSearchFallback merges its results
     *  into this rather than replacing it. */
    private suspend fun triggerEmptyCatalogFallback(
        session: ArtistResolutionSession,
        displayName: String,
        normalizedArtistKey: String,
        base: List<Album>,
        reason: String
    ) {
        session.debug = "MusicBrainz: $reason for '$displayName' — using search fallback"
        Log.d(TAG, session.debug)
        runCatching { runSearchFallback(session, displayName, normalizedArtistKey, base) }
            .onFailure { e -> Log.e(TAG, "runSearchFallback('$displayName') threw", e) }
    }

    private suspend fun runSession(session: ArtistResolutionSession) = withContext(Dispatchers.IO) {
        val key = session.normalizedKey
        val localTracks = allKnownTracks().filter { track ->
            ArtistNameUtils.extractArtists(track.title, track.artist).any { ArtistNameUtils.normalize(it) == key }
        }
        val displayName = localTracks.groupBy { it.artist }.maxByOrNull { it.value.size }?.key ?: session.rawArtistKey
        session.localTracks = localTracks
        session.displayName = displayName

        // Local storage never carries a real album name (search/related-track ingestion always
        // leaves Track.album blank), so this is typically empty — kept for the rare case it isn't.
        val localAlbums = localTracks.filter { it.album.isNotBlank() }
            .groupBy { it.album.trim() }
            .map { (albumName, albumTracks) ->
                Album(
                    id = "$displayName|$albumName",
                    title = albumName,
                    artist = displayName,
                    thumbnailUrl = albumTracks.first().thumbnailUrl,
                    year = null,
                    tracks = albumTracks
                )
            }

        // Publish before any network call at all — local Room reads only, so this is the true
        // "page becomes usable" moment: header, Popular, Singles, All Songs all work from here.
        // Whatever MusicBrainz has to say about Albums arrives as a later update, not a gate.
        publishAlbums(session, localAlbums)

        // Fast preview: a single plain YouTube search for the artist name, run unconditionally
        // and CONCURRENTLY with MusicBrainz resolution below — not gated behind "MusicBrainz has
        // no confident match" the way the full search fallback is. MusicBrainz is rate-limited to
        // ~1 request/second, so a prolific artist's full catalog can take well over a minute to
        // resolve; a NewPipe/YouTube search has no such limit and typically returns in a couple of
        // seconds. This is what actually gets real, playable songs on screen quickly regardless of
        // catalog size — MusicBrainz still drives the canonical album organization/completeness,
        // it just no longer gates the very first content. Merges into session.localTracks the same
        // way runSearchFallback's own general search does; MusicBrainz-matched tracks that turn
        // out to be the same video (same id) simply dedupe naturally once they arrive.
        resolverScope.launch {
            runCatching {
                val preview = searchRepository.searchMultiPage(displayName, maxPages = FAST_PREVIEW_MAX_PAGES)
                    .filter { trackCreditsArtist(it, key) }
                if (preview.isNotEmpty()) {
                    session.updateMutex.withLock {
                        session.localTracks = dedupeById(session.localTracks + preview)
                        session.state.value = assembleArtist(session, session.albums)
                    }
                }
            }.onFailure { e -> Log.e(TAG, "fast preview search('$displayName') threw", e) }
        }

        val cachedResolution = musicBrainzDao.getArtistResolution(key)

        if (cachedResolution != null) {
            val mbid = cachedResolution.artistMbid
            if (mbid == null) {
                // Already confirmed on a previous visit that MusicBrainz doesn't have this
                // artist — go straight to the fallback, no point re-querying to find out again.
                triggerEmptyCatalogFallback(session, displayName, key, localAlbums, "cached as 'no match'")
                return@withContext
            }

            // Show whatever's already fully cached immediately — still zero network calls. A
            // cached album with zero matched tracks isn't a real success (e.g. a prior run with
            // a matching bug since fixed) — don't trust it as "done", let it be retried below
            // rather than getting stuck showing nothing forever.
            val cachedEntities = musicBrainzDao.getAlbumsForArtist(key)
            val cachedAlbums = cachedEntities.map { it.toAlbum(musicBrainzDao.getTracksForAlbum(it.id)) }
            val trulyCached = cachedAlbums.filter { it.tracks.isNotEmpty() }
            if (trulyCached.isNotEmpty()) {
                publishAlbums(session, localAlbums + trulyCached)
            }

            // Only now check MusicBrainz for anything released/added since the last visit —
            // this happens after real content is already on screen, not before.
            val releaseGroups = runCatching { musicBrainzRepository.getReleaseGroups(mbid) }.getOrDefault(emptyList())

            // A confident artist match with zero release-groups (and nothing already cached
            // either) is NOT the same state as "no music found" — it usually means MusicBrainz's
            // catalog for this artist doesn't fit the release-group query at all (e.g. an artist
            // known only for collaborations/features, or a genuine MusicBrainz coverage gap), not
            // that the artist has no music. Recover via the same search fallback used when
            // MusicBrainz doesn't know the artist at all, rather than dead-ending on "0 songs".
            if (trulyCached.isEmpty() && releaseGroups.isEmpty()) {
                triggerEmptyCatalogFallback(
                    session, displayName, key, localAlbums,
                    "mbid=$mbid but zero release-groups"
                )
                return@withContext
            }

            val cachedIds = trulyCached.map { it.id }.toSet()
            val pendingGroups = releaseGroups.filterNot { it.id in cachedIds }
            if (pendingGroups.isEmpty()) {
                logCatalogDiagnostics(
                    session, displayName,
                    extra = "${trulyCached.size} album(s), all cached, nothing new"
                )
                return@withContext
            }

            val shells = pendingGroups.map { it.toShell(displayName) }
            publishAlbums(session, session.albums + shells)
            session.debug = "MusicBrainz: '$displayName' — ${trulyCached.size} cached, " +
                "${pendingGroups.size} pending (new or previously unmatched), resolving in background"
            Log.d(TAG, session.debug)

            resolvePendingGroups(session, pendingGroups, displayName, key, localTracks, trulyCached.size)
            return@withContext
        }

        // First time ever seeing this artist: must ask MusicBrainz who they are. A thrown
        // exception here (timeout, no connectivity, ...) is NOT the same thing as MusicBrainz
        // genuinely having no confident match — only the latter is safe to cache permanently.
        // A network failure gets treated as "not yet resolved" (nothing written to
        // mb_artist_resolution), so a plain revisit retries it instead of the artist being
        // stuck on the weaker search fallback forever because of one bad connection moment.
        val mbidOutcome = runCatching { musicBrainzRepository.findArtistMbid(displayName) }
        if (mbidOutcome.isFailure) {
            session.debug = "MusicBrainz: artist lookup failed for '$displayName' " +
                "(${mbidOutcome.exceptionOrNull()?.let { it::class.simpleName }}) — " +
                "using search fallback this visit, will retry MusicBrainz on next visit"
            Log.e(TAG, session.debug, mbidOutcome.exceptionOrNull())
            runCatching { runSearchFallback(session, displayName, key, localAlbums) }
                .onFailure { e -> Log.e(TAG, "runSearchFallback('$displayName') threw", e) }
            return@withContext
        }

        val resolved = mbidOutcome.getOrNull()
        musicBrainzDao.upsertArtistResolution(MbArtistResolutionEntity(key, resolved, System.currentTimeMillis()))

        if (resolved == null) {
            triggerEmptyCatalogFallback(session, displayName, key, localAlbums, "no confident match")
            return@withContext
        }

        val releaseGroups = runCatching { musicBrainzRepository.getReleaseGroups(resolved) }.getOrDefault(emptyList())
        if (releaseGroups.isEmpty()) {
            triggerEmptyCatalogFallback(session, displayName, key, localAlbums, "mbid=$resolved but zero release-groups")
            return@withContext
        }

        val shells = releaseGroups.map { it.toShell(displayName) }
        publishAlbums(session, localAlbums + shells)
        session.debug = "MusicBrainz: mbid=$resolved, ${releaseGroups.size} release group(s) to resolve"
        Log.d(TAG, session.debug)

        resolvePendingGroups(session, releaseGroups, displayName, key, localTracks, priorCachedCount = 0)
    }

    /** Resolves every pending release-group (bounded by [albumSemaphore]/[trackSearchSemaphore]
     *  as usual) and finishes with a per-album track-count breakdown in [ArtistResolutionSession.debug]
     *  — an album that resolved but matched zero tracks is otherwise invisible (filtered out of
     *  the displayed list by [assembleArtist]), so without this breakdown "some albums are
     *  missing" is undiagnosable from the UI alone. */
    private suspend fun resolvePendingGroups(
        session: ArtistResolutionSession,
        pendingGroups: List<MbReleaseGroup>,
        displayName: String,
        normalizedArtistKey: String,
        localTracks: List<Track>,
        priorCachedCount: Int
    ) {
        if (pendingGroups.isEmpty()) return
        coroutineScope {
            pendingGroups.map { group ->
                async { resolveOrJoin(session, group, displayName, normalizedArtistKey, localTracks) }
            }.awaitAll()
        }

        val resolvedIds = pendingGroups.map { it.id }.toSet()
        val resolvedNow = session.albums.filter { it.id in resolvedIds }
        logCatalogDiagnostics(
            session, displayName,
            extra = "$priorCachedCount previously cached, ${resolvedNow.size} resolved this pass: " +
                resolvedNow.joinToString("; ") { "'${it.title}': ${it.tracks.size} tracks" }
        )
    }

    /** Development diagnostic — logs (and stores in [ArtistResolutionSession.debug], already
     *  surfaced on-screen by ArtistScreen's temporary debug text) a full breakdown of where this
     *  artist's catalog currently stands: how many MusicBrainz release-groups actually resolved
     *  to a track, how many of those tracks matched a playable YouTube video vs. didn't (an album
     *  MusicBrainz confirms exists but the app couldn't find a video for — otherwise invisible,
     *  since [assembleArtist] filters out empty-track albums entirely), the YouTube-only "singles"
     *  bucket size, and the final deduplicated total. This is the "no unexplained disappearance of
     *  catalog items" requirement made checkable without guessing. */
    private fun logCatalogDiagnostics(session: ArtistResolutionSession, displayName: String, extra: String) {
        val mbAlbums = session.albums.filter { !it.isLoading }
        val mbAlbumsWithTracks = mbAlbums.filter { it.tracks.isNotEmpty() }
        val mbAlbumsEmpty = mbAlbums.filter { it.tracks.isEmpty() }
        val canonicalTrackCount = mbAlbumsWithTracks.sumOf { it.tracks.size }
        val youtubeOnlyCount = session.localTracks.size
        // Same "all resolved albums, not just the display-deduped view" source assembleArtist()
        // now uses for Popular/All Songs — this number is what actually appears on the Artist
        // Page, not a separate figure that could silently diverge from it again.
        val total = (mbAlbumsWithTracks.flatMap { it.tracks } + session.localTracks).distinctBy { it.id }.size
        val albumGroupCount = mbAlbums.distinctBy { ArtistNameUtils.normalize(it.title) }.size

        val summary = buildString {
            appendLine("=== ARTIST CATALOG DEBUG ===")
            appendLine("Artist: $displayName")
            appendLine("Release groups: resolved=${mbAlbums.size} withTracks=${mbAlbumsWithTracks.size}")
            appendLine("Album groups (display, deduped by title): $albumGroupCount")
            appendLine("Canonical tracks: $canonicalTrackCount")
            appendLine("YouTube-only tracks: $youtubeOnlyCount")
            appendLine("Artist-wide catalog (Popular/All Songs source, deduplicated by id): $total")
            if (mbAlbumsEmpty.isNotEmpty()) {
                appendLine(
                    "Release groups MusicBrainz confirms but with 0 matched videos " +
                        "(canonical-track -> YouTube matching failed for every track):"
                )
                mbAlbumsEmpty.forEach { appendLine("  - '${it.title}'") }
            }
            appendLine(extra)
        }
        Log.d(TAG, summary)
        session.debug = summary
    }

    /** Called on every revisit to an artist page after the first full resolution has already
     *  run. Nothing here is allowed to permanently give up just because an earlier attempt hit
     *  a network blip on a weak connection — retries whatever's still actually broken:
     *   - the artist-level MusicBrainz lookup itself, if it never got the chance to write a
     *     resolution row at all (a thrown exception, not a genuine "no match" — see
     *     [MusicBrainzRepository.findArtistMbid]'s kdoc for why those are kept distinct);
     *   - albums that are fully unmatched (0 tracks);
     *   - albums that are *partially* unmatched — some canonical tracks matched, others didn't
     *     (e.g. one NewPipe search timed out while the rest succeeded). The in-memory [Album]
     *     only stores successful matches, so this checks the cached [MbAlbumTrackEntity] rows,
     *     which keep every canonical track including the unmatched ones, to find these.
     *  A genuinely fully-resolved artist costs nothing here beyond a couple of local Room reads.
     *  [ArtistResolutionSession.retrying] just avoids piling up duplicate passes if the user
     *  bounces in and out of the page quickly. */
    private suspend fun reconcileSession(session: ArtistResolutionSession) {
        if (!session.retrying.compareAndSet(false, true)) return
        try {
            val key = session.normalizedKey
            val cachedResolution = musicBrainzDao.getArtistResolution(key)

            if (cachedResolution == null) {
                val mbidOutcome = runCatching { musicBrainzRepository.findArtistMbid(session.displayName) }
                if (mbidOutcome.isFailure) {
                    session.debug = "MusicBrainz: artist lookup retry failed for '${session.displayName}' " +
                        "(${mbidOutcome.exceptionOrNull()?.let { it::class.simpleName }}) — will retry again next visit"
                    Log.e(TAG, session.debug, mbidOutcome.exceptionOrNull())
                    return
                }
                val resolved = mbidOutcome.getOrNull()
                musicBrainzDao.upsertArtistResolution(MbArtistResolutionEntity(key, resolved, System.currentTimeMillis()))
                if (resolved == null) {
                    triggerEmptyCatalogFallback(session, session.displayName, key, session.albums, "no confident match")
                    return
                }
                val releaseGroups = runCatching { musicBrainzRepository.getReleaseGroups(resolved) }.getOrDefault(emptyList())
                if (releaseGroups.isEmpty()) {
                    triggerEmptyCatalogFallback(
                        session, session.displayName, key, session.albums,
                        "mbid=$resolved but zero release-groups"
                    )
                    return
                }
                val shells = releaseGroups.map { it.toShell(session.displayName) }
                publishAlbums(session, session.albums + shells)
                resolvePendingGroups(session, releaseGroups, session.displayName, key, session.localTracks, priorCachedCount = 0)
                return
            }

            val fullyFailed = session.albums.filter { !it.isLoading && it.tracks.isEmpty() }
            val resolvedNonEmpty = session.albums.filter { !it.isLoading && it.tracks.isNotEmpty() }
            val partiallyFailed = resolvedNonEmpty.filter { album ->
                musicBrainzDao.getTracksForAlbum(album.id).any { it.matchedVideoId == null }
            }

            val toRetry = (fullyFailed + partiallyFailed).distinctBy { it.id }
            if (toRetry.isEmpty()) return
            val groups = toRetry.map { MbReleaseGroup(it.id, it.title, it.year) }
            resolvePendingGroups(
                session, groups, session.displayName, key, session.localTracks,
                priorCachedCount = session.albums.size - toRetry.size
            )
        } finally {
            session.retrying.set(false)
        }
    }

    private fun MbReleaseGroup.toShell(artistName: String) = Album(
        id = id,
        title = title,
        artist = artistName,
        thumbnailUrl = musicBrainzCoverArtUrl(id),
        year = year,
        tracks = emptyList(),
        isLoading = true
    )

    /** Resolves an album, or joins an already-running resolution of the same album (background
     *  worker and a user's priority tap can both land on the same album — only one actually
     *  does the work). */
    private suspend fun resolveOrJoin(
        session: ArtistResolutionSession,
        group: MbReleaseGroup,
        artistName: String,
        normalizedArtistKey: String,
        localTracks: List<Track>
    ): Album {
        val deferred = session.inFlight.computeIfAbsent(group.id) {
            resolverScope.async { resolveAlbum(group, artistName, normalizedArtistKey, localTracks) }
        }
        val album = deferred.await()
        session.inFlight.remove(group.id)
        updateAlbum(session, album)
        return album
    }

    /** Resolves one album's canonical tracklist and matches every track to a playable video —
     *  bounded by [albumSemaphore] (how many albums resolve at once) and, per track match,
     *  [trackSearchSemaphore] (how many NewPipe searches are in flight app-wide at once). Track
     *  matching within the album runs in parallel now (was a sequential .map), the search
     *  semaphore is what keeps that from just moving the contention problem down one level. */
    private suspend fun resolveAlbum(
        group: MbReleaseGroup,
        artistName: String,
        normalizedArtistKey: String,
        localTracks: List<Track>
    ): Album = albumSemaphore.withPermit {
        val canonicalTracks = musicBrainzRepository.resolveTracklist(group.id).orEmpty()
        val matched = coroutineScope {
            canonicalTracks.map { mbTrack ->
                async { mbTrack to matchCanonicalTrack(mbTrack, artistName, normalizedArtistKey, localTracks) }
            }.awaitAll()
        }

        // Prefer Cover Art Archive's purpose-made cover over an incidental matched-video
        // thumbnail when it's actually confirmed to exist — a video thumbnail can be an old,
        // sub-HD, differently-cropped, or watermarked upload even when the video itself matched
        // correctly (verified concretely: Gwen Stefani's "You Make It Feel Like Christmas" and
        // "Love. Angel. Music. Baby." both have genuine ~1200px Cover Art Archive covers, but were
        // being shown with their matched video's noticeably softer thumbnail instead — the loading
        // shell already used the CAA URL via toShell(), so this was an already-good image being
        // replaced by a worse one the moment resolution finished, exactly the kind of downgrade
        // that shouldn't happen). Falls back to the matched video thumbnail only when Cover Art
        // Archive is confirmed to have nothing, rather than blindly trusting a URL that might 404.
        // Decided once here and persisted on MbAlbumEntity — not re-derived on every cached read.
        val hasCoverArt = musicBrainzRepository.hasCoverArt(group.id)
        val thumbnailUrl = if (hasCoverArt) {
            musicBrainzCoverArtUrl(group.id)
        } else {
            matched.firstNotNullOfOrNull { it.second?.thumbnailUrl } ?: musicBrainzCoverArtUrl(group.id)
        }

        musicBrainzDao.upsertAlbum(
            MbAlbumEntity(
                id = group.id,
                artistKey = normalizedArtistKey,
                artistName = artistName,
                title = group.title,
                year = group.year,
                resolvedAtMs = System.currentTimeMillis(),
                thumbnailUrl = thumbnailUrl
            )
        )
        musicBrainzDao.deleteTracksForAlbum(group.id)
        musicBrainzDao.insertTracks(
            matched.map { (mbTrack, track) ->
                MbAlbumTrackEntity(
                    albumId = group.id,
                    trackNumber = mbTrack.trackNumber,
                    title = mbTrack.title,
                    durationMs = mbTrack.durationMs,
                    matchedVideoId = track?.id,
                    matchedThumbnailUrl = track?.thumbnailUrl
                )
            }
        )

        Album(
            id = group.id,
            title = group.title,
            artist = artistName,
            thumbnailUrl = thumbnailUrl,
            year = group.year,
            tracks = matched.mapNotNull { (mbTrack, track) ->
                track?.copy(title = mbTrack.title.ifBlank { track.title }, album = group.title)
            },
            isLoading = false
        )
    }

    /** Resolves one canonical MusicBrainz track to a playable video: first against tracks this
     *  artist's page already knows about locally (free — no network), then, if unresolved, one
     *  targeted single-track search — verified in both cases by normalized title (qualifiers
     *  stripped) plus duration when both sides have one. The search itself is gated by
     *  [trackSearchSemaphore], not the local check. Uses [SearchRepository.searchRaw] (skips
     *  [com.example.ytmusicapp.domain.engine.MusicContentClassifier]) — this function already
     *  independently verifies title/duration/artist-credit against a MusicBrainz-confirmed real
     *  recording, so a generic search-quality classifier adds no value here, only the risk of
     *  dropping the correct match before this verification ever sees it. */
    private suspend fun matchCanonicalTrack(
        mbTrack: MbTrack,
        artistName: String,
        normalizedArtistKey: String,
        localTracks: List<Track>
    ): Track? {
        val targetBase = baseTitleKey(mbTrack.title, artistName)

        fun durationMatches(candidateMs: Long): Boolean {
            if (mbTrack.durationMs <= 0L || candidateMs <= 0L) return true
            return abs(candidateMs - mbTrack.durationMs) <= DURATION_TOLERANCE_MS
        }
        fun baseTitle(title: String) = baseTitleKey(title, artistName)

        localTracks.firstOrNull { baseTitle(it.title) == targetBase && durationMatches(it.durationMs) }
            ?.let { return it }

        val results = trackSearchSemaphore.withPermit {
            runCatching { searchRepository.searchRaw("${mbTrack.title} $artistName") }.getOrDefault(emptyList())
        }
        return results.firstOrNull { track ->
            baseTitle(track.title) == targetBase &&
                durationMatches(track.durationMs) &&
                trackCreditsArtist(track, normalizedArtistKey)
        }
    }

    // ── State assembly ────────────────────────────────────────────────────────────

    private suspend fun publishAlbums(session: ArtistResolutionSession, albums: List<Album>) {
        session.updateMutex.withLock {
            session.albums = albums
            session.state.value = assembleArtist(session, albums)
        }
    }

    private suspend fun updateAlbum(session: ArtistResolutionSession, album: Album) {
        session.updateMutex.withLock {
            val updated = session.albums.toMutableList()
            val idx = updated.indexOfFirst { it.id == album.id }
            if (idx >= 0) updated[idx] = album else updated.add(album)
            session.albums = updated
            session.state.value = assembleArtist(session, updated)
        }
    }

    private suspend fun assembleArtist(session: ArtistResolutionSession, albums: List<Album>): Artist {
        // displayAlbums is a VIEW for the Albums section (deduped by normalized title so a
        // deluxe/remaster reissue doesn't render as a second near-identical card) — it must never
        // gate which tracks count as the artist's songs. Two release-groups that happen to
        // normalize to the same title (common with reissues) previously meant the "losing" one's
        // tracks silently vanished from Popular/All Songs entirely, even after fully resolving —
        // songs are the primary catalog, album grouping is metadata on top of it, not a filter on
        // top of it.
        val displayAlbums = albums
            .filter { it.isLoading || it.tracks.isNotEmpty() }
            .distinctBy { ArtistNameUtils.normalize(it.title) }

        val allSongsMap = LinkedHashMap<String, Track>()
        session.localTracks.forEach { allSongsMap[it.id] = it }
        // From ALL resolved albums, not just displayAlbums — every track this artist is
        // confirmed (by MusicBrainz + a matched playable video, or by local/YouTube-fallback
        // discovery) to have counts toward the catalog, regardless of which album card ends up
        // representing it (or a title-collision duplicate) in the Albums section.
        albums.forEach { album -> album.tracks.forEach { allSongsMap.putIfAbsent(it.id, it) } }
        val allSongs = allSongsMap.values.toList()

        val albumTrackIds = displayAlbums.flatMap { it.tracks }.map { it.id }.toSet()
        val singles = allSongs.filterNot { it.id in albumTrackIds }

        val playCounts = playEventDao.getPlayCounts().associate { it.videoId to it.playCount }
        val popularityRanked = allSongs.sortedByDescending { playCounts[it.id] ?: 0 }
            .let { sorted -> if (sorted.all { (playCounts[it.id] ?: 0) == 0 }) allSongs else sorted }
        val popular = popularityRanked.take(10)

        val heroImageUrl = popular.firstOrNull()?.thumbnailUrl
            ?: allSongs.firstOrNull()?.thumbnailUrl
            ?: displayAlbums.firstOrNull { it.thumbnailUrl.isNotBlank() }?.thumbnailUrl
            ?: ""

        return Artist(
            id = session.normalizedKey,
            name = session.displayName,
            thumbnailUrl = heroImageUrl,
            songCount = allSongs.size,
            popular = popular,
            popularityRanked = popularityRanked,
            albums = displayAlbums,
            singles = singles,
            allSongs = allSongs
        )
    }

    private fun MbAlbumEntity.toAlbum(tracks: List<MbAlbumTrackEntity>) = Album(
        id = id,
        title = title,
        artist = artistName,
        // The CAA-vs-matched-video priority was already decided once in resolveAlbum() and
        // persisted here — reading a cached album never needs to re-derive it (or make a network
        // call to do so). Blank only for rows written before this field existed; those fall back
        // to Cover Art Archive directly rather than the old backwards (matched-video-first)
        // logic, so an already-cached album gets the corrected priority immediately rather than
        // only after it happens to naturally re-resolve.
        thumbnailUrl = thumbnailUrl.ifBlank { musicBrainzCoverArtUrl(id) },
        year = year,
        tracks = tracks.mapNotNull { t ->
            val videoId = t.matchedVideoId ?: return@mapNotNull null
            Track(
                id = videoId,
                title = t.title,
                artist = artistName,
                album = title,
                thumbnailUrl = t.matchedThumbnailUrl ?: "",
                durationMs = t.durationMs
            )
        }
    )

    // ── Search fallback (artists MusicBrainz doesn't have) ───────────────────────

    private data class AlbumFetchOutcome(val title: String, val album: Album?, val trackCount: Int, val error: String?)
    private data class RawAlbumTracks(val result: AlbumSearchResult, val tracks: List<Track>, val error: String?)

    /** YouTube Music album search results, filtered/deduped/collapsed heuristically. Used only
     *  when MusicBrainz has no confident match for the artist at all — everyday case this
     *  matters for is a niche/regional/indie act MusicBrainz's community catalog doesn't cover.
     *  Routed through the same shell-then-fill mechanism as the MusicBrainz path: shells publish
     *  immediately after the single `searchAlbums` call, using the *real* thumbnail the search
     *  already returned (no Cover Art Archive guess needed here). The per-album track fetch that
     *  follows is bounded by the same [albumSemaphore]/[trackSearchSemaphore] as everything else,
     *  fixing the old unbounded-parallel version of this method. It still resolves and publishes
     *  as one batch rather than album-by-album, because [dedupeById]/cross-album-conflict
     *  resolution below fundamentally needs every album's results before it can decide which
     *  album a given track actually belongs to — publishing one album before its siblings finish
     *  would risk exactly the cross-album leakage that logic exists to prevent.
     *  [SearchRepository.getAlbumTracks] is a text search per album ("<album> <artist>"), not a
     *  scoped tracklist fetch (NewPipeExtractor can't parse YT Music album pages — see its
     *  kdoc), which causes two predictable artifacts this function corrects for:
     *   - the same popular song from a DIFFERENT album leaking into every album's results
     *     (search relevance leans on the artist name more than the album title) — resolved by
     *     keeping a track only in the album where it ranked best in that album's own search;
     *   - the exact same video appearing twice in one album's raw results — collapsed by
     *     videoId only ([dedupeById]). Distinct videos that merely share a title after qualifier-
     *     stripping (a studio cut, a live cover, an acoustic version) are deliberately NOT
     *     collapsed here — for catalog-completeness purposes those are different content, and
     *     incorrectly discarding a real distinct recording is worse than a small amount of
     *     genuine duplication. */
    private suspend fun runSearchFallback(
        session: ArtistResolutionSession,
        artistName: String,
        normalizedArtistKey: String,
        localAlbums: List<Album>
    ) {
        val searchOutcome = runCatching { searchRepository.searchAlbums(artistName) }
        val searchResults = searchOutcome.getOrDefault(emptyList())
        if (searchOutcome.isFailure) {
            Log.e(TAG, "searchAlbums('$artistName') threw", searchOutcome.exceptionOrNull())
            publishAlbums(session, localAlbums)
            return
        }

        val candidates = searchResults
            .filter { result ->
                result.artist.isBlank() ||
                    ArtistNameUtils.normalize(result.artist).let {
                        it.contains(normalizedArtistKey) || normalizedArtistKey.contains(it)
                    }
            }
            .distinctBy { it.playlistUrl }
            .take(MAX_ALBUM_RESULTS)

        if (candidates.isEmpty()) {
            publishAlbums(session, localAlbums)
            return
        }

        val shells = candidates.map { result ->
            Album(
                id = result.id,
                title = result.title,
                artist = artistName,
                thumbnailUrl = result.thumbnailUrl,
                year = null,
                tracks = emptyList(),
                isLoading = true
            )
        }
        publishAlbums(session, localAlbums + shells)

        // Pass 1: fetch every album's raw, artist-filtered, search-ranked track list — bounded
        // by the same repository-global album/search concurrency caps as the MusicBrainz path.
        val raw = coroutineScope {
            candidates.map { result ->
                async {
                    albumSemaphore.withPermit {
                        runCatching {
                            trackSearchSemaphore.withPermit { searchRepository.getAlbumTracks(result.title, artistName) }
                        }.fold(
                            onSuccess = { rawTracks ->
                                val tracks = rawTracks.filter { track -> trackCreditsArtist(track, normalizedArtistKey) }
                                RawAlbumTracks(result, tracks, null)
                            },
                            onFailure = { e ->
                                Log.e(TAG, "getAlbumTracks('${result.title}', '$artistName') threw", e)
                                RawAlbumTracks(result, emptyList(), "${e::class.simpleName}: ${e.message}")
                            }
                        )
                    }
                }
            }.awaitAll()
        }

        // Pass 2: resolve cross-album conflicts — a track appearing in several albums'
        // candidate lists is kept only where it ranked best (lowest index = most relevant
        // hit for that specific album's own search).
        val bestAlbumForTrack = HashMap<String, Pair<String, Int>>()
        raw.forEach { r ->
            r.tracks.forEachIndexed { rank, track ->
                val current = bestAlbumForTrack[track.id]
                if (current == null || rank < current.second) {
                    bestAlbumForTrack[track.id] = r.result.id to rank
                }
            }
        }

        val outcomes = raw.map { r ->
            val owned = r.tracks.filter { bestAlbumForTrack[it.id]?.first == r.result.id }
            val deduped = dedupeById(owned)
            AlbumFetchOutcome(
                title = r.result.title,
                trackCount = deduped.size,
                error = r.error,
                album = if (deduped.isNotEmpty()) Album(
                    id = r.result.id,
                    title = r.result.title,
                    artist = artistName,
                    thumbnailUrl = r.result.thumbnailUrl.ifBlank { deduped.first().thumbnailUrl },
                    year = null,
                    tracks = deduped,
                    isLoading = false
                ) else null
            )
        }

        // A plain song search alongside the album search: searchAlbums only finds content YouTube
        // Music groups into an "album" playlist, which misses standalone singles, covers, and
        // performances that aren't part of any such grouping — a common gap for niche/independent
        // artists. Folded into session.localTracks (not a synthetic Album — this is ungrouped
        // content) so assembleArtist's existing "singles" bucket picks it up naturally.
        val alreadyKnownIds = (localAlbums.flatMap { it.tracks } + outcomes.mapNotNull { it.album }.flatMap { it.tracks })
            .map { it.id }.toSet()
        val generalResults = runCatching { searchRepository.search(artistName) }.getOrDefault(emptyList())
        val generalMatches = dedupeById(generalResults.filter { trackCreditsArtist(it, normalizedArtistKey) })
            .filterNot { it.id in alreadyKnownIds }
        session.localTracks = dedupeById(session.localTracks + generalMatches)

        publishAlbums(session, localAlbums + outcomes.mapNotNull { it.album })
        logCatalogDiagnostics(
            session, artistName,
            extra = "searchAlbums: ${searchResults.size} raw -> ${candidates.size} matched artist; " +
                "general search: ${generalResults.size} raw -> ${generalMatches.size} new. " +
                outcomes.joinToString("; ") { o ->
                    if (o.error != null) "'${o.title}': ERROR ${o.error}" else "'${o.title}': ${o.trackCount} tracks"
                }
        )
    }

    // Conservative on purpose: only collapses a literal duplicate videoId, never two distinct
    // videos that happen to share a title after qualifier-stripping (a studio cut vs. a live
    // cover vs. an acoustic version are different content, not duplicates of each other).
    private fun dedupeById(tracks: List<Track>): List<Track> {
        val seen = LinkedHashMap<String, Track>()
        tracks.forEach { track -> seen.putIfAbsent(track.id, track) }
        return seen.values.toList()
    }

    // ── Search (artist name matches, for the Search screen) ──────────────────────

    suspend fun searchArtists(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = ArtistNameUtils.normalize(query)
        allKnownTracks()
            .flatMap { ArtistNameUtils.extractArtists(it.title, it.artist) }
            .filter { ArtistNameUtils.normalize(it).contains(q) }
            .distinctBy { ArtistNameUtils.normalize(it) }
            .sorted()
    }

    private fun TrackEntity.toTrack() = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        thumbnailUrl = thumbnailUrl,
        durationMs = durationMs,
        isDownloaded = isDownloaded
    )

    private fun DownloadEntity.toTrack() = Track(
        id = trackId,
        title = title,
        artist = artist,
        album = "",
        thumbnailUrl = thumbnailUrl,
        durationMs = 0L,
        isDownloaded = true
    )

    private fun RecommendationCandidateEntity.toTrack() = Track(
        id = videoId,
        title = title,
        artist = artistName,
        album = albumName,
        thumbnailUrl = thumbnailUrl,
        durationMs = durationMs,
        isDownloaded = isDownloaded
    )
}
