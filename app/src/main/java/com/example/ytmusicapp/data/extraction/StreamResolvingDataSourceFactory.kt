package com.example.ytmusicapp.data.extraction

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class StreamResolvingDataSourceFactory @Inject constructor(
    private val extractionStrategy: ExtractionStrategy
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        return ResolvingDataSource(httpFactory.createDataSource()) { dataSpec ->
            // dataSpec.uri is "ytmusic://VIDEO_ID" — resolve to real stream URL fresh
            val uri = dataSpec.uri
            if (uri.scheme == "ytmusic") {
                val videoId = uri.host ?: uri.path?.trimStart('/') ?: error("No videoId in $uri")
                val streamInfo = runBlocking { extractionStrategy.getStreamUrl(videoId) }
                dataSpec.withUri(android.net.Uri.parse(streamInfo.streamUrl))
            } else {
                dataSpec
            }
        }
    }
}