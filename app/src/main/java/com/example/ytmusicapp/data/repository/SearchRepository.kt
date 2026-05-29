package com.example.ytmusicapp.data.repository

import com.example.ytmusicapp.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

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

    private fun StreamInfoItem.toDomain(): Track? {
        val videoId = extractVideoId(url)
        android.util.Log.d("SearchRepo", "raw_url=$url extracted_id=$videoId")
        if (videoId.isEmpty()) return null  // drop tracks with no valid ID
        return Track(
            id = videoId,
            title = name,
            artist = uploaderName ?: "Unknown",
            album = "",
            thumbnailUrl = thumbnails.firstOrNull()?.url ?: "",
            durationMs = duration * 1000L,
            isDownloaded = false
        )
    }

    private fun extractVideoId(url: String): String {
        // try v= param first
        val vIndex = url.indexOf("v=")
        if (vIndex != -1) {
            val id = url.substring(vIndex + 2).substringBefore("&").substringBefore("?").trim()
            if (id.length == 11) return id
        }
        // try youtu.be short url
        if (url.contains("youtu.be/")) {
            val id = url.substringAfter("youtu.be/").substringBefore("?").substringBefore("/").trim()
            if (id.length == 11) return id
        }
        // try last path segment
        val segment = url.trimEnd('/').substringAfterLast("/").substringBefore("?").trim()
        if (segment.length == 11) return segment

        return ""
    }
}