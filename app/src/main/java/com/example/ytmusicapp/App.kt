package com.example.ytmusicapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.ytmusicapp.data.datastore.UserPreferencesDataStore
import com.example.ytmusicapp.data.image.YoutubeThumbnailInterceptor
import com.example.ytmusicapp.download.DownloadManagerProvider
import com.example.ytmusicapp.download.DownloadStatusListener
import com.example.ytmusicapp.worker.WorkerScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var workerScheduler: WorkerScheduler
    @Inject lateinit var downloadStatusListener: DownloadStatusListener
    @Inject lateinit var downloadManagerProvider: DownloadManagerProvider
    @Inject lateinit var userPreferencesDataStore: UserPreferencesDataStore
    @Inject lateinit var youtubeThumbnailInterceptor: YoutubeThumbnailInterceptor

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    // Coil auto-detects an Application implementing ImageLoaderFactory and uses it as the app-wide
    // singleton ImageLoader — this is the single place YoutubeThumbnailInterceptor is installed,
    // so every AsyncImage call site in the app gets HD-preferring, verified-fallback thumbnails
    // without needing to change.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(youtubeThumbnailInterceptor) }
            .build()

    override fun onCreate() {
        super.onCreate()
        initNewPipe()
        workerScheduler.scheduleAll()
        downloadStatusListener.register() // ← was missing .register()
        applyStoredDownloadNetworkPreference()
    }

    private fun applyStoredDownloadNetworkPreference() {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val settings = userPreferencesDataStore.settings.first()
            downloadManagerProvider.setWifiOnly(settings.wifiOnlyDownloads)
        }
    }

    private fun initNewPipe() {
        NewPipe.init(object : Downloader() {
            // Default 10s timeouts can cut off a still-progressing response on a genuinely slow
            // connection (YouTube extraction payloads aren't small) — this is the client every
            // search/related-track/stream-resolution call in the app goes through, so it matters
            // more here than almost anywhere else.
            private val client = OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            override fun execute(request: Request): Response {
                val reqBuilder = okhttp3.Request.Builder().url(request.url())

                reqBuilder.header("Cookie", "CONSENT=YES+; SOCS=CAI")

                request.headers().forEach { (key, values) ->
                    values.forEach { value -> reqBuilder.addHeader(key, value) }
                }

                val body = request.dataToSend()?.let {
                    okhttp3.RequestBody.create(null, it)
                }

                reqBuilder.method(request.httpMethod(), body)

                val response = client.newCall(reqBuilder.build()).execute()

                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    response.body?.string(),
                    response.request.url.toString()
                )
            }
        })
    }
}