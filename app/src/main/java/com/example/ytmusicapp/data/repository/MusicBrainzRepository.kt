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

private val EXCLUDED_SECONDARY_TYPES = setOf(
    "compilation", "live", "remix", "soundtrack", "dj-mix", "mixtape/street",
    "spokenword", "interview", "audiobook", "audio drama"
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

    /** Studio albums only — `type=album` still includes compilations/live albums/reissues
     *  (MusicBrainz layers these as "secondary types" on top of the album primary type), which
     *  needlessly inflates how much a prolific artist's page has to resolve. That data is
     *  already in the response, just unused before now. */
    suspend fun getAlbumReleaseGroups(artistMbid: String): List<MbReleaseGroup> =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/release-group".toHttpUrl().newBuilder()
                .addQueryParameter("artist", artistMbid)
                .addQueryParameter("type", "album")
                .addQueryParameter("fmt", "json")
                .addQueryParameter("limit", "100")
                .build()
            val json = runCatching { rateLimitedGet(url) }.getOrNull() ?: return@withContext emptyList()
            val groups = json.optJSONArray("release-groups") ?: return@withContext emptyList()

            (0 until groups.length()).mapNotNull { i ->
                val g = groups.getJSONObject(i)
                val id = g.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val title = g.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null

                val secondaryTypes = g.optJSONArray("secondary-types")
                val isExcluded = secondaryTypes != null && (0 until secondaryTypes.length()).any {
                    secondaryTypes.optString(it).lowercase() in EXCLUDED_SECONDARY_TYPES
                }
                if (isExcluded) return@mapNotNull null

                val year = g.optString("first-release-date")
                    .takeIf { it.length >= 4 }
                    ?.substring(0, 4)
                    ?.toIntOrNull()
                MbReleaseGroup(id, title, year)
            }
        }

    /** Picks a "standard" release within the group (official status, modal track count across
     *  editions — avoids a deluxe/regional outlier) and returns its full ordered tracklist. */
    suspend fun resolveTracklist(releaseGroupMbid: String): List<MbTrack>? =
        withContext(Dispatchers.IO) {
            val releaseId = pickPreferredRelease(releaseGroupMbid) ?: return@withContext null
            fetchTracklist(releaseId)
        }

    private suspend fun pickPreferredRelease(releaseGroupMbid: String): String? {
        val url = "$BASE_URL/release".toHttpUrl().newBuilder()
            .addQueryParameter("release-group", releaseGroupMbid)
            .addQueryParameter("inc", "media")
            .addQueryParameter("status", "official")
            .addQueryParameter("fmt", "json")
            .addQueryParameter("limit", "100")
            .build()
        val json = runCatching { rateLimitedGet(url) }.getOrNull() ?: return null
        val releases = json.optJSONArray("releases") ?: return null
        if (releases.length() == 0) return null

        data class Candidate(val id: String, val trackCount: Int)
        val candidates = (0 until releases.length()).mapNotNull { i ->
            val r = releases.getJSONObject(i)
            val id = r.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val media = r.optJSONArray("media") ?: return@mapNotNull null
            var trackCount = 0
            for (m in 0 until media.length()) {
                trackCount += media.getJSONObject(m).optInt("track-count", 0)
            }
            if (trackCount > 0) Candidate(id, trackCount) else null
        }
        if (candidates.isEmpty()) return null

        val modalCount = candidates.groupingBy { it.trackCount }.eachCount().maxByOrNull { it.value }?.key
        return candidates.firstOrNull { it.trackCount == modalCount }?.id ?: candidates.first().id
    }

    private suspend fun fetchTracklist(releaseId: String): List<MbTrack>? {
        val url = "$BASE_URL/release/$releaseId".toHttpUrl().newBuilder()
            .addQueryParameter("inc", "recordings")
            .addQueryParameter("fmt", "json")
            .build()
        val json = runCatching { rateLimitedGet(url) }.getOrNull() ?: return null
        val media = json.optJSONArray("media") ?: return null

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
        return tracks.ifEmpty { null }
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
