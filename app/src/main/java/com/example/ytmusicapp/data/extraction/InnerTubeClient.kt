package com.example.ytmusicapp.data.extraction

import com.example.ytmusicapp.domain.model.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InnerTubeClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val poTokenGenerator: PoTokenGenerator
) {

    companion object {
        private const val API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        private const val PLAYER_URL =
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false&key=$API_KEY"

        private const val CLIENT_NAME    = "ANDROID_TESTSUITE"
        private const val CLIENT_VERSION = "1.9"
        private const val CLIENT_ID      = "30"
        private const val USER_AGENT     =
            "Mozilla/5.0 (Linux; Android 11; sdk_gphone_x86) AppleWebKit/537.36"
    }

    suspend fun getStream(videoId: String): StreamInfo = withContext(Dispatchers.IO) {
        val body = buildRequestBody(videoId)

        val request = Request.Builder()
            .url(PLAYER_URL)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .header("X-YouTube-Client-Name", CLIENT_ID)
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .header("X-Goog-Api-Key", API_KEY)
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

        val json   = JSONObject(response.body?.string() ?: throw Exception("Empty response"))
        val status = json.optJSONObject("playabilityStatus")?.optString("status")
        android.util.Log.d("InnerTubeClient", "status=$status videoId=$videoId")

        if (status != "OK") {
            val reason = json.optJSONObject("playabilityStatus")
                ?.optString("reason", "unknown")
            throw Exception("playabilityStatus=$status reason=$reason")
        }

        parseStreamInfo(videoId, json)
    }

    private fun buildRequestBody(videoId: String): String = JSONObject().apply {
        put("videoId", videoId)
        put("context", JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", CLIENT_NAME)
                put("clientVersion", CLIENT_VERSION)
                put("androidSdkVersion", 30)
                put("hl", "en")
                put("gl", "US")
                put("utcOffsetMinutes", 0)
            })
        })
        put("racyCheckOk", true)
        put("contentCheckOk", true)
    }.toString()

    private fun parseStreamInfo(videoId: String, json: JSONObject): StreamInfo {
        val streamingData = json.optJSONObject("streamingData")
            ?: throw Exception("No streamingData for $videoId")

        val adaptive = streamingData.optJSONArray("adaptiveFormats")
        val formats  = streamingData.optJSONArray("formats")

        var bestUrl     = ""
        var bestBitrate = 0
        var bestMime    = ""
        var durationMs  = 0L

        if (adaptive != null) {
            for (i in 0 until adaptive.length()) {
                val fmt     = adaptive.getJSONObject(i)
                val mime    = fmt.optString("mimeType", "")
                val url     = fmt.optString("url", "")
                if (!mime.startsWith("audio/") || url.isBlank()) continue
                val bitrate = fmt.optInt("bitrate", 0)
                if (bitrate > bestBitrate) {
                    bestBitrate = bitrate
                    bestUrl     = url
                    bestMime    = mime
                    durationMs  = fmt.optLong("approxDurationMs", 0L)
                }
            }
        }

        if (bestUrl.isBlank() && formats != null) {
            for (i in 0 until formats.length()) {
                val fmt = formats.getJSONObject(i)
                val url = fmt.optString("url", "")
                if (url.isBlank()) continue
                bestUrl     = url
                bestMime    = fmt.optString("mimeType", "")
                bestBitrate = fmt.optInt("bitrate", 0)
                durationMs  = fmt.optLong("approxDurationMs", 0L)
                break
            }
        }

        if (bestUrl.isBlank()) throw Exception("No direct-url stream for $videoId")

        android.util.Log.d(
            "InnerTubeClient",
            "resolved url length=${bestUrl.length} mime=$bestMime"
        )

        return StreamInfo(
            videoId    = videoId,
            streamUrl  = bestUrl,
            mimeType   = bestMime,
            bitrate    = bestBitrate,
            durationMs = durationMs
        )
    }
}