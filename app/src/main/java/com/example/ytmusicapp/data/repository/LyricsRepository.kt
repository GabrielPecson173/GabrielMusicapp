package com.example.ytmusicapp.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube Music's own lyrics endpoint requires an authenticated session — anonymous
 * requests return a "Lyrics not available" placeholder even for tracks that do have
 * lyrics. LRCLIB (lrclib.net) is a free, keyless lyrics database used by several
 * open-source music apps and works without login.
 */
@Singleton
class LyricsRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val GET_URL    = "https://lrclib.net/api/get"
        private const val SEARCH_URL = "https://lrclib.net/api/search"
    }

    suspend fun getLyrics(title: String, artist: String, durationSec: Int? = null): String =
        withContext(Dispatchers.IO) {
            fetchExact(title, artist, durationSec)
                ?: fetchViaSearch(title, artist)
                ?: throw Exception("No lyrics found for this track")
        }

    private fun fetchExact(title: String, artist: String, durationSec: Int?): String? {
        val urlBuilder = GET_URL.toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
        if (durationSec != null && durationSec > 0) {
            urlBuilder.addQueryParameter("duration", durationSec.toString())
        }
        val request = Request.Builder().url(urlBuilder.build()).header("User-Agent", "YTMusicApp/1.0").build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return null
        val json = JSONObject(response.body?.string() ?: return null)
        return extractPlainLyrics(json)
    }

    private fun fetchViaSearch(title: String, artist: String): String? {
        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .build()
        val request = Request.Builder().url(url).header("User-Agent", "YTMusicApp/1.0").build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return null
        val results = JSONArray(response.body?.string() ?: return null)
        for (i in 0 until results.length()) {
            val lyrics = extractPlainLyrics(results.getJSONObject(i))
            if (lyrics != null) return lyrics
        }
        return null
    }

    private fun extractPlainLyrics(json: JSONObject): String? {
        if (json.optBoolean("instrumental", false)) return "(Instrumental — no lyrics)"
        val text = json.optString("plainLyrics").trim()
        return text.ifBlank { null }
    }
}
