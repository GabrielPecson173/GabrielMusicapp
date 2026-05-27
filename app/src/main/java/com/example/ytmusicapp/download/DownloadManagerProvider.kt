package com.example.ytmusicapp.download

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.offline.DownloadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManagerProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val downloadManager: DownloadManager by lazy {
        val cache = MusicDownloadService.getDownloadCache(context)
        val dbProvider = StandaloneDatabaseProvider(context)
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        DownloadManager(
            context,
            dbProvider,
            cache,
            dataSourceFactory,
            Executors.newFixedThreadPool(3)
        ).apply {
            maxParallelDownloads = 3
        }
    }
}