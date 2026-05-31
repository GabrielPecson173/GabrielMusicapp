package com.example.ytmusicapp.data.repository

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.example.ytmusicapp.data.db.dao.DownloadDao
import com.example.ytmusicapp.data.db.entity.DownloadEntity
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.download.DownloadManagerProvider
import com.example.ytmusicapp.download.MusicDownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao,
    private val downloadManagerProvider: DownloadManagerProvider,
    @ApplicationContext private val context: Context
) {

    private val downloadManager: DownloadManager
        get() = downloadManagerProvider.downloadManager

    fun getAllDownloads(): Flow<List<DownloadEntity>> =
        downloadDao.getAllDownloads()

    fun getDownloadByTrackId(trackId: String): Flow<DownloadEntity?> =
        downloadDao.getDownloadByTrackId(trackId)

    fun getDownloadsByStatus(status: String): Flow<List<DownloadEntity>> =
        downloadDao.getDownloadsByStatus(status)

    suspend fun enqueue(track: Track) {
        // Custom scheme — ResolvingDataSourceFactory resolves fresh URL at download time
        val downloadUri = Uri.parse("ytmusic://${track.id}")

        val downloadRequest = DownloadRequest.Builder(track.id, downloadUri).build()

        DownloadService.sendAddDownload(
            context,
            MusicDownloadService::class.java,
            downloadRequest,
            false
        )

        downloadDao.insert(
            DownloadEntity(
                id = track.id,
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                filePath = downloadUri.toString(),
                status = STATUS_QUEUED
            )
        )
    }

    suspend fun cancel(trackId: String) {
        DownloadService.sendRemoveDownload(
            context,
            MusicDownloadService::class.java,
            trackId,
            false
        )
        downloadDao.updateStatus(trackId, STATUS_CANCELLED)
    }

    suspend fun remove(trackId: String) {
        DownloadService.sendRemoveDownload(
            context,
            MusicDownloadService::class.java,
            trackId,
            false
        )
        val entity = downloadDao.getDownloadByTrackId(trackId).first()
        entity?.let { downloadDao.delete(it) }
    }

    suspend fun updateStatus(trackId: String, status: String) =
        downloadDao.updateStatus(trackId, status)

    companion object {
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_DOWNLOADING = "DOWNLOADING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}