package com.zainkhalid.salah.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zainkhalid.salah.focus.FocusModeRunner
import com.zainkhalid.salah.repo
import com.zainkhalid.salah.update.UpdateChecker
import com.zainkhalid.salah.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import salah.core.Prayer
import salah.core.ReminderSound
import salah.core.focusmode.CardNote
import java.time.Instant

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderScheduler.ACTION_REMIND -> {
                val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID) ?: return
                val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: return
                val body = intent.getStringExtra(ReminderScheduler.EXTRA_BODY).orEmpty()
                val reminders = context.repo.config.value.reminders
                val azan = intent.getBooleanExtra(ReminderScheduler.EXTRA_AZAN, false) && reminders.azan && reminders.sound != ReminderSound.SILENT
                val silent = intent.getBooleanExtra(ReminderScheduler.EXTRA_SILENT, false)
                Notifications.show(context, id, title, body, reminders.sound, azan && !silent, silent)
            }
            ReminderScheduler.ACTION_REPLAN -> {
                ReminderScheduler.reschedule(context)
                UpdateChecker.dailyCheck(context)
                refreshWidgets(context)
            }
            ReminderScheduler.ACTION_PRAYER_TIME, FocusModeRunner.ACTION_RECHECK -> {
                val prayer = Prayer.fromRaw(intent.getStringExtra(ReminderScheduler.EXTRA_PRAYER).orEmpty()) ?: return
                val time = Instant.ofEpochMilli(intent.getLongExtra(ReminderScheduler.EXTRA_PRAYER_TIME, 0L))
                // ExoPlayer and the player state live on the main thread, where receivers run.
                if (intent.action == ReminderScheduler.ACTION_PRAYER_TIME) {
                    FocusModeRunner.onPrayerTime(context, prayer, time)
                } else {
                    val note = CardNote.fromRaw(intent.getStringExtra(FocusModeRunner.EXTRA_NOTE)) ?: CardNote.CALL_ENDED
                    FocusModeRunner.recheck(context, prayer, time, note)
                }
            }
            FocusModeRunner.ACTION_DONE -> FocusModeRunner.done(context)
            ReminderScheduler.ACTION_WIDGET_TICK -> refreshWidgets(context)
        }
    }

    private fun refreshWidgets(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetRefresher.refreshNow(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
