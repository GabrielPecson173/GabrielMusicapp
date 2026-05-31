package com.example.ytmusicapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.ytmusicapp.download.DownloadStatusListener
import com.example.ytmusicapp.worker.WorkerScheduler
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var workerScheduler: WorkerScheduler
    @Inject lateinit var downloadStatusListener: DownloadStatusListener

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        initNewPipe()
        workerScheduler.scheduleAll()
        downloadStatusListener.register() // ← was missing .register()
    }

    private fun initNewPipe() {
        NewPipe.init(object : Downloader() {
            private val client = OkHttpClient()

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