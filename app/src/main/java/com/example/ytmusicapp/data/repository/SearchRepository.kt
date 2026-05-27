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
                .filter { it.duration in 60..600 } // 1–10 min, kills compilations/podcasts
                .map { it.toDomain() }
        }

    suspend fun nextPage(
        query: String,
        pageUrl: String
    ): List<Track> = withContext(Dispatchers.IO) {
        val extractor: SearchExtractor = ServiceList.YouTube
            .getSearchExtractor(
                query,
                listOf("music_songs"),
                ""
            )

        extractor.fetchPage()

        val page = org.schabi.newpipe.extractor.Page(pageUrl)
        extractor.getPage(page).items
            .filterIsInstance<StreamInfoItem>()
            .filter { it.duration in 60..600 }
            .map { it.toDomain() }
    }

    private fun StreamInfoItem.toDomain(): Track {
        android.util.Log.d("SearchRepo", "url=$url extracted=${extractVideoId(url)}")
        return Track(
            id = extractVideoId(url),
            title = name,
            artist = uploaderName ?: "Unknown",
            album = "",
            thumbnailUrl = thumbnails.firstOrNull()?.url ?: "",
            durationMs = duration * 1000L,
            isDownloaded = false
        )
    }

    private fun extractVideoId(url: String): String {
        val vParam = url.substringAfter("v=").substringBefore("&").substringBefore("?")
        if (vParam.length == 11) return vParam
        val shortUrl = url.substringAfter("youtu.be/").substringBefore("?")
        if (shortUrl.length == 11) return shortUrl
        return url.trimEnd('/').substringAfterLast("/").substringBefore("?").take(11)
    }
}