package com.example.ytmusicapp.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.example.ytmusicapp.data.extraction.StreamResolvingDataSourceFactory
import com.example.ytmusicapp.download.DownloadManagerProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bounded, evictable cache for streamed (non-downloaded) playback, layered underneath the
 * permanent (unbounded) downloads cache:
 *
 *   playback read -> [downloads cache, read-only]  -- miss -->
 *                    [streaming cache, LRU-bounded] -- miss --> [network]
 *
 * Both cache layers key on the original "ytmusic://{videoId}" URI (resolution to a real,
 * signed, short-lived URL happens deeper in the chain, inside [StreamResolvingDataSourceFactory],
 * so it never affects the cache key). A track that was explicitly downloaded is served from the
 * downloads cache with zero network; anything merely streamed gets opportunistically cached here
 * instead, capped at [STREAMING_CACHE_MAX_BYTES] with least-recently-used eviction so the current
 * and next-queued tracks — the ones actually being touched — are the last to be evicted.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlaybackCacheProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManagerProvider: DownloadManagerProvider,
    private val streamResolvingDataSourceFactory: StreamResolvingDataSourceFactory
) {
    companion object {
        private const val STREAMING_CACHE_MAX_BYTES = 500L * 1024 * 1024
    }

    val streamingCache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, "stream_playback_cache"),
            LeastRecentlyUsedCacheEvictor(STREAMING_CACHE_MAX_BYTES),
            downloadManagerProvider.databaseProvider
        )
    }

    private fun streamingCacheDataSourceFactory(): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(streamingCache)
            .setUpstreamDataSourceFactory(streamResolvingDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** The DataSource.Factory ExoPlayer should read through for actual playback. */
    fun createPlaybackDataSourceFactory(): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(downloadManagerProvider.cache)
            .setUpstreamDataSourceFactory(streamingCacheDataSourceFactory())
            .setCacheWriteDataSinkFactory(null) // never write into the permanent downloads cache from normal playback
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    @Volatile private var activeWriter: CacheWriter? = null

    /** Cancels any in-flight [prefetchBlocking] call. Safe to call from any thread. */
    fun cancelPrefetch() {
        activeWriter?.cancel()
    }

    /**
     * Best-effort, blocking prefetch of a track's audio into the streaming cache. CacheWriter
     * only fetches the byte ranges not already cached, so calling this on an already-cached
     * (or already-downloaded, handled by the caller) track is cheap. Call from a background
     * thread/dispatcher. Failures (no network, cancellation) are swallowed — this is purely an
     * optimization, never a requirement for playback to proceed.
     */
    fun prefetchBlocking(videoId: String) {
        val dataSource = streamingCacheDataSourceFactory().createDataSource()
        val dataSpec = DataSpec(Uri.parse("ytmusic://$videoId"))
        val writer = CacheWriter(dataSource, dataSpec, null, null)
        activeWriter = writer
        try {
            writer.cache()
        } catch (_: Exception) {
            // Network unavailable, cancelled, etc. — nothing to do; live playback will
            // still resolve/cache normally when this track's turn actually comes.
        } finally {
            if (activeWriter === writer) activeWriter = null
        }
    }
}
