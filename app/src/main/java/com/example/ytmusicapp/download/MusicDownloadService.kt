package com.example.ytmusicapp.download

import android.app.Notification
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.example.ytmusicapp.R
import java.io.File
import java.util.concurrent.Executors

@UnstableApi
class MusicDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.download_notification_channel_name,
    0
) {

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 1001
        const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
        private const val JOB_ID = 2001
        private var downloadCache: SimpleCache? = null

        fun getDownloadCache(context: Context): SimpleCache {
            if (downloadCache == null) {
                val dir = File(context.filesDir, "downloads")
                val dbProvider = StandaloneDatabaseProvider(context)
                downloadCache = SimpleCache(dir, NoOpCacheEvictor(), dbProvider)
            }
            return downloadCache!!
        }
    }

    override fun getDownloadManager(): DownloadManager {
        val cache = getDownloadCache(this)
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        return DownloadManager(
            this,
            StandaloneDatabaseProvider(this),
            cache,
            dataSourceFactory,
            Executors.newFixedThreadPool(3)
        ).apply {
            maxParallelDownloads = 3
        }
    }

    override fun getScheduler(): Scheduler =
        PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return DownloadNotificationHelper(
            this,
            DOWNLOAD_NOTIFICATION_CHANNEL_ID
        ).buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            null,
            downloads,
            notMetRequirements
        )
    }
}