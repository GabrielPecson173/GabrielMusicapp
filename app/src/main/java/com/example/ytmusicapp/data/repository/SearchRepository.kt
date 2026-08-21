package com.example.ytmusicapp.data.repository

import android.util.Log
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
class SearchRepository @Inject constructor() {

    suspend fun search(query: String): List<Track> =
        withContext(Dispatchers.IO) {
            val extractor: SearchExtractor = ServiceList.YouTube
                .getSearchExtractor(
                    query,
                    listOf("music_songs"),
                    ""
                )
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .filter { it.duration in 60..600 }
                .mapNotNull { it.toDomain() }
        }

    suspend fun nextPage(query: String, pageUrl: String): List<Track> =
        withContext(Dispatchers.IO) {
            val extractor: SearchExtractor = ServiceList.YouTube
                .getSearchExtractor(query, listOf("music_songs"), "")
            extractor.fetchPage()
            val page = org.schabi.newpipe.extractor.Page(pageUrl)
            extractor.getPage(page).items
                .filterIsInstance<StreamInfoItem>()
                .filter { it.duration in 60..600 }
                .mapNotNull { it.toDomain() }
        }

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
            Log.d(
                "SearchRepo",
                "searchAlbums('$query') -> ${page.items.size} raw item(s) " +
                    "[${page.items.map { it::class.simpleName }.distinct()}], " +
                    "${page.errors.size} error(s)" +
                    (page.errors.firstOrNull()?.let { ": ${it.message}" } ?: "")
            )
            page.items
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