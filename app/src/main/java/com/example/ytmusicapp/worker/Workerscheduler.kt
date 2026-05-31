package com.example.ytmusicapp.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val WORK_TIER2_FEED_REFRESH = "feed_refresh_tier2"
private const val WORK_TIER3_DB_CLEANUP   = "db_cleanup_tier3"

@Singleton
class WorkerScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Call once from App.onCreate().
     * KEEP_EXISTING — won't reschedule if already enqueued, survives restarts.
     */
    fun scheduleAll() {
        scheduleTier2FeedRefresh()
        scheduleTier3Cleanup()
    }

    // ── Tier 2 — every 4 hours, requires network ──────────────────────────────

    private fun scheduleTier2FeedRefresh() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(
            repeatInterval = 4,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_TIER2_FEED_REFRESH,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    // ── Tier 3 — daily at ~3AM, requires charging + idle + any network ────────

    private fun scheduleTier3Cleanup() {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Initial delay targets next 3AM window
        val initialDelayMs = computeDelayUntil3AM()

        val request = PeriodicWorkRequestBuilder<DatabaseCleanupWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_TIER3_DB_CLEANUP,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun computeDelayUntil3AM(): Long {
        val now    = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)  // already past 3AM today
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }
}