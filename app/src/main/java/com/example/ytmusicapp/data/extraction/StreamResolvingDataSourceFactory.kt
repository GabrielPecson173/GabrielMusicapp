package com.example.ytmusicapp.data.extraction

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class StreamResolvingDataSourceFactory @Inject constructor(
    private val extractionStrategy: ExtractionStrategy
) : DataSource.Factory {

    private val urlCache = ConcurrentHashMap<String, String>()

    fun evict(videoId: String) = urlCache.remove(videoId)
    fun evictAll() = urlCache.clear()

    override fun createDataSource(): DataSource {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        return ResolvingDataSource(httpFactory.createDataSource()) { dataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme == "ytmusic") {
                val videoId = uri.host ?: uri.path?.trimStart('/') ?: error("No videoId in $uri")
                val streamUrl = urlCache.getOrPut(videoId) {
                    runBlocking { extractionStrategy.getStreamUrl(videoId).streamUrl }
                }
                dataSpec.withUri(android.net.Uri.parse(streamUrl))
            } else {
                dataSpec
            }
        }
    }
}