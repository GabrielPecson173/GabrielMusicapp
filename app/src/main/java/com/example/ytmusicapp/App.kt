package com.example.ytmusicapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ytmusicapp.worker.DatabaseCleanupWorker
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        initNewPipe()
        scheduleCleanup()
    }

    private fun initNewPipe() {
        NewPipe.init(object : Downloader() {
            private val client = OkHttpClient()

            override fun execute(request: Request): Response {
                val reqBuilder = okhttp3.Request.Builder().url(request.url())

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

    private fun scheduleCleanup() {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        val request = PeriodicWorkRequestBuilder<DatabaseCleanupWorker>(
            1, TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "db_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}