package com.owlmedia.racecontrol.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the rolling reminder window fresh.
 *
 * iOS rebuilds its notification window on every app launch. That is not enough
 * on Android, where a user may not open the app between races, so a daily
 * worker rebuilds it whether or not the app has been opened.
 */
@HiltWorker
class ReminderRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: SessionReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        scheduler.refresh()
        Result.success()
    } catch (e: Exception) {
        // A transient backend outage should not permanently stop reminders.
        Result.retry()
    }
}

@Singleton
class ReminderRefreshScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val PERIODIC_WORK = "reminder_refresh_periodic"
        const val ONE_SHOT_WORK = "reminder_refresh_now"
    }

    fun ensurePeriodicRefresh() {
        val request = PeriodicWorkRequestBuilder<ReminderRefreshWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Immediate rebuild, used after a settings change or a reboot. */
    fun refreshNow() {
        val request = OneTimeWorkRequestBuilder<ReminderRefreshWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_SHOT_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
