package com.example.ytmusicapp.data.util

/**
 * Single source of truth for YouTube thumbnail quality ranking, shared by every place that
 * stores, merges, or reconstructs a track's `thumbnailUrl` (RecommendationRepository's candidate
 * ingestion, HomeFeedRepository/SmartQueueEngine's track mapping, QueueRepository's queue
 * reconstruction) — previously each of those either passed a URL through blindly or hardcoded a
 * single low-res `mqdefault.jpg` fallback with no way to ever upgrade it later.
 *
 * Actual "is this really HD" verification (a `maxresdefault.jpg` request can succeed with a small
 * placeholder image instead of a real 404 for videos with no true HD source) happens at render
 * time in [com.example.ytmusicapp.data.image.YoutubeThumbnailInterceptor] — this object only
 * handles static URL-pattern quality ranking for merge/storage decisions.
 */
object ThumbnailResolver {

    // NewPipeExtractor doesn't always return the plain /vi/<id>/<variant>.jpg form — it can also
    // return /vi_webp/ or /vi_avif/ variants with a matching non-.jpg extension (confirmed by a
    // real device screenshot: an album cover sourced from a matched YouTube video's thumbnail was
    // silently skipped by the original .jpg-only, /vi/-only pattern and never got the HD/fallback
    // treatment at all). Match any extension and any of the /vi/, /vi_webp/, /vi_avif/ path forms;
    // URLs constructed BY this app always use the universal plain /vi/....jpg form regardless of
    // which form the original request matched, since that form is guaranteed to exist for every
    // video independent of which image-format variants also happen to be available.
    private val YT_THUMB_REGEX = Regex("""i\.ytimg\.com/vi(?:_webp|_avif)?/([\w-]{11})/(\w+)\.\w+""")

    // Higher = better. Any recognized ytimg URL whose variant isn't in this map (legacy
    // "0.jpg".."3.jpg", "default.jpg") ranks as the lowest tier, 0.
    private val VARIANT_RANK = mapOf(
        "maxresdefault" to 4,
        "sddefault" to 3,
        "hqdefault" to 2,
        "mqdefault" to 1
    )

    // A recognized ytimg URL not matching a known variant name, or a URL from an entirely
    // different source (MusicBrainz Cover Art Archive, a NewPipe-returned CDN thumbnail that
    // isn't a static ytimg default) — these aren't the low-res static defaults this ranking is
    // about, so treat them as reasonably decent rather than penalizing unfamiliarity.
    private const val UNKNOWN_SOURCE_RANK = 2

    fun rank(url: String): Int {
        if (url.isBlank()) return -1
        val match = YT_THUMB_REGEX.find(url) ?: return UNKNOWN_SOURCE_RANK
        return VARIANT_RANK[match.groupValues[2]] ?: 0
    }

    fun videoId(url: String): String? = YT_THUMB_REGEX.find(url)?.groupValues?.get(1)

    /** Never downgrades: returns whichever of [existing]/[incoming] ranks higher, keeping
     *  [existing] on a tie or when neither can be ranked higher than the other. */
    fun betterOf(existing: String, incoming: String): String =
        if (rank(incoming) > rank(existing)) incoming else existing

    /** Ranked ytimg thumbnail URLs for [videoId], best first — the fallback chain
     *  [com.example.ytmusicapp.data.image.YoutubeThumbnailInterceptor] walks when a higher tier
     *  turns out to be a placeholder. */
    fun candidateUrls(videoId: String): List<String> =
        listOf("maxresdefault", "sddefault", "hqdefault", "mqdefault", "default")
            .map { "https://i.ytimg.com/vi/$videoId/$it.jpg" }

    /** Constructs a starting thumbnail URL for a bare [videoId] with no other known thumbnail
     *  (e.g. a blank/missing stored value) — the actual best-available tier is still resolved at
     *  render time by the interceptor, this just picks where to start. */
    fun fromVideoId(videoId: String, preferHd: Boolean = true): String =
        "https://i.ytimg.com/vi/$videoId/${if (preferHd) "maxresdefault" else "hqdefault"}.jpg"
}
