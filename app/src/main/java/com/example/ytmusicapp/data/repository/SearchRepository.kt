package com.example.ytmusicapp.data.repository

import android.util.Log
import com.example.ytmusicapp.domain.engine.MusicContentClassifier
import com.example.ytmusicapp.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

/** NewPipe returns several thumbnail resolutions per item with no guaranteed ordering — taking
 *  `.firstOrNull()` (the old behavior here) can just as easily land on YouTube's smallest
 *  (120x90-ish) default thumbnail as a large one, which is exactly why images looked blurry when
 *  shown at any real size (the Artist Page hero in particular, at full screen width). Pick the
 *  widest available image instead. */
private fun List<Image>.bestUrl(): String = maxByOrNull { it.width }?.url ?: firstOrNull()?.url ?: ""

// A first page near-full of results is a signal there's likely more — worth one extra round-trip
// rather than silently treating "however many fit on page 1" as the artist's complete
// album/single catalog. Not unbounded crawling: exactly one extra page, ever.
private const val ALBUM_PAGE_FULL_THRESHOLD = 18

/** A YouTube Music album search hit — enough to show a card and fetch the full tracklist on
 *  demand via [SearchRepository.getAlbumTracks]. */
data class AlbumSearchResult(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val playlistUrl: String
)

@Singleton
class SearchRepository @Inject constructor(
    private val musicContentClassifier: MusicContentClassifier
) {

    suspend fun search(query: String): List<Track> =
        withContext(Dispatchers.IO) {
            rawSearchItems(query, null).classifyAndFilter()
        }

    suspend fun nextPage(query: String, pageUrl: String): List<Track> =
        withContext(Dispatchers.IO) {
            rawSearchItems(query, pageUrl).classifyAndFilter()
        }

    /**
     * Same search as [search], but follows up to [maxPages] of YouTube's own result pages before
     * classifying/filtering. A single page is ~20-30 items — plenty for a normal interactive
     * search, but an undercount for "give me as much of this artist's real catalog as fast as
     * possible" (ArtistRepository's fast-preview path), especially since, unlike MusicBrainz,
     * YouTube search isn't subject to any comparable rate limit — paginating it a few pages deep
     * is cheap and fast. Not unbounded: stops at [maxPages] or whenever YouTube stops offering a
     * next page, whichever comes first.
     */
    suspend fun searchMultiPage(query: String, maxPages: Int): List<Track> =
        withContext(Dispatchers.IO) {
            val extractor: SearchExtractor = ServiceList.YouTube.getSearchExtractor(query, listOf("music_songs"), "")
            extractor.fetchPage()
            val allItems = mutableListOf<StreamInfoItem>()
            var page = extractor.initialPage
            allItems += page.items.filterIsInstance<StreamInfoItem>()
            var pagesFetched = 1
            while (pagesFetched < maxPages && page.hasNextPage()) {
                page = runCatching { extractor.getPage(page.nextPage) }.getOrNull() ?: break
                allItems += page.items.filterIsInstance<StreamInfoItem>()
                pagesFetched++
            }
            Log.d("SearchRepo", "searchMultiPage('$query'): pages=$pagesFetched rawItems=${allItems.size}")
            allItems.classifyAndFilter()
        }

    /**
     * Same underlying search as [search], but WITHOUT [MusicContentClassifier] filtering — for
     * matching a known-real MusicBrainz canonical track to its playable YouTube video
     * (`ArtistRepository.matchCanonicalTrack`). That caller already independently verifies title,
     * duration, and artist-credit correspondence to a real recording MusicBrainz confirms exists;
     * running a generic music-vs-non-music classifier on top adds no benefit there and only the
     * risk of dropping the correct match before the caller's own verification ever sees it.
     */
    suspend fun searchRaw(query: String): List<Track> =
        withContext(Dispatchers.IO) {
            rawSearchItems(query, null).mapNotNull { it.toDomain() }
        }

    private fun rawSearchItems(query: String, pageUrl: String?): List<StreamInfoItem> {
        val extractor: SearchExtractor = ServiceList.YouTube
            .getSearchExtractor(query, listOf("music_songs"), "")
        extractor.fetchPage()
        val items = if (pageUrl == null) {
            extractor.initialPage.items
        } else {
            extractor.getPage(org.schabi.newpipe.extractor.Page(pageUrl)).items
        }
        return items.filterIsInstance<StreamInfoItem>()
    }

    // Replaces the old hard `duration in 60..600` cutoff, which excluded legitimate long-form
    // content (concerts, extended performances) and did nothing to catch obviously non-music
    // results. Drops only near-certain non-music (MusicContentClassifier.isLikelyNonMusic), then
    // stable-partitions into a confident tier and a borderline tier — each tier keeps YouTube's
    // own relevance ordering rather than being re-sorted by raw score. This is classification-
    // assisted filtering/demotion on top of YouTube's existing order, not a replacement ranking:
    // a 0.0-scoring result sorts ahead of a -0.1 one purely by tier, not because it's "more music".
    private fun List<StreamInfoItem>.classifyAndFilter(): List<Track> =
        mapNotNull { item ->
            item.toDomain()?.let { track ->
                track to musicContentClassifier.score(item.name, item.uploaderName ?: "", item.duration)
            }
        }
            .filterNot { (_, score) -> musicContentClassifier.isLikelyNonMusic(score) }
            .sortedByDescending { (_, score) -> score >= 0f }
            .map { (track, _) -> track }

    suspend fun getRelatedTracks(videoId: String): List<Track> =
        withContext(Dispatchers.IO) {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, url)
            streamInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .filter { it.duration in 60..600 }
                .mapNotNull { it.toDomain() }
        }

    // ── Albums (YouTube Music "music_albums" search) ──────────────────────────
    // Album/track-list metadata doesn't exist anywhere in local storage (search results and
    // related-track ingestion both leave Track.album blank — see SearchRepository.toDomain), so
    // the Artist Page's Albums section fetches it live from YouTube Music itself, the same way
    // `search`/`getRelatedTracks` already hit the network for everything else here.

    suspend fun searchAlbums(query: String): List<AlbumSearchResult> =
        withContext(Dispatchers.IO) {
            val extractor: SearchExtractor = ServiceList.YouTube
                .getSearchExtractor(query, listOf("music_albums"), "")
            extractor.fetchPage()
            val page = extractor.initialPage
            var items = page.items
            var errorCount = page.errors.size
            var firstError = page.errors.firstOrNull()?.message

            if (items.size >= ALBUM_PAGE_FULL_THRESHOLD && page.hasNextPage()) {
                val second = runCatching { extractor.getPage(page.nextPage) }.getOrNull()
                if (second != null) {
                    items = items + second.items
                    errorCount += second.errors.size
                }
            }

            Log.d(
                "SearchRepo",
                "searchAlbums('$query') -> ${items.size} raw item(s) " +
                    "[${items.map { it::class.simpleName }.distinct()}], " +
                    "$errorCount error(s)" + (firstError?.let { ": $it" } ?: "")
            )
            items
                .filterIsInstance<PlaylistInfoItem>()
                .mapNotNull { it.toAlbumResult() }
        }

    // NewPipeExtractor's generic YoutubePlaylistExtractor (used by PlaylistInfo.getInfo) only
    // recognizes classic playlistVideoRenderer/richItemRenderer JSON and always browses via
    // "VL" + playlistId. YouTube Music album pages ("OLAK5uy_..." playlists) come back in a
    // different renderer shape it doesn't parse, so it silently collects zero items — confirmed
    // against this app's pinned commit (TeamNewPipe/NewPipeExtractor@df389f5) by reading the
    // actual source rather than guessing. There's no dedicated YT Music album-page extractor in
    // this version either, so instead of fighting an unsupported endpoint, approximate the
    // tracklist via the same "music_songs" search that already works everywhere else in the app.
    suspend fun getAlbumTracks(albumTitle: String, artistName: String): List<Track> =
        withContext(Dispatchers.IO) {
            val extractor: SearchExtractor = ServiceList.YouTube
                .getSearchExtractor("$albumTitle $artistName", listOf("music_songs"), "")
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .filter { it.duration in 60..600 }
                .mapNotNull { it.toDomain(albumTitle) }
        }

    private fun PlaylistInfoItem.toAlbumResult(): AlbumSearchResult? {
        if (url.isBlank() || name.isBlank()) return null
        return AlbumSearchResult(
            id = url,
            title = name,
            artist = uploaderName ?: "",
            thumbnailUrl = thumbnails.bestUrl(),
            playlistUrl = url
        )
    }

    private fun StreamInfoItem.toDomain(albumTitle: String = ""): Track? {
        val videoId = extractVideoId(url)
        android.util.Log.d("SearchRepo", "raw_url=$url extracted_id=$videoId")
        if (videoId.isEmpty()) return null
        return Track(
            id = videoId,
            title = name,
            artist = uploaderName ?: "Unknown",
            album = albumTitle,
            thumbnailUrl = thumbnails.bestUrl(),
            durationMs = duration * 1000L,
            isDownloaded = false
        )
    }

    private fun extractVideoId(url: String): String {
        val vIndex = url.indexOf("v=")
        if (vIndex != -1) {
            val id = url.substring(vIndex + 2).substringBefore("&").substringBefore("?").trim()
            if (id.length == 11) return id
        }
        if (url.contains("youtu.be/")) {
            val id = url.substringAfter("youtu.be/").substringBefore("?").substringBefore("/").trim()
            if (id.length == 11) return id
        }
        val segment = url.trimEnd('/').substringAfterLast("/").substringBefore("?").trim()
        if (segment.length == 11) return segment
        return ""
    }
}