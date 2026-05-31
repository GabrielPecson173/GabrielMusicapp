package com.example.ytmusicapp.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.example.ytmusicapp.data.extraction.StreamResolvingDataSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import androidx.media3.exoplayer.offline.DownloadManager

@OptIn(UnstableApi::class)
@Singleton
class DownloadManagerProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolvingDataSourceFactory: StreamResolvingDataSourceFactory
) {
    // ONE instance — shared by cache AND downloadManager
    private val databaseProvider: StandaloneDatabaseProvider by lazy {
        StandaloneDatabaseProvider(context)
    }

    val cache: SimpleCache by lazy {
        SimpleCache(File(context.filesDir, "downloads"), NoOpCacheEvictor(), databaseProvider)
    }

    val downloadManager: DownloadManager by lazy {
        DownloadManager(
            context,
            databaseProvider,
            cache,
            resolvingDataSourceFactory,
            Executors.newFixedThreadPool(3)
        ).apply {
            maxParallelDownloads = 3
        }
    }
}