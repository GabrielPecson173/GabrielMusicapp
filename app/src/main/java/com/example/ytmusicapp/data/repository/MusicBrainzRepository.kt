package com.example.ytmusicapp.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class MbReleaseGroup(val id: String, val title: String, val year: Int?)
data class MbTrack(val trackNumber: Int, val title: String, val durationMs: Long)

// Excludes only secondary types that genuinely aren't a meaningfully distinct song (or aren't
// music at all) on an Artist Page: a remix/DJ-mix/mixtape edit of a track already shown elsewhere,
// or non-music spoken content. Deliberately does NOT exclude "live" or "compilation" — a live
// cover or a compilation-exclusive track is real, often unique music for a niche artist (verified
// directly against MusicBrainz: e.g. Nerissa Ravencroft has a Live-tagged release-group that is a
// distinct song cover with no other release anywhere), and "soundtrack" is likewise often an
// artist's only release of that song. This list is Artist-Page-specific (getReleaseGroups' only
// caller today) — a future stricter recommendation-candidate-ingestion use, if one is added, may
// reasonably want a narrower list than this.
private val EXCLUDED_SECONDARY_TYPES = setOf(
    "remix", "dj-mix", "mixtape/street", "spokenword", "interview", "audiobook", "audio drama"
)

/** Cover Art Archive front cover for a release-group — a plain image URL, no API call, no rate
 *  limit. Not every release-group has art uploaded; Coil just fails silently on a 404. 1200px
 *  (Cover Art Archive's next size up from 500) since this can end up as the Artist Page hero
 *  image (full screen width) as well as a small album card, and 500px looked visibly soft at
 *  hero size — still a thumbnail-sized request, not the (often multi-MB) original. */
fun musicBrainzCoverArtUrl(releaseGroupMbid: String): String =
    "https://coverartarchive.org/release-group/$releaseGroupMbid/front-1200"

/**
 * Minimal client for MusicBrainz's public API (musicbrainz.org/doc/MusicBrainz_API) — the
 * canonical artist -> release-group ("album") -> release (a specific edition) -> recording
 * (a track) hierarchy this app needs to know an album's real tracklist, which NewPipe/YouTube
 * search alone can't answer (see ArtistRepository.fetchRemoteAlbumsBySearch's kdoc for why).
 *
 * MusicBrainz requires <=1 request/second and a genuinely identifying User-Agent (swap in real
 * contact info in [USER_AGENT] if you have a public repo/contact for this app — MusicBrainz's
 * usage policy asks for it). Every call here goes through [rateLimitedGet], which serializes
 * requests with a floor between them. This is slow (~1.1s per call), which is why callers must
 * cache results (see MusicBrainzDao / ArtistRepository) instead of resolving on every page load.
 */
@Singleton
class MusicBrainzRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val BASE_URL = "https://musicbrainz.org/ws/2"
        private const val USER_AGENT = "AndroidMusicApp/1.0 ( https://github.com/example/android-music-app )"
        private const val MIN_INTERVAL_MS = 1100L
        private const val MIN_ARTIST_MATCH_SCORE = 90
    }

    private val requestMutex = Mutex()
    private var lastRequestAtMs = 0L

    /**
     * Whether Cover Art Archive actually has front cover art for this release-group — a cheap
     * HEAD request (no image bytes downloaded), and NOT subject to MusicBrainz's own 1 req/sec
     * limit since Cover Art Archive is a separate service. Lets callers decide between "prefer
     * this purpose-made album cover" and "fall back to something else" instead of blindly trusting
     * the URL and letting a 404 render as a broken image. Cover Art Archive's dedicated artwork is
     * generally meaningfully better than an incidental matched-video thumbnail (which can be an
     * old, sub-HD, or differently-cropped upload) — see [ArtistRepository.resolveAlbum].
     */
    suspend fun hasCoverArt(releaseGroupMbid: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(musicBrainzCoverArtUrl(releaseGroupMbid))
                .head()
                .header("User-Agent", USER_AGENT)
                .build()
            okHttpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** Highest-scoring artist match, or null if MusicBrainz has no confident (score >= 90)
     *  match — a real, cacheable "this artist isn't in MusicBrainz" outcome, not an error.
     *  Deliberately does NOT swallow network/parsing exceptions itself (unlike most other
     *  methods here) — this result gets cached as a permanent negative by the caller
     *  (ArtistRepository), so a timeout on a weak connection must surface as a thrown
     *  exception, not silently collapse into the same "confirmed absent" outcome as a real
     *  negative search result. The caller distinguishes the two and only caches the latter. */
    suspend fun findArtistMbid(artistName: String): String? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/artist/".toHttpUrl().newBuilder()
            .addQueryParameter("query", "artist:\"$artistName\"")
            .addQueryParameter("fmt", "json")
            .addQueryParameter("limit", "5")
            .build()
        val json = rateLimitedGet(url)
        val artists = json.optJSONArray("artists") ?: return@withContext null

        var bestId: String? = null
        var bestScore = -1
        for (i in 0 until artists.length()) {
            val a = artists.getJSONObject(i)
            val score = a.optInt("score", 0)
            if (score > bestScore) {
                bestScore = score
                bestId = a.optString("id").takeIf { it.isNotBlank() }
            }
        }
        if (bestScore >= MIN_ARTIST_MATCH_SCORE) bestId else null
    }

    /** Albums, EPs, and singles — `type=album|ep|single` (pipe-delimited multi-type is the
     *  officially documented syntax for this browse endpoint, e.g. MusicBrainz's own docs example
     *  `/ws/2/release-group?artist=...&type=album|ep`). Deliberately does NOT request every
     *  primary type (dropping the filter entirely would also admit Broadcast/Other, which are
     *  lower-signal) — just the three that represent a real, listenable release. This used to be
     *  `type=album` only, which silently returned zero release-groups for any artist whose real
     *  catalog is Single/EP-typed (extremely common for indie/VTuber/niche artists) even with a
     *  perfect artist-MBID match.
     *  Secondary types (compilations/live albums/reissues, layered on top of any primary type)
     *  are still excluded below via [EXCLUDED_SECONDARY_TYPES], independent of this change. */
    suspend fun getReleaseGroups(artistMbid: String): List<MbReleaseGroup> =
        withContext(Dispatchers.IO) {
            // A single limit=100 request silently truncates a prolific artist's catalog — page
            // through using the API's own release-group-count/release-group-offset fields rather
            // than assuming one response is complete (100 comfortably covers a niche artist, but
            // is a real completeness bug for anyone with a larger discography).
            val allGroupsJson = mutableListOf<org.json.JSONObject>()
            var offset = 0
            var totalCount = Int.MAX_VALUE
            while (offset < totalCount) {
                val url = "$BASE_URL/release-group".toHttpUrl().newBuilder()
                    .addQueryParameter("artist", artistMbid)
                    .addQueryParameter("type", "album|ep|single")
                    .addQueryParameter("fmt", "json")
                    .addQueryParameter("limit", "100")
                    .addQueryParameter("offset", offset.toString())
                    .build()
                val json = runCatching { rateLimitedGet(url) }.getOrNull() ?: break
                totalCount = json.optInt("release-group-count", 0)
                val groups = json.optJSONArray("release-groups") ?: break
                if (groups.length() == 0) break
                (0 until groups.length()).forEach { allGroupsJson.add(groups.getJSONObject(it)) }
                offset += groups.length()
            }

            var excludedCount = 0
            val retained = allGroupsJson.mapNotNull { g ->
                val id = g.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val title = g.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null

                val secondaryTypes = g.optJSONArray("secondary-types")
                val isExcluded = secondaryTypes != null && (0 until secondaryTypes.length()).any {
                    secondaryTypes.optString(it).lowercase() in EXCLUDED_SECONDARY_TYPES
                }
                if (isExcluded) { excludedCount++; return@mapNotNull null }

                val year = g.optString("first-release-date")
                    .takeIf { it.length >= 4 }
                    ?.substring(0, 4)
                    ?.toIntOrNull()
                MbReleaseGroup(id, title, year)
            }
            android.util.Log.d(
                "MusicBrainzRepo",
                "getReleaseGroups($artistMbid): fetched=${allGroupsJson.size} " +
                    "excludedBySecondaryType=$excludedCount retained=${retained.size}"
            )
            retained
        }

    /** Picks a "standard" release within the group (official status, modal track count across
     *  editions — avoids a deluxe/regional outlier) and returns its full ordered tracklist.
     *
     *  Single combined `inc=media+recordings` call per page of releases — verified directly
     *  against MusicBrainz that this endpoint embeds full tracklists (media/tracks/recordings)
     *  for every candidate release in one response, eliminating what used to be a second,
     *  separate per-release `fetchTracklist` call. For a prolific artist (dozens to 100+
     *  release-groups, each previously 2 sequential ~1.1s-rate-limited calls), this roughly
     *  halves total resolution wall-clock time without relaxing the rate limit itself — it's the
     *  same number of *releases* fetched, just one HTTP round-trip per release-group instead of
     *  two. */
    suspend fun resolveTracklist(releaseGroupMbid: String): List<MbTrack>? =
        withContext(Dispatchers.IO) {
            // Prefer an official release, but a niche/independent release-group's only release may
            // genuinely not carry "Official" status in MusicBrainz — falling back to "any status"
            // rather than giving up outright avoids treating a real release as if it doesn't exist
            // just because of its cataloged status.
            val official = fetchReleasesWithTracks(releaseGroupMbid, statusFilter = "official")
            val releasesJson = official.ifEmpty { fetchReleasesWithTracks(releaseGroupMbid, statusFilter = null) }
            if (releasesJson.isEmpty()) return@withContext null

            data class Candidate(val json: JSONObject, val trackCount: Int)
            val candidates = releasesJson.mapNotNull { r ->
                val media = r.optJSONArray("media") ?: return@mapNotNull null
                var trackCount = 0
                for (m in 0 until media.length()) {
                    trackCount += media.getJSONObject(m).optInt("track-count", 0)
                }
                if (trackCount > 0) Candidate(r, trackCount) else null
            }
            if (candidates.isEmpty()) return@withContext null

            val modalCount = candidates.groupingBy { it.trackCount }.eachCount().maxByOrNull { it.value }?.key
            val preferred = candidates.firstOrNull { it.trackCount == modalCount } ?: candidates.first()

            val tracks = parseTracklist(preferred.json)
            android.util.Log.d(
                "MusicBrainzRepo",
                "resolveTracklist($releaseGroupMbid): candidates=${candidates.size} " +
                    "picked=${preferred.json.optString("id")} recordings=${tracks.size}"
            )
            tracks.ifEmpty { null }
        }

    // Pages through using release-count/release-offset — a single limit=100 request is virtually
    // always complete for one release-group's releases, but this avoids assuming that silently.
    // inc=media+recordings embeds each release's full medium/track/recording data inline.
    private suspend fun fetchReleasesWithTracks(releaseGroupMbid: String, statusFilter: String?): List<JSONObject> {
        val all = mutableListOf<JSONObject>()
        var offset = 0
        var totalCount = Int.MAX_VALUE
        while (offset < totalCount) {
            val builder = "$BASE_URL/release".toHttpUrl().newBuilder()
                .addQueryParameter("release-group", releaseGroupMbid)
                .addQueryParameter("inc", "media+recordings")
                .addQueryParameter("fmt", "json")
                .addQueryParameter("limit", "100")
                .addQueryParameter("offset", offset.toString())
            if (statusFilter != null) builder.addQueryParameter("status", statusFilter)
            val json = runCatching { rateLimitedGet(builder.build()) }.getOrNull() ?: break
            totalCount = json.optInt("release-count", 0)
            val releases = json.optJSONArray("releases") ?: break
            if (releases.length() == 0) break
            (0 until releases.length()).forEach { all.add(releases.getJSONObject(it)) }
            offset += releases.length()
        }
        return all
    }

    // Walks every medium (multi-disc releases included) and every track within it — a release
    // object already carrying inc=media+recordings data, no further network call.
    private fun parseTracklist(releaseJson: JSONObject): List<MbTrack> {
        val media = releaseJson.optJSONArray("media") ?: return emptyList()
        val tracks = mutableListOf<MbTrack>()
        for (m in 0 until media.length()) {
            val trackArray = media.getJSONObject(m).optJSONArray("tracks") ?: continue
            for (t in 0 until trackArray.length()) {
                val track = trackArray.getJSONObject(t)
                val title = track.optString("title").takeIf { it.isNotBlank() } ?: continue
                val number = track.optString("number")
                    .filter { it.isDigit() }
                    .toIntOrNull()
                    ?: (tracks.size + 1)
                var durationMs = track.optLong("length", -1L)
                if (durationMs <= 0L) {
                    durationMs = track.optJSONObject("recording")?.optLong("length", 0L) ?: 0L
                }
                tracks.add(MbTrack(number, title, durationMs.coerceAtLeast(0L)))
            }
        }
        return tracks
    }

    private suspend fun rateLimitedGet(url: HttpUrl): JSONObject = requestMutex.withLock {
        val elapsed = System.currentTimeMillis() - lastRequestAtMs
        if (elapsed < MIN_INTERVAL_MS) delay(MIN_INTERVAL_MS - elapsed)
        lastRequestAtMs = System.currentTimeMillis()

        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("MusicBrainz request failed: ${response.code} for $url")
        }
        val body = response.body?.string() ?: throw IOException("Empty MusicBrainz response for $url")
        JSONObject(body)
    }
}
