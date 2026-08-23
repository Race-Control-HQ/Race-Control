package com.owlmedia.racecontrol.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Re-schedules reminders after events that silently clear them.
 *
 * Android drops all of an app's alarms on reboot and on app update, and a
 * timezone change invalidates every computed fire time. None of this applies on
 * iOS, where the system keeps scheduled notifications itself, so this receiver
 * has no counterpart in the iOS app and is essential here.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var refreshScheduler: ReminderRefreshScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> refreshScheduler.refreshNow()
        }
    }
}
