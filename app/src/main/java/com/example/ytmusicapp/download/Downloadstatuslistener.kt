package com.example.ytmusicapp.download

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import com.example.ytmusicapp.data.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class DownloadStatusListener @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val downloadManagerProvider: DownloadManagerProvider
) : DownloadManager.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun register() {
        downloadManagerProvider.downloadManager.addListener(this)
    }

    override fun onDownloadChanged(
        downloadManager: DownloadManager,
        download: Download,
        finalException: Exception?
    ) {
        val trackId = download.request.id
        val status = when (download.state) {
            Download.STATE_QUEUED      -> DownloadRepository.STATUS_QUEUED
            Download.STATE_DOWNLOADING -> DownloadRepository.STATUS_DOWNLOADING
            Download.STATE_COMPLETED   -> DownloadRepository.STATUS_COMPLETED
            Download.STATE_FAILED      -> DownloadRepository.STATUS_FAILED
            Download.STATE_REMOVING,
            Download.STATE_STOPPED     -> DownloadRepository.STATUS_CANCELLED
            else                       -> return
        }
        scope.launch {
            downloadRepository.updateStatus(trackId, status)
        }
    }
}