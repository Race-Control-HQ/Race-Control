package com.owlmedia.racecontrol

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.owlmedia.racecontrol.notifications.ReminderNotifier
import com.owlmedia.racecontrol.notifications.ReminderRefreshScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RaceControlApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var refreshScheduler: ReminderRefreshScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Channels are cheap and idempotent; creating one up front means the
        // user can see and tune it in system settings before the first reminder.
        notifier.ensureChannel()
        // Android users may not open the app for weeks, so the reminder window
        // is kept fresh by a daily worker rather than only on launch.
        refreshScheduler.ensurePeriodicRefresh()
    }
}
