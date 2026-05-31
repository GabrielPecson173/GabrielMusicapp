package com.example.ytmusicapp.download

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.example.ytmusicapp.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class MusicDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.download_notification_channel_name,
    0
) {

    @Inject lateinit var downloadManagerProvider: DownloadManagerProvider

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 1001
        const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
        private const val JOB_ID = 2001
    }

    // Return the SHARED instance — same one DownloadStatusListener watches
    override fun getDownloadManager(): DownloadManager =
        downloadManagerProvider.downloadManager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification = DownloadNotificationHelper(
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