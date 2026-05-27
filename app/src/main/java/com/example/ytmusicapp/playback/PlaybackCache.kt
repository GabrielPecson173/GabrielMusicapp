package com.example.ytmusicapp.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object PlaybackCache {

    private var cache: SimpleCache? = null

    // MUST be called from BACKGROUND THREAD
    @Synchronized
    fun getInstance(context: Context): SimpleCache {
        if (cache == null) {
            val dir = File(context.cacheDir, "audio_stream_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(512L * 1024 * 1024)
            val dbProvider = StandaloneDatabaseProvider(context)
            cache = SimpleCache(dir, evictor, dbProvider)
        }
        return cache!!
    }

    @Synchronized
    fun release() {
        cache?.release()
        cache = null
    }
}