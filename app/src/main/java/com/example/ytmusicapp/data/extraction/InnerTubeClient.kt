package com.example.ytmusicapp.data.extraction

import com.example.ytmusicapp.domain.model.StreamInfo
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
        private const val INNERTUBE_BASE = "https://music.youtube.com/youtubei/v1"
        private const val PLAYER_ENDPOINT = "$INNERTUBE_BASE/player"
        private const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

        private val CLIENT_CONFIG = mapOf(
            "clientName" to "MWEB",
            "clientVersion" to "2.20240726.01.00"
        )
    }

    suspend fun getStream(videoId: String): StreamInfo {
        val poToken = poTokenGenerator.generatePoToken(videoId)
        val body = buildRequestBody(videoId, poToken)

        val request = Request.Builder()
            .url("$PLAYER_ENDPOINT?key=$API_KEY")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .header("X-YouTube-Client-Name", "2")
            .header("X-YouTube-Client-Version", CLIENT_CONFIG["clientVersion"]!!)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .build()

        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("InnerTube request failed: ${response.code}")
        }

        val json = JSONObject(response.body?.string() ?: throw Exception("Empty response"))
        return parseStreamInfo(videoId, json)
    }

    private fun buildRequestBody(videoId: String, poToken: String): String {
        return JSONObject().apply {
            put("videoId", videoId)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", CLIENT_CONFIG["clientName"])
                    put("clientVersion", CLIENT_CONFIG["clientVersion"])
                    put("hl", "en")
                    put("gl", "US")
                })
            })
            put("serviceIntegrityDimensions", JSONObject().apply {
                put("poToken", poToken)
            })
            put("racyCheckOk", true)
            put("contentCheckOk", true)
        }.toString()
    }

    private fun parseStreamInfo(videoId: String, json: JSONObject): StreamInfo {
        val streamingData = json.getJSONObject("streamingData")
        val formats = streamingData.getJSONArray("adaptiveFormats")

        // Pick highest bitrate audio-only stream
        var bestUrl = ""
        var bestBitrate = 0
        var bestMime = ""
        var durationMs = 0L

        for (i in 0 until formats.length()) {
            val format = formats.getJSONObject(i)
            val mime = format.optString("mimeType", "")
            if (!mime.startsWith("audio/")) continue

            val bitrate = format.optInt("bitrate", 0)
            if (bitrate > bestBitrate) {
                bestBitrate = bitrate
                bestUrl = format.optString("url", "")
                bestMime = mime
                durationMs = format.optLong("approxDurationMs", 0L)
            }
        }

        if (bestUrl.isEmpty()) throw Exception("No audio stream found for $videoId")

        return StreamInfo(
            videoId = videoId,
            streamUrl = bestUrl,
            mimeType = bestMime,
            bitrate = bestBitrate,
            durationMs = durationMs
        )
    }
}