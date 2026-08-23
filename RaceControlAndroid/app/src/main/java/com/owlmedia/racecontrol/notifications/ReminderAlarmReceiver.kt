package com.owlmedia.racecontrol.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fires when a scheduled session reminder comes due. */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_YEAR = "year"
        const val EXTRA_ROUND = "round"
    }

    @Inject lateinit var notifier: ReminderNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val year = intent.getIntExtra(EXTRA_YEAR, 0)
        val round = intent.getIntExtra(EXTRA_ROUND, 0)
        notifier.show(id, title, body, year, round)
    }
}
